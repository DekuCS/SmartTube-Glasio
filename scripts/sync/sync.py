#!/usr/bin/env python3
"""The git and GitHub half of the upstream sync. Decisions live in sync_logic.py.

Nothing here runs code from the upstream tree: upstream's commits are only read, diffed and merged.
"""
import argparse
import json
import os
import pathlib
import subprocess
import sys
import tempfile

import sync_logic as logic

UPSTREAM_URL = "https://github.com/yuliskov/SmartTube"
GRADLE_FILE = "smarttubetv/build.gradle"
PATCH_FILE = "material-version.properties"
WORKFLOWS = ".github/workflows"
TAG_REF = "refs/sync/upstream-tag"
MERGE_REF = "refs/sync/merge"
BOT_EMAIL = "41898282+github-actions[bot]@users.noreply.github.com"


def git(cwd, *args, check=True):
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0")
    result = subprocess.run(("git",) + args, cwd=cwd, env=env, capture_output=True)
    if check and result.returncode != 0:
        raise RuntimeError("git %s failed: %s" % (args[0], result.stderr.decode(errors="replace").strip()))
    return result


def text_of(cwd, *args):
    return git(cwd, *args).stdout.decode(errors="replace").strip()


def show(cwd, spec):
    """The text of a blob, "" when it does not exist."""
    result = git(cwd, "show", spec, check=False)
    return result.stdout.decode(errors="replace") if result.returncode == 0 else ""


def names(output):
    return [name.decode(errors="replace") for name in output.split(b"\0") if name]


def repo_diff(cwd, label, old, new, compare):
    paths = names(git(cwd, "diff", "--name-only", "--no-renames", "-z", old, new).stdout)
    texts = {}
    for path in paths:
        if path.rsplit("/", 1)[-1] == ".gitmodules" or logic.is_gradle_file(path):
            texts[path] = (show(cwd, "%s:%s" % (old, path)), show(cwd, "%s:%s" % (new, path)))
    return logic.RepoDiff(label, paths, texts, compare)


def compare_url(url, old, new):
    return "%s/compare/%s...%s" % (url[:-4] if url.endswith(".git") else url, old, new)


class Sync:
    def __init__(self, root, out):
        self.root = root
        self.out = pathlib.Path(out)

    def plan(self, releases, issues, upstream_url=UPSTREAM_URL, on_tag=lambda tag: None):
        tag = logic.pick_stable_tag(releases)
        if tag is None:
            return {"action": "noop", "reason": "no-stable-release"}
        on_tag(tag)
        if logic.find_open_issue(issues, tag) is not None:
            return {"action": "noop", "reason": "open-issue", "tag": tag}
        git(self.root, "fetch", "--no-tags", "--quiet", upstream_url, "+refs/tags/%s:%s" % (tag, TAG_REF))
        return self.plan_ref(TAG_REF, tag)

    def plan_ref(self, ref, tag):
        """Gate, then merge. Leaves the merge commit in merge.bundle; no branch moves."""
        base = text_of(self.root, "rev-parse", "HEAD")
        commit = text_of(self.root, "rev-parse", ref + "^{commit}")
        plan = {"tag": tag, "base_sha": base}
        if git(self.root, "merge-base", "--is-ancestor", commit, base, check=False).returncode == 0:
            return dict(plan, action="noop", reason="already-merged")

        merge_bases = git(self.root, "merge-base", "--all", base, commit, check=False).stdout.decode().split()
        if len(merge_bases) != 1:
            return self.stop(plan, "gate", ["no single merge base with the upstream commit"], [])

        findings, urls = self.gate(base, merge_bases[0], commit)
        if findings:
            return self.stop(plan, "gate", findings, urls)
        return self.merge(plan, commit, urls)

    def gate(self, base, merge_base, commit):
        """Our pins are what ships today, so the submodules compare from them, the tree from the merge base."""
        urls = [compare_url(UPSTREAM_URL, merge_base, commit)]
        diffs = [repo_diff(self.root, "", merge_base, commit, urls[0])]
        findings = []
        for path, url in logic.parse_gitmodules(show(self.root, base + ":.gitmodules")).values():
            old = git(self.root, "rev-parse", "%s:%s" % (base, path), check=False)
            new = git(self.root, "rev-parse", "%s:%s" % (commit, path), check=False)
            if old.returncode != 0 or new.returncode != 0:
                findings.append("no pin to compare for submodule " + logic.safe_text(path))
                continue
            old, new = old.stdout.decode().strip(), new.stdout.decode().strip()
            if old == new:
                continue
            urls.append(compare_url(url, old, new))
            try:
                diffs.append(self.submodule_diff(path, url, old, new, urls[-1]))
            except RuntimeError:
                findings.append("could not fetch both pins of submodule " + logic.safe_text(path))
        return findings + logic.gate_findings(diffs), urls

    @staticmethod
    def submodule_diff(path, url, old, new, compare):
        with tempfile.TemporaryDirectory() as scratch:
            git(scratch, "init", "--quiet", "--bare")
            git(scratch, "fetch", "--quiet", "--depth=1", "--no-tags", url, old, new)
            return repo_diff(scratch, path, old, new, compare)

    def merge(self, plan, commit, urls):
        base = plan["base_sha"]
        tag = plan["tag"]
        git(self.root, "checkout", "--quiet", "--detach")
        result = git(self.root, "merge", "--no-commit", "--no-ff", "--quiet", commit, check=False)
        if git(self.root, "rev-parse", "--quiet", "--verify", "MERGE_HEAD", check=False).returncode != 0:
            raise RuntimeError("git merge did not start: " + result.stderr.decode(errors="replace").strip())

        self.drop_workflow_changes()
        self.take_upstream_pins(commit)

        conflicts = names(git(self.root, "diff", "--name-only", "--diff-filter=U", "-z").stdout)
        if conflicts:
            git(self.root, "merge", "--abort")
            findings = ["merge conflict: " + logic.safe_text(path) for path in conflicts]
            return self.stop(plan, "merge-conflict", findings, urls)

        version, problem = self.next_version(base, tag)
        if problem:
            git(self.root, "merge", "--abort")
            return self.stop(plan, "version", [problem], urls)

        patch_path = pathlib.Path(self.root, PATCH_FILE)
        patch_path.write_text(logic.with_patch(show(self.root, "%s:%s" % (base, PATCH_FILE)), version.patch))
        git(self.root, "add", "--", PATCH_FILE)
        git(self.root, "-c", "user.name=" + logic.BOT_LOGIN, "-c", "user.email=" + BOT_EMAIL, "commit", "--quiet",
            "--no-verify", "-m", "Merge upstream " + tag)

        merge_sha = text_of(self.root, "rev-parse", "HEAD")
        git(self.root, "update-ref", MERGE_REF, merge_sha)
        self.out.mkdir(parents=True, exist_ok=True)
        git(self.root, "bundle", "create", "--quiet", str(self.out / "merge.bundle"), MERGE_REF, "^" + base)
        return dict(plan, action="proceed", merge_sha=merge_sha, version_name=version.name,
                    version_code=str(version.code), release_tag=version.tag)

    def drop_workflow_changes(self):
        git(self.root, "reset", "--quiet", "HEAD", "--", WORKFLOWS)
        git(self.root, "checkout", "--quiet", "HEAD", "--", WORKFLOWS)
        git(self.root, "clean", "--quiet", "-fd", "--", WORKFLOWS)

    def take_upstream_pins(self, commit):
        for path, _ in logic.parse_gitmodules(show(self.root, commit + ":.gitmodules")).values():
            pin = text_of(self.root, "rev-parse", "%s:%s" % (commit, path))
            git(self.root, "update-index", "--add", "--cacheinfo", "160000,%s,%s" % (pin, path))

    def next_version(self, base, tag):
        """(version, problem): the version the merged tree will build, or why it cannot."""
        try:
            old_code, old_name = logic.parse_gradle_version(show(self.root, "%s:%s" % (base, GRADLE_FILE)))
            new_code, new_name = logic.parse_gradle_version(show(self.root, ":" + GRADLE_FILE))
            old_patch = logic.parse_patch(show(self.root, "%s:%s" % (base, PATCH_FILE)))
            old = logic.material_version(old_code, old_name, old_patch)
            new = logic.material_version(new_code, new_name, logic.next_patch(old_name, new_name, old_patch))
        except ValueError as error:
            return None, "version: " + logic.safe_text(str(error))
        if logic.is_stable_tag(tag) and logic.upstream_version(tag) != new_name:
            return None, "version: tag and versionName disagree: " + logic.safe_text(tag + " " + new_name)
        if new.code <= old.code:
            return None, "version: versionCode would not grow: %d to %d" % (old.code, new.code)
        return new, None

    def stop(self, plan, reason, findings, urls):
        self.out.mkdir(parents=True, exist_ok=True)
        (self.out / "issue-body.md").write_text(logic.stop_body(plan["tag"], findings, urls))
        return dict(plan, action="stop", reason=reason, findings=findings)


def apply_bundle(root, bundle, sha, checkout):
    """Takes the merge commit from the bundle of the gate job. The SHA is what makes it the same commit."""
    git(root, "fetch", "--no-tags", "--quiet", bundle, "+%s:%s" % (MERGE_REF, MERGE_REF))
    actual = text_of(root, "rev-parse", MERGE_REF)
    if actual != sha:
        sys.exit("The bundle holds %s, expected %s." % (actual, sha))
    if checkout:
        git(root, "checkout", "--quiet", "--detach", sha)


def set_outputs(values):
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as output:
            for key, value in values.items():
                output.write("%s=%s\n" % (key, value))


def summary(lines):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as output:
            output.write("\n".join(lines) + "\n")


def command_decide(args):
    dry_run = args.dry_run == "true"
    set_outputs({"dry_run": args.dry_run})
    with open(args.issues, encoding="utf-8") as source:
        issues = [issue for page in json.load(source) for issue in page]
    sync = Sync(".", args.out)
    if args.local_ref:
        plan = sync.plan_ref(args.local_ref, args.label)
    else:
        with open(args.releases, encoding="utf-8") as source:
            releases = json.load(source)
        plan = sync.plan(releases, issues, on_tag=lambda tag: set_outputs({"tag": tag}))

    set_outputs({key: value for key, value in plan.items() if key != "findings"})
    mode = "Dry run: " if dry_run else ""
    lines = ["%s%s (%s)" % (mode, plan["action"], plan.get("reason", plan.get("release_tag", "")))]
    if plan.get("tag"):
        lines.append("Upstream: `%s`" % plan["tag"])
    if plan["action"] == "stop":
        lines += ["", pathlib.Path(args.out, "issue-body.md").read_text()]
    summary(lines)
    print(json.dumps(plan, indent=2))


def command_apply_bundle(args):
    apply_bundle(".", args.bundle, args.sha, not args.no_checkout)


def command_check_tag(args):
    code, name = logic.parse_gradle_version(pathlib.Path(GRADLE_FILE).read_text(encoding="utf-8"))
    patch = logic.parse_patch(pathlib.Path(PATCH_FILE).read_text(encoding="utf-8"))
    version = logic.material_version(code, name, patch)
    if not logic.tag_matches_version(args.tag, version.name):
        print("::error::The tag does not match versionName %s (expected %s)." % (version.name, version.tag))
        return 1
    return 0


def gh(*args):
    return subprocess.run(("gh",) + args, check=True, capture_output=True, text=True).stdout


def open_issues(repo):
    pages = json.loads(gh("api", "--paginate", "--slurp", "repos/%s/issues?state=open&per_page=100" % repo))
    return [issue for page in pages for issue in page]


def command_report(args):
    """Opens or updates the one issue of the tag. A run that stopped or failed leaves exactly one trace."""
    results = dict(item.split("=", 1) for item in args.results)
    failed = logic.failed_jobs(results)
    if args.action != "stop" and not failed:
        return 0
    if not logic.is_stable_tag(args.tag):
        print("::error::The run failed before it chose an upstream tag, so there is no issue to file.")
        return 1
    if args.action == "stop":
        body = pathlib.Path(args.body_file).read_text(encoding="utf-8")
    else:
        pushed = args.merge_sha if results.get("publish") == "success" else ""
        body = logic.failure_body(args.tag, failed, args.run_url, pushed, args.release_tag)

    number = logic.find_open_issue(open_issues(args.repo), args.tag)
    with tempfile.NamedTemporaryFile("w", suffix=".md", encoding="utf-8") as body_file:
        body_file.write(body)
        body_file.flush()
        if number is None:
            gh("issue", "create", "--repo", args.repo, "--title", logic.issue_title(args.tag),
               "--body-file", body_file.name)
        else:
            gh("issue", "edit", str(number), "--repo", args.repo, "--body-file", body_file.name)
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    decide = commands.add_parser("decide", help="pick the tag, gate it, merge it locally")
    decide.add_argument("--releases", help="JSON of the upstream releases")
    decide.add_argument("--issues", required=True, help="JSON pages of this repository's open issues")
    decide.add_argument("--out", required=True, help="directory for merge.bundle and issue-body.md")
    decide.add_argument("--dry-run", choices=("true", "false"), default="false", help="only changes the summary: "
                        "the workflow skips push, release and issue on the dry_run output")
    decide.add_argument("--local-ref", help="simulation: merge this local ref instead of the picked tag")
    decide.add_argument("--label", default="local", help="simulation: the name of --local-ref")
    decide.set_defaults(run=command_decide)

    bundle = commands.add_parser("apply-bundle", help="take the merge commit of the gate job")
    bundle.add_argument("--bundle", required=True)
    bundle.add_argument("--sha", required=True)
    bundle.add_argument("--no-checkout", action="store_true")
    bundle.set_defaults(run=command_apply_bundle)

    check = commands.add_parser("check-tag", help="the release tag must be material-v<versionName>")
    check.add_argument("--tag", required=True)
    check.set_defaults(run=command_check_tag)

    report = commands.add_parser("report", help="open or update the issue of a stopped or failed run")
    report.add_argument("--repo", required=True)
    report.add_argument("--tag", default="")
    report.add_argument("--action", default="")
    report.add_argument("--results", nargs="+", required=True, help="job=result ...")
    report.add_argument("--body-file", default="")
    report.add_argument("--run-url", required=True)
    report.add_argument("--merge-sha", default="")
    report.add_argument("--release-tag", default="")
    report.set_defaults(run=command_report)

    args = parser.parse_args()
    return args.run(args) or 0


if __name__ == "__main__":
    sys.exit(main())
