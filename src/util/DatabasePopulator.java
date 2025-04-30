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

        // SQL statement for inserting a new song.
        String insertSQL = "INSERT INTO songs (title, artist, genre, duration, offset, audio_file_path, lyrics_file_path) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?)";

        // Counters for summary report
        int songsAdded = 0;
        int filesProcessed = 0;
        int errorsOrSkipped = 0; // Combined counter for simplicity in reporting

        // Use try-with-resources for database connection and listing files
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL); // Prepare statement once
             Stream<Path> stream = Files.list(dir)) {

            // Iterate over files in the directory
            for (Path path : (Iterable<Path>) stream::iterator) {
                // Process only files ending with .lrc (case-insensitive check)
                if (Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".lrc")) {
                    filesProcessed++;
                    // Process the file and update counters based on outcome
                    boolean added = processSingleLrcFile(path, dir, pstmt);
                    if (added) {
                        songsAdded++;
                    } else {
                        // Increment if not added (includes skips and errors reported within helper)
                        errorsOrSkipped++;
                    }
                } // end if .lrc file
            } // end for each path

        } catch (IOException e) {
            System.err.println("Error listing files in directory: " + directoryPath + " - " + e.getMessage());
            errorsOrSkipped++; // Count this as an error
        } catch (SQLException e) {
            System.err.println("Database connection or statement preparation error: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for critical DB errors
            // Population likely failed significantly, return songs added so far
            // return songsAdded; // Or maybe 0 depending on desired behavior
        } finally {
            // Ensure summary is printed even if SQLException occurred during processing loop
            printSummary(filesProcessed, songsAdded, errorsOrSkipped);
        }

        return songsAdded;
    }

    /**
     * Processes a single LRC file: finds matching MP3, parses metadata, validates,
     * and attempts to insert into the database using the provided PreparedStatement.
     * Errors and skips are logged directly within this method.
     *
     * @param lrcPath The Path object for the .lrc file.
     * @param containingDir The directory Path where the lrc file resides.
     * @param pstmt The PreparedStatement (already prepared with INSERT SQL) to use.
     * @return true if the song was successfully added, false otherwise (due to skip or error).
     */
    private static boolean processSingleLrcFile(Path lrcPath, Path containingDir, PreparedStatement pstmt) {
        String lrcFileName = lrcPath.getFileName().toString();
        String baseName = getBaseName(lrcFileName);

        // Construct and check for the corresponding .mp3 file
        Path mp3Path = containingDir.resolve(baseName + ".mp3");
        if (!Files.exists(mp3Path)) {
            System.err.println("Warning: Corresponding MP3 file not found for: " + lrcFileName + ". Skipping.");
            return false; // Not added
        }

        String lrcFilePathAbs = lrcPath.toAbsolutePath().toString();
        String mp3FilePathAbs = mp3Path.toAbsolutePath().toString();

        try {
            // Parse metadata (including duration and offset)
            Map<String, Object> metadata = LrcParser.parseMetadataTags(lrcFilePathAbs);

            String title = (String) metadata.get("title");
            String artist = (String) metadata.get("artist");
            String genre = (String) metadata.get("genre");
            Integer duration = (Integer) metadata.get("duration");
            Long offset = (Long) metadata.get("offset");

            // Validate required metadata
            if (title == null || title.trim().isEmpty() || artist == null || artist.trim().isEmpty()) {
                System.err.println("Warning: Missing required metadata (title or artist) in LRC file: " + lrcFileName + ". Skipping.");
                return false; // Not added
            }

            // Set parameters for the PreparedStatement
            pstmt.setString(1, title.trim());
            pstmt.setString(2, artist.trim());
            pstmt.setString(3, genre != null ? genre.trim() : null);
            pstmt.setObject(4, duration); // setObject handles null Integer
            pstmt.setObject(5, offset);   // setObject handles null Long
            pstmt.setString(6, mp3FilePathAbs);
            pstmt.setString(7, lrcFilePathAbs);

            // Execute the insert statement
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0; // Return true if added

        } catch (IOException e) {
            System.err.println("Error reading or parsing LRC file: " + lrcFileName + " - " + e.getMessage());
        } catch (SQLException e) {
            // Handle unique constraint violation gracefully (as a skip, not error)
            if (e.getErrorCode() == 19 && e.getMessage().contains("UNIQUE constraint failed: songs.audio_file_path")) {
                System.err.println("Warning: Song with audio path already exists in DB: " + mp3FilePathAbs + ". Skipping duplicate.");
            } else {
                // Log other SQL errors during insertion
                System.err.println("Database error inserting record for: " + lrcFileName + " - SQLState: " + e.getSQLState() + " ErrorCode: " + e.getErrorCode() + " Message: " + e.getMessage());
            }
        } catch (InvalidPathException | ClassCastException e) {
            System.err.println("Error processing metadata or path for: " + lrcFileName + " - " + e.getMessage());
        } catch (Exception e) { // Catch unexpected errors during processing
            System.err.println("Unexpected error processing file: " + lrcFileName + " - " + e.getMessage());
            e.printStackTrace();
        }

        return false; // Return false if any error/skip occurred
    }

    /**
     * Prints the summary report of the population process.
     *
     * @param filesProcessed Total LRC files looked at.
     * @param songsAdded Total songs successfully inserted.
     * @param errorsOrSkipped Total files that were skipped or caused an error.
     */
    private static void printSummary(int filesProcessed, int songsAdded, int errorsOrSkipped) {
        System.out.println("-----------------------------------------");
        System.out.println("Database Population Summary:");
        System.out.println(" - LRC files processed: " + filesProcessed);
        System.out.println(" - New songs added:     " + songsAdded);
        System.out.println(" - Files skipped/errors:" + errorsOrSkipped); // Updated label
        System.out.println("-----------------------------------------");
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
        return (lastDot > 0) ? fileName.substring(0, lastDot) : fileName;
    }

    // Removed the redundant parseDuration(String) method
}