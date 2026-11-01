#!/usr/bin/env bash
set -euo pipefail

# Deliberately manual and tag-only. Never modify an existing GitHub release.
: "${GITHUB_EVENT_NAME:?}"
: "${GITHUB_REF:?}"
: "${GITHUB_REF_NAME:?}"
: "${GITHUB_REPOSITORY:?}"
: "${GITHUB_SHA:?}"
: "${VERSION:?}"
: "${OUTPUT_NAME:?}"

[[ "$GITHUB_EVENT_NAME" == "workflow_dispatch" ]] || { echo "Publication requires manual dispatch" >&2; exit 1; }
[[ "$VERSION" =~ ^[0-9]+(\.[0-9]+)*$ ]] || { echo "Invalid version" >&2; exit 1; }
[[ "$GITHUB_REF" == "refs/tags/v${VERSION}" && "$GITHUB_REF_NAME" == "v${VERSION}" ]] || {
  echo "Publication requires the exact existing version tag" >&2; exit 1;
}
[[ "$OUTPUT_NAME" == "SF_FoxyMachines${VERSION}.jar" ]] || { echo "Unexpected artifact name" >&2; exit 1; }
[[ "$GITHUB_SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid commit identity" >&2; exit 1; }

jar="dist/${OUTPUT_NAME}"
[[ -s "$jar" ]] || { echo "Verified raw JAR missing" >&2; exit 1; }
tag="v${VERSION}"
if gh release view "$tag" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  echo "Release $tag already exists: refusing to alter any assets" >&2
  exit 1
fi

# --verify-tag prevents gh from creating a tag implicitly.
gh release create "$tag" "$jar" --repo "$GITHUB_REPOSITORY" --verify-tag \
  --title "SF_FoxyMachines ${VERSION}" \
  --notes "FoxyMachines ${VERSION}: canonical ${OUTPUT_NAME} release."
