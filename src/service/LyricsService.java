package service; // Define the package for service classes

// --- Model Imports ---
import model.LyricLine;
import model.Song;
import model.SongLyrics; // Now takes lines only in constructor

// --- Util Imports ---
import util.LrcParser;   // LrcParseResult now has one offset

// --- Java IO and NIO Imports ---
import java.io.IOException;
import java.nio.file.InvalidPathException;

// --- Java Util Imports ---
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
// import java.util.Objects; // Not strictly needed if List.equals() is used and LyricLine.equals() is sound

// --- JavaFX Imports ---
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Service class responsible for managing lyrics data for songs.
 * It handles loading lyrics from .lrc files using {@link LrcParser},
 * stores the parsed {@link SongLyrics}, and provides an observable list of
 * lyric lines (previous, current, and next two) synchronized with playback time.
 *
 * This service supports the following functional requirements:
 * - FR3.1: Parse LRC file for lyrics and offset. (Handled by LrcParser, managed by this service)
 * - FR3.2: Parse timestamps and text content. (Handled by LrcParser, data used by this service)
 * - FR3.4: Display synchronized lyrics lines (previous, current, next two).
 */
public class LyricsService {

    private Song currentSong;       // The song whose lyrics are currently loaded.
    private SongLyrics currentLyricsHolder; // Holds only lines
    private long initialLoadedOffsetMs = 0; // Offset read from [offset:...] in LRC

    // --- Observable Property for UI ---

    /**
     * Wrapper for the observable list of lyric lines to be displayed in the UI.
     * This list typically contains four elements:
     * 1. The previous lyric line (or null).
     * 2. The current lyric line (or null).
     * 3. The next lyric line (or null).
     * 4. The second next lyric line (or null).
     * The list is unmodifiable when exposed via displayLinesProperty().
     */
    private final ReadOnlyObjectWrapper<List<LyricLine>> displayLinesWrapper =
            new ReadOnlyObjectWrapper<>(this, "displayLines", Collections.emptyList());

    /**
     * Provides a read-only observable property containing the list of lyric lines
     * relevant to the current playback time (previous, current, next, and next+1).
     * UI components (e.g., {@code MainController}) can listen to this property to update
     * the lyrics display dynamically.
     * The returned list may contain null elements as placeholders if corresponding
     * lyric lines are unavailable (e.g., at the beginning or end of the song).
     *
     * @return A {@code ReadOnlyObjectProperty} holding an unmodifiable {@code List<LyricLine>}.
     *         Corresponds to FR3.4.
     */
    public ReadOnlyObjectProperty<List<LyricLine>> displayLinesProperty() {
        return displayLinesWrapper.getReadOnlyProperty();
    }

    /**
     * Gets the current list of lyric lines intended for display.
     * This is a snapshot of the list held by the {@code displayLinesWrapper}.
     *
     * @return An unmodifiable {@code List<LyricLine>} containing the current set of displayable lyric lines.
     */
    public List<LyricLine> getDisplayLines() {
        return displayLinesWrapper.get();
    }

    public Song getCurrentSong() { return currentSong; }

    /**
     * Gets the offset that was initially loaded from the LRC file's [offset:...] tag
     * for the currently loaded song.
     * @return The initial loaded offset in milliseconds.
     */
    public long getInitialLoadedOffsetMs() {
        return initialLoadedOffsetMs;
    }

    // --- Service Methods ---

    /**
     * Loads the lyrics for the specified song.
     * If a song is provided, it attempts to parse its associated .lrc file using {@link LrcParser}.
     * If the song is null, or if lyrics have already been loaded for the same song,
     * or if the song has no lyrics file path, or if parsing fails, the method handles
     * these cases appropriately, updating the internal state and the {@code displayLinesWrapper}.
     *
     * @param song The {@link Song} object whose lyrics should be loaded. Can be null to clear current lyrics.
     * @return {@code true} if lyrics were successfully loaded or cleared (for a null song),
     *         {@code false} if loading failed due to an error (e.g., file not found, parsing error).
     *         Supports FR3.1 and FR3.2 via {@link LrcParser}.
     */
    public boolean loadLyricsForSong(Song song) {
        if (song == null) {
            if (this.currentSong != null) { // Only log/update if actually clearing existing lyrics
                System.out.println("LyricsService: Clearing lyrics for previous song: " + this.currentSong.getTitle());
                clearLyricsInternal(); // Use internal helper to reset state
            }
            return true; // Clearing is considered a "successful" operation in this context
        }

        // Avoid reloading if it's the same song and lyrics are already conceptually loaded.
        // The actual offset value is managed by MainController live, so
        // LyricsService doesn't need to reload just for offset changes if lines are same.
        if (song.equals(this.currentSong) && this.currentLyricsHolder != null) {
            System.out.println("LyricsService: Lyrics for '" + song.getTitle() + "' conceptually already loaded.");
            // MainController will refresh display with its current live offset.
            return true;
        }

        clearLyricsInternal(); // Reset state for new song
        this.currentSong = song; // Update current song reference *before* attempting to load
        boolean success = false;

        String lyricsPath = song.getLyricsFilePath();
        if (lyricsPath == null || lyricsPath.isBlank()) {
            System.err.println("LyricsService: No lyrics file path for '" + song.getTitle() + "'.");
            // If loading failed, state is cleared. MainController will get 0 as initial offset.
            // updateCurrentDisplayLines(0,0); // Ensure display is cleared to an empty state - This will be handled by MainController
        } else {
            try {
                // Delegate parsing to LrcParser
                LrcParser.LrcParseResult parseResult = LrcParser.parseLyricsAndOffset(lyricsPath);
                this.currentLyricsHolder = new SongLyrics(parseResult.getLines()); // Store only lines
                this.initialLoadedOffsetMs = parseResult.getOffsetMillis();    // Store initial offset

                System.out.println("LyricsService: Loaded " + this.currentLyricsHolder.getSize() +
                                   " lines for '" + song.getTitle() +
                                   "' (InitialFileOffset: " + this.initialLoadedOffsetMs + "ms).");
                success = true;
                // updateCurrentDisplayLines(0, initialLoadedOffsetMs); // Initialize display for time 0 - This will be handled by MainController
            } catch (IOException | InvalidPathException e) {
                System.err.println("LyricsService: Error parsing lyrics for '" + lyricsPath + "': " + e.getMessage());
                clearLyricsInternal(); // Ensure state is null on error
            } catch (Exception e) { // Catch any other unexpected parsing errors
                System.err.println("LyricsService: Unexpected error parsing lyrics for '" + lyricsPath + "': " + e.getMessage());
                e.printStackTrace(); // Log stack trace for unexpected errors
                clearLyricsInternal();
            }
        }
        // If loading failed, state is cleared. MainController will get 0 as initial offset.
        // updateCurrentDisplayLines(0,0); // Clear display - This will be handled by MainController
        return success;
    }

    /**
     * Updates the observable list of display lines (previous, current, next, next+1)
     * based on the provided playback time and the total live offset.
     * This method is typically called by the
     * {@code MainController} in response to time updates from the {@code PlayerService}.
     *
     * @param currentPlaybackMillis Current playback time.
     * @param totalLiveOffsetFromController The current total effective offset managed by MainController.
     *                              Corresponds to FR3.4.
     */
    public void updateCurrentDisplayLines(long currentPlaybackMillis, long totalLiveOffsetFromController) {
        List<LyricLine> newDisplayLines;

        if (currentLyricsHolder == null || currentLyricsHolder.isEmpty()) {
            // If no lyrics are loaded or if the loaded lyrics are empty,
            // the display list should be empty.
            newDisplayLines = Collections.emptyList();
        } else {
            // Pass the totalLiveOffsetFromController to SongLyrics methods
            // Get the index of the lyric line that is currently active
            // (i.e., the last line whose effective timestamp is <= currentPlaybackMillis)
            int currentIndex = currentLyricsHolder.getIndexAtTime(currentPlaybackMillis, totalLiveOffsetFromController);
            List<LyricLine> allLines = currentLyricsHolder.getLines();

            // Prepare the list of lines to show (previous, current, next1, next2)
            // This list will always have 4 conceptual slots, filled with LyricLine objects or null.
            List<LyricLine> linesToShow = new ArrayList<>(4); // Initialize with capacity 4

            linesToShow.add(getLineAtIndexSafe(allLines, currentIndex - 1)); // Previous line
            linesToShow.add(getLineAtIndexSafe(allLines, currentIndex));     // Current line
            linesToShow.add(getLineAtIndexSafe(allLines, currentIndex + 1)); // Next line
            linesToShow.add(getLineAtIndexSafe(allLines, currentIndex + 2)); // Second next line

            // Make the list unmodifiable before setting it to the property
            newDisplayLines = Collections.unmodifiableList(linesToShow);
        }

        // Only update the observable property if the content of the list has actually changed.
        // This prevents unnecessary UI refreshes. List.equals() performs an element-wise
        // comparison, relying on LyricLine.equals(), and correctly handles nulls within the lists.
        if (!newDisplayLines.equals(displayLinesWrapper.get())) {
            displayLinesWrapper.set(newDisplayLines);
            // For debugging:
            // System.out.println("LyricsService: Updated display lines at " + currentPlaybackMillis + "ms with offset " + totalLiveOffsetFromController + "ms. Current index: " + (currentLyricsHolder != null ? currentLyricsHolder.getIndexAtTime(currentPlaybackMillis, totalLiveOffsetFromController) : -1) );
            // newDisplayLines.forEach(line -> System.out.println("  " + (line != null ? line.getText() : "null")));
        }
    }

    /**
     * Clears the currently loaded lyrics information and resets the display lines property to empty.
     * This is typically called when playback stops, the current song is unloaded from the player,
     * or the application is shutting down.
     */
    public void clearLyrics() {
        // Check if there's actually anything to clear to avoid redundant operations/logging
        if (this.currentSong != null || this.currentLyricsHolder != null || !this.displayLinesWrapper.get().isEmpty()) {
            System.out.println("LyricsService: Clearing current lyrics state.");
            clearLyricsInternal();
        }
    }

    /**
     * Internal helper to reset lyrics-related state.
     */
    private void clearLyricsInternal() {
        this.currentSong = null;
        this.currentLyricsHolder = null;
        this.initialLoadedOffsetMs = 0; // Reset the initial offset
        // Update the observable property to an empty list to clear the UI
        if (!this.displayLinesWrapper.get().isEmpty()) { // Only set if not already empty
            this.displayLinesWrapper.set(Collections.emptyList());
        }
    }


    /**
     * Safely retrieves a {@link LyricLine} from a list by its index.
     * Returns {@code null} if the list is null, the index is out of bounds,
     * or the element at the index is itself null.
     *
     * @param lines The list of {@code LyricLine} objects.
     * @param index The desired index.
     * @return The {@code LyricLine} at the specified index, or {@code null} if not accessible.
     */
    private LyricLine getLineAtIndexSafe(List<LyricLine> lines, int index) {
        if (lines != null && index >= 0 && index < lines.size()) {
            return lines.get(index); // LyricLine objects themselves can be null if list allows
        }
        return null; // Index is invalid or list is null
    }

    /**
     * Gets the currently loaded {@link SongLyrics} object (which now only holds lines).
     * This method is primarily for internal use or testing purposes.
     *
     * @return The current {@code SongLyrics} object, or {@code null} if no lyrics are loaded
     *         or an error occurred during loading.
     */
    public SongLyrics getCurrentLyricsObject() { // Renamed from getCurrentLyrics to avoid conflict if SongLyrics was the direct type
        return currentLyricsHolder;
    }
}