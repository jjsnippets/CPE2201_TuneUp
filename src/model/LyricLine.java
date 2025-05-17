package model;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Represents a single line of lyrics, typically parsed from an .lrc file.
 * Each instance encapsulates the timestamp (when the lyric should be displayed)
 * and the corresponding textual content of the lyric line.
 * This class is fundamental for synchronizing lyrics with audio playback (SRS 2.2 "Karaoke Lyrics Support").
 * It directly supports the requirement to parse timestamps and text content from lyric files (SRS 1.2, FR3.2).
 * Instances are comparable based on their timestamps, allowing for chronological sorting.
 */
public class LyricLine implements Comparable<LyricLine> {

    // Timestamp in milliseconds from the start of the song.
    private final long timestampMillis;

    // The text content of the lyric line.
    private final String text;

    /**
     * Constructs a new {@code LyricLine} object.
     *
     * @param timestampMillis The timestamp in milliseconds from the start of the song
     *                        when this lyric line should be displayed. Must be non-negative.
     * @param text            The textual content of the lyric line. Cannot be null.
     * @throws IllegalArgumentException if {@code timestampMillis} is negative.
     * @throws NullPointerException     if {@code text} is null.
     */
    public LyricLine(long timestampMillis, String text) {
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative: " + timestampMillis);
        }
        Objects.requireNonNull(text, "Lyric text cannot be null.");

        this.timestampMillis = timestampMillis;
        this.text = text;
    }

    // --- Getters ---

    /**
     * Returns the timestamp for this lyric line in milliseconds.
     * This indicates when the lyric line should be displayed relative to the start of the song.
     *
     * @return The timestamp in milliseconds.
     */
    public long getTimestampMillis() {
        return timestampMillis;
    }

    /**
     * Returns the text content of this lyric line.
     *
     * @return The lyric text as a {@code String}.
     */
    public String getText() {
        return text;
    }

    // --- Utility Methods ---

    /**
     * Returns a formatted string representation of the timestamp (e.g., "mm:ss.SS").
     * This can be useful for debugging or displaying lyric timing information.
     *
     * @return A {@code String} representing the formatted timestamp.
     */
    public String getFormattedTimestamp() {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timestampMillis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timestampMillis) % 60;
        long hundredths = timestampMillis % 1000 / 10; // Calculate hundredths of a second
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths);
    }


    // --- Overridden methods ---

    /**
     * Compares this {@code LyricLine} to another based on their timestamps.
     * This is essential for sorting lyric lines chronologically, ensuring they are processed
     * and displayed in the correct order.
     *
     * @param other The other {@code LyricLine} to compare against.
     * @return A negative integer, zero, or a positive integer if this object's timestamp
     *         is less than, equal to, or greater than the specified object's timestamp, respectively.
     */
    @Override
    public int compareTo(LyricLine other) {
        return Long.compare(this.timestampMillis, other.timestampMillis);
    }

    /**
     * Returns a string representation of the {@code LyricLine}, including its
     * formatted timestamp and text content. Useful for logging and debugging.
     *
     * @return A {@code String} representation of this lyric line.
     */
    @Override
    public String toString() {
        return "[" + getFormattedTimestamp() + "] " + text;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * Two {@code LyricLine} objects are considered equal if they have the same
     * timestamp and the same text content.
     *
     * @param o The reference object with which to compare.
     * @return {@code true} if this object is the same as the {@code o} argument;
     *         {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LyricLine lyricLine = (LyricLine) o;
        return timestampMillis == lyricLine.timestampMillis &&
               Objects.equals(text, lyricLine.text);
    }

    /**
     * Returns a hash code value for the object.
     * This method is supported for the benefit of hash tables such as those provided by {@link java.util.HashMap}.
     * The hash code is generated based on both the timestamp and text content.
     *
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(timestampMillis, text);
    }
}
