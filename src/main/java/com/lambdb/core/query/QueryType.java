package com.lambdb.core.query;

/**
 * QueryType.java
 * Author: Vishwas Karode
 * Description:
 * Defines the types of LAMBQL (LAMB Query Language) commands that our interpreter can recognize and process.
 * This enum helps categorize parsed queries for appropriate handling.
 */
public enum QueryType {
    CREATE_COLLECTION,   // e.g., CREATE COLLECTION users;
    INSERT_DOCUMENT,     // e.g., INSERT INTO users VALUES { ... };
    SELECT_ALL,          // e.g., SELECT * FROM users;
    SELECT_FILTERED,     // e.g., SELECT * FROM users WHERE { ... };
    UPDATE_DOCUMENT,     // e.g., UPDATE users SET { ... } WHERE { ... };
    DELETE_DOCUMENT,     // e.g., DELETE FROM users WHERE { ... };
    DELETE_COLLECTION,   // e.g., DELETE FROM users; (deletes the entire collection)
    UNKNOWN              // For commands that do not match any known LAMBQL syntax.
}