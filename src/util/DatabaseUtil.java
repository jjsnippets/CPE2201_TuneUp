package util; // Define the package for utility classes

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utility class for managing SQLite database connections for the TuneUp application.
 * This class provides a centralized method to obtain a connection to the database file.
 * It addresses the requirements for using SQLite (SRS 2.3) via JDBC (SRS 3.1.3).
 */
public class DatabaseUtil {

    // Define the database URL.
    // This specifies the JDBC protocol (jdbc), the database type (sqlite),
    // and the path to the database file (tuneup.db).
    // The file will be created in the project's root directory if it doesn't exist.
    // SRS Section 2.3, 3.1.3
    private static final String DATABASE_URL = "jdbc:sqlite:tuneup.db";

    // Static block to explicitly load the SQLite JDBC driver when the class is loaded.
    // While modern JDBC drivers often auto-register, explicit loading is safer practice.
    // SRS Section 2.4 (Dependencies - SQLite JDBC Driver)
    static {
        try {
            // Load the SQLite JDBC driver class.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            // Handle the error if the driver class is not found.
            // This usually indicates the JDBC driver JAR is missing from the classpath.
            System.err.println("SQLite JDBC driver not found. Ensure it's included in the project libraries.");
            // Throwing a runtime exception might be preferable in a full application
            // to halt execution if the database is essential.
             throw new RuntimeException("Failed to load SQLite JDBC driver", e);
        }
    }

    /**
     * Establishes and returns a connection to the SQLite database.
     * The caller is responsible for closing the connection when finished,
     * preferably using a try-with-resources statement.
     *
     * @return A Connection object to the database.
     * @throws SQLException if a database access error occurs or the URL is null.
     */
    public static Connection getConnection() throws SQLException {
        // DriverManager attempts to establish a connection to the database URL.
        // If the database file doesn't exist, SQLite JDBC driver will create it.
        return DriverManager.getConnection(DATABASE_URL);
    }

    // Private constructor to prevent instantiation of this utility class.
    private DatabaseUtil() {
        // This class is not meant to be instantiated.
    }
}
