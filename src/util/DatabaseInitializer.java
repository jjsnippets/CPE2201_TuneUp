package util;

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
    private DatabaseInitializer() {
        // This constructor is intentionally empty to prevent instantiation.
    }

    /**
     * Initializes the database schema by creating the 'songs' table if it doesn't already exist.
     * <p>
     * The 'songs' table is defined with the following columns:
     * <ul>
     *   <li>{@code id}: INTEGER, PRIMARY KEY, AUTOINCREMENT - Unique identifier for each song.</li>
     *   <li>{@code title}: TEXT, NOT NULL - The title of the song.</li>
     *   <li>{@code artist}: TEXT, NOT NULL - The artist of the song.</li>
     *   <li>{@code genre}: TEXT - The genre of the song (nullable).</li>
     *   <li>{@code duration}: INTEGER, NOT NULL - The duration of the song in milliseconds (must be positive).</li>
     *   <li>{@code offset}: INTEGER - The global lyric offset in milliseconds (nullable).</li>
     *   <li>{@code audio_file_path}: TEXT, NOT NULL, UNIQUE - The file path to the audio file (must be unique).</li>
     *   <li>{@code lyrics_file_path}: TEXT - The file path to the lyrics file (nullable).</li>
     * </ul>
     * This method obtains its own database connection and ensures it is closed after the operation.
     * If any {@link SQLException} occurs during the schema initialization, a {@link RuntimeException}
     * is thrown to indicate a critical failure in setting up the database.
     * </p>
     */
    public static void initializeDatabaseSchema() {
        // SQL statement to create the 'songs' table.
        // Uses "IF NOT EXISTS" to avoid errors if the table is already present.
        String createSongsTableSQL = """
            CREATE TABLE IF NOT EXISTS songs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                genre TEXT,
                duration INTEGER NOT NULL, -- duration in milliseconds (NOT NULL)
                offset INTEGER,   -- global offset in milliseconds (nullable)
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
