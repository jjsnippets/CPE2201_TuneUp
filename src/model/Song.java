package model;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Represents a song entity within the TuneUp Karaoke application.
 * This immutable class encapsulates all metadata associated with a song,
 * including its title, artist, genre, duration, playback offset, and file paths
 * for audio and lyrics.
 * <p>
 * This class is central to managing song information and directly supports several
 * functional requirements (FR) from the Software Requirements Specification (SRS):
 * <ul>
 *   <li>FR1.1: Storing and accessing the audio file path.</li>
 *   <li>FR2.1: Storing and accessing core song metadata (title, artist, genre, duration).</li>
 *   <li>FR2.2: Providing song information for display (title, artist, duration).</li>
 *   <li>FR3.1: Storing and accessing the lyrics file path and playback offset.</li>
 * </ul>
 * Instances of this class are typically created from data retrieved from the
 * application's database (SQLite, as per SRS Section 3.1.3, 3.3.3).
 */
public class Song {

    private final int id;               // Unique identifier (Primary Key in database)
    private final String title;         // Song title (SRS FR2.1, FR2.2)
    private final String artist;        // Song artist (SRS FR2.1, FR2.2)
    private final String genre;         // Song genre, can be null (SRS FR2.1, FR2.2)
    private final Integer duration;     // Duration in milliseconds, nullable (SRS FR2.1, FR2.2)
    private final Long offset;          // Lyrics synchronization offset in milliseconds, nullable (SRS FR3.1)
    private final String audioFilePath; // Path to the audio file (SRS FR1.1)
    private final String lyricsFilePath;// Path to the lyrics file, can be null (SRS FR3.1)

    /**
     * Constructs an immutable {@code Song} object with specified details.
     *
     * @param id                        The unique identifier for the song (e.g., from the database).
     * @param title                     The title of the song. Must not be null or blank.
     * @param artist                    The artist of the song. Must not be null or blank.
     * @param genre                     The genre of the song. May be {@code null} if not specified.
     * @param duration                  The duration of the song in milliseconds. Must not be null and must be positive.
     * @param offset                    The global lyrics synchronization offset in milliseconds. May be {@code null}.
     * @param audioFilePath             The file path to the audio file (e.g., .mp3). Must not be null or blank.
     * @param lyricsFilePath            The file path to the lyrics file (e.g., .lrc). May be {@code null}.
     * @throws NullPointerException     if {@code title}, {@code artist}, {@code audioFilePath}, or {@code duration} are {@code null}.
     * @throws IllegalArgumentException if {@code title}, {@code artist}, or {@code audioFilePath} are blank, or if {@code duration} is not positive.
     */
    public Song(int id, String title, String artist, String genre, Integer duration, Long offset, String audioFilePath, String lyricsFilePath) {
        Objects.requireNonNull(title, "Song title cannot be null.");
        Objects.requireNonNull(artist, "Song artist cannot be null.");
        Objects.requireNonNull(audioFilePath, "Song audio file path cannot be null.");
        Objects.requireNonNull(duration, "Song duration cannot be null.");

        if (title.isBlank()) {
            throw new IllegalArgumentException("Song title cannot be blank.");
        }
        if (artist.isBlank()) {
            throw new IllegalArgumentException("Song artist cannot be blank.");
        }
        if (audioFilePath.isBlank()) {
            throw new IllegalArgumentException("Song audio file path cannot be blank.");
        }
        if (duration <= 0) {
            throw new IllegalArgumentException("Song duration must be positive.");
        }

        this.id = id;
        this.title = title.trim();
        this.artist = artist.trim();
        this.genre = (genre != null) ? genre.trim() : null;
        this.duration = duration;
        this.offset = offset;
        this.audioFilePath = audioFilePath.trim();
        this.lyricsFilePath = (lyricsFilePath != null) ? lyricsFilePath.trim() : null;
    }

    // --- Getters ---

    /**
     * Returns the unique identifier of the song.
     * @return The song ID.
     */
    public int getId() { return id; }

    /**
     * Returns the title of the song (supports FR2.1, FR2.2).
     * @return The song title.
     */
    public String getTitle() { return title; }

    /**
     * Returns the artist of the song (supports FR2.1, FR2.2).
     * @return The song artist.
     */
    public String getArtist() { return artist; }

    /**
     * Returns the genre of the song (supports FR2.1, FR2.2).
     * @return The song genre, or {@code null} if not specified.
     */
    public String getGenre() { return genre; }

    /**
     * Returns the duration of the song in milliseconds (supports FR2.1, FR2.2).
     * @return The song duration as an {@link Integer}, or {@code null} if not specified.
     */
    public Integer getDuration() { return duration; }

    /**
     * Returns the global lyrics synchronization offset in milliseconds (supports FR3.1).
     * @return The global offset as a {@link Long}, or {@code null} if not specified.
     */
    public Long getOffset() { return offset; }

    /**
     * Returns the file path to the audio file (supports FR1.1).
     * @return The audio file path.
     */
    public String getAudioFilePath() { return audioFilePath; }

    /**
     * Returns the file path to the lyrics file (supports FR3.1).
     * @return The lyrics file path, or {@code null} if not available.
     */
    public String getLyricsFilePath() { return lyricsFilePath; }

    // --- Utility method for formatted duration ---

    /**
     * Returns the duration of the song formatted as a string "mm:ss" (minutes:seconds).
     * If the duration is not set (null), it returns "N/A".
     * This is useful for displaying the duration in a user-friendly format (supports FR2.2).
     *
     * @return A string representing the formatted duration, or "N/A".
     */
    public String getFormattedDuration() {
        if (duration == null) {
            return "N/A";
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // --- Overridden Object methods ---

    /**
     * Returns a string representation of the song, typically "Title - Artist".
     * This format is suitable for display in lists or simple textual representations.
     *
     * @return A string in the format "[Song Title] - [Song Artist]".
     */
    @Override
    public String toString() {
        return title + " - " + artist;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * Two {@code Song} objects are considered equal if they have the same {@code id}.
     * This is consistent with the typical behavior for database-backed entities where
     * the ID is the primary key.
     *
     * @param o The reference object with which to compare.
     * @return {@code true} if this song is the same as the {@code o} argument (i.e., same ID);
     *         {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return id == song.id;
    }

    /**
     * Returns a hash code value for the song.
     * The hash code is based on the song's {@code id}, ensuring that equal songs
     * (as per the {@link #equals(Object)} method) have the same hash code.
     *
     * @return A hash code value for this song.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}