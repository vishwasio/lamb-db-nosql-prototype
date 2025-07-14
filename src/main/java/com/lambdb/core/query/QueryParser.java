package com.lambdb.core.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lambdb.core.DBConstants; // Import DBConstants for operator names
import com.lambdb.core.JsonUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * QueryParser.java
 * Author: Vishwas Karode
 * Description:
 * This class is responsible for parsing LAMBQL (LAMB Query Language) command strings.
 * It now supports enhanced query capabilities:
 * - Projections: `SELECT field1, field2 FROM collection;`
 * - Advanced Filter Operators: `$gt`, `$lt`, `$ne`, `$in` within `WHERE` clauses.
 * It takes a raw string input, validates its basic structure, extracts keywords and data,
 * and converts it into a structured {@link Query} object using static factory methods.
 */
public class QueryParser {

    // --- LAMBQL Keyword Constants (Uppercased for case-insensitive matching) ---
    private static final String CREATE_COLLECTION_KW = "CREATE COLLECTION ";
    private static final String INSERT_INTO_KW = "INSERT INTO ";
    private static final String VALUES_KW = " VALUES ";
    private static final String SELECT_KW = "SELECT "; // Changed from SELECT * FROM
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
        String originalCommand = command; // Keep original for error messages
        String cleanedCommand = command.trim();

        if (!cleanedCommand.endsWith(SEMICOLON)) {
            throw new IllegalArgumentException("Invalid LAMBQL command by Vishwas Karode: All commands must end with a semicolon (';').");
        }
        cleanedCommand = cleanedCommand.substring(0, cleanedCommand.length() - 1).trim(); // Remove semicolon

        // Convert the command body to uppercase for keyword matching, but be careful with JSON parts.
        // We will process JSON parts after extracting them.
        String commandForKeywordMatching = cleanedCommand.toUpperCase(Locale.ROOT);


        if (commandForKeywordMatching.startsWith(CREATE_COLLECTION_KW)) {
            return parseCreateCollection(commandForKeywordMatching);
        } else if (commandForKeywordMatching.startsWith(INSERT_INTO_KW)) {
            // INSERT command's VALUES part contains JSON, so use original cleanedCommand
            return parseInsert(cleanedCommand);
        } else if (commandForKeywordMatching.startsWith(SELECT_KW)) {
            // SELECT command's WHERE part contains JSON, so use original cleanedCommand
            return parseSelect(cleanedCommand);
        } else if (commandForKeywordMatching.startsWith(UPDATE_KW)) {
            // UPDATE command's SET and WHERE parts contain JSON, so use original cleanedCommand
            return parseUpdate(cleanedCommand);
        } else if (commandForKeywordMatching.startsWith(DELETE_FROM_KW)) {
            // DELETE command's WHERE part contains JSON, so use original cleanedCommand
            return parseDelete(cleanedCommand);
        } else {
            System.err.println("[QueryParser] Unknown command pattern by Vishwas Karode: " + originalCommand);
            return new Query(QueryType.UNKNOWN, null, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    /**
     * Parses a 'CREATE COLLECTION' command.
     * Expected format: CREATE COLLECTION <collectionName>;
     * Uses Query.createCollectionQuery() static factory method.
     */
    private Query parseCreateCollection(String commandForKeywordMatching) {
        String collectionName = commandForKeywordMatching.substring(CREATE_COLLECTION_KW.length()).trim();
        if (collectionName.isEmpty()) {
            throw new IllegalArgumentException("Invalid CREATE COLLECTION command: Collection name is missing.");
        }
        return Query.createCollectionQuery(QueryType.CREATE_COLLECTION, collectionName);
    }

    /**
     * Parses an 'INSERT INTO ... VALUES ...' command.
     * Expected format: INSERT INTO <collectionName> VALUES { <jsonDocument> };
     * Uses Query.createInsertQuery() static factory method.
     */
    private Query parseInsert(String cleanedCommand) throws IOException {
        // Find the index of " VALUES "
        int valuesIndex = cleanedCommand.toUpperCase(Locale.ROOT).indexOf(VALUES_KW);
        if (valuesIndex == -1) {
            throw new IllegalArgumentException("Invalid INSERT command by Vishwas Karode: Missing '" + VALUES_KW.trim() + "' keyword.");
        }

        String collectionPart = cleanedCommand.substring(INSERT_INTO_KW.length(), valuesIndex).trim();
        String documentData = cleanedCommand.substring(valuesIndex + VALUES_KW.length()).trim();

        if (collectionPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid INSERT command: Collection name missing.");
        }
        if (documentData.isEmpty()) {
            throw new IllegalArgumentException("Invalid INSERT command: Document data (JSON) is missing.");
        }

        // Validate JSON data for insert
        JsonUtil.fromJsonString(documentData);

        return Query.createInsertQuery(collectionPart, documentData);
    }

    /**
     * Parses a 'SELECT ... FROM ... [WHERE ...]' command.
     * Expected formats:
     * - SELECT * FROM <collectionName>;
     * - SELECT field1, field2 FROM <collectionName>;
     * - SELECT * FROM <collectionName> WHERE { <jsonFilter> };
     * - SELECT field1, field2 FROM <collectionName> WHERE { <jsonFilter> };
     * Uses Query.createSelectQuery() static factory method.
     */
    private Query parseSelect(String cleanedCommand) throws IOException {
        int fromIndex = cleanedCommand.toUpperCase(Locale.ROOT).indexOf(FROM_KW);
        if (fromIndex == -1) {
            throw new IllegalArgumentException("Invalid SELECT command by Vishwas Karode: Missing '" + FROM_KW.trim() + "' keyword.");
        }

        // Extract projection fields part (between "SELECT " and " FROM ")
        String projectionPart = cleanedCommand.substring(SELECT_KW.length(), fromIndex).trim();
        Optional<List<String>> projectionFields = Optional.empty();

        if (!projectionPart.equals("*")) {
            // Parse comma-separated fields for projection
            List<String> fields = Arrays.stream(projectionPart.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("Invalid SELECT command: Projection fields are empty.");
            }
            projectionFields = Optional.of(fields);
        }

        // Extract the part after " FROM " to find collection name and WHERE clause
        String remainingCommand = cleanedCommand.substring(fromIndex + FROM_KW.length()).trim();
        int whereIndex = remainingCommand.toUpperCase(Locale.ROOT).indexOf(WHERE_KW);

        String collectionName;
        Optional<JsonNode> filter = Optional.empty();

        if (whereIndex != -1) {
            // Command includes a WHERE clause
            collectionName = remainingCommand.substring(0, whereIndex).trim();
            String filterString = remainingCommand.substring(whereIndex + WHERE_KW.length()).trim();
            if (filterString.isEmpty()) {
                throw new IllegalArgumentException("Invalid SELECT command: Empty WHERE clause provided.");
            }
            filter = Optional.of(JsonUtil.fromJsonString(filterString)); // Parse the filter JSON
        } else {
            // No WHERE clause, simply extract collection name
            collectionName = remainingCommand.trim();
        }

        if (collectionName.isEmpty()) {
            throw new IllegalArgumentException("Invalid SELECT command: Collection name is missing.");
        }

        return Query.createSelectQuery(collectionName, filter, projectionFields);
    }

    /**
     * Parses an 'UPDATE ... SET ... WHERE ...' command.
     * Expected format: UPDATE <collectionName> SET { <jsonUpdateData> } WHERE { <jsonFilter> };
     * Uses Query.createUpdateQuery() static factory method.
     */
    private Query parseUpdate(String cleanedCommand) throws IOException {
        int setIndex = cleanedCommand.toUpperCase(Locale.ROOT).indexOf(SET_KW);
        if (setIndex == -1) {
            throw new IllegalArgumentException("Invalid UPDATE command by Vishwas Karode: Missing '" + SET_KW.trim() + "' keyword.");
        }

        String collectionPart = cleanedCommand.substring(UPDATE_KW.length(), setIndex).trim();
        if (collectionPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid UPDATE command: Collection name missing.");
        }

        String remaining = cleanedCommand.substring(setIndex + SET_KW.length()).trim();
        int whereIndex = remaining.toUpperCase(Locale.ROOT).indexOf(WHERE_KW);
        if (whereIndex == -1) {
            throw new IllegalArgumentException("Invalid UPDATE command by Vishwas Karode: Missing '" + WHERE_KW.trim() + "' clause. Updates must be targeted.");
        }

        String updateDataString = remaining.substring(0, whereIndex).trim();
        String filterString = remaining.substring(whereIndex + WHERE_KW.length()).trim();

        if (updateDataString.isEmpty()) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing update data (JSON for SET clause).");
        }
        if (filterString.isEmpty()) {
            throw new IllegalArgumentException("Invalid UPDATE command: Missing filter data (JSON for WHERE clause).");
        }

        JsonNode updateData = JsonUtil.fromJsonString(updateDataString); // Parse update data JSON
        JsonNode filter = JsonUtil.fromJsonString(filterString);       // Parse filter JSON

        return Query.createUpdateQuery(collectionPart, filter, updateData);
    }

    /**
     * Parses a 'DELETE FROM ... [WHERE ...]' command.
     * Can be: DELETE FROM <collectionName>; (deletes collection) OR DELETE FROM <collectionName> WHERE { <jsonFilter> }; (deletes documents).
     * Uses Query.createCollectionQuery() or Query.createDeleteDocumentQuery() static factory methods.
     */
    private Query parseDelete(String cleanedCommand) throws IOException {
        int whereIndex = cleanedCommand.toUpperCase(Locale.ROOT).indexOf(WHERE_KW);

        if (whereIndex == -1) {
            // This suggests a 'DELETE FROM <collectionName>;' command (delete collection).
            String collectionName = cleanedCommand.substring(DELETE_FROM_KW.length()).trim();
            if (collectionName.isEmpty()) {
                throw new IllegalArgumentException("Invalid DELETE command: Collection name missing for collection deletion.");
            }
            return Query.createCollectionQuery(QueryType.DELETE_COLLECTION, collectionName);
        } else {
            // This is a 'DELETE FROM <collectionName> WHERE { <jsonFilter> };' command (delete documents).
            String collectionPart = cleanedCommand.substring(DELETE_FROM_KW.length(), whereIndex).trim();
            String filterString = cleanedCommand.substring(whereIndex + WHERE_KW.length()).trim();

            if (collectionPart.isEmpty()) {
                throw new IllegalArgumentException("Invalid DELETE command: Collection name missing for document deletion.");
            }
            if (filterString.isEmpty()) {
                throw new IllegalArgumentException("Invalid DELETE command: Missing filter data (JSON for WHERE clause).");
            }
            JsonNode filter = JsonUtil.fromJsonString(filterString); // Parse filter JSON
            return Query.createDeleteDocumentQuery(collectionPart, filter);
        }
    }
}