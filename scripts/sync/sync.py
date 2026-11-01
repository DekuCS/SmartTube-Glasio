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


def gitmodules_entries(text):
    """git's own reading of a .gitmodules text: [(key, value)], or None if git cannot read it."""
    with tempfile.NamedTemporaryFile(suffix=".gitmodules") as source:
        source.write(text.encode())
        source.flush()
        result = subprocess.run(("git", "config", "-f", source.name, "--null", "--get-regexp",
                                 r"^submodule\..*\.(url|path)$"), capture_output=True)
    if result.returncode == 1:
        return []
    if result.returncode != 0:
        return None
    entries = result.stdout.decode(errors="replace").split("\0")
    return [tuple(entry.split("\n", 1)) for entry in entries if entry]


def normalized_gitmodules(text):
    entries = gitmodules_entries(text)
    if entries is None:
        return "unreadable\n" + text
    return "\n".join("%s=%s" % entry for entry in entries)


def submodules(text):
    """[(path, url)], with the value git uses when a key repeats."""
    modules = {}
    for key, value in gitmodules_entries(text) or []:
        name, field = key[len("submodule."):].rsplit(".", 1)
        modules.setdefault(name, {})[field] = value
    return [(module.get("path"), module.get("url")) for module in modules.values()]


def changed_lines(cwd, old, new, path):
    """The added and removed lines of one file. --text so that upstream's attributes cannot hide them."""
    output = git(cwd, "--literal-pathspecs", "diff", "-U0", "--text", "--no-color", "--no-renames", "--no-ext-diff",
                 "--no-textconv", old, new, "--", path).stdout.decode(errors="replace")
    lines, in_hunk = [], False
    for line in output.split("\n"):
        if line.startswith("@@"):
            in_hunk = True
        elif in_hunk and line[:1] in ("+", "-"):
            lines.append(line[1:])
    return lines


def repo_diff(cwd, label, old, new, compare):
    paths = names(git(cwd, "diff", "--name-only", "--no-renames", "-z", old, new).stdout)
    texts, lines = {}, {}
    for path in paths:
        lines[path] = changed_lines(cwd, old, new, path)
        sides = show(cwd, "%s:%s" % (old, path)), show(cwd, "%s:%s" % (new, path))
        if path.rsplit("/", 1)[-1] == ".gitmodules":
            texts[path] = tuple(normalized_gitmodules(side) for side in sides)
        elif logic.is_gradle_file(path):
            texts[path] = sides
    return logic.RepoDiff(label, paths, texts, lines, compare)


def compare_url(url, old, new):
    return "%s/compare/%s...%s" % (url[:-4] if url.endswith(".git") else url, old, new)


class Sync:
    def __init__(self, root, out, published=()):
        self.root = root
        self.out = pathlib.Path(out)
        self.published = set(published)

    def plan(self, releases, issues, upstream_url=UPSTREAM_URL, on_tag=lambda tag: None):
        pending = self.unreleased_merge()
        if pending is not None:
            on_tag(pending["tag"])
            if logic.find_open_issue(issues, pending["tag"]) is not None:
                return {"action": "noop", "reason": "open-issue", "tag": pending["tag"]}
            return dict(pending, action="release-only", base_sha=text_of(self.root, "rev-parse", "HEAD"))
        tag = logic.pick_stable_tag(releases)
        if tag is None:
            return {"action": "noop", "reason": "no-stable-release"}
        on_tag(tag)
        if logic.find_open_issue(issues, tag) is not None:
            return {"action": "noop", "reason": "open-issue", "tag": tag}
        git(self.root, "fetch", "--no-tags", "--quiet", upstream_url, "+refs/tags/%s:%s" % (tag, TAG_REF))
        return self.plan_ref(TAG_REF, tag)

    def unreleased_merge(self):
        """The newest merge of this sync on the branch, if its release never appeared.

        History, not an issue, is the trace: a release that failed or was cancelled after the push stays visible
        here whether or not Issues works and whether or not someone closed the issue.
        """
        log = text_of(self.root, "log", "--first-parent", "--format=%H%x00%ae%x00%s", "HEAD")
        for line in log.split("\n"):
            sha, email, subject = line.split("\0", 2)
            tag = logic.synced_tag(subject)
            if email == BOT_EMAIL and tag is not None:
                version = self.version_at(sha)
                if version.tag in self.published:
                    return None
                return {"tag": tag, "merge_sha": sha, "version_name": version.name,
                        "version_code": str(version.code), "release_tag": version.tag}
        return None

    def version_at(self, spec):
        code, name = logic.parse_gradle_version(show(self.root, "%s:%s" % (spec, GRADLE_FILE)))
        return logic.material_version(code, name, logic.parse_patch(show(self.root, "%s:%s" % (spec, PATCH_FILE))))

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
        for path, url in submodules(show(self.root, base + ":.gitmodules")):
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
            "--no-verify", "-m", logic.merge_subject(tag))

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
        for path, _ in submodules(show(self.root, commit + ":.gitmodules")):
            pin = text_of(self.root, "rev-parse", "%s:%s" % (commit, path))
            git(self.root, "update-index", "--add", "--cacheinfo", "160000,%s,%s" % (pin, path))

    def next_version(self, base, tag):
        """(version, problem): the version the merged tree will build, or why it cannot."""
        try:
            old = self.version_at(base)
            new_code, new_name = logic.parse_gradle_version(show(self.root, ":" + GRADLE_FILE))
            old_name = logic.parse_gradle_version(show(self.root, "%s:%s" % (base, GRADLE_FILE)))[1]
            new = logic.material_version(new_code, new_name, logic.next_patch(old_name, new_name, old.patch))
        except ValueError as error:
            return None, "version: " + logic.safe_text(str(error))
        if logic.is_stable_tag(tag) and logic.upstream_version(tag) != new_name:
            return None, "version: tag and versionName disagree: " + logic.safe_text(tag + " " + new_name)
        if new.code <= old.code:
            return None, "version: versionCode would not grow: %d to %d" % (old.code, new.code)
        if new.tag in self.published:
            return None, "version: release already exists: " + new.tag
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
    with open(args.own_releases, encoding="utf-8") as source:
        published = [release.get("tag_name") for page in json.load(source) for release in page]
    with open(args.releases, encoding="utf-8") as source:
        releases = json.load(source)
    plan = Sync(".", args.out, published).plan(releases, issues, upstream_url=args.upstream_url,
                                               on_tag=lambda tag: set_outputs({"tag": tag}))

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
    result = subprocess.run(("gh",) + args, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError("gh %s failed: %s" % (" ".join(args[:2]), result.stderr.strip()))
    return result.stdout


def open_issues(repo):
    pages = json.loads(gh("api", "--paginate", "--slurp", "repos/%s/issues?state=open&per_page=100" % repo))
    return [issue for page in pages for issue in page]


def file_issue(repo, tag, body):
    number = logic.find_open_issue(open_issues(repo), tag)
    with tempfile.NamedTemporaryFile("w", suffix=".md", encoding="utf-8") as body_file:
        body_file.write(body)
        body_file.flush()
        if number is None:
            gh("issue", "create", "--repo", repo, "--title", logic.issue_title(tag), "--body-file", body_file.name)
        else:
            gh("issue", "edit", str(number), "--repo", repo, "--body-file", body_file.name)


def command_report(args):
    """Leaves one issue per tag if it can, and fails the run either way: GitHub's failure email is what reaches
    the owner when Issues is off or the issue cannot be filed."""
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
        pushed = args.merge_sha if "success" == results.get("publish") or args.action == "release-only" else ""
        body = logic.failure_body(args.tag, failed, args.run_url, pushed, args.release_tag)

    print("::error::The upstream sync needs the owner at %s." % args.tag)
    try:
        file_issue(args.repo, args.tag, body)
    except (RuntimeError, OSError) as error:
        print("::error::Could not file the issue: %s" % error)
        print(body)
    return 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    decide = commands.add_parser("decide", help="pick the tag, gate it, merge it locally")
    decide.add_argument("--releases", required=True, help="JSON of the upstream releases")
    decide.add_argument("--issues", required=True, help="JSON pages of this repository's open issues")
    decide.add_argument("--out", required=True, help="directory for merge.bundle and issue-body.md")
    decide.add_argument("--dry-run", choices=("true", "false"), default="false", help="only changes the summary: "
                        "the workflow skips push, release and issue on the dry_run output")
    decide.add_argument("--own-releases", required=True, help="JSON pages of this repository's releases")
    decide.add_argument("--upstream-url", default=UPSTREAM_URL)
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
