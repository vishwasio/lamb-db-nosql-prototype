package com.lambdb;

import com.lambdb.core.DBManager;
import com.lambdb.core.LambDbInterpreter;

import java.io.IOException;

/**
 * LambDbApplication.java
 * Author: Vishwas Karode
 * Description:
 * This is the main application class for the LAMB DB NoSQL prototype.
 * It demonstrates the use of the LAMBQL Interpreter, now showcasing
 * enhanced query capabilities including projections and advanced filtering operators.
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
            // --- Phase 3: Enhanced LAMBQL Interpreter Demo ---

            // 1. Initialize the Database Manager
            System.out.println("\n--- Initializing LAMB DB System ---");
            DBManager dbManager = new DBManager();

            // 2. Initialize the LAMBQL Interpreter
            LambDbInterpreter interpreter = new LambDbInterpreter(dbManager);

            System.out.println("\n--- Starting Enhanced LAMBQL Command Execution Demo ---");

            // --- Setup: Create Collections and Insert Initial Data ---
            System.out.println(interpreter.execute("CREATE COLLECTION users;"));
            System.out.println(interpreter.execute("CREATE COLLECTION products;"));
            System.out.println(interpreter.execute("CREATE COLLECTION logs;")); // For demonstrating $gt/$lt on timestamps

            System.out.println(interpreter.execute("INSERT INTO users VALUES { \"name\": \"Alice\", \"age\": 30, \"city\": \"New York\", \"email\": \"alice@example.com\", \"status\": \"active\" };"));
            System.out.println(interpreter.execute("INSERT INTO users VALUES { \"_id\": \"user_002_vk\", \"name\": \"Bob\", \"age\": 24, \"city\": \"London\", \"email\": \"bob@example.com\", \"status\": \"inactive\" };"));
            System.out.println(interpreter.execute("INSERT INTO users VALUES { \"name\": \"Charlie\", \"age\": 35, \"city\": \"New York\", \"email\": \"charlie@example.com\", \"status\": \"active\" };"));
            System.out.println(interpreter.execute("INSERT INTO users VALUES { \"name\": \"David\", \"age\": 24, \"city\": \"London\", \"email\": \"david@example.com\", \"status\": \"active\" };"));
            System.out.println(interpreter.execute("INSERT INTO users VALUES { \"name\": \"Eve\", \"age\": 42, \"city\": \"Paris\", \"email\": \"eve@example.com\", \"status\": \"inactive\" };"));

            System.out.println(interpreter.execute("INSERT INTO products VALUES { \"productName\": \"Laptop\", \"price\": 1200.00, \"stock\": 50, \"category\": \"Electronics\" };"));
            System.out.println(interpreter.execute("INSERT INTO products VALUES { \"productName\": \"Keyboard\", \"price\": 75.50, \"stock\": 200, \"category\": \"Peripherals\" };"));
            System.out.println(interpreter.execute("INSERT INTO products VALUES { \"productName\": \"Mouse\", \"price\": 25.00, \"stock\": 300, \"category\": \"Peripherals\" };"));
            System.out.println(interpreter.execute("INSERT INTO products VALUES { \"productName\": \"Monitor\", \"price\": 300.00, \"stock\": 80, \"category\": \"Electronics\" };"));

            System.out.println(interpreter.execute("INSERT INTO logs VALUES { \"timestamp\": 1678886400000, \"event\": \"UserLogin\", \"userId\": \"user_001\" };")); // March 15, 2023 12:00:00 AM UTC
            System.out.println(interpreter.execute("INSERT INTO logs VALUES { \"timestamp\": 1678886400001, \"event\": \"SessionStart\", \"userId\": \"user_001\" };"));
            System.out.println(interpreter.execute("INSERT INTO logs VALUES { \"timestamp\": 1681564800000, \"event\": \"ProductView\", \"userId\": \"user_003\", \"productId\": \"prod_002\" };")); // April 15, 2023 12:00:00 AM UTC
            System.out.println(interpreter.execute("INSERT INTO logs VALUES { \"timestamp\": 1690000000000, \"event\": \"Checkout\", \"userId\": \"user_001\" };")); // July 22, 2023 4:26:40 AM UTC


            // --- New LAMBQL Capabilities Demonstration ---

            // 1. SELECT with PROJECTION
            System.out.println("\n--- SELECT with PROJECTION ---");
            System.out.println(interpreter.execute("SELECT name, email FROM users;"));
            System.out.println(interpreter.execute("SELECT productName, price FROM products;"));
            System.out.println(interpreter.execute("SELECT event FROM logs;"));
            System.out.println(interpreter.execute("SELECT name FROM users WHERE {\"city\": \"London\"};")); // Projection with filter

            // 2. SELECT with ADVANCED FILTERING OPERATORS
            System.out.println("\n--- SELECT with ADVANCED FILTERING OPERATORS ---");

            // $gt (Greater Than)
            System.out.println(interpreter.execute("SELECT * FROM users WHERE {\"age\": {\"$gt\": 30}};")); // Users older than 30 (Charlie, Eve)
            System.out.println(interpreter.execute("SELECT productName, price FROM products WHERE {\"price\": {\"$gt\": 100.00}};")); // Products > $100 (Laptop, Monitor)
            System.out.println(interpreter.execute("SELECT * FROM logs WHERE {\"timestamp\": {\"$gt\": 1680000000000}};")); // Logs after a certain timestamp (April 15 onwards)

            // $lt (Less Than)
            System.out.println(interpreter.execute("SELECT name, age FROM users WHERE {\"age\": {\"$lt\": 30}};")); // Users younger than 30 (Bob, David)
            System.out.println(interpreter.execute("SELECT productName, price FROM products WHERE {\"stock\": {\"$lt\": 100}};")); // Products with stock < 100 (Laptop, Monitor)

            // $ne (Not Equal To)
            System.out.println(interpreter.execute("SELECT * FROM users WHERE {\"city\": {\"$ne\": \"New York\"}};")); // Users not from New York (Bob, David, Eve)
            System.out.println(interpreter.execute("SELECT * FROM products WHERE {\"category\": {\"$ne\": \"Electronics\"}};")); // Products not electronics (Keyboard, Mouse)

            // $in (Value in a list)
            System.out.println(interpreter.execute("SELECT * FROM users WHERE {\"city\": {\"$in\": [\"New York\", \"Paris\"]}};")); // Users from NY or Paris (Alice, Charlie, Eve)
            System.out.println(interpreter.execute("SELECT * FROM users WHERE {\"status\": {\"$in\": [\"active\"]}};")); // Active users
            System.out.println(interpreter.execute("SELECT * FROM products WHERE {\"productName\": {\"$in\": [\"Laptop\", \"Mouse\"]}};"));

            // Combined Filters (Implicit AND)
            System.out.println("\n--- SELECT with COMBINED FILTERS (Implicit AND) ---");
            System.out.println(interpreter.execute("SELECT * FROM users WHERE {\"city\": \"New York\", \"age\": {\"$gt\": 30}};")); // Charlie
            System.out.println(interpreter.execute("SELECT name, email FROM users WHERE {\"age\": {\"$lt\": 30}, \"status\": \"active\"};")); // David

            // 3. UPDATE with Advanced Filters
            System.out.println("\n--- UPDATE with Advanced Filters ---");
            System.out.println(interpreter.execute("UPDATE users SET {\"status\": \"premium\"} WHERE {\"age\": {\"$gt\": 30}};")); // Update users older than 30 to premium
            System.out.println(interpreter.execute("SELECT name, age, status FROM users WHERE {\"status\": \"premium\"};")); // Verify updates (Alice, Charlie, Eve)

            System.out.println(interpreter.execute("UPDATE products SET {\"stock\": 0} WHERE {\"stock\": {\"$lt\": 60}};")); // Mark low stock items as 0
            System.out.println(interpreter.execute("SELECT productName, stock FROM products WHERE {\"stock\": 0};")); // Verify (Laptop)

            // 4. DELETE with Advanced Filters
            System.out.println("\n--- DELETE with Advanced Filters ---");
            System.out.println(interpreter.execute("DELETE FROM users WHERE {\"age\": {\"$ne\": 24}, \"city\": \"London\"};")); // Delete no one here
            System.out.println(interpreter.execute("DELETE FROM users WHERE {\"age\": {\"$lt\": 25}};")); // Delete Bob and David (age 24)
            System.out.println(interpreter.execute("SELECT * FROM users;")); // Verify Bob and David are gone

            System.out.println(interpreter.execute("DELETE FROM logs WHERE {\"timestamp\": {\"$lt\": 1680000000000}};")); // Delete logs before April 2023
            System.out.println(interpreter.execute("SELECT * FROM logs;")); // Verify remaining logs

            // --- Cleanup ---
            System.out.println("\n--- Cleaning Up Collections ---");
            System.out.println(interpreter.execute("DELETE FROM users;"));
            System.out.println(interpreter.execute("DELETE FROM products;"));
            System.out.println(interpreter.execute("DELETE FROM logs;"));

            System.out.println("\n--- Enhanced LAMBQL Command Execution Demo Complete ---");

        } catch (IOException e) {
            System.err.println("❌ An I/O error occurred during LAMB DB operation: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ An unexpected critical error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\n(Note: 'lambdb_data' directory should be empty after this run.)");
        }
    }
}