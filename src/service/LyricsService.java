package service; // Define the package for service classes

import model.LyricLine;
import model.Song;
import model.SongLyrics;
import util.LrcParser;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects; // Keep for areLineListsEffectivelyEqual if needed, or remove if using List.equals()
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Service class to manage loading and querying lyrics data (SongLyrics).
 * Centralizes lyrics loading (FR3.1, FR3.2) and provides access to relevant
 * lyric lines (previous, current, next two) based on playback time (FR3.4).
 * Refactored for minor clarity improvements.
 */
public class LyricsService {

    private Song currentSong;
    private SongLyrics currentLyrics;

    // --- Observable Property for UI ---
    private final ReadOnlyObjectWrapper<List<LyricLine>> displayLinesWrapper =
            new ReadOnlyObjectWrapper<>(this, "displayLines", Collections.emptyList());

    /**
     * A read-only observable property containing the list of lyric lines relevant
     * to the current playback time (previous, current, next, next+1).
     * UI components can listen to this property for updates.
     * The list may contain null elements as placeholders if lines are unavailable.
     *
     * @return ReadOnlyObjectProperty holding an unmodifiable List of LyricLine.
     */
    public ReadOnlyObjectProperty<List<LyricLine>> displayLinesProperty() {
        return displayLinesWrapper.getReadOnlyProperty();
    }

    /** Gets the current list of lines intended for display. */
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
        // Handle clearing lyrics
        if (song == null) {
            if (this.currentSong != null) {
                System.out.println("LyricsService: Clearing lyrics.");
                this.currentSong = null;
                this.currentLyrics = null;
                updateCurrentDisplayLines(0); // Reset display
            }
            return true;
        }

        // Avoid redundant loading
        if (song.equals(this.currentSong) && this.currentLyrics != null) {
            return true;
        }

        System.out.println("LyricsService: Attempting to load lyrics for '" + song.getTitle() + "'...");
        this.currentSong = song; // Update current song reference *before* loading
        this.currentLyrics = null; // Reset lyrics state
        boolean success = false;

        String lyricsPath = song.getLyricsFilePath();
        if (lyricsPath == null || lyricsPath.isBlank()) {
            System.err.println("LyricsService: Song '" + song.getTitle() + "' has no associated lyrics file path.");
            updateCurrentDisplayLines(0); // Ensure display is cleared
        } else {
            try {
                // Delegate parsing to LrcParser
                this.currentLyrics = LrcParser.parseLyrics(lyricsPath);
                System.out.println("LyricsService: Loaded " + this.currentLyrics.getSize() + " lines for '" + song.getTitle() + "' (Offset: " + this.currentLyrics.getOffsetMillis() + "ms).");
                success = true;
                updateCurrentDisplayLines(0); // Initialize display for time 0
            } catch (IOException | InvalidPathException e) {
                System.err.println("LyricsService: I/O or Path error parsing lyrics file '" + lyricsPath + "': " + e.getMessage());
                this.currentLyrics = null; // Ensure state is null on error
                updateCurrentDisplayLines(0); // Clear display
            } catch (Exception e) { // Catch other unexpected parsing errors
                 System.err.println("LyricsService: Unexpected error parsing lyrics file '" + lyricsPath + "': " + e.getMessage());
                 e.printStackTrace();
                 this.currentLyrics = null;
                 updateCurrentDisplayLines(0); // Clear display
            }
        }
        return success;
    }

    /**
     * Updates the observable list of display lines (previous, current, next, next+1)
     * based on the provided playback time.
     *
     * @param currentPlaybackMillis The current playback time in milliseconds.
     */
    public void updateCurrentDisplayLines(long currentPlaybackMillis) {
        List<LyricLine> newDisplayLines;

        if (currentLyrics == null || currentLyrics.isEmpty()) {
            // If no lyrics loaded or empty, the display list should be empty
            newDisplayLines = Collections.emptyList();
        } else {
            // Get the index of the line active at the current time
            int currentIndex = currentLyrics.getIndexAtTime(currentPlaybackMillis);
            List<LyricLine> allLines = currentLyrics.getLines();

            // Prepare the list, potentially with null placeholders
            List<LyricLine> linesToShow = new ArrayList<>(4); // Capacity 4
            linesToShow.add(getLineAtIndexSafe(allLines, currentIndex - 1)); // Previous
            linesToShow.add(getLineAtIndexSafe(allLines, currentIndex));     // Current
            linesToShow.add(getLineAtIndexSafe(allLines, currentIndex + 1)); // Next 1
            linesToShow.add(getLineAtIndexSafe(allLines, currentIndex + 2)); // Next 2

            // Make the list unmodifiable for the property
            newDisplayLines = Collections.unmodifiableList(linesToShow);
        }

        // Only update the property if the effective content has changed
        // List.equals() correctly handles null elements and uses LyricLine.equals()
        if (!newDisplayLines.equals(displayLinesWrapper.get())) {
            displayLinesWrapper.set(newDisplayLines);
            // Debug: System.out.println("Updated display lines: " + newDisplayLines);
        }
    }

    /**
     * Safely gets a LyricLine from a list by index.
     * Returns null if the list is null or the index is out of bounds.
     *
     * @param lines The list of LyricLine objects.
     * @param index The desired index.
     * @return The LyricLine at the index, or null.
     */
    private LyricLine getLineAtIndexSafe(List<LyricLine> lines, int index) {
        if (lines != null && index >= 0 && index < lines.size()) {
            return lines.get(index);
        }
        return null; // Return null if index is invalid or list is null
    }

     /**
      * Gets the currently loaded SongLyrics object. Primarily for internal use or testing.
      * @return The current SongLyrics object, or null if none loaded or error occurred.
      */
     public SongLyrics getCurrentLyricsObject() {
         return currentLyrics;
     }
}