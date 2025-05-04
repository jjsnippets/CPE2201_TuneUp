package controller; // Place in the 'controller' package

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.Song; // Assuming you might need this later for TableView
import service.LyricsService; // Import LyricsService
import service.PlayerService; // Import PlayerService

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller class for the MainView.fxml layout.
 * Handles user interactions, updates the UI based on service states,
 * and coordinates actions between the UI and the backend services.
 */
public class MainController implements Initializable {

    // --- FXML Injected Fields ---

    // Left Pane (Library/Search)
    @FXML private TextField searchTextField;
    @FXML private ComboBox<String> genreFilterComboBox; // Assuming genre is String
    @FXML private TableView<Song> songTableView; // Parameterize with your Song model
    @FXML private TableColumn<Song, String> titleColumn;
    @FXML private TableColumn<Song, String> artistColumn;
    // Add other TableColumn injections if you uncommented them in FXML
    @FXML private Button addToQueueButton;

    // Right Pane (Queue/Lyrics)
    @FXML private TitledPane queueTitledPane;
    @FXML private Label queueSong1Label;
    @FXML private Label queueSong2Label;
    @FXML private Label queueSong3Label;
    @FXML private Label queueCountLabel;
    @FXML private VBox lyricsContainer; // Container for lyric labels
    @FXML private Label previousLyricLabel;
    @FXML private Label currentLyricLabel;
    @FXML private Label next1LyricLabel;
    @FXML private Label next2LyricLabel;

    // Bottom Pane (Controls)
    @FXML private Label currentTimeLabel;
    @FXML private Slider playbackSlider;
    @FXML private Label totalDurationLabel;
    @FXML private Button prevButton;
    @FXML private Button playPauseButton;
    @FXML private Button stopButton;
    @FXML private Button nextButton;
    @FXML private ToggleButton fullscreenToggleButton;
    @FXML private ToggleButton themeToggleButton;

    // --- Service Dependencies ---
    // These will be injected by the Application class after loading the FXML
    private PlayerService playerService;
    private LyricsService lyricsService;

    /**
     * Initializes the controller class. This method is automatically called
     * after the FXML fields have been injected.
     * Use this for setting up bindings, listeners, and initial UI state
     * that doesn't rely heavily on services being injected yet (unless using a ControllerFactory).
     *
     * @param location  The location used to resolve relative paths for the root object, or null if not known.
     * @param resources The resources used to localize the root object, or null if not known.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("MainController initialized.");

        // TODO: Add other initializations here:
        // - Configure TableView columns (setCellValueFactory)
        // - Populate genre ComboBox (fetch distinct genres from DAO?)
        // - Add listeners to search field, combo box, table selection
        // - Add listeners/bindings for player controls (slider, time labels) AFTER services are injected
        // - Add listener to lyricsService.displayLinesProperty() AFTER services are injected
        // - Add button action handlers (using @FXML methods below)
    }

    // --- Service Injection Methods ---

    /**
     * Injects the PlayerService dependency. Called manually from TuneUpApplication.
     * @param playerService The instance of PlayerService.
     */
    public void setPlayerService(PlayerService playerService) {
        this.playerService = playerService;
        // TODO: Setup bindings/listeners that depend on playerService HERE
        // Example: bind time labels, slider value/max, play/pause button text/state
        // Example: add listener to playerService.currentTimeProperty() to update lyrics
        System.out.println("PlayerService injected into MainController.");
    }

    /**
     * Injects the LyricsService dependency. Called manually from TuneUpApplication.
     * @param lyricsService The instance of LyricsService.
     */
    public void setLyricsService(LyricsService lyricsService) {
        this.lyricsService = lyricsService;
        // TODO: Setup bindings/listeners that depend on lyricsService HERE
        // Example: bind lyric labels visibility/text to lyricsService.displayLinesProperty()
        System.out.println("LyricsService injected into MainController.");
    }


    // --- FXML Action Handlers (Placeholders) ---
    // Link these to the onAction property of buttons in the FXML file, e.g., onAction="#handlePlayPause"

    @FXML
    private void handlePlayPause() {
        System.out.println("Play/Pause button clicked");
        // TODO: Implement logic using playerService (e.g., check status, call play/pause)
        // TODO: Update button text ("Play" / "Pause") based on player status
        // if (playerService != null) { ... }
    }

    @FXML
    private void handleStop() {
        System.out.println("Stop button clicked");
        // TODO: Implement logic using playerService (e.g., call stop)
        // if (playerService != null) { playerService.stop(); }
    }

    @FXML
    private void handlePrevious() {
        System.out.println("Previous button clicked");
        // TODO: Implement previous song logic (likely involves queue service/player service)
    }

    @FXML
    private void handleNext() {
        System.out.println("Next button clicked");
        // TODO: Implement next song logic (likely involves queue service/player service)
        // Example: if (playerService != null) { // Logic to play next from queue }
    }

    @FXML
    private void handleAddToQueue() {
        System.out.println("Add to Queue button clicked");
        // TODO: Get selected song from songTableView
        // TODO: Add selected song to the queue (using a future QueueService?)
        // Song selectedSong = songTableView.getSelectionModel().getSelectedItem();
        // if (selectedSong != null) { /* add to queue */ }
    }

    @FXML
    private void handleFullscreenToggle() {
        System.out.println("Fullscreen toggle: " + fullscreenToggleButton.isSelected());
        // TODO: Implement logic to make stage full screen (pass stage reference or get from node)
    }

    @FXML
    private void handleThemeToggle() {
        System.out.println("Theme toggle: " + themeToggleButton.isSelected());
        // TODO: Implement theme switching (add/remove CSS classes to root pane/scene)
        // String theme = themeToggleButton.isSelected() ? "dark" : "light";
        // Apply theme...
    }

    // --- Other Methods (Example) ---
    // Example: Method to load initial data into the table view
    public void loadInitialLibraryData() {
        // This method would be called AFTER services are injected, maybe from TuneUpApplication
        // or triggered internally once services are known to be set.
        System.out.println("Loading initial library data...");
        // TODO: Set up TableView columns cell value factories
        // TODO: Fetch data using SongDAO.getAllSongs()
        // TODO: Populate songTableView.setItems(...)
    }

}
