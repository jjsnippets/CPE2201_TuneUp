package util; // Define the package for utility classes

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility class responsible for initializing the database schema for the TuneUp application.
 * Creates necessary tables if they do not already exist.
 * Addresses SRS requirement for SQLite backend (2.3, 3.1.3) and setting up the structure
 * to store Song metadata (implicitly required by FR2.1).
 */
public class DatabaseInitializer {

    // Private constructor to prevent instantiation of this utility class.
    private DatabaseInitializer() {}

    /**
     * Initializes the database schema. Currently, creates the 'songs' table
     * if it does not exist.
     * This method obtains its own database connection and closes it afterwards.
     */
    public static void initializeDatabaseSchema() {
        // SQL statement to create the 'songs' table.
        // Uses "IF NOT EXISTS" to avoid errors if the table is already present.
        // Column names use snake_case which is common in SQL.
        // Mapping to Java object fields (camelCase) will be handled by the DAO.
        String createSongsTableSQL = """
            CREATE TABLE IF NOT EXISTS songs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                genre TEXT,
                audio_file_path TEXT NOT NULL UNIQUE,
                lyrics_file_path TEXT
            );
            """;
            // The UNIQUE constraint on audio_file_path prevents duplicate song entries based on the audio file.

        // Use try-with-resources to ensure the connection and statement are closed automatically.
        try (Connection conn = DatabaseUtil.getConnection(); // Get a connection from our utility
             Statement stmt = conn.createStatement()) { // Create a statement object

            // Execute the SQL statement to create the table.
            stmt.execute(createSongsTableSQL);
            System.out.println("Database schema verified/initialized successfully. 'songs' table is ready.");

        } catch (SQLException e) {
            // Handle potential errors during database access or SQL execution.
            System.err.println("Error initializing database schema: " + e.getMessage());
            e.printStackTrace();
            // In a real application, might throw a custom exception or handle more gracefully.
            throw new RuntimeException("Failed to initialize database schema.", e);
        }
    }
}
