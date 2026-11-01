"""Pure decisions of the upstream sync: tag picking, version math, gate matching, issue text.

No git, no network, no files. Tag names, paths and file contents come from upstream and are data.
"""
import collections
import re

BOT_LOGIN = "github-actions[bot]"

_STABLE_TAG = re.compile(r"([0-9]+)\.([0-9]+)s")
_GRADLE_CODE = re.compile(r"^\s*versionCode\s+([0-9]+)\s*$", re.M)
_GRADLE_NAME = re.compile(r'^\s*versionName\s+"([0-9]+\.[0-9]+)"\s*$', re.M)
_PATCH_LINE = re.compile(r"^materialPatch=(.*)$", re.M)
# A false stop costs the owner a look, a false pass can cost a token. PoToken is the one family left out on purpose:
# it identifies the client, not the account, and matching it would stop most syncs.
_AUTH_PATH = re.compile(
    r"(?i:auth(?!or(?:s|ed)?(?:[^a-z]|$))|RetrofitOkHttpHelper|credential|login|cookie|account|(?<!po)(?<!po_)token)"
    r"|SignIn|Signin|(?<![A-Za-z])(?i:sign[_-]?in)")
_TOKEN_LINE = re.compile(
    r"(?i)refresh[_-]?token|access[_-]?token|\bid[_-]?token|client[_-]?(?:secret|id)\b|\bauthorization\b|\bbearer\b"
    r"|persistAccounts|allowBackup|fullBackupContent|dataExtractionRules")
_BUILD_LOGIC_PATH = re.compile(
    r"(?i)(?:^|/)(?:gradle/wrapper/|gradlew(?:\.bat)?$|buildSrc/|[^/]*settings[^/]*\.gradle(?:\.kts)?$"
    r"|gradle\.properties$|gradle/[^/]*\.toml$)")
_CODE_SOURCE_LINE = re.compile(
    r"(?i)\bapply\b.*\bfrom\b|\b(?:get)?repositories\b|://|\burl\b|\buri\s*\(|\bmaven\b|\bivy\b|\bflatDir\b"
    r"|\bjcenter\b|\bmavenLocal\b")
_REPOSITORIES = re.compile(r"\brepositories\b")
_UNSAFE_TEXT = re.compile(r"[^A-Za-z0-9._/+-]")

MaterialVersion = collections.namedtuple("MaterialVersion", "code name tag patch")

# One repository's side of the change: `label` is "" for this repository or the submodule path, `paths` the changed
# files, `texts` {path: (old, new)} for the Gradle files and git's own reading of the .gitmodules files among them,
# `lines` {path: [added and removed lines]}.
RepoDiff = collections.namedtuple("RepoDiff", "label paths texts lines compare")


def is_stable_tag(tag):
    return isinstance(tag, str) and _STABLE_TAG.fullmatch(tag) is not None


def _tag_order(tag):
    return tuple(int(part) for part in _STABLE_TAG.fullmatch(tag).groups())


def pick_stable_tag(releases):
    """The highest stable tag among published, non-prerelease releases, or None."""
    tags = [r.get("tag_name") for r in releases if r.get("draft") is False and r.get("prerelease") is False]
    stable = [tag for tag in tags if is_stable_tag(tag)]
    return max(stable, key=_tag_order, default=None)


def merge_subject(tag):
    return "Merge upstream " + tag


def synced_tag(subject):
    """The upstream tag named by the subject of a sync merge, or None."""
    prefix = merge_subject("")
    tag = subject[len(prefix):] if subject.startswith(prefix) else None
    return tag if is_stable_tag(tag) else None


def upstream_version(tag):
    return tag[:-1]


def material_version(upstream_code, upstream_name, patch):
    if not 1 <= patch <= 99:
        raise ValueError("materialPatch must be between 1 and 99, got %d" % patch)
    name = "%s-%d" % (upstream_name, patch)
    return MaterialVersion(upstream_code * 100 + patch, name, "material-v" + name, patch)


def next_patch(old_upstream_name, new_upstream_name, old_patch):
    return 1 if new_upstream_name != old_upstream_name else old_patch + 1


def parse_gradle_version(text):
    """(versionCode, versionName) of defaultConfig: the first literal pair in smarttubetv/build.gradle."""
    code, name = _GRADLE_CODE.search(text), _GRADLE_NAME.search(text)
    if code is None or name is None:
        raise ValueError("no literal versionCode and versionName in the build file")
    return int(code.group(1)), name.group(1)


def parse_patch(text):
    match = _PATCH_LINE.search(text)
    if match is None or not match.group(1).isascii() or not match.group(1).isdigit():
        raise ValueError("material-version.properties needs a line materialPatch=<number>")
    return int(match.group(1))


def with_patch(text, patch):
    return _PATCH_LINE.sub("materialPatch=%d" % patch, text)


def tag_matches_version(tag, version_name):
    return tag == "material-v" + version_name


def is_auth_path(path):
    return _AUTH_PATH.search(path) is not None


def is_token_line(line):
    return _TOKEN_LINE.search(line) is not None


def is_build_logic_path(path):
    return _BUILD_LOGIC_PATH.search(path) is not None


def is_code_source_line(line):
    return _CODE_SOURCE_LINE.search(line) is not None


def is_gradle_file(path):
    return path.endswith((".gradle", ".kts", ".groovy"))


def repository_declarations(text):
    """Every `repositories { ... }` block and `repositories.x` line, without whitespace.

    An unbalanced block runs to the end of the file, so damage to one still reads as a change.
    """
    declarations = []
    position = 0
    while True:
        match = _REPOSITORIES.search(text, position)
        if match is None:
            return declarations
        start = match.start()
        rest = text[match.end():].lstrip()
        if rest.startswith("{"):
            depth, index = 0, text.index("{", match.end())
            for index in range(index, len(text)):
                depth += {"{": 1, "}": -1}.get(text[index], 0)
                if depth == 0:
                    break
            end = index + 1
        else:
            newline = text.find("\n", start)
            end = len(text) if newline < 0 else newline
        declarations.append("".join(text[start:end].split()))
        position = end


def safe_text(text):
    """Upstream text for an issue body: nothing that can close a code block, mention someone or run anything."""
    return _UNSAFE_TEXT.sub("_", text)


def _gradle_changes_a_source(path, diff):
    old, new = diff.texts.get(path, ("", ""))
    lines = diff.lines.get(path, [])
    return not lines or repository_declarations(old) != repository_declarations(new) \
        or any(is_code_source_line(line) for line in lines)


def gate_findings(diffs):
    """Why the owner must look first, one line per reason. Empty means the gate is open."""
    findings = []
    for diff in diffs:
        prefix = diff.label + "/" if diff.label else ""
        for path in diff.paths:
            shown = safe_text(prefix + path)
            if is_auth_path(path):
                findings.append("sign-in path changed: " + shown)
            if any(is_token_line(line) for line in diff.lines.get(path, [])):
                findings.append("token handling changed: " + shown)
            if is_build_logic_path(path):
                findings.append("build logic changed: " + shown)
            if path.rsplit("/", 1)[-1] == ".gitmodules":
                old, new = diff.texts.get(path, ("", ""))
                if old != new:
                    findings.append("submodule URL or path changed: " + shown)
            if is_gradle_file(path) and _gradle_changes_a_source(path, diff):
                findings.append("Gradle repositories or code sources changed: " + shown)
    return findings


def issue_title(tag):
    return "[material-sync] upstream " + tag


def find_open_issue(issues, tag):
    """The number of the sync's own open issue for this tag. Anyone can open an issue, so the author counts."""
    title = issue_title(tag)
    for issue in issues:
        if issue.get("title") == title and issue.get("user", {}).get("login") == BOT_LOGIN \
                and "pull_request" not in issue:
            return issue["number"]
    return None


def failed_jobs(results):
    return [job for job, result in results.items() if result == "failure"]


_CLOSING = "Close this issue once it is dealt with: the sync does not retry this tag while it is open."


def stop_body(tag, findings, compare_urls):
    lines = ["The upstream sync stopped at `%s`. Nothing was pushed or released." % tag, "", "```"]
    lines += findings
    lines += ["```", ""]
    lines += ["Compare: " + url for url in compare_urls]
    lines += ["", _CLOSING]
    return "\n".join(lines) + "\n"


def failure_body(tag, failed, run_url, pushed_sha, release_tag):
    lines = ["The upstream sync failed at `%s`, in: %s." % (tag, ", ".join(failed)), "", "Run: " + run_url, ""]
    if pushed_sha:
        lines += ["The merge `%s` is on `material`, but no release was published. To publish it, push the tag "
                  "`%s` at that commit; `material.yml` releases it." % (pushed_sha, release_tag), ""]
    else:
        lines += ["Nothing was pushed or released.", ""]
    lines += [_CLOSING]
    return "\n".join(lines) + "\n"
