package util; // Define the package for utility classes

import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Utility class to populate the database with song information
 * by scanning a directory for LRC and MP3 file pairs.
 * Extracts metadata solely from LRC files as per requirements.
 * Corresponds to SRS 3.1.3 (database interaction) and uses LRC parsing (FR3.1).
 */
public class DatabasePopulator {

    // Private constructor to prevent instantiation.
    private DatabasePopulator() {}

    /**
     * Scans the specified directory for .lrc files, attempts to find corresponding .mp3 files,
     * parses metadata from the .lrc file, and inserts the information into the 'songs' table.
     * Assumes .mp3 files have the same base name as .lrc files.
     *
     * @param directoryPath The path to the directory containing song files (e.g., "songs").
     * @return The number of new songs successfully added to the database.
     */
    public static int populateFromLrcFiles(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        if (!Files.isDirectory(dir)) {
            System.err.println("Error: Provided path is not a directory or doesn't exist: " + directoryPath);
            return 0;
        }

        // SQL statement for inserting a new song. Using placeholders (?) for security.
        // Matches the schema defined in DatabaseInitializer.
        String insertSQL = "INSERT INTO songs (title, artist, genre, audio_file_path, lyrics_file_path) " +
                           "VALUES (?, ?, ?, ?, ?)";

        int songsAdded = 0;
        int filesProcessed = 0;
        int errorsEncountered = 0;

        // Use try-with-resources for the database connection and prepared statement.
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL);
             // Use Files.list within try-with-resources to ensure the stream is closed.
             Stream<Path> stream = Files.list(dir)) {

            // Iterate over files in the directory
            for (Path lrcPath : (Iterable<Path>) stream::iterator) {
                // Process only files ending with .lrc (case-insensitive check)
                if (Files.isRegularFile(lrcPath) && lrcPath.toString().toLowerCase().endsWith(".lrc")) {
                    filesProcessed++;
                    String lrcFilePath = lrcPath.toAbsolutePath().toString();
                    String baseName = getBaseName(lrcPath.getFileName().toString());

                    // Construct the expected path for the corresponding .mp3 file
                    Path mp3Path = dir.resolve(baseName + ".mp3");
                    String mp3FilePath = mp3Path.toAbsolutePath().toString();

                    // Check if the corresponding .mp3 file exists
                    if (!Files.exists(mp3Path)) {
                        System.err.println("Warning: Corresponding MP3 file not found for: " + lrcPath.getFileName() + ". Skipping.");
                        errorsEncountered++;
                        continue; // Skip this LRC file
                    }

                    try {
                        // Parse metadata (title, artist, genre) from the LRC file
                        Map<String, String> metadata = LrcParser.parseMetadataTags(lrcFilePath);

                        String title = metadata.get("title");
                        String artist = metadata.get("artist");
                        String genre = metadata.get("genre"); // Can be null

                        // Basic validation: Title and Artist are required (NOT NULL in DB schema)
                        if (title == null || title.trim().isEmpty() || artist == null || artist.trim().isEmpty()) {
                            System.err.println("Warning: Missing required metadata (title or artist) in LRC file: " + lrcPath.getFileName() + ". Skipping.");
                             errorsEncountered++;
                            continue; // Skip this entry
                        }

                        // Set parameters for the PreparedStatement
                        pstmt.setString(1, title.trim()); // Use trimmed values
                        pstmt.setString(2, artist.trim());
                        pstmt.setString(3, genre != null ? genre.trim() : null); // Set genre or NULL
                        pstmt.setString(4, mp3FilePath); // Use absolute path for audio
                        pstmt.setString(5, lrcFilePath); // Use absolute path for lyrics

                        // Execute the insert statement
                        int affectedRows = pstmt.executeUpdate();
                        if (affectedRows > 0) {
                            songsAdded++;
                            // System.out.println("Added song: " + title + " - " + artist); // Optional: log success
                        }

                    } catch (IOException e) {
                        System.err.println("Error reading or parsing LRC file: " + lrcPath.getFileName() + " - " + e.getMessage());
                        errorsEncountered++;
                    } catch (SQLException e) {
                        // Specifically check for unique constraint violation (duplicate entry)
                        if (e.getErrorCode() == 19 && e.getMessage().contains("UNIQUE constraint failed: songs.audio_file_path")) {
                             System.err.println("Warning: Song with audio path already exists in DB: " + mp3FilePath + ". Skipping duplicate.");
                             // This is expected if run multiple times, treat as a warning not a hard error count.
                        } else {
                            // Handle other SQL errors during insertion
                            System.err.println("Database error inserting record for: " + lrcPath.getFileName() + " - SQLState: " + e.getSQLState() + " ErrorCode: " + e.getErrorCode() + " Message: " + e.getMessage());
                            errorsEncountered++;
                        }
                    } catch (InvalidPathException e) {
                         System.err.println("Error: Invalid file path generated for: " + lrcPath.getFileName() + " - " + e.getMessage());
                         errorsEncountered++;
                    }
                } // end if .lrc file
            } // end for each path

        } catch (IOException e) {
            System.err.println("Error listing files in directory: " + directoryPath + " - " + e.getMessage());
            errorsEncountered++; // Count this as an error
        } catch (SQLException e) {
            System.err.println("Database connection or statement preparation error: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for critical DB errors
            // Return early might be appropriate if connection fails
            return songsAdded; // Return songs added so far
        }

        System.out.println("-----------------------------------------");
        System.out.println("Database Population Summary:");
        System.out.println(" - LRC files processed: " + filesProcessed);
        System.out.println(" - New songs added:     " + songsAdded);
        System.out.println(" - Errors/Warnings:     " + errorsEncountered);
        System.out.println("-----------------------------------------");

        return songsAdded;
    }

    /**
     * Helper method to get the base name of a file (name without extension).
     * Example: "song.lrc" -> "song"
     *
     * @param fileName The full file name.
     * @return The base name.
     */
    private static String getBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName; // No extension found
    }
}
