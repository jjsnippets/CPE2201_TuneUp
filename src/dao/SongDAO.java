package dao;

import model.Song;          // Import the Song model
import util.DatabaseUtil;   // Import the database connection utility

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for interacting with the 'songs' table in the database.
 * Provides methods for retrieving song data based on various criteria, supporting
 * requirements FR2.1, FR2.3, FR2.4, and FR2.5.
 */
public class SongDAO {

    // Private constructor to prevent instantiation of this utility class with static methods.
    private SongDAO() {}

    /**
     * Retrieves all songs from the database, ordered by artist and then title.
     * Implements requirement FR2.1 (Load song metadata).
     *
     * @return A List of all Song objects found in the database. Returns an empty list if
     *         no songs are found or an error occurs.
     */
    public static List<Song> getAllSongs() {
        List<Song> songs = new ArrayList<>();
        // SQL query to select all columns from the songs table, ordered for consistent display
        String sql = "SELECT id, title, artist, genre, duration, audio_file_path, lyrics_file_path FROM songs ORDER BY artist ASC, title ASC";

        // Use try-with-resources for automatic closing of connection, statement, and result set
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // Iterate through the results
            while (rs.next()) {
                // Map the current row to a Song object and add it to the list
                songs.add(mapResultSetToSong(rs));
            }
        } catch (SQLException e) {
            // Log the error if database access fails
            System.err.println("Error fetching all songs: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for debugging
            // Return an empty list to indicate failure or absence of data
        }
        return songs;
    }

    /**
     * Finds songs based on search text (matching title or artist) and an optional genre filter.
     * Implements requirements FR2.3 (Search by Title), FR2.4 (Search by Artist),
     * FR2.5 (Filter by Genre), and FR2.6 (Update displayed list).
     *
     * @param searchText  The text to search for within song titles or artist names (case-insensitive).
     *                    If null or empty, this criterion is ignored.
     * @param genreFilter The specific genre to filter by (case-sensitive). If null, empty, or a
     *                    designated "All Genres" value (e.g., ""), this filter is ignored.
     * @return A List of Song objects matching the criteria. Returns an empty list if
     *         no matches are found or an error occurs.
     */
    public static List<Song> findSongsByCriteria(String searchText, String genreFilter) {
        List<Song> songs = new ArrayList<>();
        // Base SQL query
        StringBuilder sqlBuilder = new StringBuilder("SELECT id, title, artist, genre, duration, audio_file_path, lyrics_file_path FROM songs");
        List<Object> parameters = new ArrayList<>(); // To hold query parameters safely
        boolean hasWhereClause = false;

        // --- Build WHERE clause dynamically ---

        // Add search text condition (Title OR Artist)
        if (searchText != null && !searchText.trim().isEmpty()) {
            sqlBuilder.append(" WHERE (LOWER(title) LIKE ? OR LOWER(artist) LIKE ?)");
            String searchPattern = "%" + searchText.trim().toLowerCase() + "%";
            parameters.add(searchPattern); // Parameter for title search
            parameters.add(searchPattern); // Parameter for artist search
            hasWhereClause = true;
        }

        // Add genre filter condition
        // Assuming null, empty string, or a specific value like "All Genres" means no filter
        boolean applyGenreFilter = genreFilter != null && !genreFilter.trim().isEmpty() && !"All Genres".equalsIgnoreCase(genreFilter.trim());
        if (applyGenreFilter) {
            sqlBuilder.append(hasWhereClause ? " AND" : " WHERE"); // Append WHERE or AND correctly
            sqlBuilder.append(" genre = ?"); // Case-sensitive genre match assumed from dropdown
            parameters.add(genreFilter.trim());
            hasWhereClause = true; // Should already be true if searchText was present, but set just in case
        }

        // --- Add ORDER BY clause ---
        sqlBuilder.append(" ORDER BY artist ASC, title ASC");

        // --- Execute Query ---
        // Use try-with-resources for connection and prepared statement
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {

            // Set the parameters in the PreparedStatement
            for (int i = 0; i < parameters.size(); i++) {
                pstmt.setObject(i + 1, parameters.get(i)); // Use setObject for flexibility (String, Integer, etc.)
            }

            // Execute the query and get the results
            try (ResultSet rs = pstmt.executeQuery()) {
                // Iterate through the results
                while (rs.next()) {
                    // Map the current row to a Song object and add it to the list
                    songs.add(mapResultSetToSong(rs));
                }
            }
        } catch (SQLException e) {
            // Log the error if database access fails
            System.err.println("Error finding songs by criteria: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for debugging
        }

        return songs;
    }


    /**
     * Helper method to map a row from the ResultSet to a Song object.
     * Avoids code duplication in public DAO methods.
     *
     * @param rs The ResultSet, positioned at the row to map.
     * @return A Song object created from the current ResultSet row.
     * @throws SQLException if a database access error occurs while reading the ResultSet.
     */
    private static Song mapResultSetToSong(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String title = rs.getString("title");
        String artist = rs.getString("artist");
        String genre = rs.getString("genre"); // Retrieve genre
        Integer duration = rs.getInt("duration"); // Retrieve duration as int first
        if (rs.wasNull()) { // Check if the integer value was SQL NULL
            duration = null; // Set Java Integer to null if database value was NULL
        }
        String audioPath = rs.getString("audio_file_path");
        String lyricsPath = rs.getString("lyrics_file_path");

        // Create and return a new Song object using the retrieved data
        return new Song(id, title, artist, genre, duration, audioPath, lyricsPath);
    }
}
