"""멀티스테이지 허용이 외부 이미지 pin 검사를 우회하지 않는지 검증한다."""

from pathlib import Path
import unittest

from dockerfile_pins import validate


PIN = "alpine:3@sha256:" + "a" * 64


class DockerfilePinsTest(unittest.TestCase):
    def test_pinned_external_and_previous_stage(self):
        validate(f"FROM {PIN} AS media-decoder\nFROM media-decoder AS runtime\nFROM runtime")

    def test_each_external_base_requires_pin(self):
        with self.assertRaisesRegex(ValueError, "not digest-pinned"):
            validate(f"FROM {PIN} AS build\nFROM ubuntu:latest AS runtime")

    def test_forward_stage_is_not_a_pin_exemption(self):
        with self.assertRaisesRegex(ValueError, "not digest-pinned"):
            validate(f"FROM future\nFROM {PIN} AS future")

    def test_self_declared_stage_is_not_a_pin_exemption(self):
        with self.assertRaisesRegex(ValueError, "not digest-pinned"):
            validate("FROM alpine AS alpine")

    def test_stages_are_scoped_to_each_file(self):
        validate(f"FROM {PIN} AS base")
        with self.assertRaisesRegex(ValueError, "not digest-pinned"):
            validate("FROM base")

    def test_platform_case_and_continuation(self):
        validate(f"  from --platform=$BUILDPLATFORM {PIN} as BASE\n"
                 "FROM \\\n# intervening comment\n base AS runtime\n")

    def test_run_continuation_does_not_declare_stage(self):
        with self.assertRaisesRegex(ValueError, "not digest-pinned"):
            validate(f"FROM {PIN}\nRUN echo \\\nFROM {PIN} AS fake\nFROM fake")

    def test_malformed_digests_are_rejected(self):
        for reference in ("alpine", "alpine:latest", "alpine@sha256:" + "a" * 63,
                          "alpine@sha256:" + "g" * 64, PIN + "extra", "$BASE@sha256:" + "a" * 64):
            with self.subTest(reference=reference), self.assertRaisesRegex(ValueError, "not digest-pinned"):
                validate("FROM " + reference)

    def test_heredoc_cannot_forge_a_stage_declaration(self):
        with self.assertRaisesRegex(ValueError, "unsupported Dockerfile heredoc"):
            validate(f"FROM {PIN}\nRUN <<EOF\nFROM {PIN} AS alpine\nEOF\nFROM alpine")

    def test_invalid_from_is_rejected(self):
        for value in ("FROM", f"FROM {PIN} AS", f"FROM {PIN} extra tokens",
                      f"FROM --platform= {PIN}", f"FROM --other=x {PIN}"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate(value)

    def test_empty_file_and_dangling_continuation_are_rejected(self):
        for value in ("# FROM ignored", "FROM \\\n", "# escape=`\nFROM " + PIN):
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate(value)

    def test_production_dockerfiles(self):
        root = Path(__file__).resolve().parents[3]
        for value in ("infra/gcp/docker/api.Dockerfile", "infra/gcp/docker/web.Dockerfile",
                      "infra/gcp/budget-relay/Dockerfile", "apps/support-agent/Dockerfile"):
            with self.subTest(path=value):
                validate((root / value).read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
