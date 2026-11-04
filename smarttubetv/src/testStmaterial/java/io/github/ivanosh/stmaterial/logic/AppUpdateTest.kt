package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
private val ARM64 = listOf("arm64-v8a", "armeabi-v7a")

private fun asset(version: String, abi: String, sha: String? = "sha256:$SHA", size: Long = 1000) =
    ReleaseAsset("SmartTube_material_${version}_$abi.apk", size, sha)

private fun release(version: String, pre: Boolean, draft: Boolean = false, assets: List<ReleaseAsset>? = null, body: String = "") =
    Release("material-v$version", pre, draft, body, assets ?: listOf("arm64-v8a", "armeabi-v7a", "universal").map { asset(version, it) })

class VersionTest {
    @Test fun parsesName() {
        assertEquals(listOf(32, 68, 1), parseVersion("32.68-1"))
        assertEquals(listOf(32, 68, 12), parseVersion("32.68-12"))
    }

    @Test fun rejectsOtherShapes() {
        listOf("", "32.68", "v32.68-1", "32.68-1-x", "32.68-", "-1", "32..68-1", "a.b-1", "32.68-1 ", "99999999999.1-1").forEach {
            assertNull(it, parseVersion(it))
        }
    }

    @Test fun tagToVersion() {
        assertEquals("32.68-1", versionOfTag("material-v32.68-1"))
        assertNull(versionOfTag("v32.68-1"))
        assertNull(versionOfTag("material-v32.68"))
        assertNull(versionOfTag("material-v../x-1"))
    }

    @Test fun comparesNumerically() {
        assertTrue(compareVersions(parseVersion("32.68-10")!!, parseVersion("32.68-9")!!) > 0)
        assertTrue(compareVersions(parseVersion("32.69-1")!!, parseVersion("32.68-99")!!) > 0)
        assertTrue(compareVersions(parseVersion("33.1-1")!!, parseVersion("32.68-1")!!) > 0)
        assertEquals(0, compareVersions(parseVersion("32.68-1")!!, parseVersion("32.68-1")!!))
        assertTrue(compareVersions(parseVersion("32.68-1")!!, parseVersion("32.68-2")!!) < 0)
    }
}

class ChannelTest {
    @Test fun keys() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey(null))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromKey("junk"))
        assertEquals(UpdateChannel.OFF, UpdateChannel.fromKey("off"))
        assertEquals(UpdateChannel.PRERELEASE, UpdateChannel.fromKey("prerelease"))
    }

    @Test fun stableNeverTakesAPrerelease() {
        val feed = listOf(release("32.68-3", pre = true), release("32.68-2", pre = false), release("32.68-1", pre = false))
        val offer = pickUpdate(feed, UpdateChannel.STABLE, "32.67-1", ARM64)
        assertEquals("32.68-2", offer?.versionName)
        assertFalse(offer!!.prerelease)
    }

    @Test fun stableWithOnlyPrereleasesOffersNothing() {
        assertNull(pickUpdate(listOf(release("32.68-3", pre = true)), UpdateChannel.STABLE, "32.68-1", ARM64))
    }

    @Test fun prereleaseTakesTheNewestOfEither() {
        val feed = listOf(release("32.68-2", pre = false), release("32.68-3", pre = true), release("32.68-1", pre = false))
        assertEquals("32.68-3", pickUpdate(feed, UpdateChannel.PRERELEASE, "32.67-1", ARM64)?.versionName)
        val stableNewer = listOf(release("32.68-4", pre = false), release("32.68-3", pre = true))
        assertEquals("32.68-4", pickUpdate(stableNewer, UpdateChannel.PRERELEASE, "32.67-1", ARM64)?.versionName)
    }

    @Test fun orderInTheFeedDoesNotMatter() {
        val feed = listOf(release("32.68-9", pre = false), release("32.68-10", pre = false), release("32.68-2", pre = false))
        assertEquals("32.68-10", pickUpdate(feed, UpdateChannel.STABLE, "32.68-1", ARM64)?.versionName)
    }

    @Test fun offIsNeverAnOffer() {
        assertNull(pickUpdate(listOf(release("32.68-3", pre = false)), UpdateChannel.OFF, "32.68-1", ARM64))
    }

    @Test fun draftsAndBadTagsAreSkipped() {
        val feed = listOf(
            release("32.68-5", pre = false, draft = true),
            Release("latest", false, false, "", emptyList()),
            Release("material-v32.68-4/../x", false, false, "", emptyList()),
            release("32.68-2", pre = false),
        )
        assertEquals("32.68-2", pickUpdate(feed, UpdateChannel.STABLE, "32.68-1", ARM64)?.versionName)
    }

    @Test fun sameOrOlderIsNoUpdate() {
        val feed = listOf(release("32.68-2", pre = false))
        assertNull(pickUpdate(feed, UpdateChannel.STABLE, "32.68-2", ARM64))
        assertNull(pickUpdate(feed, UpdateChannel.STABLE, "32.68-3", ARM64))
        assertNull(pickUpdate(feed, UpdateChannel.STABLE, "33.0-1", ARM64))
    }

    @Test fun unreadableInstalledVersionOffersNothing() {
        assertNull(pickUpdate(listOf(release("32.68-2", pre = false)), UpdateChannel.STABLE, "weird", ARM64))
    }

    @Test fun withdrawnReleaseIsSimplyAbsent() {
        val feed = listOf(release("32.68-1", pre = false))
        assertNull(pickUpdate(feed, UpdateChannel.STABLE, "32.68-1", ARM64))
    }
}

class AssetPickTest {
    @Test fun takesTheDeviceAbi() {
        assertEquals("arm64-v8a", pickUpdate(listOf(release("32.68-2", false)), UpdateChannel.STABLE, "32.68-1", ARM64)?.abi)
        assertEquals("armeabi-v7a", pickUpdate(listOf(release("32.68-2", false)), UpdateChannel.STABLE, "32.68-1", listOf("armeabi-v7a"))?.abi)
    }

    @Test fun fallsBackToUniversal() {
        val only = release("32.68-2", false, assets = listOf(asset("32.68-2", "universal")))
        assertEquals("universal", pickUpdate(listOf(only), UpdateChannel.STABLE, "32.68-1", ARM64)?.abi)
    }

    @Test fun otherAbiIsNotUsed() {
        val x86 = release("32.68-2", false, assets = listOf(asset("32.68-2", "x86")))
        assertNull(pickUpdate(listOf(x86), UpdateChannel.STABLE, "32.68-1", listOf("x86")))
    }

    @Test fun assetOfAnotherVersionIsNotUsed() {
        val mixed = release("32.68-2", false, assets = listOf(asset("32.68-9", "arm64-v8a")))
        assertNull(pickUpdate(listOf(mixed), UpdateChannel.STABLE, "32.68-1", ARM64))
    }

    @Test fun needsADigest() {
        listOf(null, "", "sha256:", "sha256:abc", "md5:$SHA", SHA, "sha256:${SHA}zz", "sha256:${"g".repeat(64)}").forEach { bad ->
            val r = release("32.68-2", false, assets = listOf(asset("32.68-2", "arm64-v8a", sha = bad)))
            assertNull("digest $bad", pickUpdate(listOf(r), UpdateChannel.STABLE, "32.68-1", ARM64))
        }
    }

    @Test fun digestIsLowercasedHex() {
        val upper = release("32.68-2", false, assets = listOf(asset("32.68-2", "arm64-v8a", sha = "sha256:" + SHA.uppercase())))
        assertEquals(SHA, pickUpdate(listOf(upper), UpdateChannel.STABLE, "32.68-1", ARM64)?.asset?.sha256)
    }

    @Test fun sizeMustBeSane() {
        listOf(0L, -1L, MAX_APK_BYTES + 1).forEach { size ->
            val r = release("32.68-2", false, assets = listOf(asset("32.68-2", "arm64-v8a", size = size)))
            assertNull("size $size", pickUpdate(listOf(r), UpdateChannel.STABLE, "32.68-1", ARM64))
        }
    }

    @Test fun anOlderUsableReleaseIsOfferedWhenTheNewestHasNoDigest() {
        val broken = release("32.68-3", false, assets = listOf(asset("32.68-3", "arm64-v8a", sha = null)))
        assertEquals("32.68-2", pickUpdate(listOf(broken, release("32.68-2", false)), UpdateChannel.STABLE, "32.68-1", ARM64)?.versionName)
    }
}

class ScheduleTest {
    private val day = CHECK_INTERVAL_MS

    @Test fun offNeverChecks() = assertFalse(shouldCheck(UpdateChannel.OFF, 0, 10 * day))

    @Test fun firstRunChecks() = assertTrue(shouldCheck(UpdateChannel.STABLE, 0, 10 * day))

    @Test fun onceADay() {
        val last = 5 * day
        assertFalse(shouldCheck(UpdateChannel.STABLE, last, last + 1))
        assertFalse(shouldCheck(UpdateChannel.PRERELEASE, last, last + day - 1))
        assertTrue(shouldCheck(UpdateChannel.STABLE, last, last + day))
    }

    @Test fun clockMovedBackChecks() = assertTrue(shouldCheck(UpdateChannel.STABLE, 9 * day, 2 * day))
}

class UrlTest {
    @Test fun allowsOnlyTheGithubHostsOverHttps() {
        listOf(
            "https://github.com/ivanosh/SmartTube-Material/releases/download/material-v1.0-1/a.apk",
            "https://objects.githubusercontent.com/x?y=1",
            "https://release-assets.githubusercontent.com/x",
            "https://api.github.com/repos/ivanosh/SmartTube-Material/releases",
        ).forEach { assertTrue(it, isAllowedHop(it)) }
        listOf(
            "http://github.com/x", "https://github.com.evil.com/x", "https://evilgithub.com/x", "https://example.com/x",
            "https://github.com@evil.com/x", "https://user@github.com/x", "https://github.com:8443/x", "ftp://github.com/x",
            "https://githubusercontent.com/x", "https://evil.githubusercontent.com/x", "", "not a url", "//github.com/x",
            "https://github.com\\@evil.com/x",
        ).forEach { assertFalse(it, isAllowedHop(it)) }
    }

    @Test fun downloadUrlIsBuiltNotCopied() {
        assertEquals(
            "https://github.com/DekuCS/SmartTube-Glasio/releases/download/material-v32.68-2/SmartTube_material_32.68-2_arm64-v8a.apk",
            downloadUrl("material-v32.68-2", "SmartTube_material_32.68-2_arm64-v8a.apk"),
        )
        assertNull(downloadUrl("material-v32.68-2/../../x", "SmartTube_material_32.68-2_arm64-v8a.apk"))
        assertNull(downloadUrl("material-v32.68-2", "../evil.apk"))
        assertNull(downloadUrl("material-v32.68-2", "SmartTube_material_32.68-3_arm64-v8a.apk"))
        assertNull(downloadUrl("latest", "SmartTube_material_32.68-2_arm64-v8a.apk"))
    }
}

class FeedParseTest {
    private val feed = """
        [
          {"tag_name":"material-v32.68-2","draft":false,"prerelease":true,"body":"## What's changed\r\n* Fixed a thing\r\n\r\nVersion 32.68-2.",
           "html_url":"https://evil.example/x",
           "assets":[
             {"name":"SmartTube_material_32.68-2_arm64-v8a.apk","size":1234,"digest":"sha256:$SHA","browser_download_url":"https://evil.example/a.apk"},
             {"name":"SmartTube_material_32.68-2_universal.apk","size":99}
           ]},
          {"tag_name":"material-v32.68-1","draft":false,"prerelease":false,"body":null,"assets":[]},
          {"draft":false},
          "junk",
          {"tag_name":"material-v32.67-1","prerelease":false,"assets":"no"}
        ]
    """.trimIndent()

    @Test fun parsesFixture() {
        val releases = parseReleases(feed)
        assertEquals(listOf("material-v32.68-2", "material-v32.68-1", "material-v32.67-1"), releases.map { it.tag })
        assertTrue(releases[0].prerelease)
        assertFalse(releases[1].prerelease)
        assertEquals(2, releases[0].assets.size)
        assertEquals(ReleaseAsset("SmartTube_material_32.68-2_arm64-v8a.apk", 1234, "sha256:$SHA"), releases[0].assets[0])
        assertNull(releases[0].assets[1].sha256)
        assertEquals("", releases[1].body)
        assertTrue(releases[2].assets.isEmpty())
    }

    @Test fun missingPrereleaseFlagIsAPrerelease() {
        val r = parseReleases("""[{"tag_name":"material-v1.0-1","assets":[]}]""").single()
        assertTrue(r.prerelease)
    }

    @Test fun endToEndFromFixture() {
        val offer = pickUpdate(parseReleases(feed), UpdateChannel.PRERELEASE, "32.68-1", ARM64)!!
        assertEquals("32.68-2", offer.versionName)
        assertEquals("arm64-v8a", offer.abi)
        assertEquals(SHA, offer.asset.sha256)
        assertNull(pickUpdate(parseReleases(feed), UpdateChannel.STABLE, "32.68-1", ARM64))
    }

    @Test(expected = Exception::class) fun notAnArrayFails() {
        parseReleases("""{"message":"API rate limit exceeded"}""")
    }

    @Test(expected = Exception::class) fun garbageFails() {
        parseReleases("<html>")
    }

    @Test fun notes() {
        assertEquals(listOf("What's changed", "Fixed a thing", "Version 32.68-2."), releaseNotes("## What's changed\r\n* Fixed a thing\r\n\r\nVersion 32.68-2."))
        assertEquals(30, releaseNotes((1..100).joinToString("\n") { "- line $it" }).size)
        assertEquals(200, releaseNotes("x".repeat(500)).single().length)
        assertTrue(releaseNotes("").isEmpty())
    }
}

class VerifyTest {
    private val offer = pickUpdate(listOf(release("32.68-2", false)), UpdateChannel.STABLE, "32.68-1", ARM64)!!
    private val good = ArchiveFacts("io.github.ivanosh.smarttubematerial", 326802, "32.68-2", listOf(PINNED_CERT_SHA256))

    private fun check(
        sha: String = SHA, size: Long = 1000, archive: ArchiveFacts? = good, pkg: String = "io.github.ivanosh.smarttubematerial", code: Long = 326801,
    ) = checkDownloaded(offer, sha, size, archive, pkg, code)

    @Test fun passes() = assertNull(check())
    @Test fun digestCaseIsIgnored() = assertNull(check(sha = SHA.uppercase()))
    @Test fun wrongDigest() = assertEquals(Refusal.DIGEST, check(sha = "f".repeat(64)))
    @Test fun wrongSize() = assertEquals(Refusal.SIZE, check(size = 999))
    @Test fun unreadable() = assertEquals(Refusal.UNREADABLE, check(archive = null))
    @Test fun otherPackage() = assertEquals(Refusal.PACKAGE, check(archive = good.copy(packageName = "app.smarttube")))
    @Test fun otherVersionName() = assertEquals(Refusal.VERSION, check(archive = good.copy(versionName = "32.68-9")))
    @Test fun nullVersionName() = assertEquals(Refusal.VERSION, check(archive = good.copy(versionName = null)))
    @Test fun sameVersionCodeIsADowngrade() = assertEquals(Refusal.DOWNGRADE, check(archive = good.copy(versionCode = 326801)))
    @Test fun olderVersionCodeIsADowngrade() = assertEquals(Refusal.DOWNGRADE, check(archive = good.copy(versionCode = 100), code = 326801))
    @Test fun otherSigner() = assertEquals(Refusal.SIGNER, check(archive = good.copy(signerSha256 = listOf("a".repeat(64)))))
    @Test fun noSigner() = assertEquals(Refusal.SIGNER, check(archive = good.copy(signerSha256 = emptyList())))
    @Test fun extraSignerIsRefused() = assertEquals(Refusal.SIGNER, check(archive = good.copy(signerSha256 = listOf(PINNED_CERT_SHA256, "a".repeat(64)))))
    @Test fun signerCaseIsIgnored() = assertNull(check(archive = good.copy(signerSha256 = listOf(PINNED_CERT_SHA256.uppercase()))))
    @Test fun pinnedCertIsTheDocumentedOne() =
        assertEquals("306a3b18e9463534cfa451d83ebae6ac63cbd136d6d13baa65fe659d8e8e051a", PINNED_CERT_SHA256)
}

class StaleOfferTest {
    private fun offer(pre: Boolean) = pickUpdate(listOf(release("32.68-2", pre)), UpdateChannel.PRERELEASE, "32.68-1", ARM64)!!

    @Test fun offIsNeverAllowed() {
        assertFalse(offerAllowed(offer(false), UpdateChannel.OFF))
        assertFalse(offerAllowed(offer(true), UpdateChannel.OFF))
    }

    @Test fun stableNeverShowsAPrerelease() {
        assertFalse(offerAllowed(offer(true), UpdateChannel.STABLE))
        assertTrue(offerAllowed(offer(false), UpdateChannel.STABLE))
    }

    @Test fun prereleaseTakesBoth() {
        assertTrue(offerAllowed(offer(true), UpdateChannel.PRERELEASE))
        assertTrue(offerAllowed(offer(false), UpdateChannel.PRERELEASE))
    }
}

class FailClosedFlagsTest {
    private fun feed(flags: String) =
        """[{"tag_name":"material-v32.68-2",$flags "assets":[{"name":"SmartTube_material_32.68-2_arm64-v8a.apk","size":10,"digest":"sha256:$SHA"}]}]"""

    private fun stable(flags: String) = pickUpdate(parseReleases(feed(flags)), UpdateChannel.STABLE, "32.68-1", ARM64)
    private fun any(flags: String) = pickUpdate(parseReleases(feed(flags)), UpdateChannel.PRERELEASE, "32.68-1", ARM64)

    @Test fun exactBooleansAreOffered() = assertEquals("32.68-2", stable(""""draft":false,"prerelease":false,""")?.versionName)

    @Test fun missingDraftIsADraft() {
        assertNull(stable(""""prerelease":false,"""))
        assertNull(any(""""prerelease":false,"""))
    }

    @Test fun nonBooleanDraftIsADraft() {
        listOf("\"false\"", "0", "null", "\"\"").forEach {
            assertNull(it, any(""""draft":$it,"prerelease":false,"""))
        }
    }

    @Test fun draftTrueIsADraft() = assertNull(any(""""draft":true,"prerelease":false,"""))

    @Test fun nonBooleanPrereleaseIsNotStable() {
        listOf("\"false\"", "0", "null", "\"\"").forEach {
            assertNull(it, stable(""""draft":false,"prerelease":$it,"""))
        }
    }

    @Test fun missingPrereleaseIsNotStable() = assertNull(stable(""""draft":false,"""))
}

class WorkEndTest {
    @Test fun onlyACommitWaitsForTheSystem() {
        for (cancelled in listOf(false, true)) for (refusal in listOf(null, Refusal.SIGNER)) for (failed in listOf(false, true)) {
            assertTrue(workEnd(true, cancelled, refusal, failed) == UpdateEnd.WAIT_FOR_SYSTEM)
            assertFalse("$cancelled $refusal $failed", workEnd(false, cancelled, refusal, failed) == UpdateEnd.WAIT_FOR_SYSTEM)
        }
    }

    @Test fun cancelAtAnyPointIsCancelled() {
        // Back during verification: the file passed (no refusal), nothing was committed, cancelled is set.
        assertEquals(UpdateEnd.CANCELLED, workEnd(false, true, null, false))
        assertEquals(UpdateEnd.CANCELLED, workEnd(false, true, Refusal.DIGEST, false))
        assertEquals(UpdateEnd.CANCELLED, workEnd(false, true, null, true))
    }

    @Test fun otherEnds() {
        assertEquals(UpdateEnd.REFUSED, workEnd(false, false, Refusal.SIGNER, false))
        assertEquals(UpdateEnd.DOWNLOAD_FAILED, workEnd(false, false, null, true))
        assertEquals(UpdateEnd.INSTALL_FAILED, workEnd(false, false, null, false))
    }
}
