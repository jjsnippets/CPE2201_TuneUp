package model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the complete set of timed lyric lines for a song.
 * This class acts as an immutable container for a list of {@link LyricLine} objects.
 * It provides functionality to retrieve specific lyric lines based on playback time,
 * considering an effective offset.
 * <p>
 * This class is crucial for lyric synchronization and display, supporting SRS requirements:
 * <ul>
 *   <li>FR3.1: Storing and managing lyric lines.</li>
 *   <li>FR3.2: Displaying lyrics synchronized with audio playback.</li>
 *   <li>FR3.3: Handling lyric timing adjustments (via the {@code totalEffectiveOffset} parameter).</li>
 * </ul>
 * The list of lyric lines is expected to be sorted by timestamp upon construction.
 */
public class SongLyrics {
    private final List<LyricLine> lines;
    
    // For typical song lengths, linear scan is generally performant enough.

    /**
     * Constructs a {@code SongLyrics} object from a list of {@link LyricLine}s.
     * The provided list is defensively copied to ensure immutability of this object.
     * It is assumed that the incoming list of lines is already sorted by timestamp.
     *
     * @param lines A {@link List} of {@link LyricLine} objects, ideally pre-sorted by their timestamps.
     *              If {@code null}, an empty list of lyrics will be stored.
     */
    public SongLyrics(List<LyricLine> lines) {
        // Store an immutable copy of the list to prevent external modifications.
        // If the input list is null, an empty list is used, ensuring 'this.lines' is never null.
        this.lines = (lines != null) ? List.copyOf(lines) : Collections.emptyList();
    }

    // --- Getters ---

    /**
     * Returns an unmodifiable view of the list of individual lyric lines.
     * The list is guaranteed to be sorted if the input list provided to the constructor was sorted.
     * (Supports FR3.1)
     *
     * @return An unmodifiable {@link List} of {@link LyricLine} objects. This list will be empty
     *         if no lines were provided or if the input was {@code null}.
     */
    public List<LyricLine> getLines() {
        return lines;
    }

    // --- Core Functionality ---

    /**
     * Finds the lyric line that should be considered active at a specific playback time,
     * applying a given total effective offset. The offset adjusts the timing of all lyric lines.
     * (Supports FR3.2, FR3.3)
     *
     * @param currentPlaybackMillis The current playback time of the song, in milliseconds.
     * @param totalEffectiveOffset  The total offset (e.g., from an LRC file's [offset:...] tag,
     *                              plus any live user adjustments) to apply to lyric timestamps, in milliseconds.
     *                              A positive offset makes lyrics appear later; a negative offset earlier.
     * @return The {@link LyricLine} that is active at the specified {@code currentPlaybackMillis}
     *         after applying the {@code totalEffectiveOffset}. Returns {@code null} if no line is active
     *         at that time (e.g., before the first lyric or if lyrics are empty).
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
     * Finds the index of the lyric line that should be considered active at a specific playback time,
     * applying a given total effective offset.
     * (Supports FR3.2, FR3.3)
     *
     * @param currentPlaybackMillis The current playback time of the song, in milliseconds.
     * @param totalEffectiveOffset  The total offset to apply to lyric timestamps, in milliseconds.
     * @return The index of the active {@link LyricLine} in the internal list. Returns -1 if no line
     *         is active at that time or if lyrics are empty.
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
     * Checks if this {@code SongLyrics} object contains any lyric lines.
     * (Supports FR3.1)
     *
     * @return {@code true} if there are no lyric lines stored; {@code false} otherwise.
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Returns the total number of lyric lines stored in this object.
     * (Supports FR3.1)
     *
     * @return The count of {@link LyricLine} objects.
     */
    public int getSize() {
        return lines.size();
    }

    // --- Overridden Object methods ---

    /**
     * Returns a string representation of the {@code SongLyrics} object, typically indicating
     * the number of lines it contains.
     *
     * @return A string summary of this {@code SongLyrics} instance.
     */
    @Override
    public String toString() {
        return "SongLyrics{" + lines.size() + " lines}";
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * Two {@code SongLyrics} objects are considered equal if their respective lists of
     * {@link LyricLine} objects are equal (i.e., contain the same lines in the same order).
     *
     * @param o The reference object with which to compare.
     * @return {@code true} if this object is the same as the {@code o} argument;
     *         {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SongLyrics that = (SongLyrics) o;
        return Objects.equals(lines, that.lines);
    }

    /**
     * Returns a hash code value for the object.
     * This method is supported for the benefit of hash tables such as those provided by
     * {@link java.util.HashMap}.
     *
     * @return A hash code value for this object, based on its list of lyric lines.
     */
    @Override
    public int hashCode() {
        return Objects.hash(lines);
    }
}
