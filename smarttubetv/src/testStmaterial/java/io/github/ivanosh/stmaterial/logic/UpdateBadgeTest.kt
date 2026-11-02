package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

private fun offer(version: String = "32.68-5", prerelease: Boolean = false) = UpdateOffer(
    tag = "material-v$version", versionName = version, prerelease = prerelease, notes = listOf("One", "Two"),
    asset = ReleaseAsset("SmartTube_material_${version}_arm64-v8a.apk", 1000, SHA), abi = "arm64-v8a",
)

class UpdateBadgeTest {
    @Test fun showsNewerOfferOnItsChannel() {
        assertNotNull(badgeOffer(offer(), UpdateChannel.STABLE, "32.68-4"))
        assertNotNull(badgeOffer(offer(prerelease = true), UpdateChannel.PRERELEASE, "32.68-4"))
    }

    @Test fun clearedWhenInstalled() {
        assertNull(badgeOffer(offer("32.68-5"), UpdateChannel.STABLE, "32.68-6"))
    }

    @Test fun clearedWhenVersionEqual() {
        assertNull(badgeOffer(offer("32.68-5"), UpdateChannel.STABLE, "32.68-5"))
    }

    @Test fun clearedWhenChannelOff() {
        assertNull(badgeOffer(offer(), UpdateChannel.OFF, "32.68-4"))
    }

    @Test fun stableHidesPrereleaseOffer() {
        assertNull(badgeOffer(offer(prerelease = true), UpdateChannel.STABLE, "32.68-4"))
    }

    @Test fun noOfferNoBadge() {
        assertNull(badgeOffer(null, UpdateChannel.STABLE, "32.68-4"))
    }

    @Test fun unreadableInstalledVersionHidesBadge() {
        assertNull(badgeOffer(offer(), UpdateChannel.STABLE, "dev"))
    }

    @Test fun checkFindingNothingClears() {
        assertNull(storedOfferAfterCheck(offer(), found = null, checkSucceeded = true))
    }

    @Test fun failedCheckKeepsStored() {
        val stored = offer()
        assertEquals(stored, storedOfferAfterCheck(stored, found = null, checkSucceeded = false))
    }

    @Test fun checkFindingOfferReplacesStored() {
        val newer = offer("32.68-6")
        assertEquals(newer, storedOfferAfterCheck(offer(), found = newer, checkSucceeded = true))
    }

    @Test fun offerRoundTripsThroughJson() {
        val o = offer(prerelease = true)
        assertEquals(o, offerFromJson(offerToJson(o)))
    }

    @Test fun storedOfferThatIsTamperedIsRefused() {
        val good = offerToJson(offer())
        assertNull(offerFromJson("not json"))
        assertNull(offerFromJson(good.replace(SHA, "zz")))
        assertNull(offerFromJson(good.replace("material-v32.68-5", "material-v../x-5")))
        assertNull(offerFromJson(good.replace("\"versionName\":\"32.68-5\"", "\"versionName\":\"32.68-9\"")))
        assertNull(offerFromJson(good.replace("SmartTube_material_32.68-5_arm64-v8a.apk", "evil.apk")))
        assertNull(offerFromJson(good.replace("\"size\":1000", "\"size\":0")))
    }
}
