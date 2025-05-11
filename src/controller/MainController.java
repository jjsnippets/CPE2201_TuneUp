package controller;

// --- JavaFX Imports ---
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*; // Import all controls for convenience
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane; // For root panes
import javafx.scene.layout.StackPane;  // If StackPane is root in FXML
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;

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
import util.LrcWriter; // For saving offset
import java.io.IOException; // For LrcWriter exception
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Controller class for the MainView.fxml layout.
 * Handles user interactions, updates the UI based on service states,
 * and coordinates actions between the UI and the backend services.
 * Manages normal and fullscreen views, lyric timing adjustments, and playback controls.
 */
public class MainController implements Initializable {

    // --- FXML Injected Fields for Normal View ---
    @FXML private BorderPane normalViewRootPane;

    @FXML private TextField searchTextField;
    @FXML private ComboBox<String> genreFilterComboBox;
    @FXML private TableView<Song> songTableView;
    @FXML private TableColumn<Song, String> titleColumn;
    @FXML private TableColumn<Song, String> artistColumn;
    @FXML private Button addToQueueButton;

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

    @FXML private Label nowPlayingTitleLabel;
    @FXML private Label nowPlayingArtistLabel;
    @FXML private Label currentTimeLabel;
    @FXML private Slider playbackSlider;
    @FXML private Label totalDurationLabel;

    @FXML private Button playPauseButton;
    @FXML private Button stopButton;
    @FXML private Button skipButton;
    @FXML private ToggleButton fullscreenToggleButton; // In normal view
    @FXML private ToggleButton themeToggleButton;

    @FXML private Button increaseOffsetButton;
    @FXML private Label lyricOffsetLabel;
    @FXML private Button decreaseOffsetButton;

    // --- FXML Injected Fields for Fullscreen View ---
    @FXML private BorderPane fullscreenViewRootPane;
    @FXML private Label fullscreenNextSongLabel;
    @FXML private Label fullscreenQueueCountLabel;
    @FXML private Label fullscreenPreviousLyricLabel;
    @FXML private Label fullscreenCurrentLyricLabel;
    @FXML private Label fullscreenNext1LyricLabel;
    @FXML private Label fullscreenNext2LyricLabel;
    @FXML private Label fullscreenCurrentTimeLabel;
    @FXML private Slider fullscreenPlaybackSlider;
    @FXML private Label fullscreenTotalDurationLabel;
    @FXML private Button fullscreenPlayPauseButton;
    @FXML private Button fullscreenSkipButton;
    @FXML private ToggleButton fullscreenExitButton;


    // --- Service Dependencies ---
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;
    private Stage primaryStage;

    // --- State ---
    private Song currentlySelectedSong = null;
    private boolean isUserSeeking = false; // For normal view slider
    private boolean isUserSeekingFullscreen = false; // For fullscreen view slider
    private int currentSongLiveOffsetMs = 0; // Live offset for the current song (from [offset:...] in LRC, user-adjustable)
    private static final int LYRIC_OFFSET_ADJUSTMENT_STEP = 100; // Milliseconds

    // --- Constants ---
    private static final String ALL_GENRES = "All Genres";

    // Listener for queue changes
    private final ListChangeListener<Song> queueChangeListener = change ->
            Platform.runLater(() -> {
                updateQueueDisplay(); // Normal view
                if (primaryStage != null && primaryStage.isFullScreen()) {
                    updateFullscreenUIDisplay(); // Update fullscreen queue info
                }
            });

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("MainController initialized.");

        // Normal view initializations
        setupTableViewColumns();
        addSearchAndFilterListeners();
        addTableViewSelectionListener();
        setupPlaybackSliderListeners();
        updateNowPlayingDisplay(null);
        playbackSlider.setDisable(true);
        if (stopButton != null) stopButton.setText("Stop All");
        if (lyricOffsetLabel != null) lyricOffsetLabel.setText("0 ms");

        // Fullscreen view initializations
        setupFullscreenPlaybackSliderListeners();
        if (fullscreenViewRootPane != null) fullscreenViewRootPane.setVisible(false); // Ensure hidden initially
    }

    /**
     * Injects the primary stage and sets up listener for fullscreen changes.
     * @param stage The primary application stage.
     */
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        if (this.primaryStage != null) {
            this.primaryStage.fullScreenProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) { // Entered fullscreen
                    if(normalViewRootPane != null) normalViewRootPane.setVisible(false);
                    if(fullscreenViewRootPane != null) fullscreenViewRootPane.setVisible(true);
                    if(fullscreenToggleButton != null) fullscreenToggleButton.setSelected(true);
                    if (fullscreenExitButton != null) fullscreenExitButton.setSelected(true);
                    updateFullscreenUIDisplay();
                } else { // Exited fullscreen
                    if(fullscreenViewRootPane != null) fullscreenViewRootPane.setVisible(false);
                    if(normalViewRootPane != null) normalViewRootPane.setVisible(true);
                    if(fullscreenToggleButton != null) fullscreenToggleButton.setSelected(false);
                    if (fullscreenExitButton != null) fullscreenExitButton.setSelected(false);
                }
            });
        }
    }

    // --- Service Injection Methods ---
    public void setPlayerService(PlayerService playerService) { this.playerService = playerService; }
    public void setLyricsService(LyricsService lyricsService) { this.lyricsService = lyricsService; }
    public void setQueueService(QueueService queueService) {
        if (this.queueService != null) {
            this.queueService.getQueue().removeListener(queueChangeListener);
        }
        this.queueService = queueService;
        if (this.queueService != null) {
            this.queueService.getQueue().addListener(queueChangeListener);
            updateQueueDisplay();
        }
    }

    /**
     * Called after all services are injected to set up bindings and load initial data.
     */
    public void initializeBindingsAndListeners() {
        if (playerService == null || lyricsService == null || queueService == null) {
            System.err.println("ERROR: MainController cannot initialize bindings - services missing!");
            return;
        }
        populateGenreFilter();
        updateSongTableView();
        setupPlayerBindingsAndListeners();
        setupLyricsBindingsAndListeners();

        updateControlsBasedOnStatus(playerService.getStatus());
        updateNowPlayingDisplay(playerService.getCurrentSong());
        if (primaryStage != null && primaryStage.isFullScreen()) { // Initial update if starts in fullscreen
            updateFullscreenUIDisplay();
        }
    }

    // --- UI Setup Methods (Normal View) ---
    private void setupTableViewColumns() {
        if(titleColumn != null) titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        if(artistColumn != null) artistColumn.setCellValueFactory(new PropertyValueFactory<>("artist"));
    }

    private void addSearchAndFilterListeners() {
        if(searchTextField != null) searchTextField.textProperty().addListener((_obs, _ov, _nv) -> updateSongTableView());
        if(genreFilterComboBox != null) genreFilterComboBox.valueProperty().addListener((_obs, _ov, _nv) -> updateSongTableView());
    }

    private void addTableViewSelectionListener() {
        if(songTableView != null){
            TableView.TableViewSelectionModel<Song> sm = songTableView.getSelectionModel();
            sm.setSelectionMode(SelectionMode.SINGLE);
            sm.selectedItemProperty().addListener((obs, ov, nv) -> {
                this.currentlySelectedSong = nv;
                if(addToQueueButton != null) addToQueueButton.setDisable(nv == null);
                if(playerService == null || (playerService.getStatus() != MediaPlayer.Status.PLAYING && playerService.getStatus() != MediaPlayer.Status.PAUSED)) {
                    updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
                }
            });
            if(addToQueueButton != null) addToQueueButton.setDisable(true);
        }
    }

    private void setupPlaybackSliderListeners() {
        if(playbackSlider != null){
            playbackSlider.setOnMousePressed(e -> {
                if(playerService != null && playerService.getCurrentSong() != null) isUserSeeking = true;
                else e.consume();
            });
            playbackSlider.setOnMouseDragged(e -> {
                if(isUserSeeking && currentTimeLabel != null) currentTimeLabel.setText(formatTime(playbackSlider.getValue()));
            });
            playbackSlider.setOnMouseReleased(e -> {
                if(isUserSeeking && playerService != null && playerService.getCurrentSong() != null) {
                    playerService.seek((long)playbackSlider.getValue());
                }
                isUserSeeking = false;
            });
        }
    }

    // --- UI Setup Methods (Fullscreen View) ---
    private void setupFullscreenPlaybackSliderListeners() {
        if (fullscreenPlaybackSlider == null) return;
        fullscreenPlaybackSlider.setOnMousePressed(event -> {
            if (playerService != null && playerService.getCurrentSong() != null) {
                isUserSeekingFullscreen = true;
            } else {
                event.consume();
            }
        });
        fullscreenPlaybackSlider.setOnMouseDragged(_event -> {
            if (isUserSeekingFullscreen && fullscreenCurrentTimeLabel != null) {
                fullscreenCurrentTimeLabel.setText(formatTime(fullscreenPlaybackSlider.getValue()));
            }
        });
        fullscreenPlaybackSlider.setOnMouseReleased(_event -> {
            if (isUserSeekingFullscreen && playerService != null && playerService.getCurrentSong() != null) {
                playerService.seek((long) fullscreenPlaybackSlider.getValue());
            }
            isUserSeekingFullscreen = false;
        });
    }

    // --- Service Listener Setup ---
    private void setupPlayerBindingsAndListeners() {
        if (playerService == null) return;

        playerService.totalDurationProperty().addListener((_obs, _oldVal, newVal) -> Platform.runLater(() -> {
            double totalMillis = newVal.doubleValue();
            if (playbackSlider != null) playbackSlider.setMax(totalMillis > 0 ? totalMillis : 0.0);
            if (totalDurationLabel != null) totalDurationLabel.setText(formatTime(newVal.longValue()));
            if (primaryStage != null && primaryStage.isFullScreen()) updateFullscreenUIDisplay();
        }));

        playerService.currentTimeProperty().addListener((_obs, _oldVal, newVal) -> Platform.runLater(() -> {
            if (playbackSlider != null && !isUserSeeking) playbackSlider.setValue(newVal.doubleValue());
            if (currentTimeLabel != null) currentTimeLabel.setText(formatTime(newVal.longValue()));
            
            if (lyricsService != null) {
                lyricsService.updateCurrentDisplayLines(newVal.longValue(), this.currentSongLiveOffsetMs);
            }

            if (primaryStage != null && primaryStage.isFullScreen()) {
                if (fullscreenPlaybackSlider != null && !isUserSeekingFullscreen) fullscreenPlaybackSlider.setValue(newVal.doubleValue());
                if (fullscreenCurrentTimeLabel != null) fullscreenCurrentTimeLabel.setText(formatTime(newVal.longValue()));
                // Fullscreen lyrics update via lyricsService listener + updateFullscreenUIDisplay
            }
        }));

        playerService.statusProperty().addListener((_obs, oldStatus, newStatus) -> Platform.runLater(() -> {
            updateControlsBasedOnStatus(newStatus); // Normal view controls
            if (primaryStage != null && primaryStage.isFullScreen()) updateFullscreenUIDisplay(); // Fullscreen controls

            if (oldStatus == MediaPlayer.Status.PLAYING && (newStatus == MediaPlayer.Status.STOPPED || newStatus == MediaPlayer.Status.READY)) {
                playNextSong(true); // Auto-play context
            }
        }));

        playerService.currentSongProperty().addListener((_obs, _oldSong, newSong) -> Platform.runLater(() -> {
            updateNowPlayingDisplay(newSong); // Normal view
            if (newSong != null) {
                if (lyricsService != null) {
                    lyricsService.loadLyricsForSong(newSong);
                    // Initialize live offset from file for the new song
                    this.currentSongLiveOffsetMs = (int) lyricsService.getInitialLoadedOffsetMs();
                    if (lyricOffsetLabel != null) lyricOffsetLabel.setText(this.currentSongLiveOffsetMs + " ms");
                    // Update lyrics display with this initial offset
                    lyricsService.updateCurrentDisplayLines(playerService.getCurrentTimeMillis(), this.currentSongLiveOffsetMs);
                }
            } else {
                resetUIForNoActiveSong(); // Clears normal view elements and resets offset
            }
            if (primaryStage != null && primaryStage.isFullScreen()) updateFullscreenUIDisplay();
        }));

        if (playbackSlider != null) playbackSlider.disableProperty().bind(playerService.currentSongProperty().isNull());
        if (fullscreenPlaybackSlider != null) fullscreenPlaybackSlider.disableProperty().bind(playerService.currentSongProperty().isNull());
    }

    private void setupLyricsBindingsAndListeners() {
        if (lyricsService == null) return;
        lyricsService.displayLinesProperty().addListener((_obs, _oldLines, newLines) -> Platform.runLater(() -> {
            if(previousLyricLabel != null) previousLyricLabel.setText(getLyricTextOrEmpty(newLines, 0));
            if(currentLyricLabel != null) currentLyricLabel.setText(getLyricTextOrEmpty(newLines, 1));
            if(next1LyricLabel != null) next1LyricLabel.setText(getLyricTextOrEmpty(newLines, 2));
            if(next2LyricLabel != null) next2LyricLabel.setText(getLyricTextOrEmpty(newLines, 3));
            if (primaryStage != null && primaryStage.isFullScreen()) {
                updateFullscreenUIDisplay(); // Updates fullscreen lyrics specifically
            }
        }));
    }

    // --- UI Update Methods ---
    private void updateFullscreenUIDisplay() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateFullscreenUIDisplay);
            return;
        }
        if (playerService == null || queueService == null || lyricsService == null || fullscreenViewRootPane == null || !fullscreenViewRootPane.isVisible()) {
            return;
        }

        // Top Bar
        Song nextInQueue = queueService.peekNextSongs(1).stream().findFirst().orElse(null);
        if(fullscreenNextSongLabel != null) fullscreenNextSongLabel.setText(nextInQueue != null ? "Next: " + nextInQueue.getTitle() : "Next: -");
        int totalQueueSize = queueService.getSize();
        int remainingInQueue = (nextInQueue != null && totalQueueSize > 0) ? Math.max(0, totalQueueSize - 1) : totalQueueSize;
        if(fullscreenQueueCountLabel != null) fullscreenQueueCountLabel.setText("(+" + remainingInQueue + " more)");

        // Lyrics
        List<LyricLine> displayLines = lyricsService.getDisplayLines();
        if(fullscreenPreviousLyricLabel != null) fullscreenPreviousLyricLabel.setText(getLyricTextOrEmpty(displayLines, 0));
        if(fullscreenCurrentLyricLabel != null) fullscreenCurrentLyricLabel.setText(getLyricTextOrEmpty(displayLines, 1));
        if(fullscreenNext1LyricLabel != null) fullscreenNext1LyricLabel.setText(getLyricTextOrEmpty(displayLines, 2));
        if(fullscreenNext2LyricLabel != null) fullscreenNext2LyricLabel.setText(getLyricTextOrEmpty(displayLines, 3));

        // Bottom Bar
        MediaPlayer.Status status = playerService.getStatus();
        if(fullscreenPlayPauseButton != null) {
            fullscreenPlayPauseButton.setText(status == MediaPlayer.Status.PLAYING ? "Pause" : "Play");
            boolean canPlayFullscreen = (status == MediaPlayer.Status.PAUSED || (playerService.getCurrentSong() != null || !queueService.isEmpty()));
            fullscreenPlayPauseButton.setDisable(!(status == MediaPlayer.Status.PLAYING || canPlayFullscreen));
        }
        if(fullscreenSkipButton != null) fullscreenSkipButton.setDisable(queueService.isEmpty());

        if (playerService.getCurrentSong() != null) {
            if(fullscreenPlaybackSlider != null) fullscreenPlaybackSlider.setMax(playerService.getTotalDurationMillis());
            if(fullscreenPlaybackSlider != null && !isUserSeekingFullscreen) fullscreenPlaybackSlider.setValue(playerService.getCurrentTimeMillis());
            if(fullscreenCurrentTimeLabel != null) fullscreenCurrentTimeLabel.setText(formatTime(playerService.getCurrentTimeMillis()));
            if(fullscreenTotalDurationLabel != null) fullscreenTotalDurationLabel.setText(formatTime(playerService.getTotalDurationMillis()));
        } else {
            if(fullscreenPlaybackSlider != null) { fullscreenPlaybackSlider.setMax(0); fullscreenPlaybackSlider.setValue(0); }
            if(fullscreenCurrentTimeLabel != null) fullscreenCurrentTimeLabel.setText(formatTime(0));
            if(fullscreenTotalDurationLabel != null) fullscreenTotalDurationLabel.setText(formatTime(0));
        }
    }

    private void resetUIForNoActiveSong() {
        if (playbackSlider != null) { playbackSlider.setValue(0); playbackSlider.setMax(0); }
        if (currentTimeLabel != null) currentTimeLabel.setText(formatTime(0.0));
        if (totalDurationLabel != null) totalDurationLabel.setText(formatTime(0.0));
        if (lyricsService != null) lyricsService.clearLyrics();
        
        this.currentSongLiveOffsetMs = 0; // Reset live offset
        if(lyricOffsetLabel != null) lyricOffsetLabel.setText("0 ms"); // Reset offset display
    }

    private String getLyricTextOrEmpty(List<LyricLine> lines, int index) {
        if (lines != null && index >= 0 && index < lines.size()) {
            LyricLine line = lines.get(index);
            return line != null ? line.getText() : "";
        }
        return "";
    }

    private void populateGenreFilter() {
        if(genreFilterComboBox == null) return;
        Set<String> genres = SongDAO.getDistinctGenres();
        ObservableList<String> opts = FXCollections.observableArrayList();
        opts.add(ALL_GENRES);
        opts.addAll(genres);
        genreFilterComboBox.setItems(opts);
        genreFilterComboBox.setValue(ALL_GENRES);
    }

    private void updateSongTableView() {
        if(songTableView == null || searchTextField == null || genreFilterComboBox == null) return;
        String search = searchTextField.getText();
        String genre = genreFilterComboBox.getValue();
        if(ALL_GENRES.equals(genre)) genre = null;
        songTableView.setItems(FXCollections.observableArrayList(SongDAO.findSongsByCriteria(search, genre)));
    }
    
    private void updateControlsBasedOnStatus(MediaPlayer.Status status) {
        if (playPauseButton == null) return; // Guard against early calls
        if (status == null) status = MediaPlayer.Status.UNKNOWN;

        boolean playing = status == MediaPlayer.Status.PLAYING;
        boolean paused = status == MediaPlayer.Status.PAUSED;
        boolean stoppedOrReady = status == MediaPlayer.Status.STOPPED || status == MediaPlayer.Status.READY;
        boolean haltedOrUnknown = status == MediaPlayer.Status.HALTED || status == MediaPlayer.Status.UNKNOWN;

        boolean queueCanProvideSong = queueService != null && !queueService.isEmpty();
        boolean songIsSelected = currentlySelectedSong != null;
        boolean songIsLoaded = playerService != null && playerService.getCurrentSong() != null;

        boolean canStartPlayback = paused || ((stoppedOrReady || haltedOrUnknown) && (songIsLoaded || songIsSelected || queueCanProvideSong));

        playPauseButton.setText(playing ? "Pause" : "Play");
        playPauseButton.setDisable(!playing && !canStartPlayback);
        if(stopButton != null) stopButton.setDisable(!playing && !paused);
        if(skipButton != null) skipButton.setDisable(!queueCanProvideSong);

        if (stoppedOrReady || haltedOrUnknown) {
            if (!isUserSeeking && playbackSlider != null && !playbackSlider.isValueChanging()) playbackSlider.setValue(0);
            if (currentTimeLabel != null) currentTimeLabel.setText(formatTime(0));
            if (haltedOrUnknown && !songIsLoaded && totalDurationLabel != null) totalDurationLabel.setText(formatTime(0));
        }
    }

    private void updateQueueDisplay() {
        if(queueService == null || queueSong1Label == null) return;
        ObservableList<Song> q = queueService.getQueue();
        int size = q.size();
        queueSong1Label.setText("1. " + (size >= 1 ? formatSongForQueue(q.get(0)) : "-"));
        if(queueSong2Label != null) queueSong2Label.setText("2. " + (size >= 2 ? formatSongForQueue(q.get(1)) : "-"));
        if(queueSong3Label != null) queueSong3Label.setText("3. " + (size >= 3 ? formatSongForQueue(q.get(2)) : "-"));
        if(queueCountLabel != null) {
            int rem = Math.max(0, size - 3);
            queueCountLabel.setText("(+" + rem + " more)");
        }
        if(queueTitledPane != null) queueTitledPane.setExpanded(!q.isEmpty());
        updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
    }

    private String formatSongForQueue(Song song) { return song != null ? song.toString() : "-"; }

    private void updateNowPlayingDisplay(Song song) {
        if(nowPlayingTitleLabel == null || nowPlayingArtistLabel == null) return;
        if(song != null){
            nowPlayingTitleLabel.setText(song.getTitle() != null ? song.getTitle() : "Unknown");
            nowPlayingArtistLabel.setText(song.getArtist() != null ? song.getArtist() : "Unknown");
        } else {
            nowPlayingTitleLabel.setText("-");
            nowPlayingArtistLabel.setText("-");
        }
    }

    // --- FXML Action Handlers (Normal View) ---
    @FXML private void handlePlayPause() {
        if(playerService == null) return;
        MediaPlayer.Status s = playerService.getStatus();
        if(s == MediaPlayer.Status.PLAYING) playerService.pause();
        else if(s == MediaPlayer.Status.PAUSED) playerService.play();
        else {
            if(this.currentlySelectedSong != null) loadAndPlaySong(this.currentlySelectedSong);
            else if(queueService != null && !queueService.isEmpty()) playNextSong(false);
            else updateControlsBasedOnStatus(MediaPlayer.Status.STOPPED);
        }
    }

    @FXML private void handleStop() {
        if(playerService != null) playerService.loadSong(null, false); // Stops and unloads
        if(queueService != null) queueService.clear();
        updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
        updateQueueDisplay();
    }

    @FXML private void handleSkip() {
        if(playerService != null){
            playerService.stop(); // Stop current song first
            playNextSong(false); // Attempt to play next (not auto-play)
        }
    }

    @FXML private void handleAddToQueue() {
        if(currentlySelectedSong != null && queueService != null){
            queueService.addSong(currentlySelectedSong);
            if(songTableView != null) songTableView.getSelectionModel().clearSelection();
        }
    }

    @FXML private void handleThemeToggle() {
        if(themeToggleButton != null && themeToggleButton.getScene() != null) {
            String darkClassName = "dark-mode";
            ObservableList<String> styleClasses = themeToggleButton.getScene().getRoot().getStyleClass();
            if(themeToggleButton.isSelected()){
                if(!styleClasses.contains(darkClassName)) styleClasses.add(darkClassName);
                themeToggleButton.setText("Light Mode");
            } else {
                styleClasses.remove(darkClassName);
                themeToggleButton.setText("Dark Mode");
            }
        }
    }

    @FXML private void handleIncreaseOffset(ActionEvent event) { adjustLyricOffset(LYRIC_OFFSET_ADJUSTMENT_STEP); }
    @FXML private void handleDecreaseOffset(ActionEvent event) { adjustLyricOffset(-LYRIC_OFFSET_ADJUSTMENT_STEP); }

    // --- FXML Action Handlers (Fullscreen View) ---
    @FXML private void handleFullscreenToggle() { // For normal view button
        if (primaryStage != null) primaryStage.setFullScreen(fullscreenToggleButton.isSelected());
    }

    @FXML private void handleExitFullscreenToggle() { // For fullscreen view button
        if (primaryStage != null) primaryStage.setFullScreen(false);
        if (fullscreenExitButton != null) fullscreenExitButton.setSelected(false);
    }

    @FXML private void handleFullscreenPlayPause() {
        if (playerService == null) return;
        MediaPlayer.Status status = playerService.getStatus();
        if (status == MediaPlayer.Status.PLAYING) playerService.pause();
        else {
             if (status == MediaPlayer.Status.PAUSED || playerService.getCurrentSong() != null) playerService.play();
             else if (!queueService.isEmpty()) playNextSong(false); // Play from queue if available
        }
    }

    @FXML private void handleFullscreenSkip() { handleSkip(); } // Delegate to normal skip logic

    // --- Helper Methods ---
    private void adjustLyricOffset(int amount) {
        Song currentSongForOffset = (lyricsService != null) ? lyricsService.getCurrentSong() : null;
        if (playerService == null || currentSongForOffset == null || lyricsService == null) {
            System.out.println("Cannot adjust offset: No song active or services unavailable.");
            return;
        }

        this.currentSongLiveOffsetMs += amount;
        if(lyricOffsetLabel != null) lyricOffsetLabel.setText(this.currentSongLiveOffsetMs + " ms");

        lyricsService.updateCurrentDisplayLines(playerService.getCurrentTimeMillis(), this.currentSongLiveOffsetMs);

        String lyricsFilePath = currentSongForOffset.getLyricsFilePath();
        if (lyricsFilePath != null && !lyricsFilePath.isBlank()) {
            try {
                LrcWriter.saveOffsetToLrcFile(lyricsFilePath, this.currentSongLiveOffsetMs);
            } catch (IOException e) {
                System.err.println("Error saving offset to LRC: " + lyricsFilePath + " - " + e.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR, "Could not save lyrics timing: " + e.getMessage());
                alert.setTitle("Offset Save Error");
                alert.showAndWait();
            }
        } else {
            System.err.println("Cannot save offset: Lyrics file path unavailable for " + currentSongForOffset.getTitle());
        }
    }
    
    private void playNextSong(boolean isAutoPlayContext) {
        if(playerService == null) return;
        Song songToPlay = null;
        if(queueService != null && !queueService.isEmpty()) songToPlay = queueService.getNextSong();

        if(songToPlay == null){ // Queue was empty or getNextSong returned null
            if(!isAutoPlayContext && currentlySelectedSong != null){ // And not auto-play, and song selected in library
                MediaPlayer.Status currentStatus = playerService.getStatus();
                if(currentStatus != MediaPlayer.Status.PLAYING && currentStatus != MediaPlayer.Status.PAUSED) {
                    songToPlay = currentlySelectedSong; // Play selected library song
                }
            }
        }

        if(songToPlay != null) loadAndPlaySong(songToPlay);
        else if(playerService.getCurrentSong() != null || playerService.getStatus() == MediaPlayer.Status.PLAYING || playerService.getStatus() == MediaPlayer.Status.PAUSED) {
            playerService.loadSong(null, false); // Effectively stops and unloads if nothing to play
        }
    }

    private void loadAndPlaySong(Song song) {
        if(playerService == null) return;
        if(song == null){
            playerService.loadSong(null, false); // Unload
            return;
        }
        boolean loadingOk = playerService.loadSong(song, true); // Request load and auto-play
        if(!loadingOk){
            updateControlsBasedOnStatus(MediaPlayer.Status.HALTED);
            if(lyricsService != null) lyricsService.clearLyrics();
            Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load audio for " + song.getTitle());
            a.setTitle("Playback Error");
            a.showAndWait();
        }
        // Lyrics and offset update are handled by playerService.currentSongProperty listener
    }

    private String formatTime(long millis) {
        if (millis < 0 || millis == Long.MAX_VALUE) return "0:00";
        long secs = TimeUnit.MILLISECONDS.toSeconds(millis);
        return String.format("%d:%02d", secs / 60, secs % 60);
    }

    private String formatTime(double millis) {
        if (Double.isNaN(millis) || Double.isInfinite(millis) || millis < 0) return "0:00";
        return formatTime((long) millis);
    }
}