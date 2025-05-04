package service; // Place in the service package

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.Song; // Import the Song model

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Service responsible for managing the song playback queue.
 * Provides methods to add songs, retrieve the next song, and observe the queue state.
 * Supports FR2.8, FR2.9, FR2.10 (implicitly).
 */
public class QueueService {

    // Use an ObservableList wrapping a LinkedList for efficient add/remove at start/end
    private final ObservableList<Song> songQueue =
            FXCollections.observableList(new LinkedList<>());

    /**
     * Adds a song to the end of the queue.
     * FR2.8
     *
     * @param song The song to add. Must not be null.
     */
    public void addSong(Song song) {
        if (song != null) {
            // The modification to songQueue will automatically trigger listeners
            // attached via getQueue().addListener(...)
            songQueue.add(song);
            System.out.println("QueueService: Added '" + song.getTitle() + "' to queue. Size: " + songQueue.size());
        }
    }

    /**
     * Adds multiple songs to the end of the queue.
     *
     * @param songs The list of songs to add.
     */
    public void addSongs(List<Song> songs) {
        if (songs != null && !songs.isEmpty()) {
            songQueue.addAll(songs);
             System.out.println("QueueService: Added " + songs.size() + " songs to queue. Size: " + songQueue.size());
        }
    }


    /**
     * Removes and returns the next song from the front of the queue.
     * Returns null if the queue is empty.
     * FR2.10 (used by skip/next logic)
     *
     * @return The next Song, or null if queue is empty.
     */
    public Song getNextSong() {
        if (!songQueue.isEmpty()) {
            // remove(0) removes from the front of the LinkedList/ObservableList
            Song next = songQueue.remove(0);
             System.out.println("QueueService: Playing next '" + next.getTitle() + "'. Remaining size: " + songQueue.size());
            return next;
        }
        return null;
    }

    /**
     * Returns a view of the next few songs in the queue without removing them.
     *
     * @param count The maximum number of songs to peek at.
     * @return An immutable List containing the next 'count' songs (or fewer if queue is smaller).
     */
    public List<Song> peekNextSongs(int count) {
        if (songQueue.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }
        // Create a sublist view and copy it to prevent modification issues
        int end = Math.min(count, songQueue.size());
        return List.copyOf(songQueue.subList(0, end));
    }


    /**
     * Gets the underlying observable list representing the queue.
     * Controllers can add listeners to this list to react to changes.
     *
     * @return The ObservableList of Songs in the queue.
     */
    public ObservableList<Song> getQueue() {
        return songQueue;
    }

    /**
     * Gets the current number of songs in the queue.
     *
     * @return The size of the queue.
     */
    public int getSize() {
        return songQueue.size();
    }

    /**
     * Checks if the queue is currently empty.
     *
     * @return true if the queue has no songs, false otherwise.
     */
    public boolean isEmpty() {
        return songQueue.isEmpty();
    }

    /**
     * Removes all songs from the queue.
     */
    public void clear() {
        if (!songQueue.isEmpty()) {
            songQueue.clear();
            System.out.println("QueueService: Queue cleared.");
        }
    }
}
