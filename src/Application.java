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




// Imports needed for the SongLyrics test
import util.LrcParser; // Import LrcParser
import model.LyricLine;
import model.SongLyrics;
import java.io.IOException; // For potential parsing errors
import java.nio.file.InvalidPathException; // For potential path errors
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
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


        // 4. TEMPORARY Test methods
        printDatabaseContents();
        testSongLyrics(); 

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

        String selectAllSQL = "SELECT id, title, artist, genre, duration, offset, audio_file_path, lyrics_file_path FROM songs ORDER BY artist, title";

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
                Integer duration = rs.getInt("duration");
                    String durationFormatted = String.format("%d:%02d", TimeUnit.MILLISECONDS.toMinutes(duration), TimeUnit.MILLISECONDS.toSeconds(duration) % 60);
                long offsetVal = rs.getLong("offset");
                    Long offset = rs.wasNull() ? null : offsetVal;
                String audioPath = rs.getString("audio_file_path");
                String lyricsPath = rs.getString("lyrics_file_path"); // Can be null

                // Print the retrieved data
                System.out.printf("ID: %d | Title: %s | Artist: %s | Genre: %s | Duration: (%d ms) %-7s | Offset: %-6s ms | Audio: %s | Lyrics: %s%n",
                                    id,
                                    title,
                                    artist,
                                    (genre != null ? genre : "N/A"), // Handle null genre nicely
                                    duration,
                                    durationFormatted,
                                    (offset != null ? offset : "N/A"), // Handle null offset nicely
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

    private static void testSongLyrics() {
        System.out.println("\n--- Testing SongLyrics Functionality (using first DB song) ---");

        // 1. Get the first song from the database
        List<Song> allSongs = SongDAO.getAllSongs();
        if (allSongs.isEmpty()) {
            System.out.println("Database is empty. Cannot test SongLyrics with DB data.");
            return;
        }
        Song firstSong = allSongs.get(0);
        System.out.println("Testing with Song: " + firstSong.getTitle() + " - " + firstSong.getArtist());

        // 2. Check if the song has a lyrics file path
        String lyricsFilePath = firstSong.getLyricsFilePath();
        if (lyricsFilePath == null || lyricsFilePath.trim().isEmpty()) {
            System.out.println("First song in DB has no lyrics file path associated. Cannot test lyrics parsing/timing.");
            return;
        }
        System.out.println("Using Lyrics File: " + lyricsFilePath);

        // 3. Parse the lyrics file using LrcParser
        SongLyrics loadedLyrics = null;
        try {
            // Use the parser that returns the SongLyrics object directly
            loadedLyrics = LrcParser.parseLyrics(lyricsFilePath);
            System.out.println("Successfully parsed lyrics. Offset found: " + loadedLyrics.getOffsetMillis() + "ms. Lines found: " + loadedLyrics.getSize());
        } catch (IOException | InvalidPathException e) {
            System.err.println("Error parsing lyrics file '" + lyricsFilePath + "': " + e.getMessage());
            return; // Cannot proceed with testing if parsing failed
        } catch (Exception e) {
            // Catch any other unexpected errors during parsing
             System.err.println("Unexpected error during lyrics parsing for '" + lyricsFilePath + "': " + e.getMessage());
             e.printStackTrace();
             return;
        }

        // 4. Perform tests using the loaded SongLyrics object

        // Basic checks
        System.out.println("isEmpty check: " + loadedLyrics.isEmpty());

        // Test getLineAtTime and getIndexAtTime at various points
        List<LyricLine> lines = loadedLyrics.getLines();
        long offset = loadedLyrics.getOffsetMillis();

        if (lines.isEmpty()) {
            System.out.println("Lyrics file parsed, but contained no timed lines. Testing at arbitrary time.");
            long testTime = 15000; // 15 seconds
            System.out.println("Time: " + testTime + "ms -> Expected: null / -1 | Actual Line: " + loadedLyrics.getLineAtTime(testTime) + " | Actual Index: " + loadedLyrics.getIndexAtTime(testTime));
        } else {
            // Select meaningful time points based on the actual loaded lyrics
            long timeBeforeFirst = 0;
            long firstLineEffectiveTime = lines.get(0).getTimestampMillis() + offset;
            long timeAtFirst = Math.max(0, firstLineEffectiveTime); // Ensure time is not negative

            long lastLineEffectiveTime = lines.get(lines.size() - 1).getTimestampMillis() + offset;
            long timeAtLast = Math.max(0, lastLineEffectiveTime);

            // Calculate a time roughly in the middle
            long timeMiddle = (lines.size() > 1) ? (timeAtFirst + timeAtLast) / 2 : timeAtFirst;

            long timeAfterLast = timeAtLast + 10000; // 10 seconds after the last line's timestamp

            // Create a list of times to test
            List<Long> testTimes = List.of(
                timeBeforeFirst,
                Math.max(0, timeAtFirst - 1), // Just before the first line hits
                timeAtFirst,                  // Exactly when the first line hits
                timeMiddle,                   // Somewhere in the middle
                Math.max(0, timeAtLast - 1),  // Just before the last line hits (if different from first)
                timeAtLast,                   // Exactly when the last line hits
                timeAfterLast                 // Well after the last line
            );

             System.out.println("\nTesting getLineAtTime / getIndexAtTime:");
            for (long time : testTimes) {
                // Get the expected line/index (for easier visual comparison)
                LyricLine expectedLine = null;
                int expectedIndex = -1;
                 for (int i = 0; i < lines.size(); i++) {
                     LyricLine line = lines.get(i);
                     long effectiveTimestamp = line.getTimestampMillis() + offset;
                     if (effectiveTimestamp <= time) {
                         expectedLine = line;
                         expectedIndex = i;
                     } else {
                         break;
                     }
                 }

                LyricLine actualLine = loadedLyrics.getLineAtTime(time);
                int actualIndex = loadedLyrics.getIndexAtTime(time);

                System.out.printf("Time: %-7d ms -> Expected Line: %-40s | Expected Idx: %-2d | Actual Line: %-40s | Actual Idx: %-2d%n",
                                 time,
                                 (expectedLine != null ? expectedLine.getText() : "null"),
                                 expectedIndex,
                                 (actualLine != null ? actualLine.getText() : "null"),
                                 actualIndex
                                 );
            }
        }

        System.out.println("\n--- End of SongLyrics Testing ---");
    }
}
