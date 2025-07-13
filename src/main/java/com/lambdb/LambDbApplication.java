package com.lambdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.lambdb.core.DBConstants; // Corrected import
import com.lambdb.core.DBManager;   // Corrected import
import com.lambdb.core.collection.CollectionManager; // Still in core.collection

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * LambDbApplication.java
 * Author: Vishwas Karode
 * Description:
 * This is the main application class for the LAMB DB NoSQL prototype.
 * It serves as a demonstration of how to interact with the database using its
 * direct Java API. This class performs various CRUD (Create, Retrieve, Update, Delete)
 * operations on collections and documents, showcasing the core functionalities developed.
 *
 * This application will create a 'lambdb_data' directory in its running location
 * to store all database files.
 */
public class LambDbApplication {

    /**
     * The main entry point for the LAMB DB demonstration application.
     * @param args Command line arguments (not used in this demo).
     */
    public static void main(String[] args) {
        try {
            // --- Phase 1: Core Setup & Basic Document Storage Demo ---

            // 1. Initialize the Database Manager
            // The DBManager is the central orchestrator for the database.
            // It ensures the root directory exists and loads any pre-existing collections.
            System.out.println("\n--- Initializing LAMB DB by Vishwas Karode ---");
            DBManager dbManager = new DBManager(); // Using the new DBManager class

            // 2. Create a Collection: "users"
            // Collections in LAMB DB are analogous to tables in relational databases
            // or collections in other NoSQL document databases like MongoDB.
            // They are represented as directories on the file system.
            System.out.println("\n--- Creating 'users' collection ---");
            // If the 'users' collection already exists, DBManager will return the existing manager.
            CollectionManager usersCollection = dbManager.createCollection("users");
            System.out.println("Collection 'users' is ready for operations.");

            // 3. Insert Documents into the "users" Collection
            // Documents are JSON objects. LAMB DB stores them as individual .json files.
            // Each document will have a unique '_id' field. If not provided, LAMB DB generates one.
            System.out.println("\n--- Inserting documents into 'users' collection ---");

            // Document 1: Without a predefined '_id'. LAMB DB will assign a UUID.
            String user1Json = "{\"name\": \"Alice\", \"age\": 30, \"city\": \"New York\"}";
            String id1 = usersCollection.insertDocument(user1Json);
            System.out.println("User 1 inserted with generated ID: " + id1);

            // Document 2: With a predefined '_id'. This allows external systems to manage IDs.
            String user2Json = "{\"_id\": \"user_002_vk\", \"name\": \"Bob\", \"age\": 24, \"city\": \"London\"}";
            String id2 = usersCollection.insertDocument(user2Json);
            System.out.println("User 2 inserted with predefined ID: " + id2);

            // Document 3: Another document with generated ID, showcasing diverse data.
            String user3Json = "{\"name\": \"Charlie\", \"country\": \"India\", \"isActive\": true}";
            String id3 = usersCollection.insertDocument(user3Json);
            System.out.println("User 3 inserted with generated ID: " + id3);

            // 4. Retrieve a Specific Document by its '_id'
            System.out.println("\n--- Retrieving document with _id 'user_002_vk' ---");
            Optional<JsonNode> retrievedUser2 = usersCollection.getDocument("user_002_vk");
            if (retrievedUser2.isPresent()) {
                System.out.println("Successfully retrieved User 2:\n" + retrievedUser2.get().toPrettyString());
            } else {
                System.out.println("User 2 not found (unexpected).");
            }

            System.out.println("\n--- Attempting to retrieve a non-existent document 'nonexistent_user_id' ---");
            Optional<JsonNode> nonExistentUser = usersCollection.getDocument("nonexistent_user_id");
            if (nonExistentUser.isEmpty()) {
                System.out.println("Result: Non-existent user not found, as expected. (Good job, Vishwas!)");
            }

            // 5. Update an Existing Document
            // When updating, the provided JSON string must include the '_id' of the document to be updated.
            System.out.println("\n--- Updating document with ID '" + id1 + "' (Alice) ---");
            String updatedUser1Json = "{\"_id\": \"" + id1 + "\", \"name\": \"Alice Smith\", \"age\": 31, \"city\": \"San Francisco\", \"occupation\": \"Software Engineer\"}";
            boolean updatedStatus = usersCollection.updateDocument(id1, updatedUser1Json);
            System.out.println("Document '" + id1 + "' update status: " + (updatedStatus ? "SUCCESS" : "FAILED"));

            System.out.println("\n--- Retrieving updated document '" + id1 + "' to verify changes ---");
            usersCollection.getDocument(id1).ifPresent(jsonNode -> System.out.println("Updated content:\n" + jsonNode.toPrettyString()));


            // 6. Retrieve All Documents from the "users" Collection
            System.out.println("\n--- Retrieving all documents from 'users' collection ---");
            List<JsonNode> allUsers = usersCollection.getAllDocuments();
            System.out.println("Total documents currently in 'users' collection: " + allUsers.size());
            allUsers.forEach(jsonNode ->
                    System.out.println("  - Document ID: " + jsonNode.get(DBConstants.ID_FIELD_NAME).asText() + "\n" + jsonNode.toPrettyString() + "\n---")
            );


            // 7. Delete a Specific Document
            System.out.println("\n--- Deleting document with ID '" + id3 + "' (Charlie) ---");
            boolean deletedStatus = usersCollection.deleteDocument(id3);
            System.out.println("Document '" + id3 + "' deletion status: " + (deletedStatus ? "SUCCESS" : "FAILED"));

            System.out.println("\n--- All documents after deletion of '" + id3 + "' ---");
            List<JsonNode> remainingUsers = usersCollection.getAllDocuments();
            System.out.println("Total documents remaining in 'users' collection: " + remainingUsers.size());
            remainingUsers.forEach(jsonNode ->
                    System.out.println("  - Document ID: " + jsonNode.get(DBConstants.ID_FIELD_NAME).asText() + "\n" + jsonNode.toPrettyString() + "\n---")
            );

            // 8. Demonstrate Collection Creation and Deletion
            System.out.println("\n--- Creating 'products' collection for deletion demo ---");
            CollectionManager productsCollection = dbManager.createCollection("products");
            productsCollection.insertDocument("{\"productName\": \"Laptop\", \"price\": 1200.00, \"category\": \"Electronics\"}");
            productsCollection.insertDocument("{\"productName\": \"Mouse\", \"price\": 25.00, \"category\": \"Electronics\"}");
            System.out.println("Products inserted. Total products in 'products' collection: " + productsCollection.getAllDocuments().size());

            System.out.println("\n--- Deleting 'products' collection entirely ---");
            boolean productsCollectionDeleted = dbManager.deleteCollection("products");
            System.out.println("Collection 'products' deleted status: " + (productsCollectionDeleted ? "SUCCESS" : "FAILED"));

            // Verify 'products' collection is gone from the in-memory map
            if (dbManager.getCollection("products").isEmpty()) {
                System.out.println("Verification: 'products' collection is indeed gone from DBManager's awareness.");
            }

            // Uncomment the following lines to also delete the 'users' collection at the end of the demo.
            // This cleans up all data created by the application run.
            System.out.println("\n--- Deleting 'users' collection for cleanup ---");
            boolean usersCollectionCleanupStatus = dbManager.deleteCollection("users");
            System.out.println("Collection 'users' cleanup status: " + (usersCollectionCleanupStatus ? "SUCCESS" : "FAILED"));
            if (dbManager.getCollection("users").isEmpty()) {
                System.out.println("Verification: 'users' collection is indeed gone for cleanup.");
            }


            System.out.println("\n--- LAMB DB Phase 1 Demo Complete by Vishwas Karode ---");

        } catch (IOException e) {
            // Catch specific I/O errors that might occur during file operations
            System.err.println("❌ An I/O error occurred during LAMB DB operation: " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for debugging purposes
        } catch (IllegalArgumentException e) {
            // Catch errors related to invalid arguments, like trying to insert duplicate _id
            System.err.println("❌ A database argument error occurred: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            System.err.println("❌ An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\n(Note: Check your project directory for the 'lambdb_data' folder and its contents.)");
        }
    }
}