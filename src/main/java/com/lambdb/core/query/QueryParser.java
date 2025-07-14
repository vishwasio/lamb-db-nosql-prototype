package com.lambdb.core.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lambdb.core.DBConstants;
import com.lambdb.core.JsonUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale; // Ensure this import is present
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * QueryParser.java
 * Author: Vishwas Karode
 * Description:
 * This class is responsible for parsing LAMBQL (LAMB Query Language) command strings.
 * It now consistently converts collection names to lowercase for internal processing,
 * making collection lookups case-insensitive.
 * Supports projections and advanced filtering operators.
 */
public class QueryParser {

    // --- LAMBQL Keyword Constants (Uppercased for case-insensitive matching) ---
    private static final String CREATE_COLLECTION_KW = "CREATE COLLECTION ";
    private static final String INSERT_INTO_KW = "INSERT INTO ";
    private static final String VALUES_KW = " VALUES ";
    private static final String SELECT_KW = "SELECT ";
    private static final String FROM_KW = " FROM ";
    private static final String WHERE_KW = " WHERE ";
    private static final String UPDATE_KW = "UPDATE ";
    private static final String SET_KW = " SET ";
    private static final String DELETE_FROM_KW = "DELETE FROM ";
    private static final String SEMICOLON = ";"; // All commands must end with a semicolon

    /**
     * Parses a LAMBQL command string.
     * This is the primary method to convert a raw query string into a structured Query object.
     *
     * @param command The LAMBQL command string.
     * @return A {@link Query} object representing the parsed command.
     * @throws IllegalArgumentException If the command format is invalid or required parts are missing.
     * @throws IOException If JSON parsing within the command string fails (e.g., malformed JSON in VALUES or WHERE/SET clauses).
     */
    public Query parse(String command) throws IllegalArgumentException, IOException {
        String originalCommand = command;
        String cleanedCommand = command.trim();

        if (!cleanedCommand.endsWith(SEMICOLON)) {
            throw new IllegalArgumentException("Invalid LAMBQL command: All commands must end with a semicolon (';').");
        }
        cleanedCommand = cleanedCommand.substring(0, cleanedCommand.length() - 1).trim();

        // Convert the command body to uppercase for KEYWORD matching ONLY.
        // Collection names and JSON parts will be extracted from original casing.
        String commandForKeywordMatching = cleanedCommand.toUpperCase(Locale.ROOT);


        if (commandForKeywordMatching.startsWith(CREATE_COLLECTION_KW)) {
            // Pass original cleanedCommand to parseCreateCollection to extract name in original case, then lowercase it.
            return parseCreateCollection(cleanedCommand);
        } else if (commandForKeywordMatching.startsWith(INSERT_INTO_KW)) {
            return parseInsert(cleanedCommand);
        } else if (commandForKeywordMatching.startsWith(SELECT_KW)) {
            return parseSelect(cleanedCommand);
        } else if (commandForKeywordMatching.startsWith(UPDATE_KW)) {
            return parseUpdate(cleanedCommand);
        } else if (commandForKeywordMatching.startsWith(DELETE_FROM_KW)) {
            return parseDelete(cleanedCommand);
        } else {
            System.err.println("[QueryParser] Unknown command pattern: " + originalCommand);
            return new Query(QueryType.UNKNOWN, null, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    /**
     * Parses a 'CREATE COLLECTION' command.
     * Expected format: CREATE COLLECTION <collectionName>;
     * Now ensures collection name is lowercased for internal consistency.
     */
    private Query parseCreateCollection(String cleanedCommand) {
        String collectionName = cleanedCommand.substring(CREATE_COLLECTION_KW.length()).trim();
        if (collectionName.isEmpty()) {
            throw new IllegalArgumentException("Invalid CREATE COLLECTION command: Collection name is missing.");
        }
        // FIX: Canonicalize collection name to lowercase
        return Query.createCollectionQuery(QueryType.CREATE_COLLECTION, collectionName.toLowerCase(Locale.ROOT));
    }

    /**
     * Parses an 'INSERT INTO ... VALUES ...' command.
     * Ensures collection name is lowercased for internal consistency.
     */
    private Query parseInsert(String cleanedCommand) throws IOException {
        int valuesIndex = cleanedCommand.toUpperCase(Locale.ROOT).indexOf(VALUES_KW);
        if (valuesIndex == -1) {
            throw new IllegalArgumentException("Invalid INSERT command: Missing '" + VALUES_KW.trim() + "' keyword.");
        }

        String collectionPart = cleanedCommand.substring(INSERT_INTO_KW.length(), valuesIndex).trim();
        String documentData = cleanedCommand.substring(valuesIndex + VALUES_KW.length()).trim();

        if (collectionPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid INSERT command: Collection name missing.");
        }
        if (documentData.isEmpty()) {
            throw new IllegalArgumentException("Invalid INSERT command: Document data (JSON) is missing.");
        }

        JsonUtil.fromJsonString(documentData); // Validate JSON data

        // FIX: Canonicalize collection name to lowercase
        return Query.createInsertQuery(collectionPart.toLowerCase(Locale.ROOT), documentData);
    }

    /**
     * Parses a 'SELECT ... FROM ... [WHERE ...]' command.
     * Ensures collection name is lowercased for internal consistency.
     */
    private Query parseSelect(String cleanedCommand) throws IOException {
        int fromIndex = cleanedCommand.toUpperCase(Locale.ROOT).indexOf(FROM_KW);
        if (fromIndex == -1) {
            throw new IllegalArgumentException("Invalid SELECT command: Missing '" + FROM_KW.trim() + "' keyword.");
        }

        String projectionPart = cleanedCommand.substring(SELECT_KW.length(), fromIndex).trim();
        Optional<List<String>> projectionFields = Optional.empty();

        if (!projectionPart.equals("*")) {
            List<String> fields = Arrays.stream(projectionPart.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("Invalid SELECT command: Projection fields are empty.");
            }
            projectionFields = Optional.of(fields);
        }

        String remainingCommand = cleanedCommand.substring(fromIndex + FROM_KW.length()).trim();
        int whereIndex = remainingCommand.toUpperCase(Locale.ROOT).indexOf(WHERE_KW);

        String collectionName;
        Optional<JsonNode> filter = Optional.empty();

        if (whereIndex != -1) {
            collectionName = remainingCommand.substring(0, whereIndex).trim();
            String filterString = remainingCommand.substring(whereIndex + WHERE_KW.length()).trim();
            if (filterString.isEmpty()) {
                throw new IllegalArgumentException("Invalid SELECT command: Empty WHERE clause provided.");
            }
            filter = Optional.of(JsonUtil.fromJsonString(filterString));
        } else {
            collectionName = remainingCommand.trim();
        }

        if (collectionName.isEmpty()) {
            throw new IllegalArgumentException("Invalid SELECT command: Collection name is missing.");
        }

        // FIX: Canonicalize collection name to lowercase
        return Query.createSelectQuery(collectionName.toLowerCase(Locale.ROOT), filter, projectionFields);
    }

    /**
     * Parses an 'UPDATE ... SET ... WHERE ...' command.
     * Ensures collection name is lowercased for internal consistency.
     */
    private Query parseUpdate(String cleanedCommand) throws IOException {
        int setIndex = cleanedCommand.toUpperCase(Locale.ROOT).indexOf(SET_KW);
        if (setIndex == -1) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing '" + SET_KW.trim() + "' keyword.");
        }

        String collectionPart = cleanedCommand.substring(UPDATE_KW.length(), setIndex).trim();
        if (collectionPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid UPDATE command: Collection name missing.");
        }

        String remaining = cleanedCommand.substring(setIndex + SET_KW.length()).trim();
        int whereIndex = remaining.toUpperCase(Locale.ROOT).indexOf(WHERE_KW);
        if (whereIndex == -1) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing '" + WHERE_KW.trim() + "' clause. Updates must be targeted.");
        }

        String updateDataString = remaining.substring(0, whereIndex).trim();
        String filterString = remaining.substring(whereIndex + WHERE_KW.length()).trim();

        if (updateDataString.isEmpty()) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing update data (JSON for SET clause).");
        }
        if (filterString.isEmpty()) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing filter data (JSON for WHERE clause).");
        }

        JsonNode updateData = JsonUtil.fromJsonString(updateDataString);
        JsonNode filter = JsonUtil.fromJsonString(filterString);

        // FIX: Canonicalize collection name to lowercase
        return Query.createUpdateQuery(collectionPart.toLowerCase(Locale.ROOT), filter, updateData);
    }

    /**
     * Parses a 'DELETE FROM ... [WHERE ...]' command.
     * Ensures collection name is lowercased for internal consistency.
     */
    private Query parseDelete(String cleanedCommand) throws IOException {
        int whereIndex = cleanedCommand.toUpperCase(Locale.ROOT).indexOf(WHERE_KW);

        if (whereIndex == -1) {
            // Delete Collection
            String collectionName = cleanedCommand.substring(DELETE_FROM_KW.length()).trim();
            if (collectionName.isEmpty()) {
                throw new IllegalArgumentException("Invalid DELETE command: Collection name missing for collection deletion.");
            }
            // FIX: Canonicalize collection name to lowercase
            return Query.createCollectionQuery(QueryType.DELETE_COLLECTION, collectionName.toLowerCase(Locale.ROOT));
        } else {
            // Delete Document
            String collectionPart = cleanedCommand.substring(DELETE_FROM_KW.length(), whereIndex).trim();
            String filterString = cleanedCommand.substring(whereIndex + WHERE_KW.length()).trim();

            if (collectionPart.isEmpty()) {
                throw new IllegalArgumentException("Invalid DELETE command: Collection name missing for document deletion.");
            }
            if (filterString.isEmpty()) {
                throw new IllegalArgumentException("Invalid DELETE command: Missing filter data (JSON for WHERE clause).");
            }
            JsonNode filter = JsonUtil.fromJsonString(filterString);
            // FIX: Canonicalize collection name to lowercase
            return Query.createDeleteDocumentQuery(collectionPart.toLowerCase(Locale.ROOT), filter);
        }
    }
}