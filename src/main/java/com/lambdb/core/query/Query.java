package com.lambdb.core.query;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

/**
 * Query.java
 * Author: Vishwas Karode
 * Description:
 * A record class to represent a parsed LAMBQL query in a structured format.
 * Records are immutable data classes in Java, ideal for holding query components.
 * This class abstracts the different parts of a query, making it easier for the
 * interpreter to understand and execute.
 */
public record Query(
        QueryType type,              // The type of the query (e.g., INSERT, SELECT)
        String collectionName,       // The name of the collection the query targets
        Optional<String> documentData, // Optional: Raw JSON string for INSERT or full document for UPDATE (if replacing)
        Optional<JsonNode> filter,     // Optional: Parsed JSON for WHERE clause conditions (e.g., {"name": "Alice"})
        Optional<JsonNode> updateData  // Optional: Parsed JSON for SET clause (fields to update)
) {
    // Convenience constructor for queries that only need type and collection name (e.g., CREATE COLLECTION)
    public Query(QueryType type, String collectionName) {
        this(type, collectionName, Optional.empty(), Optional.empty(), Optional.empty());
    }

    // Convenience constructor for INSERT queries
    public Query(QueryType type, String collectionName, String documentData) {
        this(type, collectionName, Optional.of(documentData), Optional.empty(), Optional.empty());
    }
}