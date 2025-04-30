package util; // Make sure this is in the 'util' package

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Handles the core initialization sequence for the TuneUp application,
 * including database connection verification, schema setup, and initial data population.
 */
public class ApplicationInitializer {

    // Define the directory where song files (mp3, lrc) are located.
    private static final String SONGS_DIRECTORY = "songs";

    // Private constructor to prevent instantiation
    private ApplicationInitializer() {}

    /**
     * Runs the essential initialization steps for the application.
     *
     * @return true if all initialization steps complete successfully, false otherwise.
     */
    public static boolean initializeApplication() {
        System.out.println("--- Starting Application Initialization ---");

        // 1. Test basic database connectivity
        try (Connection connection = DatabaseUtil.getConnection()) {
            if (connection == null || connection.isClosed()) {
                System.err.println("Failed to establish a valid initial database connection.");
                return false; // Initialization failed
            }
            System.out.println("Successfully established initial DB connection.");
        } catch (SQLException e) {
            System.err.println("Database connection error during initial check: " + e.getMessage());
            e.printStackTrace();
            return false; // Initialization failed
        } catch (RuntimeException e) {
            // Catch potential runtime exception from driver loading failure
             System.err.println("Application Initialization Failed (Driver Loading?): " + e.getMessage());
             e.printStackTrace();
             return false; // Initialization failed
        }

        // 2. Initialize the database schema (create tables if they don't exist)
        try {
            DatabaseInitializer.initializeDatabaseSchema();
            // Assuming initializeDatabaseSchema prints its own success/failure
        } catch (RuntimeException e) {
            System.err.println("Failed to initialize database schema: " + e.getMessage());
            e.printStackTrace();
            return false; // Initialization failed
        }

        // 3. Populate the database from LRC/MP3 files in the specified directory
        System.out.println("\nAttempting to populate database from directory: " + SONGS_DIRECTORY);
        try {
            // Call the populator utility method. The summary is printed within it.
            DatabasePopulator.populateFromLrcFiles(SONGS_DIRECTORY);
        } catch (Exception e) {
            // Catch any unexpected errors during population process
            System.err.println("An unexpected error occurred during database population: " + e.getMessage());
            e.printStackTrace();
            // Decide if population failure is critical. For now, we'll continue but return false.
            return false; // Consider population essential for proper function
        }

        System.out.println("--- Application Initialization Successfully Completed ---");
        return true; // All steps successful
    }
}
