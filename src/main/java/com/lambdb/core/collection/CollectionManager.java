package com.lambdb.core.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lambdb.core.DBConstants;
import com.lambdb.core.JsonUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * CollectionManager.java
 * Author: Vishwas Karode
 * Description:
 * Manages the lifecycle and operations of documents within a single collection.
 * Each collection is represented as a directory on the file system, and documents
 * within it are stored as individual JSON files.
 */
public class CollectionManager {

    private final String collectionName; // The name of this collection (e.g., "users", "products")
    private final Path collectionPath;   // The file system path to this collection's directory

    /**
     * Constructor for CollectionManager.
     * @param collectionName The name of the collection.
     * @param dbRootPath The root path of the entire database.
     */
    public CollectionManager(String collectionName, Path dbRootPath) {
        this.collectionName = collectionName;
        // Resolve the full path to this collection's directory within the DB root.
        this.collectionPath = dbRootPath.resolve(collectionName);
    }

    /**
     * Initializes the collection by ensuring its corresponding directory exists on the file system.
     * If the directory doesn't exist, it will be created.
     * @return true if the directory was created or already exists, false otherwise.
     * @throws IOException If an I/O error occurs during directory creation.
     */
    public boolean init() throws IOException {
        if (!Files.exists(collectionPath)) {
            Files.createDirectories(collectionPath); // Create the directory and any necessary parent directories
            System.out.println("[CollectionManager] Collection directory created: " + collectionPath);
            return true;
        }
        System.out.println("[CollectionManager] Collection directory already exists: " + collectionPath);
        return false;
    }

    /**
     * Inserts a new document into the collection.
     * If the provided JSON string does not contain an '_id' field, a new UUID will be generated and assigned.
     * The document is then stored as a JSON file named after its '_id' in the collection's directory.
     *
     * @param documentJsonString The JSON string representing the document to insert.
     * @return The unique '_id' of the inserted document.
     * @throws IOException If an I/O error occurs during file writing.
     * @throws IllegalArgumentException If a document with the same '_id' already exists in this collection.
     */
    public String insertDocument(String documentJsonString) throws IOException {
        JsonNode documentNode = JsonUtil.fromJsonString(documentJsonString); // Parse the input JSON string
        documentNode = JsonUtil.ensureDocumentId(documentNode); // Ensure the document has a unique _id

        String docId = documentNode.get(DBConstants.ID_FIELD_NAME).asText(); // Get the _id
        // Construct the file path for the document (e.g., collection_dir/document_id.json)
        File docFile = collectionPath.resolve(docId + DBConstants.DOCUMENT_FILE_EXTENSION).toFile();

        // Prevent overwriting existing documents with insert (upsert logic would be different)
        if (docFile.exists()) {
            throw new IllegalArgumentException("Document with _id '" + docId + "' already exists in collection '" + collectionName + "'. Use update for modification.");
        }

        JsonUtil.writeToFile(documentNode, docFile); // Write the JSON document to the file
        System.out.println("[CollectionManager] Inserted document '" + docId + "' into collection '" + collectionName + "'");
        return docId;
    }

    /**
     * Retrieves a document from the collection based on its unique '_id'.
     *
     * @param documentId The '_id' of the document to retrieve.
     * @return An Optional containing the JsonNode of the document if found, otherwise an empty Optional.
     * @throws IOException If an I/O error occurs during file reading.
     */
    public Optional<JsonNode> getDocument(String documentId) throws IOException {
        // Construct the expected file path for the document
        File docFile = collectionPath.resolve(documentId + DBConstants.DOCUMENT_FILE_EXTENSION).toFile();

        if (docFile.exists() && docFile.isFile()) { // Check if the file exists and is a regular file
            System.out.println("[CollectionManager] Retrieved document '" + documentId + "' from collection '" + collectionName + "'");
            return Optional.of(JsonUtil.fromJsonFile(docFile)); // Read and parse the JSON file
        }
        System.out.println("[CollectionManager] Document '" + documentId + "' not found in collection '" + collectionName + "'");
        return Optional.empty(); // Document not found
    }

    /**
     * Retrieves all documents currently stored within this collection.
     * Iterates through all files in the collection's directory and parses them as JSON documents.
     *
     * @return A List of JsonNode objects, each representing a document in the collection.
     * @throws IOException If an I/O error occurs during directory listing or file reading.
     */
    public List<JsonNode> getAllDocuments() throws IOException {
        List<JsonNode> documents = new ArrayList<>();
        // Use Files.list for efficient directory traversal
        try (Stream<Path> paths = Files.list(collectionPath)) {
            paths.filter(Files::isRegularFile) // Only consider regular files
                    .filter(p -> p.toString().endsWith(DBConstants.DOCUMENT_FILE_EXTENSION)) // Only consider JSON files
                    .forEach(path -> {
                        try {
                            documents.add(JsonUtil.fromJsonFile(path.toFile())); // Read and add each document
                        } catch (IOException e) {
                            // Log error but continue processing other files
                            System.err.println("[CollectionManager] Error reading document file: " + path + " - " + e.getMessage());
                        }
                    });
        }
        System.out.println("[CollectionManager] Retrieved all " + documents.size() + " documents from collection '" + collectionName + "'");
        return documents;
    }

    /**
     * Updates an existing document identified by its '_id'.
     * The provided JSON string should contain the full updated document content, including the '_id'.
     *
     * @param documentId The '_id' of the document to update.
     * @param updatedDocumentJsonString The JSON string representing the new content of the document.
     * @return true if the document was found and successfully updated, false if the document was not found.
     * @throws IOException If an I/O error occurs during file writing.
     */
    public boolean updateDocument(String documentId, String updatedDocumentJsonString) throws IOException {
        File docFile = collectionPath.resolve(documentId + DBConstants.DOCUMENT_FILE_EXTENSION).toFile();

        if (docFile.exists() && docFile.isFile()) { // Ensure the document exists before attempting to update
            JsonNode newDocumentNode = JsonUtil.fromJsonString(updatedDocumentJsonString);
            // Crucially, ensure the _id in the updated content matches the target document's _id.
            // This prevents accidental _id changes during an update operation.
            ((ObjectNode) newDocumentNode).put(DBConstants.ID_FIELD_NAME, documentId);
            JsonUtil.writeToFile(newDocumentNode, docFile); // Overwrite the existing file
            System.out.println("[CollectionManager] Updated document '" + documentId + "' in collection '" + collectionName + "'");
            return true;
        }
        System.out.println("[CollectionManager] Document '" + documentId + "' not found for update in collection '" + collectionName + "'");
        return false;
    }

    /**
     * Deletes a document from the collection based on its unique '_id'.
     *
     * @param documentId The '_id' of the document to delete.
     * @return true if the document was found and successfully deleted, false if the document was not found.
     * @throws IOException If an I/O error occurs during file deletion.
     */
    public boolean deleteDocument(String documentId) throws IOException {
        File docFile = collectionPath.resolve(documentId + DBConstants.DOCUMENT_FILE_EXTENSION).toFile();

        if (docFile.exists() && docFile.isFile()) { // Ensure the document exists before attempting to delete
            boolean deleted = Files.deleteIfExists(docFile.toPath()); // Delete the file
            if (deleted) {
                System.out.println("[CollectionManager] Deleted document '" + documentId + "' from collection '" + collectionName + "'");
            }
            return deleted;
        }
        System.out.println("[CollectionManager] Document '" + documentId + "' not found for deletion in collection '" + collectionName + "'");
        return false;
    }

    /**
     * Finds documents in this collection that match the provided JSON filter criteria.
     * This implementation supports simple equality matching on top-level fields of the JSON filter.
     * For example, a filter `{"name": "Alice", "age": 30}` will find documents where both 'name' is 'Alice'
     * AND 'age' is '30'.
     *
     * @param filter The JsonNode representing the filter criteria (e.g., {"name": "Alice"}).
     * Only object nodes are supported as filters.
     * @return A list of {@link JsonNode} documents that match all criteria in the filter.
     * @throws IOException If an I/O error occurs while reading documents.
     */
    public List<JsonNode> findDocuments(JsonNode filter) throws IOException {
        List<JsonNode> matchingDocuments = new ArrayList<>();
        List<JsonNode> allDocuments = getAllDocuments(); // First, retrieve all documents from the disk.

        // If the filter itself is not a JSON object, it's an invalid filter for equality matching.
        if (!filter.isObject()) {
            System.err.println("[CollectionManager] Warning by Vishwas Karode: Find filter must be a JSON object. No documents will match.");
            return matchingDocuments; // Return empty list if filter is malformed.
        }

        // Iterate through each document found in the collection.
        for (JsonNode document : allDocuments) {
            boolean matches = true; // Assume document matches until a mismatch is found.

            // Iterate through each field in the filter JSON.
            // All fields in the filter must match corresponding fields in the document.
            for (java.util.Iterator<String> fieldNames = filter.fieldNames(); fieldNames.hasNext(); ) {
                String fieldName = fieldNames.next();
                // Check if the document has the field AND if the value of that field matches the filter's value.
                if (document.has(fieldName) && document.get(fieldName).equals(filter.get(fieldName))) {
                    // This field matches, continue to the next filter field.
                } else {
                    // This field does not match, so the entire document does not match the filter.
                    matches = false;
                    break; // No need to check other fields for this document.
                }
            }

            // If all filter fields matched, add the document to our results.
            if (matches) {
                matchingDocuments.add(document);
            }
        }
        System.out.println("[CollectionManager] Found " + matchingDocuments.size() + " documents matching filter in collection '" + collectionName + "'");
        return matchingDocuments;
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
     */
    public int updateDocuments(JsonNode filter, JsonNode updateData) throws IOException {
        int updatedCount = 0;
        // Step 1: Find all documents that need to be updated.
        List<JsonNode> documentsToUpdate = findDocuments(filter);

        if (!updateData.isObject()) {
            System.err.println("[CollectionManager] Warning by Vishwas Karode: Update data must be a JSON object. No updates applied.");
            return 0; // Return 0 if updateData is malformed.
        }

        // Step 2: Iterate through each matching document and apply updates.
        for (JsonNode document : documentsToUpdate) {
            String docId = document.get(DBConstants.ID_FIELD_NAME).asText(); // Get the unique ID of the document.
            // Cast the JsonNode to an ObjectNode to allow modification (Jackson's tree model).
            ObjectNode mutableDocument = (ObjectNode) document;

            // Apply updates: Iterate through fields in updateData and set them in the document.
            // This performs a shallow merge (top-level fields only).
            for (java.util.Iterator<String> fieldNames = updateData.fieldNames(); fieldNames.hasNext(); ) {
                String fieldName = fieldNames.next();
                mutableDocument.set(fieldName, updateData.get(fieldName));
            }

            // Step 3: Write the modified document back to its file, overwriting the old content.
            // Reusing updateDocument method, which handles the file writing and logging.
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
     */
    public int deleteDocuments(JsonNode filter) throws IOException {
        int deletedCount = 0;
        // Step 1: Find all documents that need to be deleted.
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
     * Returns the name of this collection.
     * @return The collection name.
     */
    public String getCollectionName() {
        return collectionName;
    }
}