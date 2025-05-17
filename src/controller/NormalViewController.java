package controller;

// --- JavaFX Imports ---
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;

// --- Model Imports ---
import model.Song;

// --- Service Imports ---
import service.LyricsService;
import service.PlayerService;
import service.QueueService;

// --- DAO Import ---
import dao.SongDAO;

// --- Util Imports ---
import java.net.URL;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Controller for {@code NormalView.fxml}, managing the standard, windowed user interface of the TuneUp application.
 * This view includes song library browsing, search and filtering, queue management, lyric display,
 * and standard playback controls. It interacts with {@link MainController} for global actions
 * and service coordination.
 * Implements {@link MainController.SubController} for standardized interaction with the main application orchestrator.
 * SRS References: This controller supports core functionalities outlined in SRS 1.2 (UI, Playback, Lyrics, Queue, Search).
 */
public class NormalViewController implements Initializable, MainController.SubController {

    // --- FXML Injected Fields for Normal View ---
    @FXML private TextField searchTextField; // For song library text-based search (SRS 1.2).
    @FXML private ComboBox<String> genreFilterComboBox; // For filtering song library by genre (SRS 1.2).
    @FXML private TableView<Song> songTableView; // Displays the song library (SRS 1.2).
    @FXML private TableColumn<Song, String> titleColumn; // Displays song titles in the library table.
    @FXML private TableColumn<Song, String> artistColumn; // Displays song artists in the library table.
    @FXML private Button addToQueueButton; // Adds selected song from library to the playback queue (SRS 1.2).

    @FXML private TitledPane queueTitledPane; // Collapsible pane displaying the song queue.
    @FXML private ListView<String> queueListView; // Displays the list of songs in the queue (SRS 1.2).

    // lyricsContainer might be used for styling or dynamic layout changes if needed.
    @FXML private VBox lyricsContainer; // Container for lyric labels.
    @FXML private Label previousLyricLabel; // Displays the preceding lyric line (SRS 1.2).
    @FXML private Label currentLyricLabel; // Displays the current, active lyric line (SRS 1.2).
    @FXML private Label next1LyricLabel; // Displays the upcoming lyric line (SRS 1.2).
    @FXML private Label next2LyricLabel; // Displays the second upcoming lyric line (SRS 1.2).

    @FXML private Label nowPlayingTitleLabel; // Displays title of the currently playing song (SRS 1.2, 2.2).
    @FXML private Label nowPlayingArtistLabel; // Displays artist of the currently playing song (SRS 1.2, 2.2).
    @FXML private Label currentTimeLabel; // Shows current playback time of the song (SRS 1.2, 2.2).
    @FXML private Slider playbackSlider; // Allows user to seek within the current song (SRS 1.2, 2.2).
    @FXML private Label totalDurationLabel; // Shows total duration of the current song (SRS 1.2, 2.2).

    @FXML private Button playPauseButton; // Toggles play/pause for the current song (SRS 1.2, 2.2).
    @FXML private Button stopButton; // Stops playback of the current song (SRS 1.2, 2.2).
    @FXML private Button skipButton; // Skips to the next song in the queue (SRS 1.2, 2.2).
    @FXML private Button fullscreenToggleButton; // Toggles fullscreen mode (SRS 1.2).
    @FXML private Button themeButton; // Toggles between light and dark themes (SRS 1.2).

    @FXML private Button increaseOffsetButton; // Increases lyric display offset (SRS 1.2).
    @FXML private Label lyricOffsetLabel; // Displays the current lyric offset (SRS 1.2).
    @FXML private Button decreaseOffsetButton; // Decreases lyric display offset (SRS 1.2).

    // --- Service Dependencies & Main Controller Reference ---
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;
    private MainController mainController;
    private Stage primaryStage; // Used by MainController for fullscreen toggle, not directly by this controller for that.

    // --- State ---
    private Song currentlySelectedSong = null; // Tracks the song selected in the songTableView.
    private boolean isUserSeeking = false; // Tracks if user is dragging the playbackSlider.
    // currentSongLiveOffsetMs is managed by MainController; this controller displays it.

    // --- Constants ---
    private static final String ALL_GENRES = "All Genres"; // Constant for the "All Genres" filter option.

    // Listener for queue changes to update its display (SRS 1.2 Queue Display).
    private final ListChangeListener<Song> queueChangeListener = (@SuppressWarnings("unused") var change) ->
            Platform.runLater(this::updateQueueDisplay);

    /**
     * Provides access to the label displaying the current lyric offset.
     * This allows the {@link MainController} to update it directly, for example, when resetting the offset.
     * @return The {@link Label} used for displaying the lyric offset, or {@code null} if not initialized.
     */
    public Label getLyricOffsetLabel() {
        return lyricOffsetLabel;
    }

    /**
     * Initializes the controller after its root element has been completely processed.
     * Sets up initial UI states and listeners not dependent on injected services.
     *
     * @param location The location used to resolve relative paths for the root object, or null if not known.
     * @param resources The resources used to localize the root object, or null if not known.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("NormalViewController initialized.");
        // Initial setup for UI elements not dependent on injected services.
        setupTableViewColumns();
        addSearchAndFilterListeners();
        addTableViewSelectionListener();

        // Set initial states for controls.
        if (playbackSlider != null) playbackSlider.setDisable(true);            // Disable until a song is loaded.
        if (stopButton != null) stopButton.setText("Stop All");
        if (lyricOffsetLabel != null) lyricOffsetLabel.setText("0 ms");   // Default offset display.
        if (addToQueueButton != null) addToQueueButton.setDisable(true);        // Disable until a song is selected.
    }

    // --- Service and Controller Injection Methods ---

    /** Sets the {@link PlayerService} instance. @param playerService The player service. */
    public void setPlayerService(PlayerService playerService) { this.playerService = playerService; }

    /** Sets the {@link LyricsService} instance. @param lyricsService The lyrics service. */
    public void setLyricsService(LyricsService lyricsService) { this.lyricsService = lyricsService; }

    /** 
     * Sets the {@link QueueService} instance and manages listeners for queue changes.
     * @param queueService The queue service. 
     */
    public void setQueueService(QueueService queueService) {
        // Remove listener from old service instance if it exists.
        if (this.queueService != null && this.queueService.getQueue() != null) {
            this.queueService.getQueue().removeListener(queueChangeListener);
        }
        this.queueService = queueService;
        // Add listener to new service instance if it exists.
        if (this.queueService != null && this.queueService.getQueue() != null) {
            this.queueService.getQueue().addListener(queueChangeListener);
        }
    }

    /** Sets the {@link MainController} instance. @param mainController The main controller. */
    public void setMainController(MainController mainController) { this.mainController = mainController; }

    /** Sets the primary {@link Stage}. @param stage The primary stage. */
    public void setPrimaryStage(Stage stage) { this.primaryStage = stage; }

    /**
     * Called by {@link MainController} after all services and references are injected.
     * Sets up listeners for service property changes and populates UI elements like the genre filter
     * and initial song list. This method finalizes the setup of the normal view.
     * SRS References: Initializes components supporting SRS 1.2 functionalities.
     */
    public void initializeBindingsAndListeners() {
        System.out.println("NormalViewController: Initializing service-dependent bindings and listeners...");
        if (playerService == null || lyricsService == null || queueService == null || mainController == null) {
            System.err.println("NormalViewController ERROR: Critical dependencies (PlayerService, LyricsService, QueueService, or MainController) are null. UI might not function correctly.");
            return;
        }
        populateGenreFilter(); // SRS 1.2: Genre filter
        updateSongTableView(); // SRS 1.2: Load initial song library

        setupPlayerServiceListeners();  // Listen to player state changes (SRS 1.2, 2.2)
        setupLyricsServiceListeners();  // Listen to lyric display changes (SRS 1.2)
        setupPlaybackSliderListeners(); // Listen to playback slider interactions (SRS 1.2, 2.2)

        // Perform an initial UI refresh to set correct states based on current service data.
        updateUIDisplay();
    }
    
    /**
     * Central method to refresh all UI elements in the normal view.
     * This is called when the view becomes active, or when application state changes
     * (e.g., song change, playback status, time update, lyrics update, queue change)
     * require the UI to reflect the new state. Ensures UI updates are on the JavaFX thread.
     * SRS References: Supports SRS 1.2 (UI display), SRS 2.2 (Playback info).
     */
    @Override
    public void updateUIDisplay() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateUIDisplay);
            return;
        }
        System.out.println("NormalViewController: Updating UI display.");
        if (playerService == null || lyricsService == null || queueService == null || mainController == null) {
            // Log error or handle missing dependencies if necessary, then return.
            System.err.println("NormalViewController: updateUIDisplay - Missing critical service or controller reference.");
            return;
        }

        // Update playback controls based on current player status.
        updateControlsBasedOnStatus(playerService.getStatus());
        // Update "Now Playing" song title and artist.
        updateNowPlayingDisplay(playerService.getCurrentSong());
        // Refresh the song queue display.
        updateQueueDisplay();
        // Update the lyric offset display from MainController's central value.
        updateLyricOffsetDisplay(mainController.getCurrentSongLiveOffsetMs());

        // Refresh lyrics based on current playback time and the centrally managed offset.
        lyricsService.updateCurrentDisplayLines(playerService.getCurrentTimeMillis(), mainController.getCurrentSongLiveOffsetMs());

        // Update playback slider position and time labels.
        Song currentSong = playerService.getCurrentSong();
        if (currentSong != null) {
            if (playbackSlider != null) {
                playbackSlider.setMax(playerService.getTotalDurationMillis());
                if (!isUserSeeking) { // Only update slider if user isn't dragging it.
                    playbackSlider.setValue(playerService.getCurrentTimeMillis());
                }
            }
            if (currentTimeLabel != null) currentTimeLabel.setText(mainController.formatTime(playerService.getCurrentTimeMillis()));
            if (totalDurationLabel != null) totalDurationLabel.setText(mainController.formatTime(playerService.getTotalDurationMillis()));
        } else {
            // Reset time displays and slider if no song is loaded.
            if (playbackSlider != null) { playbackSlider.setMax(0); playbackSlider.setValue(0); }
            if (currentTimeLabel != null) currentTimeLabel.setText(mainController.formatTime(0));
            if (totalDurationLabel != null) totalDurationLabel.setText(mainController.formatTime(0));
        }
    }

    // --- UI Setup Methods ---

    /** Sets up the columns for the song library TableView (Title, Artist). */
    private void setupTableViewColumns() {
        if(titleColumn != null) titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        if(artistColumn != null) artistColumn.setCellValueFactory(new PropertyValueFactory<>("artist"));
    }

    /** Adds listeners to the search text field and genre filter ComboBox to update the song table view. */
    private void addSearchAndFilterListeners() {
        if(searchTextField != null) {
            searchTextField.textProperty().addListener((@SuppressWarnings("unused") var _obs, @SuppressWarnings("unused") var _oldText, @SuppressWarnings("unused") var _newText) -> updateSongTableView());
        }
        if(genreFilterComboBox != null) {
            genreFilterComboBox.valueProperty().addListener((@SuppressWarnings("unused") var _obs, @SuppressWarnings("unused") var _oldGenre, @SuppressWarnings("unused") var _newGenre) -> updateSongTableView());
        }
    }

    /** Adds a listener to the song TableView to track the currently selected song and enable/disable relevant controls. */
    private void addTableViewSelectionListener() {
        if(songTableView != null){
            TableView.TableViewSelectionModel<Song> selectionModel = songTableView.getSelectionModel();
            selectionModel.setSelectionMode(SelectionMode.SINGLE); // Allow only single song selection.
            selectionModel.selectedItemProperty().addListener((@SuppressWarnings("unused") var _obs, @SuppressWarnings("unused") var _oldSelection, var newSelection) -> {
                this.currentlySelectedSong = newSelection;
                // Enable "Add to Queue" button only if a song is selected.
                if(addToQueueButton != null) addToQueueButton.setDisable(newSelection == null);
                
                // If player is not active, update controls to reflect that a library song might be playable.
                if(playerService != null && 
                   (playerService.getStatus() != MediaPlayer.Status.PLAYING && 
                    playerService.getStatus() != MediaPlayer.Status.PAUSED)) {
                    updateControlsBasedOnStatus(playerService.getStatus());
                }
            });
        }
    }

    /** Sets up listeners for the playback slider to handle user seeking actions. */
    private void setupPlaybackSliderListeners() {
        if (playbackSlider == null || playerService == null || mainController == null) return;

        playbackSlider.setOnMousePressed((@SuppressWarnings("unused") var event) -> {
            if (playerService.getCurrentSong() != null) {
                isUserSeeking = true;
            }
        });

        playbackSlider.setOnMouseDragged((@SuppressWarnings("unused") var event) -> {
            if (isUserSeeking && playerService.getCurrentSong() != null && currentTimeLabel != null && mainController != null) {
                currentTimeLabel.setText(mainController.formatTime((long) playbackSlider.getValue()));
            }
        });

        playbackSlider.setOnMouseReleased((@SuppressWarnings("unused") var event) -> {
            if (isUserSeeking && playerService != null && playerService.getCurrentSong() != null) {
                long seekMillis = (long) playbackSlider.getValue();
                System.out.println("NormalView: User released slider, seeking to: " + seekMillis + "ms via playerService.seek()");
                playerService.seek(seekMillis);
            }
            isUserSeeking = false; // Reset the flag after user finishes seeking
        });
    }

    // --- Service Listener Setup ---

    /** Sets up listeners for {@link PlayerService} properties (duration, time, status, current song). */
    private void setupPlayerServiceListeners() {
        if (playerService == null) return;

        // Listener for total duration changes.
        playerService.totalDurationProperty().addListener((@SuppressWarnings("unused") var _obs, @SuppressWarnings("unused") var _oldDuration, var newDuration) -> Platform.runLater(() -> {
            double totalMillis = newDuration.doubleValue();
            if (playbackSlider != null) playbackSlider.setMax(totalMillis > 0 ? totalMillis : 0.0);
            if (totalDurationLabel != null && mainController != null) {
                totalDurationLabel.setText(mainController.formatTime(newDuration.longValue()));
            }
        }));

        // Listener for current time changes.
        playerService.currentTimeProperty().addListener((@SuppressWarnings("unused") var _obs, @SuppressWarnings("unused") var _oldTime, var newTime) -> Platform.runLater(() -> {
            long newTimeMillis = newTime.longValue();
            if (playbackSlider != null && !isUserSeeking) playbackSlider.setValue(newTimeMillis);
            if (currentTimeLabel != null && mainController != null) currentTimeLabel.setText(mainController.formatTime(newTimeMillis));
            // Update lyrics based on new time and central offset.
            if (lyricsService != null && mainController != null) {
                lyricsService.updateCurrentDisplayLines(newTimeMillis, mainController.getCurrentSongLiveOffsetMs());
            }
        }));

        // Listener for playback status changes (Play, Pause, Stop, etc.).
        playerService.statusProperty().addListener((@SuppressWarnings("unused") var _obs, var oldStatus, var newStatus) -> Platform.runLater(() -> {
            updateControlsBasedOnStatus(newStatus);
            // SRS 1.2: Auto-play next from queue if a song finishes (transitions from PLAYING to STOPPED/READY).
            if (oldStatus == MediaPlayer.Status.PLAYING && 
                (newStatus == MediaPlayer.Status.STOPPED || newStatus == MediaPlayer.Status.READY)) {
            }
        }));

        // Listener for current song changes.
        playerService.currentSongProperty().addListener((@SuppressWarnings("unused") var _obs, @SuppressWarnings("unused") var _oldSong, var newSong) -> Platform.runLater(() -> {
            updateNowPlayingDisplay(newSong);
            if (newSong != null) {
                if (lyricsService != null && mainController != null) {
                    lyricsService.loadLyricsForSong(newSong); // Loads lyrics and initial offset.
                    mainController.setCurrentSongLiveOffsetMs((int) lyricsService.getInitialLoadedOffsetMs());
                    updateLyricOffsetDisplay(mainController.getCurrentSongLiveOffsetMs()); // Update local label.
                    // Immediately update lyrics display with the new song's initial offset.
                    lyricsService.updateCurrentDisplayLines(playerService.getCurrentTimeMillis(), mainController.getCurrentSongLiveOffsetMs());
                }
            } else {
                resetUIForNoActiveSong(); // Clear UI elements if no song is active.
            }
        }));
        // Bind playback slider's disable state to whether a song is loaded.
        if (playbackSlider != null) {
            playbackSlider.disableProperty().bind(playerService.currentSongProperty().isNull());
        }
    }

    /** Sets up listeners for {@link LyricsService} display lines property. */
    private void setupLyricsServiceListeners() {
        if (lyricsService == null) return;

        lyricsService.displayLinesProperty().addListener((@SuppressWarnings("unused") var _obs, @SuppressWarnings("unused") var _oldLines, var newLines) -> Platform.runLater(() -> {
            if(previousLyricLabel != null && mainController != null) previousLyricLabel.setText(mainController.getLyricTextOrEmpty(newLines, 0));
            if(currentLyricLabel != null && mainController != null) currentLyricLabel.setText(mainController.getLyricTextOrEmpty(newLines, 1));
            if(next1LyricLabel != null && mainController != null) next1LyricLabel.setText(mainController.getLyricTextOrEmpty(newLines, 2));
            if(next2LyricLabel != null && mainController != null) next2LyricLabel.setText(mainController.getLyricTextOrEmpty(newLines, 3));
        }));
    }
    
    // --- UI Update Methods ---

    /** Resets UI elements to a default state when no song is active/loaded. */
    private void resetUIForNoActiveSong() {
        if (playbackSlider != null) { playbackSlider.setValue(0); playbackSlider.setMax(0); }
        if (currentTimeLabel != null && mainController != null) currentTimeLabel.setText(mainController.formatTime(0));
        if (totalDurationLabel != null && mainController != null) totalDurationLabel.setText(mainController.formatTime(0));
        if (lyricsService != null) lyricsService.clearLyrics(); // Clear lyric display.
        if (mainController != null) mainController.setCurrentSongLiveOffsetMs(0); // Reset central offset.
        updateLyricOffsetDisplay(0); // Update local offset label.
        updateNowPlayingDisplay(null); // Clear now playing info.
    }

    /**
     * Updates the lyric offset label in this view to display the current live lyric timing offset.
     * @param offset The current live offset in milliseconds, obtained from {@link MainController}.
     */
    public void updateLyricOffsetDisplay(int offset) {
        if (lyricOffsetLabel != null) {
            lyricOffsetLabel.setText(String.format("%d ms", offset));
        }
    }

    /** Populates the genre filter ComboBox with distinct genres from the song library. */
    private void populateGenreFilter() {
        if(genreFilterComboBox == null) return;
        Set<String> genres = SongDAO.getDistinctGenres();
        ObservableList<String> options = FXCollections.observableArrayList();
        options.add(ALL_GENRES); // Add "All Genres" option first.
        options.addAll(genres); // Add all unique genres from DAO.
        genreFilterComboBox.setItems(options);
        genreFilterComboBox.setValue(ALL_GENRES); // Default to showing all genres.
    }

    /** Updates the song TableView based on current search text and genre filter. */
    private void updateSongTableView() {
        if(songTableView == null || searchTextField == null || genreFilterComboBox == null) return;
        String searchTerm = searchTextField.getText();
        String selectedGenre = genreFilterComboBox.getValue();
        if(ALL_GENRES.equals(selectedGenre)) {
            selectedGenre = null; // Null genre means no genre filter.
        }
        // Fetch songs from DAO based on criteria and update table.
        songTableView.setItems(FXCollections.observableArrayList(SongDAO.findSongsByCriteria(searchTerm, selectedGenre)));
    }
    
    /** 
     * Updates the state of playback controls (Play/Pause, Stop, Skip) and offset buttons
     * based on the current {@link MediaPlayer.Status} and other relevant states (queue, selection).
     * @param status The current status of the media player.
     */
    private void updateControlsBasedOnStatus(MediaPlayer.Status status) {
        if (playPauseButton == null || mainController == null || playerService == null || queueService == null) return;
        if (status == null) status = MediaPlayer.Status.UNKNOWN; // Default to UNKNOWN if status is null.

        boolean isPlaying = (status == MediaPlayer.Status.PLAYING);
        boolean isPaused = (status == MediaPlayer.Status.PAUSED);
        // Consider STOPPED, READY, or even initial UNKNOWN/HALTED states as potentially able to start playback.
        boolean isEffectivelyStopped = (status == MediaPlayer.Status.STOPPED || 
                                      status == MediaPlayer.Status.READY || 
                                      status == MediaPlayer.Status.UNKNOWN || 
                                      status == MediaPlayer.Status.HALTED);

        boolean songIsLoadedInPlayer = (playerService.getCurrentSong() != null);
        boolean songIsSelectedInLibrary = (this.currentlySelectedSong != null);
        boolean queueCanProvideSong = !queueService.isEmpty();

        // Determine if playback can be initiated.
        boolean canStartPlayback = isPaused || // Can resume if paused.
                                   (isEffectivelyStopped && 
                                    (songIsLoadedInPlayer || songIsSelectedInLibrary || queueCanProvideSong)); // Can start if stopped and a song is available.

        playPauseButton.setText(isPlaying ? "Pause" : "Play");
        playPauseButton.setDisable(!(isPlaying || canStartPlayback)); // Disable if not playing AND cannot start playback.
        
        // Stop and Skip buttons are enabled if a song is loaded OR if the queue can provide one.
        boolean canStopOrSkip = songIsLoadedInPlayer || queueCanProvideSong;
        if(stopButton != null) stopButton.setDisable(!canStopOrSkip);
        if(skipButton != null) skipButton.setDisable(!canStopOrSkip);

        // Reset slider and time if player is effectively stopped and user is not seeking.
        if (isEffectivelyStopped && !isUserSeeking && playbackSlider != null && !playbackSlider.isValueChanging()) {
            playbackSlider.setValue(0);
            if (currentTimeLabel != null) currentTimeLabel.setText(mainController.formatTime(0));
            // Only reset total duration if no song is loaded at all (e.g., initial state or after stop clears song).
            if (!songIsLoadedInPlayer && totalDurationLabel != null) totalDurationLabel.setText(mainController.formatTime(0));
        }

        // Enable offset adjustment buttons only if a song is currently loaded in the player.
        if (increaseOffsetButton != null) increaseOffsetButton.setDisable(!songIsLoadedInPlayer);
        if (decreaseOffsetButton != null) decreaseOffsetButton.setDisable(!songIsLoadedInPlayer);
    }

    /** Updates the ListView displaying the current song queue. (SRS 1.2 Queue Display) */
    private void updateQueueDisplay() {
        if (queueService == null || queueListView == null || queueTitledPane == null || mainController == null) return;

        ObservableList<Song> currentQueue = queueService.getQueue();
        if (currentQueue.isEmpty()) {
            queueListView.setItems(FXCollections.observableArrayList("Queue is empty."));
            queueTitledPane.setExpanded(false);
        } else {
            ObservableList<String> displayableQueue = FXCollections.observableArrayList();
            for (int i = 0; i < currentQueue.size(); i++) {
                Song song = currentQueue.get(i);
                if (song != null) {
                    displayableQueue.add((i + 1) + ". " + mainController.formatSongForQueue(song));
                } else {
                    displayableQueue.add((i + 1) + ". [Invalid Song Data]"); // Placeholder for null songs.
                }
            }
            queueListView.setItems(displayableQueue);
            queueTitledPane.setExpanded(true);
        }
    }

    /** Updates the "Now Playing" labels with the current song's title and artist. (SRS 1.2, 2.2) */
    private void updateNowPlayingDisplay(Song song) {
        if(nowPlayingTitleLabel == null || nowPlayingArtistLabel == null) return;
        if(song != null){
            nowPlayingTitleLabel.setText(song.getTitle() != null ? song.getTitle() : "Unknown Title");
            nowPlayingArtistLabel.setText(song.getArtist() != null ? song.getArtist() : "Unknown Artist");
        } else {
            nowPlayingTitleLabel.setText("-");
            nowPlayingArtistLabel.setText("-");
        }
    }

    // --- FXML Action Handlers ---

    /** Handles the play/pause action. Delegates to {@link MainController}. (SRS 1.2, 2.2) */
    @FXML private void handlePlayPause() { if (mainController != null) mainController.handlePlayPause(); }

    /** Handles the stop action. Delegates to {@link MainController}. (SRS 1.2, 2.2) */
    @FXML private void handleStop() { if (mainController != null) mainController.handleStop(); }

    /** Handles the skip action. Delegates to {@link MainController}. (SRS 1.2, 2.2) */
    @FXML private void handleSkip() { if (mainController != null) mainController.handleSkip(); }

    /** Handles adding the selected song from the library to the queue. (SRS 1.2) */
    @FXML private void handleAddToQueue() {
        if(currentlySelectedSong != null && queueService != null){
            queueService.addSong(currentlySelectedSong);
            if(songTableView != null) songTableView.getSelectionModel().clearSelection();
        } else {
             System.out.println("NormalViewController: Add to Queue - No song selected or QueueService is unavailable.");
        }
    }
    
    /** Handles theme toggle action. Delegates to {@link MainController}. (SRS 1.2) */
    @FXML private void handleThemeToggle() {
        if (mainController != null) {
            mainController.cycleTheme();
        }
    }

    /** Handles fullscreen toggle action. Delegates to {@link MainController}. (SRS 1.2) */
    @FXML private void handleFullscreenToggle() {
        if (mainController != null && primaryStage != null) {
            mainController.setAppFullScreen(!primaryStage.isFullScreen());
        }
    }

    /** Handles increasing lyric offset. Delegates to {@link MainController}. (SRS 1.2) */
    @FXML private void handleIncreaseOffset(ActionEvent event) { // event parameter can be kept or removed if not used.
        if (mainController != null) mainController.adjustLyricOffset(MainController.LYRIC_OFFSET_ADJUSTMENT_STEP); 
    }

    /** Handles decreasing lyric offset. Delegates to {@link MainController}. (SRS 1.2) */
    @FXML private void handleDecreaseOffset(ActionEvent event) { // event parameter can be kept or removed if not used.
        if (mainController != null) mainController.adjustLyricOffset(-MainController.LYRIC_OFFSET_ADJUSTMENT_STEP); 
    }
    
    // --- SubController Interface Implementation ---

    /** 
     * Returns the theme toggle button for this view.
     * Used by {@link MainController} to synchronize theme button states across views.
     * @return The {@link Button} used for toggling themes.
     */
    @Override
    public Button getThemeButton() {
        return themeButton;
    }

    /**
     * Returns the song currently selected in the library view (songTableView).
     * This method is part of the {@link MainController.SubController} interface.
     *
     * @return The {@link Song} currently selected in the TableView, or null if no song is selected.
     */
    @Override
    public Song getSelectedSongFromLibrary() {
        return this.currentlySelectedSong;
    }
    
    /**
     * Legacy or alternative getter for the currently selected song in the library.
     * {@link #getSelectedSongFromLibrary()} is preferred for SubController interface consistency.
     * @return The {@link Song} currently selected in the TableView.
     * @deprecated Prefer {@link #getSelectedSongFromLibrary()} for SubController interface compliance.
     */
    @Deprecated
    public Song getCurrentlySelectedSong() {
        return this.currentlySelectedSong;
    }
}