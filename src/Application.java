// Adjust package declaration if needed

import util.DatabaseInitializer;
import util.DatabasePopulator; // Import the populator
import util.DatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit; 

import dao.SongDAO;
import model.Song;
import java.util.List;

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


        // 4. TEMPORARY Test method
        printDatabaseContents(); // <-- ADD THIS LINE

        System.out.println("\nTuneUp Application initialization complete.");
        System.out.println("Ready for further implementation (DAO, Services, UI)...");

        // --> Future JavaFX application launch logic would go here <--
    }

    /**
     * TEMPORARY method for all tests 
     * Used for verification purposes after database initialization and population.
     */
    private static void printDatabaseContents() {
        // Test to query and print all entries from the 'songs' table.
        System.out.println("\n--- Printing Database Contents ---");

        String selectAllSQL = "SELECT id, title, artist, genre, duration, audio_file_path, lyrics_file_path FROM songs ORDER BY artist, title";

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
                long duration = rs.getLong("duration");
                String durationFormatted = String.format("%d:%02d", TimeUnit.MILLISECONDS.toMinutes(duration), TimeUnit.MILLISECONDS.toSeconds(duration) % 60);
                String audioPath = rs.getString("audio_file_path");
                String lyricsPath = rs.getString("lyrics_file_path"); // Can be null

                // Print the retrieved data
                System.out.printf("ID: %d | Title: %s | Artist: %s | Genre: %s | Duration: %s | Audio: %s | Lyrics: %s%n",
                                  id,
                                  title,
                                  artist,
                                  (genre != null ? genre : "N/A"), // Handle null genre nicely
                                  durationFormatted,
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

        System.out.println("\n--- Testing getAllSongs() ---");
        List<Song> allSongs = SongDAO.getAllSongs();
        if (allSongs.isEmpty()) {
            System.out.println("No songs found in the database.");
        } else {
            for (Song song : allSongs) {
                System.out.println(song);
            }
        }

        // Test getAllSongs() method
        System.out.println("\n--- Testing getAllSongs() ---");
        
        // Test findSongsByCriteria() method
        System.out.println("\n--- Testing findSongsByCriteria() ---");

        // Example search: search for songs with 'love' in title or artist, no genre filter
        List<Song> searchResults1 = SongDAO.findSongsByCriteria("love", null);
        System.out.println("Search for 'love' (no genre filter):");
        if (searchResults1.isEmpty()) {
            System.out.println("No matching songs found.");
        } else {
            for (Song song : searchResults1) {
                System.out.println(song);
            }
        }

        // Example search: search for songs with 'john' in title or artist, genre filter 'Pop'
        List<Song> searchResults2 = SongDAO.findSongsByCriteria("john", "Pop");
        System.out.println("\nSearch for 'john' with genre 'Pop':");
        if (searchResults2.isEmpty()) {
            System.out.println("No matching songs found.");
        } else {
            for (Song song : searchResults2) {
                System.out.println(song);
            }
        }

        // Example search: no search text, genre filter 'Rock'
        List<Song> searchResults3 = SongDAO.findSongsByCriteria(null, "Rock");
        System.out.println("\nSearch with genre 'Rock' only:");
        if (searchResults3.isEmpty()) {
            System.out.println("No matching songs found.");
        } else {
            for (Song song : searchResults3) {
                System.out.println(song);
            }
        }

        // Example search: no search text, no genre filter (should return all songs)
        List<Song> searchResults4 = SongDAO.findSongsByCriteria(null, null);
        System.out.println("\nSearch with no filters (all songs):");
        if (searchResults4.isEmpty()) {
            System.out.println("No songs found in the database.");
        } else {
            for (Song song : searchResults4) {
                System.out.println(song);
            }
        }
    }
}
