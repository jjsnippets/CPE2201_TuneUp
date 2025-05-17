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
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing LRC (LyRiCs) files, a common format for synchronized lyrics.
 * This non-instantiable class provides static methods to extract timed lyric lines
 * and metadata tags (such as title, artist, album, genre, duration, and a global offset).
 *
 * Conforms to:
 * <ul>
 *   <li>FR3.1: Parse LRC files for lyrics and metadata.</li>
 * </ul>
 *
 * The parser handles:
 * <ul>
 *   <li>Time tags for lyrics: {@code [mm:ss.xx]} or {@code [mm:ss.xxx]} (xx for centiseconds, xxx for milliseconds).</li>
 *   <li>Metadata tags: {@code [tag:value]}, e.g., {@code [ti:Song Title]}, {@code [ar:Artist Name]},
 *   {@code [al:Album Name]}, {@code [length:mm:ss.xxx]} (for song duration),
 *   {@code [genre:Song Genre]}, and {@code [offset:value]} (for global lyric timing adjustment in milliseconds).<li>
 * </ul>
 *
 * All file operations are performed using UTF-8 encoding.
 */
public final class LrcParser {

    // Regex for timed lyric lines: [mm:ss.xx] or [mm:ss.xxx]
    // Group 1: minutes, Group 2: seconds, Group 3: centiseconds or milliseconds
    private static final Pattern TIME_TAG_PATTERN = Pattern.compile(
            "\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\]");

    // Regex for standard metadata tags: [key:value]
    // Group 1: key (e.g., "ti", "ar", "al", "length", "genre", "offset")
    // Group 2: value
    private static final Pattern METADATA_TAG_PATTERN = Pattern.compile(
            "^\\[(ti|ar|al|length|genre|offset):(.*)\\]\\s*$", Pattern.CASE_INSENSITIVE);

    // Regex specifically for the [offset:...] tag, used for global lyric timing adjustment.
    // Group 1: offset value (milliseconds)
    private static final Pattern LYRIC_OFFSET_TAG_PATTERN = Pattern.compile(
            "^\\[offset:(.*)\\]\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private LrcParser() {
        // This class is not meant to be instantiated.
    }

    /**
     * Container for the results of parsing an LRC file, specifically holding the list of
     * timed lyric lines and the global offset value defined by an {@code [offset:...]} tag.
     */
    public static class LrcParseResult {
        private final List<LyricLine> lines;
        private final long offsetMillis;  // From [offset:...] tag, defaults to 0 if not found.

        /**
         * Constructs an LrcParseResult.
         * @param lines A list of {@link LyricLine} objects. If null, an empty unmodifiable list is used.
         * @param offsetMillis The global offset in milliseconds.
         */
        public LrcParseResult(List<LyricLine> lines, long offsetMillis) {
            this.lines = (lines != null) ? Collections.unmodifiableList(new ArrayList<>(lines)) : Collections.emptyList();
            this.offsetMillis = offsetMillis;
        }

        /**
         * @return An unmodifiable list of parsed {@link LyricLine} objects, sorted by timestamp.
         */
        public List<LyricLine> getLines() {
            return lines;
        }

        /**
         * @return The global lyric offset in milliseconds, extracted from an {@code [offset:...]} tag.
         *         Defaults to 0 if the tag is not present or invalid.
         */
        public long getOffsetMillis() {
            return offsetMillis;
        }
    }

    /**
     * Parses the given LRC file to extract timed lyric lines and the global {@code [offset:...]} value.
     * Lines are read using UTF-8 encoding. Lyric lines are sorted by their timestamps.
     *
     * @param filePath The absolute path to the .lrc file.
     * @return An {@link LrcParseResult} object containing the list of {@link LyricLine}s and the global offset.
     *         The offset defaults to 0 if no valid {@code [offset:...]} tag is found.
     * @throws IOException If an I/O error occurs during file reading.
     * @throws InvalidPathException If the {@code filePath} string cannot be converted to a {@link Path}.
     */
    public static LrcParseResult parseLyricsAndOffset(String filePath) throws IOException, InvalidPathException {
        List<LyricLine> lyricLines = new ArrayList<>();
        AtomicLong parsedOffsetMillis = new AtomicLong(0); // Default to 0, updated if [offset:] tag is found.

        Path path = Paths.get(filePath);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                processLineForLyricsAndSingleOffset(line.trim(), lyricLines, parsedOffsetMillis);
            }
        }

        Collections.sort(lyricLines); // Ensure lines are sorted by timestamp.
        return new LrcParseResult(lyricLines, parsedOffsetMillis.get());
    }

    /**
     * Processes a single line from an LRC file for the {@link #parseLyricsAndOffset(String)} method.
     * If the line is an {@code [offset:...]} tag, it updates the {@code offsetRef}.
     * If the line contains timed lyric tags, it parses them and adds {@link LyricLine} objects to {@code lyricLines}.
     *
     * @param line The trimmed line content from the LRC file.
     * @param lyricLines The list to which parsed {@link LyricLine} objects are added.
     * @param offsetRef An {@link AtomicLong} holding the current global offset, which is updated
     *                  if an {@code [offset:...]} tag is encountered.
     */
    private static void processLineForLyricsAndSingleOffset(String line, List<LyricLine> lyricLines,
                                                             AtomicLong offsetRef) {
        if (line.isEmpty()) {
            return;
        }

        Matcher offsetMatcher = LYRIC_OFFSET_TAG_PATTERN.matcher(line);
        if (offsetMatcher.matches()) {
            String offsetValueStr = offsetMatcher.group(1).trim();
            try {
                offsetRef.set(Long.parseLong(offsetValueStr));
            } catch (NumberFormatException e) {
                System.err.println("Warning: Could not parse [offset:] value in line: \"" + line + "\". Error: " + e.getMessage());
            }
            return; // An offset line does not contain lyrics.
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
            // Even if the text part is empty, if there are valid timestamps, add them.
            // This handles LRC lines that might just be for timing or empty phrases.
            if (!timestamps.isEmpty()) {
                for (Long timestamp : timestamps) {
                    lyricLines.add(new LyricLine(timestamp, text));
                }
            }
        }
    }

    /**
     * Parses metadata tags (e.g., title, artist, album, duration from {@code [length:]}, genre, global {@code [offset:]})
     * from the specified LRC file.
     * Metadata keys in the returned map are: "title", "artist", "album", "genre", "duration" (Integer, milliseconds),
     * and "offset" (Long, milliseconds). If a tag is not found or its value is invalid, the corresponding
     * map entry may be {@code null} or absent for typed values (duration, offset).
     *
     * @param filePath The absolute path to the .lrc file.
     * @return A {@link Map} where keys are metadata tag names (e.g., "title", "artist") and
     *         values are the parsed metadata values (String, Integer for duration, Long for offset).
     * @throws IOException If an I/O error occurs during file reading.
     * @throws InvalidPathException If the {@code filePath} string cannot be converted to a {@link Path}.
     */
    public static Map<String, Object> parseMetadataTags(String filePath) throws IOException, InvalidPathException {
        Map<String, Object> metadata = new HashMap<>();
        // Initialize expected keys to null.
        metadata.put("title", null);
        metadata.put("artist", null);
        metadata.put("album", null);
        metadata.put("genre", null);
        metadata.put("duration", null); // Will be Integer (milliseconds)
        metadata.put("offset", null);   // Will be Long (milliseconds)

        Path path = Paths.get(filePath);
        String fileNameForContext = path.getFileName().toString(); // For logging

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = METADATA_TAG_PATTERN.matcher(line.trim());
                if (matcher.matches()) {
                    String key = matcher.group(1).toLowerCase().trim();
                    String value = matcher.group(2).trim();

                    switch (key) {
                        case "ti":
                            metadata.put("title", value);
                            break;
                        case "ar":
                            metadata.put("artist", value);
                            break;
                        case "al":
                            metadata.put("album", value);
                            break;
                        case "length":
                            Integer durationMillis = parseDurationValue(value, fileNameForContext);
                            if (durationMillis != null) {
                                metadata.put("duration", durationMillis);
                            }
                            break;
                        case "genre":
                            metadata.put("genre", value);
                            break;
                        case "offset":
                            Long offsetMillis = parseOffsetValue(value, fileNameForContext);
                            if (offsetMillis != null) {
                                metadata.put("offset", offsetMillis);
                            }
                            break;
                        default:
                            // Silently ignore unknown tags, or log if necessary:
                            // System.err.println("Info: Unknown metadata tag '" + key + "' in file: " + fileNameForContext);
                            break;
                    }
                }
            }
        }
        return metadata;
    }

    /**
     * Parses a matched LRC time tag (e.g., {@code [mm:ss.xx]} or {@code [mm:ss.xxx]}) into total milliseconds.
     *
     * @param matcher A {@link Matcher} object for {@link #TIME_TAG_PATTERN} where {@code find()}
     *                has just returned {@code true}.
     * @return The timestamp in milliseconds, or {@code null} if parsing fails or components are invalid.
     */
    private static Long parseLrcTimestamp(Matcher matcher) {
        try {
            long minutes = Long.parseLong(matcher.group(1));
            long seconds = Long.parseLong(matcher.group(2));
            String fracSecStr = matcher.group(3); // Milliseconds or centiseconds part
            long fracSeconds = Long.parseLong(fracSecStr);

            if (fracSecStr.length() == 2) { // Input is in centiseconds
                fracSeconds *= 10; // Convert centiseconds to milliseconds
            }

            // Validate time components
            if (minutes < 0 || seconds < 0 || seconds >= 60 || fracSeconds < 0 || fracSeconds >= 1000) {
                System.err.println("Warning: Invalid time components in LRC timestamp: " + matcher.group(0));
                return null;
            }
            return (minutes * 60 + seconds) * 1000 + fracSeconds;
        } catch (NumberFormatException e) {
            System.err.println("Warning: Could not parse LRC timestamp: " + matcher.group(0) + ". Error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses the value string from an LRC {@code [length:value]} tag into total milliseconds.
     * The expected format for the value is "mm:ss" or "mm:ss.xx" (centiseconds) or "mm:ss.xxx" (milliseconds).
     *
     * @param value The raw time string (e.g., "03:45.12", "03:45.123", "03:45").
     * @param contextFileName The name of the LRC file being parsed, for logging context.
     * @return Duration in milliseconds as an {@link Integer}, or {@code null} if parsing fails,
     *         the format is invalid, or the value represents an invalid time.
     */
    private static Integer parseDurationValue(String value, String contextFileName) {
        // Regex for "mm:ss" or "mm:ss.cs" or "mm:ss.ms"
        Pattern durationPattern = Pattern.compile("^(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?$");
        Matcher matcher = durationPattern.matcher(value.trim());

        if (matcher.matches()) {
            try {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                long millis = 0;

                String millisStr = matcher.group(3);
                if (millisStr != null) {
                    if (millisStr.length() > 3) { // Max 3 digits for ms
                         System.err.println("Warning: Millisecond part too long in duration in " + contextFileName + ": " + value);
                         return null;
                    }
                    millis = Long.parseLong(millisStr);
                    if (millisStr.length() == 1) { // e.g., [00:00.1] -> 100ms
                        millis *= 100;
                    } else if (millisStr.length() == 2) { // e.g., [00:00.12] -> 120ms (centiseconds)
                        millis *= 10;
                    }
                    // If length is 3, it's already milliseconds.
                }

                if (minutes < 0 || seconds < 0 || seconds >= 60 || millis < 0 || millis >= 1000) {
                    System.err.println("Warning: Invalid time components in duration in " + contextFileName + ": " + value);
                    return null;
                }

                long totalMillis = (minutes * 60 + seconds) * 1000 + millis;
                if (totalMillis > Integer.MAX_VALUE) {
                    System.err.println("Warning: Duration value " + totalMillis + "ms exceeds Integer.MAX_VALUE in " + contextFileName + ": " + value);
                    return null;
                }
                return (int) totalMillis;
            } catch (NumberFormatException e) {
                System.err.println("Warning: Could not parse numeric parts of duration in " + contextFileName + ": " + value + ". Error: " + e.getMessage());
                return null;
            }
        } else {
            System.err.println("Warning: Duration format error in " + contextFileName + ". Expected mm:ss[.xxx], got: \"" + value + "\"");
            return null;
        }
    }

    /**
     * Parses the value string from an LRC {@code [offset:value]} tag into milliseconds.
     *
     * @param value The raw offset value string (e.g., "200", "-150").
     * @param contextFileName The name of the LRC file being parsed, for logging context.
     * @return Offset in milliseconds as a {@link Long}, or {@code null} if parsing fails.
     */
    private static Long parseOffsetValue(String value, String contextFileName) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Warning: Could not parse offset value in " + contextFileName + ": \"" + value + "\". Error: " + e.getMessage());
            return null;
        }
    }
}