# LAMB DB: Layered, Atomic, Map-Based Database

![Java](https://img.shields.io/badge/Java-21-darkorange?style=for-the-badge&logo=java)
![Status](https://img.shields.io/badge/Status-Running-brightgreen?style=for-the-badge)
![Type](https://img.shields.io/badge/Type-Prototype-blue?style=for-the-badge)
![Build](https://img.shields.io/badge/Build-Maven-blueviolet?style=for-the-badge&logo=apache-maven)
![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)
![Language](https://img.shields.io/badge/Language-Java-red?style=for-the-badge)

**LambDB**, which stands for **L**ayered, **A**tomic, **M**ap-**B**ased **D**atabase, is a lightweight, command-line driven NoSQL database prototype built entirely in Java. It serves as a foundational demonstration of key NoSQL principles, offering schemaless document storage, flexible querying capabilities, and an internal hash-based indexing strategy for efficient data retrieval. LambDB provides a custom, intuitive query language called LAMBQL for all database interactions.

---

## 📜 What is LAMBQL?

**LAMBQL** LAMB Query Language is the custom domain-specific language designed for interacting with LambDB. It provides a simple, human-readable syntax that mirrors common NoSQL database operations, making it easy to create collections, insert documents, query data, and perform updates or deletions. LAMBQL aims to be intuitive for anyone familiar with document-oriented databases.

---

## ✨ Features

* **Command-Line Interface (CLI):** Fully interactive console for direct database operations.
* **Schemaless Document Storage:** Store flexible JSON-like documents without needing to pre-define their structure.
* **Collections:** Organize your documents into logical groupings, similar to tables in relational databases.
* **Full CRUD Operations:**
    * `CREATE COLLECTION <collectionName>;`: Set up new document collections.
    * `INSERT INTO <collectionName> VALUES <jsonDocument>;`: Add new JSON documents.
    * `SELECT [field1, field2, ...] FROM <collectionName> [WHERE <jsonFilter>];`: Retrieve documents with powerful filtering options and specific field projection.
    * `UPDATE <collectionName> SET <jsonUpdateData> WHERE <jsonFilter>;`: Modify existing documents that match a given filter.
    * `DELETE FROM <collectionName> [WHERE <jsonFilter>];`: Remove specific documents or an entire collection.
* **Advanced Filtering Operators:** Support for `$gt` (greater than), `$lt` (less than), `$ne` (not equal), and `$in` (value in array) for more sophisticated queries.
* **Hash-Based Indexing:** An internal indexing strategy that utilizes hash maps to speed up document lookups on frequently queried fields, improving performance compared to full collection scans.
* **Automated Demo Mode:** A built-in feature to quickly showcase a comprehensive set of LAMBQL commands and functionalities.

---

## 🚀 Getting Started

To get LambDB up and running on your system:

### Prerequisites

* **Java Development Kit (JDK):** Version 17 or higher (tested with Java 21). You can download it from [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/install/).
* **Git:** For cloning the repository.
* **Maven:** While not strictly required for running the pre-built JAR, it's essential if you plan to build the project from source. IntelliJ IDEA conveniently bundles its own Maven distribution.

### 1. Download the Executable JAR

The quickest way to start is by downloading the latest pre-built executable JAR file from the [Releases](https://github.com/awkward-student/LambDB-NoSQL-Prototype/releases) section of this repository.

* Download `lambdb-nosql-prototype-1.0.0-SNAPSHOT.jar`.

### 2. Run the Application

Once downloaded, open your terminal or command prompt, navigate to the directory where you saved the JAR file, and execute the following command:

```bash
  java -jar lambdb-nosql-prototype-1.0.0-SNAPSHOT.jar
```

### 3. Using the CLI

Upon launching the application, you will be greeted with the initial prompt:
```bash
--- Welcome to LAMB DB ---
Type 'initializecli' to begin interactive console mode.
Type 'help' for an introduction to LAMBQL and commands.
Type 'exit' to quit the application.
```

* `initializecli`: Type this to enter the interactive LAMBQL console, where you can issue database commands.
* `help`: Provides detailed information on all LAMBQL syntax and special console commands, including how to run the automated demo.
* `exit`: Quits the entire LambDB application directly from this initial prompt.
* `demo`: (Not explicitly listed, but functional and described in help) Runs a comprehensive automated demonstration of all LambDB features and LAMBQL commands.

## 🛠️ Building from Source (for Developers)

If you wish to delve into the codebase or build the project from its source:

### 1. Clone the repository:

```bash
git clone [https://github.com/awkward-student/LambDB-NoSQL-Prototype.git](https://github.com/awkward-student/LambDB-NoSQL-Prototype.git)
cd LambDB-NoSQL-Prototype
```

### Build with maven:

```bash
mvn clean package
```

The executable JAR will be generated and placed in the target/ directory.

## 🤝 Contributing

Contributions are highly welcome! If you encounter any bugs, have suggestions for new features, or wish to improve the codebase, please feel free to:
* Open an issue to report problems or propose ideas.
* Fork the repository, create a feature branch, and submit a pull request.

## 📄 License

This project is open-source and distributed under the MIT License.