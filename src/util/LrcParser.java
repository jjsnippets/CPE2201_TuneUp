package util; // Define the package for utility classes

import model.LyricLine; // Import the model class for lyrics

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing LRC (.lrc) files.
 * Provides methods to extract timed lyric lines and metadata tags.
 * Supports FR3.1, FR3.2 and implicitly FR2.1, FR2.2 by parsing metadata tags.
 */
public class LrcParser {

    // Regex for timed lyric lines [mm:ss.xx] or [mm:ss.xxx]
    private static final Pattern TIME_TAG_PATTERN = Pattern.compile(
            "\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})]");

    // Regex specifically for required metadata tags: ti, ar, genre
    // Captures the tag name (group 1) and the value (group 2)
    private static final Pattern METADATA_TAG_PATTERN = Pattern.compile(
            "^\\[(ti|ar|genre):(.*)\\]\\s*$");

    // Regex to skip other common metadata lines if needed (though not strictly necessary
    // if we only process lines matched by METADATA_TAG_PATTERN for metadata)
    private static final Pattern OTHER_METADATA_LINE_PATTERN = Pattern.compile(
            "^\\[(al|au|length|by|offset|re|ve):.*\\]\\s*$");


    // Private constructor to prevent instantiation.
    private LrcParser() { }

    /**
     * Parses the given LRC file to extract specific metadata tags.
     * Looks for [ti:], [ar:], and [genre:] tags.
     *
     * @param filePath The path to the .lrc file.
     * @return A Map where keys are "title", "artist", "genre" and values are the
     *         corresponding strings found in the tags. Returns null values for tags not found.
     *         Returns an empty map if the file cannot be read or no relevant tags are found.
     * @throws IOException If an I/O error occurs reading the file.
     * @throws InvalidPathException If the filePath string cannot be converted to a Path.
     */
    public static Map<String, String> parseMetadataTags(String filePath) throws IOException, InvalidPathException {
        Map<String, String> metadata = new HashMap<>();
        // Pre-populate with nulls to indicate they are expected but not yet found
        metadata.put("title", null);
        metadata.put("artist", null);
        metadata.put("genre", null);

        Path path = Paths.get(filePath);

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = METADATA_TAG_PATTERN.matcher(line.trim());
                if (matcher.matches()) {
                    String key = matcher.group(1).toLowerCase(); // "ti", "ar", or "genre"
                    String value = matcher.group(2).trim();

                    // Map LRC tag names to desired map keys and store if not already found
                    // (Usually, the first occurrence is the intended one)
                    switch (key) {
                        case "ti":
                            if (metadata.get("title") == null) metadata.put("title", value);
                            break;
                        case "ar":
                            if (metadata.get("artist") == null) metadata.put("artist", value);
                            break;
                        case "genre":
                            if (metadata.get("genre") == null) metadata.put("genre", value);
                            break;
                    }

                    // Optional: If all required tags are found, we could potentially break early
                    // if (metadata.get("title") != null && metadata.get("artist") != null && metadata.get("genre") != null) {
                    //     break;
                    // }
                }
            }
        }
        return metadata;
    }


    /**
     * Parses the given LRC file and extracts timed lyric lines.
     * Ignores metadata tags and empty lines.
     * (This method remains unchanged from previous implementation)
     *
     * @param filePath The path to the .lrc file.
     * @return A List of LyricLine objects, sorted chronologically. Returns an empty list if
     *         the file cannot be read, is empty, or contains no valid lyric lines.
     * @throws IOException If an I/O error occurs reading the file.
     * @throws InvalidPathException If the filePath string cannot be converted to a Path.
     */
    public static List<LyricLine> parseLrcFile(String filePath) throws IOException, InvalidPathException {
        List<LyricLine> lyricLines = new ArrayList<>();
        Path path = Paths.get(filePath);

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines or lines containing only standard metadata tags
                // Adjusted to skip *any* line starting with '[' and ending with ']'
                // that isn't a time tag, to be more robust against unexpected metadata.
                if (line.isEmpty() || (line.startsWith("[") && line.endsWith("]") && !TIME_TAG_PATTERN.matcher(line).find())) {
                     // A simpler check: If it looks like metadata '[key:value]' and doesn't contain a time tag, skip.
                     // We rely on the metadata parser for metadata, and here we only want timed lyrics.
                     // Note: This logic assumes metadata tags don't appear on the same line as timed lyrics.
                     continue;
                }


                Matcher matcher = TIME_TAG_PATTERN.matcher(line);
                List<Long> timestamps = new ArrayList<>();
                int lastTagEnd = 0;

                while (matcher.find()) {
                    try {
                        long minutes = Long.parseLong(matcher.group(1));
                        long seconds = Long.parseLong(matcher.group(2));
                        String millisStr = matcher.group(3);
                        long millis = Long.parseLong(millisStr);

                        if (millisStr.length() == 2) {
                            millis *= 10;
                        }

                        long totalMillis = (minutes * 60 + seconds) * 1000 + millis;
                        timestamps.add(totalMillis);
                        lastTagEnd = matcher.end();
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Could not parse timestamp in line: " + line);
                    }
                }

                if (!timestamps.isEmpty()) {
                    String text = line.substring(lastTagEnd).trim();
                    // Only add if the text is not empty
                    if (!text.isEmpty()) {
                       for (Long timestamp : timestamps) {
                           lyricLines.add(new LyricLine(timestamp, text));
                       }
                    }
                } else if (!line.isEmpty()) {
                     // Line had content but no valid time tags and wasn't identified as metadata.
                     System.err.println("Warning: Skipping non-empty line with no valid time tags: " + line);
                }
            }
        }

        Collections.sort(lyricLines);
        return lyricLines;
    }
}
