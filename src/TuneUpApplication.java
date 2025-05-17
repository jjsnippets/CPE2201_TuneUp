import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.Parent;         // Use Parent for loaded root
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
 * This class is responsible for initializing the application's backend services (SRS Section 1.2),
 * loading the primary user interface (UI) from an FXML file (SRS FR4.1), injecting dependencies
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
     * services and running backend initializers (e.g., database connection, schema validation).
     * This supports requirements like FR2.1 (Load song metadata from database) via ApplicationInitializer.
     *
     * @throws Exception if any error occurs during initialization.
     */
    @Override
    public void init() throws Exception {
        super.init();
        System.out.println("TuneUp Application Initializing Backend...");

        // Instantiate core services
        this.playerService = new PlayerService();
        this.lyricsService = new LyricsService();
        this.queueService = new QueueService();
        System.out.println("Core services instantiated.");

        // Perform core application initialization (database, schema, data population)
        // This supports requirements like FR2.1 (Load song metadata from database).
        this.initializationOk = ApplicationInitializer.initializeApplication();

        if (this.initializationOk) {
            System.out.println("Core application initialization successful.");
        } else {
            // Log critical failure; the start() method will show an error dialog
            // and prevent UI launch if initializationOk is false.
            System.err.println("CRITICAL: Application backend failed to initialize correctly. UI launch will be aborted.");
        }
    }

    /**
     * The main entry point for all JavaFX applications.
     * This method is called after the {@link #init()} method has returned;
     * it is run on the JavaFX Application Thread.
     * Responsible for setting up the primary stage and scene, loading the FXML (SRS FR4.1, FR4.2),
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
            // This call uses showErrorDialog and then exits.
            handleFatalError("Initialization Error", "Application Initialization Failed",
                    "Could not initialize critical backend components. The application will now exit.", null);
            return; // Exit if initialization was unsuccessful
        }

        primaryStage.setTitle("TuneUp Karaoke Application"); // As per SRS: Application Name

        try {
            // --- Load MainView.fxml ---
            // FR4.1: Present functionality through JavaFX GUI.
            // FR4.2: UI provides clear separation (defined in FXML).
            URL fxmlUrl = getClass().getResource("/view/MainView.fxml");
            if (fxmlUrl == null) {
                // More specific error for FXML loading
                handleFatalError("FXML Loading Error", "Cannot Load Main UI",
                        "Cannot find FXML resource: /view/MainView.fxml. " +
                        "Please ensure it is in the classpath within the 'view' directory.", null);
                return;
            }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load(); // Load the FXML hierarchy

            // --- Get Controller and Inject Services ---
            MainController controller = loader.getController();
            if (controller != null) {
                controller.setPrimaryStage(primaryStage);
                controller.setPlayerService(this.playerService);
                controller.setLyricsService(this.lyricsService);
                controller.setQueueService(this.queueService);
                // Initialize sub-controllers and their services through the MainController
                controller.initializeSubControllersAndServices();
                System.out.println("MainController initialized and services injected.");
            } else {
                // If controller is null, it's a fatal issue with FXML setup or controller class.
                handleFatalError("Controller Error", "Cannot Initialize Main Controller",
                        "FXMLLoader failed to create or retrieve the controller instance for MainView.fxml.", null);
                return;
            }

            // --- Setup Scene and Stage ---
            Scene scene = new Scene(root, 1000, 700); // Default window size
            primaryStage.setScene(scene);

            // --- Load CSS Stylesheets ---
            // Load base CSS, essential for application appearance.
            if (!loadCssFile(scene, "/view/css/base.css")) {
                // Log a warning but don't make it fatal, app might still be usable.
                System.err.println("Warning: Could not load base.css. UI may not appear as intended.");
            }
            
            // Load theme CSS (default to light theme as per design).
            if (!loadCssFile(scene, "/view/css/themes/light-theme.css")) {
                // Log a warning for theme loading failure.
                System.err.println("Warning: Could not load light-theme.css. Default theme may not apply.");
            }

            primaryStage.show();
            System.out.println("JavaFX UI started and stage shown.");

        } catch (IOException e) {
            // Catch IOException specifically from FXML loading or other I/O operations.
            handleFatalError("UI Startup Error", "IOException During UI Setup",
                    "An I/O error occurred while setting up the main application window: " + e.getMessage(), e);
        } catch (Exception e) {
            // Catch any other unexpected exceptions during startup.
            handleFatalError("Unexpected Error", "Critical Error During UI Startup",
                    "An unexpected error occurred: " + e.getMessage(), e);
        }
    }

    /**
     * This method is called when the application should stop, and provides a
     * convenient place to handle resource cleanup (e.g., media players, database connections).
     * It is called by the JavaFX toolkit on the JavaFX Application Thread
     * after the last window has been closed.
     *
     * @throws Exception if any error occurs during stopping.
     */
    @Override
    public void stop() throws Exception {
        System.out.println("TuneUp Application Shutting Down...");
        try {
            // Dispose of services that require cleanup.
            if (playerService != null) {
                playerService.dispose(); // Assuming PlayerService has a dispose method for MediaPlayer.
                System.out.println("PlayerService disposed.");
            }
        } finally {
            super.stop(); // Ensure super.stop() is called even if our cleanup throws an exception.
            System.out.println("Application stopped successfully.");
        }
    }

    /**
     * The main() method is typically ignored in correctly deployed JavaFX applications.
     * It serves as a fallback in case the application is launched as a regular Java application
     * (e.g., from an IDE without full JavaFX support or via command line).
     *
     * @param args command line arguments passed to the application (not used by this application).
     */
    public static void main(String[] args) {
        // Launch the JavaFX application lifecycle.
        launch(args);
    }

    /**
     * Handles fatal errors that occur during application startup or critical operations
     * by logging the error, displaying an error dialog to the user, and then exiting the application.
     * This method ensures the user is informed of critical failures.
     *
     * @param title The title for the error dialog window.
     * @param header The header text for the error dialog.
     * @param content The detailed content/message of the error.
     * @param e The exception that caused the fatal error (can be null if not exception-specific).
     */
    private void handleFatalError(String title, String header, String content, Exception e) {
        System.err.println("FATAL ERROR - " + title + ": " + header + " - " + content);
        if (e != null) {
            e.printStackTrace(); // Print stack trace for debugging.
        }

        // Ensure dialog is shown on the FX Application Thread.
        // If called from init() or other non-FX threads before start() is fully running,
        // Platform.runLater might be needed, but here it's typically called from start() or related FX thread actions.
        if (Platform.isFxApplicationThread()) {
            showErrorDialog(title, header, content);
        } else {
            Platform.runLater(() -> showErrorDialog(title, header, content));
        }
        
        // Terminate the application after a fatal error.
        System.err.println("Application will now exit due to a fatal error.");
        System.exit(1);
    }

    /**
     * Helper method to display a standardized error dialog to the user.
     * This method must be called on the JavaFX Application Thread.
     *
     * @param title The title for the error dialog window.
     * @param header The header text for the error dialog (can be null).
     * @param content The detailed content/message of the error.
     */
    private void showErrorDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header); // Header can be null if not needed.
        alert.setContentText(content);
        alert.showAndWait(); // Show and wait for user to close it.
    }
    
    /**
     * Helper method to load a CSS stylesheet file into a JavaFX Scene.
     * Handles resource loading and logs errors if the CSS file cannot be found or applied.
     *
     * @param scene The JavaFX Scene to apply the stylesheet to.
     * @param cssPath The resource path to the CSS file (e.g., "/view/css/base.css").
     * @return true if the CSS was loaded successfully, false otherwise.
     */
    private boolean loadCssFile(Scene scene, String cssPath) {
        if (scene == null || cssPath == null || cssPath.isEmpty()) {
            System.err.println("Error: Cannot load CSS. Scene or CSS path is null/empty.");
            return false;
        }
        try {
            URL cssUrl = getClass().getResource(cssPath);
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                System.out.println("Successfully loaded CSS: " + cssPath);
                return true;
            } else {
                System.err.println("Error: Cannot find CSS resource: " + cssPath +
                                   ". Please ensure it is in the classpath.");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error loading CSS file '" + cssPath + "': " + e.getMessage());
            e.printStackTrace(); // For detailed diagnostics
            return false;
        }
    }
}
