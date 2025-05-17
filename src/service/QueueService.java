package service;

// --- JavaFX Imports ---
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

// --- Model Imports ---
import model.Song;

// --- Java Util Imports ---
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Service responsible for managing the song playback queue.
 * This service allows adding songs, retrieving the next song for playback,
 * peeking at upcoming songs, checking the queue's status (size, emptiness),
 * and clearing the queue. It uses an {@link ObservableList} to allow UI components
 * to listen for changes in the queue.
 *
 * <p>SRS References:
 * <ul>
 *  <li>FR2.8 (Add to Queue): Implemented by {@link #addSong(Song)} and {@link #addSongs(List)}.
 *  <li>FR2.9 (View Queue): Supported by {@link #getQueue()} and {@link #peekNextSongs(int)}.
 *  <li>FR2.10 (Automatic Song Transition): {@link #getNextSong()} is used by playback logic to get the next song.
 *  <li>UC3, Alternative Flow (User Stops) & UC3, Alternative Flow (User Skips): Clearing queue functionality is related to stopping or skipping songs which can lead to queue clearing.
 * </ul>
 */
public class QueueService {

    // Use an ObservableList wrapping a LinkedList for efficient add/remove at start/end
    private final ObservableList<Song> songQueue =
            FXCollections.observableList(new LinkedList<>());

    /**
     * Adds a single song to the end of the playback queue.
     * If the provided song is null, no action is taken.
     * This change will be reflected in the observable list returned by {@link #getQueue()}.
     * <p>FR2.8: Add to Queue.
     *
     * @param song The {@link Song} to add to the queue.
     */
    public void addSong(Song song) {
        if (song != null) {
            songQueue.add(song);
            System.out.println("QueueService: Added '" + song.getTitle() + "' to queue. Current size: " + songQueue.size());
        } else {
            System.out.println("QueueService: Attempted to add a null song to the queue. No action taken.");
        }
    }

    /**
     * Adds a list of songs to the end of the playback queue.
     * If the provided list is null or empty, no action is taken.
     * This change will be reflected in the observable list returned by {@link #getQueue()}.
     * <p>FR2.8: Add to Queue.
     *
     * @param songs The {@link List} of {@link Song} objects to add to the queue.
     */
    public void addSongs(List<Song> songs) {
        if (songs != null && !songs.isEmpty()) {
            songQueue.addAll(songs);
            System.out.println("QueueService: Added " + songs.size() + " songs to queue. Current size: " + songQueue.size());
        } else {
            System.out.println("QueueService: Attempted to add a null or empty list of songs. No action taken.");
        }
    }

    /**
     * Removes and returns the next song from the front of the queue.
     * If the queue is empty, this method returns {@code null}.
     * This operation modifies the queue.
     * <p>FR1.5, FR2.10: Used for automatic song transition and manual skip.
     *
     * @return The next {@link Song} from the queue, or {@code null} if the queue is empty.
     */
    public Song getNextSong() {
        if (!songQueue.isEmpty()) {
            Song next = songQueue.remove(0); // remove(0) from LinkedList is O(1)
            System.out.println("QueueService: Retrieving next song '" + (next != null ? next.getTitle() : "null") + "'. Remaining size: " + songQueue.size());
            return next;
        }
        System.out.println("QueueService: getNextSong called on empty queue.");
        return null;
    }

    /**
     * Returns an immutable list of the next few songs in the queue without removing them.
     * This allows for displaying upcoming songs in the UI.
     * <p>FR2.9: View Queue (partially supports by allowing peeking).
     *
     * @param count The maximum number of songs to peek at. Must be positive.
     * @return An immutable {@link List} containing the next 'count' songs,
     *         or fewer if the queue is smaller. Returns an empty list if count is not positive
     *         or the queue is empty.
     */
    public List<Song> peekNextSongs(int count) {
        if (songQueue.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        // Create a sublist view and then copy it to a new list to ensure immutability
        // and prevent issues if the underlying queue is modified elsewhere.
        int end = Math.min(count, songQueue.size());
        return List.copyOf(songQueue.subList(0, end));
    }

    /**
     * Gets the underlying {@link ObservableList} representing the queue.
     * This allows UI components or other services to add listeners and react to changes
     * in the queue (e.g., songs added, removed, or cleared).
     * <p>FR2.9: View Queue (primary mechanism for UI updates).
     *
     * @return The {@link ObservableList} of {@link Song} objects in the queue.
     */
    public ObservableList<Song> getQueue() {
        return songQueue;
    }

    /**
     * Gets the current number of songs in the playback queue.
     *
     * @return The size of the queue (number of songs).
     */
    public int getSize() {
        return songQueue.size();
    }

    /**
     * Checks if the playback queue is currently empty.
     *
     * @return {@code true} if the queue has no songs, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return songQueue.isEmpty();
    }

    /**
     * Removes all songs from the playback queue.
     * This operation modifies the queue and will trigger listeners on the observable list.
     * <p>UC3, Alternative Flow (User Stops): Stop action clears the queue.
     */
    public void clearQueue() {
        if (!songQueue.isEmpty()) {
            songQueue.clear();
            System.out.println("QueueService: Queue cleared. All songs removed.");
        } else {
            System.out.println("QueueService: clearQueue called, but queue was already empty.");
        }
    }
}
