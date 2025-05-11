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
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
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

// --- Util Imports (LrcWriter is not directly used here; MainController handles saving) ---
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Controller for NormalView.fxml, managing the standard user interface of the application.
 * Handles UI elements, event responses, and updates based on service states for the normal view.
 * Implements MainController.SubController for interaction with the main orchestrator.
 */
public class NormalViewController implements Initializable, MainController.SubController {

    // --- FXML Injected Fields for Normal View ---
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

    @FXML private VBox lyricsContainer; // Though not directly manipulated, good to have if styles change
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
    @FXML private Button fullscreenToggleButton;
    @FXML private ToggleButton themeToggleButton;

    @FXML private Button increaseOffsetButton;
    @FXML private Label lyricOffsetLabel;
    @FXML private Button decreaseOffsetButton;

    // --- Service Dependencies & Main Controller Reference ---
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;
    private MainController mainController;
    private Stage primaryStage; // Though stage operations like fullscreen are handled by MainController

    // --- State ---
    private Song currentlySelectedSong = null;
    private boolean isUserSeeking = false;
    // currentSongLiveOffsetMs is now managed by MainController. This controller will display it.

    // --- Constants ---
    private static final String ALL_GENRES = "All Genres";

    // Listener for queue changes to update its display
    private final ListChangeListener<Song> queueChangeListener = change ->
            Platform.runLater(this::updateQueueDisplay);


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("NormalViewController initialized.");
        // Initial setup not dependent on injected services
        setupTableViewColumns();
        addSearchAndFilterListeners();
        addTableViewSelectionListener();
        setupPlaybackSliderListeners();

        if (playbackSlider != null) playbackSlider.setDisable(true);
        if (stopButton != null) stopButton.setText("Stop All"); // Consistent text
        if (lyricOffsetLabel != null) lyricOffsetLabel.setText("0 ms"); // Initial display
        if (addToQueueButton != null) addToQueueButton.setDisable(true); // Initially disabled
    }

    // --- Service and Controller Injection Methods ---
    public void setPlayerService(PlayerService playerService) { this.playerService = playerService; }
    public void setLyricsService(LyricsService lyricsService) { this.lyricsService = lyricsService; }
    public void setQueueService(QueueService queueService) {
        if (this.queueService != null) {
            this.queueService.getQueue().removeListener(queueChangeListener);
        }
        this.queueService = queueService;
        if (this.queueService != null) {
            this.queueService.getQueue().addListener(queueChangeListener);
        }
    }
    public void setMainController(MainController mainController) { this.mainController = mainController; }
    public void setPrimaryStage(Stage stage) { this.primaryStage = stage; }

    /**
     * Called by MainController after all services and references are injected.
     */
    public void initializeBindingsAndListeners() {
        System.out.println("NormalViewController: Initializing service-dependent bindings and listeners...");
        if (playerService == null || lyricsService == null || queueService == null || mainController == null) {
            System.err.println("NormalViewController ERROR: Critical dependencies are null!");
            return;
        }
        populateGenreFilter();
        updateSongTableView(); // Load initial song list

        setupPlayerServiceListeners();
        setupLyricsServiceListeners();

        // Initial UI state updates
        updateUIDisplay();
    }
    
    /**
     * General method to refresh the entire UI of this view.
     * Can be called by MainController when this view becomes active or state changes.
     */
    public void updateUIDisplay() {
        System.out.println("NormalViewController: Updating UI display.");
        if (playerService == null || lyricsService == null || queueService == null || mainController == null) {
            return;
        }
        updateControlsBasedOnStatus(playerService.getStatus());
        updateNowPlayingDisplay(playerService.getCurrentSong());
        updateQueueDisplay();
        // Get current offset from MainController and update label
        updateLyricOffsetDisplay(mainController.getCurrentSongLiveOffsetMs());

        // Refresh lyrics based on current time and centrally managed offset
        lyricsService.updateCurrentDisplayLines(playerService.getCurrentTimeMillis(), mainController.getCurrentSongLiveOffsetMs());

        // Update slider position and time labels
        if (playerService.getCurrentSong() != null) {
            if (playbackSlider != null) {
                playbackSlider.setMax(playerService.getTotalDurationMillis());
                if (!isUserSeeking) playbackSlider.setValue(playerService.getCurrentTimeMillis());
            }
            if (currentTimeLabel != null) currentTimeLabel.setText(mainController.formatTime(playerService.getCurrentTimeMillis()));
            if (totalDurationLabel != null) totalDurationLabel.setText(mainController.formatTime(playerService.getTotalDurationMillis()));
        } else {
            if (playbackSlider != null) { playbackSlider.setMax(0); playbackSlider.setValue(0); }
            if (currentTimeLabel != null) currentTimeLabel.setText(mainController.formatTime(0));
            if (totalDurationLabel != null) totalDurationLabel.setText(mainController.formatTime(0));
        }
    }

    // --- UI Setup Methods ---
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
            sm.selectedItemProperty().addListener((obs,ov,nv)->{
                this.currentlySelectedSong = nv;
                if(addToQueueButton != null) addToQueueButton.setDisable(nv == null);
                if(playerService == null || (playerService.getStatus() != MediaPlayer.Status.PLAYING && playerService.getStatus() != MediaPlayer.Status.PAUSED)) {
                    updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
                }
            });
        }
    }

    private void setupPlaybackSliderListeners() {
        if(playbackSlider != null){
            playbackSlider.setOnMousePressed(e -> {
                if(playerService != null && playerService.getCurrentSong() != null) isUserSeeking = true;
                else e.consume();
            });
            playbackSlider.setOnMouseDragged(e -> {
                if(isUserSeeking && currentTimeLabel != null && mainController != null) {
                    currentTimeLabel.setText(mainController.formatTime(playbackSlider.getValue()));
                }
            });
            playbackSlider.setOnMouseReleased(e -> {
                if(isUserSeeking && playerService != null && playerService.getCurrentSong() != null) {
                    playerService.seek((long)playbackSlider.getValue());
                }
                isUserSeeking = false;
            });
        }
    }

    // --- Service Listener Setup ---
    private void setupPlayerServiceListeners() {
        playerService.totalDurationProperty().addListener((_obs, _oldVal, newVal) -> Platform.runLater(() -> {
            double totalMillis = newVal.doubleValue();
            if (playbackSlider != null) playbackSlider.setMax(totalMillis > 0 ? totalMillis : 0.0);
            if (totalDurationLabel != null && mainController != null) totalDurationLabel.setText(mainController.formatTime(newVal.longValue()));
        }));

        playerService.currentTimeProperty().addListener((_obs, _oldVal, newVal) -> Platform.runLater(() -> {
            long newTimeMillis = newVal.longValue();
            if (playbackSlider != null && !isUserSeeking) playbackSlider.setValue(newTimeMillis);
            if (currentTimeLabel != null && mainController != null) currentTimeLabel.setText(mainController.formatTime(newTimeMillis));
            if (lyricsService != null && mainController != null) {
                lyricsService.updateCurrentDisplayLines(newTimeMillis, mainController.getCurrentSongLiveOffsetMs());
            }
        }));

        playerService.statusProperty().addListener((_obs, oldStatus, newStatus) -> Platform.runLater(() -> {
            updateControlsBasedOnStatus(newStatus);
            if (oldStatus == MediaPlayer.Status.PLAYING && (newStatus == MediaPlayer.Status.STOPPED || newStatus == MediaPlayer.Status.READY)) {
                if (mainController != null) mainController.playNextSong(true); // Auto-play context
            }
        }));

        playerService.currentSongProperty().addListener((_obs, _oldSong, newSong) -> Platform.runLater(() -> {
            updateNowPlayingDisplay(newSong);
            if (newSong != null) {
                if (lyricsService != null && mainController != null) {
                    lyricsService.loadLyricsForSong(newSong); // This populates LyricsService.initialLoadedOffsetMs
                    mainController.setCurrentSongLiveOffsetMs((int) lyricsService.getInitialLoadedOffsetMs()); // Inform MainController
                    updateLyricOffsetDisplay(mainController.getCurrentSongLiveOffsetMs()); // Update local label
                    // Update lyrics display with this initial offset
                    lyricsService.updateCurrentDisplayLines(playerService.getCurrentTimeMillis(), mainController.getCurrentSongLiveOffsetMs());
                }
            } else {
                resetUIForNoActiveSong();
            }
        }));
        if (playbackSlider != null) playbackSlider.disableProperty().bind(playerService.currentSongProperty().isNull());
    }

    private void setupLyricsServiceListeners() {
        lyricsService.displayLinesProperty().addListener((_obs, _oldLines, newLines) -> Platform.runLater(() -> {
            if(previousLyricLabel != null && mainController != null) previousLyricLabel.setText(mainController.getLyricTextOrEmpty(newLines, 0));
            if(currentLyricLabel != null && mainController != null) currentLyricLabel.setText(mainController.getLyricTextOrEmpty(newLines, 1));
            if(next1LyricLabel != null && mainController != null) next1LyricLabel.setText(mainController.getLyricTextOrEmpty(newLines, 2));
            if(next2LyricLabel != null && mainController != null) next2LyricLabel.setText(mainController.getLyricTextOrEmpty(newLines, 3));
        }));
    }
    
    // --- UI Update Methods ---
    private void resetUIForNoActiveSong() {
        if (playbackSlider != null) { playbackSlider.setValue(0); playbackSlider.setMax(0); }
        if (currentTimeLabel != null && mainController != null) currentTimeLabel.setText(mainController.formatTime(0));
        if (totalDurationLabel != null && mainController != null) totalDurationLabel.setText(mainController.formatTime(0));
        if (lyricsService != null) lyricsService.clearLyrics();
        if (mainController != null) mainController.setCurrentSongLiveOffsetMs(0); // Reset central offset
        updateLyricOffsetDisplay(0); // Update local label
    }

    /**
     * Updates the lyric offset label in this view. Called by MainController.
     * @param offset The current live offset in milliseconds.
     */
    public void updateLyricOffsetDisplay(int offset) {
        if (lyricOffsetLabel != null) {
            lyricOffsetLabel.setText(offset + " ms");
        }
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
        if (playPauseButton == null || mainController == null) return;
        if (status == null) status = MediaPlayer.Status.UNKNOWN;

        boolean playing = status == MediaPlayer.Status.PLAYING;
        boolean paused = status == MediaPlayer.Status.PAUSED;
        boolean stoppedOrReady = status == MediaPlayer.Status.STOPPED || status == MediaPlayer.Status.READY;
        boolean haltedOrUnknown = status == MediaPlayer.Status.HALTED || status == MediaPlayer.Status.UNKNOWN;

        boolean queueCanProvideSong = queueService != null && !queueService.isEmpty();
        boolean songIsSelectedInLibrary = currentlySelectedSong != null;
        boolean songIsLoadedInPlayer = playerService != null && playerService.getCurrentSong() != null;

        boolean canStartPlayback = paused || ((stoppedOrReady || haltedOrUnknown) && (songIsLoadedInPlayer || songIsSelectedInLibrary || queueCanProvideSong));

        playPauseButton.setText(playing ? "Pause" : "Play");
        playPauseButton.setDisable(!playing && !canStartPlayback);
        if(stopButton != null) stopButton.setDisable(!playing && !paused);
        if(skipButton != null) skipButton.setDisable(!queueCanProvideSong && !songIsLoadedInPlayer);

        if (stoppedOrReady || haltedOrUnknown) {
            if (!isUserSeeking && playbackSlider != null && !playbackSlider.isValueChanging()) playbackSlider.setValue(0);
            if (currentTimeLabel != null) currentTimeLabel.setText(mainController.formatTime(0));
            if (haltedOrUnknown && !songIsLoadedInPlayer && totalDurationLabel != null) totalDurationLabel.setText(mainController.formatTime(0));
        }
    }

    private void updateQueueDisplay() {
        if(queueService == null || queueSong1Label == null || mainController == null) return;
        ObservableList<Song> q = queueService.getQueue();
        int size = q.size();
        queueSong1Label.setText("1. " + (size >= 1 ? mainController.formatSongForQueue(q.get(0)) : "-"));
        if(queueSong2Label != null) queueSong2Label.setText("2. " + (size >= 2 ? mainController.formatSongForQueue(q.get(1)) : "-"));
        if(queueSong3Label != null) queueSong3Label.setText("3. " + (size >= 3 ? mainController.formatSongForQueue(q.get(2)) : "-"));
        if(queueCountLabel != null){
            int rem = Math.max(0, size - 3);
            queueCountLabel.setText("(+" + rem + " more)");
        }
        if(queueTitledPane != null) queueTitledPane.setExpanded(!q.isEmpty());
        updateControlsBasedOnStatus(playerService != null ? playerService.getStatus() : MediaPlayer.Status.UNKNOWN);
    }

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
    @FXML private void handlePlayPause() { if (mainController != null) mainController.handlePlayPause(); }
    @FXML private void handleStop() { if (mainController != null) mainController.handleStop(); }
    @FXML private void handleSkip() { if (mainController != null) mainController.handleSkip(); }

    @FXML private void handleAddToQueue() {
        if(currentlySelectedSong != null && queueService != null){
            queueService.addSong(currentlySelectedSong);
            if(songTableView != null) songTableView.getSelectionModel().clearSelection();
        } else {
             System.out.println("NormalViewController: Add to Queue - No song selected or queue service unavailable.");
        }
    }
    
    @FXML private void handleThemeToggle() {
        if (mainController != null && themeToggleButton != null) {
            mainController.toggleTheme(themeToggleButton.isSelected(), themeToggleButton);
        }
    }

    @FXML private void handleFullscreenToggle() {
        if (mainController != null && primaryStage != null) {
            // Now, this button simply toggles the current state of the stage
            mainController.setAppFullScreen(!primaryStage.isFullScreen());
        }
        // No selected state to manage on the button itself.
        // The text "Toggle Fullscreen" remains static.
    }


    @FXML private void handleIncreaseOffset(ActionEvent event) { 
        if (mainController != null) mainController.adjustLyricOffset(MainController.LYRIC_OFFSET_ADJUSTMENT_STEP); 
    }
    @FXML private void handleDecreaseOffset(ActionEvent event) { 
        if (mainController != null) mainController.adjustLyricOffset(-MainController.LYRIC_OFFSET_ADJUSTMENT_STEP); 
    }
    
    // --- Getter for MainController to sync theme button & access selected song ---
    @Override
    public ToggleButton getThemeToggleButton() {
        return themeToggleButton;
    }
    
    public Song getCurrentlySelectedSong() {
        return this.currentlySelectedSong;
    }
}