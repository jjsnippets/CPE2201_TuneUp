// package com.yourcompany.tuneup; // TODO: Replace with your actual package name

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Parent; // Use Parent for loaded root
import javafx.scene.control.Alert;
import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.net.URL;

// Imports for services and controller
import service.PlayerService;
import service.LyricsService;
import service.QueueService;
import controller.MainController;

// Imports for initialization/testing
import util.ApplicationInitializer;
import util.DevelopmentTester;

/**
 * Main application class for TuneUp. Initializes the backend and launches the JavaFX UI.
 * Extends javafx.application.Application.
 */
public class TuneUpApplication extends Application {

    // --- Constants ---
    // Flag to easily enable/disable development tests (e.g., set via system property or config later)
    private static final boolean RUN_DEVELOPMENT_TESTS = true; // Set to false for production

    // --- Instance Variables ---
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;
    private boolean initializationOk = false; // Flag to track successful initialization

    /**
     * Initialization method called by JavaFX toolkit before start().
     * Performs backend initialization and optional testing. Runs on a background thread.
     *
     * @throws Exception Can be thrown by super.init() or if initialization is critical.
     */
    @Override
    public void init() throws Exception {
        super.init(); // Call superclass init is good practice
        System.out.println("TuneUp Application Initializing Backend...");

        // Instantiate services (can be done early)
        this.playerService = new PlayerService();
        this.lyricsService = new LyricsService();
        this.queueService = new QueueService();
        System.out.println("Services instantiated.");

        // Initialize core components (DB, Schema, Population)
        this.initializationOk = ApplicationInitializer.initializeApplication();

        if (this.initializationOk) {
            System.out.println("Core initialization successful (from init method).");

            // --- Development Step: Conditionally Run Tests ---
            if (RUN_DEVELOPMENT_TESTS) {
                System.out.println("Running development tests (from init method)...");
                DevelopmentTester.runAllDevelopmentTests();
                System.out.println("Finished development tests.");
            }
            // --- End Development Step ---

        } else {
            // Log critical failure; start() will show an error UI
            System.err.println("Application backend failed to initialize correctly (from init method). UI might not function.");
        }
    }

    /**
     * The main entry point for the JavaFX application UI.
     * Runs on the JavaFX Application Thread.
     *
     * @param primaryStage The primary stage for this application.
     */
    @Override
    public void start(Stage primaryStage) {
         System.out.println("Starting JavaFX UI...");

         // Check if backend initialization failed
         if (!this.initializationOk) {
             showErrorDialog("Initialization Error", "Application Initialization Failed",
                             "Could not initialize backend components. Exiting.");
             // Don't call Platform.exit() here, just let start() finish gracefully after dialog.
             // System.exit(1) is okay if Platform isn't fully running.
             System.exit(1); // Exit if init failed critically
             return;
         }

        primaryStage.setTitle("TuneUp Karaoke Application");

        try {
            // --- Load MainView.fxml ---
            URL fxmlUrl = getClass().getResource("/view/MainView.fxml");
            if (fxmlUrl == null) {
                throw new IOException("Cannot find FXML: /view/MainView.fxml. Check classpath and file location.");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            // --- Get Controller and Inject Services ---
            MainController controller = loader.getController();
            if (controller != null) {
                controller.setPlayerService(this.playerService);
                controller.setLyricsService(this.lyricsService);
                controller.setQueueService(this.queueService);
                // Call method to setup bindings/listeners *after* all services are injected
                controller.initializeBindingsAndListeners();
            } else {
                 throw new IOException("FXMLLoader failed to create the controller instance for MainView.fxml.");
            }

            // --- Setup Scene and Stage ---
            Scene scene = new Scene(root, 1000, 700); // Initial size

            // --- Set CSS Stylesheet ---
            URL cssUrl = getClass().getResource("/view/style.css"); // Make sure path is correct
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                System.out.println("Loaded CSS: " + cssUrl.toExternalForm());
            } else {
                System.err.println("Warning: CSS file not found at /view/style.css");
            }
            

            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> System.out.println("Main window close request received...")); // Log closing intent
            primaryStage.show();

        } catch (IOException e) { // Catch FXML loading errors
            handleFatalError("UI Load Error", "Failed to Load User Interface",
                             "Could not load the main view (MainView.fxml):\n" + e.getMessage(), e);
        } catch (Exception e) { // Catch other potential UI setup errors
            handleFatalError("UI Setup Error", "Failed to Setup User Interface",
                             "An unexpected error occurred during UI setup:\n" + e.getMessage(), e);
        }
    }

    /**
     * Called when the application is stopping. Performs cleanup.
     * Runs on the JavaFX Application Thread *after* the last window is closed.
     */
    @Override
    public void stop() throws Exception {
        System.out.println("TuneUp Application Shutting Down (via stop method)...");

        // Dispose services that need cleanup (like MediaPlayer)
        if (playerService != null) {
            try {
                playerService.dispose();
                System.out.println("PlayerService disposed.");
            } catch (Exception e) {
                System.err.println("Error disposing PlayerService: " + e.getMessage());
                e.printStackTrace();
            }
        }
        // Dispose other services if they implement a dispose() method

        super.stop(); // Call superclass stop
        System.out.println("Application stopped.");
    }

    /**
     * The main method which launches the JavaFX application.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(String[] args) {
        // Launch the JavaFX application lifecycle
        launch(args);
    }

    /** Handles fatal errors during startup by logging, showing a dialog, and exiting. */
    private void handleFatalError(String title, String header, String content, Exception e) {
         System.err.println(title + ": " + content);
         if (e != null) {
            e.printStackTrace();
         }
         // Assuming this is called from start() which is on FX thread
         showErrorDialog(title, header, content);
         System.exit(1); // Terminate on fatal startup error
    }

    /** Helper method to show a simple error dialog. Runs on FX thread. */
    private void showErrorDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().setPrefSize(480, 320);
        alert.showAndWait();
    }
}