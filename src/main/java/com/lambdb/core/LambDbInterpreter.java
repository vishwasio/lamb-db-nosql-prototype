package com.lambdb.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lambdb.core.collection.CollectionManager;
import com.lambdb.core.query.Query;
import com.lambdb.core.query.QueryParser;
import com.lambdb.core.query.QueryType;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * LambDbInterpreter.java
 * Author: Vishwas Karode
 * Description:
 * This class acts as the command-line interpreter for LAMBQL queries.
 * It takes a raw LAMBQL string, parses it using {@link QueryParser},
 * and then executes the corresponding database operations.
 * This version enhances SELECT query handling to support projections,
 * ensuring only specified fields are returned to the user.
 */
public class LambDbInterpreter {

    private final DBManager dbManager;      // The core database manager instance
    private final QueryParser queryParser;  // The parser to convert strings to Query objects

    /**
     * Constructor for LambDbInterpreter.
     * @param dbManager The initialized DBManager instance to operate on.
     */
    public LambDbInterpreter(DBManager dbManager) {
        this.dbManager = dbManager;
        this.queryParser = new QueryParser();
    }

    /**
     * Executes a single LAMBQL command string.
     * This is the primary public method for the interpreter.
     * It handles parsing, command dispatch, and error reporting.
     *
     * @param commandString The LAMBQL command to execute (e.g., "SELECT name, age FROM users;").
     * @return A String containing the result of the execution (success message, query output, or error).
     */
    public String execute(String commandString) {
        try {
            // Step 1: Parse the command string into a structured Query object.
            Query query = queryParser.parse(commandString);
            System.out.println("\n[Interpreter] Executing LAMBQL command: " + commandString);

            // Step 2: Dispatch the command based on its type.
            switch (query.type()) {
                case CREATE_COLLECTION:
                    dbManager.createCollection(query.collectionName());
                    return "✅ Collection '" + query.collectionName() + "' created successfully.";

                case INSERT_DOCUMENT:
                    Optional<CollectionManager> insertCollection = dbManager.getCollection(query.collectionName());
                    if (insertCollection.isEmpty()) {
                        return "❌ Error: Collection '" + query.collectionName() + "' does not exist for insert operation.";
                    }
                    String docId = insertCollection.get().insertDocument(query.documentData().orElseThrow());
                    return "✅ Document inserted into '" + query.collectionName() + "' with _id: " + docId;

                case SELECT_ALL:
                case SELECT_FILTERED:
                    Optional<CollectionManager> selectCollection = dbManager.getCollection(query.collectionName());
                    if (selectCollection.isEmpty()) {
                        return "❌ Error: Collection '" + query.collectionName() + "' does not exist for SELECT operation.";
                    }
                    List<JsonNode> resultDocs;
                    if (query.filter().isPresent()) {
                        resultDocs = selectCollection.get().findDocuments(query.filter().get());
                    } else {
                        resultDocs = selectCollection.get().getAllDocuments();
                    }

                    // Apply projection if specified in the query
                    if (query.projectionFields().isPresent()) {
                        List<String> projections = query.projectionFields().get();
                        resultDocs = resultDocs.stream()
                                .map(doc -> applyProjection(doc, projections))
                                .collect(Collectors.toList());
                    }

                    return formatDocumentsOutput(query.collectionName(), resultDocs,
                            query.filter().isPresent() ? "Filtered" : "All",
                            query.projectionFields().isPresent());

                case UPDATE_DOCUMENT:
                    Optional<CollectionManager> updateCollection = dbManager.getCollection(query.collectionName());
                    if (updateCollection.isEmpty()) {
                        return "❌ Error: Collection '" + query.collectionName() + "' does not exist for UPDATE operation.";
                    }
                    int updatedCount = updateCollection.get().updateDocuments(query.filter().orElseThrow(), query.updateData().orElseThrow());
                    return "✅ Updated " + updatedCount + " documents in '" + query.collectionName() + "'.";

                case DELETE_DOCUMENT:
                    Optional<CollectionManager> deleteDocumentCollection = dbManager.getCollection(query.collectionName());
                    if (deleteDocumentCollection.isEmpty()) {
                        return "❌ Error: Collection '" + query.collectionName() + "' does not exist for DELETE DOCUMENT operation.";
                    }
                    int deletedDocsCount = deleteDocumentCollection.get().deleteDocuments(query.filter().orElseThrow());
                    return "✅ Deleted " + deletedDocsCount + " documents from '" + query.collectionName() + "'.";

                case DELETE_COLLECTION:
                    boolean collectionDeleted = dbManager.deleteCollection(query.collectionName());
                    return collectionDeleted ? "✅ Collection '" + query.collectionName() + "' deleted successfully." : "❌ Error: Collection '" + query.collectionName() + "' not found for deletion.";

                case UNKNOWN:
                    return "❌ Error: Unknown or unsupported LAMBQL command: '" + commandString + "'";

                default:
                    // This case should ideally not be reached if all QueryTypes are handled.
                    return "❌ Error: Unhandled query type encountered: " + query.type();
            }
        } catch (IllegalArgumentException | IOException e) {
            // Catch specific parsing or I/O errors and provide user-friendly messages.
            return "❌ Execution Error: " + e.getMessage();
        } catch (Exception e) {
            // Catch any other unexpected runtime exceptions.
            System.err.println("An unexpected internal error occurred during LAMBQL execution:");
            e.printStackTrace(); // Print full stack trace for debugging.
            return "❌ Critical Error: An unexpected issue occurred during command execution. Check console for details.";
        }
    }

    /**
     * Applies projection to a single JsonNode document.
     * Creates a new JsonNode containing only the specified fields.
     * If a field does not exist in the original document, it is skipped.
     *
     * @param originalDoc The original JsonNode document.
     * @param projectionFields A list of field names to include in the projected document.
     * @return A new JsonNode containing only the projected fields.
     */
    private JsonNode applyProjection(JsonNode originalDoc, List<String> projectionFields) {
        ObjectNode projectedNode = JsonUtil.OBJECT_MAPPER.createObjectNode(); // Create an empty mutable JSON object

        // Always include _id if it's present, regardless of projection fields, for consistency.
        if (originalDoc.has(DBConstants.ID_FIELD_NAME)) {
            projectedNode.set(DBConstants.ID_FIELD_NAME, originalDoc.get(DBConstants.ID_FIELD_NAME));
        }

        // Add requested projection fields
        for (String field : projectionFields) {
            if (originalDoc.has(field)) {
                projectedNode.set(field, originalDoc.get(field));
            }
        }
        return projectedNode;
    }

    /**
     * Helper method to format the output of retrieved documents for SELECT queries.
     * @param collectionName The name of the collection queried.
     * @param documents The list of JsonNode documents to format.
     * @param type A descriptive string (e.g., "All", "Filtered").
     * @param isProjected True if projections were applied to the documents.
     * @return A formatted string representation of the documents.
     */
    private String formatDocumentsOutput(String collectionName, List<JsonNode> documents, String type, boolean isProjected) {
        StringBuilder sb = new StringBuilder("--- ")
                .append(type)
                .append(" Documents in '")
                .append(collectionName)
                .append("' (")
                .append(documents.size())
                .append(" found)");
        if (isProjected) {
            sb.append(" (Projected)");
        }
        sb.append(" ---\n");

        if (documents.isEmpty()) {
            sb.append("  (No matching documents found)\n");
        } else {
            documents.forEach(doc -> sb.append(doc.toPrettyString()).append("\n------------------\n"));
        }
        return sb.toString();
    }
}