package controller;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import model.LyricLine;
import model.Song;
import service.LyricsService;
import service.PlayerService;
import service.QueueService;
import dao.SongDAO;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Controller class for the MainView.fxml layout.
 * Handles user interactions, updates the UI based on service states,
 * and coordinates actions between the UI and the backend services.
 * Refactored for clarity and consistency.
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
    @FXML private Label currentTimeLabel;
    @FXML private Slider playbackSlider;
    @FXML private Label totalDurationLabel;
    @FXML private Button playPauseButton;
    @FXML private Button stopButton;
    @FXML private Button skipButton;
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
    private final ListChangeListener<Song> queueChangeListener = change -> updateQueueDisplay();

    // --- Initialization ---

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("MainController initialized.");
        // Setup UI components that don't depend on injected services
        setupTableViewColumns();
        addSearchAndFilterListeners();
        addTableViewSelectionListener();
        setupPlaybackSliderListeners();
        // Removed stale TODO comments
    }

    /** Configures the TableView columns */
    private void setupTableViewColumns() {
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        artistColumn.setCellValueFactory(new PropertyValueFactory<>("artist"));
    }

    /** Adds listeners to search field and genre combo box */
    private void addSearchAndFilterListeners() {
        searchTextField.textProperty().addListener((obs, ov, nv) -> updateSongTableView());
        genreFilterComboBox.valueProperty().addListener((obs, ov, nv) -> updateSongTableView());
    }

    /** Adds a listener to the TableView's selection model */
    private void addTableViewSelectionListener() {
        TableView.TableViewSelectionModel<Song> selectionModel = songTableView.getSelectionModel();
        selectionModel.selectedItemProperty().addListener((observable, oldSelection, newSelection) -> {
            this.currentlySelectedSong = newSelection;
            boolean songIsSelected = (newSelection != null);
            System.out.println(songIsSelected ? "Song selected: " + newSelection : "Song selection cleared.");
            addToQueueButton.setDisable(!songIsSelected);
            // Update play button state if player is idle
            if (playerService == null || (playerService.getStatus() != MediaPlayer.Status.PLAYING && playerService.getStatus() != MediaPlayer.Status.PAUSED)) {
                playPauseButton.setDisable(!songIsSelected && (queueService == null || queueService.isEmpty()));
            }
        });
        addToQueueButton.setDisable(true); // Initially disable
    }

    /** Adds listeners to the playback slider for seeking */
    private void setupPlaybackSliderListeners() {
        playbackSlider.setOnMousePressed(event -> isUserSeeking = true);
        playbackSlider.setOnMouseReleased(event -> {
            if (playerService != null && isUserSeeking) {
                playerService.seek((long) playbackSlider.getValue());
            }
            isUserSeeking = false;
        });
        playbackSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isUserSeeking || playbackSlider.isValueChanging()) {
                currentTimeLabel.setText(formatTime(newVal.longValue()));
            }
        });
        playbackSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && playerService != null && !isUserSeeking) {
                playerService.seek((long) playbackSlider.getValue());
            }
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
        if (this.queueService != null) this.queueService.getQueue().removeListener(queueChangeListener);
        this.queueService = queueService;
        System.out.println("QueueService injected.");
        if (this.queueService != null) {
            this.queueService.getQueue().addListener(queueChangeListener);
            updateQueueDisplay(); // Initial update
        }
    }

    /**
     * Method called after all services are injected to set up bindings,
     * listeners, and load initial data.
     */
    public void initializeBindingsAndListeners() {
         System.out.println("Initializing service-dependent bindings, listeners, and data...");
         populateGenreFilter();
         updateSongTableView(); // Initial data load for table
         setupPlayerBindingsAndListeners();
         setupLyricsBindingsAndListeners();
    }

    /** Sets up bindings and listeners related to the PlayerService. */
    private void setupPlayerBindingsAndListeners() {
        if (playerService == null) return;

        // Slider Max & Total Duration Label
        playerService.totalDurationProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(() -> {
            double totalMillis = newVal.doubleValue();
            playbackSlider.setMax(totalMillis > 0 ? totalMillis : 0.0);
            totalDurationLabel.setText(formatTime(totalMillis));
        }));

        // Slider Value, Current Time Label & Lyrics Update Trigger
        playerService.currentTimeProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(() -> {
            if (!isUserSeeking && !playbackSlider.isValueChanging()) {
                playbackSlider.setValue(newVal.doubleValue());
            }
            currentTimeLabel.setText(formatTime(newVal.longValue()));
            if (lyricsService != null) {
                lyricsService.updateCurrentDisplayLines(newVal.longValue());
            }
        }));

        // Player Status -> Button Text/States
        playerService.statusProperty().addListener((obs, oldStatus, newStatus) -> Platform.runLater(() -> {
            updateControlsBasedOnStatus(newStatus);
        }));

        // Initialize control states based on initial (likely UNKNOWN) status
        updateControlsBasedOnStatus(playerService.getStatus());
    }

    /** Sets up bindings and listeners related to the LyricsService. */
    private void setupLyricsBindingsAndListeners() {
        if (lyricsService == null) return;

        lyricsService.displayLinesProperty().addListener((obs, oldLines, newLines) -> Platform.runLater(() -> {
            previousLyricLabel.setText(getLyricTextOrEmpty(newLines, 0));
            currentLyricLabel.setText(getLyricTextOrEmpty(newLines, 1));
            next1LyricLabel.setText(getLyricTextOrEmpty(newLines, 2));
            next2LyricLabel.setText(getLyricTextOrEmpty(newLines, 3));
        }));

        // Set initial empty state
        previousLyricLabel.setText("");
        currentLyricLabel.setText("");
        next1LyricLabel.setText("");
        next2LyricLabel.setText("");
    }

    /** Helper to update control states based on MediaPlayer status */
    private void updateControlsBasedOnStatus(MediaPlayer.Status status) {
        boolean playing = (status == MediaPlayer.Status.PLAYING);
        boolean paused = (status == MediaPlayer.Status.PAUSED);
        boolean stopped = (status == MediaPlayer.Status.STOPPED || status == MediaPlayer.Status.READY); // Treat READY like STOPPED for controls
        boolean haltedOrUnknown = (status == MediaPlayer.Status.HALTED || status == MediaPlayer.Status.UNKNOWN);
        boolean queueIsEmpty = (queueService == null || queueService.isEmpty());
        boolean songIsSelected = (currentlySelectedSong != null);

        playPauseButton.setText(playing ? "Pause" : "Play");
        playPauseButton.setDisable(haltedOrUnknown && !songIsSelected && queueIsEmpty); // Can always pause if playing, can play if something available or paused/stopped/ready

        stopButton.setDisable(!playing && !paused); // Can only stop if playing or paused

        skipButton.setDisable(queueIsEmpty); // Can only skip if queue has items

        // If stopped/halted, reset slider/time visually
        if (stopped || haltedOrUnknown) {
            if (!isUserSeeking && !playbackSlider.isValueChanging()) playbackSlider.setValue(0);
            currentTimeLabel.setText(formatTime(0));
        }
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
    private void populateGenreFilter() {
        Set<String> distinctGenres = SongDAO.getDistinctGenres();
        ObservableList<String> genreOptions = FXCollections.observableArrayList(ALL_GENRES);
        genreOptions.addAll(distinctGenres);
        genreFilterComboBox.setItems(genreOptions);
        genreFilterComboBox.setValue(ALL_GENRES);
    }
    private void updateSongTableView() {
        if (songTableView == null || searchTextField == null || genreFilterComboBox == null) return;
        String searchText = searchTextField.getText();
        String genreFilter = genreFilterComboBox.getValue();
        if (ALL_GENRES.equals(genreFilter)) genreFilter = null;
        List<Song> filteredSongs = SongDAO.findSongsByCriteria(searchText, genreFilter);
        songTableView.setItems(FXCollections.observableArrayList(filteredSongs));
        System.out.println("Updated song table view. Found " + filteredSongs.size());
    }

    // --- Queue Display Update ---
    private void updateQueueDisplay() {
        if (queueService == null || queueSong1Label == null) return;
        ObservableList<Song> currentQueue = queueService.getQueue();
        int queueSize = currentQueue.size();
        queueSong1Label.setText("1. " + (queueSize >= 1 ? formatSongForQueue(currentQueue.get(0)) : "-"));
        queueSong2Label.setText("2. " + (queueSize >= 2 ? formatSongForQueue(currentQueue.get(1)) : "-"));
        queueSong3Label.setText("3. " + (queueSize >= 3 ? formatSongForQueue(currentQueue.get(2)) : "-"));
        int remaining = Math.max(0, queueSize - 3);
        queueCountLabel.setText((remaining > 0) ? "(+" + remaining + " more)" : "(+0 more)");
        // Update control states that depend on queue emptiness
        updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
    }
    private String formatSongForQueue(Song song) {
        return (song != null) ? song.toString() : "-";
    }

    // --- FXML Action Handlers ---

    @FXML
    private void handlePlayPause() {
        System.out.println("Play/Pause clicked");
        if (playerService == null) return;
        MediaPlayer.Status status = playerService.getStatus();
        if (status == MediaPlayer.Status.PLAYING) {
            playerService.pause();
        } else { // Paused, Ready, Stopped, Unknown, Halted
            if (playerService.getCurrentSong() != null && status != MediaPlayer.Status.UNKNOWN && status != MediaPlayer.Status.HALTED) {
                playerService.play(); // Resume
            } else {
                playNextSong(); // Play next from queue or selection
            }
        }
    }

    @FXML
    private void handleStop() {
        System.out.println("Stop clicked");
        if (playerService != null) playerService.stop();
    }

    @FXML
    private void handleSkip() {
        System.out.println("Skip clicked");
        if (playerService != null) playerService.stop(); // Stop current first
        playNextSong(); // Attempt to play next from queue
    }

    @FXML
    private void handleAddToQueue() {
        if (currentlySelectedSong != null && queueService != null) {
            System.out.println("Adding to queue: " + currentlySelectedSong);
            queueService.addSong(currentlySelectedSong);
        } else {
            System.out.println("Add to Queue: No song selected or queue service unavailable.");
        }
    }

    @FXML
    private void handleFullscreenToggle() {
        System.out.println("Fullscreen toggle: " + fullscreenToggleButton.isSelected());
        // TODO: Implement fullscreen logic using Stage reference
    }

    @FXML
    private void handleThemeToggle() {
        System.out.println("Theme toggle: " + themeToggleButton.isSelected());
        // TODO: Implement theme switching logic (CSS manipulation)
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

    // Overload for double when needed (e.g., slider value)
    private String formatTime(double millis) {
        return formatTime((long) millis);
    }

}
