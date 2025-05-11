package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for writing modifications, specifically the timing offset,
 * back to LRC (.lrc) files using the [offset:...] tag.
 * Supports FR3.7 (Persist lyrics timing adjustment).
 */
public class LrcWriter {
    private static final String OFFSET_TAG_PREFIX = "[offset:";
    private static final Pattern OFFSET_TAG_PATTERN = Pattern.compile(
            "^\\[offset:([+-]?\\d+)\\]\\s*$", Pattern.CASE_INSENSITIVE);

    private LrcWriter() {
        // Private constructor to prevent instantiation
    }

    /**
     * Saves or updates the timing offset in the specified LRC file using the [offset:...] tag.
     * If the tag exists, it's updated. If not, it's added.
     *
     * @param filePath The absolute path to the .lrc file.
     * @param offsetToSaveMillis The offset in milliseconds to save.
     * @throws IOException If an I/O error occurs during file reading or writing.
     */
    public static void saveOffsetToLrcFile(String filePath, long offsetToSaveMillis) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            System.err.println("LrcWriter: Cannot save offset, file path is null or empty.");
            return;
        }

        Path lrcFile = Paths.get(filePath);
        if (!Files.exists(lrcFile) || !Files.isRegularFile(lrcFile) || !Files.isWritable(lrcFile)) {
            System.err.println("LrcWriter: LRC file does not exist, is not a file, or is not writable: " + filePath);
            return;
        }

        List<String> lines = new ArrayList<>();
        boolean offsetTagFound = false;
        String newOffsetLine = OFFSET_TAG_PREFIX + offsetToSaveMillis + "]";

        try (BufferedReader reader = Files.newBufferedReader(lrcFile, StandardCharsets.UTF_8)) {
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                if (OFFSET_TAG_PATTERN.matcher(currentLine.trim()).matches()) {
                    lines.add(newOffsetLine); // Replace existing offset tag
                    offsetTagFound = true;
                } else {
                    lines.add(currentLine);
                }
            }
        }

        if (!offsetTagFound) {
            // Add the new offset tag, typically near other metadata or at the top.
            // This simple implementation adds it at the beginning.
            // A more sophisticated approach might place it after [ti:], [ar:], etc.
            int insertAt = 0;
            Pattern commonMetaPattern = Pattern.compile("^\\[(ti|ar|al|length|by|re|ve):.*\\]\\s*$", Pattern.CASE_INSENSITIVE);
            for (int i = 0; i < lines.size(); i++) {
                if (commonMetaPattern.matcher(lines.get(i).trim()).matches()) {
                    insertAt = i + 1; // Prepare to insert after the current metadata tag
                } else {
                    // Found a non-metadata line (likely a lyric line or empty line),
                    // insert before this if we've passed metadata, otherwise stick to 'insertAt'
                    if (insertAt > 0) break; // Already passed metadata block
                }
            }
            lines.add(insertAt, newOffsetLine);
        }
        
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(lrcFile.getParent(), lrcFile.getFileName().toString(), ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                for (int i = 0; i < lines.size(); i++) {
                    writer.write(lines.get(i));
                    if (i < lines.size() - 1) {
                        writer.newLine();
                    }
                }
            }
            Files.move(tempFile, lrcFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("LrcWriter: Successfully updated [offset:" + offsetToSaveMillis + "ms] in " + lrcFile.getFileName());
        } catch (IOException e) {
            System.err.println("LrcWriter: Error writing updated LRC file " + filePath + ": " + e.getMessage());
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException cleanupEx) { /* ignore */ }
            }
            throw e;
        }
    }
}
