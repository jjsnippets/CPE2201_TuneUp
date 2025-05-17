package controller;

// --- JavaFX Imports ---
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;

// --- Model Imports ---
import model.Song;

// --- Service Imports ---
import service.LyricsService;
import service.PlayerService;
import service.QueueService;

// --- Util Imports ---
// LrcWriter interaction is handled by MainController
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for {@code FullscreenView.fxml}, managing the simplified, immersive user interface
 * for lyric display and playback control.
 * This view is typically activated when the application enters fullscreen mode (SRS 1.2).
 * It handles UI element updates based on service states (Player, Lyrics, Queue)
 * and forwards user actions to the {@link MainController}.
 * Implements {@link MainController.SubController} for standardized interaction
 * with the main application orchestrator.
 */
public class FullscreenViewController implements Initializable, MainController.SubController {

    // --- FXML Injected Fields for Fullscreen View ---
    @FXML private Label fullscreenNextSongLabel;            // Displays the title of the next song in the queue.
    @FXML private Label fullscreenQueueCountLabel;          // Shows count of additional songs in queue.
    @FXML private Label fullscreenPreviousLyricLabel;       // Displays the preceding lyric line.
    @FXML private Label fullscreenCurrentLyricLabel;        // Displays the current, active lyric line.
    @FXML private Label fullscreenNext1LyricLabel;          // Displays the upcoming lyric line.
    @FXML private Label fullscreenNext2LyricLabel;          // Displays the second upcoming lyric line.
    @FXML private Label fullscreenCurrentTimeLabel;         // Shows current playback time of the song.
    @FXML private Slider fullscreenPlaybackSlider;          // Allows user to seek within the current song.
    @FXML private Label fullscreenTotalDurationLabel;       // Shows total duration of the current song.
    @FXML private Label fullscreenNowPlayingTitleLabel;     // Displays title of the currently playing song.
    @FXML private Label fullscreenNowPlayingArtistLabel;    // Displays artist of the currently playing song.
    @FXML private Button fullscreenPlayPauseButton;         // Toggles play/pause for the current song.
    @FXML private Button fullscreenSkipButton;              // Skips to the next song in the queue.
    @FXML private Button fullscreenExitButton;              // Exits fullscreen mode.
    @FXML private Button fullscreenThemeButton;             // Toggles between light and dark themes.
    @FXML private Button fullscreenStopButton;              // Stops playback of the current song.
    @FXML private Button fullscreenIncreaseOffsetButton;    // Increases lyric display offset.
    @FXML private Label fullscreenLyricOffsetLabel;         // Displays the current lyric offset.
    @FXML private Button fullscreenDecreaseOffsetButton;    // Decreases lyric display offset.

    // --- Service Dependencies & Main Controller Reference ---
    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;
    private MainController mainController;
    private Stage primaryStage;

    // --- State ---
    private boolean isUserSeekingFullscreen = false; // Tracks if user is dragging the playback slider.
    // The actual currentSongLiveOffsetMs is managed by MainController.

    /**
     * Initializes the controller after its root element has been completely processed.
     * Sets up initial UI states, like disabling the playback slider.
     *
     * @param location The location used to resolve relative paths for the root object, or null if not known.
     * @param resources The resources used to localize the root object, or null if not known.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("FullscreenViewController initialized.");
        setupFullscreenPlaybackSliderListeners();
        if (fullscreenPlaybackSlider != null) {
            fullscreenPlaybackSlider.setDisable(true); // Initially disable until a song is loaded.
        }
        // Initial button texts and other UI states are set by updateUIDisplay(),
        // which is called after services are injected and bindings are set up.
    }

    // --- Service and Controller Injection Methods ---

    /**
     * Sets the {@link PlayerService} instance for this controller.
     * @param playerService The application's player service.
     */
    public void setPlayerService(PlayerService playerService) { this.playerService = playerService; }

    /**
     * Sets the {@link LyricsService} instance for this controller.
     * @param lyricsService The application's lyrics service.
     */
    public void setLyricsService(LyricsService lyricsService) { this.lyricsService = lyricsService; }

    /**
     * Sets the {@link QueueService} instance for this controller.
     * @param queueService The application's queue service.
     */
    public void setQueueService(QueueService queueService) { this.queueService = queueService; }

    /**
     * Sets the {@link MainController} instance for this controller.
     * @param mainController The main application controller.
     */
    public void setMainController(MainController mainController) { this.mainController = mainController; }

    /**
     * Sets the primary {@link Stage} for this controller.
     * Used to determine fullscreen status for UI updates.
     * @param stage The primary application stage.
     */
    public void setPrimaryStage(Stage stage) { this.primaryStage = stage; }


    /**
     * Called by {@link MainController} after all services and references are injected.
     * Sets up listeners for service property changes (e.g., playback status, current song, lyrics)
     * to keep the fullscreen UI synchronized with the application state.
     * Ensures UI updates are run on the JavaFX Application Thread.
     */
    public void initializeBindingsAndListeners() {
        System.out.println("FullscreenViewController: Initializing service-dependent bindings and listeners...");
        if (playerService == null || lyricsService == null || queueService == null || mainController == null) {
            System.err.println("FullscreenViewController ERROR: Critical dependencies (PlayerService, LyricsService, QueueService, or MainController) are null. UI might not update correctly.");
            return;
        }

        // Add listeners to service properties that affect this view's display.
        // These listeners call updateUIDisplay to refresh relevant parts.
        playerService.statusProperty().addListener((@SuppressWarnings("unused") var _unusedObs, @SuppressWarnings("unused") var _unusedOldStatus, @SuppressWarnings("unused") var _unusedNewStatus) -> Platform.runLater(this::updateUIDisplay));
        playerService.currentTimeProperty().addListener((@SuppressWarnings("unused") var _unusedObs, @SuppressWarnings("unused") var _unusedOldTime, @SuppressWarnings("unused") var _unusedNewTime) -> Platform.runLater(this::updateUIDisplay));
        playerService.totalDurationProperty().addListener((@SuppressWarnings("unused") var _unusedObs, @SuppressWarnings("unused") var _unusedOldDuration, @SuppressWarnings("unused") var _unusedNewDuration) -> Platform.runLater(this::updateUIDisplay));
        
        playerService.currentSongProperty().addListener((@SuppressWarnings("unused") var _unusedObs, @SuppressWarnings("unused") var _unusedOldSong, var newSong) -> Platform.runLater(() -> {
            // When a new song loads, LyricsService should process it (e.g., load lyrics and initial offset).
            // This controller then ensures MainController's central live offset is updated
            // with the initial offset from LyricsService for the new song.
            if (lyricsService != null && mainController != null) { // Ensure services are available
                if (newSong != null) {
                    // It's assumed LyricsService has already loaded the lyrics and determined the
                    // initial offset for 'newSong'. This typically happens because LyricsService
                    // also listens to playerService.currentSongProperty() or is explicitly told
                    // to load lyrics when a song is selected/loaded by PlayerService.
                    mainController.setCurrentSongLiveOffsetMs((int) lyricsService.getInitialLoadedOffsetMs());
                } else {
                    mainController.setCurrentSongLiveOffsetMs(0); // Reset offset if no song is loaded
                }
            }
            updateUIDisplay(); // Perform a general UI refresh for other elements.
        }));

        lyricsService.displayLinesProperty().addListener((@SuppressWarnings("unused") var _unusedObs, @SuppressWarnings("unused") var _unusedOldLines, @SuppressWarnings("unused") var _unusedNewLines) -> Platform.runLater(this::updateUIDisplay));

        if (fullscreenPlaybackSlider != null) {
            // Disable slider if no song is loaded.
            fullscreenPlaybackSlider.disableProperty().bind(playerService.currentSongProperty().isNull());
        }
        updateUIDisplay(); // Perform an initial UI refresh to set correct states.
    }

    /**
     * Central method to refresh all UI elements in the fullscreen view.
     * This should be called whenever a state change (e.g., song change, playback status,
     * time update, lyrics update, queue change) requires the UI to reflect the new state.
     * Ensures that UI updates are performed on the JavaFX Application Thread.
     * Also checks if the view should update based on services being available and stage being in fullscreen.
     * SRS references: Implicitly supports SRS 1.2 (UI display), SRS 2.2 (Playback info).
     */
    @Override
    public void updateUIDisplay() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateUIDisplay);
            return;
        }
        // Avoid updates if critical services are not injected, primary stage is not set,
        // or the application is not currently in fullscreen mode.
        if (playerService == null || lyricsService == null || queueService == null || mainController == null || 
            primaryStage == null || !primaryStage.isFullScreen()) {
            return;
        }

        // --- Top Bar: Next Song and Queue Count (SRS 1.2 Queue Display) ---
        Song nextInQueue = queueService.peekNextSongs(1).stream().findFirst().orElse(null);
        int songsCurrentlyInQueue = queueService.getSize();

        if(fullscreenNextSongLabel != null) {
            fullscreenNextSongLabel.setText(nextInQueue != null ? "Next: " + mainController.formatSongForQueue(nextInQueue) : "Next: -");
        }
        if(fullscreenQueueCountLabel != null) {
            if (nextInQueue != null && songsCurrentlyInQueue > 1) { // If a "next" song is shown and there are more after it
                int additionalSongsCount = songsCurrentlyInQueue - 1;
                fullscreenQueueCountLabel.setText("(+" + additionalSongsCount + " more)");
                fullscreenQueueCountLabel.setVisible(true);
            } else {
                fullscreenQueueCountLabel.setVisible(false); // No additional songs or queue is small/empty
            }
        }

        // --- Lyrics Display (SRS 1.2 Lyric Display) ---
        var displayLines = lyricsService.getDisplayLines();
        if(fullscreenPreviousLyricLabel != null) fullscreenPreviousLyricLabel.setText(mainController.getLyricTextOrEmpty(displayLines, 0));
        if(fullscreenCurrentLyricLabel != null) fullscreenCurrentLyricLabel.setText(mainController.getLyricTextOrEmpty(displayLines, 1));
        if(fullscreenNext1LyricLabel != null) fullscreenNext1LyricLabel.setText(mainController.getLyricTextOrEmpty(displayLines, 2));
        if(fullscreenNext2LyricLabel != null) fullscreenNext2LyricLabel.setText(mainController.getLyricTextOrEmpty(displayLines, 3));

        // --- Now Playing Info (SRS 1.2, SRS 2.2) ---
        Song currentSong = playerService.getCurrentSong();
        if (currentSong != null) {
            if (fullscreenNowPlayingTitleLabel != null) fullscreenNowPlayingTitleLabel.setText(currentSong.getTitle());
            if (fullscreenNowPlayingArtistLabel != null) fullscreenNowPlayingArtistLabel.setText(currentSong.getArtist());
        } else {
            if (fullscreenNowPlayingTitleLabel != null) fullscreenNowPlayingTitleLabel.setText("-");
            if (fullscreenNowPlayingArtistLabel != null) fullscreenNowPlayingArtistLabel.setText("-");
        }

        // --- Bottom Bar Controls (SRS 1.2, SRS 2.2 Playback Controls) ---
        MediaPlayer.Status status = playerService.getStatus();
        boolean isPlaying = (status == MediaPlayer.Status.PLAYING);
        boolean isPaused = (status == MediaPlayer.Status.PAUSED);
        boolean songIsLoaded = (currentSong != null);

        if(fullscreenPlayPauseButton != null) {
            fullscreenPlayPauseButton.setText(isPlaying ? "Pause" : "Play");
            // Enable if playing (to pause), or if paused (to resume), 
            // or if a song can be started (loaded or in queue).
            boolean canStartPlayback = songIsLoaded || !queueService.isEmpty();
            fullscreenPlayPauseButton.setDisable(!(isPlaying || isPaused || canStartPlayback));
        }
        if(fullscreenSkipButton != null) {
            // Disable skip if no song is loaded AND queue is empty.
            fullscreenSkipButton.setDisable(!songIsLoaded && queueService.isEmpty());
        }
        if(fullscreenStopButton != null) {
            // Disable stop if no song is loaded AND queue is empty.
            fullscreenStopButton.setDisable(!songIsLoaded && queueService.isEmpty());
        }

        // --- Playback Slider and Time Labels (SRS 1.2, SRS 2.2) ---
        if (songIsLoaded) {
            if(fullscreenPlaybackSlider != null) {
                fullscreenPlaybackSlider.setMax(playerService.getTotalDurationMillis());
                if (!isUserSeekingFullscreen) { // Only update slider value if user isn't dragging it
                    fullscreenPlaybackSlider.setValue(playerService.getCurrentTimeMillis());
                }
            }
            if(fullscreenCurrentTimeLabel != null) fullscreenCurrentTimeLabel.setText(mainController.formatTime(playerService.getCurrentTimeMillis()));
            if(fullscreenTotalDurationLabel != null) fullscreenTotalDurationLabel.setText(mainController.formatTime(playerService.getTotalDurationMillis()));
        } else { // No song loaded
            if(fullscreenPlaybackSlider != null) { 
                fullscreenPlaybackSlider.setMax(0); 
                fullscreenPlaybackSlider.setValue(0); 
            }
            if(fullscreenCurrentTimeLabel != null) fullscreenCurrentTimeLabel.setText(mainController.formatTime(0));
            if(fullscreenTotalDurationLabel != null) fullscreenTotalDurationLabel.setText(mainController.formatTime(0));
        }
        
        // --- Lyric Offset Controls (SRS 1.2 Lyric Timing Adjustment) ---
        // Display the centrally managed offset from MainController.
        updateLyricOffsetDisplay(mainController.getCurrentSongLiveOffsetMs()); 
        boolean offsetControlsDisabled = !songIsLoaded; // Disable if no song loaded
        if(fullscreenIncreaseOffsetButton != null) fullscreenIncreaseOffsetButton.setDisable(offsetControlsDisabled);
        if(fullscreenDecreaseOffsetButton != null) fullscreenDecreaseOffsetButton.setDisable(offsetControlsDisabled);
    }

    /**
     * Updates the lyric offset label in this fullscreen view to display the current
     * live lyric timing offset.
     * @param offset The current live offset in milliseconds, obtained from {@link MainController}.
     */
    public void updateLyricOffsetDisplay(int offset) {
        if (fullscreenLyricOffsetLabel != null) {
            fullscreenLyricOffsetLabel.setText(String.format("%d ms", offset));
        }
    }

    /**
     * Sets up listeners for the playback slider to handle user seeking actions.
     * Updates {@code isUserSeekingFullscreen} state and seeks in {@link PlayerService}
     * on mouse release. The current time label is updated during drag.
     */
    private void setupFullscreenPlaybackSliderListeners() {
        if (fullscreenPlaybackSlider == null) return;

        fullscreenPlaybackSlider.setOnMousePressed(event -> {
            if (playerService != null && playerService.getCurrentSong() != null) {
                isUserSeekingFullscreen = true;
            } else {
                // If no song, consume event to prevent slider interaction.
                event.consume(); 
            }
        });

        fullscreenPlaybackSlider.setOnMouseDragged((@SuppressWarnings("unused") var _unusedDragEvent) -> {
            if (isUserSeekingFullscreen && fullscreenCurrentTimeLabel != null && mainController != null) {
                // Update time label live as user drags slider
                fullscreenCurrentTimeLabel.setText(mainController.formatTime(fullscreenPlaybackSlider.getValue()));
            }
        });

        fullscreenPlaybackSlider.setOnMouseReleased((@SuppressWarnings("unused") var _unusedReleaseEvent) -> {
            if (isUserSeekingFullscreen && playerService != null && playerService.getCurrentSong() != null) {
                playerService.seek((long) fullscreenPlaybackSlider.getValue());
            }
            isUserSeekingFullscreen = false; // Reset seeking state
        });
    }

    // --- FXML Action Handlers ---

    /**
     * Handles the action to exit fullscreen mode.
     * Delegates to {@link MainController#setAppFullScreen(boolean)}.
     * SRS 1.2: Fullscreen capability.
     */
    @FXML 
    private void handleExitFullscreenToggle() {
        if (mainController != null) {
            mainController.setAppFullScreen(false); // This button always exits fullscreen.
        }
    }

    /**
     * Handles the action to cycle to the next theme (e.g., light/dark).
     * Delegates to {@link MainController#cycleTheme()}.
     * SRS 1.2: Basic themes.
     */
    @FXML 
    private void handleFullscreenThemeToggle() {
        if (mainController != null) {
            mainController.cycleTheme(); 
        }
    }

    /**
     * Handles the play/pause action for media playback.
     * Delegates to {@link MainController#handlePlayPause()}.
     * SRS 1.2, 2.2: Playback controls.
     */
    @FXML 
    private void handleFullscreenPlayPause() { 
        if (mainController != null) mainController.handlePlayPause(); 
    }

    /**
     * Handles the skip action to play the next song in the queue.
     * Delegates to {@link MainController#handleSkip()}.
     * SRS 1.2, 2.2: Playback controls, Song queuing.
     */
    @FXML 
    private void handleFullscreenSkip() { 
        if (mainController != null) mainController.handleSkip(); 
    }

    /**
     * Handles the stop action for media playback.
     * Delegates to {@link MainController#handleStop()}.
     * SRS 1.2, 2.2: Playback controls.
     */
    @FXML 
    private void handleFullscreenStop() {
        if (mainController != null) {
            mainController.handleStop();
        }
    }

    /**
     * Handles the action to increase the lyric display offset.
     * Delegates to {@link MainController#adjustLyricOffset(int)}.
     * SRS 1.2: Manual lyric timing adjustment.
     */
    @FXML 
    private void handleFullscreenIncreaseOffset() {
        if (mainController != null) {
            mainController.adjustLyricOffset(MainController.LYRIC_OFFSET_ADJUSTMENT_STEP);
        }
    }

    /**
     * Handles the action to decrease the lyric display offset.
     * Delegates to {@link MainController#adjustLyricOffset(int)}.
     * SRS 1.2: Manual lyric timing adjustment.
     */
    @FXML 
    private void handleFullscreenDecreaseOffset() {
        if (mainController != null) {
            mainController.adjustLyricOffset(-MainController.LYRIC_OFFSET_ADJUSTMENT_STEP);
        }
    }

    // --- SubController Interface Implementation ---

    /**
     * Returns the theme toggle button for this view.
     * Used by {@link MainController} to synchronize theme button states across views.
     * @return The {@link Button} used for toggling themes.
     */
    @Override
    public Button getThemeButton() {
        return fullscreenThemeButton;
    }

    /**
     * Provides access to the label displaying the current lyric offset.
     * This allows the {@link MainController} to update it directly, for example, when resetting the offset.
     * @return The {@link Label} used for displaying the lyric offset, or {@code null} if not initialized.
     */
    public Label getFullscreenLyricOffsetLabel() {
        return fullscreenLyricOffsetLabel;
    }

    /**
     * Returns the song currently selected in a library view, if one exists in this controller.
     * FullscreenView does not have a library selection interface, so this implementation
     * always returns null.
     * This method is part of the {@link MainController.SubController} interface.
     *
     * @return Always null, as FullscreenViewController does not feature a song library selection.
     */
    @Override
    public Song getSelectedSongFromLibrary() {
        return null; // Fullscreen view does not have a library to select from.
    }
}
