package service; // Define the package for service classes

import model.LyricLine; // Import LyricLine model
import model.Song;      // Import Song model
import model.SongLyrics; // Import SongLyrics model
import util.LrcParser;   // Import the parser utility

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.beans.property.ReadOnlyObjectProperty; // For observable property
import javafx.beans.property.ReadOnlyObjectWrapper; // For observable property wrapper
import java.util.Objects; // For null-safe comparison


/**
 * Service class to manage loading and querying lyrics data (SongLyrics).
 * Centralizes lyrics loading (FR3.1, FR3.2) and provides access to relevant
 * lyric lines (previous, current, next two) based on playback time (FR3.4).
 */
public class LyricsService {

    private Song currentSong; // Track which song's lyrics are loaded
    private SongLyrics currentLyrics; // Holds the parsed lyrics object

    // --- Observable Property for UI ---

    // Wraps the list of lines to be displayed (previous, current, next, next+1)
    // This allows UI controllers to observe changes easily.
    private final ReadOnlyObjectWrapper<List<LyricLine>> displayLinesWrapper =
            new ReadOnlyObjectWrapper<>(this, "displayLines", Collections.emptyList());

    /**
     * A read-only observable property containing the list of lyric lines relevant
     * to the current playback time (previous, current, next, next+1).
     * UI components can listen to this property for updates.
     *
     * @return ReadOnlyObjectProperty holding an unmodifiable List of LyricLine.
     */
    public ReadOnlyObjectProperty<List<LyricLine>> displayLinesProperty() {
        return displayLinesWrapper.getReadOnlyProperty();
    }

    /**
     * Gets the current list of lines intended for display.
     * @return An unmodifiable List of LyricLine.
     */
    public List<LyricLine> getDisplayLines() {
        return displayLinesWrapper.get();
    }

    // --- Service Methods ---

    /**
     * Loads the lyrics for the specified song using LrcParser.
     * Updates the internal state and clears the display lines if loading fails
     * or the song has no lyrics.
     *
     * @param song The Song object whose lyrics should be loaded. Can be null to clear current lyrics.
     * @return true if lyrics were successfully loaded or cleared (for null song), false if loading failed.
     */
    public boolean loadLyricsForSong(Song song) {
        // Handle clearing lyrics if null song is passed
        if (song == null) {
            if (this.currentSong != null) { // Only log/update if clearing existing lyrics
                System.out.println("LyricsService: Clearing lyrics.");
                this.currentSong = null;
                this.currentLyrics = null;
                updateCurrentDisplayLines(0); // Update property with empty list
            }
            return true; // Clearing is considered a success
        }

        // Avoid reloading if the same song's lyrics are already loaded
        if (song.equals(this.currentSong) && this.currentLyrics != null) {
            // Lyrics already loaded, maybe force an update based on current time if needed elsewhere
            // updateDisplayLines(playerService.getCurrentTimeMillis()); // Example if needed
            return true;
        }

        System.out.println("LyricsService: Attempting to load lyrics for '" + song.getTitle() + "'...");
        this.currentSong = song; // Update current song reference
        this.currentLyrics = null; // Reset lyrics before loading attempt
        boolean success = false;

        String lyricsPath = song.getLyricsFilePath();
        if (lyricsPath == null || lyricsPath.isBlank()) {
            System.err.println("LyricsService: Song '" + song.getTitle() + "' has no associated lyrics file path.");
            // Ensure displayLines property is cleared
            if (!displayLinesWrapper.get().isEmpty()) {
                displayLinesWrapper.set(Collections.emptyList());
            }
        } else {
            try {
                // Parse the LRC file to get the SongLyrics object
                this.currentLyrics = LrcParser.parseLyrics(lyricsPath);
                System.out.println("LyricsService: Successfully loaded " + this.currentLyrics.getSize() + " lines for '" + song.getTitle() + "' (Offset: " + this.currentLyrics.getOffsetMillis() + "ms).");
                success = true;
                // Initial update of display lines (e.g., show first few lines at time 0)
                updateCurrentDisplayLines(0);
            } catch (IOException | InvalidPathException e) {
                System.err.println("LyricsService: I/O or Path error parsing lyrics file '" + lyricsPath + "': " + e.getMessage());
                this.currentLyrics = null; // Ensure null on error
                 if (!displayLinesWrapper.get().isEmpty()) { displayLinesWrapper.set(Collections.emptyList()); }
            } catch (Exception e) { // Catch any other unexpected parsing errors
                 System.err.println("LyricsService: Unexpected error parsing lyrics file '" + lyricsPath + "': " + e.getMessage());
                 e.printStackTrace();
                 this.currentLyrics = null;
                 if (!displayLinesWrapper.get().isEmpty()) { displayLinesWrapper.set(Collections.emptyList()); }
            }
        }
        return success;
    }

    /**
     * Updates the observable list of display lines (previous, current, next, next+1)
     * based on the provided playback time. This should be called regularly by the
     * controller listening to the PlayerService's time property.
     *
     * @param currentPlaybackMillis The current playback time in milliseconds.
     */
    public void updateCurrentDisplayLines(long currentPlaybackMillis) {
        List<LyricLine> linesToShow = new ArrayList<>(); // Max size 4

        if (currentLyrics == null || currentLyrics.isEmpty()) {
            // No lyrics loaded or the file was empty, ensure property reflects this
            if (!displayLinesWrapper.get().isEmpty()) {
                displayLinesWrapper.set(Collections.unmodifiableList(linesToShow)); // Set to empty list
            }
            return; // Nothing to display
        }

        // Find the index of the line currently active (last line whose time <= playback time)
        int currentIndex = currentLyrics.getIndexAtTime(currentPlaybackMillis); // Uses SongLyrics logic
        List<LyricLine> allLines = currentLyrics.getLines();
        int totalLines = allLines.size();

        // Determine the indices to fetch: [currentIndex - 1, currentIndex + 2]
        int prevIndex = currentIndex - 1;
        int next1Index = currentIndex + 1;
        int next2Index = currentIndex + 2;

        // Add previous line if valid index
        if (prevIndex >= 0) {
            linesToShow.add(allLines.get(prevIndex));
        } else {
             linesToShow.add(null); // Placeholder for previous if current is first or before first
        }

        // Add current line if valid index
        if (currentIndex >= 0) {
            linesToShow.add(allLines.get(currentIndex));
        } else {
             linesToShow.add(null); // Placeholder for current if time is before the first line
        }

        // Add next line if valid index
        if (next1Index < totalLines) {
            linesToShow.add(allLines.get(next1Index));
        } else {
             linesToShow.add(null); // Placeholder for next if current is last
        }

        // Add second next line if valid index
        if (next2Index < totalLines) {
            linesToShow.add(allLines.get(next2Index));
        } else {
             linesToShow.add(null); // Placeholder for next+1 if current is last or second-to-last
        }

        // Create an unmodifiable list for the property
        // Note: This list may contain nulls as placeholders
        List<LyricLine> newDisplayLines = Collections.unmodifiableList(linesToShow);

        // Only update the property if the list content actually changes
        // Custom comparison needed as list might contain same lines but different null placeholders
        if (!areLineListsEffectivelyEqual(displayLinesWrapper.get(), newDisplayLines)) {
            displayLinesWrapper.set(newDisplayLines);
             // Debug: System.out.println("Time: " + currentPlaybackMillis + "ms, CurrentIdx: " + currentIndex + ", Lines: " + newDisplayLines.stream().map(l -> l != null ? l.getText() : "null").collect(Collectors.joining(" | ")));
        }
    }

    /**
     * Helper to compare two lists of LyricLine, considering null placeholders.
     */
    private boolean areLineListsEffectivelyEqual(List<LyricLine> list1, List<LyricLine> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }
        for (int i = 0; i < list1.size(); i++) {
            if (!Objects.equals(list1.get(i), list2.get(i))) { // Objects.equals handles nulls correctly
                return false;
            }
        }
        return true;
    }

     /**
      * Gets the currently loaded SongLyrics object. Primarily for internal use or testing.
      * @return The current SongLyrics object, or null if none loaded or error occurred.
      */
     public SongLyrics getCurrentLyricsObject() {
         return currentLyrics;
     }
}
