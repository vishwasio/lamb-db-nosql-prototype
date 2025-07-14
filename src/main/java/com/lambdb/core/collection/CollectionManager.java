package com.lambdb.core.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode; // <--- FIX 1: ADDED THIS IMPORT
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lambdb.core.DBConstants;
import com.lambdb.core.JsonUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CollectionManager.java
 * Author: Vishwas Karode
 * Date: July 14, 2025
 * Description:
 * Manages a single collection of documents on the file system. Each document is stored as a separate JSON file.
 * This version introduces basic in-memory hash-based indexing for specified fields, which are also persisted.
 * This significantly improves the performance of filtered queries on indexed fields.
 */
public class CollectionManager {

    private final String collectionName;
    private final Path collectionPath; // Absolute path to the collection directory
    private final Path dbRootPath;     // Absolute path to the database root directory

    // In-memory cache for document data (simple for now, could be LRU for larger scale)
    private final Map<String, JsonNode> documentCache = new ConcurrentHashMap<>();

    // NEW: In-memory indexes.
    // Outer Map: fieldName -> Inner Map
    // Inner Map: fieldValue -> Set of document _ids that have this field value
    private final Map<String, Map<String, Set<String>>> fieldIndexes = new ConcurrentHashMap<>();

    // NEW: List of fields that should be indexed for this collection
    // For this prototype, these are hardcoded. In a real DB, this would be user-defined (CREATE INDEX command).
    private static final Map<String, List<String>> INDEXED_FIELDS_BY_COLLECTION = Map.of(
            "users", Arrays.asList("name", "age", "city", "status"),
            "products", Arrays.asList("productName", "price", "category"),
            "logs", Arrays.asList("timestamp", "event", "userId")
    );


    /**
     * Constructor for CollectionManager.
     * Initializes the collection, ensuring its directory exists and loading existing documents and indexes.
     *
     * @param collectionName The name of the collection.
     * @param dbRootDir The string path to the database root directory.
     * @throws IOException If an I/O error occurs during directory creation or loading.
     */
    public CollectionManager(String collectionName, String dbRootDir) throws IOException {
        this.collectionName = collectionName;
        this.dbRootPath = Paths.get(dbRootDir);
        this.collectionPath = dbRootPath.resolve(collectionName); // Construct path for this collection

        // Ensure collection directory exists
        if (!Files.exists(this.collectionPath)) {
            Files.createDirectories(this.collectionPath);
            System.out.println("[CollectionManager] Created directory for collection: " + collectionName.toUpperCase(Locale.ROOT));
        } else {
            System.out.println("[CollectionManager] Collection directory already exists: " + collectionName.toUpperCase(Locale.ROOT));
        }

        loadAllDocumentsIntoCache(); // Load existing documents into memory cache
        loadIndexes();              // Load existing indexes for this collection
    }

    /**
     * Inserts a new document into the collection.
     * Generates a unique '_id' if not provided and saves the document as a JSON file.
     * Updates relevant indexes after insertion.
     *
     * @param documentJsonString The JSON string representation of the document.
     * @return The _id of the newly inserted document.
     * @throws IOException If an I/O error occurs during file writing.
     * @throws IllegalArgumentException If the provided documentJsonString is malformed JSON.
     */
    public String insertDocument(String documentJsonString) throws IOException {
        ObjectNode documentNode = (ObjectNode) JsonUtil.fromJsonString(documentJsonString);

        String id = documentNode.has(DBConstants.ID_FIELD_NAME) && !documentNode.get(DBConstants.ID_FIELD_NAME).asText().isEmpty()
                ? documentNode.get(DBConstants.ID_FIELD_NAME).asText()
                : UUID.randomUUID().toString(); // Generate ID if not provided or empty

        // Ensure the document has the _id field set
        ObjectNode finalDocumentNode = JsonUtil.addIdToNode(documentNode, id);

        Path documentFilePath = collectionPath.resolve(id + DBConstants.DOCUMENT_FILE_EXTENSION);
        Files.writeString(documentFilePath, JsonUtil.toJson(finalDocumentNode)); // Save document

        documentCache.put(id, finalDocumentNode); // Update in-memory cache
        updateIndexesForDocument(finalDocumentNode, true); // NEW: Update indexes for insertion

        return id;
    }

    /**
     * Retrieves all documents from the collection.
     * Reads from cache primarily.
     *
     * @return A list of all documents (JsonNode).
     * @throws IOException If an I/O error occurs during file reading.
     */
    public List<JsonNode> getAllDocuments() throws IOException {
        if (documentCache.isEmpty()) {
            loadAllDocumentsIntoCache(); // Ensure cache is populated if empty
        }
        return new ArrayList<>(documentCache.values());
    }

    /**
     * Finds documents that match a given filter (WHERE clause).
     * This method now utilizes indexes if an indexed field is present in the filter.
     *
     * @param filter The filter condition as a JsonNode.
     * @return A list of documents that match the filter.
     * @throws IOException If an I/O error occurs during file reading.
     * @throws IllegalArgumentException If the filter is not a valid JSON object.
     */
    public List<JsonNode> findDocuments(JsonNode filter) throws IOException {
        if (!filter.isObject()) {
            throw new IllegalArgumentException("Filter must be a JSON object.");
        }

        // Try to use an index first if a single, simple field is being queried
        Optional<Set<String>> candidateIds = getIdsFromIndex(filter);

        if (candidateIds.isPresent()) {
            // If an index was used, only load and filter these specific documents
            return candidateIds.get().stream()
                    .map(this::getDocumentFromCacheOrDisk) // Load only candidates
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(doc -> matchesFilter(doc, filter)) // Apply full filter
                    .collect(Collectors.toList());
        } else {
            // Fallback to full scan if no suitable index or complex filter
            return getAllDocuments().stream()
                    .filter(doc -> matchesFilter(doc, filter))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Updates documents matching a filter with new data.
     * Utilizes indexes for efficient targeting. Updates relevant indexes after modification.
     *
     * @param filter The filter condition.
     * @param updateData The data to update (JsonNode).
     * @return The number of documents updated.
     * @throws IOException If an I/O error occurs.
     * @throws IllegalArgumentException If filter or updateData is not a valid JSON object.
     */
    public int updateDocuments(JsonNode filter, JsonNode updateData) throws IOException {
        if (!filter.isObject() || !updateData.isObject()) {
            throw new IllegalArgumentException("Filter and update data must be JSON objects.");
        }

        List<JsonNode> documentsToUpdate = findDocuments(filter); // Use findDocuments (which uses index)

        int updatedCount = 0;
        for (JsonNode doc : documentsToUpdate) {
            String id = doc.get(DBConstants.ID_FIELD_NAME).asText();
            ObjectNode mutableDoc = JsonUtil.toMutableObjectNode(doc);

            // Remove old document's data from indexes BEFORE modifying
            updateIndexesForDocument(mutableDoc, false); // false for deletion from index perspective

            // Apply updates
            updateData.fields().forEachRemaining(entry -> {
                mutableDoc.set(entry.getKey(), entry.getValue());
            });

            // Save updated document
            Path documentFilePath = collectionPath.resolve(id + DBConstants.DOCUMENT_FILE_EXTENSION);
            Files.writeString(documentFilePath, JsonUtil.toJson(mutableDoc));

            documentCache.put(id, mutableDoc); // Update cache
            updateIndexesForDocument(mutableDoc, true); // Add new document's data to indexes

            updatedCount++;
        }
        if (updatedCount > 0) {
            saveIndexes(); // Save indexes to disk after modifications
        }
        return updatedCount;
    }

    /**
     * Deletes documents matching a filter.
     * Utilizes indexes for efficient targeting. Updates relevant indexes after deletion.
     *
     * @param filter The filter condition.
     * @return The number of documents deleted.
     * @throws IOException If an I/O error occurs.
     * @throws IllegalArgumentException If the filter is not a valid JSON object.
     */
    public int deleteDocuments(JsonNode filter) throws IOException {
        if (!filter.isObject()) {
            throw new IllegalArgumentException("Filter must be a JSON object.");
        }

        List<JsonNode> documentsToDelete = findDocuments(filter); // Use findDocuments (which uses index)

        int deletedCount = 0;
        for (JsonNode doc : documentsToDelete) {
            String id = doc.get(DBConstants.ID_FIELD_NAME).asText();
            Path documentFilePath = collectionPath.resolve(id + DBConstants.DOCUMENT_FILE_EXTENSION);

            Files.deleteIfExists(documentFilePath); // Delete file
            documentCache.remove(id); // Remove from cache
            updateIndexesForDocument(doc, false); // NEW: Update indexes for deletion (pass false)

            deletedCount++;
        }
        if (deletedCount > 0) {
            saveIndexes(); // Save indexes to disk after modifications
        }
        return deletedCount;
    }

    /**
     * Checks if a document matches the provided filter.
     * This is the core logic for the WHERE clause.
     * Supports basic key-value matching and advanced operators like $gt, $lt, $ne, $in.
     *
     * @param document The document to check.
     * @param filter The filter JsonNode.
     * @return true if the document matches, false otherwise.
     */
    private boolean matchesFilter(JsonNode document, JsonNode filter) {
        if (!filter.isObject()) {
            // If filter is not an object (e.g., just a value), it's invalid for this context.
            return false;
        }

        // All conditions in the filter object must be true (implicit AND)
        for (Iterator<Map.Entry<String, JsonNode>> it = filter.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String fieldName = entry.getKey();
            JsonNode filterValue = entry.getValue();

            // Handle special "_id" field separately if it's a simple string match
            if (fieldName.equals(DBConstants.ID_FIELD_NAME) && filterValue.isTextual()) {
                if (!document.has(DBConstants.ID_FIELD_NAME) || !document.get(DBConstants.ID_FIELD_NAME).asText().equals(filterValue.asText())) {
                    return false;
                }
                continue; // Process next field
            }

            // Check if the document has the field
            if (!document.has(fieldName)) {
                return false; // Document doesn't have the field, so it can't match the filter on that field
            }

            JsonNode docValue = document.get(fieldName);

            // Handle advanced operators ($gt, $lt, $ne, $in)
            if (filterValue.isObject()) {
                // Assuming only one operator per field for simplicity in this prototype
                Map.Entry<String, JsonNode> operatorEntry = filterValue.fields().next();
                String operator = operatorEntry.getKey();
                JsonNode operand = operatorEntry.getValue();

                switch (operator) {
                    case "$gt":
                        if (!docValue.isNumber() || !operand.isNumber() || docValue.asDouble() <= operand.asDouble()) return false;
                        break;
                    case "$lt":
                        if (!docValue.isNumber() || !operand.isNumber() || docValue.asDouble() >= operand.asDouble()) return false;
                        break;
                    case "$ne": // Not Equal
                        if (docValue.equals(operand)) return false; // If they are equal, it's NOT a match for $ne
                        break;
                    case "$in":
                        if (!operand.isArray()) {
                            throw new IllegalArgumentException("$in operator requires an array operand.");
                        }
                        // FIX 1: The ArrayNode class is now imported
                        boolean found = false;
                        for (JsonNode item : (ArrayNode) operand) { // <--- Uses ArrayNode after import
                            if (docValue.equals(item)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) return false;
                        break;
                    default:
                        // Unrecognized operator, treat as no match or throw error
                        System.err.println("[CollectionManager] Warning: Unrecognized operator '" + operator + "'. Document will not match this filter part.");
                        return false;
                }
            } else {
                // Simple equality match (non-operator value)
                if (!docValue.equals(filterValue)) {
                    return false;
                }
            }
        }
        return true; // All filter conditions met
    }

    /**
     * Helper method to load a document from cache or disk given its ID.
     * @param id The ID of the document.
     * @return An Optional containing the JsonNode if found, empty otherwise.
     */
    private Optional<JsonNode> getDocumentFromCacheOrDisk(String id) {
        // First, check cache
        if (documentCache.containsKey(id)) {
            return Optional.of(documentCache.get(id));
        }

        // If not in cache, try to load from disk
        Path documentPath = collectionPath.resolve(id + DBConstants.DOCUMENT_FILE_EXTENSION);
        if (Files.exists(documentPath)) {
            try {
                String json = Files.readString(documentPath);
                JsonNode doc = JsonUtil.fromJsonString(json);
                documentCache.put(id, doc); // Add to cache after loading
                return Optional.of(doc);
            } catch (IOException e) {
                System.err.println("[CollectionManager] Error loading document '" + id + "': " + e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Loads all documents from disk into the in-memory cache.
     * Called during initialization.
     * @throws IOException If an I/O error occurs during directory listing or file reading.
     */
    private void loadAllDocumentsIntoCache() throws IOException {
        documentCache.clear(); // Clear existing cache before reloading
        if (!Files.exists(collectionPath)) {
            return; // Collection directory doesn't exist yet, nothing to load.
        }

        try (Stream<Path> paths = Files.list(collectionPath)) {
            paths.filter(p -> p.toString().endsWith(DBConstants.DOCUMENT_FILE_EXTENSION) &&
                            !p.getFileName().toString().startsWith(DBConstants.INDEX_FILE_PREFIX)) // Exclude index files
                    .forEach(p -> {
                        String id = p.getFileName().toString().replace(DBConstants.DOCUMENT_FILE_EXTENSION, "");
                        try {
                            String json = Files.readString(p);
                            JsonNode doc = JsonUtil.fromJsonString(json);
                            documentCache.put(id, doc);
                        } catch (IOException e) {
                            System.err.println("[CollectionManager] Error loading document file " + p.getFileName() + ": " + e.getMessage());
                        }
                    });
        }
        System.out.println("[CollectionManager] Loaded " + documentCache.size() + " documents into cache for '" + collectionName + "'.");
    }

    /**
     * Determines if the filter can utilize an index, and if so, returns the set of candidate document IDs.
     * Currently supports simple equality filters on a single indexed field.
     *
     * @param filter The JsonNode representing the filter.
     * @return An Optional containing a Set of document IDs if an index can be used, otherwise empty.
     */
    private Optional<Set<String>> getIdsFromIndex(JsonNode filter) {
        // We look for a simple key-value pair in the filter that corresponds to an indexed field.
        // For example: {"fieldName": "fieldValue"}
        // Or: {"fieldName": {"$eq": "fieldValue"}} (though we only support implicit equality for index lookup for now)

        List<String> collectionIndexedFields = INDEXED_FIELDS_BY_COLLECTION.getOrDefault(collectionName.toLowerCase(Locale.ROOT), Collections.emptyList());

        for (String fieldName : collectionIndexedFields) {
            if (filter.has(fieldName)) {
                JsonNode filterValueNode = filter.get(fieldName);

                // Simple equality check for index lookup
                if (filterValueNode.isTextual() || filterValueNode.isNumber() || filterValueNode.isBoolean()) {
                    String fieldValue = filterValueNode.asText();
                    Map<String, Set<String>> index = fieldIndexes.get(fieldName);
                    if (index != null && index.containsKey(fieldValue)) {
                        System.out.println("[CollectionManager] Using index for field '" + fieldName + "' with value '" + fieldValue + "'");
                        return Optional.of(new HashSet<>(index.get(fieldValue))); // Return a copy to prevent external modification
                    }
                }
                // Could be extended to handle $eq operator specifically, or range queries on numeric indexes.
            }
        }
        return Optional.empty(); // No suitable index found for this filter
    }

    /**
     * Updates the in-memory indexes for a given document.
     * This method is called after insert, update, and delete operations.
     *
     * @param documentNode The document (JsonNode) to update indexes for.
     * @param isAdd If true, adds the document's values to indexes; if false, removes them.
     */
    private void updateIndexesForDocument(JsonNode documentNode, boolean isAdd) {
        String docId = documentNode.get(DBConstants.ID_FIELD_NAME).asText();
        List<String> collectionIndexedFields = INDEXED_FIELDS_BY_COLLECTION.getOrDefault(collectionName.toLowerCase(Locale.ROOT), Collections.emptyList());

        for (String fieldName : collectionIndexedFields) {
            if (documentNode.has(fieldName)) {
                JsonNode fieldValueNode = documentNode.get(fieldName);
                if (fieldValueNode.isTextual() || fieldValueNode.isNumber() || fieldValueNode.isBoolean()) {
                    String fieldValue = fieldValueNode.asText();

                    fieldIndexes.computeIfAbsent(fieldName, k -> new ConcurrentHashMap<>()); // Ensure outer map exists
                    Map<String, Set<String>> index = fieldIndexes.get(fieldName);

                    if (isAdd) {
                        index.computeIfAbsent(fieldValue, k -> ConcurrentHashMap.newKeySet()).add(docId);
                    } else { // isRemove
                        Set<String> ids = index.get(fieldValue);
                        if (ids != null) {
                            ids.remove(docId);
                            if (ids.isEmpty()) {
                                index.remove(fieldValue); // Clean up if no more documents for this value
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads all indexes from disk for this collection.
     * Index files are named _index_<fieldName>.json.
     * @throws IOException If an I/O error occurs during loading.
     */
    private void loadIndexes() throws IOException {
        fieldIndexes.clear(); // Clear existing indexes before reloading
        List<String> collectionIndexedFields = INDEXED_FIELDS_BY_COLLECTION.getOrDefault(collectionName.toLowerCase(Locale.ROOT), Collections.emptyList());

        if (!Files.exists(collectionPath)) {
            return; // No directory, no indexes to load
        }

        try (Stream<Path> paths = Files.list(collectionPath)) {
            paths.filter(p -> p.getFileName().toString().startsWith(DBConstants.INDEX_FILE_PREFIX) && p.toString().endsWith(DBConstants.DOCUMENT_FILE_EXTENSION))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String fieldName = fileName.replace(DBConstants.INDEX_FILE_PREFIX, "")
                                .replace(DBConstants.DOCUMENT_FILE_EXTENSION, "");
                        if (collectionIndexedFields.contains(fieldName)) { // Only load if it's a field we intend to index
                            try {
                                String json = Files.readString(p);
                                // Deserialize directly into Map<String, List<String>>
                                Map<String, List<String>> rawIndex = JsonUtil.OBJECT_MAPPER.readValue(json,
                                        JsonUtil.OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, String.class, List.class));

                                Map<String, Set<String>> indexMap = new ConcurrentHashMap<>();
                                // FIX 2: Correctly populate the Concurrent Set from the List
                                rawIndex.forEach((k, v) -> {
                                    Set<String> docIds = ConcurrentHashMap.newKeySet(); // Create a new thread-safe set
                                    docIds.addAll(v); // Add all elements from the list to the set
                                    indexMap.put(k, docIds);
                                });

                                fieldIndexes.put(fieldName, indexMap);
                                System.out.println("[CollectionManager] Loaded index for field '" + fieldName + "' for collection '" + collectionName + "'.");
                            } catch (IOException e) {
                                System.err.println("[CollectionManager] Error loading index file " + p.getFileName() + ": " + e.getMessage());
                            }
                        }
                    });
        }
    }

    /**
     * Saves all in-memory indexes to disk for this collection.
     * This should be called after any batch of write operations.
     * @throws IOException If an I/O error occurs during saving.
     */
    private void saveIndexes() throws IOException {
        for (Map.Entry<String, Map<String, Set<String>>> entry : fieldIndexes.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Set<String>> indexMap = entry.getValue();

            // Only save if the index is not empty
            if (!indexMap.isEmpty()) {
                Path indexPath = collectionPath.resolve(DBConstants.INDEX_FILE_PREFIX + fieldName + DBConstants.DOCUMENT_FILE_EXTENSION);
                // Convert Sets to Lists for JSON serialization
                Map<String, List<String>> serializableIndex = indexMap.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> new ArrayList<>(e.getValue()) // Convert Set to List for serialization
                        ));
                Files.writeString(indexPath, JsonUtil.toJson(JsonUtil.OBJECT_MAPPER.valueToTree(serializableIndex)));
                System.out.println("[CollectionManager] Saved index for field '" + fieldName + "' for collection '" + collectionName + "'.");
            } else {
                // If an index becomes empty, delete its file
                Path indexPath = collectionPath.resolve(DBConstants.INDEX_FILE_PREFIX + fieldName + DBConstants.DOCUMENT_FILE_EXTENSION);
                Files.deleteIfExists(indexPath);
            }
        }
    }
}