package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OriginalAudioChoiceTest {
    private static final List<String> RU_WITH_DUBS = Arrays.asList(
            "en‐US (dubbed-auto)", "ru (original)", "de (dubbed-auto)",
            "ru (original)", "en‐US (dubbed-auto)", "de (dubbed-auto)");

    @Test
    public void russianOriginalWinsOverEnglishAndGermanAutoDubs() {
        // Device language English is not an input: nothing here can prefer "en"
        assertEquals("ru (original)", OriginalAudioChoice.choose(RU_WITH_DUBS, null));
    }

    @Test
    public void emptyManualChoiceMeansOriginal() {
        assertEquals("ru (original)", OriginalAudioChoice.choose(RU_WITH_DUBS, ""));
    }

    @Test
    public void manualChoiceAppliesToThisVideo() {
        assertEquals("de (dubbed-auto)", OriginalAudioChoice.choose(RU_WITH_DUBS, "de"));
    }

    @Test
    public void nextVideoWithoutManualChoiceStartsOnOriginalAgain() {
        List<String> next = Arrays.asList("en (original)", "ru (dubbed-auto)", "en (original)");
        assertEquals("de (dubbed-auto)", OriginalAudioChoice.choose(RU_WITH_DUBS, "de"));
        assertEquals("en (original)", OriginalAudioChoice.choose(next, null));
    }

    @Test
    public void manualChoicePrefersHumanDubOverAutoDub() {
        List<String> tracks = Arrays.asList("ru (original)", "en (dubbed-auto)", "en (dubbed)");
        assertEquals("en (dubbed)", OriginalAudioChoice.choose(tracks, "en"));
    }

    @Test
    public void manualChoiceMissingFromVideoFallsBackToOriginal() {
        assertEquals("ru (original)", OriginalAudioChoice.choose(RU_WITH_DUBS, "fr"));
    }

    @Test
    public void noMarkedOriginalPicksTheSingleNonDubbedTrack() {
        List<String> tracks = Arrays.asList("en (dubbed-auto)", "ru", "de (dubbed-auto)");
        assertEquals("ru", OriginalAudioChoice.choose(tracks, null));
    }

    @Test
    public void noMarkedOriginalAndNoUnmarkedPicksHumanDubNotAutoDub() {
        List<String> tracks = Arrays.asList("en (dubbed-auto)", "de (dubbed)", "fr (dubbed-auto)");
        assertEquals("de (dubbed)", OriginalAudioChoice.choose(tracks, null));
    }

    @Test
    public void onlyAutoDubsGivesNoOpinion() {
        List<String> tracks = Arrays.asList("en (dubbed-auto)", "de (dubbed-auto)");
        assertNull(OriginalAudioChoice.choose(tracks, null));
    }

    @Test
    public void twoLanguagesOnlyStillChoosesOriginal() {
        // The old code returned early for lists of two groups
        List<String> tracks = Arrays.asList("ru (original)", "en (dubbed-auto)");
        assertEquals("ru (original)", OriginalAudioChoice.choose(tracks, null));
    }

    @Test
    public void singleLabelGivesNoOpinionSoNoTrackIsDropped() {
        assertNull(OriginalAudioChoice.choose(Collections.singletonList("ru (original)"), null));
        assertNull(OriginalAudioChoice.choose(Collections.singletonList("ru (original)"), "en"));
        // one auto-dub plus tracks without a label: filtering would drop the unlabelled tracks
        assertNull(OriginalAudioChoice.choose(Arrays.asList("en (dubbed-auto)", null, null), null));
    }

    @Test
    public void unknownMarkerRanksBelowPlainAndHumanDub() {
        assertEquals("ru", OriginalAudioChoice.choose(Arrays.asList("de (weird)", "ru", "en (dubbed-auto)"), null));
        assertEquals("fr (dubbed)", OriginalAudioChoice.choose(Arrays.asList("de (weird)", "fr (dubbed)"), null));
    }

    @Test
    public void legacyLocaleCodesMatch() {
        List<String> tracks = Arrays.asList("ru (original)", "id (dubbed)", "he (dubbed)");
        assertEquals("id (dubbed)", OriginalAudioChoice.choose(tracks, "in"));
        assertEquals("he (dubbed)", OriginalAudioChoice.choose(tracks, "iw"));
    }

    @Test
    public void exactLabelOfAPickedTrackIsKept() {
        List<String> tracks = Arrays.asList("ru (original)", "en\u2010us (dubbed)", "en\u2010us (dubbed-auto)");
        assertEquals("en\u2010us (dubbed-auto)", OriginalAudioChoice.choose(tracks, "en\u2010us (dubbed-auto)"));
    }

    @Test
    public void tracksWithoutLanguageGiveNoOpinion() {
        assertNull(OriginalAudioChoice.choose(Arrays.asList(null, null), null));
        assertNull(OriginalAudioChoice.choose(Collections.<String>emptyList(), null));
    }
}
