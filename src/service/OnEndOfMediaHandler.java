package service;

/**
 * A functional interface for handling the end of media playback.
 * This interface is intended to be implemented by classes that need to perform actions
 * when a media player (e.g., in {@link MediaPlayerService}) reaches the end of a song.
 * The single method {@link #handle()} will be invoked at that point.
 *
 * <p>SRS: This supports requirements related to playback control, specifically FR2.10 (Automatic Song Transition), by providing a hook for the {@link controller.MainController} to manage song progression.
 */
@FunctionalInterface
public interface OnEndOfMediaHandler {
    /**
     * Called when the media playback has finished.
     * Implementations should define the specific actions to take, such as playing the next song,
     * stopping playback, or repeating the current song.
     */
    void handle();
}
