package controller; // Place in the 'controller' package

import javafx.collections.FXCollections; // Import FXCollections
import javafx.collections.ObservableList; // Import ObservableList
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory; // Import PropertyValueFactory
import javafx.scene.layout.VBox;
import model.Song; // Assuming you might need this later for TableView

import service.LyricsService; // Import LyricsService
import service.PlayerService; // Import PlayerService
import service.QueueService; // Import QueueService
import dao.SongDAO; // Import SongDAO

import java.net.URL;
import java.util.List; 
import java.util.ResourceBundle;
import java.util.Set;

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
    @FXML private Button playPauseButton;
    @FXML private Button stopButton;
    @FXML private Button nextButton; // Text is "Skip" in FXML
    @FXML private ToggleButton fullscreenToggleButton;
    @FXML private ToggleButton themeToggleButton;

    // --- Service Dependencies ---
    // These will be injected by the Application class after loading the FXML
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;

        // --- Constants ---
        private static final String ALL_GENRES = "All Genres"; // Constant for the default filter option

    // Listener for queue changes
    private final ListChangeListener<Song> queueChangeListener = change -> {
        // This lambda runs whenever the queueService.songQueue list changes
        updateQueueDisplay();
    };


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

        // Basic setup that doesn't need injected services yet
        setupTableViewColumns();
        // Setup listeners that trigger updates, can be done here
        addSearchAndFilterListeners();
        

        // TODO: Add other initializations here:
        // - Configure TableView columns (setCellValueFactory)
        // - Populate genre ComboBox (fetch distinct genres from DAO?)
        // - Add listeners to search field, combo box, table selection
        // - Add listeners/bindings for player controls (slider, time labels) AFTER services are injected
        // - Add listener to lyricsService.displayLinesProperty() AFTER services are injected
        // - Add button action handlers (using @FXML methods below)
    }

        /**
     * Configures the TableView columns to map to Song properties.
     */
    private void setupTableViewColumns() {
        // Use PropertyValueFactory to link columns to Song properties (getTitle(), getArtist())
        // Make sure the property names ("title", "artist") match the getter names in Song.java
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        artistColumn.setCellValueFactory(new PropertyValueFactory<>("artist"));
        // TODO: Configure other columns if added (e.g., duration, genre)
        // genreColumn.setCellValueFactory(new PropertyValueFactory<>("genre"));
        // durationColumn.setCellValueFactory(new PropertyValueFactory<>("formattedDuration")); // Use helper method
    }

    /**
     * Adds listeners to the search text field and genre combo box
     * to trigger updates to the song list.
     */
    private void addSearchAndFilterListeners() {
        // Listener for search text changes
        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateSongTableView(); // Call update method when text changes
        });

        // Listener for genre filter selection changes
        genreFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateSongTableView(); // Call update method when selection changes
        });
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

    /**
     * Injects the QueueService dependency. Called manually from TuneUpApplication.
     * @param queueService The instance of QueueService.
     */
    public void setQueueService(QueueService queueService) {
        // Remove existing listener if service is somehow re-injected
        if (this.queueService != null) {
             this.queueService.getQueue().removeListener(queueChangeListener);
        }

        this.queueService = queueService;
        System.out.println("QueueService injected.");

        // Add listener to the observable queue list
        if (this.queueService != null) {
             this.queueService.getQueue().addListener(queueChangeListener);
             // Update display initially based on current queue state
             updateQueueDisplay();
        }
    }

    /**
     * Method called after all services are injected to set up bindings
     * and listeners that depend on them.
     * Called from TuneUpApplication.start().
     */
    public void initializeBindingsAndListeners() {
        System.out.println("Initializing service-dependent bindings and listeners...");
         // Populate Genre Filter ComboBox
         populateGenreFilter();

         // Load initial full list of songs into the table view
         updateSongTableView(); // Initial load uses empty search/filter

         // TODO: Add PlayerService bindings (slider, time labels, play/pause button state)
         // TODO: Add PlayerService time listener to update lyricsService.updateCurrentDisplayLines()
         // TODO: Add LyricsService bindings (bind lyric labels to displayLinesProperty)
         // TODO: Add QueueService listener (already done in setQueueService)
   }



    // --- Data Loading and UI Update ---

    /**
     * Populates the genre filter ComboBox with distinct genres from the database
     * and adds the "All Genres" option.
     */
    private void populateGenreFilter() {
        // Fetch distinct genres from the DAO
        Set<String> distinctGenres = SongDAO.getDistinctGenres();

        // Create an observable list for the ComboBox items
        ObservableList<String> genreOptions = FXCollections.observableArrayList();

        // Add the default "All Genres" option first
        genreOptions.add(ALL_GENRES);

        // Add the genres fetched from the database
        genreOptions.addAll(distinctGenres); // Works because distinctGenres is sorted (TreeSet)

        // Set the items in the ComboBox
        genreFilterComboBox.setItems(genreOptions);

        // Set the default selection to "All Genres"
        genreFilterComboBox.setValue(ALL_GENRES);
    }

    /**
     * Updates the song TableView based on the current text in the search field
     * and the selected value in the genre filter ComboBox.
     * Implements FR2.3, FR2.4, FR2.5, FR2.6.
     */
    private void updateSongTableView() {
        if (songTableView == null || searchTextField == null || genreFilterComboBox == null) {
            System.err.println("updateSongTableView called before UI elements injected.");
            return;
        }

        String searchText = searchTextField.getText(); // Get current search text
        String genreFilter = genreFilterComboBox.getValue(); // Get current selected genre

        // Handle the "All Genres" selection - treat it as no filter (null) for DAO query
        if (ALL_GENRES.equals(genreFilter)) {
            genreFilter = null;
        }

        // Call the DAO method to get filtered songs
        // This runs synchronously on the JavaFX thread - consider background thread for large DBs
        List<Song> filteredSongs = SongDAO.findSongsByCriteria(searchText, genreFilter);

        // Update the TableView's items
        // Using FXCollections.observableArrayList is good practice if list needs observation elsewhere
        songTableView.setItems(FXCollections.observableArrayList(filteredSongs));

        System.out.println("Updated song table view. Found " + filteredSongs.size() + " songs matching criteria.");
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
        if (songTableView == null || queueService == null) return;

        Song selectedSong = songTableView.getSelectionModel().getSelectedItem();
        if (selectedSong != null) {
            System.out.println("Add to Queue button clicked for: " + selectedSong);
            queueService.addSong(selectedSong); // Add to service, listener will update UI
        } else {
            System.out.println("Add to Queue button clicked, but no song selected.");
            // Optionally show a small notification/alert to the user
        }
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

    // --- Queue Display Update ---

    /**
     * Updates the queue display labels based on the current state
     * of the QueueService.
     */
    private void updateQueueDisplay() {
        if (queueService == null || queueSong1Label == null) {
            // Services not injected or FXML elements not ready
            return;
        }

        ObservableList<Song> currentQueue = queueService.getQueue();
        int queueSize = currentQueue.size();

        // Update first 3 labels
        queueSong1Label.setText("1. " + (queueSize >= 1 ? formatSongForQueue(currentQueue.get(0)) : "-"));
        queueSong2Label.setText("2. " + (queueSize >= 2 ? formatSongForQueue(currentQueue.get(1)) : "-"));
        queueSong3Label.setText("3. " + (queueSize >= 3 ? formatSongForQueue(currentQueue.get(2)) : "-"));

        // Update count label
        int remaining = Math.max(0, queueSize - 3);
        queueCountLabel.setText((remaining > 0) ? "(+" + remaining + " more)" : "(+0 more)");

        // Optional: Collapse TitledPane if queue becomes empty
        // if (queueTitledPane != null) {
        //     queueTitledPane.setExpanded(!currentQueue.isEmpty());
        // }
    }

    /**
     * Formats a Song object for display in the queue labels.
     * @param song The Song object.
     * @return A formatted string (e.g., "Title - Artist").
     */
    private String formatSongForQueue(Song song) {
        if (song == null) {
            return "-";
        }
        // Use the song's natural toString() or create custom format
        return song.toString(); // Assumes Song.toString() returns "Title - Artist"
        // Or: return song.getTitle() + " - " + song.getArtist();
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
