package controller;

// --- JavaFX Imports ---
import javafx.application.Platform;
// import javafx.beans.binding.Bindings; // No longer strictly needed if not doing complex binds here
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;

// --- Model Imports ---
import model.LyricLine;
import model.Song;

// --- Service Imports ---
import service.LyricsService;
import service.PlayerService;
import service.QueueService;

// --- Util Imports ---
// LrcWriter interaction is handled by MainController
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for FullscreenView.fxml, managing the simplified, immersive user interface.
 * Handles UI elements, event responses, and updates based on service states for the fullscreen view.
 * Implements MainController.SubController for interaction with the main orchestrator.
 */
public class FullscreenViewController implements Initializable, MainController.SubController {

    // --- FXML Injected Fields for Fullscreen View ---
    @FXML private Label fullscreenNextSongLabel;
    @FXML private Label fullscreenQueueCountLabel;
    @FXML private Label fullscreenPreviousLyricLabel;
    @FXML private Label fullscreenCurrentLyricLabel;
    @FXML private Label fullscreenNext1LyricLabel;
    @FXML private Label fullscreenNext2LyricLabel;
    @FXML private Label fullscreenCurrentTimeLabel;
    @FXML private Slider fullscreenPlaybackSlider;
    @FXML private Label fullscreenTotalDurationLabel;
    @FXML private Label fullscreenNowPlayingTitleLabel;
    @FXML private Label fullscreenNowPlayingArtistLabel;
    @FXML private Button fullscreenPlayPauseButton;
    @FXML private Button fullscreenSkipButton;
    @FXML private Button fullscreenExitButton;
    @FXML private Button fullscreenThemeButton;
    @FXML private Button fullscreenStopButton;
    @FXML private Button fullscreenIncreaseOffsetButton;
    @FXML private Label fullscreenLyricOffsetLabel;
    @FXML private Button fullscreenDecreaseOffsetButton;

    // --- Service Dependencies & Main Controller Reference ---
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;
    private MainController mainController;
    private Stage primaryStage;

    // --- State ---
    private boolean isUserSeekingFullscreen = false;
    // The actual currentSongLiveOffsetMs is managed by MainController

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("FullscreenViewController initialized.");
        setupFullscreenPlaybackSliderListeners();
        if (fullscreenPlaybackSlider != null) fullscreenPlaybackSlider.setDisable(true); // Initially disable
        // Initial button texts will be set by updateUIDisplay
    }

    // --- Service and Controller Injection Methods ---
    public void setPlayerService(PlayerService playerService) { this.playerService = playerService; }
    public void setLyricsService(LyricsService lyricsService) { this.lyricsService = lyricsService; }
    public void setQueueService(QueueService queueService) { this.queueService = queueService; }
    public void setMainController(MainController mainController) { this.mainController = mainController; }
    public void setPrimaryStage(Stage stage) { this.primaryStage = stage; }


    /**
     * Called by MainController after all services and references are injected.
     * Sets up listeners for service property changes to keep the UI synchronized.
     */
    public void initializeBindingsAndListeners() {
        System.out.println("FullscreenViewController: Initializing service-dependent bindings and listeners...");
        if (playerService == null || lyricsService == null || queueService == null || mainController == null) {
            System.err.println("FullscreenViewController ERROR: Critical dependencies are null!");
            return;
        }

        // Add listeners to service properties that affect this view's display.
        // These listeners call updateUIDisplay to refresh relevant parts.
        playerService.statusProperty().addListener((_obs, _oldVal, _newVal) -> Platform.runLater(this::updateUIDisplay));
        playerService.currentTimeProperty().addListener((_obs, _oldVal, _newVal) -> Platform.runLater(this::updateUIDisplay));
        playerService.totalDurationProperty().addListener((_obs, _oldVal, _newVal) -> Platform.runLater(this::updateUIDisplay));
        playerService.currentSongProperty().addListener((_obs, _oldVal, newSong) -> Platform.runLater(() -> {
            // When a new song loads, LyricsService gets the initial offset.
            // MainController's currentSongLiveOffsetMs needs to be updated.
            // This controller just reflects that centrally managed offset.
            if (newSong != null) {
                // LyricsService would have loaded lyrics and initial offset.
                // MainController's listener on PlayerService.currentSongProperty should have updated
                // its own currentSongLiveOffsetMs by calling lyricsService.getInitialLoadedOffsetMs()
                // and then MainController.setCurrentSongLiveOffsetMs().
                // So, here we just ensure the display is refreshed.
                 mainController.setCurrentSongLiveOffsetMs((int) lyricsService.getInitialLoadedOffsetMs());
            } else {
                 mainController.setCurrentSongLiveOffsetMs(0); // Reset offset if no song
            }
            updateUIDisplay(); // General refresh
        }));


        lyricsService.displayLinesProperty().addListener((_obs, _oldVal, _newVal) -> Platform.runLater(this::updateUIDisplay));

        // QueueService changes are observed by MainController, which then calls updateUIDisplay on active sub-controllers.
        // No direct queue listener here to prevent redundant calls to updateUIDisplay.

        if (fullscreenPlaybackSlider != null) {
            fullscreenPlaybackSlider.disableProperty().bind(playerService.currentSongProperty().isNull());
        }
        updateUIDisplay(); // Perform an initial UI refresh
    }

    /**
     * Central method to refresh all UI elements in the fullscreen view.
     * This should be called whenever a state change requires the UI to update.
     */
    public void updateUIDisplay() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateUIDisplay);
            return;
        }
        if (playerService == null || lyricsService == null || queueService == null || mainController == null || primaryStage == null || !primaryStage.isFullScreen()) {
            // Avoid updates if not in fullscreen or services not ready,
            // unless MainController explicitly needs to update something like theme button state
            return;
        }

        // Top Bar: Next Song and Queue Count
        Song nextInQueue = queueService.peekNextSongs(1).stream().findFirst().orElse(null);
        int totalQueueSize = queueService.getSize();
        int calculatedRemainingInQueue = (nextInQueue != null) ? Math.max(0, totalQueueSize - 1) : 0;

        if(fullscreenNextSongLabel != null) {
            fullscreenNextSongLabel.setText(nextInQueue != null ? "Next: " + mainController.formatSongForQueue(nextInQueue) : "Next: -");
        }
        if(fullscreenQueueCountLabel != null) {
            if (calculatedRemainingInQueue > 0) {
                fullscreenQueueCountLabel.setText("(+" + calculatedRemainingInQueue + " more)");
                fullscreenQueueCountLabel.setVisible(true);
            } else {
                fullscreenQueueCountLabel.setVisible(false);
            }
        }

        // Lyrics
        List<LyricLine> displayLines = lyricsService.getDisplayLines();
        if(fullscreenPreviousLyricLabel != null) fullscreenPreviousLyricLabel.setText(mainController.getLyricTextOrEmpty(displayLines, 0));
        if(fullscreenCurrentLyricLabel != null) fullscreenCurrentLyricLabel.setText(mainController.getLyricTextOrEmpty(displayLines, 1));
        if(fullscreenNext1LyricLabel != null) fullscreenNext1LyricLabel.setText(mainController.getLyricTextOrEmpty(displayLines, 2));
        if(fullscreenNext2LyricLabel != null) fullscreenNext2LyricLabel.setText(mainController.getLyricTextOrEmpty(displayLines, 3));

        // Now Playing Info
        Song currentSong = playerService.getCurrentSong();
        if (currentSong != null) {
            if (fullscreenNowPlayingTitleLabel != null) {
                fullscreenNowPlayingTitleLabel.setText(currentSong.getTitle());
            }
            if (fullscreenNowPlayingArtistLabel != null) {
                fullscreenNowPlayingArtistLabel.setText(currentSong.getArtist());
            }
        } else {
            if (fullscreenNowPlayingTitleLabel != null) {
                fullscreenNowPlayingTitleLabel.setText("-");
            }
            if (fullscreenNowPlayingArtistLabel != null) {
                fullscreenNowPlayingArtistLabel.setText("-");
            }
        }

        // Bottom Bar Controls
        MediaPlayer.Status status = playerService.getStatus();
        boolean isPlaying = (status == MediaPlayer.Status.PLAYING);
        boolean isPaused = (status == MediaPlayer.Status.PAUSED);
        boolean songLoaded = (playerService.getCurrentSong() != null);

        if(fullscreenPlayPauseButton != null) {
            fullscreenPlayPauseButton.setText(isPlaying ? "Pause" : "Play");
            boolean canPlayFs = isPaused || songLoaded || !queueService.isEmpty();
            fullscreenPlayPauseButton.setDisable(!isPlaying && !canPlayFs);
        }
        if(fullscreenSkipButton != null) fullscreenSkipButton.setDisable(queueService.isEmpty() && !songLoaded);
        if(fullscreenStopButton != null) fullscreenStopButton.setDisable(queueService.isEmpty() && !songLoaded);

        // Slider and Time Labels
        if (songLoaded) {
            if(fullscreenPlaybackSlider != null) {
                fullscreenPlaybackSlider.setMax(playerService.getTotalDurationMillis());
                if (!isUserSeekingFullscreen) fullscreenPlaybackSlider.setValue(playerService.getCurrentTimeMillis());
            }
            if(fullscreenCurrentTimeLabel != null) fullscreenCurrentTimeLabel.setText(mainController.formatTime(playerService.getCurrentTimeMillis()));
            if(fullscreenTotalDurationLabel != null) fullscreenTotalDurationLabel.setText(mainController.formatTime(playerService.getTotalDurationMillis()));
        } else {
            if(fullscreenPlaybackSlider != null) { fullscreenPlaybackSlider.setMax(0); fullscreenPlaybackSlider.setValue(0); }
            if(fullscreenCurrentTimeLabel != null) fullscreenCurrentTimeLabel.setText(mainController.formatTime(0));
            if(fullscreenTotalDurationLabel != null) fullscreenTotalDurationLabel.setText(mainController.formatTime(0));
        }
        
        // Lyric Offset Controls - display the centrally managed offset
        updateLyricOffsetDisplay(mainController.getCurrentSongLiveOffsetMs());
        boolean offsetControlsDisabled = !songLoaded;
        if(fullscreenIncreaseOffsetButton != null) fullscreenIncreaseOffsetButton.setDisable(offsetControlsDisabled);
        if(fullscreenDecreaseOffsetButton != null) fullscreenDecreaseOffsetButton.setDisable(offsetControlsDisabled);
    }

    /**
     * Updates the lyric offset label in this fullscreen view.
     * @param offset The current live offset in milliseconds.
     */
    public void updateLyricOffsetDisplay(int offset) {
        if (fullscreenLyricOffsetLabel != null) {
            fullscreenLyricOffsetLabel.setText(offset + " ms");
        }
    }


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
            if (isUserSeekingFullscreen && fullscreenCurrentTimeLabel != null && mainController != null) {
                fullscreenCurrentTimeLabel.setText(mainController.formatTime(fullscreenPlaybackSlider.getValue()));
            }
        });
        fullscreenPlaybackSlider.setOnMouseReleased(_event -> {
            if (isUserSeekingFullscreen && playerService != null && playerService.getCurrentSong() != null) {
                playerService.seek((long) fullscreenPlaybackSlider.getValue());
            }
            isUserSeekingFullscreen = false;
        });
    }

    // --- FXML Action Handlers ---
    @FXML private void handleExitFullscreenToggle() {
        if (mainController != null) {
            mainController.setAppFullScreen(false); // This button always exits fullscreen
        }
        // No selected state to manage on the button itself.
    }

    @FXML private void handleFullscreenThemeToggle() {
        if (mainController != null) {
            mainController.cycleTheme(); 
        }
    }

    @FXML private void handleFullscreenPlayPause() { if (mainController != null) mainController.handlePlayPause(); }
    @FXML private void handleFullscreenSkip() { if (mainController != null) mainController.handleSkip(); }
    @FXML private void handleFullscreenStop() {
        if (mainController != null) {
            mainController.handleSkip(); // quick bug fix: calling stop directly seems to not update the UI correctly
            mainController.handleStop();
        }
    }

    @FXML private void handleFullscreenIncreaseOffset() {
        if (mainController != null) mainController.adjustLyricOffset(MainController.LYRIC_OFFSET_ADJUSTMENT_STEP);
        // updateUIDisplay(); // MainController's adjustLyricOffset might call this on the active controller
    }
    @FXML private void handleFullscreenDecreaseOffset() {
        if (mainController != null) mainController.adjustLyricOffset(-MainController.LYRIC_OFFSET_ADJUSTMENT_STEP);
        // updateUIDisplay();
    }

    // --- Getter for MainController to sync theme button ---
    @Override
    public Button getThemeButton() {
        return fullscreenThemeButton;
    }
}
