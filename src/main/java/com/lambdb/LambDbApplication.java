package com.lambdb;

import com.lambdb.core.DBManager;         // Corrected import
import com.lambdb.core.LambDbInterpreter; // New import

import java.io.IOException;

/**
 * LambDbApplication.java
 * Author: Vishwas Karode
 * Description:
 * This is the main application class for the LAMB DB NoSQL prototype.
 * It now demonstrates the use of the LAMBQL Interpreter, allowing interaction
 * with the database through custom query language commands.
 * This provides a higher-level, more "database-like" experience.
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
            // --- Phase 2: LAMBQL Interpreter Demo ---

            // 1. Initialize the Database Manager
            System.out.println("\n--- Initializing LAMB DB System by Vishwas Karode ---");
            DBManager dbManager = new DBManager(); // Our core DB management component

            // 2. Initialize the LAMBQL Interpreter
            // The interpreter bridges the gap between LAMBQL commands and the underlying DBManager.
            LambDbInterpreter interpreter = new LambDbInterpreter(dbManager);

            System.out.println("\n--- Starting LAMBQL Command Execution Demo ---");

            // --- LAMBQL Commands ---

            // 1. CREATE COLLECTION Command
            System.out.println(interpreter.execute("CREATE COLLECTION users;"));
            System.out.println(interpreter.execute("CREATE COLLECTION products;"));
            System.out.println(interpreter.execute("CREATE COLLECTION orders;")); // Another collection for demonstration

            // 2. INSERT DOCUMENT Command
            // Notice how the document data is passed as a JSON string directly in the query.
            System.out.println(interpreter.execute("INSERT INTO users VALUES { \"name\": \"Alice\", \"age\": 30, \"city\": \"New York\", \"email\": \"alice@example.com\" };"));
            System.out.println(interpreter.execute("INSERT INTO users VALUES { \"_id\": \"user_002_vk\", \"name\": \"Bob\", \"age\": 24, \"city\": \"London\", \"email\": \"bob@example.com\" };"));
            System.out.println(interpreter.execute("INSERT INTO users VALUES { \"name\": \"Charlie\", \"age\": 35, \"city\": \"New York\", \"email\": \"charlie@example.com\" };"));
            System.out.println(interpreter.execute("INSERT INTO products VALUES { \"productName\": \"Laptop\", \"price\": 1200, \"stock\": 50, \"category\": \"Electronics\" };"));
            System.out.println(interpreter.execute("INSERT INTO products VALUES { \"productName\": \"Keyboard\", \"price\": 75, \"stock\": 200, \"category\": \"Peripherals\" };"));

            // 3. SELECT ALL Documents Command
            System.out.println(interpreter.execute("SELECT * FROM users;"));
            System.out.println(interpreter.execute("SELECT * FROM products;"));
            System.out.println(interpreter.execute("SELECT * FROM orders;")); // Should be empty

            // 4. SELECT Documents with Filter (Equality based)
            // The WHERE clause accepts a JSON object for simple equality filtering.
            System.out.println(interpreter.execute("SELECT * FROM users WHERE {\"city\": \"New York\"};"));
            System.out.println(interpreter.execute("SELECT * FROM users WHERE {\"age\": 24};")); // Should find Bob
            System.out.println(interpreter.execute("SELECT * FROM products WHERE {\"category\": \"Electronics\"};"));
            System.out.println(interpreter.execute("SELECT * FROM users WHERE {\"name\": \"NonExistent\"};")); // Should find no documents

            // 5. UPDATE Documents Command
            // Targets documents using a WHERE clause and updates specified fields in the SET clause.
            System.out.println(interpreter.execute("UPDATE users SET {\"age\": 31, \"status\": \"active\"} WHERE {\"name\": \"Alice\"};"));
            System.out.println(interpreter.execute("SELECT * FROM users WHERE {\"name\": \"Alice\"};")); // Verify Alice's update

            System.out.println(interpreter.execute("UPDATE products SET {\"stock\": 150} WHERE {\"productName\": \"Laptop\"};"));
            System.out.println(interpreter.execute("SELECT * FROM products WHERE {\"productName\": \"Laptop\"};")); // Verify Laptop update

            // 6. DELETE Documents Command
            // Deletes documents matching the WHERE clause.
            System.out.println(interpreter.execute("DELETE FROM users WHERE {\"name\": \"Charlie\"};"));
            System.out.println(interpreter.execute("SELECT * FROM users;")); // Verify Charlie is gone

            System.out.println(interpreter.execute("DELETE FROM products WHERE {\"price\": 75};")); // Delete Keyboard
            System.out.println(interpreter.execute("SELECT * FROM products;")); // Verify Keyboard is gone

            // 7. DELETE COLLECTION Command (entire collection and its data)
            System.out.println(interpreter.execute("DELETE FROM orders;")); // Delete empty orders collection
            System.out.println(interpreter.execute("DELETE FROM products;")); // Delete remaining products collection

            System.out.println("\n--- LAMBQL Command Execution Demo Complete by Vishwas Karode ---");

        } catch (IOException e) {
            // Catch specific I/O errors that might occur during file operations
            System.err.println("❌ An I/O error occurred during LAMB DB operation (by Vishwas Karode): " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for debugging purposes
        } catch (Exception e) {
            // Catch any other unexpected exceptions during application startup or interpreter initialization
            System.err.println("❌ An unexpected critical error occurred (by Vishwas Karode): " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\n(Note from Vishwas Karode: 'lambdb_data' directory should be empty after this run, or contain only remnants from previous incomplete runs.)");
        }
    }
}