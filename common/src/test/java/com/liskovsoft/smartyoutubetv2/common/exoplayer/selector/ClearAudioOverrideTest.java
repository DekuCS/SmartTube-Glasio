package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ClearAudioOverrideTest {
    private static TrackGroupArray groups() {
        Format format = Format.createAudioContainerFormat("250", "audio/webm", "audio/opus", "opus", 81500, 2, 48000, null, 0, "en (dubbed-auto)");
        return new TrackGroupArray(new TrackGroup(format));
    }

    @Test
    public void materialClearsTheAudioOverrideLeftByAPick() {
        SidebarService.instance(RuntimeEnvironment.application).enableMaterialUi();
        DefaultTrackSelector selector = new DefaultTrackSelector();
        TrackGroupArray groups = groups();
        selector.setParameters(selector.buildUponParameters()
                .setSelectionOverride(TrackSelectorManager.RENDERER_INDEX_AUDIO, groups, new SelectionOverride(0, 0)));
        TrackSelectorManager manager = new TrackSelectorManager(RuntimeEnvironment.application);
        manager.setTrackSelector(selector);

        assertTrue(selector.getParameters().hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_AUDIO, groups));

        manager.clearAudioOverride();

        assertFalse(selector.getParameters().hasSelectionOverride(TrackSelectorManager.RENDERER_INDEX_AUDIO, groups));
    }
}
