package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import org.robolectric.RuntimeEnvironment;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AudioPickPolicyTest {
    private static final String EN_AUTO = "en‐us (dubbed-auto)";
    private PlayerData mPlayerData;
    private FormatItem mRu;
    private FormatItem mEn;
    private List<FormatItem> mFormats;

    private static FormatItem item(String id, int bitrate, String language) {
        return ExoFormatItem.from(Format.createAudioContainerFormat(
                id, "audio/webm", "audio/opus", "opus", bitrate, 2, 48000, null, 0, language));
    }

    @Before
    public void setUp() {
        mPlayerData = PlayerData.instance(RuntimeEnvironment.application);
        mPlayerData.setTempAudioFormat(null);
        mPlayerData.setTempAudioLanguage(null);
        mRu = item("251", 143000, "ru (original)");
        mEn = item("251", 80000, EN_AUTO);
        mFormats = Arrays.asList(mRu, mEn, item("140", 128000, EN_AUTO));
    }

    @Test
    public void pickOfAnotherLanguageLastsForThisVideoAndIsNotSaved() {
        boolean persist = AudioPickPolicy.applyPick(mPlayerData, mFormats, mEn);

        assertFalse(persist);
        assertEquals(EN_AUTO, mPlayerData.getTempAudioLanguage());
        assertSame(mEn, mPlayerData.getTempAudioFormat());
    }

    @Test
    public void pickWithinTheAutomaticLanguageIsSavedAsAPreference() {
        mPlayerData.setTempAudioFormat(mEn); // left from an earlier pick of this video

        boolean persist = AudioPickPolicy.applyPick(mPlayerData, mFormats, mRu);

        assertTrue(persist);
        assertNull(mPlayerData.getTempAudioFormat());
    }

    @Test
    public void pickWithoutLanguageIsSaved() {
        assertTrue(AudioPickPolicy.applyPick(mPlayerData, mFormats, item("251", 100000, null)));
        assertNull(mPlayerData.getTempAudioLanguage());
    }
}
