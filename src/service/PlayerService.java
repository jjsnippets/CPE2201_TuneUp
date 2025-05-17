package service;

// --- JavaFX Imports ---
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaException;
import javafx.util.Duration;

// --- Model Imports ---
import model.Song;

// --- Java IO Imports ---
import java.io.File;
import java.io.IOException;
// import java.net.MalformedURLException; // Not directly used if File.toURI().toString() is robust

/**
 * Service class that encapsulates JavaFX MediaPlayer functionality.
 * Manages loading audio (enabling FR1.1), playback controls (FR1.3 for play/pause, FR1.4 for stop),
 * seeking (FR1.7), and exposes observable properties for playback state and time (FR1.6),
 * and the current song.
 * Includes logic for auto-playing a song once it's ready after loading.
 * Abstracts low-level JavaFX media handling from controllers.
 */
public class PlayerService {

    private MediaPlayer mediaPlayer;
    private boolean playWhenReady = false; // Flag to manage auto-play after loading
    private Long pendingSeekMillis = null; // Stores a seek request if made before player is ready
    private OnEndOfMediaHandler onEndOfMediaHandler; // Callback for when media ends

    // --- Observable Properties ---
    // Wraps the MediaPlayer status, providing a read-only property.
    private final ReadOnlyObjectWrapper<MediaPlayer.Status> statusWrapper =
            new ReadOnlyObjectWrapper<>(this, "status", MediaPlayer.Status.UNKNOWN);

    // Wraps the current playback time in milliseconds.
    private final ReadOnlyLongWrapper currentTimeMillisWrapper =
            new ReadOnlyLongWrapper(this, "currentTimeMillis", 0L);

    // Wraps the total duration of the media in milliseconds.
    private final ReadOnlyLongWrapper totalDurationMillisWrapper =
            new ReadOnlyLongWrapper(this, "totalDurationMillis", 0L);

    // Wraps the currently loaded Song object.
    private final ReadOnlyObjectWrapper<Song> currentSongWrapper =
            new ReadOnlyObjectWrapper<>(this, "currentSong", null);


    // --- Public Read-Only Property Accessors ---

    /**
     * @return A read-only observable property for the MediaPlayer's status.
     * <p>Supports FR1.6 by providing playback state information.
     */
    public final ReadOnlyObjectProperty<MediaPlayer.Status> statusProperty() {
        return statusWrapper.getReadOnlyProperty();
    }

    /**
     * @return The current status of the MediaPlayer.
     */
    public final MediaPlayer.Status getStatus() {
        return statusWrapper.get();
    }

    /**
     * @return A read-only observable property for the current playback time in milliseconds.
     * <p>Supports FR1.6 by providing current time information.
     */
    public final ReadOnlyLongProperty currentTimeProperty() {
        return currentTimeMillisWrapper.getReadOnlyProperty();
    }

    /**
     * @return The current playback time in milliseconds.
     */
    public final long getCurrentTimeMillis() {
        return currentTimeMillisWrapper.get();
    }

    /**
     * @return A read-only observable property for the total duration of the media in milliseconds.
     * <p>Supports FR1.6 by providing total duration information.
     */
    public final ReadOnlyLongProperty totalDurationProperty() {
        return totalDurationMillisWrapper.getReadOnlyProperty();
    }

    /**
     * @return The total duration of the media in milliseconds.
     */
    public final long getTotalDurationMillis() {
        return totalDurationMillisWrapper.get();
    }

    /**
     * @return A read-only observable property for the currently loaded Song.
     */
    public final ReadOnlyObjectProperty<Song> currentSongProperty() {
        return currentSongWrapper.getReadOnlyProperty();
    }

    /**
     * @return The currently loaded Song object, or null if no song is loaded.
     */
    public final Song getCurrentSong() {
        return currentSongWrapper.get();
    }

    // --- Public Service Methods ---

    /**
     * Sets a handler to be called when the current media finishes playing.
     * @param handler The handler to execute.
     */
    public void setOnEndOfMediaHandler(OnEndOfMediaHandler handler) {
        this.onEndOfMediaHandler = handler;
    }

    /**
     * Loads the audio file from the given Song's path and optionally prepares it
     * for playback upon readiness. Disposes of any existing MediaPlayer.
     * Updates the currentSongProperty upon successful loading initiation.
     * <p>SRS: FR1.1 (enables playing audio by loading the track).
     *
     * @param song The Song object to load. Can be null to unload/reset the player.
     * @param startPlayback When true, playback will begin automatically once the media is ready.
     * @return true if loading was successfully initiated, false otherwise (e.g., invalid path, immediate error).
     */
    public boolean loadSong(Song song, boolean startPlayback) {
        disposePlayer(); // Clean up previous player and reset state (including playWhenReady)

        if (song == null || song.getAudioFilePath() == null || song.getAudioFilePath().isBlank()) {
            System.err.println("PlayerService: Cannot load null song or song with invalid audio path. Player reset.");
            // State is already reset by disposePlayer()
            return false; // Loading cannot be initiated
        }

        System.out.println("PlayerService: Attempting to load song '" + song.getTitle() +
                           "' (Start Playback: " + startPlayback + ")");
        this.playWhenReady = startPlayback; // Set the flag for auto-play

        try {
            File audioFile = new File(song.getAudioFilePath());
            if (!audioFile.exists() || !audioFile.canRead()) {
                throw new IOException("Audio file not found or cannot be read: " + song.getAudioFilePath());
            }
            String mediaUriString = audioFile.toURI().toString();
            Media media = new Media(mediaUriString); // Can throw MediaException if URI is malformed or unsupported

            mediaPlayer = new MediaPlayer(media); // Can throw MediaException

            addMediaPlayerListeners(); // Setup listeners for the new MediaPlayer

            // Set initial state for wrappers (actual values update via listeners)
            statusWrapper.set(mediaPlayer.getStatus()); // Typically UNKNOWN initially
            currentTimeMillisWrapper.set(0L);
            totalDurationMillisWrapper.set(0L); // Will be updated by onReady

            currentSongWrapper.set(song); // Set the current song property *after* successful setup
            System.out.println("PlayerService: Initiated loading for '" + song.getTitle() + "'.");
            return true; // Loading successfully initiated

        } catch (IOException | IllegalArgumentException | MediaException | SecurityException e) {
            handleLoadError("Error loading media", song.getAudioFilePath(), e);
            return false; // Loading failed
        } catch (Exception e) { // Catch any other unexpected errors
            handleLoadError("Unexpected error loading media", song.getAudioFilePath(), e);
            return false; // Loading failed
        }
    }

    /**
     * Loads the given song, automatically determining whether to start playback based on the
     * state of the song currently being played or paused. If the current song is playing,
     * the new song will also play once loaded. Otherwise (e.g., paused, stopped),
     * the new song will be loaded but will not auto-play.
     * This method is intended for use with "skip next" or "skip previous" functionality.
     *
     * @param song The Song object to load. Can be null, in which case the player is reset
     *             and false is returned (consistent with {@link #loadSong(Song, boolean)}).
     * @return true if loading was successfully initiated, false otherwise.
     */
    public boolean loadSongAfterSkip(Song song) {
        MediaPlayer.Status previousStatus = getStatus(); // Get current status
        boolean startPlaybackForNewSong = (previousStatus == MediaPlayer.Status.PLAYING);

        System.out.println("PlayerService: loadSongAfterSkip called. Previous status: " + previousStatus +
                           ". New song will " + (startPlaybackForNewSong ? "auto-play." : "load paused/ready."));

        // Call the main loadSong method with the determined playback flag.
        // loadSong handles null song checks, disposing the old player, and setting up the new one.
        return loadSong(song, startPlaybackForNewSong);
    }

    /**
     * Starts or resumes playback of the currently loaded song.
     * <p>SRS: FR1.3.
     */
    public void play() {
        if (mediaPlayer != null &&
            (getStatus() == MediaPlayer.Status.READY ||
             getStatus() == MediaPlayer.Status.PAUSED ||
             getStatus() == MediaPlayer.Status.STOPPED)) {
            System.out.println("PlayerService: Playing '" +
                               (getCurrentSong() != null ? getCurrentSong().getTitle() : "media") + "'.");
            this.playWhenReady = false; // Manual play overrides pending auto-play
            mediaPlayer.play();
        } else if (mediaPlayer == null) {
            System.err.println("PlayerService: Cannot play, no media loaded.");
        } else {
            System.out.println("PlayerService: Play called in invalid state: " + getStatus());
        }
    }

    /**
     * Pauses the currently playing song.
     * <p>SRS: FR1.3.
     * If called, it cancels any pending auto-play.
     */
    public void pause() {
        if (mediaPlayer != null && getStatus() == MediaPlayer.Status.PLAYING) {
            System.out.println("PlayerService: Pausing '" +
                               (getCurrentSong() != null ? getCurrentSong().getTitle() : "media") + "'.");
            this.playWhenReady = false; // Manual pause overrides pending auto-play
            mediaPlayer.pause();
        }
    }

    /**
     * Stops playback and resets the playback position to the beginning.
     * <p>SRS: FR1.4.
     * If called, it cancels any pending auto-play.
     */
    public void stop() {
        if (mediaPlayer != null) {
            System.out.println("PlayerService: Stopping '" +
                               (getCurrentSong() != null ? getCurrentSong().getTitle() : "media") + "'.");
            this.playWhenReady = false; // Manual stop overrides pending auto-play
            mediaPlayer.stop();
            // The status listener will update statusWrapper.
            // currentTimeMillisWrapper might also be reset by the player or listener.
        }
    }

    /**
     * Seeks to the specified position in the media.
     * <p>SRS: FR1.7.
     *
     * @param millis The position to seek to, in milliseconds.
     */
    public void seek(long millis) {
        if (mediaPlayer != null) {
            MediaPlayer.Status currentStatus = getStatus();
            if (currentStatus == MediaPlayer.Status.READY ||
                currentStatus == MediaPlayer.Status.PAUSED ||
                currentStatus == MediaPlayer.Status.PLAYING ||
                currentStatus == MediaPlayer.Status.STOPPED) {
                System.out.println("PlayerService: Seeking to " + millis + "ms. Current status: " + currentStatus);
                mediaPlayer.seek(Duration.millis(millis));
                this.pendingSeekMillis = null; // Clear any prior pending seek
                
                // Important: Update current time wrapper even if not playing
                // This ensures lyrics update properly after seeking without playing
                Platform.runLater(() -> currentTimeMillisWrapper.set(millis));
            } else {
                System.out.println("PlayerService: Deferring seek to " + millis + "ms. Current status: " + currentStatus);
                this.pendingSeekMillis = millis;
            }
        } else {
            System.err.println("PlayerService: Cannot seek, no media loaded.");
            this.pendingSeekMillis = null;
        }
    }

    /**
     * Disposes the internal MediaPlayer instance, releasing system resources.
     * This should be called when the service is no longer needed (e.g., application shutdown).
     */
    public void dispose() {
        System.out.println("PlayerService: Dispose called. Cleaning up resources.");
        disposePlayer();
        System.out.println("PlayerService: Service disposed.");
    }

    // --- Private Helper Methods ---

    /**
     * Sets up all necessary listeners on the current {@code mediaPlayer} instance.
     * This method is called internally after a new MediaPlayer is created.
     */
    private void addMediaPlayerListeners() {
        if (mediaPlayer == null) return; // Added return to prevent NullPointerException if mediaPlayer is null

        // Listener for status changes
        mediaPlayer.statusProperty().addListener((@SuppressWarnings("unused") var obs, @SuppressWarnings("unused") var oldStatus, var newStatus) -> {
            Platform.runLater(() -> statusWrapper.set(newStatus));
        });

        // Listener for current time changes
        mediaPlayer.currentTimeProperty().addListener((@SuppressWarnings("unused") var obs, @SuppressWarnings("unused") var oldTime, var newTime) -> {
            Platform.runLater(() -> currentTimeMillisWrapper.set((long) newTime.toMillis()));
        });

        // Listener for when media is ready
        mediaPlayer.setOnReady(() -> {
            Platform.runLater(() -> {
                totalDurationMillisWrapper.set((long) mediaPlayer.getTotalDuration().toMillis());
                // statusWrapper.set(MediaPlayer.Status.READY); // Done by statusProperty listener

                Song currentSong = currentSongWrapper.get();
                String songTitle = (currentSong != null) ? currentSong.getTitle() : "media";

                System.out.println("PlayerService: Media ready for '" + songTitle +
                                   "'. Duration: " + mediaPlayer.getTotalDuration().toMillis() + "ms");
                
                if (pendingSeekMillis != null) {
                    System.out.println("PlayerService: Applying pending seek to " + pendingSeekMillis + "ms for '" + songTitle + "'.");
                    mediaPlayer.seek(Duration.millis(pendingSeekMillis));
                    // Also update the current time wrapper to ensure lyrics update for pending seeks
                    currentTimeMillisWrapper.set(pendingSeekMillis);
                    // pendingSeekMillis is cleared below
                    pendingSeekMillis = null;
                }

                if (playWhenReady) {
                    System.out.println("PlayerService: Auto-playing '" + songTitle + "' as playWhenReady is true.");
                    play(); // Call the service's play method, which handles playWhenReady flag and plays
                } else {
                    // If not auto-playing, player is ready. If a seek was applied, it's at the new position.
                    System.out.println("PlayerService: Media ready for '" + songTitle + "', playWhenReady is false. Current time: " + mediaPlayer.getCurrentTime().toMillis() + "ms");
                }
            });
        });

        // Listener for end of media
        mediaPlayer.setOnEndOfMedia(() -> {
            Platform.runLater(() -> {
                System.out.println("PlayerService: End of media reached for '" +
                                   (currentSongWrapper.get() != null ? currentSongWrapper.get().getTitle() : "media") + "'.");
                if (onEndOfMediaHandler != null) {
                    onEndOfMediaHandler.handle();
                }
            });
        });

        // Listener for errors
        mediaPlayer.setOnError(() -> {
            Platform.runLater(() -> {
                MediaException error = mediaPlayer.getError();
                String filePath = (currentSongWrapper.get() != null) ? currentSongWrapper.get().getAudioFilePath() : "Unknown file";
                handleLoadError("MediaPlayer error during playback", filePath, error);
            });
        });

        // Listener for total duration changes (though setOnReady is often primary for initial duration)
        mediaPlayer.totalDurationProperty().addListener((@SuppressWarnings("unused") var obs, @SuppressWarnings("unused") var oldDuration, var newDuration) -> {
            if (newDuration != null && newDuration != Duration.UNKNOWN) {
                Platform.runLater(() -> totalDurationMillisWrapper.set((long) newDuration.toMillis()));
            }
        });
    }

    /**
     * Handles errors occurring during media loading or playback.
     * Logs the error, disposes the player, and resets state wrappers.
     *
     * @param message A descriptive message prefix.
     * @param filePath The file path associated with the error, if known.
     * @param throwable The exception/error that occurred (can be null).
     */
    private void handleLoadError(String message, String filePath, Throwable throwable) {
        System.err.println("PlayerService Error: " + message + " [" + (filePath != null ? filePath : "N/A") + "]");
        if (throwable != null) {
            System.err.println(" -> Cause: " + throwable.getMessage());
            // Only print stack trace for unexpected exceptions or non-standard MediaExceptions
            if (!(throwable instanceof IOException ||
                  (throwable instanceof MediaException &&
                   ((MediaException)throwable).getType() != MediaException.Type.UNKNOWN) || // Log common media errors less verbosely
                  throwable instanceof SecurityException)) {
                throwable.printStackTrace();
            }
        }

        disposePlayer(); // Clean up potentially corrupted player and reset all state

        // Explicitly set status to HALTED after disposal sets it to UNKNOWN
        statusWrapper.set(MediaPlayer.Status.HALTED);
        // Other properties (currentTime, totalDuration, currentSong) are reset by disposePlayer.
    }

    /**
     * Safely stops and disposes the current {@code mediaPlayer} instance,
     * resetting all relevant service state (flags and observable properties).
     */
    private void disposePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop(); // Attempt to stop first
            } catch (Exception e) {
                System.err.println("PlayerService: Error stopping media player during disposal: " + e.getMessage());
            }
            try {
                mediaPlayer.dispose(); // Release system resources
                System.out.println("PlayerService: Disposed existing MediaPlayer instance.");
            } catch (Exception e) {
                System.err.println("PlayerService: Error disposing media player: " + e.getMessage());
            } finally {
                mediaPlayer = null; // Clear the reference
            }
        }

        // Always reset state when player is disposed or was not present
        playWhenReady = false; // Reset auto-play flag
        pendingSeekMillis = null; // Reset pending seek

        // Reset observable properties to their initial/default state
        if (currentSongWrapper.get() != null) { // Check before setting to avoid needless event if already null
            currentSongWrapper.set(null);
        }
        // Set status to UNKNOWN, as player is gone. HALTED is for player-specific error states.
        if (getStatus() != MediaPlayer.Status.UNKNOWN) { // Check before setting
            statusWrapper.set(MediaPlayer.Status.UNKNOWN);
        }
        if (getCurrentTimeMillis() != 0L) { // Check before setting
            currentTimeMillisWrapper.set(0L);
        }
        if (getTotalDurationMillis() != 0L) { // Check before setting
            totalDurationMillisWrapper.set(0L);
        }
    }
}
