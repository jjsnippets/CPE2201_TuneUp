// package com.yourcompany.tuneup; // TODO: Replace with your actual package name

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane; // Example layout
import javafx.scene.control.Label;    // Example control
import javafx.scene.control.Alert;    // For showing errors
import javafx.fxml.FXMLLoader;        // For loading FXML later
import java.io.IOException;           // For FXML loading errors
import java.net.URL;                  // For FXML loading path

// Imports from previous version for initialization/testing
import util.ApplicationInitializer;
import util.DevelopmentTester;

/**
 * Main application class for TuneUp. Initializes the backend and launches the JavaFX UI.
 * Extends javafx.application.Application.
 */
public class TuneUpApplication extends javafx.application.Application {

    private boolean initializationOk = false; // Flag to track successful initialization

    /**
     * Initialization method called by JavaFX toolkit before start().
     * Performs backend initialization (DB, Schema, Population) and optional testing.
     * This runs on a separate thread, NOT the JavaFX Application Thread.
     *
     * @throws Exception if initialization fails critically (though we handle failures internally for now).
     */
    @Override
    public void init() throws Exception {
        super.init(); // Call superclass init is good practice
        System.out.println("TuneUp Application Initializing Backend...");

        // Initialize core components (DB, Schema, Population)
        this.initializationOk = ApplicationInitializer.initializeApplication();

        if (this.initializationOk) {
            System.out.println("Core initialization successful (from init method).");

            // --- Development Step: Run Tests ---
            // This call should be removed or made conditional for a production build.
             System.out.println("Running development tests (from init method)...");
            DevelopmentTester.runAllDevelopmentTests();
            // --- End Development Step ---
        } else {
            // Log critical failure; the start() method will handle showing an error UI
            System.err.println("Application backend failed to initialize correctly (from init method). UI might not function.");
            // We don't throw an exception here to allow start() to show a user-friendly error.
        }
    }

    /**
     * The main entry point for the JavaFX application UI.
     * This method is called after init() completes and runs on the JavaFX Application Thread.
     *
     * @param primaryStage The primary stage for this application, onto which
     *                     the application scene can be set.
     */
    @Override
    public void start(Stage primaryStage) {
         System.out.println("Starting JavaFX UI...");

         // Check if initialization in init() failed and show error/exit
         if (!this.initializationOk) {
             showErrorDialog("Initialization Error",
                             "Application Initialization Failed",
                             "The application could not initialize backend components (database, etc.). Please check logs. Exiting.");
             // Platform.exit(); // Use Platform.exit() for graceful JavaFX shutdown, but System.exit might be needed if Platform isn't running yet
             System.exit(1); // Force exit
             return; // Stop further UI setup
         }

        // Set the title for the main window
        primaryStage.setTitle("TuneUp Karaoke Application");

        try {
            // --- UI Loading ---
            // TODO: Replace placeholder with FXML loading for MainView.fxml

            // Placeholder UI:
            BorderPane root = new BorderPane();
            Label welcomeLabel = new Label("Welcome to TuneUp! (UI Placeholder - Load MainView.fxml here)");
            root.setCenter(welcomeLabel);
            Scene scene = new Scene(root, 900, 700); // Example starting size
            // --- End Placeholder UI ---


            /* --- Example FXML Loading (replace placeholder later) ---
            // Ensure the FXML file path is correct relative to the classpath root or resources folder
            URL fxmlUrl = getClass().getResource("/view/MainView.fxml"); // Assumes 'view' is in resources or classpath root
            if (fxmlUrl == null) {
                throw new IOException("Cannot find FXML file. Make sure /view/MainView.fxml exists and is correctly placed.");
            }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            BorderPane root = loader.load(); // Load the FXML root element

            // Access controller if needed (after loader.load()):
            // controller.MainController controller = loader.getController(); // Assuming controller class is MainController
            // Pass services or perform setup on the controller
            // controller.setPlayerService(playerServiceInstance);
            // controller.setLyricsService(lyricsServiceInstance);
            // controller.initializeUI(); // Or similar setup method

            Scene scene = new Scene(root);
            --- End Example FXML Loading --- */


            // Set the scene on the stage
            primaryStage.setScene(scene);

            // Add any global CSS if needed (ensure path is correct)
            // URL cssUrl = getClass().getResource("/css/style.css");
            // if (cssUrl != null) {
            //    scene.getStylesheets().add(cssUrl.toExternalForm());
            // } else {
            //    System.err.println("Warning: Global CSS file not found.");
            // }

            // Configure stage closing behavior (optional but good practice)
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("Close request received. Shutting down...");
                // Perform any necessary cleanup before closing
                // e.g., playerService.dispose();
            });

            // Show the stage
            primaryStage.show();

        } catch (Exception e) { // Catch potential exceptions during UI loading (e.g., FXML errors)
            System.err.println("Fatal error setting up UI: " + e.getMessage());
            e.printStackTrace();
            showErrorDialog("UI Error", "Failed to Load User Interface",
                            "An unexpected error occurred while loading the main view:\n" + e.getMessage());
            System.exit(1); // Exit on critical UI failure
        }
    }

     /**
     * Optional: Override stop() method to perform cleanup on application exit.
     * This method is called when the application is closed (e.g., closing the window).
     */
     @Override
     public void stop() throws Exception {
         System.out.println("TuneUp Application Shutting Down (via stop method)...");
         // TODO: Add cleanup code here (e.g., dispose services)
         // Example: if (playerService != null) playerService.dispose();
         super.stop(); // Call superclass stop
     }


    /**
     * The main method which launches the JavaFX application.
     * The actual application logic starts in the init() and start() methods.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(String[] args) {
        // Launch the JavaFX application. This calls init() on a background thread,
        // then calls start() on the JavaFX Application Thread.
        TuneUpApplication.launch(args);
    }

    /**
     * Helper method to show a simple error dialog.
     * Should only be called from the JavaFX Application Thread.
     *
     * @param title Title of the dialog window.
     * @param header Header text inside the dialog.
     * @param content Detailed content message.
     */
    private void showErrorDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        // Increase dialog size if needed
        alert.getDialogPane().setPrefSize(480, 320);
        alert.showAndWait();
    }
}
