package util;

import model.LyricLine;
import model.SongLyrics;

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
import java.util.concurrent.atomic.AtomicLong; // Used to pass offset by reference effectively
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing LRC (.lrc) files.
 * Extracts timed lyric lines, offset, and specific metadata tags.
 * Refactored for improved clarity and structure.
 */
public class LrcParser {

    // Regex for timed lyric lines [mm:ss.xx] or [mm:ss.xxx]
    private static final Pattern TIME_TAG_PATTERN = Pattern.compile(
            "\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})]"); // Group 1:min, 2:sec, 3:ms/cs

    // Regex for metadata tags used in parseMetadataTags
    private static final Pattern METADATA_TAG_PATTERN_FOR_TAGS = Pattern.compile(
            "^\\[(ti|ar|al|length|genre|offset):(.*)\\]\\s*$"); // Group 1:key, 2:value

    // Regex for just the offset tag, used within parseLyrics
    private static final Pattern OFFSET_TAG_PATTERN = Pattern.compile(
            "^\\[offset:(.*)\\]\\s*$", Pattern.CASE_INSENSITIVE); // Group 1:value

    // Private constructor to prevent instantiation.
    private LrcParser() { }

    // --- Public Method: Parse Lyrics and Offset ---

    /**
     * Parses the given LRC file to extract timed lyric lines and the offset.
     *
     * @param filePath The path to the .lrc file.
     * @return A SongLyrics object containing the parsed lines and offset. Returns
     *         an empty SongLyrics object if the file cannot be read or contains no valid lines.
     * @throws IOException If an I/O error occurs reading the file.
     * @throws InvalidPathException If the filePath string cannot be converted to a Path.
     */
    public static SongLyrics parseLyrics(String filePath) throws IOException, InvalidPathException {
        List<LyricLine> lyricLines = new ArrayList<>();
        AtomicLong offsetMillis = new AtomicLong(0); // Use AtomicLong to allow modification in helper

        Path path = Paths.get(filePath);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Delegate line processing to helper method
                processLineForLyricsAndOffset(line.trim(), lyricLines, offsetMillis);
            }
        }

        Collections.sort(lyricLines); // Ensure lines are sorted by timestamp
        return new SongLyrics(lyricLines, offsetMillis.get());
    }

    // --- Public Method: Parse Metadata Tags ---

    /**
     * Parses the given LRC file to extract specific metadata tags (Title, Artist, Genre, Duration, Offset).
     * Correctly parses duration from mm:ss[.xxx] format.
     * Note: This method does NOT parse the lyric lines themselves.
     *
     * @param filePath The path to the .lrc file.
     * @return A Map where keys are "title", "artist", "genre", "duration", "offset" and values are the
     *         corresponding strings/numbers found. Returns null values for tags not found.
     * @throws IOException If an I/O error occurs reading the file.
     * @throws InvalidPathException If the filePath string cannot be converted to a Path.
     */
    public static Map<String, Object> parseMetadataTags(String filePath) throws IOException, InvalidPathException {
        Map<String, Object> metadata = new HashMap<>();
        // Pre-initialize keys to ensure they exist even if tags aren't found
        metadata.put("title", null);
        metadata.put("artist", null);
        metadata.put("genre", null);
        metadata.put("duration", null);
        metadata.put("offset", null);

        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString(); // Get filename for logging

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = METADATA_TAG_PATTERN_FOR_TAGS.matcher(line.trim());
                if (matcher.matches()) {
                    String key = matcher.group(1).toLowerCase(); // "ti", "ar", "length", "offset", etc.
                    String value = matcher.group(2).trim();

                    // Use a temporary variable for the target map key
                    String mapKey = key;
                    Object mapValue = value; // Default to string value

                    // Determine the correct map key and potentially parse the value
                    switch (key) {
                        case "ti":
                            mapKey = "title";
                            break;
                        case "ar":
                            mapKey = "artist";
                            break;
                        case "length":
                            mapKey = "duration";
                            mapValue = parseDurationValue(value, fileName);
                            break;
                        case "offset":
                            mapKey = "offset";
                            mapValue = parseOffsetValue(value, fileName);
                            break;
                        case "genre":
                            // mapKey is already "genre", mapValue is already the string value
                            break;
                        default:
                            // Ignore other tags like 'al', 'by', etc.
                            continue; // Skip to next line
                    }

                    // Store the value using the correct mapKey, but only if:
                    // 1. The value is not effectively empty/null (after potential parsing)
                    // 2. The map doesn't already have a non-null value for this mapKey
                    if (mapValue != null && !(mapValue instanceof String && ((String)mapValue).isEmpty()) && metadata.get(mapKey) == null) {
                         metadata.put(mapKey, mapValue);
                    }
                }
            }
        }

        // Final type safety check (optional but good practice)
        metadata.computeIfPresent("title", (k, v) -> v instanceof String ? v : null);
        metadata.computeIfPresent("artist", (k, v) -> v instanceof String ? v : null);
        metadata.computeIfPresent("genre", (k, v) -> v instanceof String ? v : null);

        return metadata;
    }

    // --- Private Helper Methods ---

    /**
     * Processes a single line from an LRC file for the parseLyrics method.
     * Updates the offset if an offset tag is found, or adds LyricLine objects
     * if timed lyrics are found.
     *
     * @param line Trimmed line content.
     * @param lyricLines List to add parsed LyricLine objects to.
     * @param offsetMillis AtomicLong holding the current offset (updated if tag found).
     */
    private static void processLineForLyricsAndOffset(String line, List<LyricLine> lyricLines, AtomicLong offsetMillis) {
        if (line.isEmpty()) {
            return; // Skip empty lines
        }

        // Check for offset tag first
        Matcher offsetMatcher = OFFSET_TAG_PATTERN.matcher(line);
        if (offsetMatcher.matches()) {
            String offsetValueStr = offsetMatcher.group(1).trim();
            try {
                offsetMillis.set(Long.parseLong(offsetValueStr)); // Update offset
            } catch (NumberFormatException e) {
                System.err.println("Warning: Could not parse offset value in line: " + line);
            }
            return; // Processed offset line, nothing more to do
        }

        // If not an offset tag, check for timed lyrics
        Matcher timeMatcher = TIME_TAG_PATTERN.matcher(line);
        List<Long> timestamps = new ArrayList<>();
        int lastTagEnd = 0;
        boolean foundTimeTag = false;

        while (timeMatcher.find()) {
            foundTimeTag = true;
            Long parsedMillis = parseLrcTimestamp(timeMatcher);
            if (parsedMillis != null) {
                timestamps.add(parsedMillis);
                lastTagEnd = timeMatcher.end(); // Update position after the last valid tag found
            }
        }

        // If valid time tags were found, extract text and add lines
        if (foundTimeTag) {
            String text = line.substring(lastTagEnd).trim();
            if (!text.isEmpty() && !timestamps.isEmpty()) {
                for (Long timestamp : timestamps) {
                    lyricLines.add(new LyricLine(timestamp, text));
                }
            }
            // Ignore lines with time tags but empty text
        }
        // Ignore lines that are not offset, not timed lyrics (e.g., other metadata, comments)
    }

    /**
     * Parses a matched LRC time tag ([mm:ss.xx]) into milliseconds.
     *
     * @param matcher A Matcher object where find() has just returned true for TIME_TAG_PATTERN.
     * @return The timestamp in milliseconds, or null if parsing fails.
     */
    private static Long parseLrcTimestamp(Matcher matcher) {
        try {
            long minutes = Long.parseLong(matcher.group(1));
            long seconds = Long.parseLong(matcher.group(2));
            String millisStr = matcher.group(3);
            long millis = Long.parseLong(millisStr);

            // Adjust if format is centiseconds (xx) instead of milliseconds (xxx)
            if (millisStr.length() == 2) {
                millis *= 10;
            }

            // Basic range check (optional but good)
            if (minutes < 0 || seconds < 0 || seconds >= 60 || millis < 0 || millis >= 1000) {
                throw new NumberFormatException("Time component out of range");
            }

            return (minutes * 60 + seconds) * 1000 + millis;
        } catch (NumberFormatException e) {
            System.err.println("Warning: Could not parse timestamp components from tag match: " + matcher.group(0) + " - " + e.getMessage());
            return null; // Indicate failure
        }
    }

    /**
     * Parses the value string from an LRC [length:mm:ss.xxx] tag into milliseconds.
     *
     * @param value The raw value string (e.g., "03:45.123").
     * @param contextFileName Filename for logging purposes.
     * @return Duration in milliseconds as an Integer, or null if parsing fails or invalid.
     */
    private static Integer parseDurationValue(String value, String contextFileName) {
        try {
            String[] timeParts = value.split(":", 2);
            if (timeParts.length == 2) {
                long minutes = Long.parseLong(timeParts[0].trim());
                double secondsWithMillis = Double.parseDouble(timeParts[1].trim());

                // Validate parts
                if (minutes < 0 || secondsWithMillis < 0.0) {
                     throw new NumberFormatException("Negative time component");
                }

                long seconds = (long) secondsWithMillis;
                long millis = Math.round((secondsWithMillis - seconds) * 1000);

                // Ensure milliseconds are within range after rounding
                 if (millis >= 1000) {
                     seconds += millis / 1000;
                     millis %= 1000;
                 }
                 // Ensure seconds are within range (though typically LRC doesn't enforce this)
                 // if (seconds >= 60) { minutes += seconds / 60; seconds %= 60; }


                long totalMillis = (minutes * 60 + seconds) * 1000 + millis;

                // Check for potential Integer overflow
                if (totalMillis > Integer.MAX_VALUE) {
                    System.err.println("Warning: Parsed duration exceeds Integer max value in " + contextFileName + ": " + value);
                    return Integer.MAX_VALUE; // Or return null? Capping seems safer.
                }
                return (int) totalMillis; // Cast to int for storage
            } else {
                System.err.println("Warning: Invalid duration/length format (expected mm:ss[.xxx]) in " + contextFileName + ": " + value);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            System.err.println("Warning: Could not parse duration/length value in " + contextFileName + ": " + value + " - " + e.getMessage());
        }
        return null; // Return null if any error occurred
    }

    /**
     * Parses the value string from an LRC [offset:...] tag into milliseconds.
     *
     * @param value The raw value string (e.g., "200").
     * @param contextFileName Filename for logging purposes.
     * @return Offset in milliseconds as a Long, or null if parsing fails.
     */
    private static Long parseOffsetValue(String value, String contextFileName) {
        try {
            return Long.parseLong(value); // Offset is typically just milliseconds
        } catch (NumberFormatException e) {
            System.err.println("Warning: Could not parse offset value in " + contextFileName + ": " + value);
            return null; // Return null on failure
        }
    }
}