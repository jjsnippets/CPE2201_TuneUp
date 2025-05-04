package service; // Define the package for service classes

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaException;
import javafx.util.Duration;
import model.Song; // Import the Song model

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException; // Often involved with URI issues

/**
 * Service class that encapsulates JavaFX MediaPlayer functionality.
 * Manages loading audio (FR1.1), playback controls (FR1.3, FR1.4), seeking (FR1.7),
 * and exposes observable properties for playback state, time (FR1.6), and current song.
 * Includes logic for auto-playing a song once it's ready after loading.
 * Abstracts low-level JavaFX media handling from controllers.
 */
public class PlayerService {

    private MediaPlayer mediaPlayer;

    // --- Observable Properties ---
    private final ReadOnlyObjectWrapper<MediaPlayer.Status> statusWrapper =
            new ReadOnlyObjectWrapper<>(this, "status", MediaPlayer.Status.UNKNOWN);
    private final ReadOnlyLongWrapper currentTimeMillisWrapper =
            new ReadOnlyLongWrapper(this, "currentTimeMillis", 0L);
    private final ReadOnlyLongWrapper totalDurationMillisWrapper =
            new ReadOnlyLongWrapper(this, "totalDurationMillis", 0L);
    // Added property for the currently loaded song
    private final ReadOnlyObjectWrapper<Song> currentSongWrapper =
            new ReadOnlyObjectWrapper<>(this, "currentSong", null);

    // Flag to manage auto-play after loading
    private boolean playWhenReady = false;

    // --- Public Read-Only Property Accessors ---
    public final ReadOnlyObjectProperty<MediaPlayer.Status> statusProperty() { return statusWrapper.getReadOnlyProperty(); }
    public final MediaPlayer.Status getStatus() { return statusWrapper.get(); }
    public final ReadOnlyLongProperty currentTimeProperty() { return currentTimeMillisWrapper.getReadOnlyProperty(); }
    public final long getCurrentTimeMillis() { return currentTimeMillisWrapper.get(); }
    public final ReadOnlyLongProperty totalDurationProperty() { return totalDurationMillisWrapper.getReadOnlyProperty(); }
    public final long getTotalDurationMillis() { return totalDurationMillisWrapper.get(); }
    public final ReadOnlyObjectProperty<Song> currentSongProperty() { return currentSongWrapper.getReadOnlyProperty(); }
    public final Song getCurrentSong() { return currentSongWrapper.get(); }

    // --- Public Service Methods ---

    /**
     * Loads the audio file from the given Song's path and optionally prepares it
     * for playback upon readiness.
     * Disposes of any existing MediaPlayer before creating a new one.
     * Updates the currentSongProperty upon successful loading initiation.
     *
     * @param song The Song object to load. Can be null to unload/reset.
     * @param startPlayback When true, playback will begin automatically once the media is ready.
     * @return true if loading was successfully initiated, false otherwise (e.g., invalid path, immediate error).
     */
    public boolean loadSong(Song song, boolean startPlayback) {
        disposePlayer(); // Clean up previous player first, resets state including playWhenReady

        if (song == null || song.getAudioFilePath() == null || song.getAudioFilePath().isBlank()) {
            System.err.println("PlayerService: Cannot load null song or song with invalid audio path.");
            // State already reset by disposePlayer
            return false; // Loading cannot be initiated
        }

        System.out.println("PlayerService: Attempting to load song - " + song.getTitle() + " (Start Playback: " + startPlayback + ")");
        this.playWhenReady = startPlayback; // Set the flag based on parameter

        try {
            // --- Media Creation ---
            File audioFile = new File(song.getAudioFilePath());
            if (!audioFile.exists() || !audioFile.canRead()) {
                throw new IOException("Audio file not found or cannot be read: " + song.getAudioFilePath());
            }
            String mediaUriString = audioFile.toURI().toString(); // Use toString() for Media constructor
            Media media = new Media(mediaUriString); // Can throw MediaException

            // --- MediaPlayer Creation ---
            mediaPlayer = new MediaPlayer(media); // Can throw MediaException

            // --- Setup Listeners ---
            addMediaPlayerListeners(); // Setup listeners before potential errors during preparation

            // --- Set Initial State (before READY) ---
            statusWrapper.set(mediaPlayer.getStatus()); // Initial status (usually UNKNOWN)
            currentTimeMillisWrapper.set(0L);
            totalDurationMillisWrapper.set(0L); // Will be updated onReady

            // --- Loading Initiated Successfully ---
            currentSongWrapper.set(song); // Set the current song property
            System.out.println("PlayerService: Initiated loading for " + song.getTitle());
            return true; // Return true for successful initiation

        } catch (IOException | IllegalArgumentException | MediaException | SecurityException e) {
            // Catch specific, potentially recoverable loading errors
            handleLoadError("Error loading media", song.getAudioFilePath(), e);
            // playWhenReady is reset inside handleLoadError via disposePlayer
            return false; // Return false on loading initiation errors
        } catch (Exception e) {
            // Catch unexpected errors during loading
            handleLoadError("Unexpected error loading media", song.getAudioFilePath(), e);
            // playWhenReady is reset inside handleLoadError via disposePlayer
            return false; // Return false on unexpected errors
        }
    }

    /**
     * Starts or resumes playback of the currently loaded song.
     * Resets the playWhenReady flag if called manually.
     */
    public void play() {
        // Check if media player is ready or can be resumed
        if (mediaPlayer != null && (getStatus() == MediaPlayer.Status.READY || getStatus() == MediaPlayer.Status.PAUSED || getStatus() == MediaPlayer.Status.STOPPED)) {
            System.out.println("PlayerService: Playing " + (getCurrentSong() != null ? getCurrentSong().getTitle() : "media"));
            this.playWhenReady = false; // If play is called manually, cancel pending auto-play
            mediaPlayer.play(); // Start or resume playback
        } else if (mediaPlayer == null) {
            System.err.println("PlayerService: Cannot play, no media loaded.");
        } else {
            System.out.println("PlayerService: Play called in invalid state: " + getStatus());
        }
    }

    /**
     * Pauses the currently playing song.
     * Resets the playWhenReady flag.
     */
    public void pause() {
        // Check if media player is playing
        if (mediaPlayer != null && getStatus() == MediaPlayer.Status.PLAYING) {
            System.out.println("PlayerService: Pausing " + (getCurrentSong() != null ? getCurrentSong().getTitle() : "media"));
            this.playWhenReady = false; // Cancel pending auto-play
            mediaPlayer.pause(); // Pause playback
        }
    }

    /**
     * Stops playback and resets the playback position to the beginning.
     * Resets the playWhenReady flag.
     */
    public void stop() {
        // Check if media player exists
        if (mediaPlayer != null) {
            System.out.println("PlayerService: Stopping " + (getCurrentSong() != null ? getCurrentSong().getTitle() : "media"));
            this.playWhenReady = false; // Cancel pending auto-play
            mediaPlayer.stop(); // Stop playback and reset position
            // Status/time listeners should handle UI updates.
        }
    }

    /**
     * Seeks to the specified position in the media.
     * Handles edge cases and updates time property manually if needed.
     *
     * @param millis The position to seek to, in milliseconds.
     */
    public void seek(long millis) {
        if (mediaPlayer != null && (getStatus() == MediaPlayer.Status.PLAYING || getStatus() == MediaPlayer.Status.PAUSED || getStatus() == MediaPlayer.Status.READY || getStatus() == MediaPlayer.Status.STOPPED)) {
            // Adjust seek time
            long adjustedMillis = Math.max(0, millis);
            long total = getTotalDurationMillis();
            if (total > 0 && adjustedMillis > total) {
                adjustedMillis = total; // Cap seek time
            }

            System.out.println("PlayerService: Seeking to " + adjustedMillis + "ms");

            // Create effectively final variable for lambda capture
            final long finalSeekMillis = adjustedMillis;

            mediaPlayer.seek(Duration.millis(finalSeekMillis)); // Perform seek

            // Manually update time if seeking while not actively playing
            if (getStatus() != MediaPlayer.Status.PLAYING) {
                 Platform.runLater(() -> {
                      // Check status again in case it changed right after seek call
                      if (getStatus() != MediaPlayer.Status.PLAYING) {
                           currentTimeMillisWrapper.set(finalSeekMillis); // Update time property
                      }
                 });
            }
        } else if (mediaPlayer != null) {
             System.err.println("PlayerService: Cannot seek in current state: " + getStatus());
        } else {
             System.err.println("PlayerService: Cannot seek, no media loaded.");
        }
    }

    /** Disposes the internal MediaPlayer instance, releasing system resources. */
    public void dispose() {
        disposePlayer(); // Call helper to dispose player
        System.out.println("PlayerService: Service disposed.");
    }

    // --- Private Helper Methods ---

    /** Sets up all necessary listeners on the current mediaPlayer instance. */
    private void addMediaPlayerListeners() {
        if (mediaPlayer == null) return;

        // Status listener: Update internal status wrapper
        mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            statusWrapper.set(newStatus);
        });

        // Time listener: Update internal time wrapper
        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime != null) {
                currentTimeMillisWrapper.set((long) newTime.toMillis());
            }
        });

        // Ready handler: Update duration and trigger auto-play if requested
        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getTotalDuration();
            long totalMillis = 0L;
            if (total != null && !total.isUnknown() && !total.isIndefinite()) {
                totalMillis = (long) total.toMillis();
            }
            totalDurationMillisWrapper.set(totalMillis); // Update duration property

            // --- Auto-play logic ---
            if (playWhenReady) {
                System.out.println("PlayerService: Media ready, auto-playing...");
                mediaPlayer.play(); // Start playback now
                playWhenReady = false; // Reset flag after use
            } else {
                 System.out.println("PlayerService: Media ready (auto-play disabled).");
            }
            // Status becomes READY via its own listener.
        });
        

        // End of media handler: Stop playback
        mediaPlayer.setOnEndOfMedia(() -> {
            System.out.println("PlayerService: End of media reached for " + (getCurrentSong() != null ? getCurrentSong().getTitle() : "media"));
            stop(); // Call service's stop method (resets flag, handles status via listener)
        });

        // Error handler: Use centralized error handling
        mediaPlayer.setOnError(() -> {
            MediaException error = mediaPlayer.getError();
            handleLoadError("MediaPlayer internal error", (getCurrentSong() != null ? getCurrentSong().getAudioFilePath() : "N/A"), error);
            // playWhenReady is reset inside handleLoadError via disposePlayer
        });
    }

    /**
     * Handles errors occurring during media loading or playback.
     * Logs the error, disposes the player, and resets state wrappers.
     */
    private void handleLoadError(String message, String filePath, Throwable throwable) {
        System.err.println("PlayerService Error: " + message + " [" + filePath + "]");
        if (throwable != null) {
            System.err.println(" -> Cause: " + throwable.getMessage());
            // Only print stack trace for unexpected errors
            if (!(throwable instanceof IOException || throwable instanceof MediaException || throwable instanceof SecurityException)) {
                throwable.printStackTrace();
            }
        }

        disposePlayer(); // Clean up player resources (also resets playWhenReady)

        // Reset observable properties to indicate error state
        statusWrapper.set(MediaPlayer.Status.HALTED);
        currentTimeMillisWrapper.set(0L);
        totalDurationMillisWrapper.set(0L);
        // currentSongWrapper is set to null inside disposePlayer()
    }

    /**
     * Safely stops and disposes the current mediaPlayer instance, resetting all relevant state.
     */
    private void disposePlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception e) { /* Ignore errors stopping */ }
            try {
                mediaPlayer.dispose();
                System.out.println("PlayerService: Disposed existing MediaPlayer.");
            } catch (Exception e) {
                System.err.println("PlayerService: Error disposing media player: " + e.getMessage());
            } finally {
                mediaPlayer = null;
            }
        }

        // Always reset state when player is disposed or wasn't present
        playWhenReady = false; // Reset auto-play flag
        if (currentSongWrapper.get() != null) currentSongWrapper.set(null); // Clear current song
        if (getStatus() != MediaPlayer.Status.UNKNOWN) statusWrapper.set(MediaPlayer.Status.UNKNOWN);
        if (getCurrentTimeMillis() != 0L) currentTimeMillisWrapper.set(0L);
        if (getTotalDurationMillis() != 0L) totalDurationMillisWrapper.set(0L);
    }
}
