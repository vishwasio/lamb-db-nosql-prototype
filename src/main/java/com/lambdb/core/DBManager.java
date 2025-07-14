package com.lambdb.core;

import com.lambdb.core.collection.CollectionManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * DBManager.java
 * Author: Vishwas Karode
 * Description:
 * The central manager for the LAMB DB NoSQL prototype.
 * It handles the initialization of the database root directory,
 * loading of existing collections, and providing methods to
 * create, retrieve, and delete collections.
 */
public class DBManager {

    private final Path dbRootPath; // The file system path to the root directory of the database
    // In-memory map to quickly access CollectionManager instances by their collection name.
    // This acts as a cache for active collections.
    private final Map<String, CollectionManager> collections = new HashMap<>();

    /**
     * Constructor for DbManager.
     * Initializes the database by ensuring the root directory exists and loading any existing collections.
     * @throws IOException If an I/O error occurs during directory operations.
     */
    public DBManager() throws IOException {
        // Resolve the absolute path for the database root directory.
        // It will be created relative to where the application is run.
        this.dbRootPath = Paths.get(DBConstants.DB_ROOT_DIR);
        initDbRoot(); // Ensure the physical root directory is set up
        loadExistingCollections(); // Populate the in-memory 'collections' map with existing ones
    }

    /**
     * Ensures that the main database root directory exists on the file system.
     * If it does not exist, it will be created.
     * @throws IOException If an I/O error occurs while creating the directory.
     */
    private void initDbRoot() throws IOException {
        if (!Files.exists(dbRootPath)) {
            Files.createDirectories(dbRootPath); // Create the directory
            System.out.println("[DbManager] LAMB DB root directory created: " + dbRootPath.toAbsolutePath());
        } else {
            System.out.println("[DbManager] LAMB DB root directory already exists: " + dbRootPath.toAbsolutePath());
        }
    }

    /**
     * Scans the database root directory for existing collection directories
     * and loads them into the in-memory 'collections' map.
     * This ensures that when DbManager starts, it's aware of all previously created collections.
     * @throws IOException If an I/O error occurs during directory listing.
     */
    private void loadExistingCollections() throws IOException {
        // List all directories directly under the DB root path
        try (Stream<Path> paths = Files.list(dbRootPath)) {
            paths.filter(Files::isDirectory) // Only process directories (which represent collections)
                    .forEach(path -> {
                        String collectionName = path.getFileName().toString();
                        // Create a CollectionManager for each found directory
                        CollectionManager manager = new CollectionManager(collectionName, dbRootPath);
                        collections.put(collectionName, manager); // Add to our in-memory map
                        System.out.println("[DbManager] Loaded existing collection: '" + collectionName + "'");
                    });
        }
    }

    /**
     * Creates a new collection in the database.
     * This involves creating a new directory under the database root.
     * If the collection already exists, it simply returns the existing CollectionManager.
     *
     * @param collectionName The desired name for the new collection.
     * @return The CollectionManager instance for the newly created or existing collection.
     * @throws IOException If an I/O error occurs during directory creation.
     * @throws IllegalArgumentException If a file with the same name already exists at the collection path.
     */
    public CollectionManager createCollection(String collectionName) throws IOException {
        // Check if the collection is already loaded in memory
        if (collections.containsKey(collectionName)) {
            System.out.println("[DbManager] Collection '" + collectionName + "' already exists in memory.");
            return collections.get(collectionName);
        }

        Path potentialCollectionPath = dbRootPath.resolve(collectionName);
        // Prevent creating a collection if a file with the same name already exists.
        if (Files.exists(potentialCollectionPath) && !Files.isDirectory(potentialCollectionPath)) {
            throw new IllegalArgumentException("Cannot create collection. A file with name '" + collectionName + "' already exists in the database root.");
        }

        // Create a new CollectionManager and initialize its directory.
        CollectionManager manager = new CollectionManager(collectionName, dbRootPath);
        manager.init(); // This will create the physical directory if it doesn't exist.
        collections.put(collectionName, manager); // Add to our in-memory map
        System.out.println("[DbManager] Collection '" + collectionName + "' created/initialized.");
        return manager;
    }

    /**
     * Retrieves an existing CollectionManager by its name.
     * This allows clients to perform operations on a specific collection.
     *
     * @param collectionName The name of the collection to retrieve.
     * @return An Optional containing the CollectionManager if found, or an empty Optional if the collection does not exist.
     */
    public Optional<CollectionManager> getCollection(String collectionName) {
        // Retrieve from the in-memory map.
        return Optional.ofNullable(collections.get(collectionName));
    }

    /**
     * Deletes a collection and all its contents (documents) from the database.
     * This involves deleting the entire directory corresponding to the collection.
     *
     * @param collectionName The name of the collection to delete.
     * @return true if the collection was found and successfully deleted, false if not found.
     * @throws IOException If an I/O error occurs during deletion (e.g., permissions issue).
     */
    public boolean deleteCollection(String collectionName) throws IOException {
        Optional<CollectionManager> managerOpt = getCollection(collectionName);
        if (managerOpt.isPresent()) {
            // Get the path to the collection directory
            Path collectionToDeletePath = dbRootPath.resolve(collectionName);

            // Delete all files and subdirectories within the collection directory first, then the directory itself.
            // Files.walk provides a stream of paths starting from the given path.
            // sorted(Comparator.reverseOrder()) ensures deletion of contents before parent directory.
            try (Stream<Path> paths = Files.walk(collectionToDeletePath)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete); // Delete each file/directory
            }

            // Remove from our in-memory map
            collections.remove(collectionName);
            System.out.println("[DbManager] Collection '" + collectionName + "' and its contents deleted.");
            return true;
        }
        System.out.println("[DbManager] Collection '" + collectionName + "' not found for deletion.");
        return false;
    }
}