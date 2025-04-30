package util;

import model.LyricLine;
import model.SongLyrics; // Import the new SongLyrics class

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
 * Extracts timed lyric lines and metadata tags (including offset).
 * Supports FR3.1, FR3.2 and implicitly FR2.1, FR2.2.
 */
public class LrcParser {

    // Regex for timed lyric lines [mm:ss.xx] or [mm:ss.xxx]
    private static final Pattern TIME_TAG_PATTERN = Pattern.compile(
            "\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})]");

    // Regex for specific metadata tags we might care about (e.g., offset)
    // Captures the tag name (group 1) and the value (group 2)
    private static final Pattern METADATA_TAG_PATTERN = Pattern.compile(
            "^\\[(ti|ar|al|au|length|by|offset|re|ve|genre):(.*)\\]\\s*$");


    // Private constructor to prevent instantiation.
    private LrcParser() { }

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
        long offsetMillis = 0; // Default offset is 0

        Path path = Paths.get(filePath);

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Check for offset tag first
                Matcher offsetMatcher = METADATA_TAG_PATTERN.matcher(line);
                if (offsetMatcher.matches() && "offset".equalsIgnoreCase(offsetMatcher.group(1))) {
                    try {
                        // LRC offset is typically milliseconds to *add* to the timestamps
                        offsetMillis = Long.parseLong(offsetMatcher.group(2).trim());
                        // Optional: log the found offset
                        // System.out.println("Found offset: " + offsetMillis + "ms in " + path.getFileName());
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Could not parse offset value in line: " + line);
                        // Keep default offset of 0
                    }
                    continue; // Processed offset line, move to next line
                }

                // Now check for timed lyrics
                Matcher timeMatcher = TIME_TAG_PATTERN.matcher(line);
                List<Long> timestamps = new ArrayList<>();
                int lastTagEnd = 0;
                boolean foundTimeTag = false;

                while (timeMatcher.find()) {
                    foundTimeTag = true; // Mark that we found at least one time tag
                    try {
                        long minutes = Long.parseLong(timeMatcher.group(1));
                        long seconds = Long.parseLong(timeMatcher.group(2));
                        String millisStr = timeMatcher.group(3);
                        long millis = Long.parseLong(millisStr);

                        if (millisStr.length() == 2) {
                            millis *= 10; // Convert centiseconds to milliseconds
                        }

                        long totalMillis = (minutes * 60 + seconds) * 1000 + millis;
                        timestamps.add(totalMillis);
                        lastTagEnd = timeMatcher.end();
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Could not parse timestamp component in line: " + line);
                        // Skip this specific timestamp, but maybe others on the line are valid
                    }
                }

                // If timestamps were found, extract the lyric text
                if (foundTimeTag) { // Process only if time tags were present
                    String text = line.substring(lastTagEnd).trim();
                    // Add a LyricLine object for each timestamp associated with this text,
                    // only if the text is not empty.
                    if (!text.isEmpty()) {
                        for (Long timestamp : timestamps) {
                            lyricLines.add(new LyricLine(timestamp, text));
                        }
                    }
                     // If text is empty after valid time tags, we typically ignore it.
                     // else { System.err.println("Warning: Empty text after time tags: " + line); }

                } else if (!line.startsWith("[")) {
                    // Line wasn't empty, didn't have time tags, wasn't an offset tag,
                    // and doesn't look like other metadata (doesn't start with '[').
                    // Treat as potential comment or unstructured text - ignore.
                    // System.err.println("Info: Skipping non-metadata, non-timed line: " + line);
                }
                // Ignore lines matching METADATA_TAG_PATTERN that are not 'offset'
            }
        }

        // Sort the extracted lyric lines by timestamp
        Collections.sort(lyricLines);

        // Return the result encapsulated in a SongLyrics object
        return new SongLyrics(lyricLines, offsetMillis);
    }

    /**
     * Parses the given LRC file to extract specific metadata tags (Title, Artist, Genre, Duration, Offset).
    * Note: This method does NOT parse the lyric lines themselves.
    *       Use parseLyrics() for lyric lines and offset extraction together if needed elsewhere.
    *
    * @param filePath The path to the .lrc file.
    * @return A Map where keys are "title", "artist", "genre", "duration", "offset" and values are the
    *         corresponding strings/numbers found. Returns null values for tags not found.
    * @throws IOException If an I/O error occurs reading the file.
    * @throws InvalidPathException If the filePath string cannot be converted to a Path.
    */
    public static Map<String, Object> parseMetadataTags(String filePath) throws IOException, InvalidPathException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", null);
        metadata.put("artist", null);
        metadata.put("genre", null);
        metadata.put("duration", null); // Store duration as Integer
        metadata.put("offset", null);   // Store offset as Long

        Path path = Paths.get(filePath);
        Pattern metadataPattern = Pattern.compile("^\\[(ti|ar|al|length|genre|offset):(.*)\\]\\s*$"); // Match relevant tags

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = metadataPattern.matcher(line.trim());
                if (matcher.matches()) {
                    String key = matcher.group(1).toLowerCase();
                    String value = matcher.group(2).trim();

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
                        case "length": // LRC 'length' tag corresponds to 'duration', format mm:ss[.xxx]
                            if (metadata.get("duration") == null) {
                                try {
                                    // Split into minutes and seconds[.milliseconds] parts
                                    String[] timeParts = value.split(":", 2);
                                    if (timeParts.length == 2) {
                                        long minutes = Long.parseLong(timeParts[0].trim());

                                        // Use double to handle potential milliseconds
                                        double secondsWithMillis = Double.parseDouble(timeParts[1].trim());

                                        long seconds = (long) secondsWithMillis; // Get the whole seconds part
                                        // Calculate milliseconds from the fractional part
                                        long millis = Math.round((secondsWithMillis - seconds) * 1000);

                                        // Calculate total duration in milliseconds
                                        long totalMillis = (minutes * 60 + seconds) * 1000 + millis;

                                        // Store as Integer, checking for potential overflow
                                        if (totalMillis > Integer.MAX_VALUE) {
                                            System.err.println("Warning: Parsed duration exceeds Integer max value in " + path.getFileName() + ": " + value);
                                            metadata.put("duration", Integer.MAX_VALUE); // Cap at max value? Or null?
                                        } else if (totalMillis < 0) {
                                             System.err.println("Warning: Parsed duration is negative in " + path.getFileName() + ": " + value);
                                             metadata.put("duration", null); // Negative duration is invalid
                                        }
                                        else {
                                            metadata.put("duration", (int) totalMillis);
                                        }
                                    } else {
                                        // Format was not mm:ss...
                                        System.err.println("Warning: Invalid duration/length format (expected mm:ss[.xxx]) in " + path.getFileName() + ": " + value);
                                        metadata.put("duration", null);
                                    }
                                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                                    // Catch parsing errors or errors from splitting
                                    System.err.println("Warning: Could not parse duration/length value in " + path.getFileName() + ": " + value + " - " + e.getMessage());
                                    metadata.put("duration", null);
                                }
                            }
                            break; // End case "length"
                        case "offset": // Handle offset tag
                        if (metadata.get("offset") == null && !value.isEmpty()) {
                            try {
                                // Parse offset tag value as long (milliseconds)
                                metadata.put("offset", Long.parseLong(value));
                            } catch (NumberFormatException e) {
                                System.err.println("Warning: Could not parse offset value in " + path.getFileName() + ": " + value);
                                metadata.put("offset", null); // Ensure it's null if parsing fails
                            }
                            }
                            break;
                        // Ignore other tags like 'al'
                    }
                }
            }
        }
        // Ensure required string fields are actually strings or null
        metadata.computeIfPresent("title", (k, v) -> v instanceof String ? v : null);
        metadata.computeIfPresent("artist", (k, v) -> v instanceof String ? v : null);
        metadata.computeIfPresent("genre", (k, v) -> v instanceof String ? v : null);

        return metadata;
    }
}