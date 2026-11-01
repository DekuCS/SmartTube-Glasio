package io.github.ivanosh.stmaterial.player

import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import com.liskovsoft.smartyoutubetv2.tv.R

/** English names for the legend: the UI is English, and the upstream names follow the device language. */
val SPONSOR_CATEGORY_NAMES: Map<String, Int> = mapOf(
    SponsorSegment.CATEGORY_SPONSOR to R.string.material_sb_sponsor,
    SponsorSegment.CATEGORY_INTRO to R.string.material_sb_intro,
    SponsorSegment.CATEGORY_OUTRO to R.string.material_sb_outro,
    SponsorSegment.CATEGORY_INTERACTION to R.string.material_sb_interaction,
    SponsorSegment.CATEGORY_SELF_PROMO to R.string.material_sb_selfpromo,
    SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC to R.string.material_sb_music_offtopic,
    SponsorSegment.CATEGORY_PREVIEW_RECAP to R.string.material_sb_preview,
    SponsorSegment.CATEGORY_POI_HIGHLIGHT to R.string.material_sb_poi_highlight,
    SponsorSegment.CATEGORY_FILLER to R.string.material_sb_filler,
)
