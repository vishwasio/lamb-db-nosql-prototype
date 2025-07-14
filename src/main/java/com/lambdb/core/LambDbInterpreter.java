package com.lambdb.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.lambdb.core.collection.CollectionManager;
import com.lambdb.core.query.Query;
import com.lambdb.core.query.QueryParser;
import com.lambdb.core.query.QueryType;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * LambDbInterpreter.java
 * Author: Vishwas Karode
 * Description:
 * This class acts as the command-line interpreter for LAMBQL queries.
 * It takes a raw LAMBQL string, parses it using {@link QueryParser},
 * and then executes the corresponding database operations by interacting
 * with the {@link DBManager} and {@link CollectionManager}.
 * It provides a higher-level interface for users compared to direct Java API calls.
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
     * @param commandString The LAMBQL command to execute (e.g., "SELECT * FROM users;").
     * @return A String containing the result of the execution (success message, query output, or error).
     */
    public String execute(String commandString) {
        try {
            // Step 1: Parse the command string into a structured Query object.
            Query query = queryParser.parse(commandString);
            System.out.println("\n[Interpreter] Vishwas Karode executing LAMBQL command: " + commandString);

            // Step 2: Dispatch the command based on its type.
            switch (query.type()) {
                case CREATE_COLLECTION:
                    dbManager.createCollection(query.collectionName());
                    return "✅ Collection '" + query.collectionName() + "' created successfully.";

                case INSERT_DOCUMENT:
                    Optional<CollectionManager> insertCollection = dbManager.getCollection(query.collectionName());
                    if (insertCollection.isEmpty()) {
                        return "❌ Error by Vishwas Karode: Collection '" + query.collectionName() + "' does not exist for insert operation.";
                    }
                    String docId = insertCollection.get().insertDocument(query.documentData().orElseThrow());
                    return "✅ Document inserted into '" + query.collectionName() + "' with _id: " + docId;

                case SELECT_ALL:
                    Optional<CollectionManager> selectCollectionAll = dbManager.getCollection(query.collectionName());
                    if (selectCollectionAll.isEmpty()) {
                        return "❌ Error by Vishwas Karode: Collection '" + query.collectionName() + "' does not exist for SELECT ALL operation.";
                    }
                    List<JsonNode> allDocs = selectCollectionAll.get().getAllDocuments();
                    return formatDocumentsOutput(query.collectionName(), allDocs, "All");

                case SELECT_FILTERED:
                    Optional<CollectionManager> selectCollectionFiltered = dbManager.getCollection(query.collectionName());
                    if (selectCollectionFiltered.isEmpty()) {
                        return "❌ Error by Vishwas Karode: Collection '" + query.collectionName() + "' does not exist for SELECT FILTERED operation.";
                    }
                    List<JsonNode> filteredDocs = selectCollectionFiltered.get().findDocuments(query.filter().orElseThrow());
                    return formatDocumentsOutput(query.collectionName(), filteredDocs, "Filtered");

                case UPDATE_DOCUMENT:
                    Optional<CollectionManager> updateCollection = dbManager.getCollection(query.collectionName());
                    if (updateCollection.isEmpty()) {
                        return "❌ Error by Vishwas Karode: Collection '" + query.collectionName() + "' does not exist for UPDATE operation.";
                    }
                    int updatedCount = updateCollection.get().updateDocuments(query.filter().orElseThrow(), query.updateData().orElseThrow());
                    return "✅ Updated " + updatedCount + " documents in '" + query.collectionName() + "'.";

                case DELETE_DOCUMENT:
                    Optional<CollectionManager> deleteDocumentCollection = dbManager.getCollection(query.collectionName());
                    if (deleteDocumentCollection.isEmpty()) {
                        return "❌ Error by Vishwas Karode: Collection '" + query.collectionName() + "' does not exist for DELETE DOCUMENT operation.";
                    }
                    int deletedDocsCount = deleteDocumentCollection.get().deleteDocuments(query.filter().orElseThrow());
                    return "✅ Deleted " + deletedDocsCount + " documents from '" + query.collectionName() + "'.";

                case DELETE_COLLECTION:
                    boolean collectionDeleted = dbManager.deleteCollection(query.collectionName());
                    return collectionDeleted ? "✅ Collection '" + query.collectionName() + "' deleted successfully." : "❌ Error by Vishwas Karode: Collection '" + query.collectionName() + "' not found for deletion.";

                case UNKNOWN:
                    return "❌ Error by Vishwas Karode: Unknown or unsupported LAMBQL command: '" + commandString + "'";

                default:
                    // This case should ideally not be reached if all QueryTypes are handled.
                    return "❌ Error by Vishwas Karode: Unhandled query type encountered: " + query.type();
            }
        } catch (IllegalArgumentException | IOException e) {
            // Catch specific parsing or I/O errors and provide user-friendly messages.
            return "❌ Execution Error by Vishwas Karode: " + e.getMessage();
        } catch (Exception e) {
            // Catch any other unexpected runtime exceptions.
            System.err.println("An unexpected internal error occurred during LAMBQL execution by Vishwas Karode:");
            e.printStackTrace(); // Print full stack trace for debugging.
            return "❌ Critical Error: An unexpected issue occurred during command execution. Check console for details.";
        }
    }

    /**
     * Helper method to format the output of retrieved documents for SELECT queries.
     * @param collectionName The name of the collection queried.
     * @param documents The list of JsonNode documents to format.
     * @param type A descriptive string (e.g., "All", "Filtered").
     * @return A formatted string representation of the documents.
     */
    private String formatDocumentsOutput(String collectionName, List<JsonNode> documents, String type) {
        StringBuilder sb = new StringBuilder("--- " + type + " Documents in '" + collectionName + "' (" + documents.size() + " found) ---\n");
        if (documents.isEmpty()) {
            sb.append("  (No matching documents found by Vishwas Karode)\n");
        } else {
            documents.forEach(doc -> sb.append(doc.toPrettyString()).append("\n------------------\n"));
        }
        return sb.toString();
    }
}