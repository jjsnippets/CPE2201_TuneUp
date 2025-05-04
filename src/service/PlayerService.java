package service; // Define the package for service classes

import javafx.application.Platform; // Keep import for comments/potential future use
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaException; // Import for specific errors
import javafx.util.Duration;
import model.Song; // Import the Song model

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException; // Import for URI errors

/**
 * Service class that encapsulates JavaFX MediaPlayer functionality.
 * Manages loading audio (FR1.1), playback controls (FR1.3, FR1.4), seeking (FR1.7),
 * and exposes observable properties for playback state and time (FR1.6).
 * Abstracts low-level JavaFX media handling from controllers.
 * Refactored for improved error handling structure.
 */
public class PlayerService {

    private MediaPlayer mediaPlayer;
    private Song currentSong;

    // --- Observable Properties ---
    private final ReadOnlyObjectWrapper<MediaPlayer.Status> statusWrapper =
            new ReadOnlyObjectWrapper<>(this, "status", MediaPlayer.Status.UNKNOWN);
    private final ReadOnlyLongWrapper currentTimeMillisWrapper =
            new ReadOnlyLongWrapper(this, "currentTimeMillis", 0L);
    private final ReadOnlyLongWrapper totalDurationMillisWrapper =
            new ReadOnlyLongWrapper(this, "totalDurationMillis", 0L);

    // Public read-only property accessors
    public final ReadOnlyObjectProperty<MediaPlayer.Status> statusProperty() { return statusWrapper.getReadOnlyProperty(); }
    public final MediaPlayer.Status getStatus() { return statusWrapper.get(); }
    public final ReadOnlyLongProperty currentTimeProperty() { return currentTimeMillisWrapper.getReadOnlyProperty(); }
    public final long getCurrentTimeMillis() { return currentTimeMillisWrapper.get(); }
    public final ReadOnlyLongProperty totalDurationProperty() { return totalDurationMillisWrapper.getReadOnlyProperty(); }
    public final long getTotalDurationMillis() { return totalDurationMillisWrapper.get(); }


    // --- Public Service Methods ---

    /**
     * Loads the audio file from the given Song's audioFilePath.
     * Disposes of any existing MediaPlayer before creating a new one.
     *
     * @param song The Song object to load. Can be null to unload/reset.
     */
    public void loadSong(Song song) {
        disposePlayer(); // Clean up previous player first

        if (song == null || song.getAudioFilePath() == null || song.getAudioFilePath().isBlank()) {
            System.err.println("PlayerService: Cannot load null song or song with invalid audio path. Resetting player.");
            // Reset state fully if input is invalid (already handled by disposePlayer)
            return; // Nothing further to load
        }

        // Update current song reference *after* successful loading starts
        this.currentSong = song;
        System.out.println("PlayerService: Attempting to load song - " + song.getTitle());

        try {
            // --- Media Creation ---
            File audioFile = new File(song.getAudioFilePath());
            if (!audioFile.exists() || !audioFile.canRead()) {
                throw new IOException("Audio file not found or cannot be read: " + song.getAudioFilePath());
            }
            String mediaUriString = audioFile.toURI().toString();
            Media media = new Media(mediaUriString); // Can throw MediaException

            // --- MediaPlayer Creation ---
            mediaPlayer = new MediaPlayer(media); // Can throw MediaException

            // --- Setup Listeners ---
            addMediaPlayerListeners();

            // --- Set Initial State ---
            // Status is initially derived from the new player's status
            statusWrapper.set(mediaPlayer.getStatus());
            currentTimeMillisWrapper.set(0L);
            totalDurationMillisWrapper.set(0L); // Will be updated onReady

        } catch (IOException | IllegalArgumentException | MediaException | SecurityException e) {
            // Catch specific, potentially recoverable loading errors
            handleLoadError("Error loading media", song.getAudioFilePath(), e);
        } catch (Exception e) {
            // Catch unexpected errors during loading
            handleLoadError("Unexpected error loading media", song.getAudioFilePath(), e);
        }
    }

    /**
     * Starts or resumes playback of the currently loaded song.
     */
    public void play() {
        if (mediaPlayer != null && (getStatus() == MediaPlayer.Status.READY || getStatus() == MediaPlayer.Status.PAUSED || getStatus() == MediaPlayer.Status.STOPPED)) {
             System.out.println("PlayerService: Playing " + (currentSong != null ? currentSong.getTitle() : "media"));
            mediaPlayer.play();
        } else if (mediaPlayer == null) {
             System.err.println("PlayerService: Cannot play, no media loaded.");
        } else {
             System.out.println("PlayerService: Play called in invalid state: " + getStatus());
        }
    }

    /** Pauses the currently playing song. */
    public void pause() {
        if (mediaPlayer != null && getStatus() == MediaPlayer.Status.PLAYING) {
             System.out.println("PlayerService: Pausing " + (currentSong != null ? currentSong.getTitle() : "media"));
            mediaPlayer.pause();
        }
    }

    /** Stops playback and resets the playback position to the beginning. */
    public void stop() {
        if (mediaPlayer != null) {
             System.out.println("PlayerService: Stopping " + (currentSong != null ? currentSong.getTitle() : "media"));
            mediaPlayer.stop();
            // stop() usually resets time, but confirm with listener or manual reset if needed
            // currentTimeMillisWrapper.set(0L); // Listener typically handles this via status change
        }
    }

    /** Seeks to the specified position in the media. */
    public void seek(long millis) {
        if (mediaPlayer != null && (getStatus() == MediaPlayer.Status.PLAYING || getStatus() == MediaPlayer.Status.PAUSED || getStatus() == MediaPlayer.Status.READY)) {
             millis = Math.max(0, millis); // Ensure non-negative

             long total = getTotalDurationMillis();
             if (total > 0 && millis > total) {
                 millis = total; // Cap at duration
             }

            System.out.println("PlayerService: Seeking to " + millis + "ms");
            mediaPlayer.seek(Duration.millis(millis));
            // Manually update wrapper if listener isn't fast enough or seeking from non-playing state
             if (getStatus() != MediaPlayer.Status.PLAYING && getStatus() != MediaPlayer.Status.PAUSED) {
                 currentTimeMillisWrapper.set(millis);
             }
        } else if (mediaPlayer != null) {
            System.err.println("PlayerService: Cannot seek in current state: " + getStatus());
        }
    }

    /** Gets the currently loaded Song object. */
    public Song getCurrentSong() {
        return currentSong;
    }

    /** Disposes the internal MediaPlayer instance, releasing resources. */
    public void dispose() {
        disposePlayer();
        System.out.println("PlayerService: Service disposed.");
    }


    // --- Private Helper Methods ---

    /**
     * Sets up all necessary listeners on the current mediaPlayer instance.
     * Precondition: mediaPlayer must not be null.
     */
    private void addMediaPlayerListeners() {
        // Status listener -> update statusWrapper
        mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            // Updates usually happen on FX thread, direct update often okay.
            // Platform.runLater(() -> statusWrapper.set(newStatus));
            statusWrapper.set(newStatus);
        });

        // Time listener -> update currentTimeMillisWrapper
        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime != null) {
                // Platform.runLater(() -> currentTimeMillisWrapper.set((long) newTime.toMillis()));
                currentTimeMillisWrapper.set((long) newTime.toMillis());
            }
        });

        // Ready handler -> update totalDurationMillisWrapper
        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getTotalDuration();
            long totalMillis = 0L;
            if (total != null && !total.isUnknown() && !total.isIndefinite()) {
                totalMillis = (long) total.toMillis();
            }
            // Platform.runLater(() -> totalDurationMillisWrapper.set(totalMillis));
            totalDurationMillisWrapper.set(totalMillis);
            // Status should transition to READY via its own listener.
        });

        // End of media handler -> stop player, update status
        mediaPlayer.setOnEndOfMedia(() -> {
            System.out.println("PlayerService: End of media reached for " + (currentSong != null ? currentSong.getTitle() : "media"));
            // Stop resets time and status to STOPPED/READY depending on implementation
            mediaPlayer.stop(); // Call internal player stop first
            // Listener on statusProperty should handle setting wrapper to STOPPED/READY
        });

        // Error handler -> log and update state
        mediaPlayer.setOnError(() -> {
             // Use the centralized error handler
             MediaException error = mediaPlayer.getError();
             handleLoadError("MediaPlayer internal error", (currentSong != null ? currentSong.getAudioFilePath() : "N/A"), error);
        });
    }

    /**
     * Handles errors occurring during media loading or playback.
     * Logs the error, disposes the player, and resets state wrappers.
     *
     * @param message A descriptive message prefix.
     * @param filePath The file path associated with the error (if known).
     * @param throwable The exception/error that occurred (can be null).
     */
    private void handleLoadError(String message, String filePath, Throwable throwable) {
        System.err.println("PlayerService Error: " + message + " [" + filePath + "]");
        if (throwable != null) {
             System.err.println(" -> Cause: " + throwable.getMessage());
             // Only print stack trace for unexpected exceptions, not common ones like file not found
             if (!(throwable instanceof IOException || throwable instanceof MediaException || throwable instanceof SecurityException)) {
                 throwable.printStackTrace();
             }
        }

        disposePlayer(); // Clean up potentially corrupted player

        // Reset observable properties to indicate error/stopped state
        statusWrapper.set(MediaPlayer.Status.HALTED);
        currentTimeMillisWrapper.set(0L);
        totalDurationMillisWrapper.set(0L);
    }

    /**
     * Safely stops and disposes the current mediaPlayer instance, resetting state.
     */
    private void disposePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop(); // Attempt to stop first
            } catch (Exception e) {
                 System.err.println("PlayerService: Error stopping media player during disposal: " + e.getMessage());
            }
            try {
                mediaPlayer.dispose(); // Release resources
                System.out.println("PlayerService: Disposed existing MediaPlayer.");
            } catch (Exception e) {
                System.err.println("PlayerService: Error disposing media player: " + e.getMessage());
            } finally {
                mediaPlayer = null; // Clear reference
            }
        }
        // Always reset state when player is disposed (or wasn't there)
        currentSong = null;
        if (getStatus() != MediaPlayer.Status.UNKNOWN) statusWrapper.set(MediaPlayer.Status.UNKNOWN); // Avoid needless updates
        if (getCurrentTimeMillis() != 0L) currentTimeMillisWrapper.set(0L);
        if (getTotalDurationMillis() != 0L) totalDurationMillisWrapper.set(0L);
    }
}
