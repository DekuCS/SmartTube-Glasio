"""What the workflows promise about isolation. Skipped where only scripts/sync is checked out (the sync's own job)."""
import os
import pathlib
import re
import stat
import subprocess
import tempfile
import unittest

WORKFLOWS = pathlib.Path(__file__).resolve().parents[2] / ".github" / "workflows"
HOLD = "vars.MATERIAL_SYNC_HOLD != 'true'"


def jobs_of(name):
    text = (WORKFLOWS / name).read_text()
    body = text[text.index("\njobs:\n"):]
    parts = re.split(r"^  ([a-z-]+):\n", body, flags=re.M)
    return dict(zip(parts[1::2], parts[2::2]))


def step_of(job, name):
    """The text of one step of a job, by its name."""
    steps = re.split(r"^      - ", job, flags=re.M)
    for step in steps:
        if step.startswith("name: " + name + "\n"):
            return step
    raise AssertionError("no step named " + name)


def run_block(step):
    block = []
    for line in step[step.index("run: |\n") + len("run: |\n"):].split("\n"):
        if line.strip() and not line.startswith(" " * 10):
            break
        block.append(line[10:])
    return "\n".join(block) + "\n"


@unittest.skipUnless(WORKFLOWS.is_dir(), "the workflows are not checked out here")
class Isolation(unittest.TestCase):
    def test_every_python_call_ignores_the_script_directory(self):
        for path in WORKFLOWS.glob("*.yml"):
            for line in path.read_text().splitlines():
                if re.search(r"\bpython3\b", line):
                    self.assertIn("python3 -I -B", line, path.name)

    def test_writing_jobs_run_scripts_only_from_the_tools_directory(self):
        jobs = jobs_of("material-sync.yml")
        for name in ("publish", "report"):
            scripts = re.findall(r"python3 -I -B (\S+)", jobs[name])
            self.assertTrue(scripts, name)
            for script in scripts:
                self.assertEqual(script, '"$GITHUB_WORKSPACE/tools/scripts/sync/sync.py"', name)
            self.assertNotIn("gradlew", jobs[name])

    def test_the_tools_directory_holds_only_the_sync_scripts(self):
        jobs = jobs_of("material-sync.yml")
        for name in ("decide", "publish", "report"):
            step = step_of(jobs[name], "Check out the sync scripts")
            self.assertIn("path: tools", step, name)
            self.assertIn("/scripts/sync/", step, name)
            self.assertIn("sparse-checkout-cone-mode: false", step, name)
            self.assertIn("persist-credentials: false", step, name)

    def test_the_tools_come_from_the_commit_before_the_merge(self):
        jobs = jobs_of("material-sync.yml")
        self.assertIn("ref: ${{ steps.base.outputs.sha }}", step_of(jobs["decide"], "Check out the sync scripts"))
        self.assertIn("ref: ${{ needs.decide.outputs.base_sha }}", step_of(jobs["publish"], "Check out the sync scripts"))

    def test_publish_has_no_stored_credentials_and_the_token_only_for_the_push(self):
        publish = jobs_of("material-sync.yml")["publish"]
        self.assertEqual(publish.count("actions/checkout@"), publish.count("persist-credentials: false"))
        self.assertEqual(publish.count("github.token"), 1)
        push = step_of(publish, "Push exactly that commit to material")
        self.assertIn("github.token", push)
        self.assertNotIn("python3", push)

    def test_nothing_after_decide_runs_during_a_hold(self):
        for name, job in jobs_of("material-sync.yml").items():
            if name != "decide":
                self.assertIn(HOLD, job, name)

    def test_a_hold_shows_in_the_run(self):
        decide = jobs_of("material-sync.yml")["decide"]
        self.assertIn("::notice::", step_of(decide, "Say that the sync is on hold"))


@unittest.skipUnless(WORKFLOWS.is_dir(), "the workflows are not checked out here")
class SignJob(unittest.TestCase):
    def setUp(self):
        self.sign = jobs_of("material-release.yml")["sign"]

    def test_the_tool_that_needs_no_secret_runs_without_them(self):
        align = step_of(self.sign, "Align")
        self.assertIn("zipalign", align)
        self.assertNotIn("secrets.", align)
        sign = step_of(self.sign, "Sign")
        self.assertNotIn("zipalign", sign)

    def test_no_checkout(self):
        self.assertNotIn("actions/checkout", self.sign)

    def check(self, names, symlink=None):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            unsigned = root / "unsigned"
            unsigned.mkdir()
            for name in names:
                (unsigned / name).write_text("apk")
            if symlink:
                (unsigned / symlink).symlink_to(root / "elsewhere")
            tools = root / "sdk" / "build-tools" / "36.0.0"
            tools.mkdir(parents=True)
            aapt2 = tools / "aapt2"
            aapt2.write_text("#!/bin/sh\necho \"package: name='io.github.ivanosh.smarttubematerial' "
                             "versionCode='245801' versionName='32.68-1' x\"\n")
            aapt2.chmod(aapt2.stat().st_mode | stat.S_IEXEC)
            env = dict(os.environ, TAG="material-v32.68-1", RUNNER_TEMP=temp, ANDROID_HOME=str(root / "sdk"),
                       BUILD_TOOLS_VERSION="36.0.0")
            script = run_block(step_of(self.sign, "The APKs must be the expected files of this release"))
            return subprocess.run(("bash", "-c", script), env=env, capture_output=True, text=True).returncode

    ARM64 = "SmartTube_material_32.68-1_arm64-v8a.apk"
    ARM32 = "SmartTube_material_32.68-1_armeabi-v7a.apk"
    UNIVERSAL = "SmartTube_material_32.68-1_universal.apk"

    def test_the_expected_files_pass(self):
        self.assertEqual(self.check([self.ARM64, self.ARM32, self.UNIVERSAL]), 0)

    def test_a_name_with_a_space_does_not_stand_for_two_files(self):
        self.assertNotEqual(self.check([self.ARM64 + " " + self.ARM32, self.UNIVERSAL]), 0)

    def test_a_missing_file_fails(self):
        self.assertNotEqual(self.check([self.ARM64, self.UNIVERSAL]), 0)

    def test_an_extra_file_fails(self):
        self.assertNotEqual(self.check([self.ARM64, self.ARM32, self.UNIVERSAL, ".hidden"]), 0)

    def test_a_symlink_fails(self):
        self.assertNotEqual(self.check([self.ARM64, self.ARM32], symlink=self.UNIVERSAL), 0)


if __name__ == "__main__":
    unittest.main()
