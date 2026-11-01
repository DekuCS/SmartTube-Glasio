package com.liskovsoft.smartyoutubetv2.common.utils;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AudioLanguageDialogTest {
    @Test
    public void materialChoiceInTheDialogDropsThePickFromTheTrackList() {
        PlayerData playerData = PlayerData.instance(RuntimeEnvironment.application);
        playerData.setTempAudioFormat(ExoFormatItem.from(Format.createAudioContainerFormat(
                "250", "audio/webm", "audio/opus", "opus", 81500, 2, 48000, null, 0, "en (dubbed-auto)")));
        playerData.setTempAudioLanguage("en (dubbed-auto)");

        AppDialogUtil.selectAudioLanguage(playerData, "", true); // Original

        assertEquals("", playerData.getTempAudioLanguage());
        assertNull(playerData.getTempAudioFormat()); // restoreAudioFormat would re-apply it after the restart
    }
}
