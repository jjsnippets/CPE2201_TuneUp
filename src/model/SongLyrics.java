package model; // Define the package for model classes

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the complete set of timed lyrics for a single song, including any offset.
 * Encapsulates a sorted list of individual LyricLine objects and the global time offset.
 * Provides methods to query lyrics based on playback time, considering the offset.
 * Fundamental for lyric synchronization features (FR3.4, FR3.5).
 */
public class SongLyrics {

    // Unmodifiable list of lyric lines, assumed to be sorted by timestamp.
    private final List<LyricLine> lines;

    // The global offset in milliseconds, parsed from the LRC [offset:] tag.
    // This value is typically added to each line's timestamp for synchronization.
    private final long offsetMillis;

    // Optional: Store the index of the last found line for slight optimization
    // in sequential lookups. Must be managed carefully if used.
    // private transient int lastFoundIndex = -1; // Example, needs careful handling

    /**
     * Constructs a SongLyrics object.
     *
     * @param lines        A list of LyricLine objects. The list should ideally be pre-sorted
     *                     by timestamp. An immutable copy is stored. Can be null or empty.
     * @param offsetMillis The global time offset in milliseconds (from [offset:] tag).
     */
    public SongLyrics(List<LyricLine> lines, long offsetMillis) {
        // Store an immutable copy of the list to prevent external modifications.
        // Ensure list is not null.
        this.lines = (lines != null) ? List.copyOf(lines) : Collections.emptyList();
        this.offsetMillis = offsetMillis;
        // Note: We assume the input list is already sorted. If not guaranteed, sort here:
        // this.lines = new ArrayList<>(lines != null ? lines : Collections.emptyList());
        // Collections.sort(this.lines);
        // this.lines = Collections.unmodifiableList(this.lines);
    }

    // --- Getters ---

    /**
     * Gets an unmodifiable view of the list of individual lyric lines.
     * The list is guaranteed to be sorted by the original timestamp of each LyricLine.
     *
     * @return An unmodifiable list of LyricLine objects.
     */
    public List<LyricLine> getLines() {
        return lines;
    }

    /**
     * Gets the global time offset in milliseconds.
     * This value should be added to each LyricLine's timestamp to get the
     * effective display time relative to the audio playback.
     *
     * @return The offset in milliseconds.
     */
    public long getOffsetMillis() {
        return offsetMillis;
    }

    // --- Core Functionality ---

    /**
     * Finds the lyric line that should be currently displayed based on the playback time.
     * This method accounts for the global offset. It returns the *last* line whose
     * effective timestamp (original timestamp + offset) is less than or equal to
     * the current playback time.
     *
     * Corresponds to FR3.4 (Synchronize lyrics) and supports FR3.5 (Jump to timestamp).
     *
     * @param currentPlaybackMillis The current playback time of the song in milliseconds.
     * @return The active LyricLine at the given time, or null if no line is active
     *         (e.g., before the first lyric starts or if lyrics are empty).
     */
    public LyricLine getLineAtTime(long currentPlaybackMillis) {
        if (lines.isEmpty()) {
            return null; // No lyrics to display
        }

        LyricLine activeLine = null;

        // Iterate through the sorted lines
        for (LyricLine line : lines) {
            // Calculate the effective time this line should appear
            long effectiveTimestamp = line.getTimestampMillis() + this.offsetMillis;

            // If the line's effective time is on or before the current playback time,
            // it's a candidate for the currently active line.
            if (effectiveTimestamp <= currentPlaybackMillis) {
                activeLine = line;
            } else {
                // Since the list is sorted, once we pass the current playback time,
                // we can stop searching. The previous 'activeLine' is the correct one.
                break;
            }
        }
        // Return the last line that met the criteria
        return activeLine;
    }

    /**
     * Finds the *index* of the lyric line active at a specific time.
     * Useful for highlighting or scrolling in a UI list view.
     *
     * @param currentPlaybackMillis The current playback time in milliseconds.
     * @return The index of the active LyricLine in the list, or -1 if none is active.
     */
    public int getIndexAtTime(long currentPlaybackMillis) {
         if (lines.isEmpty()) {
            return -1;
        }
        int activeIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
             LyricLine line = lines.get(i);
             long effectiveTimestamp = line.getTimestampMillis() + this.offsetMillis;
             if (effectiveTimestamp <= currentPlaybackMillis) {
                 activeIndex = i;
             } else {
                 break; // Found the boundary
             }
        }
        return activeIndex;
    }


    /**
     * Checks if this SongLyrics object contains any lyric lines.
     * @return true if the list of lines is empty, false otherwise.
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Returns the number of lyric lines.
     * @return The total count of LyricLine objects.
     */
    public int getSize() {
        return lines.size();
    }

    // --- Overridden methods ---

    @Override
    public String toString() {
        return "SongLyrics{" +
               "lines=" + lines.size() + " lines" +
               ", offsetMillis=" + offsetMillis +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SongLyrics that = (SongLyrics) o;
        return offsetMillis == that.offsetMillis &&
               Objects.equals(lines, that.lines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lines, offsetMillis);
    }
}
