// package com.yourcompany.tuneup; // TODO: Replace with your actual package name

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Parent;           // For loaded root
import javafx.scene.control.Alert;    // For showing errors
import javafx.fxml.FXMLLoader;        // For loading FXML later
import java.io.IOException;           // For FXML loading errors
import java.net.URL;                  // For FXML loading path

import service.PlayerService;       // Import service
import service.LyricsService;       // Import service
import controller.MainController;   // Import controller

// Imports from previous version for initialization/testing
import util.ApplicationInitializer;
import util.DevelopmentTester;

/**
 * Main application class for TuneUp. Initializes the backend and launches the JavaFX UI.
 * Extends javafx.application.Application.
 */
public class TuneUpApplication extends javafx.application.Application {

    private PlayerService playerService;        // Hold service instance
    private LyricsService lyricsService;        // Hold service instance
    private boolean initializationOk = false;   // Flag to track successful initialization

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

        // Create service instances EARLY (before init completes is fine)
        // They don't depend on UI thread
        this.playerService = new PlayerService();
        this.lyricsService = new LyricsService();
        System.out.println("Services instantiated.");

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

        if (!this.initializationOk) {
            showErrorDialog("Initialization Error", "Application Initialization Failed",
                            "Could not initialize backend components. Exiting.");
            System.exit(1);
            return;
        }

       primaryStage.setTitle("TuneUp Karaoke Application");

       try {
           // --- Load MainView.fxml ---
           // Ensure the path is correct (relative to classpath root/resources folder)
           URL fxmlUrl = getClass().getResource("/view/MainView.fxml");
           if (fxmlUrl == null) {
               throw new IOException("Cannot find FXML: /view/MainView.fxml. Check classpath and file location.");
           }

           FXMLLoader loader = new FXMLLoader(fxmlUrl);
           Parent root = loader.load(); // Load the root element (e.g., BorderPane)

           // --- Get Controller and Inject Services ---
           MainController controller = loader.getController(); // Get the controller instance created by FXMLLoader
           if (controller != null) {
               // Use the setter methods to inject the service instances
               controller.setPlayerService(this.playerService);
               controller.setLyricsService(this.lyricsService);

               // Optionally call a method on controller for service-dependent setup if needed now
               // controller.setupServiceDependentBindings(); // Example custom method
               // controller.loadInitialLibraryData(); // Example
           } else {
                throw new IOException("FXMLLoader failed to create the controller instance for MainView.fxml.");
           }

           Scene scene = new Scene(root, 1000, 700); // Use Parent 'root', set size

           primaryStage.setScene(scene);
           primaryStage.setOnCloseRequest(event -> System.out.println("Shutting down..."));
           primaryStage.show();

       } catch (IOException e) { // Catch FXML loading errors
           System.err.println("Fatal error loading UI from FXML: " + e.getMessage());
           e.printStackTrace();
           showErrorDialog("UI Load Error", "Failed to Load User Interface",
                           "Could not load the main view (MainView.fxml):\n" + e.getMessage());
           System.exit(1);
       } catch (Exception e) { // Catch other potential UI setup errors
           System.err.println("Fatal error setting up UI: " + e.getMessage());
           e.printStackTrace();
           showErrorDialog("UI Error", "Failed to Setup User Interface",
                           "An unexpected error occurred during UI setup:\n" + e.getMessage());
           System.exit(1);
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
