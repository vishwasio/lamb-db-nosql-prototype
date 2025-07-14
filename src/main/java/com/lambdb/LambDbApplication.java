package com.lambdb;

import com.lambdb.core.DBConstants;
import com.lambdb.core.LambDbInterpreter;
import com.lambdb.core.DBManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Scanner;
import java.util.stream.Stream;

/**
 * LambDbApplication.java
 * Author: Vishwas Karode
 * Description:
 * Main application class for LAMB DB.
 * This version introduces an interactive console, a 'help' command, and an 'exit' command.
 * It provides a streamlined startup experience, with the automated demo accessible via 'help'.
 * This version is adapted to work with LambDbInterpreter's execute(String) method and constructor,
 * and DBManager's no-argument constructor.
 */
public class LambDbApplication {

    public static void main(String[] args) {
        // Use try-with-resources for Scanner to ensure it's closed automatically
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("--- Welcome to LAMB DB ---");
            System.out.println("Type 'initializecli' to begin interactive console mode.");
            System.out.println("Type 'help' for an introduction to LAMBDB - LAMBQL and commands.");
            System.out.println("Type 'exit' to quit the application.");

            DBManager dbManager = new DBManager(); // Initialize DBManager using its NO-ARGUMENT constructor
            LambDbInterpreter interpreter = new LambDbInterpreter(dbManager); // Pass dbManager to interpreter constructor

            String initialCommand;
            while (true) {
                System.out.print("> "); // Prompt for initial command
                initialCommand = scanner.nextLine().trim().toLowerCase();

                if ("initializecli".equals(initialCommand)) {
                    System.out.println("\n--- Starting Interactive Console ---");
                    startInteractiveConsole(scanner, interpreter);
                    break; // Exit main loop after console finishes
                } else if ("help".equals(initialCommand)) {
                    printHelp();
                    // After printing help, loop back to prompt for initial command again
                } else if ("exit".equals(initialCommand)) {
                    System.out.println("Exiting LAMB DB. Goodbye!");
                    break; // Exit main loop immediately
                } else if ("demo".equals(initialCommand)) { // "demo" command is still functional but not listed upfront
                    System.out.println("\n--- Running Automated Demo ---");
                    deleteDataDirectory(); // Ensure clean slate for demo
                    // Re-initialize DBManager and Interpreter after deleting data
                    dbManager = new DBManager();
                    interpreter = new LambDbInterpreter(dbManager);
                    runAutomatedDemo(interpreter);
                    System.out.println("\n--- Automated Demo Complete. Starting Interactive Console. ---");
                    startInteractiveConsole(scanner, interpreter);
                    break; // Exit main loop after demo and console finish
                } else {
                    System.out.println("Unrecognized command. Please type 'initializecli', 'help', or 'exit'.");
                }
            }
        } catch (IOException e) {
            System.err.println("A critical error occurred during DB initialization or operation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Runs the interactive console loop.
     *
     * @param scanner The Scanner object for user input.
     * @param interpreter The LambDbInterpreter instance.
     */
    private static void startInteractiveConsole(Scanner scanner, LambDbInterpreter interpreter) {
        System.out.println("Type your LAMBQL commands. Type 'help' for syntax, 'exit' to quit console.");
        String command;
        while (true) {
            System.out.print("LAMBDB> ");
            command = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(command)) {
                System.out.println("Exiting LAMB DB interactive console. Goodbye!");
                break; // Exit the interactive console loop
            } else if ("help".equalsIgnoreCase(command)) {
                printHelp(); // Display help message
            } else if ("clear".equalsIgnoreCase(command)) {
                System.out.print("\033[H\033[2J"); // ANSI escape codes for clear screen
                System.out.flush();
                System.out.println("--- Console Cleared ---");
                System.out.println("Type 'help' for syntax, 'exit' to quit console.");
            }
            else {
                // Call execute and print its returned String
                String result = interpreter.execute(command);
                System.out.println(result);
            }
        }
    }

    /**
     * Runs a predefined set of LAMBQL commands as a demonstration.
     *
     * @param interpreter The LambDbInterpreter instance.
     */
    private static void runAutomatedDemo(LambDbInterpreter interpreter) {
        String[] demoCommands = {
                "CREATE COLLECTION users;",
                "CREATE COLLECTION products;",
                "CREATE COLLECTION logs;",

                "INSERT INTO users VALUES { \"name\": \"Alice\", \"age\": 30, \"city\": \"New York\", \"email\": \"alice@example.com\", \"status\": \"active\" };",
                "INSERT INTO users VALUES { \"_id\": \"user_002_vk\", \"name\": \"Bob\", \"age\": 24, \"city\": \"London\", \"email\": \"bob@example.com\", \"status\": \"inactive\" };",
                "INSERT INTO users VALUES { \"name\": \"Charlie\", \"age\": 35, \"city\": \"New York\", \"email\": \"charlie@example.com\", \"status\": \"active\" };",
                "INSERT INTO users VALUES { \"name\": \"David\", \"age\": 24, \"city\": \"London\", \"email\": \"david@example.com\", \"status\": \"active\" };",
                "INSERT INTO users VALUES { \"name\": \"Eve\", \"age\": 42, \"city\": \"Paris\", \"email\": \"eve@example.com\", \"status\": \"inactive\" };",

                "INSERT INTO products VALUES { \"productName\": \"Laptop\", \"price\": 1200.00, \"stock\": 50, \"category\": \"Electronics\" };",
                "INSERT INTO products VALUES { \"productName\": \"Keyboard\", \"price\": 75.50, \"stock\": 200, \"category\": \"Peripherals\" };",
                "INSERT INTO products VALUES { \"productName\": \"Mouse\", \"price\": 25.00, \"stock\": 300, \"category\": \"Peripherals\" };",
                "INSERT INTO products VALUES { \"productName\": \"Monitor\", \"price\": 300.00, \"stock\": 80, \"category\": \"Electronics\" };",

                "INSERT INTO logs VALUES { \"timestamp\": 1678886400000, \"event\": \"UserLogin\", \"userId\": \"user_001\" };",
                "INSERT INTO logs VALUES { \"timestamp\": 1678886400001, \"event\": \"SessionStart\", \"userId\": \"user_001\" };",
                "INSERT INTO logs VALUES { \"timestamp\": 1681564800000, \"event\": \"ProductView\", \"userId\": \"user_003\", \"productId\": \"prod_002\" };",
                "INSERT INTO logs VALUES { \"timestamp\": 1690000000000, \"event\": \"Checkout\", \"userId\": \"user_001\" };",

                "\n--- SELECT with PROJECTION ---",
                "SELECT name, email FROM users;",
                "SELECT productName, price FROM products;",
                "SELECT event FROM logs;",
                "SELECT name FROM users WHERE {\"city\": \"London\"};", // This should use index

                "\n--- SELECT with ADVANCED FILTERING OPERATORS ---",
                "SELECT * FROM users WHERE {\"age\": {\"$gt\": 30}};",
                "SELECT productName, price FROM products WHERE {\"price\": {\"$gt\": 100.00}};",
                "SELECT * FROM logs WHERE {\"timestamp\": {\"$gt\": 1680000000000}};",
                "SELECT name, age FROM users WHERE {\"age\": {\"$lt\": 30}};",
                "SELECT productName, stock FROM products WHERE {\"stock\": {\"$lt\": 100}};",
                "SELECT * FROM users WHERE {\"city\": {\"$ne\": \"New York\"}};",
                "SELECT * FROM products WHERE {\"category\": {\"$ne\": \"Electronics\"}};",
                "SELECT * FROM users WHERE {\"city\": {\"$in\": [\"New York\", \"Paris\"]}};",
                "SELECT * FROM users WHERE {\"status\": {\"$in\": [\"active\"]}};", // This should use index
                "SELECT * FROM products WHERE {\"productName\": {\"$in\": [\"Laptop\", \"Mouse\"]}};",

                "\n--- SELECT with COMBINED FILTERS (Implicit AND) ---",
                "SELECT * FROM users WHERE {\"city\": \"New York\", \"age\": {\"$gt\": 30}};", // Index on city
                "SELECT name, email FROM users WHERE {\"age\": {\"$lt\": 30}, \"status\": \"active\"};", // Index on status

                "\n--- UPDATE with Advanced Filters ---",
                "UPDATE users SET {\"status\": \"premium\"} WHERE {\"age\": {\"$gt\": 30}};",
                "SELECT name, age, status FROM users WHERE {\"status\": \"premium\"};", // This should use index
                "UPDATE products SET {\"stock\": 0} WHERE {\"stock\": {\"$lt\": 60}};",
                "SELECT productName, stock FROM products WHERE {\"stock\": 0};",

                "\n--- DELETE with Advanced Filters ---",
                "DELETE FROM users WHERE {\"age\": {\"$ne\": 24}, \"city\": \"London\"};", // This query will now correctly delete 0
                "DELETE FROM users WHERE {\"age\": {\"$lt\": 25}};", // This should use index indirectly
                "SELECT * FROM users;",
                "DELETE FROM logs WHERE {\"timestamp\": {\"$lt\": 1680000000000}};", // This should use index indirectly
                "SELECT * FROM logs;",

                "\n--- Cleaning Up Collections ---",
                "DELETE FROM users;",
                "DELETE FROM products;",
                "DELETE FROM logs;"
        };

        for (String command : demoCommands) {
            System.out.println("\n[Interpreter] Executing LAMBQL command: " + command);
            String result = interpreter.execute(command);
            System.out.println(result);
        }
        System.out.println("\n--- Enhanced LAMBQL Command Execution Demo Complete ---");
        System.out.println("(Note: 'lambdb_data' directory should be empty after this run if chosen in setup.)");
    }

    /**
     * Prints the help message for LAMBQL commands.
     */
    private static void printHelp() {
        System.out.println("\n--- LAMBQL HELP & Console Commands ---");
        System.out.println("\nTo get started:");
        System.out.println("  initializecli - Start the interactive LAMB DB console.");
        System.out.println("  help          - Display this help message.");
        System.out.println("  exit          - Quit the LAMB DB application (from initial prompt).");
        System.out.println("  demo          - Run an automated demonstration of LAMBQL commands (from initial prompt).");

        System.out.println("\nAvailable LAMBQL Commands (within the console):");
        System.out.println("  CREATE COLLECTION <collectionName>;");
        System.out.println("  INSERT INTO <collectionName> VALUES <jsonDocument>;");
        System.out.println("  SELECT [field1, field2, ...] FROM <collectionName> [WHERE <jsonFilter>];");
        System.out.println("    - Use '*' for all fields (e.g., SELECT * FROM users;)");
        System.out.println("    - Filter supports: {\"field\": \"value\"} for equality.");
        System.out.println("    - Advanced operators: {\"field\": {\"$gt\": 10}}, {\"field\": {\"$lt\": 20}}, {\"field\": {\"$ne\": \"value\"}}, {\"field\": {\"$in\": [\"val1\", \"val2\"]}}");
        System.out.println("  UPDATE <collectionName> SET <jsonUpdateData> WHERE <jsonFilter>;");
        System.out.println("  DELETE FROM <collectionName> [WHERE <jsonFilter>];");
        System.out.println("    - If no WHERE clause, deletes the entire collection (e.g., DELETE FROM myCollection;)");

        System.out.println("\nSpecial Console Commands (available within the interactive console):");
        System.out.println("  help    - Display this help message.");
        System.out.println("  exit    - Exit the current LAMB DB console session.");
        System.out.println("  clear   - Clear the console screen (if supported by your terminal).");
        System.out.println("----------------------------------------\n");
    }

    /**
     * Deletes the entire lambdb_data directory for a clean run.
     * This is crucial for the automated demo to start fresh.
     */
    private static void deleteDataDirectory() {
        Path dataPath = Paths.get(DBConstants.DB_ROOT_DIR);
        if (Files.exists(dataPath)) {
            try (Stream<Path> paths = Files.walk(dataPath)) {
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("[App] Cleaned up existing 'lambdb_data' directory.");
            } catch (IOException e) {
                System.err.println("[App] Error cleaning up data directory: " + e.getMessage());
            }
        }
    }
}