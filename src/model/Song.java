package model; // Define the package for model classes

import java.util.Objects;
import java.util.concurrent.TimeUnit; // Keep for potential detailed toString formatting

/**
 * Represents a song entity within the TuneUp application.
 * This class holds metadata such as title, artist, genre, duration, offset,
 * as well as file paths for the corresponding audio and lyrics files.
 * It corresponds to functional requirements FR1.1, FR2.1, FR2.2, FR3.1
 * and is designed to be stored and retrieved via the SQLite database (Section 3.1.3, 3.3.3).
 * This class is immutable.
 */
public class Song {

    // Unique identifier for the song, typically used as the primary key in the database.
    private final int id;

    // The title of the song (FR2.1, FR2.2).
    private final String title;

    // The artist of the song (FR2.1, FR2.2).
    private final String artist;

    // The genre of the song (FR2.1, FR2.2). Can be null.
    private final String genre;

    // Duration in milliseconds. Stored as Integer to allow null.
    private final Integer duration; // CORRECTED TYPE: Integer

    // Global offset in milliseconds. Stored as Long to allow null.
    private final Long offset;

    // The file path to the audio file (e.g., .mp3) for this song (FR1.1).
    private final String audioFilePath;

    // The file path to the lyrics file (e.g., .lrc) for this song (FR3.1). Can be null.
    private final String lyricsFilePath;

    /**
     * Constructs a new immutable Song object.
     *
     * @param id             The unique identifier for the song.
     * @param title          The title of the song. Cannot be null or blank.
     * @param artist         The artist of the song. Cannot be null or blank.
     * @param genre          The genre of the song. Can be null.
     * @param duration       The duration of the song in milliseconds. Can be null.
     * @param offset         The global lyrics offset in milliseconds. Can be null.
     * @param audioFilePath  The file path to the audio file. Cannot be null or blank.
     * @param lyricsFilePath The file path to the lyrics file. Can be null.
     * @throws NullPointerException if title, artist, or audioFilePath are null.
     * @throws IllegalArgumentException if title, artist, or audioFilePath are blank.
     */
    public Song(int id, String title, String artist, String genre, Integer duration, Long offset, String audioFilePath, String lyricsFilePath) {
        // Basic validation for required fields
        Objects.requireNonNull(title, "Title cannot be null");
        Objects.requireNonNull(artist, "Artist cannot be null");
        Objects.requireNonNull(audioFilePath, "Audio file path cannot be null");

        // Additional validation for blank strings (optional but recommended)
        if (title.isBlank()) throw new IllegalArgumentException("Title cannot be blank");
        if (artist.isBlank()) throw new IllegalArgumentException("Artist cannot be blank");
        if (audioFilePath.isBlank()) throw new IllegalArgumentException("Audio file path cannot be blank");

        // Validate duration if not null (optional)
        // if (duration != null && duration < 0) throw new IllegalArgumentException("Duration cannot be negative");

        this.id = id;
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.duration = duration; // Assign Integer
        this.offset = offset;
        this.audioFilePath = audioFilePath;
        this.lyricsFilePath = lyricsFilePath;
    }

    // --- Getters ---

    /** Gets the unique identifier of the song. @return The song ID. */
    public int getId() { return id; }

    /** Gets the title of the song. @return The song title. */
    public String getTitle() { return title; }

    /** Gets the artist of the song. @return The song artist. */
    public String getArtist() { return artist; }

    /** Gets the genre of the song. @return The song genre, or null if not specified. */
    public String getGenre() { return genre; }

    /** Gets the duration of the song in milliseconds. @return The song duration as Integer, or null. */
    public Integer getDuration() { return duration; } // CORRECTED RETURN TYPE: Integer

    /** Gets the global lyrics offset in milliseconds. @return The global offset as Long, or null. */
    public Long getOffset() { return offset; }

    /** Gets the file path to the audio file. @return The audio file path. */
    public String getAudioFilePath() { return audioFilePath; }

    /** Gets the file path to the lyrics file. @return The lyrics file path, or null. */
    public String getLyricsFilePath() { return lyricsFilePath; }

    // --- Utility method for formatted duration (optional) ---

    /**
     * Gets the duration formatted as mm:ss.
     * @return The formatted duration string, or "N/A" if duration is null.
     */
    public String getFormattedDuration() {
        if (duration == null) {
            return "N/A";
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }


    // --- Overridden methods ---

    /**
     * Returns a simple string representation (Title - Artist).
     * Suitable for default display in UI lists.
     * @return A simple string representation of the song.
     */
    @Override
    public String toString() {
        return title + " - " + artist;
        /*
        // Alternative detailed version for debugging:
        return String.format("Song{id=%d, title='%s', artist='%s', genre='%s', duration=%s (%s ms), offset=%s ms, audio='%s', lyrics='%s'}",
                id,
                title,
                artist,
                genre != null ? genre : "N/A",
                getFormattedDuration(), // Use helper method
                duration != null ? duration : "N/A",
                offset != null ? offset : "N/A",
                audioFilePath,
                lyricsFilePath != null ? lyricsFilePath : "N/A"
        );
        */
    }

    /**
     * Compares this Song to another object for equality based on their IDs.
     * @param o The object to compare with.
     * @return true if the objects have the same ID, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        // Equality is primarily based on the unique ID for database entities
        return id == song.id;
    }

    /**
     * Generates a hash code based primarily on the unique ID.
     * @return The hash code value for this object.
     */
    @Override
    public int hashCode() {
        // Hash code based primarily on the unique ID for consistency with equals()
        return Objects.hash(id);
    }
}