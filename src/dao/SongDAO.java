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
import java.util.stream.Collectors; // For joining conditions

import java.util.Set;      // Import Set
import java.util.TreeSet;  // Import TreeSet for sorted order

/**
 * Data Access Object (DAO) for interacting with the 'songs' table in the database.
 * Provides methods for retrieving song data based on various criteria, supporting
 * requirements FR2.1, FR2.3, FR2.4, and FR2.5.
 * Refactored for minor clarity improvements in query building.
 */
public class SongDAO {

    // Static constant for the base SELECT columns to avoid repetition
    private static final String SELECT_COLUMNS = "SELECT id, title, artist, genre, duration, offset, audio_file_path, lyrics_file_path FROM songs";

    // Private constructor to prevent instantiation of this utility class with static methods.
    private SongDAO() {}

        /**
     * Retrieves a sorted set of all distinct genres present in the songs table.
     * Ignores null or empty genre values.
     *
     * @return A Set of unique genre strings, sorted alphabetically. Returns an empty set on error.
     */
    public static Set<String> getDistinctGenres() {
        // Use TreeSet for automatic sorting and uniqueness
        Set<String> genres = new TreeSet<>();
        String sql = "SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                genres.add(rs.getString("genre"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching distinct genres: " + e.getMessage());
            e.printStackTrace();
        }
        return genres;
    }

    /**
     * Retrieves all songs from the database, ordered by artist and then title.
     * Implements requirement FR2.1 (Load song metadata).
     *
     * @return A List of all Song objects found in the database. Returns an empty list if
     *         no songs are found or an error occurs.
     */
    public static List<Song> getAllSongs() {
        List<Song> songs = new ArrayList<>();
        // Use the constant SELECT_COLUMNS
        String sql = SELECT_COLUMNS + " ORDER BY artist ASC, title ASC";

        // Use try-with-resources for automatic closing of connection, statement, and result set
        try (Connection conn = DatabaseUtil.getConnection();
             // Using PreparedStatement even without parameters for consistency
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            // Iterate through the results
            while (rs.next()) {
                // Map the current row to a Song object and add it to the list
                songs.add(mapResultSetToSong(rs));
            }
        } catch (SQLException e) {
            // Log the error if database access fails
            System.err.println("Error fetching all songs: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for debugging
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
     * @param genreFilter The specific genre to filter by. If null, empty, or a
     *                    designated "All Genres" value (e.g., ""), this filter is ignored.
     * @return A List of Song objects matching the criteria. Returns an empty list if
     *         no matches are found or an error occurs.
     */
    public static List<Song> findSongsByCriteria(String searchText, String genreFilter) {
        List<Song> songs = new ArrayList<>();
        // Use constant SELECT_COLUMNS
        StringBuilder sqlBuilder = new StringBuilder(SELECT_COLUMNS);
        List<Object> parameters = new ArrayList<>(); // To hold query parameters safely
        List<String> conditions = new ArrayList<>(); // To hold WHERE clause conditions

        // --- Build WHERE conditions dynamically ---

        // Add search text condition (Title OR Artist)
        if (searchText != null && !searchText.trim().isEmpty()) {
            conditions.add("(LOWER(title) LIKE ? OR LOWER(artist) LIKE ?)");
            String searchPattern = "%" + searchText.trim().toLowerCase() + "%";
            parameters.add(searchPattern); // Parameter for title search
            parameters.add(searchPattern); // Parameter for artist search
        }

        // Add genre filter condition
        boolean applyGenreFilter = genreFilter != null && !genreFilter.trim().isEmpty() && !"All Genres".equalsIgnoreCase(genreFilter.trim());
        if (applyGenreFilter) {
            // Assuming exact match for genre from dropdown
            conditions.add("genre = ?");
            parameters.add(genreFilter.trim());
        }

        // --- Append WHERE clause if conditions exist ---
        if (!conditions.isEmpty()) {
            sqlBuilder.append(" WHERE ");
            // Join the collected conditions with " AND "
            sqlBuilder.append(String.join(" AND ", conditions));
        }

        // --- Add ORDER BY clause ---
        sqlBuilder.append(" ORDER BY artist ASC, title ASC");

        // --- Execute Query ---
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {

            // Set the parameters in the PreparedStatement
            for (int i = 0; i < parameters.size(); i++) {
                pstmt.setObject(i + 1, parameters.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    songs.add(mapResultSetToSong(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding songs by criteria: " + e.getMessage());
            e.printStackTrace();
        }

        return songs;
    }


    /**
     * Helper method to map a row from the ResultSet to a Song object.
     * Avoids code duplication in public DAO methods. (No changes needed here)
     *
     * @param rs The ResultSet, positioned at the row to map.
     * @return A Song object created from the current ResultSet row.
     * @throws SQLException if a database access error occurs while reading the ResultSet.
     */
    private static Song mapResultSetToSong(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String title = rs.getString("title");
        String artist = rs.getString("artist");
        String genre = rs.getString("genre");

        Integer duration = rs.getInt("duration");
        if (rs.wasNull()) duration = null;

        long offsetVal = rs.getLong("offset");
        Long offset = rs.wasNull() ? null : offsetVal;

        String audioPath = rs.getString("audio_file_path");
        String lyricsPath = rs.getString("lyrics_file_path");

        return new Song(id, title, artist, genre, duration, offset, audioPath, lyricsPath);
    }
}