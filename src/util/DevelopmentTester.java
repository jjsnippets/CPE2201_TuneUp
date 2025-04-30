package util; // Or a dedicated test package e.g., com.yourcompany.tuneup.test

// Import all necessary classes used in the tests
import dao.SongDAO;
import model.Song;
import model.SongLyrics;
import model.LyricLine;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;
import java.nio.file.InvalidPathException;

/**
 * Contains temporary methods for testing various components during development.
 * These methods can be called after initialization to verify functionality.
 * This class and its methods should ideally be excluded from production builds.
 */
public class DevelopmentTester {

    // Private constructor to prevent instantiation
    private DevelopmentTester() {}

    /**
     * Runs all temporary development tests sequentially.
     */
    public static void runAllDevelopmentTests() {
        System.out.println("\n B--------------------------------------B ");
        System.out.println("|   RUNNING DEVELOPMENT/DEBUG TESTS    |");
        System.out.println(" E--------------------------------------E ");
        printDatabaseContents();
        testSongDAO();
        testSongLyrics();
        System.out.println("\n B--------------------------------------B ");
        System.out.println("| FINISHED DEVELOPMENT/DEBUG TESTS     |");
        System.out.println(" E--------------------------------------E ");
    }

    /**
     * TEMPORARY: Queries and prints all entries from the 'songs' table.
     * Includes basic formatting for duration and handles nulls.
     */
    private static void printDatabaseContents() {
        System.out.println("\n--- Printing Database Contents ---");
        String selectAllSQL = "SELECT id, title, artist, genre, duration, offset, audio_file_path, lyrics_file_path FROM songs ORDER BY artist, title";

        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectAllSQL)) {

            int count = 0;
            while (rs.next()) {
                count++;
                int id = rs.getInt("id");
                String title = rs.getString("title");
                String artist = rs.getString("artist");
                String genre = rs.getString("genre");
                Integer duration = rs.getInt("duration"); if (rs.wasNull()) duration = null;
                long offsetVal = rs.getLong("offset"); Long offset = rs.wasNull() ? null : offsetVal;
                String audioPath = rs.getString("audio_file_path");
                String lyricsPath = rs.getString("lyrics_file_path");

                // Format duration if not null
                String durationFormatted = "N/A";
                if (duration != null) {
                    durationFormatted = String.format("%d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(duration),
                        TimeUnit.MILLISECONDS.toSeconds(duration) % 60);
                }

                System.out.printf("ID: %-3d | Title: %-30s | Artist: %-20s | Genre: %-10s | Duration: %-7s (%d ms) | Offset: %-6s ms | Audio: %s%n",
                                  id,
                                  truncate(title, 30),
                                  truncate(artist, 20),
                                  truncate(genre != null ? genre : "N/A", 10),
                                  durationFormatted, duration != null ? duration : -1, // Show raw ms too
                                  (offset != null ? offset.toString() : "N/A"),
                                  audioPath); // Optionally truncate paths too
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

    /** Helper for cleaner printing within printDatabaseContents. */
    private static String truncate(String value, int length) {
        if (value == null) return "N/A";
        if (value.length() <= length) return value;
        return value.substring(0, length - 3) + "...";
    }

    /**
     * TEMPORARY: Tests basic SongDAO methods (getAllSongs, findSongsByCriteria).
     */
    private static void testSongDAO() {
        System.out.println("\n--- Testing SongDAO ---");

        // Test getAllSongs()
        System.out.println("Testing getAllSongs():");
        List<Song> allSongs = SongDAO.getAllSongs();
        System.out.println(" -> Found " + (allSongs != null ? allSongs.size() : "null list") + " total songs.");
        // Optionally print first few songs:
        // if (allSongs != null && !allSongs.isEmpty()) {
        //     System.out.println(" -> First song: " + allSongs.get(0));
        // }

        // Test findSongsByCriteria() - examples
        System.out.println("\nTesting findSongsByCriteria():");
        String testSearchTerm = "you"; // Example search term
        String testGenre = "Pop";       // Example genre filter

        List<Song> searchResults1 = SongDAO.findSongsByCriteria(testSearchTerm, null);
        System.out.println(" -> Search for '" + testSearchTerm + "' (any genre): Found " + searchResults1.size() + " songs.");

        List<Song> searchResults2 = SongDAO.findSongsByCriteria(null, testGenre);
        System.out.println(" -> Search for Genre '" + testGenre + "' (any title/artist): Found " + searchResults2.size() + " songs.");

        List<Song> searchResults3 = SongDAO.findSongsByCriteria(testSearchTerm, testGenre);
        System.out.println(" -> Search for '" + testSearchTerm + "' AND Genre '" + testGenre + "': Found " + searchResults3.size() + " songs.");

        List<Song> searchResults4 = SongDAO.findSongsByCriteria("", "All Genres"); // Should be same as null, null
        System.out.println(" -> Search for '' and 'All Genres': Found " + searchResults4.size() + " songs.");


        System.out.println("--- End of SongDAO Testing ---");
    }


    /**
     * TEMPORARY: Tests SongLyrics class using data from the first song in the database.
     */
    private static void testSongLyrics() {
        System.out.println("\n--- Testing SongLyrics Functionality (using first DB song) ---");

        List<Song> allSongs = SongDAO.getAllSongs();
        if (allSongs.isEmpty()) {
            System.out.println("Database is empty. Cannot test SongLyrics with DB data.");
            return;
        }
        Song firstSong = allSongs.get(0);
        System.out.println("Testing with Song: " + firstSong.getTitle() + " - " + firstSong.getArtist());

        String lyricsFilePath = firstSong.getLyricsFilePath();
        if (lyricsFilePath == null || lyricsFilePath.trim().isEmpty()) {
            System.out.println("First song has no lyrics file path. Skipping lyrics timing test.");
            return;
        }
        System.out.println("Using Lyrics File: " + lyricsFilePath);

        SongLyrics loadedLyrics;
        try {
            loadedLyrics = LrcParser.parseLyrics(lyricsFilePath);
            System.out.println("Parsed lyrics. Offset: " + loadedLyrics.getOffsetMillis() + "ms. Lines: " + loadedLyrics.getSize());
        } catch (IOException | InvalidPathException e) {
            System.err.println("Error parsing lyrics file '" + lyricsFilePath + "': " + e.getMessage());
            return;
        } catch (Exception e) {
             System.err.println("Unexpected error during lyrics parsing for '" + lyricsFilePath + "': " + e.getMessage());
             e.printStackTrace();
             return;
        }

        // Perform timing tests if lines were found
        List<LyricLine> lines = loadedLyrics.getLines();
        long offset = loadedLyrics.getOffsetMillis();

        if (lines.isEmpty()) {
            System.out.println("Lyrics file contained no timed lines.");
        } else {
            // Select meaningful time points based on the actual loaded lyrics
            long firstLineEffectiveTime = lines.get(0).getTimestampMillis() + offset;
            long lastLineEffectiveTime = lines.get(lines.size() - 1).getTimestampMillis() + offset;
            List<Long> testTimes = List.of(
                0L,                                     // Start
                Math.max(0, firstLineEffectiveTime - 1), // Just before first
                Math.max(0, firstLineEffectiveTime),     // Exactly first
                Math.max(0, (firstLineEffectiveTime + lastLineEffectiveTime) / 2), // Middle
                Math.max(0, lastLineEffectiveTime),      // Exactly last
                lastLineEffectiveTime + 10000             // After last
            );

             System.out.println("\nTesting getLineAtTime / getIndexAtTime:");
            for (long time : testTimes) {
                LyricLine actualLine = loadedLyrics.getLineAtTime(time);
                int actualIndex = loadedLyrics.getIndexAtTime(time);
                System.out.printf("Time: %-7d ms -> Actual Line: %-40s | Actual Idx: %-2d%n",
                                 time,
                                 truncate(actualLine != null ? actualLine.getText() : "null", 40),
                                 actualIndex);
            }
        }
        System.out.println("\n--- End of SongLyrics Testing ---");
    }
}