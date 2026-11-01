package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.AudioTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FilterToOriginalAudioTest {
    private static final String EN_AUTO = "en‐us (dubbed-auto)";

    private static AudioTrack track(String id, String language) {
        AudioTrack track = new AudioTrack(TrackSelectorManager.RENDERER_INDEX_AUDIO);
        track.format = Format.createAudioContainerFormat(id, "audio/webm", "audio/opus", "opus", 128000, 2, 48000, null, 0, language);
        return track;
    }

    @Test
    public void keepsOnlyOriginalTracksAndTheGroupStructure() {
        MediaTrack ruLow = track("1", "ru (original)");
        MediaTrack ruHigh = track("2", "ru (original)");
        MediaTrack en = track("3", EN_AUTO);
        MediaTrack[][] groups = {{en, ruLow}, {ruHigh}, {track("4", EN_AUTO)}};

        MediaTrack[][] result = TrackSelectorManager.filterToOriginalAudio(groups, null);

        assertEquals(2, result.length);
        assertArrayEquals(new MediaTrack[] {ruLow}, result[0]);
        assertArrayEquals(new MediaTrack[] {ruHigh}, result[1]);
    }

    @Test
    public void nullGroupsNullTracksAndTracksWithoutFormatAreSkipped() {
        MediaTrack ru = track("1", "ru (original)");
        MediaTrack noFormat = new AudioTrack(TrackSelectorManager.RENDERER_INDEX_AUDIO);
        MediaTrack[][] groups = {null, {null, noFormat, track("2", EN_AUTO), ru}};

        MediaTrack[][] result = TrackSelectorManager.filterToOriginalAudio(groups, null);

        assertEquals(1, result.length);
        assertArrayEquals(new MediaTrack[] {ru}, result[0]);
    }

    @Test
    public void noOpinionReturnsTheSameList() {
        MediaTrack[][] noLanguage = {{track("1", null), track("2", null)}};
        MediaTrack[][] onlyAutoDubs = {{track("1", EN_AUTO), track("2", "de (dubbed-auto)")}};
        MediaTrack[][] empty = {};

        assertSame(noLanguage, TrackSelectorManager.filterToOriginalAudio(noLanguage, null));
        assertSame(onlyAutoDubs, TrackSelectorManager.filterToOriginalAudio(onlyAutoDubs, null));
        assertSame(empty, TrackSelectorManager.filterToOriginalAudio(empty, null));
    }

    @Test
    public void trackListPickStaysThroughReSelection() {
        // The pick is recorded as the manual choice. Re-selecting the saved track (isSaved) must keep it
        MediaTrack ru = track("1", "ru (original)");
        MediaTrack en = track("2", EN_AUTO);
        MediaTrack[][] groups = {{ru, en}};

        for (int i = 0; i < 2; i++) {
            MediaTrack[][] result = TrackSelectorManager.filterToOriginalAudio(groups, EN_AUTO);
            assertArrayEquals(new MediaTrack[] {en}, result[0]);
        }

        // The next video: the manual choice is cleared
        assertArrayEquals(new MediaTrack[] {ru}, TrackSelectorManager.filterToOriginalAudio(groups, null)[0]);
    }

    @Test
    public void savedTrackOfAnotherLanguageIsIgnored() {
        MediaTrack ru = track("1", "ru (original)");
        MediaTrack[][] filtered = {{ru}};
        AudioTrack savedEn = track("2", EN_AUTO);
        savedEn.isSaved = true;
        AudioTrack savedRu = track("3", "ru (original)");
        savedRu.isSaved = true;
        AudioTrack savedDefault = track("4", null);
        savedDefault.isSaved = true;
        AudioTrack pickedEn = track("5", EN_AUTO); // a pick of this video is not saved

        assertTrue(TrackSelectorManager.isSavedFromOtherLanguage(savedEn, filtered));
        assertFalse(TrackSelectorManager.isSavedFromOtherLanguage(savedRu, filtered));
        assertFalse(TrackSelectorManager.isSavedFromOtherLanguage(savedDefault, filtered));
        assertFalse(TrackSelectorManager.isSavedFromOtherLanguage(pickedEn, filtered));
    }
}
