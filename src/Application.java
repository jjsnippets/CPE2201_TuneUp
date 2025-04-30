// Update package declaration if needed, e.g., package com.yourcompany.tuneup;

import util.ApplicationInitializer; // Import the initializer
import util.DevelopmentTester;     // Import the tester

// Import for JavaFX launch (if/when added)
// import javafx.application.Application as JfxApplication;
// import ui.TuneUpGui; // Assuming your main UI class is TuneUpGui in ui package

/**
 * Main application class for TuneUp.
 * Responsible for initializing the application core and launching the user interface.
 */
public class Application { // In a full JavaFX app, this would extend javafx.application.Application

    /**
     * The main entry point for the TuneUp application.
     * Initializes core components and optionally runs development tests.
     *
     * @param args Command line arguments (not used directly here, but passed to JavaFX launch).
     */
    public static void main(String[] args) {
        System.out.println("TuneUp Application Starting...");

        // Initialize core components (DB, Schema, Population)
        boolean initializationOk = ApplicationInitializer.initializeApplication();

        if (initializationOk) {
            System.out.println("Core initialization successful.");

            // --- Development Step: Run Tests ---
            // This call should be removed or made conditional for a production build.
            DevelopmentTester.runAllDevelopmentTests();
            // --- End Development Step ---

            System.out.println("\nReady to launch User Interface...");

            // --> Future JavaFX application launch logic would go here <--
            // Example:
            // JfxApplication.launch(TuneUpGui.class, args);

        } else {
            // Initialization failed - critical error
            System.err.println("Application failed to initialize correctly. Exiting.");
            // In a GUI app, might show an error dialog before exiting.
            System.exit(1); // Exit with error code
        }
    }
}
