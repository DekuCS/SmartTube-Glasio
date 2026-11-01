"""The git half of the upstream sync, on small repositories in a temp directory. No network."""
import argparse
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
        self.decide()
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

    def test_upstream_workflow_changes_are_dropped(self):
        self.bump()
        write(self.upstream, ".github/workflows/CI.yml", "name: CI changed\n")
        write(self.upstream, ".github/workflows/new.yml", "name: New\n")
        self.upstream_commit()

        plan = self.decide()

        self.assertEqual(plan["action"], "proceed", plan)
        names = run(self.clone, "git", "ls-tree", "-r", "--name-only", plan["merge_sha"], ".github/workflows")
        self.assertEqual(names.split(), [".github/workflows/material.yml"])

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
    def test_decide_needs_no_network_when_nothing_is_published(self):
        releases = pathlib.Path(self.tmp.name, "releases.json")
        issues = pathlib.Path(self.tmp.name, "issues.json")
        releases.write_text("[]")
        issues.write_text("[]")
        plan = sync.Sync(self.clone, self.out).plan(json.loads(releases.read_text()), json.loads(issues.read_text()),
                                                    upstream_url="file:///nonexistent")
        self.assertEqual(plan, {"action": "noop", "reason": "no-stable-release"})

    def test_open_issue_blocks_before_any_fetch(self):
        releases = [{"tag_name": "32.75s", "draft": False, "prerelease": False}]
        issues = [{"number": 9, "title": "[material-sync] upstream 32.75s", "user": {"login": "github-actions[bot]"}}]
        plan = sync.Sync(self.clone, self.out).plan(releases, issues, upstream_url="file:///nonexistent")
        self.assertEqual(plan, {"action": "noop", "reason": "open-issue", "tag": "32.75s"})


class Report(unittest.TestCase):
    RUN = "https://github.com/x/y/actions/runs/1"

    def report(self, open_issues, **overrides):
        values = dict(repo="x/y", tag="32.75s", action="proceed", results=["decide=success", "verify=failure"],
                      body_file="", run_url=self.RUN, merge_sha="", release_tag="")
        values.update(overrides)
        with mock.patch.object(sync, "open_issues", return_value=open_issues), \
                mock.patch.object(sync, "gh") as gh:
            code = sync.command_report(argparse.Namespace(**values))
        return code, [call.args for call in gh.call_args_list]

    def test_first_failure_creates_the_issue(self):
        code, calls = self.report([])
        self.assertEqual(code, 0)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][:2], ("issue", "create"))
        self.assertIn("[material-sync] upstream 32.75s", calls[0])

    def test_a_second_failure_updates_the_same_issue(self):
        issue = {"number": 5, "title": "[material-sync] upstream 32.75s", "user": {"login": "github-actions[bot]"}}
        code, calls = self.report([issue])
        self.assertEqual(code, 0)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][:3], ("issue", "edit", "5"))

    def test_success_files_nothing(self):
        self.assertEqual(self.report([], results=["decide=success", "verify=success"]), (0, []))

    def test_a_failure_without_a_tag_files_nothing_and_stays_red(self):
        self.assertEqual(self.report([], tag="", action="", results=["decide=failure"]), (1, []))

    def test_a_stop_files_the_body_of_the_gate(self):
        with tempfile.NamedTemporaryFile("w", suffix=".md") as body:
            body.write("gate text")
            body.flush()
            code, calls = self.report([], action="stop", body_file=body.name, results=["decide=success"])
        self.assertEqual(code, 0)
        self.assertEqual(calls[0][:2], ("issue", "create"))


if __name__ == "__main__":
    unittest.main()
