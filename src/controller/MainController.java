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
 * Corresponds to SRS sections regarding UI interaction and coordination (e.g., FR1.x, FR2.x, FR3.x handling).
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
    @FXML private Button skipButton; // Corrected fx:id in FXML should be "skipButton"
    @FXML private ToggleButton fullscreenToggleButton;
    @FXML private ToggleButton themeToggleButton;

    // --- Service Dependencies ---
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;

    // --- State ---
    private Song currentlySelectedSong = null; // Keep track of the selected song from the TableView
    private boolean isUserSeeking = false; // Flag to prevent time updates while user drags slider

    // --- Constants ---
    private static final String ALL_GENRES = "All Genres";

    // Listener for queue changes
    private final ListChangeListener<Song> queueChangeListener = _change ->
            Platform.runLater(this::updateQueueDisplay); // Ensure UI update on FX thread

    // --- Initialization ---

    /**
     * Called by FXMLLoader after FXML fields are injected.
     * Sets up UI components that don't depend on injected services.
     *
     * @param location  The location used to resolve relative paths for the root object, or null if not known.
     * @param resources The resources used to localize the root object, or null if not known.
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
        if (stopButton != null) {
            // Sets the text for the stop button, which implies clearing all playback and queue.
            stopButton.setText("Stop All");
        }
    }

    /**
     * Configures the TableView columns to map to Song properties (title and artist).
     * Supports FR2.2 (Display song metadata).
     */
    private void setupTableViewColumns() {
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        artistColumn.setCellValueFactory(new PropertyValueFactory<>("artist"));
    }

    /**
     * Adds listeners to the search text field and genre combo box.
     * Triggers `updateSongTableView` when their values change.
     * Supports FR2.3 (Search by Title/Artist) and FR2.5 (Filter by Genre).
     */
    private void addSearchAndFilterListeners() {
        searchTextField.textProperty().addListener((_obs, _ov, _nv) -> updateSongTableView());
        genreFilterComboBox.valueProperty().addListener((_obs, _ov, _nv) -> updateSongTableView());
    }

    /**
     * Adds a listener to the TableView's selection model to track the selected song.
     * Updates `currentlySelectedSong` and enables/disables the "Add to Queue" button.
     * Also updates playback controls if the player is idle.
     */
    private void addTableViewSelectionListener() {
        TableView.TableViewSelectionModel<Song> selectionModel = songTableView.getSelectionModel();
        selectionModel.setSelectionMode(SelectionMode.SINGLE); // Ensure single selection

        selectionModel.selectedItemProperty().addListener((_observable, _oldSelection, newSelection) -> {
            this.currentlySelectedSong = newSelection;
            boolean songIsSelected = (newSelection != null);
            System.out.println(songIsSelected ? "Song selected: " + newSelection : "Song selection cleared.");

            addToQueueButton.setDisable(!songIsSelected); // Enable/disable Add button based on selection

            // Update Play button state ONLY if player is idle (not playing/paused)
            // This allows the Play button to become enabled if a song is selected while player is stopped.
            if (playerService == null ||
                (playerService.getStatus() != MediaPlayer.Status.PLAYING &&
                 playerService.getStatus() != MediaPlayer.Status.PAUSED)) {
                updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
            }
        });
        addToQueueButton.setDisable(true); // Initially disable Add to Queue button
    }

    /**
     * Adds listeners to the playback slider to handle user seeking.
     * Supports FR1.7 (Seek functionality).
     */
    private void setupPlaybackSliderListeners() {
        // Flag when user starts dragging/pressing the slider thumb
        playbackSlider.setOnMousePressed(event -> {
            if (playerService != null && playerService.getCurrentSong() != null) {
                isUserSeeking = true;
            } else {
                event.consume(); // Prevent interaction if no song is loaded
            }
        });

        // Optional: Update time label visually while user is dragging the thumb
        playbackSlider.setOnMouseDragged(_event -> {
            if (isUserSeeking) {
                currentTimeLabel.setText(formatTime(playbackSlider.getValue()));
            }
        });

        // Perform seek when user releases the slider thumb (after drag or direct click)
        playbackSlider.setOnMouseReleased(_event -> {
            if (isUserSeeking && playerService != null) {
                if (playerService.getCurrentSong() != null) {
                    long seekMillis = (long) playbackSlider.getValue();
                    System.out.println("Slider released - Seeking to " + seekMillis + "ms");
                    playerService.seek(seekMillis);
                } else {
                    System.out.println("Slider released, but no song loaded to seek.");
                }
            }
            isUserSeeking = false; // Reset flag when mouse is released
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
        if (this.queueService != null) { // Remove listener from old service if any
            this.queueService.getQueue().removeListener(queueChangeListener);
        }
        this.queueService = queueService;
        System.out.println("QueueService injected.");
        if (this.queueService != null) { // Add listener to the new service's queue
            this.queueService.getQueue().addListener(queueChangeListener);
            updateQueueDisplay(); // Initial update of queue display
        }
    }

    /**
     * Method called after all services are injected (typically from TuneUpApplication).
     * Sets up bindings, listeners that depend on services, and loads initial data.
     */
    public void initializeBindingsAndListeners() {
        System.out.println("Initializing service-dependent bindings, listeners, and data...");
        if (playerService == null || lyricsService == null || queueService == null) {
            System.err.println("ERROR: Cannot initialize bindings - one or more services are null!");
            // Optionally show an error dialog to the user here
            return;
        }

        populateGenreFilter();
        updateSongTableView(); // Load initial song list into table

        setupPlayerBindingsAndListeners();
        setupLyricsBindingsAndListeners();

        // Initialize control states based on initial (likely UNKNOWN) player status
        updateControlsBasedOnStatus(playerService.getStatus());
        // Initialize Now Playing display based on initial song (likely null)
        updateNowPlayingDisplay(playerService.getCurrentSong());
    }

    // --- Setup Bindings and Listeners Dependent on Services ---

    /**
     * Sets up bindings and listeners related to the PlayerService properties
     * (totalDuration, currentTime, status, currentSong).
     */
    private void setupPlayerBindingsAndListeners() {
        if (playerService == null) return;

        // Listener for Total Duration -> Updates Slider Max & Total Duration Label
        playerService.totalDurationProperty().addListener((_obs, _oldVal, newVal) -> Platform.runLater(() -> {
            double totalMillis = newVal.doubleValue();
            playbackSlider.setMax(totalMillis > 0 ? totalMillis : 0.0);
            totalDurationLabel.setText(formatTime(newVal.longValue()));
        }));

        // Listener for Current Time -> Updates Slider Value, Current Time Label & Triggers Lyrics Update
        // Supports FR1.6 (Display current playback time), FR3.4 (Synchronize lyrics)
        playerService.currentTimeProperty().addListener((_obs, _oldVal, newVal) -> Platform.runLater(() -> {
            if (!isUserSeeking) { // Only update slider if user isn't dragging it
                playbackSlider.setValue(newVal.doubleValue());
            }
            currentTimeLabel.setText(formatTime(newVal.longValue()));
            if (lyricsService != null) {
                lyricsService.updateCurrentDisplayLines(newVal.longValue());
            }
        }));

        // Listener for Player Status -> Updates UI Controls & Handles Auto-Play Next
        playerService.statusProperty().addListener((_obs, oldStatus, newStatus) -> Platform.runLater(() -> {
            updateControlsBasedOnStatus(newStatus);

            // Auto-Play Next Logic (FR2.10)
            // If playback stopped/ready after being in PLAYING state, it implies the song finished or was manually stopped.
            if (oldStatus == MediaPlayer.Status.PLAYING &&
                (newStatus == MediaPlayer.Status.STOPPED || newStatus == MediaPlayer.Status.READY)) {
                System.out.println("Playback stopped/ready after playing. Triggering auto playNextSong.");
                playNextSong(true); // Pass true to indicate auto-play context
            }
        }));

        // Listener for Current Song in PlayerService -> Updates Now Playing Display & Lyrics
        playerService.currentSongProperty().addListener((_obs, _oldSong, newSong) -> Platform.runLater(() -> {
            updateNowPlayingDisplay(newSong); // Update "Now Playing" text labels

            if (newSong != null) {
                // A new song has been loaded into PlayerService. Load its lyrics.
                if (lyricsService != null) {
                    lyricsService.loadLyricsForSong(newSong); // FR3.1
                }
            } else {
                // Current song in PlayerService is null (e.g., after stop, end of queue). Reset UI elements.
                resetUIForNoActiveSong();
            }
        }));

        // Bind slider's disableProperty: slider is disabled if no song is currently loaded in PlayerService.
        playbackSlider.disableProperty().bind(
            Bindings.createBooleanBinding(() -> playerService.getCurrentSong() == null,
                        playerService.currentSongProperty())
        );

        // Initial UI state update after bindings are set.
        updateControlsBasedOnStatus(playerService.getStatus());
        updateNowPlayingDisplay(playerService.getCurrentSong());
    }
    
    /**
     * Helper method to reset UI elements when no song is active in the player.
     * Clears time labels, slider, and lyrics.
     */
    private void resetUIForNoActiveSong() {
        if (playbackSlider != null) {
            playbackSlider.setValue(0);
            playbackSlider.setMax(0); // No duration available
        }
        if (currentTimeLabel != null) {
            currentTimeLabel.setText(formatTime(0.0));
        }
        if (totalDurationLabel != null) {
            totalDurationLabel.setText(formatTime(0.0));
        }
        if (lyricsService != null) {
            lyricsService.loadLyricsForSong(null); // Clears lyrics in LyricsService
        }
    }


    /**
     * Sets up bindings and listeners related to the LyricsService (e.g., displayLinesProperty).
     * Supports FR3.4 (Display synchronized lyrics lines).
     */
    private void setupLyricsBindingsAndListeners() {
        if (lyricsService == null) return;

        // Listener for LyricsService's displayLinesProperty -> Updates Lyric Labels in UI
        lyricsService.displayLinesProperty().addListener((_obs, _oldLines, newLines) -> Platform.runLater(() -> {
            previousLyricLabel.setText(getLyricTextOrEmpty(newLines, 0)); // Index 0 = Previous
            currentLyricLabel.setText(getLyricTextOrEmpty(newLines, 1));  // Index 1 = Current
            next1LyricLabel.setText(getLyricTextOrEmpty(newLines, 2));    // Index 2 = Next1
            next2LyricLabel.setText(getLyricTextOrEmpty(newLines, 3));    // Index 3 = Next2
        }));

        // Set initial empty state for lyric labels
        previousLyricLabel.setText("");
        currentLyricLabel.setText("");
        next1LyricLabel.setText("");
        next2LyricLabel.setText("");
    }

    /**
     * Updates the state (text, enabled/disabled) of playback control buttons
     * based on the MediaPlayer status and other contextual information (queue state, selected song).
     *
     * @param status The current MediaPlayer.Status.
     */
    private void updateControlsBasedOnStatus(MediaPlayer.Status status) {
        if (status == null) { // Defensive null check for status
            System.err.println("Warning: updateControlsBasedOnStatus received null status. Defaulting controls.");
            status = MediaPlayer.Status.UNKNOWN;
        }

        boolean playing = (status == MediaPlayer.Status.PLAYING);
        boolean paused = (status == MediaPlayer.Status.PAUSED);
        boolean stoppedOrReady = (status == MediaPlayer.Status.STOPPED || status == MediaPlayer.Status.READY);
        boolean haltedOrUnknown = (status == MediaPlayer.Status.HALTED || status == MediaPlayer.Status.UNKNOWN);

        boolean queueCanProvideSong = (queueService != null && !queueService.isEmpty());
        boolean songIsSelectedInLibrary = (currentlySelectedSong != null);
        boolean songIsLoadedInPlayer = (playerService != null && playerService.getCurrentSong() != null);

        // Determine if the Play button should be enabled
        boolean canStartPlayback = paused || // Can resume if paused
                                  ((stoppedOrReady || haltedOrUnknown) && // Or if stopped/ready/halted/unknown
                                   (songIsLoadedInPlayer || songIsSelectedInLibrary || queueCanProvideSong)); // AND there's something to play

        playPauseButton.setText(playing ? "Pause" : "Play");
        playPauseButton.setDisable(!playing && !canStartPlayback); // Disable if not playing AND cannot start playback

        stopButton.setDisable(!playing && !paused); // Can only stop if actively playing or paused

        skipButton.setDisable(!queueCanProvideSong); // Can only skip if queue has songs

        // Visual reset for slider/time if player is stopped, halted, or ready (and not being sought by user)
        if (stoppedOrReady || haltedOrUnknown) {
            if (!isUserSeeking && (playbackSlider != null && !playbackSlider.isValueChanging())) {
                playbackSlider.setValue(0);
            }
            if (currentTimeLabel != null) currentTimeLabel.setText(formatTime(0));
            // If player truly stopped/halted AND no song is loaded, reset total duration label.
            // Max slider value is reset by currentSongProperty listener when song becomes null.
            if(haltedOrUnknown && !songIsLoadedInPlayer && totalDurationLabel != null) {
                totalDurationLabel.setText(formatTime(0));
            }
        }
    }

    /**
     * Helper to safely get text from the LyricLine list or return an empty string.
     * Used to populate lyric display labels.
     *
     * @param lines The list of LyricLine objects (previous, current, next1, next2).
     * @param index The index of the line to retrieve.
     * @return The lyric text, or an empty string if the line is null or index is invalid.
     */
    private String getLyricTextOrEmpty(List<LyricLine> lines, int index) {
        if (lines != null && index >= 0 && index < lines.size()) {
            LyricLine line = lines.get(index);
            return (line != null) ? line.getText() : "";
        }
        return "";
    }

    // --- Data Loading and UI Update ---

    /**
     * Populates the genre filter ComboBox with distinct genres from the database.
     * Supports FR2.5 (Filter by Genre).
     */
    private void populateGenreFilter() {
        if (genreFilterComboBox == null) return;
        Set<String> distinctGenres = SongDAO.getDistinctGenres();
        ObservableList<String> genreOptions = FXCollections.observableArrayList();
        genreOptions.add(ALL_GENRES); // Default "All Genres" option
        genreOptions.addAll(distinctGenres); // Add genres from database
        genreFilterComboBox.setItems(genreOptions);
        genreFilterComboBox.setValue(ALL_GENRES); // Set default selection
    }

    /**
     * Updates the song TableView based on current search text and genre filter criteria.
     * Fetches data using SongDAO.findSongsByCriteria.
     * Supports FR2.6 (Update displayed list based on search/filter).
     */
    private void updateSongTableView() {
        if (songTableView == null || searchTextField == null || genreFilterComboBox == null) {
            System.err.println("updateSongTableView called before required UI elements injected.");
            return;
        }
        String searchText = searchTextField.getText();
        String genreFilter = genreFilterComboBox.getValue();
        if (ALL_GENRES.equals(genreFilter)) {
            genreFilter = null; // Treat "All Genres" as no filter for DAO
        }

        List<Song> filteredSongs = SongDAO.findSongsByCriteria(searchText, genreFilter);
        songTableView.setItems(FXCollections.observableArrayList(filteredSongs));
        System.out.println("Updated song table view. Found " + filteredSongs.size() + " songs matching criteria.");
    }

    // --- Queue Display Update ---

    /**
     * Updates the queue display labels (queueSong1Label, etc., and queueCountLabel)
     * based on the current state of the QueueService.
     * Supports FR2.9 (Display playback queue).
     */
    private void updateQueueDisplay() {
        if (queueService == null || queueSong1Label == null) return;

        ObservableList<Song> currentQueue = queueService.getQueue();
        int queueSize = currentQueue.size();

        queueSong1Label.setText("1. " + (queueSize >= 1 ? formatSongForQueue(currentQueue.get(0)) : " - "));
        queueSong2Label.setText("2. " + (queueSize >= 2 ? formatSongForQueue(currentQueue.get(1)) : " - "));
        queueSong3Label.setText("3. " + (queueSize >= 3 ? formatSongForQueue(currentQueue.get(2)) : " - "));

        int remaining = Math.max(0, queueSize - 3);
        queueCountLabel.setText((remaining > 0) ? "(+" + remaining + " more)" : "(+0 more)");

        if (queueTitledPane != null) { // Optional: Collapse TitledPane if queue is empty
            queueTitledPane.setExpanded(!currentQueue.isEmpty());
        }
        // Refresh control states as queue changes can affect Play/Skip button usability
        updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
    }

    /**
     * Formats a Song object for display in the queue labels.
     * @param song The Song object.
     * @return A string representation (e.g., "Title - Artist"), or "-" if song is null.
     */
    private String formatSongForQueue(Song song) {
        return (song != null) ? song.toString() : "-"; // Assumes Song.toString() is suitable
    }

    // --- Now Playing Display Update ---

    /**
     * Updates the 'Now Playing' labels (title and artist) in the UI.
     * Clears labels if the provided song is null.
     * @param song The currently playing Song, or null if none.
     */
    private void updateNowPlayingDisplay(Song song) {
        if (nowPlayingTitleLabel != null && nowPlayingArtistLabel != null) {
            if (song != null) {
                nowPlayingTitleLabel.setText(song.getTitle() != null ? song.getTitle() : "Unknown Title");
                nowPlayingArtistLabel.setText(song.getArtist() != null ? song.getArtist() : "Unknown Artist");
            } else {
                nowPlayingTitleLabel.setText("-"); // Default text when nothing is playing
                nowPlayingArtistLabel.setText("-");
            }
        } else {
            System.err.println("Warning: Now Playing labels not injected correctly or FXML not updated.");
        }
    }

    // --- FXML Action Handlers ---

    /**
     * Handles the Play/Pause button action.
     * Implements FR1.3 (Play), FR1.4 (Pause).
     * If player is stopped/ready:
     * - Plays the currently selected song from the library if one is selected.
     * - Otherwise, plays the next song from the queue if available.
     */
    @FXML
    private void handlePlayPause() {
        System.out.println("Play/Pause clicked");
        if (playerService == null) return;

        MediaPlayer.Status status = playerService.getStatus();

        if (status == MediaPlayer.Status.PLAYING) {
            playerService.pause(); // FR1.4
        } else if (status == MediaPlayer.Status.PAUSED) {
            playerService.play(); // Resume playback
        } else { // Status is STOPPED, READY, HALTED, UNKNOWN
            if (this.currentlySelectedSong != null) {
                // FR1.3: Start playback of the selected song
                Song songToPlay = this.currentlySelectedSong;
                System.out.println("PlayPause: Playing selected song from library: " + songToPlay.getTitle());
                loadAndPlaySong(songToPlay);
                // Optional: Clear selection after initiating play, or let it persist.
                // songTableView.getSelectionModel().clearSelection();
            } else if (queueService != null && !queueService.isEmpty()) {
                // FR1.3: ...or the next song in the queue if no song is actively selected
                System.out.println("PlayPause: No song selected in library. Attempting to play next from queue.");
                playNextSong(false); // This will get the next song from the queue
            } else {
                System.out.println("PlayPause: No song selected and queue is empty. Nothing to play.");
                updateControlsBasedOnStatus(MediaPlayer.Status.STOPPED); // Ensure UI reflects no action taken
            }
        }
    }

    /**
     * Handles the Stop button action. Clears current playback and the entire queue.
     * This is a "Stop All" functionality.
     * Implements FR1.5 (Stop - extended to clear queue as per button text "Stop All").
     */
    @FXML
    private void handleStop() {
        System.out.println("Stop All clicked");
        if (playerService != null) {
            // playerService.stop() would stop and reset current song.
            // loadSong(null, false) effectively stops and unloads the current song.
            playerService.loadSong(null, false);
        }
        if (queueService != null) {
            queueService.clear(); // Clear the playback queue
            System.out.println("Queue cleared by Stop All.");
        }
        // UI updates are handled by listeners on player status and queue changes.
        // Explicitly call updateControls to ensure immediate effect if no listeners fire.
        updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
        updateQueueDisplay(); // Update queue display (should show empty)
    }

    /**
     * Handles the Skip (Next) button action. Stops the current song and plays the next from the queue.
     * Implements FR2.7 (Skip to next song).
     */
    @FXML
    private void handleSkip() { // fx:id in FXML should be "skipButton"
        System.out.println("Skip clicked");
        if (playerService != null) {
            playerService.stop(); // Stop current song first (this also resets player state)
            playNextSong(false);  // Attempt to play the next song from the queue (not auto-play context)
        }
    }

    /**
     * Handles the "Add Selected to Queue" button action.
     * Adds the currently selected song from the library to the playback queue.
     * Implements FR2.8 (Add song to queue).
     */
    @FXML
    private void handleAddToQueue() {
        if (currentlySelectedSong != null && queueService != null) {
            System.out.println("Adding to queue: " + currentlySelectedSong);
            queueService.addSong(currentlySelectedSong); // QueueService listener will update UI
            songTableView.getSelectionModel().clearSelection(); // Deselect after adding
        } else {
            System.out.println("Add to Queue: No song selected or queue service unavailable.");
        }
    }

    /**
     * Handles the Fullscreen toggle button action. (Placeholder)
     * Intended for FR4.3 (Fullscreen mode).
     */
    @FXML
    private void handleFullscreenToggle() {
        System.out.println("Fullscreen toggle: " + fullscreenToggleButton.isSelected());
        // TODO: Implement fullscreen logic (Requires Stage reference from TuneUpApplication)
    }

    /**
     * Handles the Theme toggle button action. (Placeholder)
     * Intended for FR4.4 (Theme switching).
     */
    @FXML
    private void handleThemeToggle() {
        System.out.println("Theme toggle: " + themeToggleButton.isSelected());
        // TODO: Implement theme switching logic (CSS manipulation, requires Scene access)
    }

    // --- Helper Methods ---

    /**
     * Attempts to play the next song based on queue content and selection state.
     *
     * @param isAutoPlayTrigger true if called due to a song finishing (auto-play context),
     *                          false if due to direct user action (e.g., Play, Skip).
     */
    private void playNextSong(boolean isAutoPlayTrigger) {
        if (playerService == null) return;

        Song songToPlay = null;

        // 1. Try getting the next song from the queue
        if (queueService != null && !queueService.isEmpty()) {
            songToPlay = queueService.getNextSong(); // Removes from queue and returns it
            if (songToPlay != null) {
                System.out.println("Playing next from queue: " + songToPlay);
            }
        }

        // 2. If queue was empty AND this is NOT an auto-play trigger (i.e., user action like initial Play or Skip),
        //    AND a song is selected in the library, play the selected song.
        //    If it IS an auto-play trigger, DO NOT play a selected library song if queue is empty.
        if (songToPlay == null) { // Queue is empty or failed to get song
            if (!isAutoPlayTrigger && currentlySelectedSong != null) {
                // Ensure player isn't already playing/paused from a rapid previous action
                MediaPlayer.Status currentStatus = playerService.getStatus();
                if (currentStatus != MediaPlayer.Status.PLAYING && currentStatus != MediaPlayer.Status.PAUSED) {
                    songToPlay = currentlySelectedSong;
                    System.out.println("Queue empty, user action initiated play of selected library song: " + songToPlay);
                } else {
                     System.out.println("Queue empty, song selected, but player is busy. Not playing selected song now.");
                }
            } else if (isAutoPlayTrigger) {
                System.out.println("Auto-play: Queue empty. Not playing selected library song as per auto-play behavior.");
            } else {
                 System.out.println("Queue empty, no selected song, or not an auto-play scenario for selected song.");
            }
        }

        // 3. Load and play the determined song, or ensure player is stopped/cleared if no song found.
        if (songToPlay != null) {
            loadAndPlaySong(songToPlay);
        } else {
            System.out.println("PlayNextSong: No song available to play. Ensuring player is cleared.");
            // If a song was playing and player didn't naturally stop/clear, explicitly clear it.
            if (playerService.getCurrentSong() != null || playerService.getStatus() == MediaPlayer.Status.PLAYING || playerService.getStatus() == MediaPlayer.Status.PAUSED) {
                 playerService.loadSong(null, false); // This stops and unloads.
            }
        }
    }


    /**
     * Helper method to load a song into PlayerService and LyricsService,
     * and request playback to start when ready.
     *
     * @param song The song to load and play. If null, requests player to unload current song.
     */
    private void loadAndPlaySong(Song song) {
        if (playerService == null) {
            System.err.println("MainController: Cannot load/play song: PlayerService is null.");
            if (lyricsService != null) lyricsService.loadLyricsForSong(null); // Clear lyrics
            updateControlsBasedOnStatus(MediaPlayer.Status.HALTED); // Reflect error state
            return;
        }

        if (song == null) {
            System.out.println("MainController: loadAndPlaySong called with null song. Requesting player to unload.");
            playerService.loadSong(null, false); // PlayerService handles setting its currentSong to null.
                                                 // Listeners on currentSongProperty will clear UI (lyrics, slider, etc.).
            return;
        }

        // Lyrics loading is now primarily driven by the PlayerService.currentSongProperty listener
        // once PlayerService successfully sets the new song.
        System.out.println("MainController: Requesting player to load and play: " + song.getTitle());
        boolean loadingInitiated = playerService.loadSong(song, true); // Request PlayerService to load and auto-play

        if (!loadingInitiated) {
            System.err.println("MainController: PlayerService failed to initiate loading for " + song.getTitle() + ".");
            if (lyricsService != null) { // Fallback: ensure lyrics are cleared if loading fails fast
                lyricsService.loadLyricsForSong(null);
            }
            // Show an alert for immediate loading failures.
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Playback Error");
            alert.setHeaderText("Could not load audio");
            alert.setContentText("Failed to load the audio file for:\n" +
                                 song.getTitle() + " - " + song.getArtist() +
                                 "\nPlease check file integrity and location.");
            alert.showAndWait();
            updateControlsBasedOnStatus(MediaPlayer.Status.HALTED); // Reflect error state in UI
        }
        // UI updates (Now Playing, slider, time, lyrics for new song) are driven by listeners
        // on PlayerService's properties (status, currentTime, totalDuration, currentSong).
    }

    /**
     * Formats time in milliseconds to mm:ss string.
     * @param millis Time in milliseconds.
     * @return Formatted string "mm:ss" or "0:00" if input is invalid/negative.
     */
    private String formatTime(long millis) {
        if (millis < 0 || Long.MAX_VALUE == millis) { // Check for invalid values like Duration.UNKNOWN
            return "0:00";
        }
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Overload for double, converting to long first. Handles NaN, Infinity.
     * @param millis Time in milliseconds as double.
     * @return Formatted string "mm:ss" or "0:00".
     */
    private String formatTime(double millis) {
        if (Double.isNaN(millis) || Double.isInfinite(millis) || millis < 0) {
            return "0:00";
        }
        return formatTime((long) millis);
    }
} // End of MainController class
