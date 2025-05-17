package dao;

import model.Song;
import util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import java.util.Set;
import java.util.TreeSet;  // Import TreeSet for sorted order

/**
 * Data Access Object (DAO) for the {@link Song} entity, responsible for all database
 * interactions related to songs. This class encapsulates SQL queries and provides
 * methods to retrieve song records.
 * <p>
 * It primarily supports the following functional requirements from the SRS:
 * <ul>
 *   <li>FR2.1: Load song metadata from the database.</li>
 *   <li>FR2.3: Search songs by title.</li>
 *   <li>FR2.4: Search songs by artist.</li>
 *   <li>FR2.5: Filter songs by genre.</li>
 * </ul>
 * This DAO helps in decoupling the application's business logic from the database access logic.
 * All methods are static as this class acts as a utility for database operations on songs.
 */
public class SongDAO {

    // Static constant for the base SELECT columns to avoid repetition
    private static final String SELECT_COLUMNS = "SELECT id, title, artist, genre, duration, offset, audio_file_path, lyrics_file_path FROM songs";

    // Private constructor to prevent instantiation of this utility class with static methods.
    private SongDAO() {}

    /**
     * Retrieves a sorted set of all distinct genres present in the songs table.
     * This method supports FR2.5 (Filter by Genre) by providing the available genres
     * for UI filter components. Genres that are null or empty strings are excluded.
     *
     * @return A {@link Set} of unique genre strings, sorted alphabetically (case-sensitive,
     *         as per default string comparison in TreeSet and typical database collation).
     *         Returns an empty set if no genres are found or an SQL error occurs.
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
     * Retrieves all songs from the database, ordered alphabetically by artist and then by title.
     * This method directly implements requirement FR2.1 (Load song metadata from database),
     * providing the initial dataset for the song library.
     *
     * @return A {@link List} of {@link Song} objects. Returns an empty list if no songs
     *         are found in the database or if an SQL error occurs during retrieval.
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
     * Finds songs based on a textual search query (matching title or artist, case-insensitively)
     * and an optional genre filter. Results are ordered alphabetically by artist and then by title.
     * <p>
     * This method supports the following functional requirements:
     * <ul>
     *   <li>FR2.3: Search songs by title.</li>
     *   <li>FR2.4: Search songs by artist.</li>
     *   <li>FR2.5: Filter songs by genre.</li>
     * </ul>
     * The data returned by this method is intended to be used for FR2.6 (Update displayed list of songs
     * based on search/filter).
     *
     * @param searchText  The text to search for within song titles or artist names. The search is
     *                    case-insensitive and matches if the text appears anywhere in the title or artist.
     *                    If {@code null} or empty, this search criterion is not applied.
     * @param genreFilter The specific genre to filter by. An exact match is performed.
     *                    If {@code null}, empty, or the special string "All Genres" (case-insensitive)
     *                    is provided, the genre filter is not applied.
     * @return A {@link List} of {@link Song} objects matching the specified criteria.
     *         Returns an empty list if no songs match or if an SQL error occurs.
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
     * Helper method to map a single row from a {@link ResultSet} to a {@link Song} object.
     * This utility encapsulates the logic of extracting column values and constructing a Song,
     * ensuring consistency and reducing code duplication across DAO methods.
     *
     * @param rs The {@link ResultSet} currently positioned at the row to be mapped.
     *           It is assumed that the ResultSet contains all columns specified in {@code SELECT_COLUMNS}.
     * @return A {@link Song} object populated with data from the current row of the ResultSet.
     * @throws SQLException if a database access error occurs while reading from the ResultSet
     *                      (e.g., column label not found, data type mismatch).
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