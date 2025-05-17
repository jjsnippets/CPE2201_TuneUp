package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utility class for managing SQLite database connections for the TuneUp application.
 * This non-instantiable class provides a centralized static method to obtain a connection
 * to the application's database file, ensuring consistent configuration and access.
 *
 * Key functionalities and SRS alignments:
 * <ul>
 *   <li>Provides database connections: {@link #getConnection()}
 *   <li>Uses SQLite as the database engine (SRS 2.3).
 *   <li>Interacts with the database via JDBC (SRS 3.1.3).
 *   <li>Manages the SQLite JDBC driver dependency (SRS 2.4).
 * </ul>
 */
public final class DatabaseUtil { // Added final keyword as it's a utility class with only static members and private constructor

    // Define the database URL.
    // This specifies the JDBC protocol (jdbc), the database type (sqlite),
    // and the path to the database file (tuneup.db).
    // The file will be created in the project's root directory if it doesn't exist.
    // Corresponds to SRS 2.3 (SQLite usage) and SRS 3.1.3 (JDBC for database interaction).
    private static final String DATABASE_URL = "jdbc:sqlite:tuneup.db";

    /**
     * Private constructor to prevent instantiation of this utility class.
     * All members of this class are static, and it is not intended to be instantiated.
     */
    private DatabaseUtil() {
        // This class is not meant to be instantiated.
    }

    // Static initializer block to explicitly load the SQLite JDBC driver when the class is loaded.
    // While modern JDBC drivers (version 4.0 and above) often auto-register via the
    // Service Provider Interface (SPI) mechanism, explicit loading with Class.forName()
    // provides an additional layer of certainty and can help diagnose classpath issues early.
    // Corresponds to SRS 2.4 (Dependencies - SQLite JDBC Driver).
    static {
        try {
            // Load the SQLite JDBC driver class.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            // Critical error: The application cannot function without database access.
            System.err.println("Fatal Error: SQLite JDBC driver (org.sqlite.JDBC) not found. " +
                               "Ensure the driver JAR is correctly included in the project's classpath.");
            // Throwing a RuntimeException to halt execution as the database is essential.
            throw new RuntimeException("Failed to load SQLite JDBC driver, application cannot continue.", e);
        }
    }

    /**
     * Establishes and returns a new connection to the SQLite database defined by {@link #DATABASE_URL}.
     * It is the responsibility of the caller to close this connection when it is no longer needed,
     * typically using a try-with-resources statement to ensure proper resource management and
     * prevent connection leaks.
     *
     * @return A {@link Connection} object to the SQLite database.
     * @throws SQLException if a database access error occurs (e.g., the database URL is malformed,
     *                      or the database server is unavailable) or the URL is {@code null}.
     */
    public static Connection getConnection() throws SQLException {
        // DriverManager attempts to establish a connection to the given database URL.
        // If the database file (tuneup.db) doesn't exist, the SQLite JDBC driver will create it.
        return DriverManager.getConnection(DATABASE_URL);
    }
}
