package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * MATERIAL: picks the audio language a video starts with.<br/>
 * The language label comes from the manifest: {@code "<lang> (<acont>)"}, where acont is one of
 * original, descriptive, dubbed, dubbed-auto, secondary. A plain {@code "<lang>"} means no marker.<br/>
 * The device language and any language saved by the user are never inputs.
 */
public final class OriginalAudioChoice {
    private OriginalAudioChoice() {
    }

    /**
     * @param languages    language label of every audio track of the video (null entries are ignored)
     * @param manualChoice language code chosen by the user for the current video only; null or empty for none
     * @return the exact label to keep, or null when there is nothing to choose between
     */
    @Nullable
    public static String choose(Collection<String> languages, @Nullable String manualChoice) {
        Set<String> labels = new LinkedHashSet<>();

        for (String language : languages) {
            if (language != null) {
                labels.add(language);
            }
        }

        if (labels.size() <= 1) {
            return null; // nothing to choose between. Filtering would only drop the tracks without a label
        }

        if (manualChoice != null && !manualChoice.isEmpty()) {
            String manual = bestMatch(labels, manualChoice.toLowerCase(Locale.ROOT));

            if (manual != null) {
                return manual;
            }
        }

        String original = firstWithKind(labels, Kind.ORIGINAL);

        if (original != null) {
            return original;
        }

        // No marked original: a track without any marker, then a human dub, never an auto dub
        for (Kind kind : new Kind[] {Kind.PLAIN, Kind.DUBBED, Kind.SECONDARY, Kind.DESCRIPTIVE, Kind.UNKNOWN}) {
            String label = firstWithKind(labels, kind);

            if (label != null) {
                return label;
            }
        }

        return null;
    }

    /**
     * Label of the requested language; a human dub is preferred over an auto dub
     */
    @Nullable
    private static String bestMatch(Set<String> labels, String languageCode) {
        String result = null;
        String legacyFixed = fixLangCode(languageCode);

        for (String label : labels) {
            if (label.toLowerCase(Locale.ROOT).equals(languageCode)) { // the exact label of a track picked from the list
                return label;
            }
        }

        for (String label : labels) {
            String lower = label.toLowerCase(Locale.ROOT);

            if (!lower.startsWith(languageCode) && !lower.startsWith(legacyFixed)) {
                continue;
            }

            if (kindOf(label) != Kind.AUTO_DUBBED) {
                return label;
            }

            if (result == null) {
                result = label;
            }
        }

        return result;
    }

    @Nullable
    private static String firstWithKind(Set<String> labels, Kind kind) {
        for (String label : labels) {
            if (kindOf(label) == kind) {
                return label;
            }
        }

        return null;
    }

    /** Locale.getLanguage() still returns the legacy codes */
    private static String fixLangCode(String code) {
        switch (code) {
            case "in":
                return "id";
            case "iw":
                return "he";
            case "ji":
                return "yi";
            default:
                return code;
        }
    }

    private enum Kind { ORIGINAL, PLAIN, DUBBED, SECONDARY, DESCRIPTIVE, UNKNOWN, AUTO_DUBBED }

    private static Kind kindOf(String label) {
        String lower = label.toLowerCase(Locale.ROOT);

        if (lower.contains("dubbed-auto") || lower.contains("auto-dubbed")) {
            return Kind.AUTO_DUBBED;
        } else if (lower.contains("original")) {
            return Kind.ORIGINAL;
        } else if (lower.contains("dubbed")) {
            return Kind.DUBBED;
        } else if (lower.contains("secondary")) {
            return Kind.SECONDARY;
        } else if (lower.contains("descriptive")) {
            return Kind.DESCRIPTIVE;
        }

        return lower.contains("(") ? Kind.UNKNOWN : Kind.PLAIN; // an acont this code does not know
    }
}
