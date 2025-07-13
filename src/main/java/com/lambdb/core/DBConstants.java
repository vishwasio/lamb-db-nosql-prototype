package com.lambdb.core;

/**
 * DBConstants.java
 * Author: Vishwas Karode
 * Description:
 * This class holds global constants used throughout the LAMB DB prototype.
 * It defines the root directory for database storage and conventions for documents.
 */
public class DBConstants {
    // The name of the root directory where all LAMB DB data will be stored.
    // This directory will be created in the application's current working directory.
    public static final String DB_ROOT_DIR = "lambdb_data";

    // The file extension for all document files stored within collections.
    // All documents will be persisted as JSON files.
    public static final String DOCUMENT_FILE_EXTENSION = ".json";

    // The standard field name used for a document's unique identifier.
    // Similar to '_id' in MongoDB, every document in LAMB DB will have this field.
    public static final String ID_FIELD_NAME = "_id";
}