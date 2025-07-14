package com.lambdb.core.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.lambdb.core.JsonUtil; // Corrected import

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * QueryParser.java
 * Author: Vishwas Karode
 * Description:
 * This class is responsible for parsing LAMBQL (LAMB Query Language) command strings.
 * It takes a raw string input, validates its basic structure, extracts keywords and data,
 * and converts it into a structured {@link Query} object that the interpreter can understand.
 *
 * Supported commands: CREATE COLLECTION, INSERT INTO ... VALUES, SELECT * FROM ... [WHERE],
 * UPDATE ... SET ... WHERE ..., DELETE FROM ... WHERE ..., DELETE FROM ... (collection).
 * The parser is case-insensitive for keywords.
 */
public class QueryParser {

    // --- LAMBQL Keyword Constants (Uppercased for case-insensitive matching) ---
    private static final String CREATE_COLLECTION_PREFIX = "CREATE COLLECTION ";
    private static final String INSERT_INTO_PREFIX = "INSERT INTO ";
    private static final String VALUES_KEYWORD = " VALUES ";
    private static final String SELECT_FROM_PREFIX = "SELECT * FROM ";
    private static final String WHERE_KEYWORD = " WHERE ";
    private static final String UPDATE_PREFIX = "UPDATE ";
    private static final String SET_KEYWORD = " SET ";
    private static final String DELETE_FROM_PREFIX = "DELETE FROM ";
    private static final String SEMICOLON = ";"; // All commands must end with a semicolon

    /**
     * Parses a LAMBQL command string.
     * This is the primary method to convert a raw query string into a structured Query object.
     *
     * @param command The LAMBQL command string (e.g., "INSERT INTO users VALUES { \"name\": \"Alice\" };").
     * @return A {@link Query} object representing the parsed command.
     * @throws IllegalArgumentException If the command format is invalid or required parts are missing.
     * @throws IOException If JSON parsing within the command string fails (e.g., malformed JSON in VALUES or WHERE/SET clauses).
     */
    public Query parse(String command) throws IllegalArgumentException, IOException {
        // Step 1: Clean and standardize the command string
        // Trim whitespace and convert to uppercase for case-insensitive keyword matching.
        String cleanedCommand = command.trim().toUpperCase(Locale.ROOT);

        // Step 2: Validate ending semicolon
        if (!cleanedCommand.endsWith(SEMICOLON)) {
            throw new IllegalArgumentException("Invalid LAMBQL command by Vishwas Karode: All commands must end with a semicolon (';').");
        }
        // Remove the semicolon for easier parsing of the command body.
        cleanedCommand = cleanedCommand.substring(0, cleanedCommand.length() - 1).trim();

        // Step 3: Identify the command type and delegate to specific parsers
        if (cleanedCommand.startsWith(CREATE_COLLECTION_PREFIX)) {
            return parseCreateCollection(cleanedCommand);
        } else if (cleanedCommand.startsWith(INSERT_INTO_PREFIX)) {
            return parseInsert(cleanedCommand);
        } else if (cleanedCommand.startsWith(SELECT_FROM_PREFIX)) {
            return parseSelect(cleanedCommand);
        } else if (cleanedCommand.startsWith(UPDATE_PREFIX)) {
            return parseUpdate(cleanedCommand);
        } else if (cleanedCommand.startsWith(DELETE_FROM_PREFIX)) {
            // DELETE can be either DELETE DOCUMENT (with WHERE) or DELETE COLLECTION (without WHERE)
            return parseDelete(cleanedCommand);
        } else {
            // If no known command prefix matches, it's an UNKNOWN query.
            System.err.println("[QueryParser] Unknown command pattern: " + command);
            return new Query(QueryType.UNKNOWN, null); // Return unknown type for unparseable commands.
        }
    }

    /**
     * Parses a 'CREATE COLLECTION' command.
     * Expected format: CREATE COLLECTION <collectionName>;
     */
    private Query parseCreateCollection(String command) {
        String collectionName = command.substring(CREATE_COLLECTION_PREFIX.length()).trim();
        if (collectionName.isEmpty()) {
            throw new IllegalArgumentException("Invalid CREATE COLLECTION command: Collection name is missing.");
        }
        return new Query(QueryType.CREATE_COLLECTION, collectionName);
    }

    /**
     * Parses an 'INSERT INTO ... VALUES ...' command.
     * Expected format: INSERT INTO <collectionName> VALUES { <jsonDocument> };
     */
    private Query parseInsert(String command) throws IOException {
        int valuesIndex = command.indexOf(VALUES_KEYWORD);
        if (valuesIndex == -1) {
            throw new IllegalArgumentException("Invalid INSERT command: Missing 'VALUES' keyword.");
        }

        String collectionPart = command.substring(INSERT_INTO_PREFIX.length(), valuesIndex).trim();
        String documentData = command.substring(valuesIndex + VALUES_KEYWORD.length()).trim();

        // Basic validation for extracted parts
        if (collectionPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid INSERT command: Collection name missing.");
        }
        if (documentData.isEmpty()) {
            throw new IllegalArgumentException("Invalid INSERT command: Document data (JSON) is missing.");
        }

        // Attempt to parse documentData to ensure it's valid JSON.
        // If it's not valid, JsonUtil.fromJsonString will throw an IOException.
        JsonUtil.fromJsonString(documentData); // Just parse to validate, not storing the node here.

        return new Query(QueryType.INSERT_DOCUMENT, collectionPart, documentData);
    }

    /**
     * Parses a 'SELECT * FROM ... [WHERE ...]' command.
     * Expected formats: SELECT * FROM <collectionName>; OR SELECT * FROM <collectionName> WHERE { <jsonFilter> };
     */
    private Query parseSelect(String command) throws IOException {
        int whereIndex = command.indexOf(WHERE_KEYWORD);

        String collectionName;
        Optional<JsonNode> filter = Optional.empty();
        QueryType type;

        if (whereIndex != -1) {
            // Command includes a WHERE clause, so it's a filtered select.
            collectionName = command.substring(SELECT_FROM_PREFIX.length(), whereIndex).trim();
            String filterString = command.substring(whereIndex + WHERE_KEYWORD.length()).trim();
            if (filterString.isEmpty()) {
                throw new IllegalArgumentException("Invalid SELECT command: Empty WHERE clause provided.");
            }
            filter = Optional.of(JsonUtil.fromJsonString(filterString)); // Parse the filter JSON
            type = QueryType.SELECT_FILTERED;
        } else {
            // Command is a simple SELECT ALL.
            collectionName = command.substring(SELECT_FROM_PREFIX.length()).trim();
            type = QueryType.SELECT_ALL;
        }

        if (collectionName.isEmpty()) {
            throw new IllegalArgumentException("Invalid SELECT command: Collection name is missing.");
        }
        // For SELECT queries, documentData and updateData are not applicable.
        return new Query(type, collectionName, Optional.empty(), filter, Optional.empty());
    }

    /**
     * Parses an 'UPDATE ... SET ... WHERE ...' command.
     * Expected format: UPDATE <collectionName> SET { <jsonUpdateData> } WHERE { <jsonFilter> };
     */
    private Query parseUpdate(String command) throws IOException {
        int setIndex = command.indexOf(SET_KEYWORD);
        if (setIndex == -1) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing 'SET' keyword.");
        }

        String collectionPart = command.substring(UPDATE_PREFIX.length(), setIndex).trim();
        if (collectionPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid UPDATE command: Collection name missing.");
        }

        String remaining = command.substring(setIndex + SET_KEYWORD.length()).trim();
        int whereIndex = remaining.indexOf(WHERE_KEYWORD);
        if (whereIndex == -1) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing 'WHERE' clause. Updates must be targeted.");
        }

        String updateDataString = remaining.substring(0, whereIndex).trim();
        String filterString = remaining.substring(whereIndex + WHERE_KEYWORD.length()).trim();

        if (updateDataString.isEmpty()) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing update data (JSON for SET clause).");
        }
        if (filterString.isEmpty()) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing filter data (JSON for WHERE clause).");
        }

        JsonNode updateData = JsonUtil.fromJsonString(updateDataString); // Parse update data JSON
        JsonNode filter = JsonUtil.fromJsonString(filterString);       // Parse filter JSON

        return new Query(QueryType.UPDATE_DOCUMENT, collectionPart, Optional.empty(), Optional.of(filter), Optional.of(updateData));
    }

    /**
     * Parses a 'DELETE FROM ... [WHERE ...]' command.
     * Can be: DELETE FROM <collectionName>; (deletes collection) OR DELETE FROM <collectionName> WHERE { <jsonFilter> }; (deletes documents)
     */
    private Query parseDelete(String command) throws IOException {
        int whereIndex = command.indexOf(WHERE_KEYWORD);

        if (whereIndex == -1) {
            // This suggests a 'DELETE FROM <collectionName>;' command (delete collection).
            String collectionName = command.substring(DELETE_FROM_PREFIX.length()).trim();
            if (collectionName.isEmpty()) {
                throw new IllegalArgumentException("Invalid DELETE command: Collection name missing for collection deletion.");
            }
            return new Query(QueryType.DELETE_COLLECTION, collectionName);
        } else {
            // This is a 'DELETE FROM <collectionName> WHERE { <jsonFilter> };' command (delete documents).
            String collectionPart = command.substring(DELETE_FROM_PREFIX.length(), whereIndex).trim();
            String filterString = command.substring(whereIndex + WHERE_KEYWORD.length()).trim();

            if (collectionPart.isEmpty()) {
                throw new IllegalArgumentException("Invalid DELETE command: Collection name missing for document deletion.");
            }
            if (filterString.isEmpty()) {
                throw new IllegalArgumentException("Invalid DELETE command: Missing filter data (JSON for WHERE clause).");
            }
            JsonNode filter = JsonUtil.fromJsonString(filterString); // Parse filter JSON
            return new Query(QueryType.DELETE_DOCUMENT, collectionPart, Optional.empty(), Optional.of(filter), Optional.empty());
        }
    }
}