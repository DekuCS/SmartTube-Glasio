package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;

/**
 * MATERIAL: what a track-list audio pick means in the Material UI
 */
public final class AudioPickPolicy {
    private AudioPickPolicy() {
    }

    /**
     * @return true when the pick should be saved as the preferred audio format
     */
    public static boolean applyPick(PlayerData playerData, List<FormatItem> audioFormats, FormatItem pick) {
        String language = pick.getLanguage();

        if (language == null || language.isEmpty()) {
            return true;
        }

        playerData.setTempAudioLanguage(language);

        List<String> languages = new ArrayList<>();

        for (FormatItem format : audioFormats) {
            if (format != null) {
                languages.add(format.getLanguage());
            }
        }

        String automatic = OriginalAudioChoice.choose(languages, null);

        if (automatic == null || automatic.equals(language)) {
            // Same language as the automatic choice: codec or bitrate is a preference, saved as upstream does
            playerData.setTempAudioFormat(null);
            return true;
        }

        // Another language lasts for this video only. Saving it would steer the next videos by its bitrate and codec
        playerData.setTempAudioFormat(pick);
        return false;
    }
}
