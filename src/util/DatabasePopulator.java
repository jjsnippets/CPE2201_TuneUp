package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

/**
 * Utility class to populate the database with song information
 * by scanning a directory for LRC and MP3 file pairs.
 * Extracts metadata solely from LRC files as per requirements.
 * This class directly interacts with the database to insert song records.
 * <p>
 * Corresponds to:
 * <ul>
 *   <li>SRS 3.1.3: Database interaction for storing song metadata.</li>
 *   <li>SRS 3.1.3.1: Adherence to the 'songs' table schema, including constraints like NOT NULL and data types for title, artist, duration, etc.</li>
 *   <li>FR3.1: Parsing LRC files for metadata.</li>
 * </ul>
 */
public class DatabasePopulator {

    // Private constructor to prevent instantiation.
    private DatabasePopulator() {}

    /**
     * Scans the specified directory for .lrc files, attempts to find corresponding .mp3 files,
     * parses metadata from the .lrc file, and inserts the song information into the 'songs' table
     * in the database. It assumes that .mp3 files share the same base name as their .lrc counterparts.
     * A summary of the population process (files processed, songs added, errors/skips) is printed to standard error.
     *
     * @param directoryPath The path to the directory containing song files (e.g., "songs").
     *                      This path should point to a directory accessible by the application.
     * @return The number of new songs successfully added to the database. Returns 0 if the
     *         directoryPath is invalid or no songs could be added.
     * @see #processSingleLrcFile(Path, Path, PreparedStatement)
     * @see #printSummary(int, int, int)
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
        try (PreparedStatement pstmt = DatabaseUtil.getConnection().prepareStatement(insertSQL); // Prepare statement once
             var stream = Files.list(dir)) {

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
     * Processes a single LRC file:
     * 1. Derives the expected MP3 file name and checks for its existence.
     * 2. Parses metadata (title, artist, genre, duration, offset) from the LRC file using {@link LrcParser}.
     * 3. Validates essential metadata:
     * <ul>
     *   <li>Title must be present.</li>
     *   <li>Artist must be present.</li>
     *   <li>Duration must be present and a positive integer value.</li>
     * </ul>
     * 4. If validation passes, it attempts to insert the song record into the database
     *    using the provided {@link PreparedStatement}.
     *
     * Errors encountered during file processing (e.g., missing MP3, missing required metadata,
     * invalid duration, I/O issues, SQL errors) are logged to standard error, and the method
     * will return {@code false} for that file. Skipped files (e.g., duplicates based on
     * unique constraints) are also logged and result in a {@code false} return.
     *
     * @param lrcPath The {@link Path} object for the .lrc file to be processed.
     * @param containingDir The {@link Path} of the directory where the {@code lrcPath} resides,
     *                      used to resolve the corresponding .mp3 file.
     * @param pstmt The {@link PreparedStatement} (already prepared with the INSERT SQL command)
     *              to use for inserting the song data into the database.
     * @return {@code true} if the song was successfully parsed, validated, and added to the database;
     *         {@code false} otherwise (due to a skip, validation failure, or error during processing).
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

            // Validate required metadata: title, artist, and duration
            if (title == null || title.trim().isEmpty()) {
                System.err.println("Warning: Missing required metadata (title) in LRC file: " + lrcFileName + ". Skipping.");
                return false; // Not added
            }
            if (artist == null || artist.trim().isEmpty()) {
                System.err.println("Warning: Missing required metadata (artist) in LRC file: " + lrcFileName + ". Skipping.");
                return false; // Not added
            }
            // Duration must be present and positive, consistent with Song object and DB schema (NOT NULL, positive)
            if (duration == null || duration <= 0) {
                System.err.println("Warning: Missing or invalid (must be a positive integer) duration in LRC file: "
                                   + lrcFileName + ". Duration found: " + duration + ". Skipping.");
                return false; // Not added
            }

            // Set parameters for the PreparedStatement
            pstmt.setString(1, title.trim());
            pstmt.setString(2, artist.trim());
            pstmt.setString(3, genre != null ? genre.trim() : null); // Genre is optional
            pstmt.setInt(4, duration); // Duration is now validated as non-null positive int
            pstmt.setObject(5, offset);   // Offset is optional and can be null
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
     * Prints a summary report of the database population process to standard output.
     * This includes the total number of LRC files processed, the number of new songs
     * successfully added to the database, and the number of files that were skipped
     * or resulted in errors.
     *
     * @param filesProcessed Total number of LRC files encountered and attempted to process.
     * @param songsAdded Total number of new songs successfully inserted into the database.
     * @param errorsOrSkipped Total number of files that were skipped (e.g., duplicates, missing MP3s,
     *                        invalid metadata) or caused an error during processing.
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
     * Helper method to get the base name of a file (i.e., the file name without its extension).
     * For example, "song.lrc" would return "song". If the file name has no extension,
     * the original file name is returned.
     *
     * @param fileName The full file name (e.g., "mysong.txt", "archive", "image.jpeg").
     * @return The base name of the file. If {@code fileName} is {@code null} or empty,
     *         behavior might be unexpected depending on {@link String#lastIndexOf(int)} and {@link String#substring(int, int)}.
     *         It's assumed {@code fileName} is a valid, non-null file name string.
     */
    private static String getBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0) ? fileName.substring(0, lastDot) : fileName;
    }

    // Removed the redundant parseDuration(String) method
}