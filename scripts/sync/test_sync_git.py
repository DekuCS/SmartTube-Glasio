"""The git half of the upstream sync, on small repositories in a temp directory. No network."""
import argparse
import contextlib
import io
import json
import os
import pathlib
import subprocess
import tempfile
import unittest
from unittest import mock

import sync

GRADLE = 'android {\n    defaultConfig {\n        versionCode %d\n        versionName "%s"\n    }\n}\n'
SUBMODULES = '[submodule "MediaServiceCore"]\n\tpath = MediaServiceCore\n\turl = %s\n'


def run(cwd, *args):
    return subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def write(root, path, text):
    target = pathlib.Path(root, path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)


def identity(root):
    run(root, "git", "config", "user.name", "t")
    run(root, "git", "config", "user.email", "t@example.invalid")
    run(root, "git", "config", "commit.gpgsign", "false")


def commit(root, message):
    run(root, "git", "add", "-A")
    run(root, "git", "commit", "-q", "-m", message)
    return run(root, "git", "rev-parse", "HEAD")


def set_pin(root, sha):
    run(root, "git", "update-index", "--add", "--cacheinfo", "160000,%s,MediaServiceCore" % sha)


class Fixture(unittest.TestCase):
    """An upstream with a submodule, and a `material` branch forked from it in a clone."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        base = pathlib.Path(self.tmp.name).resolve()
        self.core = str(base / "core")
        self.upstream = str(base / "upstream")
        self.clone = str(base / "clone")
        self.out = str(base / "out")

        os.mkdir(self.core)
        run(self.core, "git", "init", "-q", "-b", "main")
        identity(self.core)
        run(self.core, "git", "config", "uploadpack.allowAnySHA1InWant", "true")
        write(self.core, "lib/Video.java", "class Video {}\n")
        write(self.core, "lib/auth/AuthApi.java", "class AuthApi {}\n")
        self.pin_old = commit(self.core, "core one")

        os.mkdir(self.upstream)
        run(self.upstream, "git", "init", "-q", "-b", "master")
        identity(self.upstream)
        os.mkdir(pathlib.Path(self.upstream, "MediaServiceCore"))  # unpopulated, as in a checkout without submodules
        write(self.upstream, ".gitmodules", SUBMODULES % ("file://" + self.core))
        write(self.upstream, "smarttubetv/build.gradle", GRADLE % (2458, "32.68"))
        write(self.upstream, "build.gradle", "repositories { google() }\n")
        write(self.upstream, "app/Main.java", "class Main {}\n")
        write(self.upstream, ".github/workflows/CI.yml", "name: CI\n")
        run(self.upstream, "git", "add", "-A")
        set_pin(self.upstream, self.pin_old)
        commit(self.upstream, "upstream one")

        run(base, "git", "clone", "-q", "-b", "master", self.upstream, self.clone)
        identity(self.clone)
        run(self.clone, "git", "checkout", "-q", "-b", "material")
        run(self.clone, "git", "rm", "-q", "-r", ".github/workflows")
        write(self.clone, ".github/workflows/material.yml", "name: Material\n")
        write(self.clone, "material-version.properties", "materialPatch=1\n")
        write(self.clone, "smarttubetv/src/stmaterial/Ui.kt", "class Ui\n")
        self.base = commit(self.clone, "material work")

    def upstream_commit(self, message="upstream two"):
        return commit(self.upstream, message)

    def fetch_upstream(self):
        run(self.clone, "git", "fetch", "-q", "--no-tags", self.upstream, "+refs/heads/master:refs/remotes/upstream/master")
        return "refs/remotes/upstream/master"

    def decide(self, tag="32.75s"):
        return sync.Sync(self.clone, self.out).plan_ref(self.fetch_upstream(), tag)

    def tree_text(self, ref, path):
        return run(self.clone, "git", "show", "%s:%s" % (ref, path))


class Decide(Fixture):
    def bump(self, code=2465, name="32.75"):
        write(self.upstream, "smarttubetv/build.gradle", GRADLE % (code, name))

    def test_merges_with_two_parents_and_resets_the_patch(self):
        write(self.clone, "material-version.properties", "materialPatch=4\n")
        self.base = commit(self.clone, "patch 4")
        self.bump()
        write(self.upstream, "app/Main.java", "class Main { int x; }\n")
        self.upstream_commit()

        plan = self.decide()

        self.assertEqual(plan["action"], "proceed", plan)
        self.assertEqual(plan["version_name"], "32.75-1")
        self.assertEqual(plan["version_code"], "246501")
        self.assertEqual(plan["release_tag"], "material-v32.75-1")
        self.assertEqual(plan["base_sha"], self.base)
        parents = run(self.clone, "git", "show", "-s", "--format=%P", plan["merge_sha"]).split()
        self.assertEqual(parents[0], self.base)
        self.assertEqual(len(parents), 2)
        self.assertEqual(self.tree_text(plan["merge_sha"], "material-version.properties"), "materialPatch=1")
        self.assertIn("int x", self.tree_text(plan["merge_sha"], "app/Main.java"))

    def test_nothing_moves_on_the_branch_itself(self):
        self.bump()
        self.upstream_commit()
        before = run(self.clone, "git", "rev-parse", "refs/heads/material")
        self.assertEqual(self.decide()["action"], "proceed")
        self.assertEqual(run(self.clone, "git", "rev-parse", "refs/heads/material"), before)

    def test_already_merged_tag_is_a_no_op(self):
        self.bump()
        self.upstream_commit()
        ref = self.fetch_upstream()
        run(self.clone, "git", "merge", "-q", "--no-edit", ref)
        self.assertEqual(self.decide()["action"], "noop")

    def test_same_upstream_version_takes_a_new_patch(self):
        write(self.upstream, "app/Main.java", "class Main { int x; }\n")
        self.upstream_commit()
        plan = self.decide("32.68s")
        self.assertEqual(plan["action"], "proceed", plan)
        self.assertEqual(plan["release_tag"], "material-v32.68-2")

    def test_upstream_workflow_change_stops(self):
        self.bump()
        write(self.upstream, ".github/workflows/CI.yml", "name: CI changed\n")
        self.upstream_commit()
        self.assertEqual(self.decide()["reason"], "gate")

    def test_upstream_file_next_to_the_sync_scripts_stops(self):
        self.bump()
        write(self.upstream, "scripts/sync/json.py", "raise SystemExit('planted')\n")
        self.upstream_commit()
        plan = self.decide()
        self.assertEqual(plan["reason"], "gate")
        self.assertIn("scripts/sync/json.py", pathlib.Path(self.out, "issue-body.md").read_text())

    def test_merge_conflict_stops_and_leaves_a_clean_tree(self):
        self.bump()
        write(self.upstream, "app/Main.java", "class Main { upstream }\n")
        self.upstream_commit()
        write(self.clone, "app/Main.java", "class Main { material }\n")
        head = commit(self.clone, "our change")

        plan = self.decide()

        self.assertEqual(plan["action"], "stop")
        self.assertEqual(plan["reason"], "merge-conflict")
        self.assertEqual(run(self.clone, "git", "status", "--porcelain"), "")
        self.assertEqual(run(self.clone, "git", "rev-parse", "HEAD"), head)
        self.assertIn("app/Main.java", pathlib.Path(self.out, "issue-body.md").read_text())

    def test_gate_stops_before_merging_on_an_auth_path(self):
        self.bump()
        write(self.upstream, "app/SignInPresenter.java", "class S {}\n")
        self.upstream_commit()

        plan = self.decide()

        self.assertEqual(plan["action"], "stop")
        self.assertEqual(plan["reason"], "gate")
        self.assertNotIn("merge_sha", plan)
        body = pathlib.Path(self.out, "issue-body.md").read_text()
        self.assertIn("app/SignInPresenter.java", body)
        self.assertIn("https://github.com/yuliskov/SmartTube/compare/", body)

    def test_pin_change_touching_auth_stops(self):
        self.bump()
        write(self.core, "lib/auth/AuthApi.java", "class AuthApi { int token; }\n")
        pin_new = commit(self.core, "core auth")
        run(self.upstream, "git", "add", "-A")
        set_pin(self.upstream, pin_new)
        self.upstream_commit()

        plan = self.decide()

        self.assertEqual(plan["action"], "stop")
        body = pathlib.Path(self.out, "issue-body.md").read_text()
        self.assertIn("MediaServiceCore/lib/auth/AuthApi.java", body)

    def test_pin_change_elsewhere_passes_and_takes_the_upstream_pin(self):
        self.bump()
        write(self.core, "lib/Video.java", "class Video { int x; }\n")
        pin_new = commit(self.core, "core video")
        run(self.upstream, "git", "add", "-A")
        set_pin(self.upstream, pin_new)
        self.upstream_commit()

        plan = self.decide()

        self.assertEqual(plan["action"], "proceed", plan)
        self.assertEqual(run(self.clone, "git", "rev-parse", plan["merge_sha"] + ":MediaServiceCore"), pin_new)

    def test_conflicting_pin_takes_the_upstream_pin(self):
        self.bump()
        write(self.core, "lib/Video.java", "class Video { int x; }\n")
        ours = commit(self.core, "ours")
        write(self.core, "lib/Video.java", "class Video { int y; }\n")
        theirs = commit(self.core, "theirs")
        run(self.upstream, "git", "add", "-A")
        set_pin(self.upstream, theirs)
        self.upstream_commit()
        set_pin(self.clone, ours)
        run(self.clone, "git", "commit", "-q", "-m", "our pin")

        plan = self.decide()

        self.assertEqual(plan["action"], "proceed", plan)
        self.assertEqual(run(self.clone, "git", "rev-parse", plan["merge_sha"] + ":MediaServiceCore"), theirs)

    def test_unreachable_pin_fails_closed(self):
        self.bump()
        run(self.upstream, "git", "add", "-A")
        set_pin(self.upstream, "1234567890123456789012345678901234567890")
        self.upstream_commit()

        plan = self.decide()

        self.assertEqual(plan["action"], "stop")
        self.assertIn("MediaServiceCore", pathlib.Path(self.out, "issue-body.md").read_text())

    def test_gitmodules_url_change_stops(self):
        self.bump()
        write(self.upstream, ".gitmodules", SUBMODULES % "https://example.invalid/evil")
        self.upstream_commit()
        self.assertEqual(self.decide()["action"], "stop")

    def test_gitmodules_url_added_in_upper_case_stops(self):
        self.bump()
        text = SUBMODULES % ("file://" + self.core) + "\tURL = https://example.invalid/evil\n"
        write(self.upstream, ".gitmodules", text)
        self.upstream_commit()
        self.assertEqual(self.decide()["reason"], "gate")

    def test_gitmodules_url_on_the_header_line_stops(self):
        self.bump()
        text = SUBMODULES % ("file://" + self.core) + '[submodule "MediaServiceCore"] url = https://example.invalid/evil\n'
        write(self.upstream, ".gitmodules", text)
        self.upstream_commit()
        self.assertEqual(self.decide()["reason"], "gate")

    def test_gitmodules_that_git_cannot_read_stops(self):
        self.bump()
        write(self.upstream, ".gitmodules", SUBMODULES % ("file://" + self.core) + "[broken\n")
        self.upstream_commit()
        self.assertEqual(self.decide()["reason"], "gate")

    def test_gitmodules_change_that_keeps_urls_passes(self):
        self.bump()
        write(self.upstream, ".gitmodules", SUBMODULES % ("file://" + self.core) + "\tbranch = main\n")
        self.upstream_commit()
        self.assertEqual(self.decide()["action"], "proceed")

    def test_gitmodules_url_without_a_value_stops_cleanly(self):
        self.bump()
        write(self.upstream, ".gitmodules", SUBMODULES % ("file://" + self.core) + "\turl\n")
        self.upstream_commit()
        plan = self.decide()
        self.assertEqual(plan["reason"], "gate")
        self.assertIn(".gitmodules", pathlib.Path(self.out, "issue-body.md").read_text())

    def test_version_lower_than_the_newest_tag_stops(self):
        self.bump()
        self.upstream_commit()
        run(self.clone, "git", "tag", "material-v33.1-1")
        plan = self.decide()
        self.assertEqual((plan["action"], plan["reason"]), ("stop", "version"))

    def test_bare_tag_of_that_version_stops(self):
        self.bump()
        self.upstream_commit()
        run(self.clone, "git", "tag", "material-v32.75-1")
        self.assertEqual(self.decide()["reason"], "version")

    def test_token_term_in_a_changed_line_stops(self):
        self.bump()
        write(self.upstream, "app/Main.java", "class Main { void f() { store.persistRefreshToken(t); } }\n")
        self.upstream_commit()
        plan = self.decide()
        self.assertEqual(plan["reason"], "gate")
        self.assertIn("app/Main.java", pathlib.Path(self.out, "issue-body.md").read_text())

    def test_token_term_behind_a_binary_attribute_still_stops(self):
        self.bump()
        write(self.upstream, ".gitattributes", "*.java -diff\n")
        write(self.upstream, "app/Main.java", "class Main { String refresh_token; }\n")
        self.upstream_commit()
        self.assertEqual(self.decide()["reason"], "gate")

    def test_token_term_behind_a_pin_stops(self):
        self.bump()
        write(self.core, "lib/Video.java", "class Video { String accessToken; }\n")
        pin_new = commit(self.core, "core token")
        run(self.upstream, "git", "add", "-A")
        set_pin(self.upstream, pin_new)
        self.upstream_commit()
        self.assertEqual(self.decide()["reason"], "gate")

    def test_wrapper_change_stops(self):
        self.bump()
        write(self.upstream, "gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https://evil\n")
        self.upstream_commit()
        self.assertEqual(self.decide()["reason"], "gate")

    def test_url_in_a_gradle_variable_stops(self):
        self.bump()
        write(self.upstream, "smarttubetv/extra.gradle", "ext.mirror = 'https://evil.example'\n")
        self.upstream_commit()
        self.assertEqual(self.decide()["reason"], "gate")

    def test_existing_release_tag_stops_before_merging(self):
        self.bump()
        self.upstream_commit()
        plan = sync.Sync(self.clone, self.out, published={"material-v32.75-1"}).plan_ref(self.fetch_upstream(), "32.75s")
        self.assertEqual(plan["action"], "stop")
        self.assertEqual(plan["reason"], "version")

    def test_repositories_change_stops(self):
        self.bump()
        write(self.upstream, "build.gradle", "repositories { maven { url 'https://evil' } }\n")
        self.upstream_commit()
        self.assertEqual(self.decide()["action"], "stop")

    def test_tag_that_disagrees_with_the_build_file_stops(self):
        self.bump(2465, "32.74")
        self.upstream_commit()
        plan = self.decide("32.75s")
        self.assertEqual(plan["action"], "stop")
        self.assertEqual(plan["reason"], "version")

    def test_version_that_does_not_grow_stops(self):
        self.bump(2400, "32.75")
        self.upstream_commit()
        plan = self.decide("32.75s")
        self.assertEqual(plan["action"], "stop")
        self.assertEqual(plan["reason"], "version")

    def test_unrelated_history_stops(self):
        run(self.upstream, "git", "checkout", "-q", "--orphan", "other")
        self.bump()
        write(self.upstream, ".gitmodules", SUBMODULES % ("file://" + self.core))
        run(self.upstream, "git", "add", "-A")
        set_pin(self.upstream, self.pin_old)
        commit(self.upstream, "unrelated")
        run(self.upstream, "git", "branch", "-q", "-f", "master", "other")
        run(self.upstream, "git", "checkout", "-q", "master")
        self.assertEqual(self.decide()["action"], "stop")


class PendingRelease(Fixture):
    """A merge on material whose release is missing is released once, and only after the same checks as a new one."""

    BOT = ("-c", "user.name=" + "github-actions[bot]", "-c", "user.email=" + sync.BOT_EMAIL)

    def tagged_upstream(self):
        write(self.upstream, "smarttubetv/build.gradle", GRADLE % (2465, "32.75"))
        self.upstream_commit()
        run(self.upstream, "git", "tag", "32.75s")

    def merged(self):
        self.tagged_upstream()
        plan = self.decide()
        run(self.clone, "git", "checkout", "-q", "material")
        run(self.clone, "git", "merge", "-q", "--ff-only", plan["merge_sha"])
        return plan

    def hand_merge(self, extra=None, ref=None):
        """A bot-authored merge made outside the sync, with the sync's subject."""
        ref = ref or self.fetch_upstream()
        run(self.clone, "git", "merge", "-q", "--no-ff", "--no-commit", ref)
        write(self.clone, "material-version.properties", "materialPatch=1\n")
        if extra:
            write(self.clone, extra, "planted\n")
        run(self.clone, "git", "add", "-A")
        run(self.clone, "git", *self.BOT, "commit", "-q", "-m", "Merge upstream 32.75s")
        return run(self.clone, "git", "rev-parse", "HEAD")

    def next_run(self, issues=(), published=()):
        rows = [{"tag_name": "32.75s", "draft": False, "prerelease": False}]
        return sync.Sync(self.clone, self.out, published=published).plan(rows, list(issues), upstream_url=self.upstream)

    def test_unreleased_merge_is_released_on_the_next_run(self):
        merged = self.merged()
        plan = self.next_run()
        self.assertEqual(plan["action"], "release-only")
        self.assertEqual(plan["merge_sha"], merged["merge_sha"])
        self.assertEqual(plan["release_tag"], "material-v32.75-1")
        self.assertEqual(plan["tag"], "32.75s")

    def test_the_check_leaves_the_checkout_where_it_was(self):
        self.merged()
        head = run(self.clone, "git", "rev-parse", "HEAD")
        plan = self.next_run()
        self.assertEqual(plan["base_sha"], head)
        self.assertEqual(run(self.clone, "git", "rev-parse", "HEAD"), head)
        self.assertEqual(run(self.clone, "git", "status", "--porcelain"), "")

    def test_published_release_is_a_no_op(self):
        self.merged()
        plan = self.next_run(published=["material-v32.75-1"])
        self.assertEqual((plan["action"], plan["reason"]), ("noop", "already-merged"))

    def test_deleted_release_with_its_tag_left_is_not_published_again(self):
        self.merged()
        run(self.clone, "git", "tag", "material-v32.75-1")
        plan = self.next_run()
        self.assertEqual((plan["action"], plan["reason"]), ("noop", "already-merged"))

    def test_newer_version_by_hand_keeps_the_older_merge_unpublished(self):
        self.merged()
        run(self.clone, "git", "tag", "material-v32.75-2")
        plan = self.next_run()
        self.assertEqual((plan["action"], plan["reason"]), ("noop", "already-merged"))

    def test_open_issue_holds_the_retry(self):
        self.merged()
        issues = [{"number": 9, "title": "[material-sync] upstream 32.75s", "user": {"login": "github-actions[bot]"}}]
        plan = self.next_run(issues=issues)
        self.assertEqual((plan["action"], plan["reason"]), ("noop", "open-issue"))

    def test_our_own_commits_after_the_merge_do_not_hide_it(self):
        merged = self.merged()
        write(self.clone, "smarttubetv/src/stmaterial/Ui.kt", "class Ui2\n")
        commit(self.clone, "a later commit of ours")
        self.assertEqual(self.next_run()["merge_sha"], merged["merge_sha"])

    def test_a_single_parent_commit_with_the_bot_and_the_subject_does_not_count(self):
        run(self.clone, "git", *self.BOT, "commit", "-q", "--allow-empty", "-m", "Merge upstream 32.75s")
        self.assertIsNone(sync.Sync(self.clone, self.out).unreleased_merge())

    def test_a_merge_of_something_other_than_the_tag_stops(self):
        self.tagged_upstream()
        run(self.clone, "git", "branch", "side", self.base)
        run(self.clone, "git", "checkout", "-q", "side")
        write(self.clone, "app/Side.java", "class Side {}\n")
        write(self.clone, "smarttubetv/build.gradle", GRADLE % (2465, "32.75"))
        commit(self.clone, "not upstream")
        run(self.clone, "git", "checkout", "-q", "material")
        self.hand_merge(ref="side")
        plan = self.next_run()
        self.assertEqual((plan["action"], plan["reason"]), ("stop", "release-check"))
        self.assertNotIn("merge_sha", plan)

    def test_a_merge_with_a_tree_the_sync_would_not_build_stops(self):
        self.tagged_upstream()
        self.hand_merge(extra="app/Planted.java")
        plan = self.next_run()
        self.assertEqual((plan["action"], plan["reason"]), ("stop", "release-check"))

    def test_a_merge_the_gate_would_stop_stops(self):
        write(self.upstream, "smarttubetv/build.gradle", GRADLE % (2465, "32.75"))
        write(self.upstream, "app/SignInPresenter.java", "class S {}\n")
        self.upstream_commit()
        run(self.upstream, "git", "tag", "32.75s")
        self.hand_merge()
        plan = self.next_run()
        self.assertEqual((plan["action"], plan["reason"]), ("stop", "gate"))
        self.assertIn("SignInPresenter", pathlib.Path(self.out, "issue-body.md").read_text())
        self.assert_says_the_merge_is_pushed(run(self.clone, "git", "rev-parse", "HEAD"))

    def test_a_tag_that_moved_since_the_merge_stops(self):
        self.merged()
        write(self.upstream, "app/Main.java", "class Main { int moved; }\n")
        self.upstream_commit("moved")
        run(self.upstream, "git", "tag", "-f", "32.75s")
        merged = run(self.clone, "git", "rev-parse", "HEAD")
        self.assertEqual(self.next_run()["reason"], "release-check")
        self.assert_says_the_merge_is_pushed(merged)

    def assert_says_the_merge_is_pushed(self, sha):
        body = pathlib.Path(self.out, "issue-body.md").read_text()
        self.assertNotIn("Nothing was pushed", body)
        self.assertIn(sha, body)
        self.assertIn("material-v32.75-1", body)


class Bundle(Fixture):
    def test_merge_commit_travels_between_jobs_unchanged(self):
        write(self.upstream, "smarttubetv/build.gradle", GRADLE % (2465, "32.75"))
        self.upstream_commit()
        plan = self.decide()

        other = str(pathlib.Path(self.tmp.name, "other"))
        run(self.tmp.name, "git", "clone", "-q", "-b", "material", self.clone, other)
        run(other, "git", "checkout", "-q", "--detach", self.base)
        sync.apply_bundle(other, str(pathlib.Path(self.out, "merge.bundle")), plan["merge_sha"], checkout=True)

        self.assertEqual(run(other, "git", "rev-parse", "HEAD"), plan["merge_sha"])

    def test_wrong_sha_is_refused(self):
        write(self.upstream, "smarttubetv/build.gradle", GRADLE % (2465, "32.75"))
        self.upstream_commit()
        self.decide()
        other = str(pathlib.Path(self.tmp.name, "other"))
        run(self.tmp.name, "git", "clone", "-q", "-b", "material", self.clone, other)
        run(other, "git", "checkout", "-q", "--detach", self.base)
        with self.assertRaises(SystemExit):
            sync.apply_bundle(other, str(pathlib.Path(self.out, "merge.bundle")), "0" * 40, checkout=True)


class CommandLine(Fixture):
    def cli(self, *args):
        output = pathlib.Path(self.tmp.name, "github-output")
        env = dict(os.environ, GITHUB_OUTPUT=str(output), GITHUB_STEP_SUMMARY=os.devnull)
        script = pathlib.Path(sync.__file__)
        result = subprocess.run(("python3", "-I", "-B", str(script)) + args, cwd=self.clone, env=env, capture_output=True,
                                text=True)
        return result, output.read_text() if output.exists() else ""

    def decide_files(self, releases, issues, own):
        paths = []
        for name, value in (("r.json", releases), ("i.json", issues), ("o.json", own)):
            path = pathlib.Path(self.tmp.name, name)
            path.write_text(json.dumps(value))
            paths.append(str(path))
        return paths

    def test_decide_writes_the_outputs_the_workflow_reads(self):
        releases, issues, own = self.decide_files([], [[]], [[]])
        result, output = self.cli("decide", "--releases", releases, "--issues", issues, "--own-releases", own,
                                  "--out", self.out, "--dry-run", "true")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("dry_run=true\n", output)
        self.assertIn("action=noop\n", output)

    def test_decide_marks_a_real_run(self):
        releases, issues, own = self.decide_files([], [[]], [[]])
        _, output = self.cli("decide", "--releases", releases, "--issues", issues, "--own-releases", own,
                             "--out", self.out, "--dry-run", "false")
        self.assertIn("dry_run=false\n", output)

    def test_decide_reports_the_tag_before_a_crash(self):
        rows = [{"tag_name": "32.75s", "draft": False, "prerelease": False}]
        releases, issues, own = self.decide_files(rows, [[]], [[]])
        result, output = self.cli("decide", "--releases", releases, "--issues", issues, "--own-releases", own,
                                  "--out", self.out, "--dry-run", "false", "--upstream-url", "file:///nonexistent")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("tag=32.75s\n", output)

    def test_check_tag(self):
        self.assertEqual(self.cli("check-tag", "--tag", "material-v32.68-1")[0].returncode, 0)
        failed = self.cli("check-tag", "--tag", "material-v32.68")[0]
        self.assertEqual(failed.returncode, 1)
        self.assertIn("::error::", failed.stdout)

    def test_no_simulation_flags_in_the_production_command(self):
        self.assertNotIn("--local-ref", self.cli("decide", "--help")[0].stdout)

    def test_apply_bundle_without_checkout_keeps_the_tree(self):
        write(self.upstream, "smarttubetv/build.gradle", GRADLE % (2465, "32.75"))
        self.upstream_commit()
        plan = self.decide()
        other = str(pathlib.Path(self.tmp.name, "other"))
        run(self.tmp.name, "git", "clone", "-q", "-b", "material", self.clone, other)
        run(other, "git", "checkout", "-q", "--detach", self.base)
        sync.apply_bundle(other, str(pathlib.Path(self.out, "merge.bundle")), plan["merge_sha"], checkout=False)
        self.assertEqual(run(other, "git", "rev-parse", "HEAD"), self.base)
        self.assertEqual(run(other, "git", "rev-parse", "refs/sync/merge"), plan["merge_sha"])

    def test_decide_with_nothing_published_touches_no_network(self):
        plan = sync.Sync(self.clone, self.out).plan([], [], upstream_url="file:///nonexistent")
        self.assertEqual(plan, {"action": "noop", "reason": "no-stable-release"})

    def test_open_issue_blocks_before_any_fetch(self):
        releases = [{"tag_name": "32.75s", "draft": False, "prerelease": False}]
        issues = [{"number": 9, "title": "[material-sync] upstream 32.75s", "user": {"login": "github-actions[bot]"}}]
        plan = sync.Sync(self.clone, self.out).plan(releases, issues, upstream_url="file:///nonexistent")
        self.assertEqual(plan, {"action": "noop", "reason": "open-issue", "tag": "32.75s"})


class Gitmodules(unittest.TestCase):
    """Git decides what these files mean, so git reads them."""
    PLAIN = '[submodule "a"]\n\tpath = a\n\turl = https://x/a\n'

    def same(self, text):
        return sync.normalized_gitmodules(text) == sync.normalized_gitmodules(self.PLAIN)

    def test_layout_changes_are_not_changes(self):
        self.assertTrue(self.same('# note\n[SUBMODULE "a"]\n  PATH=a\n  url = https://x/a\n  branch = main\n'))

    def test_reviewers_cases(self):
        self.assertFalse(self.same(self.PLAIN + "\tURL = https://evil/a\n"))
        self.assertFalse(self.same(self.PLAIN + '[submodule "a"] url = https://evil/a\n'))

    def test_url_change_new_submodule_and_garbage(self):
        self.assertFalse(self.same(self.PLAIN.replace("x/a", "evil/a")))
        self.assertFalse(self.same(self.PLAIN + '[submodule "b"]\n\tpath = b\n\turl = https://x/b\n'))
        self.assertFalse(self.same(self.PLAIN + "[broken\n"))

    def test_empty(self):
        self.assertEqual(sync.normalized_gitmodules(""), "")

    def test_a_key_without_a_value_counts_as_a_change(self):
        self.assertFalse(self.same(self.PLAIN + "\turl\n"))

    def test_submodules_follow_the_value_git_uses(self):
        text = self.PLAIN + "\tURL = https://other/a\n"
        self.assertEqual(sync.submodules(text), [("a", "https://other/a")])


class Report(unittest.TestCase):
    RUN = "https://github.com/x/y/actions/runs/1"

    def report(self, open_issues, gh_error=None, **overrides):
        values = dict(repo="x/y", tag="32.75s", action="proceed", results=["decide=success", "verify=failure"],
                      body_file="", run_url=self.RUN, merge_sha="", release_tag="")
        values.update(overrides)
        printed = io.StringIO()
        with mock.patch.object(sync, "open_issues", return_value=open_issues), \
                mock.patch.object(sync, "gh", side_effect=gh_error) as gh, contextlib.redirect_stdout(printed):
            self.bodies = []
            gh.side_effect = lambda *args: self.record(args, gh_error)
            code = sync.command_report(argparse.Namespace(**values))
        self.printed = printed.getvalue()
        return code, [call.args for call in gh.call_args_list]

    def record(self, args, error):
        if "--body-file" in args:
            self.bodies.append(pathlib.Path(args[args.index("--body-file") + 1]).read_text())
        if error is not None:
            raise error

    def test_first_failure_creates_the_issue_and_stays_red(self):
        code, calls = self.report([])
        self.assertEqual(code, 1)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][:2], ("issue", "create"))
        self.assertIn("[material-sync] upstream 32.75s", calls[0])

    def test_a_second_failure_updates_the_same_issue(self):
        issue = {"number": 5, "title": "[material-sync] upstream 32.75s", "user": {"login": "github-actions[bot]"}}
        code, calls = self.report([issue])
        self.assertEqual(code, 1)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][:3], ("issue", "edit", "5"))

    def test_success_files_nothing_and_stays_green(self):
        self.assertEqual(self.report([], results=["decide=success", "verify=success"]), (0, []))

    def test_a_failure_without_a_tag_files_nothing_and_stays_red(self):
        self.assertEqual(self.report([], tag="", action="", results=["decide=failure"]), (1, []))
        self.assertIn("::error::", self.printed)

    def test_a_stop_files_the_text_of_the_gate_and_stays_red(self):
        with tempfile.NamedTemporaryFile("w", suffix=".md") as body:
            body.write("gate text")
            body.flush()
            code, calls = self.report([], action="stop", body_file=body.name, results=["decide=success"])
        self.assertEqual(code, 1)
        self.assertEqual(calls[0][:2], ("issue", "create"))
        self.assertEqual(self.bodies, ["gate text"])

    def test_a_stop_stays_red_when_issues_cannot_be_filed_and_says_why(self):
        error = RuntimeError("gh issue create failed: the repository has disabled issues")
        with tempfile.NamedTemporaryFile("w", suffix=".md") as body:
            body.write("gate text: a/auth/b.java")
            body.flush()
            code, _ = self.report([], gh_error=error, action="stop", body_file=body.name, results=["decide=success"])
        self.assertEqual(code, 1)
        self.assertIn("disabled issues", self.printed)
        self.assertIn("a/auth/b.java", self.printed)

    def test_a_failed_listing_of_issues_stays_red_too(self):
        with mock.patch.object(sync, "open_issues", side_effect=RuntimeError("no")), \
                contextlib.redirect_stdout(io.StringIO()):
            code = sync.command_report(argparse.Namespace(
                repo="x/y", tag="32.75s", action="proceed", results=["verify=failure"], body_file="",
                run_url=self.RUN, merge_sha="", release_tag=""))
        self.assertEqual(code, 1)

    def test_a_failed_retry_of_the_release_names_the_merge(self):
        self.report([], action="release-only", merge_sha="abc123", release_tag="material-v32.75-1",
                    results=["decide=success", "publish=skipped", "release=failure"])
        self.assertIn("abc123", self.bodies[0])
        self.assertNotIn("Nothing was pushed", self.bodies[0])


class PlantedFiles(unittest.TestCase):
    """How the workflows call the script: a file beside it must not replace a standard module."""

    def test_a_module_planted_beside_the_script_does_not_run(self):
        source = pathlib.Path(sync.__file__).parent
        with tempfile.TemporaryDirectory() as temp:
            for name in ("sync.py", "sync_logic.py"):
                pathlib.Path(temp, name).write_text((source / name).read_text())
            marker = pathlib.Path(temp, "ran")
            for name in ("json", "argparse", "collections", "re", "subprocess"):
                pathlib.Path(temp, name + ".py").write_text("open(%r, 'w').close()\n" % str(marker))
            result = subprocess.run(("python3", "-I", "-B", str(pathlib.Path(temp, "sync.py")), "--help"),
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse(marker.exists())


class GhErrors(unittest.TestCase):
    def test_the_error_carries_what_gh_said(self):
        failed = subprocess.CompletedProcess(("gh",), 1, "", "HTTP 410: Issues are disabled")
        with mock.patch.object(sync.subprocess, "run", return_value=failed):
            with self.assertRaisesRegex(RuntimeError, "Issues are disabled"):
                sync.gh("issue", "create")


if __name__ == "__main__":
    unittest.main()
