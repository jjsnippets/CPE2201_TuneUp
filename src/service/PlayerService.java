package service; // Define the package for service classes

// --- JavaFX Imports ---
import javafx.application.Platform; // For potential future use or specific threading needs
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaException; // For specific media-related errors
import javafx.util.Duration;

// --- Model Import ---
import model.Song; // Import the Song model

// --- Java IO Imports ---
import java.io.File;
import java.io.IOException;
// import java.net.MalformedURLException; // Not directly used if File.toURI().toString() is robust

/**
 * Service class that encapsulates JavaFX MediaPlayer functionality.
 * Manages loading audio (FR1.1), playback controls (FR1.3, FR1.4), seeking (FR1.7),
 * and exposes observable properties for playback state, time (FR1.6), and the current song.
 * Includes logic for auto-playing a song once it's ready after loading.
 * Abstracts low-level JavaFX media handling from controllers.
 * Corresponds to SRS sections related to media playback.
 */
public class PlayerService {

    private MediaPlayer mediaPlayer;
    private boolean playWhenReady = false; // Flag to manage auto-play after loading

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
     * @return A read-only observable property for the MediaPlayer's status. (Corresponds to FR1.6 aspect)
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
     * @return A read-only observable property for the current playback time in milliseconds. (FR1.6)
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
     * @return A read-only observable property for the total duration of the media in milliseconds. (FR1.6 aspect)
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
     * Loads the audio file from the given Song's path and optionally prepares it
     * for playback upon readiness. Disposes of any existing MediaPlayer.
     * Updates the currentSongProperty upon successful loading initiation.
     * Implements FR1.1 (Load audio file).
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
     * Starts or resumes playback of the currently loaded song. (FR1.3)
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
     * Pauses the currently playing song. (FR1.4)
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
     * Stops playback and resets the playback position to the beginning. (FR1.5)
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
     * Seeks to the specified position in the media. (FR1.7)
     *
     * @param millis The position to seek to, in milliseconds.
     */
    public void seek(long millis) {
        if (mediaPlayer != null &&
            (getStatus() == MediaPlayer.Status.PLAYING ||
             getStatus() == MediaPlayer.Status.PAUSED ||
             getStatus() == MediaPlayer.Status.READY ||  // Allow seeking when ready but not yet played
             getStatus() == MediaPlayer.Status.STOPPED)) { // Allow seeking when stopped

            long adjustedMillis = Math.max(0, millis); // Ensure non-negative
            long totalDurationMs = getTotalDurationMillis();

            if (totalDurationMs > 0 && adjustedMillis > totalDurationMs) {
                adjustedMillis = totalDurationMs; // Cap seek time at total duration
            }

            System.out.println("PlayerService: Seeking to " + adjustedMillis + "ms.");
            mediaPlayer.seek(Duration.millis(adjustedMillis));

            // Manually update currentTimeWrapper if seeking from a non-PLAYING state,
            // as MediaPlayer's currentTimeProperty might not update immediately or trigger listeners
            // until playback resumes or another action occurs.
            if (getStatus() != MediaPlayer.Status.PLAYING) {
                // Ensure this runs on FX thread if PlayerService might be called from non-FX thread,
                // though typically service methods are called from controller on FX thread.
                // For direct property updates from listeners, Platform.runLater isn't usually needed.
                // Here, it's a direct update after an action.
                final long finalAdjustedMillis = adjustedMillis; // Effective final for lambda
                Platform.runLater(() -> {
                     // Check status again in case it changed rapidly
                    if (getStatus() != MediaPlayer.Status.PLAYING) {
                       currentTimeMillisWrapper.set(finalAdjustedMillis);
                    }
                });
            }
        } else if (mediaPlayer != null) {
            System.err.println("PlayerService: Cannot seek in current state: " + getStatus());
        } else {
            System.err.println("PlayerService: Cannot seek, no media loaded.");
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
        if (mediaPlayer == null) return;

        mediaPlayer.statusProperty().addListener((_obs, _oldStatus, newStatus) -> {
            statusWrapper.set(newStatus); // Update the service's status property
        });

        mediaPlayer.currentTimeProperty().addListener((_obs, _oldTime, newTime) -> {
            if (newTime != null) {
                currentTimeMillisWrapper.set((long) newTime.toMillis()); // Update current time
            }
        });

        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getTotalDuration();
            long totalMillis = 0L;
            if (total != null && !total.isUnknown() && !total.isIndefinite()) {
                totalMillis = (long) total.toMillis();
            }
            totalDurationMillisWrapper.set(totalMillis); // Update total duration

            if (playWhenReady) {
                System.out.println("PlayerService: Media ready, auto-playing...");
                mediaPlayer.play();
                playWhenReady = false; // Reset flag
            } else {
                System.out.println("PlayerService: Media ready (auto-play disabled or already handled).");
            }
            // Note: The status will transition to READY via the statusProperty listener.
        });

        mediaPlayer.setOnEndOfMedia(() -> {
            System.out.println("PlayerService: End of media reached for '" +
                               (getCurrentSong() != null ? getCurrentSong().getTitle() : "media") + "'.");
            // Transition to STOPPED state. The controller's status listener
            // can then decide to play the next song from the queue.
            // Calling stop() also resets playWhenReady and handles status update via listener.
            stop();
        });

        mediaPlayer.setOnError(() -> {
            MediaException error = mediaPlayer.getError();
            handleLoadError("MediaPlayer internal error",
                            (getCurrentSong() != null ? getCurrentSong().getAudioFilePath() : "N/A"),
                            error);
            // playWhenReady is reset by handleLoadError via disposePlayer
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
