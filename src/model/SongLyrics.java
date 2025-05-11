package model; // Define the package for model classes

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the timed lyric lines for a song.
 * This class purely holds the list of LyricLine objects.
 * Offset calculations are handled by passing the total effective offset to its methods.
 */
public class SongLyrics {
    private final List<LyricLine> lines;

    // Optional: Store the index of the last found line for slight optimization
    // in sequential lookups. Must be managed carefully if used.
    // private transient int lastFoundIndex = -1; // Example, needs careful handling

    /**
     * Constructs a SongLyrics object.
     * @param lines A list of LyricLine objects, expected to be sorted. An immutable copy is stored.
     */
    public SongLyrics(List<LyricLine> lines) {
        // Store an immutable copy of the list to prevent external modifications.
        // Ensure list is not null.
        this.lines = (lines != null) ? List.copyOf(lines) : Collections.emptyList();
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

    // --- Core Functionality ---

    /**
     * Finds the lyric line active at a specific time, using a provided total effective offset.
     *
     * @param currentPlaybackMillis The current playback time.
     * @param totalEffectiveOffset The total offset (from [offset:...] tag, potentially live adjusted) to apply.
     * @return The active LyricLine, or null.
     */
    public LyricLine getLineAtTime(long currentPlaybackMillis, long totalEffectiveOffset) {
        if (lines.isEmpty()) {
            return null; // No lyrics to display
        }

        LyricLine activeLine = null;

        // Iterate through the sorted lines
        for (LyricLine line : lines) {
            // Calculate the effective time this line should appear
            long effectiveTimestamp = line.getTimestampMillis() + totalEffectiveOffset;

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
     * Finds the index of the lyric line active at a specific time, using a provided total effective offset.
     *
     * @param currentPlaybackMillis The current playback time.
     * @param totalEffectiveOffset The total offset to apply.
     * @return The index of the active LyricLine, or -1.
     */
    public int getIndexAtTime(long currentPlaybackMillis, long totalEffectiveOffset) {
         if (lines.isEmpty()) {
            return -1;
        }
        int activeIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
             LyricLine line = lines.get(i);
             long effectiveTimestamp = line.getTimestampMillis() + totalEffectiveOffset;
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
        return "SongLyrics{" + lines.size() + " lines}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SongLyrics that = (SongLyrics) o;
        return Objects.equals(lines, that.lines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lines);
    }
}
