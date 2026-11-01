"""Pure logic of the upstream sync. No network, no git: python3 -m unittest discover -s scripts/sync"""
import unittest

import sync_logic as logic


def release(tag, draft=False, prerelease=False):
    return {"tag_name": tag, "draft": draft, "prerelease": prerelease}


class TagPicking(unittest.TestCase):
    def test_takes_the_highest_stable_tag(self):
        releases = [release("32.47s"), release("32.68s"), release("32.56s")]
        self.assertEqual(logic.pick_stable_tag(releases), "32.68s")

    def test_compares_numbers_not_strings(self):
        self.assertEqual(logic.pick_stable_tag([release("32.9s"), release("32.10s")]), "32.10s")
        self.assertEqual(logic.pick_stable_tag([release("9.6s"), release("10.1s")]), "10.1s")

    def test_ignores_prereleases_drafts_and_other_tags(self):
        releases = [
            release("33.01s", prerelease=True),
            release("33.02s", draft=True),
            release("32.68"),
            release("notification2"),
            release("latest"),
            release("32.56s"),
        ]
        self.assertEqual(logic.pick_stable_tag(releases), "32.56s")

    def test_rejects_hostile_tag_names(self):
        for tag in ('32.68s"; rm -rf /', "32.68s\n", "$(id)", "32.68s ", " 32.68s", "٣٢.٦٨s", "32.68S", "32.6.8s"):
            self.assertFalse(logic.is_stable_tag(tag), repr(tag))
            self.assertIsNone(logic.pick_stable_tag([release(tag)]), repr(tag))

    def test_rejects_entries_that_are_not_plain_releases(self):
        self.assertIsNone(logic.pick_stable_tag([{"tag_name": 32, "draft": False, "prerelease": False}]))
        self.assertIsNone(logic.pick_stable_tag([{"tag_name": "32.68s"}]))
        self.assertIsNone(logic.pick_stable_tag([]))

    def test_upstream_version_of_a_tag(self):
        self.assertEqual(logic.upstream_version("32.68s"), "32.68")


class VersionMath(unittest.TestCase):
    def test_material_version(self):
        version = logic.material_version(2458, "32.68", 1)
        self.assertEqual(version.code, 245801)
        self.assertEqual(version.name, "32.68-1")
        self.assertEqual(version.tag, "material-v32.68-1")

    def test_upgrades_over_the_existing_install(self):
        self.assertGreater(logic.material_version(2458, "32.68", 1).code, 2458)

    def test_patch_stays_in_the_two_digits_it_owns(self):
        self.assertEqual(logic.material_version(2458, "32.68", 99).code, 245899)
        for patch in (0, -1, 100):
            with self.assertRaises(ValueError):
                logic.material_version(2458, "32.68", patch)

    def test_new_upstream_version_resets_the_patch(self):
        self.assertEqual(logic.next_patch("32.68", "32.75", 7), 1)

    def test_same_upstream_version_needs_a_new_patch(self):
        self.assertEqual(logic.next_patch("32.68", "32.68", 1), 2)

    def test_reads_the_upstream_version_from_gradle(self):
        text = 'defaultConfig {\n        versionCode 2458\n        versionName "32.68"\n}\n' \
               'stmaterial {\n    versionCode android.defaultConfig.versionCode * 100 + materialPatch\n' \
               '    versionName "${android.defaultConfig.versionName}-${materialPatch}"\n}\n'
        self.assertEqual(logic.parse_gradle_version(text), (2458, "32.68"))

    def test_gradle_without_a_version_is_an_error(self):
        with self.assertRaises(ValueError):
            logic.parse_gradle_version("android {}\n")

    def test_patch_file(self):
        text = "# comment\nmaterialPatch=3\n"
        self.assertEqual(logic.parse_patch(text), 3)
        self.assertEqual(logic.with_patch(text, 1), "# comment\nmaterialPatch=1\n")
        with self.assertRaises(ValueError):
            logic.parse_patch("materialPatch=x\n")

    def test_release_tag_check(self):
        self.assertTrue(logic.tag_matches_version("material-v32.68-1", "32.68-1"))
        self.assertFalse(logic.tag_matches_version("material-v32.68", "32.68-1"))
        self.assertFalse(logic.tag_matches_version("material-v32.68-2", "32.68-1"))
        self.assertFalse(logic.tag_matches_version("material-v32.68-1\n", "32.68-1"))


class AuthPaths(unittest.TestCase):
    def test_real_sign_in_paths_of_the_tree(self):
        for path in (
            "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/auth/V2/AuthApi.java",
            "youtubeapi/src/main/java/com/liskovsoft/googlecommon/common/models/auth/AccessToken.java",
            "youtubeapi/src/main/java/com/liskovsoft/googlecommon/service/oauth/YouTubeAccount.java",
            "youtubeapi/src/main/java/com/liskovsoft/googleapi/oauth2/manager/OAuth2AccountManager.java",
            "youtubeapi/src/main/java/com/liskovsoft/googlecommon/common/helpers/RetrofitOkHttpHelper.kt",
            "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/YouTubeSignInService.java",
            "mediaserviceinterfaces/src/main/java/com/liskovsoft/mediaserviceinterfaces/SignInService.java",
            "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/SignInPresenter.java",
            "smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/signin/SignInFragment.java",
            "smarttubetv/src/main/res/layout/fragment_signin.xml",
            "res/layout/sign_in_view.xml",
        ):
            self.assertTrue(logic.is_auth_path(path), path)

    def test_lookalikes_do_not_stop_the_sync(self):
        for path in (
            "common/src/main/java/DesignInfo.java",
            "common/src/main/java/assigning/Task.java",
            "docs/author/README.md",
            "common/src/main/java/Video.java",
            "smarttubetv/build.gradle",
        ):
            self.assertFalse(logic.is_auth_path(path), path)


class Gitmodules(unittest.TestCase):
    TEXT = '[submodule "SharedModules"]\n\tpath = SharedModules\n\turl = https://github.com/yuliskov/SharedModules\n' \
           '[submodule "MediaServiceCore"]\n\tpath = MediaServiceCore\n\turl = https://github.com/yuliskov/MediaServiceCore\n'

    def test_parses_paths_and_urls(self):
        self.assertEqual(logic.parse_gitmodules(self.TEXT), {
            "SharedModules": ("SharedModules", "https://github.com/yuliskov/SharedModules"),
            "MediaServiceCore": ("MediaServiceCore", "https://github.com/yuliskov/MediaServiceCore"),
        })


class Repositories(unittest.TestCase):
    def test_extracts_blocks_and_ignores_the_layout(self):
        one = "buildscript {\n  repositories {\n    google()\n    maven { url 'https://a' }\n  }\n}\n"
        two = "buildscript {\n    repositories {\n        google()\n        maven {   url 'https://a'   }\n    }\n}\n// other\n"
        self.assertEqual(logic.repository_declarations(one), logic.repository_declarations(two))

    def test_sees_a_changed_url(self):
        one = "repositories { maven { url 'https://a' } }\n"
        two = "repositories { maven { url 'https://evil' } }\n"
        self.assertNotEqual(logic.repository_declarations(one), logic.repository_declarations(two))

    def test_sees_the_short_form_and_an_unbalanced_block(self):
        self.assertEqual(logic.repository_declarations("repositories.mavenCentral()\n"),
                         ["repositories.mavenCentral()"])
        self.assertTrue(logic.repository_declarations("repositories {\n  google()\n"))


def diff(label="", paths=(), texts=None, compare=None):
    return logic.RepoDiff(label, list(paths), texts or {}, compare)


class Gate(unittest.TestCase):
    def test_plain_change_passes(self):
        self.assertEqual(logic.gate_findings([diff(paths=["common/src/Video.java"])]), [])

    def test_auth_path_in_this_repository(self):
        findings = logic.gate_findings([diff(paths=["common/src/SignInPresenter.java"])])
        self.assertEqual(len(findings), 1)
        self.assertIn("common/src/SignInPresenter.java", findings[0])

    def test_auth_path_behind_a_submodule_pin_names_the_submodule(self):
        findings = logic.gate_findings([diff(), diff("MediaServiceCore", ["youtubeapi/x/auth/AuthApi.java"])])
        self.assertEqual(len(findings), 1)
        self.assertIn("MediaServiceCore/youtubeapi/x/auth/AuthApi.java", findings[0])

    def test_gitmodules_url_change(self):
        old = '[submodule "a"]\n\tpath = a\n\turl = https://x/a\n'
        new = '[submodule "a"]\n\tpath = a\n\turl = https://evil/a\n'
        self.assertEqual(len(logic.gate_findings([diff(paths=[".gitmodules"], texts={".gitmodules": (old, new)})])), 1)

    def test_gitmodules_added_submodule(self):
        old = '[submodule "a"]\n\tpath = a\n\turl = https://x/a\n'
        new = old + '[submodule "b"]\n\tpath = b\n\turl = https://x/b\n'
        self.assertEqual(len(logic.gate_findings([diff(paths=[".gitmodules"], texts={".gitmodules": (old, new)})])), 1)

    def test_gitmodules_change_that_keeps_urls_passes(self):
        old = '[submodule "a"]\n\tpath = a\n\turl = https://x/a\n'
        new = '[submodule "a"]\n\tpath = a\n\turl = https://x/a\n\tbranch = main\n'
        self.assertEqual(logic.gate_findings([diff(paths=[".gitmodules"], texts={".gitmodules": (old, new)})]), [])

    def test_gradle_repositories_change(self):
        old = "repositories { google() }\ndependencies { implementation 'a:b:1' }\n"
        new = "repositories { google(); maven { url 'https://evil' } }\ndependencies { implementation 'a:b:1' }\n"
        findings = logic.gate_findings([diff(paths=["build.gradle"], texts={"build.gradle": (old, new)})])
        self.assertEqual(len(findings), 1)
        self.assertIn("build.gradle", findings[0])

    def test_gradle_change_outside_repositories_passes(self):
        old = "repositories { google() }\nversionCode 1\n"
        new = "repositories { google() }\nversionCode 2\n"
        self.assertEqual(logic.gate_findings([diff(paths=["build.gradle"], texts={"build.gradle": (old, new)})]), [])

    def test_gradle_rules_cover_submodules_too(self):
        texts = {"build.gradle": ("repositories { google() }\n", "repositories { jcenter() }\n")}
        findings = logic.gate_findings([diff("MediaServiceCore", ["build.gradle"], texts)])
        self.assertIn("MediaServiceCore/build.gradle", findings[0])

    def test_is_gradle_file(self):
        for path in ("build.gradle", "smarttubetv/build.gradle", "settings.gradle", "a/b.gradle.kts"):
            self.assertTrue(logic.is_gradle_file(path), path)
        self.assertFalse(logic.is_gradle_file("gradle.properties"))


class Issues(unittest.TestCase):
    BOT = {"login": "github-actions[bot]"}

    def test_title(self):
        self.assertEqual(logic.issue_title("32.75s"), "[material-sync] upstream 32.75s")

    def test_finds_our_open_issue_for_the_tag(self):
        issues = [
            {"number": 1, "title": "[material-sync] upstream 32.68s", "user": self.BOT},
            {"number": 2, "title": "[material-sync] upstream 32.75s", "user": self.BOT},
        ]
        self.assertEqual(logic.find_open_issue(issues, "32.75s"), 2)

    def test_a_stranger_cannot_block_the_sync_with_a_title(self):
        issues = [{"number": 3, "title": "[material-sync] upstream 32.75s", "user": {"login": "someone"}}]
        self.assertIsNone(logic.find_open_issue(issues, "32.75s"))

    def test_pull_requests_are_not_issues(self):
        issues = [{"number": 4, "title": "[material-sync] upstream 32.75s", "user": self.BOT, "pull_request": {}}]
        self.assertIsNone(logic.find_open_issue(issues, "32.75s"))

    def test_lists_failed_jobs_only(self):
        results = {"decide": "success", "verify": "failure", "publish": "skipped", "release": "skipped"}
        self.assertEqual(logic.failed_jobs(results), ["verify"])
        self.assertEqual(logic.failed_jobs({"decide": "success", "verify": "skipped"}), [])

    def test_stop_body_names_files_and_links_the_compare_view(self):
        body = logic.stop_body("32.75s", ["changed: a/auth/b.java"], ["https://github.com/x/y/compare/a...b"])
        self.assertIn("a/auth/b.java", body)
        self.assertIn("https://github.com/x/y/compare/a...b", body)
        self.assertIn("32.75s", body)

    def test_failure_body_before_and_after_the_push(self):
        run = "https://github.com/x/y/actions/runs/1"
        before = logic.failure_body("32.75s", ["verify"], run, "", "")
        self.assertIn("Nothing was pushed", before)
        self.assertIn(run, before)
        after = logic.failure_body("32.75s", ["release"], run, "abc123", "material-v32.75-1")
        self.assertNotIn("Nothing was pushed", after)
        self.assertIn("abc123", after)
        self.assertIn("material-v32.75-1", after)

    def test_untrusted_text_cannot_leave_the_code_block(self):
        cleaned = logic.safe_text("a`b\n@owner $(id)/c.java")
        for character in "`\n@$()":
            self.assertNotIn(character, cleaned)
        self.assertTrue(cleaned.endswith("/c.java"))

    def test_findings_are_cleaned_in_the_body(self):
        findings = logic.gate_findings([diff(paths=["a`b/SignIn.java"])])
        self.assertNotIn("`", findings[0])


if __name__ == "__main__":
    unittest.main()
