package util;

import model.LyricLine;

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
 * Extracts timed lyric lines, a single offset ([offset:...]), and specific metadata tags.
 */
public class LrcParser {

    // Regex for timed lyric lines [mm:ss.xx] or [mm:ss.xxx]
    private static final Pattern TIME_TAG_PATTERN = Pattern.compile(
            "\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})]"); // Group 1:min, 2:sec, 3:ms/cs

    // Updated to include only 'offset' for metadata parsing related to this functionality.
    // Other tags like 'user_offset' are removed from specific parsing here.
    private static final Pattern METADATA_TAG_PATTERN_FOR_TAGS = Pattern.compile(
            "^\\[(ti|ar|al|length|genre|offset):(.*)\\]\\s*$"); // Group 1:key, 2:value

    // This is the single offset tag we care about for lyric timing.
    private static final Pattern LYRIC_OFFSET_TAG_PATTERN = Pattern.compile(
            "^\\[offset:(.*)\\]\\s*$", Pattern.CASE_INSENSITIVE); // Group 1:value

    // Private constructor to prevent instantiation.
    private LrcParser() { }

    /**
     * Container for results from parsing an LRC file for lyrics content and its single offset.
     */
    public static class LrcParseResult {
        public final List<LyricLine> lines;
        public final long offsetMillis;  // From [offset:...]

        public LrcParseResult(List<LyricLine> lines, long offsetMillis) {
            this.lines = (lines != null) ? Collections.unmodifiableList(lines) : Collections.emptyList();
            this.offsetMillis = offsetMillis;
        }
        
        public List<LyricLine> getLines() { return lines; }
        public long getOffsetMillis() { return offsetMillis; }
    }

    /**
     * Parses the given LRC file to extract timed lyric lines and the single [offset:...] value.
     *
     * @param filePath The path to the .lrc file.
     * @return An LrcParseResult object. Offset defaults to 0 if not found.
     * @throws IOException If an I/O error occurs.
     * @throws InvalidPathException If the filePath is invalid.
     */
    public static LrcParseResult parseLyricsAndOffset(String filePath) throws IOException, InvalidPathException {
        List<LyricLine> lyricLines = new ArrayList<>();
        AtomicLong parsedOffsetMillis = new AtomicLong(0); // Default to 0

        Path path = Paths.get(filePath);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Delegate line processing to helper method
                processLineForLyricsAndSingleOffset(line.trim(), lyricLines, parsedOffsetMillis);
            }
        }

        Collections.sort(lyricLines); // Ensure lines are sorted by timestamp
        return new LrcParseResult(lyricLines, parsedOffsetMillis.get());
    }
    
    /**
     * Processes a single line from an LRC file for the parseLyricsAndOffset method.
     * Updates the offset if a single [offset:...] tag is found, or adds LyricLine objects
     * if timed lyrics are found.
     *
     * @param line Trimmed line content.
     * @param lyricLines List to add parsed LyricLine objects to.
     * @param offsetRef AtomicLong holding the current offset (updated if tag found).
     */
    private static void processLineForLyricsAndSingleOffset(String line, List<LyricLine> lyricLines,
                                                             AtomicLong offsetRef) {
        if (line.isEmpty()) return;

        Matcher offsetMatcher = LYRIC_OFFSET_TAG_PATTERN.matcher(line);
        if (offsetMatcher.matches()) {
            String offsetValueStr = offsetMatcher.group(1).trim();
            try {
                offsetRef.set(Long.parseLong(offsetValueStr));
            } catch (NumberFormatException e) {
                System.err.println("Warning: Could not parse [offset:] value in line: " + line);
            }
            return; 
        }
        
        Matcher timeMatcher = TIME_TAG_PATTERN.matcher(line);
        List<Long> timestamps = new ArrayList<>();
        int lastTagEnd = 0;
        boolean foundTimeTag = false;

        while (timeMatcher.find()) {
            foundTimeTag = true;
            Long parsedMillis = parseLrcTimestamp(timeMatcher);
            if (parsedMillis != null) {
                timestamps.add(parsedMillis);
                lastTagEnd = timeMatcher.end();
            }
        }

        if (foundTimeTag) {
            String text = line.substring(lastTagEnd).trim();
            // Empty text lines are allowed as long as there's a timestamp
            if (!timestamps.isEmpty()) { 
                for (Long timestamp : timestamps) {
                    lyricLines.add(new LyricLine(timestamp, text));
                }
            }
        }
    }

    /**
     * Parses metadata tags, including the single [offset:...].
     * (Ensure this method is consistent with how offset is treated elsewhere if used for DB population)
     */
    public static Map<String, Object> parseMetadataTags(String filePath) throws IOException, InvalidPathException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", null);
        metadata.put("artist", null);
        metadata.put("genre", null);
        metadata.put("duration", null); 
        metadata.put("offset", null); // For the single [offset:...] tag

        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = METADATA_TAG_PATTERN_FOR_TAGS.matcher(line.trim());
                if (matcher.matches()) {
                    String key = matcher.group(1).toLowerCase();
                    String value = matcher.group(2).trim();
                    String mapKey = key;
                    Object mapValue = value;

                    switch (key) {
                        case "ti": mapKey = "title"; break;
                        case "ar": mapKey = "artist"; break;
                        case "length":
                            mapKey = "duration";
                            mapValue = parseDurationValue(value, fileName);
                            break;
                        case "offset": // Handles the single [offset:...] tag
                            mapKey = "offset";
                            mapValue = parseOffsetValue(value, fileName);
                            break;
                        case "genre": break;
                        default: continue;
                    }
                    if (mapValue != null && !(mapValue instanceof String && ((String)mapValue).isEmpty()) && metadata.get(mapKey) == null) {
                         metadata.put(mapKey, mapValue);
                    }
                }
            }
        }
        // Type safety for string fields
        metadata.computeIfPresent("title", (_, val) -> val instanceof String ? val : null);
        metadata.computeIfPresent("artist", (_, val) -> val instanceof String ? val : null);
        metadata.computeIfPresent("genre", (_, val) -> val instanceof String ? val : null);
        return metadata;
    }

    // --- Helper Methods (parseLrcTimestamp, parseDurationValue, parseOffsetValue) ---
    // These remain largely the same as in the previous version of LrcParser.java
    // Ensure parseOffsetValue is used for the [offset:] tag.

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
            if (millisStr.length() == 2) millis *= 10;
            if (minutes < 0 || seconds < 0 || seconds >= 60 || millis < 0 || millis >= 1000) {
                throw new NumberFormatException("Time component out of range");
            }
            return (minutes * 60 + seconds) * 1000 + millis;
        } catch (NumberFormatException e) {
            System.err.println("Warning: Could not parse timestamp: " + matcher.group(0) + " - " + e.getMessage());
            return null;
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
                if (minutes < 0 || secondsWithMillis < 0.0) throw new NumberFormatException("Negative time");
                long seconds = (long) secondsWithMillis;
                long millis = Math.round((secondsWithMillis - seconds) * 1000);
                if (millis >= 1000) { seconds += millis / 1000; millis %= 1000; }
                long totalMillis = (minutes * 60 + seconds) * 1000 + millis;
                if (totalMillis > Integer.MAX_VALUE) return Integer.MAX_VALUE;
                return (int) totalMillis;
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            System.err.println("Warning: Could not parse duration in " + contextFileName + ": " + value + " - " + e.getMessage());
        }
        return null;
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
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Warning: Could not parse offset in " + contextFileName + ": '" + value + "' - " + e.getMessage());
            return null;
        }
    }
}