package controller; // Place in the 'controller' package

import javafx.application.Platform; 
import javafx.beans.binding.Bindings;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory; // Import PropertyValueFactory
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent; // Import MouseEvent for slider interaction
import javafx.scene.media.MediaPlayer; // Import MediaPlayer status
import javafx.util.Duration; // Import Duration

import java.net.URL;
import java.util.List; 
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit; // Import TimeUnit for formatting

import model.LyricLine;
import model.Song; // Assuming you might need this later for TableView
import service.LyricsService; // Import LyricsService
import service.PlayerService; // Import PlayerService
import service.QueueService; // Import QueueService
import dao.SongDAO; // Import SongDAO



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

    // --- State ---
    private Song currentlySelectedSong = null; // Keep track of the selected song
    private boolean isUserSeeking = false; // Flag to prevent time updates while user drags slider

    // --- Constants ---
    private static final String ALL_GENRES = "All Genres"; // Constant for the default filter option

    // Listener for queue changes
    private final ListChangeListener<Song> queueChangeListener = change -> {
        // This lambda runs whenever the queueService.songQueue list changes
        updateQueueDisplay();
    };

    // --- Initialization ---

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

        // Setup listeners for table selection changes
        addTableViewSelectionListener();

        // Setup listeners for playback slider changes
        setupPlaybackSliderListeners(); 
        

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

        /**
     * Adds a listener to the TableView's selection model to track the selected song.
     */
    private void addTableViewSelectionListener() {
        // Get the selection model
        TableView.TableViewSelectionModel<Song> selectionModel = songTableView.getSelectionModel();
        // Set selection mode if needed (optional, SINGLE is default)
        // selectionModel.setSelectionMode(SelectionMode.SINGLE);

        // Add a listener to the selected item property
        selectionModel.selectedItemProperty().addListener((observable, oldSelection, newSelection) -> {
            // Update the currently selected song
            this.currentlySelectedSong = newSelection;

            if (newSelection != null) {
                // A song is selected
                System.out.println("Song selected: " + newSelection.getTitle() + " - " + newSelection.getArtist());
                // Enable buttons that require a selection (e.g., Add to Queue)
                addToQueueButton.setDisable(false);
                // TODO: Potentially enable Play button if queue is empty and no song is playing
            } else {
                // No song is selected (selection cleared)
                System.out.println("Song selection cleared.");
                // Disable buttons that require a selection
                addToQueueButton.setDisable(true);
                // TODO: Potentially disable Play button if queue is empty and no song is playing
            }
        });

        // Initially disable buttons that require selection
        addToQueueButton.setDisable(true);
    }

    /**
     * Adds listeners to the playback slider to handle user seeking.
     */
    private void setupPlaybackSliderListeners() {
        // Listener when user presses mouse on slider (start seeking)
        playbackSlider.setOnMousePressed((MouseEvent event) -> {
            isUserSeeking = true;
            // Optional: Pause playback while seeking?
            // if (playerService != null && playerService.getStatus() == MediaPlayer.Status.PLAYING) {
            //     playerService.pause();
            // }
        });

        // Listener when user releases mouse from slider (finish seeking)
        playbackSlider.setOnMouseReleased((MouseEvent event) -> {
            if (playerService != null && isUserSeeking) {
                long seekTimeMillis = (long) playbackSlider.getValue();
                System.out.println("Slider released - Seeking to: " + seekTimeMillis + "ms");
                playerService.seek(seekTimeMillis);
                // Optional: Resume playback if paused for seeking
                // if (playerService.getStatus() == MediaPlayer.Status.PAUSED) {
                //     playerService.play();
                // }
            }
            isUserSeeking = false;
        });

        // Alternative/Additional: Listener for value changes (e.g., if user uses arrow keys)
        // Needs careful handling with isUserSeeking flag if using mouse listeners too
         playbackSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
             if (playbackSlider.isValueChanging() || isUserSeeking) {
                 // Update time label immediately while dragging/changing
                 currentTimeLabel.setText(formatTime(newVal.longValue()));
             }
         });
         // Track if the slider value is being changed by the user
         playbackSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
             if (!isChanging && playerService != null) { // Value change finished
                 // Check if this change wasn't triggered by mouse release already handled
                 if (!isUserSeeking) {
                     long seekTimeMillis = (long) playbackSlider.getValue();
                     System.out.println("Slider value change finished - Seeking to: " + seekTimeMillis + "ms");
                     playerService.seek(seekTimeMillis);
                 }
             }
         });
    }


    // --- Service Injection & Post-Injection Setup ---

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

         setupPlayerBindingsAndListeners(); // Setup bindings dependent on PlayerService
         setupLyricsBindingsAndListeners(); // Setup bindings dependent on LyricsService


         // TODO: Add PlayerService bindings (slider, time labels, play/pause button state)
         // TODO: Add PlayerService time listener to update lyricsService.updateCurrentDisplayLines()
         // TODO: Add LyricsService bindings (bind lyric labels to displayLinesProperty)
         // TODO: Add QueueService listener (already done in setQueueService)
   }

    /**
     * Sets up bindings and listeners for the PlayerService.
     * Called from initializeBindingsAndListeners() after service injection.
     */
    private void setupPlayerBindingsAndListeners() {
        if (playerService == null) return;

        // Bind slider max value to total duration
        // Using listener as duration might change when new media is loaded
        playerService.totalDurationProperty().addListener((obs, oldDuration, newDuration) -> {
            Platform.runLater(() -> { // Ensure UI updates on JavaFX Application Thread
                 double totalMillis = newDuration.doubleValue();
                 playbackSlider.setMax(totalMillis > 0 ? totalMillis : 0.0); // Set max only if duration is valid
                 totalDurationLabel.setText(formatTime(totalMillis));
            });
        });

        // Bind slider current value to current time (only update if user isn't seeking)
        playerService.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            Platform.runLater(() -> {
                if (!isUserSeeking && !playbackSlider.isValueChanging()) {
                    playbackSlider.setValue(newTime.doubleValue());
                    // Update time label (already handled by slider listener, but good backup)
                    // currentTimeLabel.setText(formatTime(newTime.longValue()));
                }
                // --- Trigger Lyrics Update ---
                if (lyricsService != null) {
                    lyricsService.updateCurrentDisplayLines(newTime.longValue());
                }
                // --- ---
            });
        });

        // Bind Play/Pause button text and potentially disable state based on player status
        playerService.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            Platform.runLater(() -> {
                switch (newStatus) {
                    case PLAYING:
                        playPauseButton.setText("Pause");
                        playPauseButton.setDisable(false);
                        stopButton.setDisable(false);
                        nextButton.setDisable(false); // Enable skip when playing
                        break;
                    case PAUSED:
                        playPauseButton.setText("Play");
                        playPauseButton.setDisable(false);
                        stopButton.setDisable(false);
                        nextButton.setDisable(false); // Keep skip enabled when paused
                        break;
                    case READY:
                    case STOPPED:
                    case HALTED: // Treat HALTED as stopped state for controls
                    case UNKNOWN:
                    default:
                        playPauseButton.setText("Play");
                        // Enable Play if there's something to play (selected song or queue)
                        playPauseButton.setDisable(currentlySelectedSong == null && queueService.isEmpty());
                        stopButton.setDisable(true); // Can't stop if not playing/paused
                        nextButton.setDisable(queueService.isEmpty()); // Can only skip if queue has items
                        // Reset time labels/slider if stopped/halted
                        if (newStatus == MediaPlayer.Status.STOPPED || newStatus == MediaPlayer.Status.HALTED) {
                            playbackSlider.setValue(0);
                            currentTimeLabel.setText(formatTime(0));
                        }
                        break;
                }
            });
        });

         // Initialize button states based on initial player status (usually UNKNOWN)
         playPauseButton.setText("Play");
         playPauseButton.setDisable(currentlySelectedSong == null && queueService.isEmpty());
         stopButton.setDisable(true);
         nextButton.setDisable(queueService.isEmpty()); // Disable skip if queue is initially empty
    }

     /**
      * Sets up bindings and listeners related to the LyricsService.
      */
     private void setupLyricsBindingsAndListeners() {
         if (lyricsService == null) return;

         // Listen to the displayLines property from LyricsService
         lyricsService.displayLinesProperty().addListener((obs, oldLines, newLines) -> {
             Platform.runLater(() -> {
                 // Update the 4 lyric labels based on the new list
                 // Handle potential nulls in the list (placeholders)
                 previousLyricLabel.setText(getLyricTextOrEmpty(newLines, 0));
                 currentLyricLabel.setText(getLyricTextOrEmpty(newLines, 1));
                 next1LyricLabel.setText(getLyricTextOrEmpty(newLines, 2));
                 next2LyricLabel.setText(getLyricTextOrEmpty(newLines, 3));
             });
         });

         // Set initial state (empty)
         previousLyricLabel.setText("");
         currentLyricLabel.setText("");
         next1LyricLabel.setText("");
         next2LyricLabel.setText("");
     }

     /** Helper to safely get text from LyricLine list or return empty string. */
     private String getLyricTextOrEmpty(List<LyricLine> lines, int index) {
         if (lines != null && index >= 0 && index < lines.size()) {
             LyricLine line = lines.get(index);
             return (line != null) ? line.getText() : "";
         }
         return "";
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
        if (playerService == null) return;

        MediaPlayer.Status currentStatus = playerService.getStatus();

        if (currentStatus == MediaPlayer.Status.PLAYING) {
            playerService.pause();
        } else if (currentStatus == MediaPlayer.Status.PAUSED || currentStatus == MediaPlayer.Status.READY || currentStatus == MediaPlayer.Status.STOPPED) {
            // If paused/ready/stopped, try to resume or start
            if (playerService.getCurrentSong() != null) { // Check if something is loaded
                 playerService.play(); // Resume or start from beginning if stopped/ready
            } else {
                // Nothing loaded - try playing from selection or queue
                playNextSong();
            }
        } else { // e.g., UNKNOWN, HALTED - try playing next
             playNextSong();
        }
    }

    @FXML
    private void handleStop() {
        System.out.println("Stop button clicked");
        if (playerService != null) {
            playerService.stop();
            // Optionally clear lyrics display?
            // if(lyricsService != null) lyricsService.updateCurrentDisplayLines(0);
        }
    }

    @FXML
    private void handlePrevious() {
        System.out.println("Previous button clicked");
        // TODO: Implement previous song logic (likely involves queue service/player service)
    }

    @FXML
    private void handleNext() { // Skip button
        System.out.println("Skip button clicked");
        if (playerService != null) {
            playerService.stop(); // Stop current song immediately
        }
        playNextSong(); // Attempt to play the next song from queue
    }

    @FXML
    private void handleAddToQueue() {
        if (currentlySelectedSong != null && queueService != null) {
            System.out.println("Add to Queue button clicked for: " + currentlySelectedSong);
            queueService.addSong(currentlySelectedSong); // Add the tracked selected song
        } else {
            System.out.println("Add to Queue button clicked, but no song selected or queue service unavailable.");
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
        if (playerService != null && playerService.getStatus() != MediaPlayer.Status.PLAYING && playerService.getStatus() != MediaPlayer.Status.PAUSED) {
            playPauseButton.setDisable(currentlySelectedSong == null && queueService.isEmpty());
        }
        nextButton.setDisable(queueService.isEmpty()); // Disable skip if queue is empty

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

    // --- Helper Methods ---

    /**
     * Attempts to play the next song, prioritizing the queue, then the selection.
     */
    private void playNextSong() {
        Song nextSong = null;
        if (queueService != null && !queueService.isEmpty()) {
            nextSong = queueService.getNextSong(); // Get and remove next from queue
        } else if (currentlySelectedSong != null && playerService.getStatus() != MediaPlayer.Status.PLAYING && playerService.getStatus() != MediaPlayer.Status.PAUSED) {
            // If queue is empty and nothing playing, play the selected song
            System.out.println("Queue empty, playing selected song.");
            nextSong = currentlySelectedSong;
            // Optional: Clear selection after playing it this way?
            // songTableView.getSelectionModel().clearSelection();
        }

        if (nextSong != null) {
            // Load and play the determined song
            loadAndPlaySong(nextSong);
        } else {
            System.out.println("No song selected and queue is empty. Nothing to play.");
            // Ensure player is stopped if nothing could be played
             if (playerService != null && playerService.getStatus() != MediaPlayer.Status.STOPPED) {
                 playerService.stop();
             }
        }
    }

    /**
     * Helper method to load a song into PlayerService and LyricsService and start playback.
     * @param song The song to play.
     */
    private void loadAndPlaySong(Song song) {
        if (song == null || playerService == null || lyricsService == null) return;

        System.out.println("Loading and playing: " + song.getTitle());
        // Load song into player (this stops previous, loads new)
        playerService.loadSong(song);
        // Load lyrics for the song
        lyricsService.loadLyricsForSong(song);

        // Check if player loaded successfully before playing
        // Note: loadSong is async, status might still be unknown briefly.
        // It's safer to rely on status changes or setOnReady, but for immediate play:
         if (playerService.getStatus() != MediaPlayer.Status.HALTED) {
             playerService.play();
         } else {
             System.err.println("Cannot play song, player failed to load.");
         }
    }

    /**
     * Formats time in milliseconds to mm:ss string.
     * @param millis Time in milliseconds.
     * @return Formatted string "mm:ss" or "0:00" if input is negative.
     */
    private String formatTime(long millis) {
        if (millis < 0) millis = 0; // Handle potential negative values

        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        return String.format("%d:%02d", minutes, seconds);
    }

     // Overload for double if needed (e.g., slider value)
     private String formatTime(double millis) {
         return formatTime((long) millis);
     }

}
