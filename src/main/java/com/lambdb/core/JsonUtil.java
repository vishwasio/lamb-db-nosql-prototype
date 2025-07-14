package com.lambdb.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * JsonUtil.java
 * Author: Vishwas Karode
 * Description:
 * A utility class for handling JSON serialization and deserialization using the Jackson library.
 * It provides methods to convert Java objects to/from JSON strings and files,
 * and to manage the '_id' field for documents.
 */
public class JsonUtil {

    // ObjectMapper is thread-safe and should be reused for performance.
    // It's the core component of Jackson for performing conversions.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Converts any Java object into a pretty-printed JSON string.
     * This is useful for storing documents in a human-readable format.
     *
     * @param obj The Java object to convert to JSON.
     * @return A formatted JSON string representation of the object.
     * @throws IOException If there's an error during JSON serialization (e.g., invalid object structure).
     */
    public static String toJson(Object obj) throws IOException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

    /**
     * Parses a JSON string into a Jackson JsonNode object.
     * JsonNode provides a tree-model representation of JSON, allowing easy navigation and modification.
     *
     * @param jsonString The JSON string to parse.
     * @return A JsonNode representing the parsed JSON structure.
     * @throws IOException If the JSON string is malformed.
     */
    public static JsonNode fromJsonString(String jsonString) throws IOException {
        return OBJECT_MAPPER.readTree(jsonString);
    }

    /**
     * Reads a JSON document from a specified file and parses it into a JsonNode.
     *
     * @param file The File object pointing to the JSON document.
     * @return A JsonNode representing the content of the file.
     * @throws IOException If an I/O error occurs during file reading or JSON parsing.
     */
    public static JsonNode fromJsonFile(File file) throws IOException {
        return OBJECT_MAPPER.readTree(file);
    }

    /**
     * Writes a JsonNode object to a specified file as a pretty-printed JSON document.
     *
     * @param jsonNode The JsonNode to write to the file.
     * @param file The target File object where the JSON will be written.
     * @throws IOException If an I/O error occurs during file writing.
     */
    public static void writeToFile(JsonNode jsonNode, File file) throws IOException {
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, jsonNode);
    }

    /**
     * Ensures that a given JsonNode document has a unique '_id' field.
     * If the document already has an '_id' field with a non-null value, it remains unchanged.
     * Otherwise, a new UUID (Universally Unique Identifier) is generated and assigned as the '_id'.
     * This is crucial for identifying documents uniquely within a collection.
     *
     * @param documentNode The JsonNode representing the document.
     * @return The modified JsonNode with an '_id' field guaranteed to be present.
     */
    public static JsonNode ensureDocumentId(JsonNode documentNode) {
        // Check if the document already has an '_id' field and if it's not null.
        if (!documentNode.has(DBConstants.ID_FIELD_NAME) || documentNode.get(DBConstants.ID_FIELD_NAME).isNull()) {
            // If not, cast to ObjectNode to allow modification and add a new UUID as _id.
            ((ObjectNode) documentNode).put(DBConstants.ID_FIELD_NAME, UUID.randomUUID().toString());
        }
        return documentNode;
    }

    /**
     * Converts an immutable JsonNode to a mutable ObjectNode.
     * This is useful when you need to modify an existing document (JsonNode)
     * by adding or changing fields.
     * @param node The JsonNode to convert.
     * @return An ObjectNode that can be modified. If the input node is not an object,
     * it throws an IllegalArgumentException.
     */
    public static ObjectNode toMutableObjectNode(JsonNode node) {
        if (node.isObject()) {
            // CORRECTED LINE: Using our OBJECT_MAPPER instance
            return (ObjectNode) OBJECT_MAPPER.valueToTree(node);
        }
        throw new IllegalArgumentException("Provided JsonNode is not an object and cannot be converted to a mutable ObjectNode.");
    }
}