package model; // Define the package for model classes

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Represents a song entity within the TuneUp application.
 * This class holds metadata such as title, artist, and genre,
 * as well as file paths for the corresponding audio and lyrics files.
 * It corresponds to functional requirements FR1.1, FR2.1, FR2.2, FR3.1
 * and is designed to be stored and retrieved via the SQLite database (Section 3.1.3, 3.3.3).
 */
public class Song {

    // Unique identifier for the song, typically used as the primary key in the database.
    private final int id;

    // The title of the song (FR2.1, FR2.2).
    private final String title;

    // The artist of the song (FR2.1, FR2.2).
    private final String artist;

    // The genre of the song (FR2.1, FR2.2).
    private final String genre;

    // Duration in milliseconds
    private final long duration;  

    // The file path to the audio file (e.g., .mp3) for this song (FR1.1).
    private final String audioFilePath;

    // The file path to the lyrics file (e.g., .lrc) for this song (FR3.1).
    private final String lyricsFilePath;

    /**
     * Constructs a new Song object.
     *
     * @param id             The unique identifier for the song.
     * @param title          The title of the song. Cannot be null.
     * @param artist         The artist of the song. Cannot be null.
     * @param genre          The genre of the song. Can be null if not specified.
     * @param audioFilePath  The file path to the audio file. Cannot be null.
     * @param lyricsFilePath The file path to the lyrics file. Can be null if no lyrics exist.
     * @throws NullPointerException if title, artist, or audioFilePath are null.
     */
    public Song(int id, String title, String artist, String genre, long duration, String audioFilePath, String lyricsFilePath) {

        // Basic validation for required fields
        Objects.requireNonNull(title, "Title cannot be null");
        Objects.requireNonNull(artist, "Artist cannot be null");
        Objects.requireNonNull(audioFilePath, "Audio file path cannot be null");

        this.id = id;
        this.title = title;
        this.artist = artist;
        this.genre = genre; // Genre can be optional/null
        this.duration = duration;
        this.audioFilePath = audioFilePath;
        this.lyricsFilePath = lyricsFilePath; // Lyrics file path can be optional/null
    }

    // --- Getters ---

    /**
     * Gets the unique identifier of the song.
     * @return The song ID.
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the title of the song.
     * @return The song title.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Gets the artist of the song.
     * @return The song artist.
     */
    public String getArtist() {
        return artist;
    }

    /**
     * Gets the genre of the song.
     * @return The song genre, or null if not specified.
     */
    public String getGenre() {
        return genre;
    }

    /**
     * Gets the duration of the song in milliseconds.
     * @return The song duration.
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Gets the file path to the audio file.
     * @return The audio file path.
     */
    public String getAudioFilePath() {
        return audioFilePath;
    }

    /**
     * Gets the file path to the lyrics file.
     * @return The lyrics file path, or null if no lyrics file exists.
     */
    public String getLyricsFilePath() {
        return lyricsFilePath;
    }

    // --- Overridden methods ---

    /**
     * Returns a string representation of the Song object, typically including title and artist.
     * Useful for display in lists or debugging.
     * @return A string representation of the song.
     */
    @Override
    public String toString() {
        return title + " - " + artist; // Simple representation for UI lists
        // More detailed version for debugging:
        // return "Song{" +
        //        "id=" + id +
        //        ", title='" + title + '\'' +
        //        ", artist='" + artist + '\'' +
        //        ", genre='" + genre + '\'' +
        //        ", duration=" + String.format("(%d:%02d)", TimeUnit.MILLISECONDS.toMinutes(duration), TimeUnit.MILLISECONDS.toSeconds(duration) % 60) + '\'' +
        //        ", audioFilePath='" + audioFilePath + '\'' +
        //        ", lyricsFilePath='" + lyricsFilePath + '\'' +
        //        '}';
    }

    /**
     * Compares this Song object to another object for equality.
     * Two songs are considered equal if they have the same ID.
     *
     * @param o The object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        // Equality is primarily based on the unique ID
        return id == song.id;
        // Alternatively, if ID is not guaranteed unique across instances (e.g., before DB persistence),
        // you might compare other fields like file paths or title/artist combo:
        // return Objects.equals(title, song.title) &&
        //        Objects.equals(artist, song.artist) &&
        //        Objects.equals(audioFilePath, song.audioFilePath);
    }

    /**
     * Generates a hash code for the Song object.
     * Based primarily on the unique ID for consistency with equals().
     *
     * @return The hash code value for this object.
     */
    @Override
    public int hashCode() {
        // Hash code based primarily on the unique ID
        return Objects.hash(id);
        // Alternative if ID is not the primary equality factor:
        // return Objects.hash(title, artist, audioFilePath);
    }
}
