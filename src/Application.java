// Adjust package declaration if needed

import util.DatabaseInitializer;
import util.DatabasePopulator; // Import the populator
import util.DatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;

import java.sql.ResultSet;
import java.sql.Statement; 

/**
 * Main application class for TuneUp.
 */
public class Application {

    // Define the directory where song files (mp3, lrc) are located.
    private static final String SONGS_DIRECTORY = "songs";

    /**
     * Main entry point. Initializes DB, populates from LRC files, prepares for app launch.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        System.out.println("TuneUp Application Starting...");

        // 1. Test basic database connectivity
        try (Connection connection = DatabaseUtil.getConnection()) {
            if (connection != null && !connection.isClosed()) {
                System.out.println("Successfully established initial connection to the SQLite database (tuneup.db).");
            } else {
                System.err.println("Failed to establish a valid initial database connection.");
                return;
            }
        } catch (SQLException e) {
            System.err.println("Database connection error during initial check: " + e.getMessage());
            e.printStackTrace();
            return;
        } catch (RuntimeException e) {
             System.err.println("Application Initialization Failed (Driver Loading?): " + e.getMessage());
             e.printStackTrace();
             return;
        }

        // 2. Initialize the database schema
        try {
            DatabaseInitializer.initializeDatabaseSchema();
        } catch (RuntimeException e) {
            System.err.println("Failed to initialize database schema. Application cannot continue.");
            e.printStackTrace();
            return;
        }

        // 3. Populate the database from LRC/MP3 files in the specified directory
        System.out.println("\nAttempting to populate database from directory: " + SONGS_DIRECTORY);
        try {
            // Call the populator utility method
            int songsAdded = DatabasePopulator.populateFromLrcFiles(SONGS_DIRECTORY);
            // The summary is printed within the populateFromLrcFiles method now.
            // System.out.println("Finished populating database. Added " + songsAdded + " new songs.");
        } catch (Exception e) {
            // Catch any unexpected errors during population process
            System.err.println("An unexpected error occurred during database population: " + e.getMessage());
            e.printStackTrace();
            // Decide whether to continue or exit based on severity
            // return; // Optionally exit if population is critical
        }


        // 4. TEMPORARY: Print database contents for verification
        printDatabaseContents(); // <-- ADD THIS LINE

        System.out.println("\nTuneUp Application initialization complete.");
        System.out.println("Ready for further implementation (DAO, Services, UI)...");

        // --> Future JavaFX application launch logic would go here <--
    }

    /**
     * TEMPORARY method to query and print all entries from the 'songs' table.
     * Used for verification purposes after database initialization and population.
     */
    private static void printDatabaseContents() {
        System.out.println("\n--- Printing Database Contents ---");

        String selectAllSQL = "SELECT id, title, artist, genre, audio_file_path, lyrics_file_path FROM songs ORDER BY artist, title";

        // Use try-with-resources for automatic resource management
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectAllSQL)) { // Execute the query

            int count = 0;
            // Loop through the result set
            while (rs.next()) {
                count++;
                // Retrieve data by column name
                int id = rs.getInt("id");
                String title = rs.getString("title");
                String artist = rs.getString("artist");
                String genre = rs.getString("genre"); // Can be null
                String audioPath = rs.getString("audio_file_path");
                String lyricsPath = rs.getString("lyrics_file_path"); // Can be null

                // Print the retrieved data
                System.out.printf("ID: %d | Title: %s | Artist: %s | Genre: %s | Audio: %s | Lyrics: %s%n",
                                  id,
                                  title,
                                  artist,
                                  (genre != null ? genre : "N/A"), // Handle null genre nicely
                                  audioPath,
                                  (lyricsPath != null ? lyricsPath : "N/A")); // Handle null lyrics path nicely
            }

            if (count == 0) {
                System.out.println("The 'songs' table is currently empty.");
            } else {
                System.out.println("----------------------------------");
                System.out.println("Total songs found in database: " + count);
            }

        } catch (SQLException e) {
            System.err.println("Error querying or printing database contents: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("--- End of Database Contents ---");
    }

    // ... main method definition ...
}


