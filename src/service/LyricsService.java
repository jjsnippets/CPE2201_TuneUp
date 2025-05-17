package service;

// --- Model Imports ---
import model.LyricLine;
import model.Song;
import model.SongLyrics;

// --- Util Imports ---
import util.LrcParser;

// --- Java IO and NIO Imports ---
import java.io.IOException;
import java.nio.file.InvalidPathException;

// --- Java Util Imports ---
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// --- JavaFX Imports ---
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Manages the loading, processing, and provision of song lyrics within the application.
 * This service is responsible for parsing LRC files, storing the resulting {@link SongLyrics},
 * and providing an observable list of lyric lines (previous, current, and next two)
 * synchronized with the song's playback time and effective offset.
 * <p>
 * Key functionalities and SRS correlations:
 * <ul>
 *   <li><b>FR3.1 (Parse LRC File):</b> Leverages {@link LrcParser} to read LRC files, extracting
 *       both the lyric lines and any global offset defined in an {@code [offset:...]} tag.</li>
 *   <li><b>FR3.2 (Parse Timestamps and Text):</b> Relies on {@link LrcParser} for the detailed
 *       parsing of individual timestamped lyric lines.</li>
 *   <li><b>FR3.4 (Display Synchronized Lyrics):</b> Provides the {@link #displayLinesProperty()} which
 *       emits a list of currently relevant lyric lines (previous, current, next, next+1)
 *       based on playback time and the effective offset, enabling synchronized display.</li>
 *   <li><b>FR3.3 (Lyric Timing Adjustment):</b> While the initial offset is loaded here (from LRC file),
 *       the live, dynamic adjustment of this offset is typically managed by a controller (e.g., MainController)
 *       which then passes the {@code totalLiveOffsetFromController} to {@link #updateCurrentDisplayLines(long, long)}.
 *       This service stores the {@code initialLoadedOffsetMs} from the file.</li>
 * </ul>
 */
public class LyricsService {

    private Song currentSong;                   // The song whose lyrics are currently loaded.
    private SongLyrics currentLyricsHolder;     // Holds the parsed SongLyrics object (lines and original structure).
    private long initialLoadedOffsetMs = 0;     // Offset read from [offset:...] tag in the LRC file.

    // --- Observable Property for UI (FR3.4) ---

    /**
     * A read-only JavaFX property wrapper for the list of lyric lines to be displayed.
     * This list is dynamically updated and typically contains four {@link LyricLine} objects:
     * <ol>
     *   <li>The lyric line immediately preceding the current one (or {@code null}).</li>
     *   <li>The current, active lyric line (or {@code null}).</li>
     *   <li>The lyric line immediately following the current one (or {@code null}).</li>
     *   <li>The second lyric line following the current one (or {@code null}).</li>
     * </ol>
     * The list itself is unmodifiable. UI components should observe this property for updates.
     */
    private final ReadOnlyObjectWrapper<List<LyricLine>> displayLinesWrapper =
            new ReadOnlyObjectWrapper<>(this, "displayLines", Collections.emptyList());

    /**
     * Provides public, read-only access to the observable property containing the list of
     * lyric lines relevant to the current playback position (previous, current, next, and next+1).
     * This directly supports FR3.4 by allowing UI components to bind to or listen for changes
     * in the displayed lyrics.
     *
     * @return A {@link ReadOnlyObjectProperty} holding an unmodifiable {@code List<LyricLine>}.
     *         The list may contain {@code null} elements as placeholders if corresponding
     *         lyric lines are not available (e.g., at the very beginning or end of the song).
     */
    public ReadOnlyObjectProperty<List<LyricLine>> displayLinesProperty() {
        return displayLinesWrapper.getReadOnlyProperty();
    }

    /**
     * Gets a snapshot of the current list of lyric lines intended for display.
     * This is the list currently held by the {@link #displayLinesWrapper}.
     *
     * @return An unmodifiable {@code List<LyricLine>} containing the current set of displayable lyric lines.
     *         The list will be empty if no lyrics are loaded or if playback is not at a point where lyrics are defined.
     */
    public List<LyricLine> getDisplayLines() {
        return displayLinesWrapper.get();
    }

    /**
     * Gets the song whose lyrics are currently loaded or were last attempted to be loaded.
     * @return The current {@link Song}, or {@code null} if no song is active.
     */
    public Song getCurrentSong() { return currentSong; }

    /**
     * Gets the global offset value (in milliseconds) that was initially loaded from the
     * LRC file's {@code [offset:...]} tag for the currently loaded song.
     * This value serves as the base offset before any live adjustments are applied.
     * (Supports FR3.1, FR3.3)
     *
     * @return The initial loaded offset in milliseconds. Returns 0 if no song is loaded,
     *         no lyrics file was found, or no offset tag was present in the LRC file.
     */
    public long getInitialLoadedOffsetMs() {
        return initialLoadedOffsetMs;
    }

    // --- Service Methods ---

    /**
     * Loads the lyrics for the specified {@link Song}.
     * If a song is provided, this method attempts to parse its associated LRC file using {@link LrcParser}.
     * The parsed {@link LyricLine}s and the initial file offset are stored.
     * <p>
     * Behavior details:
     * <ul>
     *   <li>If {@code song} is {@code null}, any currently loaded lyrics are cleared.</li>
     *   <li>If lyrics for the exact same {@code song} instance are already loaded (and {@code currentLyricsHolder} is not null),
     *       reloading is skipped to avoid redundant processing. The live offset is managed externally.</li>
     *   <li>If the song has no lyrics file path specified, an error is logged, and no lyrics are loaded.</li>
     *   <li>If parsing fails (e.g., {@link IOException}, {@link InvalidPathException}), an error is logged,
     *       and any previously loaded lyrics are cleared.</li>
     * </ul>
     * (Supports FR3.1, FR3.2 via {@link LrcParser})
     *
     * @param song The {@link Song} object for which lyrics should be loaded. May be {@code null} to clear lyrics.
     * @return {@code true} if lyrics were successfully loaded or if {@code song} was {@code null} (clearing is successful).
     *         {@code false} if loading failed due to an error (e.g., file not found, parsing error, no lyrics path).
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
     * based on the provided current playback time and the total live offset (which includes
     * the initial file offset and any dynamic user adjustments).
     * <p>
     * This method is typically called by a controller (e.g., {@code MainController}) in response to
     * time updates from the {@link PlayerService} or when the live offset changes.
     * It uses the {@link SongLyrics#getIndexAtTime(long, long)} method to determine the current line.
     * (Supports FR3.4, FR3.3)
     *
     * @param currentPlaybackMillis The current playback time of the song, in milliseconds.
     * @param totalLiveOffsetFromController The current total effective offset, in milliseconds,
     *                                      as managed by the calling controller. This offset is the sum of
     *                                      the initial file offset and any live user adjustments.
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
     * Clears all currently loaded lyrics information, including the reference to the current song,
     * the parsed lyrics holder, the initial offset, and resets the {@link #displayLinesProperty() displayLinesProperty}
     * to an empty list. This is typically called when playback stops, the current song is unloaded
     * from the player, or the application is preparing to shut down.
     */
    public void clearLyrics() {
        // Check if there's actually anything to clear to avoid redundant operations/logging
        if (this.currentSong != null || this.currentLyricsHolder != null || !this.displayLinesWrapper.get().isEmpty()) {
            System.out.println("LyricsService: Clearing current lyrics state.");
            clearLyricsInternal();
        }
    }

    /**
     * Internal helper to reset lyrics-related state:
     * <ul>
     *      <li>Sets {@code currentSong} to null.</li>
     *      <li>Sets {@code currentLyricsHolder} to null.</li>
     *      <li>Resets {@code initialLoadedOffsetMs} to 0.</li>
     *      <li>Clears the {@code displayLinesWrapper} by setting it to an empty list if it's not already empty.</li>
     * </ul>
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
     * Returns {@code null} if the list is {@code null}, the index is out of bounds
     * (less than 0 or greater than or equal to list size).
     *
     * @param lines The list of {@link LyricLine} objects from which to retrieve an element.
     * @param index The desired index of the element.
     * @return The {@link LyricLine} at the specified index, or {@code null} if the index is invalid
     *         or the list is {@code null}.
     */
    private LyricLine getLineAtIndexSafe(List<LyricLine> lines, int index) {
        if (lines != null && index >= 0 && index < lines.size()) {
            return lines.get(index); // LyricLine objects themselves can be null if list allows
        }
        return null; // Index is invalid or list is null
    }

    /**
     * Gets the currently loaded {@link SongLyrics} object, which contains the parsed lyric lines.
     * This method is primarily intended for internal use within the service package or for testing purposes.
     *
     * @return The current {@link SongLyrics} object, or {@code null} if no lyrics are currently loaded
     *         or if an error occurred during the last loading attempt.
     */
    public SongLyrics getCurrentLyricsObject() { // Renamed from getCurrentLyrics to avoid conflict if SongLyrics was the direct type
        return currentLyricsHolder;
    }
}