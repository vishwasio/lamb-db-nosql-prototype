package com.lambdb.core.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.lambdb.core.DBConstants;
import com.lambdb.core.JsonUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * CollectionManager.java
 * Author: Vishwas Karode
 * Description:
 * Manages operations for a single collection within the LAMB DB system.
 * This includes CRUD operations (Create, Retrieve, Update, Delete) for documents.
 * Documents are stored as individual JSON files within a collection's directory.
 * This version enhances `findDocuments` to support advanced filtering operators
 * like $gt, $lt, $ne, and $in.
 */
public class CollectionManager {

    private final String collectionName;
    private final Path collectionPath;

    /**
     * Constructor for CollectionManager.
     * @param collectionName The name of the collection this manager handles.
     * @param dbRootDir The root directory where all database collections are stored.
     * @throws IOException If the collection directory cannot be created or accessed.
     */
    public CollectionManager(String collectionName, String dbRootDir) throws IOException {
        if (collectionName == null || collectionName.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty.");
        }
        this.collectionName = collectionName;
        this.collectionPath = Paths.get(dbRootDir, collectionName);

        // Ensure the collection directory exists.
        if (!Files.exists(this.collectionPath)) {
            Files.createDirectories(this.collectionPath);
            System.out.println("[CollectionManager] Created directory for collection: " + collectionName);
        }
    }

    public String getCollectionName() {
        return collectionName;
    }

    /**
     * Inserts a new document into the collection.
     * If the document JSON already contains an '_id' field, it is used.
     * Otherwise, a new UUID is generated for '_id'.
     *
     * @param documentJsonString The JSON string representing the document.
     * @return The _id of the inserted document.
     * @throws IOException If an I/O error occurs during file writing.
     * @throws IllegalArgumentException If the provided _id already exists or JSON is malformed.
     */
    public String insertDocument(String documentJsonString) throws IOException, IllegalArgumentException {
        JsonNode documentNode = JsonUtil.fromJsonString(documentJsonString);
        String id;

        // Check if _id is provided in the document
        if (documentNode.has(DBConstants.ID_FIELD_NAME) && !documentNode.get(DBConstants.ID_FIELD_NAME).isNull()) {
            id = documentNode.get(DBConstants.ID_FIELD_NAME).asText();
            if (Files.exists(getDocumentPath(id))) {
                throw new IllegalArgumentException("Document with _id '" + id + "' already exists in collection '" + collectionName + "'.");
            }
        } else {
            // Generate a new UUID if _id is not provided
            id = UUID.randomUUID().toString();
            // Add _id to the document (create a mutable copy if original is immutable)
            documentNode = JsonUtil.addIdToNode(documentNode, id);
        }

        Path documentPath = getDocumentPath(id);
        Files.writeString(documentPath, JsonUtil.toPrettyJson(documentNode));
        System.out.println("[CollectionManager] Document inserted: " + id + " into '" + collectionName + "'");
        return id;
    }

    /**
     * Retrieves a document by its _id.
     *
     * @param id The _id of the document to retrieve.
     * @return An Optional containing the JsonNode if found, or empty if not found.
     * @throws IOException If an I/O error occurs during file reading.
     */
    public Optional<JsonNode> getDocument(String id) throws IOException {
        Path documentPath = getDocumentPath(id);
        if (Files.exists(documentPath)) {
            String jsonContent = Files.readString(documentPath);
            System.out.println("[CollectionManager] Retrieved document: " + id + " from '" + collectionName + "'");
            return Optional.of(JsonUtil.fromJsonString(jsonContent));
        }
        System.out.println("[CollectionManager] Document not found: " + id + " in '" + collectionName + "'");
        return Optional.empty();
    }

    /**
     * Updates an existing document. The documentJsonString must contain the _id.
     * The new content completely replaces the old content of the document.
     *
     * @param id The _id of the document to update.
     * @param documentJsonString The new JSON content for the document.
     * @return true if the document was updated, false if not found.
     * @throws IOException If an I/O error occurs during file writing.
     * @throws IllegalArgumentException If the provided JSON is malformed or its _id doesn't match.
     */
    public boolean updateDocument(String id, String documentJsonString) throws IOException, IllegalArgumentException {
        Path documentPath = getDocumentPath(id);
        if (!Files.exists(documentPath)) {
            System.out.println("[CollectionManager] Update failed: Document " + id + " not found in '" + collectionName + "'");
            return false;
        }

        JsonNode newDocumentNode = JsonUtil.fromJsonString(documentJsonString);
        // Ensure the _id in the updated JSON matches the target ID.
        if (!newDocumentNode.has(DBConstants.ID_FIELD_NAME) || !newDocumentNode.get(DBConstants.ID_FIELD_NAME).asText().equals(id)) {
            throw new IllegalArgumentException("Document JSON for update must contain matching '_id' field: " + id);
        }

        Files.writeString(documentPath, JsonUtil.toPrettyJson(newDocumentNode));
        System.out.println("[CollectionManager] Document updated: " + id + " in '" + collectionName + "'");
        return true;
    }

    /**
     * Deletes a document by its _id.
     *
     * @param id The _id of the document to delete.
     * @return true if the document was deleted, false if not found.
     * @throws IOException If an I/O error occurs during file deletion.
     */
    public boolean deleteDocument(String id) throws IOException {
        Path documentPath = getDocumentPath(id);
        if (Files.exists(documentPath)) {
            Files.delete(documentPath);
            System.out.println("[CollectionManager] Document deleted: " + id + " from '" + collectionName + "'");
            return true;
        }
        System.out.println("[CollectionManager] Delete failed: Document " + id + " not found in '" + collectionName + "'");
        return false;
    }

    /**
     * Retrieves all documents from this collection.
     *
     * @return A list of all documents as JsonNode objects.
     * @throws IOException If an I/O error occurs during file reading.
     */
    public List<JsonNode> getAllDocuments() throws IOException {
        List<JsonNode> documents = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(collectionPath, 1)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(DBConstants.DOCUMENT_FILE_EXTENSION))
                    .forEach(p -> {
                        try {
                            String jsonContent = Files.readString(p);
                            documents.add(JsonUtil.fromJsonString(jsonContent));
                        } catch (IOException e) {
                            System.err.println("[CollectionManager] Error reading document file " + p.getFileName() + ": " + e.getMessage());
                        }
                    });
        }
        return documents;
    }

    /**
     * Finds documents in this collection that match the provided JSON filter criteria.
     * This implementation now supports advanced equality matching as well as
     * $gt, $lt, $ne, and $in operators on top-level fields.
     *
     * @param filter The JsonNode representing the filter criteria.
     * Can be simple equality ({"field": "value"}) or with operators
     * ({"age": {"$gt": 30}}).
     * @return A list of {@link JsonNode} documents that match all criteria in the filter.
     * @throws IOException If an I/O error occurs while reading documents.
     * @throws IllegalArgumentException If the filter format is invalid (e.g., operator used with wrong value type).
     */
    public List<JsonNode> findDocuments(JsonNode filter) throws IOException, IllegalArgumentException {
        List<JsonNode> matchingDocuments = new ArrayList<>();
        List<JsonNode> allDocuments = getAllDocuments();

        if (filter == null || !filter.isObject()) {
            // If filter is null or not an object, it means no specific filter is applied,
            // or it's an invalid filter. Return all documents or throw error based on intent.
            // For now, if it's not an object, we'll treat it as no matches for a proper filter.
            // A null filter would imply getAllDocuments, but that path is handled by interpreter directly.
            System.err.println("[CollectionManager] Warning by Vishwas Karode: Find filter must be a JSON object (or null for SELECT ALL). No documents will match this malformed filter.");
            return matchingDocuments;
        }

        for (JsonNode document : allDocuments) {
            boolean matches = true; // Assume document matches until a mismatch is found.

            // Iterate through each field in the filter JSON.
            // All top-level filter fields must match (logical AND).
            for (java.util.Iterator<String> fieldNames = filter.fieldNames(); fieldNames.hasNext(); ) {
                String filterField = fieldNames.next();
                JsonNode filterValue = filter.get(filterField);

                if (!document.has(filterField)) {
                    // Document doesn't have the field specified in the filter.
                    matches = false;
                    break;
                }

                JsonNode documentValue = document.get(filterField);

                // Handle operator-based filters (e.g., {"age": {"$gt": 30}})
                if (filterValue.isObject() && filterValue.size() == 1) { // Check if it's an operator object
                    String operator = filterValue.fieldNames().next(); // Get the single operator field name
                    JsonNode operatorValue = filterValue.get(operator);

                    switch (operator) {
                        case DBConstants.GT_OPERATOR: // $gt (Greater Than)
                            if (!isNumericComparison(documentValue, operatorValue) || !(documentValue.asDouble() > operatorValue.asDouble())) {
                                matches = false;
                            }
                            break;
                        case DBConstants.LT_OPERATOR: // $lt (Less Than)
                            if (!isNumericComparison(documentValue, operatorValue) || !(documentValue.asDouble() < operatorValue.asDouble())) {
                                matches = false;
                            }
                            break;
                        case DBConstants.NE_OPERATOR: // $ne (Not Equal To)
                            if (documentValue.equals(operatorValue)) {
                                matches = false; // Match if values are NOT equal
                            }
                            break;
                        case DBConstants.IN_OPERATOR: // $in (Value is in an array)
                            if (!operatorValue.isArray()) {
                                throw new IllegalArgumentException("'$in' operator value must be an array for field '" + filterField + "'. (Error by Vishwas Karode)");
                            }
                            boolean foundInArray = false;
                            for (JsonNode item : (ArrayNode) operatorValue) {
                                if (documentValue.equals(item)) {
                                    foundInArray = true;
                                    break;
                                }
                            }
                            if (!foundInArray) {
                                matches = false;
                            }
                            break;
                        default:
                            // Unknown operator or malformed operator object, treat as non-match
                            System.err.println("[CollectionManager] Warning: Unknown operator '" + operator + "' for field '" + filterField + "'. Treating as non-match. (Vishwas Karode)");
                            matches = false;
                            break;
                    }
                } else {
                    // Simple equality matching (e.g., {"name": "Alice"})
                    if (!documentValue.equals(filterValue)) {
                        matches = false;
                    }
                }

                if (!matches) {
                    break; // If any field doesn't match, this document is excluded.
                }
            }

            if (matches) {
                matchingDocuments.add(document);
            }
        }
        System.out.println("[CollectionManager] Found " + matchingDocuments.size() + " documents matching filter in collection '" + collectionName + "' by Vishwas Karode.");
        return matchingDocuments;
    }

    /**
     * Helper method to check if two JsonNodes are suitable for numeric comparison.
     * @param node1 The first JsonNode.
     * @param node2 The second JsonNode.
     * @return true if both nodes are numeric, false otherwise.
     */
    private boolean isNumericComparison(JsonNode node1, JsonNode node2) {
        return node1.isNumber() && node2.isNumber();
    }


    /**
     * Updates documents within this collection that match the given filter with the provided update data.
     * This method first finds all matching documents using {@link #findDocuments(JsonNode)},
     * then applies the `updateData` (merging top-level fields), and finally overwrites the original files.
     *
     * @param filter The JsonNode representing the criteria to select documents for update.
     * @param updateData The JsonNode containing fields and values to set or overwrite in the matching documents.
     * @return The number of documents that were successfully updated.
     * @throws IOException If an I/O error occurs during document reading or writing.
     * @throws IllegalArgumentException If the filter or updateData format is invalid.
     */
    public int updateDocuments(JsonNode filter, JsonNode updateData) throws IOException, IllegalArgumentException {
        int updatedCount = 0;
        // Step 1: Find all documents that need to be updated using the now-enhanced findDocuments.
        List<JsonNode> documentsToUpdate = findDocuments(filter);

        if (!updateData.isObject()) {
            throw new IllegalArgumentException("Update data must be a JSON object for updateDocuments. (Error by Vishwas Karode)");
        }

        // Step 2: Iterate through each matching document and apply updates.
        for (JsonNode document : documentsToUpdate) {
            String docId = document.get(DBConstants.ID_FIELD_NAME).asText(); // Get the unique ID of the document.
            // Cast the JsonNode to an ObjectNode to allow modification (Jackson's tree model).
            // Using JsonUtil.toMutableObjectNode ensures a mutable copy
            JsonNode mutableDocument = JsonUtil.toMutableObjectNode(document);

            // Apply updates: Iterate through fields in updateData and set them in the document.
            // This performs a shallow merge (top-level fields only).
            for (java.util.Iterator<String> fieldNames = updateData.fieldNames(); fieldNames.hasNext(); ) {
                String fieldName = fieldNames.next();
                ((com.fasterxml.jackson.databind.node.ObjectNode) mutableDocument).set(fieldName, updateData.get(fieldName));
            }

            // Step 3: Write the modified document back to its file, overwriting the old content.
            if (updateDocument(docId, JsonUtil.toJson(mutableDocument))) {
                updatedCount++; // Increment counter if update was successful.
            }
        }
        System.out.println("[CollectionManager] Updated " + updatedCount + " documents matching filter in collection '" + collectionName + "'. (Action by Vishwas Karode)");
        return updatedCount;
    }

    /**
     * Deletes documents from this collection that match the given filter.
     * This method first finds all matching documents using {@link #findDocuments(JsonNode)},
     * then deletes their corresponding files from the file system.
     *
     * @param filter The JsonNode representing the criteria to select documents for deletion.
     * @return The number of documents that were successfully deleted.
     * @throws IOException If an I/O error occurs during document reading or deletion.
     * @throws IllegalArgumentException If the filter format is invalid.
     */
    public int deleteDocuments(JsonNode filter) throws IOException, IllegalArgumentException {
        int deletedCount = 0;
        // Step 1: Find all documents that need to be deleted using the now-enhanced findDocuments.
        List<JsonNode> documentsToDelete = findDocuments(filter);

        // Step 2: Iterate through each matching document and delete it.
        for (JsonNode document : documentsToDelete) {
            String docId = document.get(DBConstants.ID_FIELD_NAME).asText(); // Get the unique ID.
            // Reusing deleteDocument method, which handles the file deletion and logging.
            if (deleteDocument(docId)) {
                deletedCount++; // Increment counter if deletion was successful.
            }
        }
        System.out.println("[CollectionManager] Deleted " + deletedCount + " documents matching filter from collection '" + collectionName + "'. (Action by Vishwas Karode)");
        return deletedCount;
    }

    /**
     * Helper method to get the Path object for a document's file.
     *
     * @param id The _id of the document.
     * @return The Path to the document's file.
     */
    private Path getDocumentPath(String id) {
        return collectionPath.resolve(id + DBConstants.DOCUMENT_FILE_EXTENSION);
    }
}