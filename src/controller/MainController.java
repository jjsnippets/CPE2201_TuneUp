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
import javafx.scene.media.MediaPlayer; // For MediaPlayer.Status
import javafx.stage.Stage;

// --- Model Imports ---
import model.LyricLine; // For getLyricTextOrEmpty utility
import model.Song;    // For loadAndPlaySong utility

// --- Service Imports ---
import service.LyricsService;
import service.PlayerService;
import service.QueueService;

// --- Util Imports ---
import util.LrcWriter; // For saving offset
import java.io.IOException; // For LrcWriter exception
import java.net.URL;
import java.util.List; // For getLyricTextOrEmpty utility
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

/**
 * Main orchestrating controller for the TuneUp application.
 * Manages the primary stage, switches between Normal and Fullscreen views,
 * injects services into sub-controllers, and coordinates global actions.
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

    // Shared state for lyric offset, managed by this central controller
    private int currentSongLiveOffsetMs = 0;
    public static final int LYRIC_OFFSET_ADJUSTMENT_STEP = 100;
    private boolean isDarkMode = false;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("Main Orchestrating Controller initialized.");
        if (normalView != null) normalView.setVisible(true);
        if (fullscreenView != null) fullscreenView.setVisible(false);
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        if (this.primaryStage != null) {
            this.primaryStage.fullScreenProperty().addListener((obs, oldVal, isFullScreen) -> {
                if (isFullScreen) {
                    if(normalView != null) normalView.setVisible(false);
                    if(fullscreenView != null) fullscreenView.setVisible(true);
                    if (fullscreenViewController != null) {
                        syncThemeToggleStates(normalViewController, fullscreenViewController);
                        fullscreenViewController.updateUIDisplay();
                    }
                } else { // Exited fullscreen
                    if(fullscreenView != null) fullscreenView.setVisible(false);
                    if(normalView != null) normalView.setVisible(true);
                    if (normalViewController != null) {
                        syncThemeToggleStates(fullscreenViewController, normalViewController);
                        normalViewController.updateUIDisplay();
                    }
                }
            });
        }
    }
    

    private void syncThemeToggleStates(SubController source, SubController target) {
        if (source != null && source.getThemeButton() != null &&
            target != null && target.getThemeButton() != null) {
            // For Buttons, we primarily sync the text that indicates the next state.
            target.getThemeButton().setText(source.getThemeButton().getText());
        }
    }

    public void setPlayerService(PlayerService playerService) { this.playerService = playerService; }
    public void setLyricsService(LyricsService lyricsService) { this.lyricsService = lyricsService; }
    public void setQueueService(QueueService queueService) { this.queueService = queueService; }

    public void initializeSubControllersAndServices() {
        System.out.println("MainController: Initializing sub-controllers and injecting services...");
        if (playerService == null || lyricsService == null || queueService == null) {
            System.err.println("MainController ERROR: Core services are null.");
            showErrorDialog("Critical Error", "Service Initialization Failed", "Core services could not be initialized.");
            return;
        }

        if (normalViewController != null) {
            normalViewController.setPlayerService(this.playerService);
            normalViewController.setLyricsService(this.lyricsService);
            normalViewController.setQueueService(this.queueService);
            normalViewController.setMainController(this);
            normalViewController.setPrimaryStage(this.primaryStage);
            normalViewController.initializeBindingsAndListeners();
        } else { System.err.println("MainController WARNING: NormalViewController is null."); }

        if (fullscreenViewController != null) {
            fullscreenViewController.setPlayerService(this.playerService);
            fullscreenViewController.setLyricsService(this.lyricsService);
            fullscreenViewController.setQueueService(this.queueService);
            fullscreenViewController.setMainController(this);
            fullscreenViewController.setPrimaryStage(this.primaryStage);
            fullscreenViewController.initializeBindingsAndListeners();
        } else { System.err.println("MainController WARNING: FullscreenViewController is null."); }

        // Set the end of media handler for PlayerService
        if (playerService != null) {
            playerService.setOnEndOfMediaHandler(() -> {
                System.out.println("MainController: EndOfMediaHandler triggered.");
                Song nextSong = queueService.getNextSong(); // This also removes the song from the queue
                if (nextSong != null) {
                    System.out.println("MainController: Playing next song from queue: " + nextSong.getTitle());
                    playerService.loadSong(nextSong, true); // Autoplay next song
                } else {
                    System.out.println("MainController: Queue is empty. Clearing player to 'no song' state.");
                    playerService.loadSong(null, false); // Load null to clear player
                }
            });
        }

        // Apply initial theme based on the default state
        applyTheme(isDarkMode); // Apply initial theme
        updateThemeButtonTexts(); // Set initial button texts
        
        // Update initial UI state for the visible controller
        Platform.runLater(() -> { // Defer UI updates slightly to ensure all init is done
            if (primaryStage != null && primaryStage.isFullScreen() && fullscreenViewController != null) {
                fullscreenViewController.updateUIDisplay();
            } else if (normalViewController != null) {
                normalViewController.updateUIDisplay();
            }
        });
    }

    // --- Public methods for Sub-Controllers to call ---

    public void setAppFullScreen(boolean fullScreen) {
        if (primaryStage != null) {
            primaryStage.setFullScreen(fullScreen);
        }
    }

    public void cycleTheme() {
        isDarkMode = !isDarkMode;
        applyTheme(isDarkMode);
        updateThemeButtonTexts();
    }

    private void updateThemeButtonTexts(){
        String buttonText = isDarkMode ? "Light Mode" : "Dark Mode";
        if (normalViewController != null && normalViewController.getThemeButton() != null) {
            normalViewController.getThemeButton().setText(buttonText);
        }
        if (fullscreenViewController != null && fullscreenViewController.getThemeButton() != null) {
            fullscreenViewController.getThemeButton().setText(buttonText);
        }
    }
    
    private void applyTheme(boolean isDarkMode) {
        if (rootStackPane == null) {
            System.err.println("Theme application: Root StackPane is null.");
            return;
        }
        String darkClassName = "dark-mode";
        ObservableList<String> styleClasses = rootStackPane.getStyleClass();
        if (isDarkMode) {
            if (!styleClasses.contains(darkClassName)) styleClasses.add(darkClassName);
        } else {
            styleClasses.remove(darkClassName);
        }
        System.out.println("Theme " + (isDarkMode ? "Dark" : "Light") + " applied to rootStackPane.");
    }


    public void handlePlayPause() {
        if (playerService == null) return;
        MediaPlayer.Status s = playerService.getStatus();

        if (s == MediaPlayer.Status.PLAYING) {
            playerService.pause();
        } else if (s == MediaPlayer.Status.PAUSED) {
            playerService.play();
        } else if (playerService.getCurrentSong() != null &&
                   (s == MediaPlayer.Status.READY || s == MediaPlayer.Status.STOPPED)) {
            // If a song is loaded and is READY or STOPPED, just play it.
            // This handles resuming a stopped song or starting a song that was loaded
            // (e.g. after skip if prev was paused/stopped, then scrubbed, then play is hit).
            playerService.play();
        } else {
            // Covers:
            // - HALTED or UNKNOWN (needs a song load/reload).
            // - Player has no current song (e.g. initial state, or after stop all).
            // - Or, user selected a *different* song from the library to play.

            Song songToPlay = null;
            Song currentSelectedFromView = null;

            // Determine active view and get its selected song
            // Assuming normalView and fullscreenView are Nodes and their visibility can be checked.
            // And that controllers are not null.
            if (normalViewController != null && normalView != null && normalView.isVisible()) {
                currentSelectedFromView = normalViewController.getCurrentlySelectedSong();
            } else if (fullscreenViewController != null && fullscreenView != null && fullscreenView.isVisible()) {
                // currentSelectedFromView = fullscreenViewController.getCurrentlySelectedSong(); // Assuming FullscreenViewController has this method
            }

            // Priority 1: Play a song selected in the library if it's different from what's in the player,
            // or if the player is in a state (like HALTED) that warrants a reload of the selected song.
            if (currentSelectedFromView != null &&
                (playerService.getCurrentSong() == null ||
                 !playerService.getCurrentSong().equals(currentSelectedFromView) ||
                 s == MediaPlayer.Status.HALTED /* If player is HALTED, good to reload selected song */
                )) {
                songToPlay = currentSelectedFromView;
            }
            // Priority 2: If no new selection, but player was HALTED and had a song, try to reload that.
            else if (playerService.getCurrentSong() != null && s == MediaPlayer.Status.HALTED) {
                songToPlay = playerService.getCurrentSong(); // Attempt reload
            }

            // If we decided on a song to load/reload:
            if (songToPlay != null) {
                loadAndPlaySong(songToPlay);
            }
            // Otherwise, if nothing specific to load/reload, try the queue:
            else if (queueService != null && !queueService.isEmpty()) {
                playNextSong(false); // Gets from queue and calls loadAndPlaySong
            } else {
                System.out.println("MainController.handlePlayPause: No song to play or action to take in the 'else' block.");
            }
        }
    }

    public void handleStop() {
        if (playerService != null) playerService.loadSong(null, false);
        if (queueService != null) queueService.clear();
        // UI state updated by listeners
    }

    public void handleSkip() {
        if (playerService == null || queueService == null) {
            System.err.println("MainController.handleSkip: playerService or queueService is null. Cannot skip.");
            return;
        }

        // Attempt to get the next song from the queue. This will remove it.
        Song nextSong = queueService.getNextSong();

        if (nextSong != null) {
            // If a next song is available, load it using the new method
            // which respects the previous song's playback state.
            System.out.println("MainController.handleSkip: Loading next song: " + (nextSong.getTitle() != null ? nextSong.getTitle() : "Unknown Title"));
            playerService.loadSongAfterSkip(nextSong);
        } else {
            // No next song was available in the queue.
            // Stop the player and clear the current song.
            System.out.println("MainController.handleSkip: Queue is empty. Stopping player.");
            playerService.loadSong(null, false); // This effectively stops and resets.
        }
    }

    public void playNextSong(boolean isAutoPlayContext) {
        if (playerService == null || queueService == null) return;
        Song songToPlay = null;
        if (!queueService.isEmpty()) songToPlay = queueService.getNextSong();

        if (songToPlay == null) {
            if (!isAutoPlayContext && normalViewController != null && normalViewController.getCurrentlySelectedSong() != null) {
                Song currentSelected = normalViewController.getCurrentlySelectedSong();
                MediaPlayer.Status currentStatus = playerService.getStatus();
                if (currentStatus != MediaPlayer.Status.PLAYING && currentStatus != MediaPlayer.Status.PAUSED) {
                    songToPlay = currentSelected;
                }
            }
        }
        if (songToPlay != null) loadAndPlaySong(songToPlay);
        else if (playerService.getCurrentSong() != null || 
                 playerService.getStatus() == MediaPlayer.Status.PLAYING || 
                 playerService.getStatus() == MediaPlayer.Status.PAUSED) {
            playerService.loadSong(null, false);
        }
    }

    public void loadAndPlaySong(Song song) {
        if (playerService == null) return;
        if (song == null) {
            playerService.loadSong(null, false);
            return;
        }
        // LyricsService loading and currentSongLiveOffsetMs initialization
        // are now triggered by the PlayerService.currentSongProperty listener
        // in both NormalViewController and FullscreenViewController (via updateUIDisplay)
        // after PlayerService sets its current song.
        boolean loadingOk = playerService.loadSong(song, true); // Request auto-play
        if (!loadingOk) {
            // PlayerService handles its internal state (e.g. HALTED).
            // Sub-controllers' status listeners will update their UIs.
            showErrorDialog("Playback Error", "Could not load audio", 
                            "Failed to load audio for: " + song.getTitle());
        }
    }

    public void adjustLyricOffset(int amount) {
        Song currentSongForOffset = (playerService != null) ? playerService.getCurrentSong() : null;
        if (playerService == null || currentSongForOffset == null || lyricsService == null) {
            System.out.println("Cannot adjust offset: No song active or services unavailable.");
            return;
        }
        this.currentSongLiveOffsetMs += amount;
        
        // Update offset label in the active view
        if (normalViewController != null && normalView.isVisible()) {
            normalViewController.updateLyricOffsetDisplay(this.currentSongLiveOffsetMs);
        }
        if (fullscreenViewController != null && fullscreenView.isVisible()) {
            fullscreenViewController.updateLyricOffsetDisplay(this.currentSongLiveOffsetMs);
        }

        lyricsService.updateCurrentDisplayLines(playerService.getCurrentTimeMillis(), this.currentSongLiveOffsetMs);

        String lyricsFilePath = currentSongForOffset.getLyricsFilePath();
        if (lyricsFilePath != null && !lyricsFilePath.isBlank()) {
            try {
                LrcWriter.saveOffsetToLrcFile(lyricsFilePath, this.currentSongLiveOffsetMs);
            } catch (IOException e) {
                System.err.println("Error saving offset to LRC: " + lyricsFilePath + " - " + e.getMessage());
                showErrorDialog("Offset Save Error", "Could not save lyrics timing", e.getMessage());
            }
        } else {
            System.err.println("Cannot save offset: Lyrics file path unavailable for " + currentSongForOffset.getTitle());
        }
    }
    
    // Getter for sub-controllers to know the current centrally managed live offset
    public int getCurrentSongLiveOffsetMs() {
        return currentSongLiveOffsetMs;
    }
    
    // Method for sub-controllers to set the initial offset when a new song is loaded
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
     * Formats a Song object for display in queue labels.
     * @param song The Song object.
     * @return A string representation (e.g., "Title - Artist"), or "-" if song is null.
     */
    public String formatSongForQueue(Song song) { // Made public
        if (song == null) return "-";
        String title = song.getTitle() != null ? song.getTitle() : "Unknown Title";
        String artist = song.getArtist() != null ? song.getArtist() : "Unknown Artist";
        return title + " - " + artist;
    }

    public String formatTime(long millis) {
        if (millis < 0 || millis == Long.MAX_VALUE) return "0:00";
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public String formatTime(double millis) {
        if (Double.isNaN(millis) || Double.isInfinite(millis) || millis < 0) return "0:00";
        return formatTime((long) millis);
    }

    public String getLyricTextOrEmpty(List<LyricLine> lines, int index) {
        if (lines != null && index >= 0 && index < lines.size()) {
            LyricLine line = lines.get(index);
            return line != null ? line.getText() : "";
        }
        return "";
    }
    
    private void showErrorDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().setPrefSize(480, 200); 
        alert.showAndWait();
    }
    
    // Interface for sub-controllers to expose common methods (like getThemeToggleButton)
    // This is a simple way to avoid instanceof checks or more complex structures for now.
    public interface SubController {
        Button getThemeButton();
    }
}