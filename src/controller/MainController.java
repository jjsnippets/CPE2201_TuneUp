package controller;

// --- JavaFX Imports ---
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*; // Import all controls for convenience
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox; // Keep VBox
import javafx.scene.media.MediaPlayer;

// --- Model Imports ---
import model.LyricLine;
import model.Song;

// --- Service Imports ---
import service.LyricsService;
import service.PlayerService;
import service.QueueService;

// --- DAO Import ---
import dao.SongDAO;

// --- Util Imports ---
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
* Controller class for the MainView.fxml layout.
* Handles user interactions, updates the UI based on service states,
* and coordinates actions between the UI and the backend services.
* Incorporates fixes for asynchronous loading, Play/Skip logic, and Now Playing display.
*/
public class MainController implements Initializable {

    // --- FXML Injected Fields ---
    // Left Pane (Library/Search)
    @FXML private TextField searchTextField;
    @FXML private ComboBox<String> genreFilterComboBox;
    @FXML private TableView<Song> songTableView;
    @FXML private TableColumn<Song, String> titleColumn;
    @FXML private TableColumn<Song, String> artistColumn;
    @FXML private Button addToQueueButton;

    // Right Pane (Queue/Lyrics)
    @FXML private TitledPane queueTitledPane;
    @FXML private Label queueSong1Label;
    @FXML private Label queueSong2Label;
    @FXML private Label queueSong3Label;
    @FXML private Label queueCountLabel;
    @FXML private VBox lyricsContainer;
    @FXML private Label previousLyricLabel;
    @FXML private Label currentLyricLabel;
    @FXML private Label next1LyricLabel;
    @FXML private Label next2LyricLabel;

    // Bottom Pane (Controls)
    @FXML private Label nowPlayingTitleLabel; // Added for Now Playing
    @FXML private Label nowPlayingArtistLabel; // Added for Now Playing
    @FXML private Label currentTimeLabel;
    @FXML private Slider playbackSlider;
    @FXML private Label totalDurationLabel;
    @FXML private Button playPauseButton;
    @FXML private Button stopButton;
    @FXML private Button skipButton; // Corrected fx:id
    @FXML private ToggleButton fullscreenToggleButton;
    @FXML private ToggleButton themeToggleButton;

    // --- Service Dependencies ---
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;

    // --- State ---
    private Song currentlySelectedSong = null; // Keep track of the selected song
    private boolean isUserSeeking = false; // Flag to prevent time updates while user drags slider

    // --- Constants ---
    private static final String ALL_GENRES = "All Genres";

    // Listener for queue changes
    private final ListChangeListener<Song> queueChangeListener = _change -> Platform.runLater(this::updateQueueDisplay); // Ensure UI update on FX thread

    // --- Initialization ---
    /**
    * Called by FXMLLoader after FXML fields are injected.
    * Sets up UI components that don't depend on injected services.
    */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("MainController initialized.");
        setupTableViewColumns();
        addSearchAndFilterListeners();
        addTableViewSelectionListener();
        setupPlaybackSliderListeners();
        updateNowPlayingDisplay(null); // Initialize Now Playing labels
        playbackSlider.setDisable(true); // Initially disable slider until a song is loaded
    }


    /** Configures the TableView columns to map to Song properties. */
    private void setupTableViewColumns() {
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        artistColumn.setCellValueFactory(new PropertyValueFactory<>("artist"));
    }


    /** Adds listeners to the search text field and genre combo box. */
    private void addSearchAndFilterListeners() {
        // Use Platform.runLater if DAO call becomes long, for now direct call is okay
        searchTextField.textProperty().addListener((_obs, _ov, _nv) -> updateSongTableView());
        genreFilterComboBox.valueProperty().addListener((_obs, _ov, _nv) -> updateSongTableView());
    }


    /** Adds a listener to the TableView's selection model to track the selected song. */
    private void addTableViewSelectionListener() {
        TableView.TableViewSelectionModel<Song> selectionModel = songTableView.getSelectionModel();
        selectionModel.setSelectionMode(SelectionMode.SINGLE); // Ensure single selection
        selectionModel.selectedItemProperty().addListener((_observable, _oldSelection, newSelection) -> {
            this.currentlySelectedSong = newSelection;
            boolean songIsSelected = (newSelection != null);
            System.out.println(songIsSelected ? "Song selected: " + newSelection : "Song selection cleared.");
            addToQueueButton.setDisable(!songIsSelected); // Enable/disable Add button

            // Update Play button state ONLY if player is idle (not playing/paused)
            if (playerService == null ||
                (playerService.getStatus() != MediaPlayer.Status.PLAYING &&
                 playerService.getStatus() != MediaPlayer.Status.PAUSED)) {
                updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
            }

        });
        addToQueueButton.setDisable(true); // Initially disable Add button
    }


    /** Adds listeners to the playback slider to handle user seeking. */
    private void setupPlaybackSliderListeners() {
        // Flag when user starts dragging/pressing
        playbackSlider.setOnMousePressed(event -> {
            // Only allow seeking if a song is loaded and player service exists
            if (playerService != null && playerService.getCurrentSong() != null) {
                isUserSeeking = true;
                // Optional: You could update the time label here too, or rely on drag
                // currentTimeLabel.setText(formatTime(playbackSlider.getValue()));
            } else {
                event.consume(); // Prevent interaction if no song loaded
            }
        });

        // Optional: Update time label visually while user is dragging
        playbackSlider.setOnMouseDragged(_event -> {
            if (isUserSeeking) {
                currentTimeLabel.setText(formatTime(playbackSlider.getValue()));
            }
        });

        // Perform seek when user releases slider (handles both clicks and drags)
        playbackSlider.setOnMouseReleased(_event -> {
            if (isUserSeeking && playerService != null) {
                // Only seek if a song is actually loaded
                if (playerService.getCurrentSong() != null) {
                    long seekMillis = (long) playbackSlider.getValue();
                    System.out.println("Slider released - Seeking to " + seekMillis + "ms");
                    playerService.seek(seekMillis);
                } else {
                    System.out.println("Slider released, but no song loaded to seek.");
                }
            }
            // Always reset the flag when the mouse is released over the slider
            isUserSeeking = false;
        });
    }


    // --- Service Injection & Post-Injection Setup ---
    public void setPlayerService(PlayerService playerService) {
        this.playerService = playerService;
        System.out.println("PlayerService injected.");
    }

    public void setLyricsService(LyricsService lyricsService) {
        this.lyricsService = lyricsService;
        System.out.println("LyricsService injected.");
    }

    public void setQueueService(QueueService queueService) {
        // Remove listener from old service if re-injected
        if (this.queueService != null) {
            this.queueService.getQueue().removeListener(queueChangeListener);
        }
        this.queueService = queueService;
        System.out.println("QueueService injected.");
        // Add listener to the new service's queue
        if (this.queueService != null) {
            this.queueService.getQueue().addListener(queueChangeListener);
            updateQueueDisplay(); // Initial update
        }
    }

    /**
    * Method called after all services are injected to set up bindings,
    * listeners, and load initial data.
    * Called from TuneUpApplication.start().
    */
    public void initializeBindingsAndListeners() {
        System.out.println("Initializing service-dependent bindings, listeners, and data...");
        if (playerService == null || lyricsService == null || queueService == null) {
            System.err.println("ERROR: Cannot initialize bindings - one or more services are null!");
            // Optionally show an error dialog to the user
            return;
        }

        populateGenreFilter();
        updateSongTableView(); // Initial data load for table

        setupPlayerBindingsAndListeners(); // Sets up player listeners AND the slider binding
        setupLyricsBindingsAndListeners();

        // Initialize control states based on initial (likely UNKNOWN) status
        updateControlsBasedOnStatus(playerService.getStatus());
        // Initialize Now Playing display based on initial song (likely null)
        updateNowPlayingDisplay(playerService.getCurrentSong());
    }


    // --- Setup Bindings and Listeners Dependent on Services ---
    /** Sets up bindings and listeners related to the PlayerService. */
    private void setupPlayerBindingsAndListeners() {
        if (playerService == null) return;

        // Listener for Total Duration -> Slider Max & Label
        playerService.totalDurationProperty().addListener((_obs, _oldVal, newVal) -> Platform.runLater(() -> {
            double totalMillis = newVal.doubleValue(); // Use doubleValue for slider max
            playbackSlider.setMax(totalMillis > 0 ? totalMillis : 0.0);
            totalDurationLabel.setText(formatTime(newVal.longValue())); // Use longValue for display
        }));

        // Listener for Current Time -> Slider Value, Label & Lyrics Update
        playerService.currentTimeProperty().addListener((_obs, _oldVal, newVal) -> Platform.runLater(() -> {
            // Only update slider position if user is NOT actively dragging/seeking
            // Note: isValueChanging check might be redundant now with the new isUserSeeking logic, but harmless
            if (!isUserSeeking /* && !playbackSlider.isValueChanging() */) {
                playbackSlider.setValue(newVal.doubleValue()); // Use doubleValue for slider value
            }
            currentTimeLabel.setText(formatTime(newVal.longValue())); // Use longValue for display
            if (lyricsService != null) {
                lyricsService.updateCurrentDisplayLines(newVal.longValue());
            }
        }));

        // Listener for Player Status -> Update UI Controls
        playerService.statusProperty().addListener((_obs, oldStatus, newStatus) -> Platform.runLater(() -> {
            updateControlsBasedOnStatus(newStatus);

            // --- Auto-Play Next Logic ---
            // This condition detects when playback finishes naturally or is stopped from playing state.
            if (oldStatus == MediaPlayer.Status.PLAYING && (newStatus == MediaPlayer.Status.STOPPED || newStatus == MediaPlayer.Status.READY)) {
                System.out.println("Playback stopped/ready after playing. Triggering auto playNextSong.");
                playNextSong(true); // MODIFIED: Pass true for auto-play context
            }
        }));

        // Listener for Current Song -> Update Now Playing Display & Reset Slider
        playerService.currentSongProperty().addListener((_obs, _oldSong, newSong) -> Platform.runLater(() -> {
            updateNowPlayingDisplay(newSong);
            if (newSong == null) { // If current song becomes null (e.g., after stop/skip last)
                // Reset slider and time labels
                playbackSlider.setValue(0);
                playbackSlider.setMax(0); // Reset max if no song
                currentTimeLabel.setText(formatTime(0.0));
                totalDurationLabel.setText(formatTime(0.0));
                // Lyrics are cleared by playNextSong or if loadAndPlaySong fails for lyrics
            }
            // Slider disable state will be updated by its binding.
        }));

        // Bind slider disable state here to react to currentSongProperty changes
        // The slider should be disabled if PlayerService is null or if no song is currently loaded in PlayerService
        playbackSlider.disableProperty().bind(
            Bindings.createBooleanBinding(() -> playerService.getCurrentSong() == null,
                                           playerService.currentSongProperty())
        );

        // Initialize based on initial state (redundant if called in initializeBindingsAndListeners, but safe)
        updateControlsBasedOnStatus(playerService.getStatus());
        updateNowPlayingDisplay(playerService.getCurrentSong());
    }


    /** Sets up bindings and listeners related to the LyricsService. */
    private void setupLyricsBindingsAndListeners() {
        if (lyricsService == null) return;

        // Listener for Display Lines -> Update Lyric Labels
        lyricsService.displayLinesProperty().addListener((_obs, _oldLines, newLines) -> Platform.runLater(() -> {
            previousLyricLabel.setText(getLyricTextOrEmpty(newLines, 0)); // Index 0 = Previous
            currentLyricLabel.setText(getLyricTextOrEmpty(newLines, 1));  // Index 1 = Current
            next1LyricLabel.setText(getLyricTextOrEmpty(newLines, 2));     // Index 2 = Next1
            next2LyricLabel.setText(getLyricTextOrEmpty(newLines, 3));     // Index 3 = Next2
        }));

        // Set initial empty state for lyrics
        previousLyricLabel.setText("");
        currentLyricLabel.setText("");
        next1LyricLabel.setText("");
        next2LyricLabel.setText("");
    }


    /** Helper to update control states based on MediaPlayer status and context. */
    private void updateControlsBasedOnStatus(MediaPlayer.Status status) {
        // --- Add Null Check for Status ---
        if (status == null) {
            System.err.println("Warning: updateControlsBasedOnStatus received null status. Defaulting controls.");
            status = MediaPlayer.Status.UNKNOWN; // Default to a safe state
        }
        // --- End Null Check ---

        boolean playing = (status == MediaPlayer.Status.PLAYING);
        boolean paused = (status == MediaPlayer.Status.PAUSED);
        boolean stoppedOrReady = (status == MediaPlayer.Status.STOPPED || status == MediaPlayer.Status.READY);
        boolean haltedOrUnknown = (status == MediaPlayer.Status.HALTED || status == MediaPlayer.Status.UNKNOWN);

        // Contextual checks
        boolean queueCanProvideSong = (queueService != null && !queueService.isEmpty());
        boolean songIsSelected = (currentlySelectedSong != null);
        boolean songIsLoaded = (playerService != null && playerService.getCurrentSong() != null);

        // Determine if the Play button should be enabled
        // Can play if: Paused OR (Ready/Stopped/Halted/Unknown AND (song loaded OR song selected OR queue can provide song))
        boolean canStartPlayback = paused || ((stoppedOrReady || haltedOrUnknown) && (songIsLoaded || songIsSelected || queueCanProvideSong));

        // --- Update Play/Pause Button ---
        playPauseButton.setText(playing ? "Pause" : "Play");
        // Disable if NOT currently playing AND cannot transition to playing
        playPauseButton.setDisable(!playing && !canStartPlayback);

        // --- Update Stop Button ---
        // Can only stop if actively playing or paused
        stopButton.setDisable(!playing && !paused);

        // --- Update Skip Button ---
        // Can only skip if the queue can provide the next song
        skipButton.setDisable(!queueCanProvideSong);


        // --- Visual Reset on Stop/Halt/Ready ---
        // If stopped/halted/ready/unknown, reset slider/time visually
        if (stoppedOrReady || haltedOrUnknown) {
            // Only reset if user isn't actively seeking AND slider isn't being changed by user click (isValueChanging)
            if (!isUserSeeking && !playbackSlider.isValueChanging()) { // isValueChanging for direct clicks on slider
                playbackSlider.setValue(0);
            }
            // Always update time label, as seeking might have changed slider but not actual time yet
            currentTimeLabel.setText(formatTime(0));

            // If truly stopped/halted AND no song is considered "loaded" in player, reset total duration
            if(haltedOrUnknown && (playerService == null || playerService.getCurrentSong() == null)) {
                totalDurationLabel.setText(formatTime(0));
                // playbackSlider.setMax(0) is handled by currentSongProperty listener if song becomes null
            }
        }
    }


    /** Helper to safely get text from the LyricLine list or return an empty string. */
    private String getLyricTextOrEmpty(List<LyricLine> lines, int index) {
        if (lines != null && index >= 0 && index < lines.size()) {
            LyricLine line = lines.get(index);
            return (line != null) ? line.getText() : ""; // Handle potential null LyricLine in list
        }
        return ""; // Return empty string if index is invalid or list is null
    }


    // --- Data Loading and UI Update ---
    /** Populates the genre filter ComboBox with distinct genres from the database. */
    private void populateGenreFilter() {
        if (genreFilterComboBox == null) return;

        Set<String> distinctGenres = SongDAO.getDistinctGenres(); // Assuming this returns sorted (e.g., TreeSet)
        ObservableList<String> genreOptions = FXCollections.observableArrayList();
        genreOptions.add(ALL_GENRES); // Add default option
        genreOptions.addAll(distinctGenres); // Add fetched genres

        genreFilterComboBox.setItems(genreOptions);
        genreFilterComboBox.setValue(ALL_GENRES); // Set default selection
    }


    /** Updates the song TableView based on current search/filter criteria. */
    private void updateSongTableView() {
        if (songTableView == null || searchTextField == null || genreFilterComboBox == null) {
            System.err.println("updateSongTableView called before required UI elements injected.");
            return;
        }

        String searchText = searchTextField.getText();
        String genreFilter = genreFilterComboBox.getValue();

        // Treat 'All Genres' selection as null filter for DAO
        if (ALL_GENRES.equals(genreFilter)) {
            genreFilter = null;
        }

        // Consider running this on a background thread if DAO takes time
        List<Song> filteredSongs = SongDAO.findSongsByCriteria(searchText, genreFilter);
        songTableView.setItems(FXCollections.observableArrayList(filteredSongs));
        System.out.println("Updated song table view. Found " + filteredSongs.size() + " songs matching criteria.");
    }


    // --- Queue Display Update ---
    /** Updates the queue display labels based on the current state of the QueueService. */
    private void updateQueueDisplay() {
        if (queueService == null || queueSong1Label == null) return; // Check required elements

        ObservableList<Song> currentQueue = queueService.getQueue();
        int queueSize = currentQueue.size();

        // Update labels for the first 3 songs (or fewer)
        queueSong1Label.setText("1. " + (queueSize >= 1 ? formatSongForQueue(currentQueue.get(0)) : "-"));
        queueSong2Label.setText("2. " + (queueSize >= 2 ? formatSongForQueue(currentQueue.get(1)) : "-"));
        queueSong3Label.setText("3. " + (queueSize >= 3 ? formatSongForQueue(currentQueue.get(2)) : "-"));

        // Update the count label for remaining songs
        int remaining = Math.max(0, queueSize - 3);
        queueCountLabel.setText((remaining > 0) ? "(+" + remaining + " more)" : "(+0 more)"); // Adjusted format

        // Optional: Collapse TitledPane if queue becomes empty
        if (queueTitledPane != null) {
            queueTitledPane.setExpanded(!currentQueue.isEmpty());
        }

        // Update control states that depend on queue emptiness (like Play/Skip buttons)
        updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
    }


    /** Formats a Song object for display in the queue labels. */
    private String formatSongForQueue(Song song) {
        return (song != null) ? song.toString() : "-"; // Assumes Song.toString() is suitable (e.g., "Title - Artist")
    }


    // --- Now Playing Display Update ---
    /**
    * Updates the 'Now Playing' labels in the UI.
    * Clears labels if the provided song is null.
    * @param song The currently playing Song, or null if none.
    */
    private void updateNowPlayingDisplay(Song song) {
        if (nowPlayingTitleLabel != null && nowPlayingArtistLabel != null) {
            if (song != null) {
                nowPlayingTitleLabel.setText(song.getTitle() != null ? song.getTitle() : "Unknown Title");
                nowPlayingArtistLabel.setText(song.getArtist() != null ? song.getArtist() : "Unknown Artist");
                // Update other labels if added (e.g., album)
            } else {
                nowPlayingTitleLabel.setText("-"); // Default text when nothing is playing
                nowPlayingArtistLabel.setText("-");
                // Clear other labels if added
            }
        } else {
            System.err.println("Warning: Now Playing labels not injected correctly or FXML not updated.");
        }
    }


    // --- FXML Action Handlers ---
    @FXML
    private void handlePlayPause() {
        System.out.println("Play/Pause clicked");
        if (playerService == null) return;

        MediaPlayer.Status status = playerService.getStatus();

        if (status == MediaPlayer.Status.PLAYING) {
            playerService.pause();
        } else if (status == MediaPlayer.Status.PAUSED) {
            playerService.play(); // Resume from pause
        } else { // Status is STOPPED, READY, HALTED, UNKNOWN
            Song songSelectedInUI = this.currentlySelectedSong; // Capture before potential clear

            if (songSelectedInUI != null) {
                // Add the selected song to the END of the queue
                System.out.println("PlayPause: Song selected in UI. Adding to END of queue: " + songSelectedInUI.getTitle());
                if (queueService != null) {
                    // Optional: Consider logic to prevent adding duplicates if the song is already in the queue.
                    // For now, simply add to the end.
                    queueService.addSong(songSelectedInUI); // Changed from addSongToFront
                }
                playNextSong(false); // Let playNextSong handle playing from the (now updated) queue
                songTableView.getSelectionModel().clearSelection(); // Deselect the song
            } else {
                // No song selected in UI.
                // Attempt to play from queue (if anything is there from previous adds)
                // or handle empty queue scenario (playNextSong will manage this).
                System.out.println("PlayPause: No song selected in UI. Attempting to play next from queue.");
                playNextSong(false);
            }
        }
    }

    @FXML
    private void handleStop() {
        System.out.println("Stop clicked");
        if (playerService != null) {
            playerService.stop();
            // playerService.currentSongProperty listener handles UI updates including NowPlaying
        }
    }

    @FXML
    private void handleSkip() { // Corrected method name
        System.out.println("Skip clicked");
        if (playerService != null) {
            playerService.stop(); // Stop current song first (this resets player state)
            playNextSong(false); // MODIFIED: Pass false for user-initiated skip
        }
    }

    @FXML
    private void handleAddToQueue() {
        if (currentlySelectedSong != null && queueService != null) {
            System.out.println("Adding to queue: " + currentlySelectedSong);
            queueService.addSong(currentlySelectedSong); // Listener updates UI
            songTableView.getSelectionModel().clearSelection(); // Deselect the song
        } else {
            System.out.println("Add to Queue: No song selected or queue service unavailable.");
            // Consider showing a brief user notification/alert
        }
    }

    @FXML
    private void handleFullscreenToggle() {
        System.out.println("Fullscreen toggle: " + fullscreenToggleButton.isSelected());
        // TODO: Implement fullscreen logic (Requires Stage reference passed from TuneUpApplication)
    }

    @FXML
    private void handleThemeToggle() {
        System.out.println("Theme toggle: " + themeToggleButton.isSelected());
        // TODO: Implement theme switching logic (CSS manipulation, requires Scene access)
    }


    // --- Helper Methods ---
    /**
    * Attempts to play the next song.
    * If isAutoPlayTrigger is true (song just finished), it will not play the selected song from the library if the queue is empty.
    * If isAutoPlayTrigger is false (user action like Play or Skip), it may play the selected song if the queue is empty.
    *
    * Priority:
    * 1. Play next song from QueueService.
    * 2. If not auto-play and queue empty: Play currently selected song in TableView (if player isn't already playing/paused).
    * Stops the player if no song is available.
    * @param isAutoPlayTrigger true if called due to a song finishing, false if due to direct user action.
    */
    private void playNextSong(boolean isAutoPlayTrigger) { // MODIFIED: Added boolean parameter
        if (playerService == null) return;

        // Reset Now Playing labels and clear lyrics
        if (nowPlayingTitleLabel != null) nowPlayingTitleLabel.setText("-");
        if (nowPlayingArtistLabel != null) nowPlayingArtistLabel.setText("-");
        if (lyricsService != null) {
            lyricsService.clearLyrics();
        }

        Song songToPlay = null;

        // 1. Try getting the next song from the queue
        if (queueService != null && !queueService.isEmpty()) {
            songToPlay = queueService.getNextSong(); // Removes from queue
            if (songToPlay != null) {
                System.out.println("Playing next from queue: " + songToPlay);
            }
        }

        // 2. If queue was empty:
        //    - If NOT an auto-play trigger (i.e., user initiated action like Play or Skip), try playing the currently selected song.
        //    - If it IS an auto-play trigger, DO NOT play the selected song.
        if (songToPlay == null) { // Queue is empty or failed to get song
            if (!isAutoPlayTrigger && currentlySelectedSong != null) {
                // This branch is for user-initiated actions (Play/Skip) when queue is empty
                MediaPlayer.Status currentStatus = playerService.getStatus();
                // Ensure player is in a state where it can start a new song
                if (currentStatus != MediaPlayer.Status.PLAYING && currentStatus != MediaPlayer.Status.PAUSED) {
                    songToPlay = currentlySelectedSong;
                    System.out.println("Queue empty, user initiated play of selected song: " + songToPlay);
                } else {
                    System.out.println("Queue empty, song selected, but player is busy. Not playing selected song for this user action.");
                }
            } else if (isAutoPlayTrigger) {
                // This branch is for auto-play when queue is empty. We specifically DO NOT play selected song.
                System.out.println("Auto-play: Queue empty, and per new behavior, not playing selected song from library.");
            } else {
                 // This branch: not auto-play, but no song selected. Or some other unlikely combo.
                System.out.println("Queue empty, no selected song to play, or not an auto-play scenario for selected song.");
            }
        }


        // 3. Load and play the determined song, or stop if none found
        if (songToPlay != null) {
            loadAndPlaySong(songToPlay);
        } else {
            System.out.println("PlayNextSong: No song available to play.");
            // Ensure player is fully stopped if nothing could be played and it was playing/paused.
            MediaPlayer.Status currentStatus = playerService.getStatus();
            if (currentStatus == MediaPlayer.Status.PLAYING || currentStatus == MediaPlayer.Status.PAUSED) {
                playerService.stop(); // Stop if it was playing/paused without a next song
            } else {
                // If already stopped/ready/halted, ensure controls reflect inability to play
                updateControlsBasedOnStatus(currentStatus);
            }
        }
    }


    /**
    * Helper method to load a song into PlayerService and LyricsService
    * and request playback to start when ready.
    * Assumes PlayerService.loadSong returns boolean and handles auto-play internally.
    * @param song The song to load and play.
    */
    private void loadAndPlaySong(Song song) {
        if (song == null || playerService == null || lyricsService == null) {
            System.err.println("Cannot load/play song: Null song or service(s).");
            if(playerService != null) playerService.stop(); // Ensure player stops if called with null song or missing service
            return;
        }

        System.out.println("Requesting load and play for: " + song.getTitle());

        // Load lyrics (can happen concurrently or before player load)
        // Consider if lyrics loading failure should prevent audio playback attempt
        lyricsService.loadLyricsForSong(song); // This method should handle its own errors/nulls

        // Load song into player and request auto-play on ready.
        // The PlayerService's setOnReady handler is expected to call play() if auto-play is requested.
        boolean loadingInitiated = playerService.loadSong(song, true); // Pass true to request auto-play

        if (!loadingInitiated) {
            // Handle case where PlayerService.loadSong indicated immediate failure (e.g., file not found before async load)
            System.err.println("Failed to initiate loading for " + song.getTitle() + ". Player status should reflect this (e.g., HALTED).");
            // Optionally show user alert here, though PlayerService might also log/handle
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Playback Error");
            alert.setHeaderText("Could not load audio");
            alert.setContentText("Failed to load the audio file for:\n" + song.getTitle() + " - " + song.getArtist() + "\nPlease check file integrity and location.");
            alert.showAndWait();
            updateControlsBasedOnStatus(MediaPlayer.Status.HALTED); // Reflect error state in UI
        }
        // PlayerService handles the actual 'play()' call internally when ready and if auto-play was true.
        // UI updates (Now Playing, slider, etc.) are driven by listeners on PlayerService properties.
    }


    /**
    * Formats time in milliseconds to mm:ss string.
    * @param millis Time in milliseconds.
    * @return Formatted string "mm:ss" or "0:00" if input is invalid/negative.
    */
    private String formatTime(long millis) {
        if (millis < 0 || Long.MAX_VALUE == millis) { // Check for invalid values like Duration.UNKNOWN
            return "0:00"; // Or your preferred placeholder for unknown duration
        }

        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /** Overload for double, converting to long first. Handles NaN, Infinity. */
    private String formatTime(double millis) {
        if (Double.isNaN(millis) || Double.isInfinite(millis) || millis < 0) {
            return "0:00"; // Or placeholder
        }
        return formatTime((long) millis);
    }

} // End of MainController class
