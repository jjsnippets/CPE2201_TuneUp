package service; // Define the package for service classes

import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import model.Song; // Import the Song model

import java.io.File; // Import File class

/**
 * Service class that encapsulates JavaFX MediaPlayer functionality.
 * Manages loading audio (FR1.1), playback controls (FR1.3, FR1.4), seeking (FR1.7),
 * and exposes observable properties for playback state and time (FR1.6).
 * Abstracts low-level JavaFX media handling from controllers.
 */
public class PlayerService {

    private MediaPlayer mediaPlayer;
    private Song currentSong; // Keep track of the currently loaded song

    // --- Observable Properties ---

    // Wraps the MediaPlayer status, providing a read-only property for observers.
    private final ReadOnlyObjectWrapper<MediaPlayer.Status> statusWrapper =
            new ReadOnlyObjectWrapper<>(this, "status", MediaPlayer.Status.UNKNOWN);
    public final ReadOnlyObjectProperty<MediaPlayer.Status> statusProperty() {
        return statusWrapper.getReadOnlyProperty();
    }
    public final MediaPlayer.Status getStatus() {
        return statusWrapper.get();
    }

    // Wraps the current playback time, providing a read-only property (in milliseconds).
    private final ReadOnlyLongWrapper currentTimeMillisWrapper =
            new ReadOnlyLongWrapper(this, "currentTimeMillis", 0L);
    public final ReadOnlyLongProperty currentTimeProperty() {
        return currentTimeMillisWrapper.getReadOnlyProperty();
    }
    public final long getCurrentTimeMillis() {
        return currentTimeMillisWrapper.get();
    }

    // Wraps the total duration of the media, providing a read-only property (in milliseconds).
    private final ReadOnlyLongWrapper totalDurationMillisWrapper =
            new ReadOnlyLongWrapper(this, "totalDurationMillis", 0L);
    public final ReadOnlyLongProperty totalDurationProperty() {
        return totalDurationMillisWrapper.getReadOnlyProperty();
    }
    public final long getTotalDurationMillis() {
        return totalDurationMillisWrapper.get();
    }

    // --- Public Service Methods ---

    /**
     * Loads the audio file from the given Song's audioFilePath.
     * Disposes of any existing MediaPlayer before creating a new one.
     * Updates status, current time, and total duration properties.
     * Implements FR1.1.
     *
     * @param song The Song object to load. Can be null to unload.
     */
    public void loadSong(Song song) {
        disposePlayer(); // Dispose previous player if exists
        this.currentSong = song; // Update current song reference

        if (song == null || song.getAudioFilePath() == null || song.getAudioFilePath().isBlank()) {
            // Reset properties if song is null or has no valid path
            statusWrapper.set(MediaPlayer.Status.UNKNOWN);
            currentTimeMillisWrapper.set(0L);
            totalDurationMillisWrapper.set(0L);
            System.err.println("PlayerService: Cannot load null song or song with invalid audio path.");
            return;
        }

        try {
            // Create Media object from file path
            File audioFile = new File(song.getAudioFilePath());
            if (!audioFile.exists()) {
                throw new IllegalArgumentException("Audio file not found: " + song.getAudioFilePath());
            }
            // Convert file path to a valid URI string for Media constructor
            String mediaUriString = audioFile.toURI().toString();
            Media media = new Media(mediaUriString);

            // Create new MediaPlayer
            mediaPlayer = new MediaPlayer(media);

            // --- Setup Listeners to update Service Properties ---

            // Update status property when MediaPlayer status changes
            mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
                // Ensure updates run on the JavaFX Application Thread if modifying UI directly elsewhere
                // Platform.runLater(() -> statusWrapper.set(newStatus)); // Usually safe as listeners often called on FX thread
                 statusWrapper.set(newStatus);
            });

            // Update currentTimeMillis property when MediaPlayer time changes
            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (newTime != null) {
                    // Platform.runLater(() -> currentTimeMillisWrapper.set((long) newTime.toMillis()));
                     currentTimeMillisWrapper.set((long) newTime.toMillis());
                }
            });

            // Update totalDurationMillis property once the media is ready
            mediaPlayer.setOnReady(() -> {
                Duration total = mediaPlayer.getTotalDuration();
                if (total != null && !total.isUnknown() && !total.isIndefinite()) {
                    // Platform.runLater(() -> totalDurationMillisWrapper.set((long) total.toMillis()));
                     totalDurationMillisWrapper.set((long) total.toMillis());
                } else {
                     totalDurationMillisWrapper.set(0L); // Indicate unknown/invalid duration
                }
                // Can set status to READY here explicitly if needed, but listener should handle it
                // statusWrapper.set(MediaPlayer.Status.READY);
            });

             // Handle end of media event (e.g., for auto-play next song in queue)
             mediaPlayer.setOnEndOfMedia(() -> {
                 // Reset time to 0, keep status as STOPPED (or READY depending on desired behavior)
                 // Could potentially fire an event here for the controller to handle 'next song' logic
                 stop(); // Stop sets time to 0 typically
                 statusWrapper.set(MediaPlayer.Status.STOPPED); // Or READY?
                 System.out.println("PlayerService: End of media reached for " + currentSong.getTitle());
             });

            // Handle player errors
            mediaPlayer.setOnError(() -> {
                System.err.println("MediaPlayer Error: " + mediaPlayer.getError());
                statusWrapper.set(MediaPlayer.Status.HALTED);
                currentTimeMillisWrapper.set(0L);
                // Optionally try to dispose and nullify mediaPlayer on error
                // disposePlayer();
            });

            // Initial state after creation (will transition to READY soon)
            statusWrapper.set(mediaPlayer.getStatus()); // Set initial status
            currentTimeMillisWrapper.set(0L);
            totalDurationMillisWrapper.set(0L); // Duration unknown until READY

            System.out.println("PlayerService: Successfully loaded song - " + song.getTitle());

        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            // Catch specific errors related to path/URI or media format
            System.err.println("PlayerService Error loading media '" + song.getAudioFilePath() + "': " + e.getMessage());
            disposePlayer(); // Ensure cleanup on error
            statusWrapper.set(MediaPlayer.Status.HALTED);
            currentTimeMillisWrapper.set(0L);
            totalDurationMillisWrapper.set(0L);
        } catch (Exception e) {
            // Catch any other unexpected errors
            System.err.println("PlayerService Unexpected error loading media: " + e.getMessage());
            e.printStackTrace();
            disposePlayer();
            statusWrapper.set(MediaPlayer.Status.HALTED);
            currentTimeMillisWrapper.set(0L);
            totalDurationMillisWrapper.set(0L);
        }
    }

    /**
     * Starts or resumes playback of the currently loaded song.
     * Implements FR1.2 (implied by Play).
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

    /**
     * Pauses the currently playing song.
     * Implements FR1.3.
     */
    public void pause() {
        if (mediaPlayer != null && getStatus() == MediaPlayer.Status.PLAYING) {
             System.out.println("PlayerService: Pausing " + (currentSong != null ? currentSong.getTitle() : "media"));
            mediaPlayer.pause();
        }
    }

    /**
     * Stops playback and typically resets the playback position to the beginning.
     * Implements FR1.4.
     */
    public void stop() {
        if (mediaPlayer != null) {
             System.out.println("PlayerService: Stopping " + (currentSong != null ? currentSong.getTitle() : "media"));
            mediaPlayer.stop();
            // Note: stop() usually resets currentTimeProperty automatically.
            // If not observed reliably, manually set: currentTimeMillisWrapper.set(0L);
        }
    }

    /**
     * Seeks to the specified position in the media.
     * Implements FR1.7.
     *
     * @param millis The position to seek to, in milliseconds.
     */
    public void seek(long millis) {
        if (mediaPlayer != null && (getStatus() == MediaPlayer.Status.PLAYING || getStatus() == MediaPlayer.Status.PAUSED || getStatus() == MediaPlayer.Status.READY)) {
             if (millis < 0) millis = 0; // Ensure non-negative seek time

             // Avoid seeking beyond duration if possible, though MediaPlayer might handle it
             long total = getTotalDurationMillis();
             if (total > 0 && millis > total) {
                 millis = total;
             }

            System.out.println("PlayerService: Seeking to " + millis + "ms");
            mediaPlayer.seek(Duration.millis(millis));
            // Manually update wrapper if listener isn't fast enough or seeking from STOPPED state
             if (getStatus() != MediaPlayer.Status.PLAYING && getStatus() != MediaPlayer.Status.PAUSED) {
                 // If seeking from READY or STOPPED, the listener might not fire immediately
                 currentTimeMillisWrapper.set(millis);
             }
        } else if (mediaPlayer != null) {
            System.err.println("PlayerService: Cannot seek in current state: " + getStatus());
        }
    }

    /**
     * Gets the currently loaded Song object.
     * @return The current Song, or null if none is loaded.
     */
    public Song getCurrentSong() {
        return currentSong;
    }

    /**
     * Disposes the internal MediaPlayer instance, releasing system resources.
     * Should be called when the service is no longer needed (e.g., application shutdown).
     */
    public void dispose() {
        disposePlayer();
        System.out.println("PlayerService: Disposed.");
    }

    /**
     * Private helper to dispose of the media player safely.
     */
    private void disposePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop(); // Stop playback before disposing
                mediaPlayer.dispose(); // Release resources
                System.out.println("PlayerService: Disposed existing MediaPlayer.");
            } catch (Exception e) {
                System.err.println("PlayerService: Error during MediaPlayer disposal: " + e.getMessage());
            } finally {
                mediaPlayer = null; // Ensure reference is cleared
                currentSong = null; // Clear current song reference
            }
        }
    }
}
