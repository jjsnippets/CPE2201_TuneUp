package model; // Define the package for model classes

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Represents a single line of lyrics extracted from an .lrc file.
 * Each instance holds the timestamp at which the lyric should be displayed
 * and the corresponding text content.
 * This class directly supports FR3.2 (parsing timestamps and text content).
 * Instances of this class are typically stored in a list, sorted by timestamp,
 * to represent the full lyrics of a song.
 */
public class LyricLine implements Comparable<LyricLine> {

    // Timestamp in milliseconds from the start of the song when this line should be displayed.
    private final long timestampMillis;

    // The text content of the lyric line.
    private final String text;

    /**
     * Constructs a new LyricLine object.
     *
     * @param timestampMillis The timestamp in milliseconds when the line should appear. Must be non-negative.
     * @param text            The text content of the lyric line. Cannot be null.
     * @throws IllegalArgumentException if timestampMillis is negative.
     * @throws NullPointerException     if text is null.
     */
    public LyricLine(long timestampMillis, String text) {
        // Validate inputs
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative.");
        }
        Objects.requireNonNull(text, "Lyric text cannot be null");

        this.timestampMillis = timestampMillis;
        this.text = text;
    }

    // --- Getters ---

    /**
     * Gets the timestamp for this lyric line in milliseconds.
     *
     * @return The timestamp in milliseconds.
     */
    public long getTimestampMillis() {
        return timestampMillis;
    }

    /**
     * Gets the text content of this lyric line.
     *
     * @return The lyric text.
     */
    public String getText() {
        return text;
    }

    // --- Utility Methods ---

    /**
     * Returns a formatted string representation of the timestamp (e.g., mm:ss.SS).
     * Useful for debugging or display if needed.
     *
     * @return Formatted timestamp string.
     */
    public String getFormattedTimestamp() {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timestampMillis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timestampMillis) % 60;
        long millis = timestampMillis % 1000 / 10; // Get hundredths of a second
        return String.format("%02d:%02d.%02d", minutes, seconds, millis);
    }


    // --- Overridden methods ---

    /**
     * Compares this LyricLine to another based on their timestamps.
     * Allows sorting lyric lines chronologically.
     *
     * @param other The other LyricLine to compare against.
     * @return a negative integer, zero, or a positive integer as this object
     *         is less than, equal to, or greater than the specified object.
     */
    @Override
    public int compareTo(LyricLine other) {
        return Long.compare(this.timestampMillis, other.timestampMillis);
    }

    /**
     * Returns a string representation of the LyricLine, including timestamp and text.
     * Useful for debugging.
     *
     * @return A string representation of the lyric line.
     */
    @Override
    public String toString() {
        return "[" + getFormattedTimestamp() + "] " + text;
    }

    /**
     * Compares this LyricLine object to another object for equality.
     * Two LyricLines are considered equal if they have the same timestamp and text content.
     *
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
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
     * Generates a hash code for the LyricLine object.
     * Based on both timestamp and text content for consistency with equals().
     *
     * @return The hash code value for this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(timestampMillis, text);
    }
}
