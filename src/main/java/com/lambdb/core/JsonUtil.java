package com.lambdb.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * JsonUtil.java
 * Author: Vishwas Karode
 * Description:
 * Utility class for JSON serialization and deserialization using Jackson.
 * Provides helper methods to convert between JSON strings and JsonNode objects,
 * to manage the '_id' field, and to format JSON output.
 * The ObjectMapper instance is public for broader access across the application.
 */
public class JsonUtil {

    // Public ObjectMapper for consistent JSON processing throughout the application.
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT); // Enable pretty printing for readability

    /**
     * Converts a JSON string to a JsonNode object.
     * @param jsonString The JSON string to parse.
     * @return A JsonNode representation of the JSON string.
     * @throws IOException If the JSON string is malformed.
     */
    public static JsonNode fromJsonString(String jsonString) throws IOException {
        return OBJECT_MAPPER.readTree(jsonString);
    }

    /**
     * Converts a JsonNode object to a compact JSON string.
     * @param jsonNode The JsonNode to convert.
     * @return A compact JSON string.
     * @throws IOException If conversion fails.
     */
    public static String toJson(JsonNode jsonNode) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(jsonNode);
    }

    /**
     * Converts a JsonNode object to a pretty-printed JSON string.
     * This method is essential for readable output in the console.
     * @param jsonNode The JsonNode to convert.
     * @return A pretty-printed JSON string.
     * @throws IOException If conversion fails.
     */
    public static String toPrettyJson(JsonNode jsonNode) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(jsonNode);
    }

    /**
     * Adds or updates the '_id' field in a JsonNode (document).
     * This method creates a new ObjectNode if the original is not mutable.
     * @param originalNode The original JsonNode document.
     * @param id The ID string to set for the '_id' field.
     * @return A new or modified ObjectNode with the '_id' field set.
     */
    public static ObjectNode addIdToNode(JsonNode originalNode, String id) {
        ObjectNode mutableNode;
        if (originalNode instanceof ObjectNode) {
            mutableNode = (ObjectNode) originalNode;
        } else {
            // If it's not an ObjectNode, create a new one from its contents
            mutableNode = OBJECT_MAPPER.createObjectNode();
            originalNode.fields().forEachRemaining(entry -> mutableNode.set(entry.getKey(), entry.getValue()));
        }
        mutableNode.put(DBConstants.ID_FIELD_NAME, id);
        return mutableNode;
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
            return (ObjectNode) OBJECT_MAPPER.valueToTree(node);
        }
        throw new IllegalArgumentException("Provided JsonNode is not an object and cannot be converted to a mutable ObjectNode.");
    }
}