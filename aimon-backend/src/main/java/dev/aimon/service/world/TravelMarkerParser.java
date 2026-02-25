package dev.aimon.service.world;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses [TRAVEL:XXX] markers from LLM response text.
 * Strips markers for TTS and history storage.
 */
@ApplicationScoped
public class TravelMarkerParser {

    private static final Pattern TRAVEL_PATTERN =
        Pattern.compile("\\[TRAVEL:([A-Z_]+)\\]");

    /** Extract sub-location code from text. Returns null if no marker found. */
    public String parse(String text) {
        if (text == null) return null;
        Matcher m = TRAVEL_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** Strip travel marker from text (for TTS and history storage). */
    public String strip(String text) {
        if (text == null) return text;
        return TRAVEL_PATTERN.matcher(text).replaceAll("").trim();
    }
}
