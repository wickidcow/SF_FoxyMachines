"""Fail-closed, executable release-publisher regressions (no GitHub network access)."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
PUBLISH = ROOT / "scripts" / "publish_immutable_release.sh"
WORKFLOW = ROOT / ".github" / "workflows" / "maven.yml"
SHA = "a" * 40


class ImmutablePublisherTest(unittest.TestCase):
    def invoke(self, *, event="workflow_dispatch", ref="refs/tags/v1.0.2",
               ref_name="v1.0.2", version="1.0.2", output="SF_FoxyMachines1.0.2.jar",
               exists=False, make_jar=True):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "bin").mkdir()
            (root / "dist").mkdir()
            if make_jar:
                (root / "dist" / output).write_bytes(b"fixture jar bytes")
            log = root / "calls.log"
            gh = root / "bin" / "gh"
            gh.write_text(
                "#!/usr/bin/env bash\n"
                "printf '%s\\n' \"$*\" >> \"$FAKE_GH_LOG\"\n"
                "if [[ \"$1 $2\" == \"release view\" ]]; then\n"
                "  [[ \"${FAKE_RELEASE_EXISTS:-0}\" == 1 ]]\n"
                "elif [[ \"$1 $2\" == \"release create\" ]]; then\n"
                "  exit 0\n"
                "else exit 99; fi\n"
            )
            gh.chmod(0o755)
            env = os.environ.copy()
            env.update({
                "PATH": str(root / "bin") + os.pathsep + env.get("PATH", ""),
                "FAKE_GH_LOG": str(log),
                "FAKE_RELEASE_EXISTS": "1" if exists else "0",
                "GITHUB_EVENT_NAME": event,
                "GITHUB_REF": ref,
                "GITHUB_REF_NAME": ref_name,
                "GITHUB_REPOSITORY": "wickidcow/SF_FoxyMachines",
                "GITHUB_SHA": SHA,
                "VERSION": version,
                "OUTPUT_NAME": output,
            })
            result = subprocess.run(["bash", str(PUBLISH)], cwd=root, env=env,
                                    capture_output=True, text=True, check=False)
            calls = log.read_text().splitlines() if log.exists() else []
            return result, calls

    def test_manual_exact_tag_creates_new_release_once(self):
        result, calls = self.invoke()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, len(calls))
        self.assertIn("release view v1.0.2", calls[0])
        self.assertIn("release create v1.0.2 dist/SF_FoxyMachines1.0.2.jar", calls[1])
        self.assertIn("--verify-tag", calls[1])

    def test_existing_release_is_immutable(self):
        result, calls = self.invoke(exists=True)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, len(calls))
        self.assertIn("already exists", result.stderr)

    def test_main_push_cannot_publish(self):
        result, calls = self.invoke(event="push", ref="refs/heads/master", ref_name="master")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(calls)

    def test_tag_push_cannot_publish(self):
        result, calls = self.invoke(event="push")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(calls)

    def test_manual_branch_dispatch_cannot_publish(self):
        result, calls = self.invoke(ref="refs/heads/master", ref_name="master")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(calls)

    def test_manual_wrong_tag_cannot_publish(self):
        result, calls = self.invoke(ref="refs/tags/v1.0.3", ref_name="v1.0.3")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(calls)

    def test_missing_raw_jar_cannot_publish(self):
        result, calls = self.invoke(make_jar=False)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(calls)

    def test_wrong_jar_identity_cannot_publish(self):
        result, calls = self.invoke(output="SF_FoxyMachines1.0.3.jar")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(calls)

    def test_publisher_and_workflow_have_no_mutable_release_actions(self):
        source = PUBLISH.read_text()
        workflow = WORKFLOW.read_text()
        self.assertNotIn("--clobber", source + workflow)
        self.assertNotIn("delete-asset", source + workflow)
        self.assertNotIn("release upload", source + workflow)
        self.assertIn("github.event_name == 'workflow_dispatch' && startsWith(github.ref, 'refs/tags/v')", workflow)
        self.assertIn("bash scripts/publish_immutable_release.sh", workflow)


if __name__ == "__main__":
    unittest.main()
