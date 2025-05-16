// package com.yourcompany.tuneup; // TODO: Replace with your actual package name

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Parent; // Use Parent for loaded root
import javafx.scene.control.Alert;
import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.net.URL;
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
// Imports for initialization
import util.ApplicationInitializer;

/**
 * Main application class for the TuneUp Karaoke Application.
 * This class is responsible for initializing the application's backend services,
 * loading the primary user interface (UI) from an FXML file, injecting dependencies
 * into the UI controller, and managing the application lifecycle.
 * It extends {@link javafx.application.Application}.
 * Corresponds to overall application setup and UI launch (SRS Section 1.2, FR4.1).
 */
public class TuneUpApplication extends Application {
    // --- Instance Variables ---
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;
    private boolean initializationOk = false; // Tracks if backend initialization was successful

    /**
     * The application initialization method, called by the JavaFX toolkit on a
     * background thread before the {@link #start(Stage)} method.
     * This method is used for non-GUI initialization tasks such as setting up
     * services and running backend initializers.
     *
     * @throws Exception if any error occurs during initialization.
     */
    @Override
    public void init() throws Exception {
        super.init(); // It's good practice to call the superclass's init method
        System.out.println("TuneUp Application Initializing Backend...");
        // Instantiate core services
        this.playerService = new PlayerService();
        this.lyricsService = new LyricsService();
        this.queueService = new QueueService();
        System.out.println("Services instantiated.");
        // Perform core application initialization (database, schema, data population)
        // This supports requirements like FR2.1 (Load song metadata from database).
        this.initializationOk = ApplicationInitializer.initializeApplication();
        if (this.initializationOk) {
            System.out.println("Core initialization successful (from init method).");
        } else {
            // Log critical failure; the start() method will show an error dialog
            // and prevent UI launch.
            System.err.println("Application backend failed to initialize correctly (from init method). UI may not function.");
        }
    }

    /**
     * The main entry point for all JavaFX applications.
     * This method is called after the {@link #init()} method has returned;
     * it is run on the JavaFX Application Thread.
     * Responsible for setting up the primary stage and scene, loading the FXML,
     * and injecting services into the controller.
     *
     * @param primaryStage The primary stage for this application, onto which
     * the application scene can be set.
     */
    @Override
    public void start(Stage primaryStage) {
        System.out.println("Starting JavaFX UI...");
        // Critical check: If backend initialization failed, show error and exit.
        if (!this.initializationOk) {
            showErrorDialog("Initialization Error", "Application Initialization Failed",
                    "Could not initialize critical backend components. The application will now exit.");
            System.exit(1); // Terminate if initialization was unsuccessful
            return;
        }
        primaryStage.setTitle("TuneUp Karaoke Application"); // SRS: Application Name
        try {
            // --- Load MainView.fxml ---
            // FR4.1: Present functionality through JavaFX GUI.
            // FR4.2: UI provides clear separation (defined in FXML).
            URL fxmlUrl = getClass().getResource("/view/MainView.fxml");
            if (fxmlUrl == null) {
                throw new IOException("Cannot find FXML resource: /view/MainView.fxml. " +
                "Please ensure it is in the classpath within the 'view' directory.");
            }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load(); // Load the FXML hierarchy

            // --- Get Controller and Inject Services ---
            MainController controller = loader.getController();
            if (controller != null) {
                controller.setPrimaryStage(primaryStage); // Pass the stage to the MainController
                controller.setPlayerService(this.playerService);
                controller.setLyricsService(this.lyricsService);
                controller.setQueueService(this.queueService);
                // Call the new method in MainController to initialize sub-controllers
                controller.initializeSubControllersAndServices();
            } else {
                throw new IOException("FXMLLoader failed to create or retrieve the controller instance for MainView.fxml.");
            }

            // --- Setup Scene and Stage ---
            Scene scene = new Scene(root, 1000, 700); // Initial window size
            primaryStage.setScene(scene);

            // --- Load Multiple CSS Stylesheets ---
                // Load base CSS
                loadCssFile(scene, "/view/css/base.css");
                
                // Load theme CSS (default to light theme)
                loadCssFile(scene, "/view/css/themes/light-theme.css");
                // Load component-specific CSS files
                loadCssFile(scene, "/view/css/components/common-components.css");
                loadCssFile(scene, "/view/css/components/table-styles.css");
                loadCssFile(scene, "/view/css/components/scrollbar-styles.css");
                loadCssFile(scene, "/view/css/components/queue-styles.css");
                loadCssFile(scene, "/view/css/components/player-controls.css");
                loadCssFile(scene, "/view/css/components/normal-view.css");
                loadCssFile(scene, "/view/css/components/lyrics-styles.css");
                loadCssFile(scene, "/view/css/components/fullscreen-view.css");

            // Add window close handler to properly shut down the application
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("Main window close request received. Initiating shutdown...");
                // Consume the event to handle the closing ourselves
                event.consume();
                // Properly close the application by calling Platform.exit()
                javafx.application.Platform.exit();
            });
            
            primaryStage.show();

        } catch (IOException e) {
            // Handle fatal errors during FXML loading or controller setup
            handleFatalError("UI Load Error", "Failed to Load User Interface",
                    "Could not load the main application view (MainView.fxml):\n" + e.getMessage(), e);
        } catch (Exception e) {
            // Catch any other unexpected errors during UI setup
            handleFatalError("UI Setup Error", "Failed to Setup User Interface",
                    "An unexpected error occurred during UI setup:\n" + e.getMessage(), e);
        }
    }

    /**
     * This method is called when the application should stop, and provides a
     * convenient place to handle resource cleanup.
     * It is called by the JavaFX toolkit on the JavaFX Application Thread
     * after the last window has been closed.
     *
     * @throws Exception if any error occurs during stopping.
     */
    @Override
    public void stop() throws Exception {
        System.out.println("TuneUp Application Shutting Down (via stop method)...");
        // Dispose of services that require cleanup, like PlayerService which holds MediaPlayer resources.
        if (playerService != null) {
            try {
                playerService.dispose();
                System.out.println("PlayerService disposed successfully.");
            } catch (Exception e) {
                System.err.println("Error during PlayerService disposal: " + e.getMessage());
                e.printStackTrace(); // Log details of disposal error
            }
        }
        // If LyricsService or QueueService implemented a dispose() method for cleanup, call them here.

        super.stop(); // Call the superclass's stop method
        System.out.println("Application stopped.");
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application is launched
     * as a regular Java application (e.g., from an IDE without full JavaFX support).
     *
     * @param args command line arguments passed to the application.
     */
    public static void main(String[] args) {
        // Launch the JavaFX application lifecycle
        launch(args);
    }

    /**
     * Handles fatal errors that occur during application startup by logging,
     * displaying an error dialog to the user, and then exiting the application.
     * This method assumes it is called from a context where JavaFX UI can be shown (e.g., start method).
     *
     * @param title The title for the error dialog window.
     * @param header The header text for the error dialog.
     * @param content The detailed content/message of the error.
     * @param e The exception that caused the fatal error (can be null).
     */
    private void handleFatalError(String title, String header, String content, Exception e) {
        System.err.println("FATAL ERROR - " + title + ": " + content);
        if (e != null) {
            e.printStackTrace(); // Log the full stack trace for debugging
        }
        // Show an error dialog to the user.
        // This is typically called from the start() method, which runs on the FX Application Thread.
        showErrorDialog(title, header, content);
        // Terminate the application after a fatal error during startup.
        System.exit(1);
    }

    /**
     * Helper method to display a simple error dialog to the user.
     * This method must be called on the JavaFX Application Thread.
     *
     * @param title The title for the error dialog window.
     * @param header The header text for the error dialog.
     * @param content The detailed content/message of the error.
     */
    private void showErrorDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        // Set a preferred size for the dialog pane for better readability of error messages
        alert.getDialogPane().setPrefSize(480, 200); // Adjusted height
        alert.showAndWait(); // Show dialog and wait for user to close it
    }
    
    /**
     * Helper method to load a CSS stylesheet file into a JavaFX Scene.
     * Handles resource loading and error logging.
     *
     * @param scene The JavaFX Scene to apply the stylesheet to
     * @param cssPath The resource path to the CSS file (e.g., "/view/css/base.css")
     * @return true if the CSS was loaded successfully, false otherwise
     */
    private boolean loadCssFile(Scene scene, String cssPath) {
        URL cssUrl = getClass().getResource(cssPath);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
            System.out.println("Loaded CSS: " + cssPath);
            return true;
        } else {
            System.err.println("Warning: CSS file not found at " + cssPath);
            return false;
        }
    }
}
