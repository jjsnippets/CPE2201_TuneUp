package controller;

// --- JavaFX Imports ---
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaPlayer;      // For MediaPlayer.Status
import javafx.stage.Stage;

// --- Model Imports ---
import model.LyricLine;                     // For getLyricTextOrEmpty utility
import model.Song;                          // For loadAndPlaySong utility

// --- Service Imports ---
import service.LyricsService;
import service.PlayerService;
import service.QueueService;

// --- Util Imports ---
import util.LrcWriter;                      // For saving offset
import java.io.IOException;                 // For LrcWriter exception
import java.net.URL;
import java.util.List;                      // For getLyricTextOrEmpty utility
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

/**
 * Main orchestrating controller for the TuneUp application.
 * This class manages the primary application stage, including transitions between
 * Normal and Fullscreen views (SRS 1.2). It is responsible for initializing and injecting
 * core services ({@link PlayerService}, {@link LyricsService}, {@link QueueService}) into
 * the respective sub-controllers ({@link NormalViewController}, {@link FullscreenViewController}).
 *
 * It also handles global UI actions such as theme cycling (SRS 1.2 "basic themes"),
 * media playback controls (play, pause, stop, skip - SRS 1.2, 2.2), song queuing (SRS 1.2),
 * and lyric timing adjustments (SRS 1.2). It acts as a central hub for communication
 * and state synchronization between different parts of the UI.
 * Implements {@link Initializable} for FXML loading.
 */
public class MainController implements Initializable {

    @FXML private StackPane rootStackPane;
    @FXML private Node normalView;
    @FXML private NormalViewController normalViewController;
    @FXML private Node fullscreenView;
    @FXML private FullscreenViewController fullscreenViewController;

    private PlayerService playerService;
    private LyricsService lyricsService;
    private QueueService queueService;
    private Stage primaryStage;

    // Shared state for lyric offset, managed by this central controller. SRS 1.2: manual timing adjustment.
    private int currentSongLiveOffsetMs = 0;
    public static final int LYRIC_OFFSET_ADJUSTMENT_STEP = 100; // Milliseconds
    private boolean isDarkMode = false;                         // Default theme is light.

    /**
     * Initializes the controller after its root element has been completely processed.
     * Sets the initial visibility of the normal and fullscreen views.
     *
     * @param location The location used to resolve relative paths for the root object, or null if not known.
     * @param resources The resources used to localize the root object, or null if not known.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("Main Orchestrating Controller initialized.");
        if (normalView != null) normalView.setVisible(true);
        if (fullscreenView != null) fullscreenView.setVisible(false);
    }

    /**
     * Sets the primary stage for the application.
     * Attaches a listener to the stage's fullScreenProperty to automatically
     * switch between normal and fullscreen views (implements SRS 1.2 fullscreen requirement).
     *
     * @param stage The primary {@link Stage} of the application.
     */
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        if (this.primaryStage != null) {
            this.primaryStage.fullScreenProperty().addListener((@SuppressWarnings("unused") var _observable, @SuppressWarnings("unused") var _oldValue, var isFullScreen) -> {
                if (isFullScreen) {
                    // Entering fullscreen mode
                    if(normalView != null) normalView.setVisible(false);
                    if(fullscreenView != null) fullscreenView.setVisible(true);
                    if (fullscreenViewController != null) {
                        syncThemeToggleStates(normalViewController, fullscreenViewController);
                        fullscreenViewController.updateUIDisplay(); // Ensure UI is current
                    }
                } else {
                    // Exiting fullscreen mode
                    if(fullscreenView != null) fullscreenView.setVisible(false);
                    if(normalView != null) normalView.setVisible(true);
                    if (normalViewController != null) {
                        syncThemeToggleStates(fullscreenViewController, normalViewController);
                        normalViewController.updateUIDisplay();     // Ensure UI is current
                    }
                }
            });
        }
    }
    

    /**
     * Synchronizes the theme toggle button's text between two sub-controllers.
     * This ensures that when switching views (e.g., normal to fullscreen), the theme
     * button in the newly visible view correctly reflects the current theme state.
     *
     * @param source The {@link SubController} from which to get the current theme button state.
     * @param target The {@link SubController} to which to apply the theme button state.
     */
    private void syncThemeToggleStates(SubController source, SubController target) {
        if (source != null && source.getThemeButton() != null &&
            target != null && target.getThemeButton() != null) {
            // For Buttons, we primarily sync the text that indicates the next state (e.g., "Dark Mode" or "Light Mode").
            target.getThemeButton().setText(source.getThemeButton().getText());
        }
    }

    /**
     * Sets the {@link PlayerService} instance.
     * @param playerService The player service.
     */
    public void setPlayerService(PlayerService playerService) { this.playerService = playerService; }
    /**
     * Sets the {@link LyricsService} instance.
     * @param lyricsService The lyrics service.
     */
    public void setLyricsService(LyricsService lyricsService) { this.lyricsService = lyricsService; }
    /**
     * Sets the {@link QueueService} instance.
     * @param queueService The queue service.
     */
    public void setQueueService(QueueService queueService) { this.queueService = queueService; }

    /**
     * Initializes sub-controllers (NormalView and FullscreenView) by injecting necessary services
     * and setting up main controller references. This method is critical for the proper functioning
     * of the sub-views. It also configures the global end-of-media handler for automatic
     * playback of the next song in the queue (SRS 1.2).
     * Applies the initial theme settings.
     */
    public void initializeSubControllersAndServices() {
        System.out.println("MainController: Initializing sub-controllers and injecting services...");
        if (playerService == null || lyricsService == null || queueService == null) {
            System.err.println("MainController ERROR: Core services are null. Cannot initialize sub-controllers.");
            showErrorDialog("Critical Error", "Service Initialization Failed",
                            "Core services (Player, Lyrics, Queue) could not be initialized. Sub-views may not function.");
            return;
        }

        // Initialize NormalViewController
        if (normalViewController != null) {
            normalViewController.setPlayerService(this.playerService);
            normalViewController.setLyricsService(this.lyricsService);
            normalViewController.setQueueService(this.queueService);
            normalViewController.setMainController(this);
            normalViewController.setPrimaryStage(this.primaryStage);
            normalViewController.initializeBindingsAndListeners();
            System.out.println("NormalViewController initialized and services injected.");
        } else {
            System.err.println("MainController WARNING: NormalViewController is null. Normal view features will be unavailable.");
        }

        // Initialize FullscreenViewController
        if (fullscreenViewController != null) {
            fullscreenViewController.setPlayerService(this.playerService);
            fullscreenViewController.setLyricsService(this.lyricsService);
            fullscreenViewController.setQueueService(this.queueService);
            fullscreenViewController.setMainController(this);
            fullscreenViewController.setPrimaryStage(this.primaryStage);
            fullscreenViewController.initializeBindingsAndListeners();
            System.out.println("FullscreenViewController initialized and services injected.");
        } else {
            System.err.println("MainController WARNING: FullscreenViewController is null. Fullscreen view features will be unavailable.");
        }

        // Set the end of media handler for PlayerService to manage song queue (SRS 1.2)
        if (playerService != null) {
            playerService.setOnEndOfMediaHandler(() -> {
                System.out.println("MainController: EndOfMediaHandler triggered.");
                // Automatically play the next song from the queue.
                Song nextSong = queueService.getNextSong(); // This also removes the song from the queue.
                if (nextSong != null) {
                    System.out.println("MainController: Playing next song from queue: " + nextSong.getTitle());
                    playerService.loadSong(nextSong, true); // Autoplay next song
                } else {
                    System.out.println("MainController: Queue is empty. Clearing player to 'no song' state.");
                    playerService.loadSong(null, false); // Load null to clear player, stopping playback.
                }
            });
        }

        // Apply initial theme based on the default state (isDarkMode = false -> light theme)
        applyTheme(isDarkMode);     // Apply initial theme (SRS 1.2 "basic themes")
        updateThemeButtonTexts();   // Set initial button texts

        // Update initial UI state for the currently visible controller.
        // Deferred to ensure all initializations are complete before UI updates.
        Platform.runLater(() -> {
            if (primaryStage != null && primaryStage.isFullScreen() && fullscreenViewController != null) {
                fullscreenViewController.updateUIDisplay();
            } else if (normalViewController != null) {
                normalViewController.updateUIDisplay();
            }
        });
    }

    // --- Public methods for Sub-Controllers to call ---

    /**
     * Sets the application to fullscreen or windowed mode.
     * Corresponds to SRS 1.2 requirement for fullscreen capability.
     * @param fullScreen true to enter fullscreen, false to exit.
     */
    public void setAppFullScreen(boolean fullScreen) {
        if (primaryStage != null) {
            primaryStage.setFullScreen(fullScreen);
            // Listener in setPrimaryStage handles view switching.
        }
    }

    /**
     * Cycles between light and dark themes for the application.
     * Corresponds to SRS 1.2 requirement for "basic themes".
     */
    public void cycleTheme() {
        isDarkMode = !isDarkMode;
        applyTheme(isDarkMode);
        updateThemeButtonTexts(); // Ensure buttons in both views reflect the new theme state.
    }

    /**
     * Updates the text of the theme toggle buttons in both normal and fullscreen views
     * to reflect the current theme (e.g., shows "Dark Mode" if current is light, and vice-versa).
     */
    private void updateThemeButtonTexts(){
        String buttonText = isDarkMode ? "Light Mode" : "Dark Mode"; // Text indicates action to switch
        if (normalViewController != null && normalViewController.getThemeButton() != null) {
            normalViewController.getThemeButton().setText(buttonText);
        }
        if (fullscreenViewController != null && fullscreenViewController.getThemeButton() != null) {
            fullscreenViewController.getThemeButton().setText(buttonText);
        }
    }
    
    /**
     * Applies the selected theme (dark or light) to the root pane of the application.
     * It adds or removes the "dark-mode" CSS class from the rootStackPane.
     * The actual styling is defined in CSS files (e.g., dark-theme.css).
     *
     * @param newIsDarkMode true to apply dark mode, false to apply light mode.
     */
    private void applyTheme(boolean newIsDarkMode) {
        if (rootStackPane == null) {
            System.err.println("Theme application: Root StackPane is null. Cannot apply theme.");
            return;
        }
        String darkClassName = "dark-mode"; // CSS class defined for dark theme
        ObservableList<String> styleClasses = rootStackPane.getStyleClass();

        if (newIsDarkMode) {
            if (!styleClasses.contains(darkClassName)) {
                styleClasses.add(darkClassName);
            }
        } else {
            styleClasses.remove(darkClassName);
        }
        System.out.println("Theme " + (newIsDarkMode ? "Dark" : "Light") + " applied to rootStackPane.");
    }


    /**
     * Handles the global play/pause action.
     * Toggles playback state of the current song via {@link PlayerService}.
     * If the player is stopped or in an initial state, it attempts to play the current song if one is loaded,
     * otherwise, it attempts to play the next song from the queue.
     * Corresponds to SRS 1.2 and 2.2 for media playback controls.
     */
    public void handlePlayPause() {
        if (playerService == null) {
            System.err.println("MainController: handlePlayPause - PlayerService is null.");
            return;
        }

        MediaPlayer.Status currentStatus = playerService.getStatus();
        Song librarySelectedSong = null;

        // Determine the active sub-controller and get the selected song from its library view
        SubController activeSubController = null;
        if (normalView != null && normalView.isVisible() && normalViewController != null) {
            activeSubController = normalViewController;
        } else if (fullscreenView != null && fullscreenView.isVisible() && fullscreenViewController != null) {
            activeSubController = fullscreenViewController;
        }

        if (activeSubController != null) {
            librarySelectedSong = activeSubController.getSelectedSongFromLibrary();
        }

        if (currentStatus == MediaPlayer.Status.PLAYING) {
            playerService.pause();
        } else if (currentStatus == MediaPlayer.Status.PAUSED) {
            // If paused, always resume the currently loaded and paused song.
            playerService.play();
        } else {
            // Covers STOPPED, READY, UNKNOWN, HALTED, STALLED etc.
            // Priority 1: If a song is selected in the library view of the active controller,
            // load and play that song.
            if (librarySelectedSong != null) {
                System.out.println("MainController: handlePlayPause - Playing song selected from library: " + librarySelectedSong.getTitle());
                playerService.loadSong(librarySelectedSong, true); // Load and autoplay.
            }
            // Priority 2: If no song is selected in the library, but a song is already loaded in PlayerService
            // (e.g., it was playing and then stopped, or was the last played from queue), play that.
            else if (playerService.getCurrentSong() != null) {
                System.out.println("MainController: handlePlayPause - Attempting to play current loaded song in PlayerService: " + playerService.getCurrentSong().getTitle());
                playerService.play(); // PlayerService.play() should handle starting from READY/STOPPED states.
            }
            // Priority 3: If no song selected in library and no song loaded in PlayerService, play from queue.
            else if (queueService != null && !queueService.isEmpty()) {
                Song nextSong = queueService.getNextSong(); // Retrieves and removes the next song.
                if (nextSong != null) {
                    System.out.println("MainController: handlePlayPause - No library selection or current song, playing next from queue: " + nextSong.getTitle());
                    playerService.loadSong(nextSong, true); // Load and autoplay.
                } else {
                    System.err.println("MainController: handlePlayPause - QueueService reported non-empty, but failed to retrieve next song.");
                }
            }
            // Priority 4: No song selected, no song loaded, and queue is empty.
            else {
                System.out.println("MainController: handlePlayPause - No song selected, no song loaded, and queue is empty. Nothing to play.");
            }
        }
        // UI state (e.g., play/pause button icon) is typically updated by listeners in sub-controllers
        // reacting to PlayerService state changes.
    }

    /**
     * Handles the global stop action.
     * Stops playback of the current song via {@link PlayerService}, clears lyrics from {@link LyricsService},
     * clears the song queue in {@link QueueService}, and resets any live lyric offset.
     * This action effectively resets the application to a "no song playing, empty queue" state,
     * as per SRS requirements for a comprehensive stop functionality.
     * Corresponds to SRS 1.2 and 2.2 for media playback controls.
     */
    public void handleStop() {
        System.out.println("MainController: Stop action initiated - resetting to no song state and clearing queue.");
        if (playerService != null) {
            playerService.stop(); // Stops playback
            playerService.loadSong(null, false); // Clears the current song from the player
        } else {
            System.err.println("MainController: handleStop - PlayerService is null.");
        }

        if (lyricsService != null) {
            lyricsService.clearLyrics(); // Clears lyrics and resets display
        } else {
            System.err.println("MainController: handleStop - LyricsService is null.");
        }

        if (queueService != null) {
            queueService.clearQueue(); // Clears all songs from the playback queue
            System.out.println("MainController: Song queue cleared.");
        } else {
            System.err.println("MainController: handleStop - QueueService is null, cannot clear queue.");
        }

        // Reset the live lyric offset for any future song
        currentSongLiveOffsetMs = 0;

        // UI state (e.g., now playing info, lyric display, queue display, offset labels)
        // is updated by listeners in sub-controllers reacting to service state changes.
        // Explicitly trigger a UI update in the active controller to ensure all elements reflect the reset.
        Platform.runLater(() -> {
            SubController activeController = getActiveSubController();
            if (activeController != null) {
                activeController.updateUIDisplay(); // General UI update

                // Specifically update lyric offset labels if the controllers and labels exist
                if (activeController instanceof FullscreenViewController) {
                    FullscreenViewController fsController = (FullscreenViewController) activeController;
                    if (fsController.getFullscreenLyricOffsetLabel() != null) {
                        fsController.getFullscreenLyricOffsetLabel().setText("Offset: 0ms");
                    }
                } else if (activeController instanceof NormalViewController) {
                    NormalViewController nvController = (NormalViewController) activeController;
                    if (nvController.getLyricOffsetLabel() != null) {
                        nvController.getLyricOffsetLabel().setText("0 ms");
                    }
                }
            }
        });
        System.out.println("MainController: Playback stopped, lyrics cleared, queue cleared, and offset reset.");
    }

    /**
     * Handles the global skip action.
     * Stops the current song and attempts to play the next song from the {@link QueueService}.
     * Corresponds to SRS 1.2 and 2.2 for media playback controls and song queuing.
     */
    public void handleSkip() {
        if (playerService == null || queueService == null) {
            System.err.println("handleSkip: PlayerService or QueueService is null.");
            return;
        }

        System.out.println("MainController: Skip action initiated.");
        MediaPlayer.Status previousStatus = playerService.getStatus(); // Remember current status

        // Attempt to get the next song from the queue. This will remove it.
        Song nextSong = queueService.getNextSong();

        if (nextSong != null) {
            System.out.println("MainController: Skipping to next song: " + nextSong.getTitle());
            // Load the song. If the previous state was PLAYING, autoplay. Otherwise, load paused.
            boolean autoPlay = (previousStatus == MediaPlayer.Status.PLAYING);
            playerService.loadSong(nextSong, autoPlay); 
        } else {
            System.out.println("MainController: Queue is empty. Stopping current song and clearing player.");
            playerService.loadSong(null, false); // No next song, clear the player
        }
    }

    /**
     * Plays the next song in the queue. This method is typically called by the
     * end-of-media handler or explicitly by user action (like skip).
     * Corresponds to SRS 1.2 for song queuing.
     *
     * @param isAutoPlayContext true if called in an autoplay context (e.g., end of media),
     *                          false if initiated by direct user action that might not imply immediate play.
     *                          Currently, this method always attempts to autoplay if a song is found.
     */
    public void playNextSong(boolean isAutoPlayContext) { // isAutoPlayContext currently not changing behavior, but kept for potential future use
        if (playerService == null || queueService == null) {
            System.err.println("playNextSong: PlayerService or QueueService is null.");
            return;
        }
        Song songToPlay = null;
        if (!queueService.isEmpty()) {
            songToPlay = queueService.getNextSong(); // Retrieves and removes the next song
        }

        if (songToPlay == null) {
            System.out.println("MainController: No next song in queue to play.");
            playerService.loadSong(null, false); // Clear player if queue is empty
            return;
        }
        
        System.out.println("MainController: Playing next song: " + songToPlay.getTitle());
        playerService.loadSong(songToPlay, true); // Autoplay the next song
    }

    /**
     * Loads and plays the specified song using the {@link PlayerService}.
     * This is a direct command to play a song, often selected from the library.
     * Corresponds to SRS 1.2 and 2.2 for media playback.
     *
     * @param song The {@link Song} to load and play.
     */
    public void loadAndPlaySong(Song song) {
        if (playerService == null) {
            System.err.println("loadAndPlaySong: PlayerService is null.");
            return;
        }
        if (song == null) {
            System.err.println("loadAndPlaySong: Attempted to load a null song.");
            playerService.loadSong(null, false); // Clear player
            return;
        }
        // LyricsService loading and currentSongLiveOffsetMs initialization
        // are now triggered by the PlayerService.currentSongProperty listener
        // in both NormalViewController and FullscreenViewController (via updateUIDisplay)
        // after PlayerService sets its current song. This ensures offset is reset/loaded per song.
        System.out.println("MainController: Loading and playing song: " + song.getTitle());
        boolean loadingOk = playerService.loadSong(song, true); // Request auto-play
        if (!loadingOk) {
            showErrorDialog("Playback Error", "Could not play song", "Failed to load or play: " + song.getTitle());
        }
    }

    /**
     * Adjusts the live lyric display offset for the currently playing song by a specified amount.
     * The new offset is applied to the {@link LyricsService} for immediate visual feedback
     * and then saved to the .lrc file using {@link LrcWriter}.
     * Corresponds to SRS 1.2 for manual lyric timing adjustment.
     *
     * @param amount The amount in milliseconds to adjust the offset by (positive to delay lyrics, negative to advance).
     */
    public void adjustLyricOffset(int amount) {
        Song currentSongForOffset = (playerService != null) ? playerService.getCurrentSong() : null;
        if (playerService == null || currentSongForOffset == null || lyricsService == null) {
            System.err.println("adjustLyricOffset: Cannot adjust offset. Player, current song, or LyricsService is null.");
            return;
        }
        this.currentSongLiveOffsetMs += amount;
        
        // Update offset label in the active view immediately
        if (normalViewController != null && normalView.isVisible()) {
            normalViewController.updateLyricOffsetDisplay(this.currentSongLiveOffsetMs);
        }
        if (fullscreenViewController != null && fullscreenView.isVisible()) {
            fullscreenViewController.updateLyricOffsetDisplay(this.currentSongLiveOffsetMs);
        }

        // Apply the new offset to the lyrics display
        lyricsService.updateCurrentDisplayLines(playerService.getCurrentTimeMillis(), this.currentSongLiveOffsetMs);

        // Persist the new total offset to the .lrc file
        String lyricsFilePath = currentSongForOffset.getLyricsFilePath();
        if (lyricsFilePath != null && !lyricsFilePath.isBlank()) {
            try {
                LrcWriter.saveOffsetToLrcFile(lyricsFilePath, this.currentSongLiveOffsetMs);
                System.out.println("Lyric offset (" + this.currentSongLiveOffsetMs + "ms) saved for: " + currentSongForOffset.getTitle());
            } catch (IOException e) {
                System.err.println("Failed to save lyric offset to LRC file: " + lyricsFilePath + " - " + e.getMessage());
                showErrorDialog("Offset Save Error", "Could not save lyric offset",
                                "Failed to write offset to file: " + lyricsFilePath);
            }
        } else {
            System.err.println("Cannot save lyric offset: Lyrics file path is missing for song: " + currentSongForOffset.getTitle());
        }
    }
    
    /**
     * Gets the current live lyric offset in milliseconds for the active song.
     * This offset is the sum of the original file offset and any live adjustments.
     * @return The current live lyric offset in milliseconds.
     */
    public int getCurrentSongLiveOffsetMs() {
        return currentSongLiveOffsetMs;
    }
    
    /**
     * Sets the initial live lyric offset when a new song is loaded.
     * This is typically called by sub-controllers when they detect a new song
     * and have retrieved its stored offset from {@link LyricsService}.
     * @param offset The initial lyric offset in milliseconds for the current song.
     */
    public void setCurrentSongLiveOffsetMs(int offset) {
        this.currentSongLiveOffsetMs = offset;
         // Update offset labels in sub-controllers if they are visible
        if (normalViewController != null && normalView != null && normalView.isVisible()) {
            normalViewController.updateLyricOffsetDisplay(this.currentSongLiveOffsetMs);
        }
        if (fullscreenViewController != null && fullscreenView != null && fullscreenView.isVisible()) {
            fullscreenViewController.updateLyricOffsetDisplay(this.currentSongLiveOffsetMs);
        }
    }


    // --- Utility Methods (public for sub-controllers) ---

    /**
     * Formats a Song object for display in UI elements like queue labels.
     * @param song The {@link Song} object.
     * @return A string representation (e.g., "Title - Artist"), or "-" if song is null.
     */
    public String formatSongForQueue(Song song) { // Made public for use by sub-controllers
        if (song == null) return "-"; // Placeholder for empty queue slots or null song
        String title = song.getTitle() != null ? song.getTitle() : "Unknown Title";
        String artist = song.getArtist() != null ? song.getArtist() : "Unknown Artist";
        return title + " - " + artist;
    }

    /**
     * Formats time in milliseconds to a "minutes:seconds" string.
     * @param millis The time in milliseconds.
     * @return Formatted time string (e.g., "3:45"), or "0:00" for invalid input.
     */
    public String formatTime(long millis) {
        if (millis < 0 || millis == Long.MAX_VALUE) return "0:00"; // Handle invalid or uninitialized times
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Formats time in milliseconds (double) to a "minutes:seconds" string.
     * @param millis The time in milliseconds.
     * @return Formatted time string, or "0:00" for invalid input.
     */
    public String formatTime(double millis) {
        if (Double.isNaN(millis) || Double.isInfinite(millis) || millis < 0) return "0:00";
        return formatTime((long) millis);
    }

    /**
     * Safely retrieves lyric text from a list of {@link LyricLine} objects.
     * @param lines The list of lyric lines.
     * @param index The index of the desired line.
     * @return The lyric text if the index is valid, otherwise an empty string.
     */
    public String getLyricTextOrEmpty(List<LyricLine> lines, int index) {
        if (lines != null && index >= 0 && index < lines.size()) {
            LyricLine line = lines.get(index);
            return (line != null && line.getText() != null) ? line.getText() : "";
        }
        return "";
    }
    
    /**
     * Displays a standardized error dialog to the user.
     * This method should be called for errors that the user needs to be aware of.
     *
     * @param title The title for the error dialog window.
     * @param header The header text for the error dialog (can be null).
     * @param content The detailed content/message of the error.
     */
    private void showErrorDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header); // Header can be null if not needed.
        alert.setContentText(content);
        alert.getDialogPane().setPrefSize(480, 200); // Example size
        alert.showAndWait(); // Show and wait for user to close it.
    }
    
    /**
     * Interface for sub-controllers (like {@link NormalViewController} and {@link FullscreenViewController})
     * to expose common UI elements or methods that the {@link MainController} might need to interact with
     * in a generalized way, such as retrieving the theme toggle button for synchronization.
     */
    public interface SubController {
        /**
         * Gets the theme toggle button from the sub-controller.
         * @return The {@link Button} used for toggling themes, or null if not available.
         */
        Button getThemeButton();

        /**
         * Retrieves the song currently selected in the sub-controller's library view, if any.
         * This allows the MainController to prioritize playback of a song selected
         * directly in a library view.
         * <p>
         * Implementations should return the selected {@link Song} if a library view
         * with selection capability is active and a song is selected.
         * If the sub-controller does not have a library view or no song is selected,
         * it should return {@code null}. For example, {@code FullscreenViewController}
         * might return {@code null} if it doesn't display a selectable library.
         * </p>
         * Corresponds to SRS 1.2, 2.2 (implicit for playback prioritization).
         * @return The {@link Song} selected in the library, or {@code null} if none.
         */
        Song getSelectedSongFromLibrary();

        /**
         * Called by MainController to instruct the sub-view to refresh its entire UI display.
         * This is useful when the view becomes active or significant global state changes occur.
         */
        void updateUIDisplay();
    }

    private SubController getActiveSubController() {
        if (normalView != null && normalView.isVisible() && normalViewController != null) {
            return normalViewController;
        } else if (fullscreenView != null && fullscreenView.isVisible() && fullscreenViewController != null) {
            return fullscreenViewController;
        }
        return null;
    }
}