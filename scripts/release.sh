#!/usr/bin/env bash
# Releases the version on main, then moves main to the next one.
#
#   scripts/release.sh            # release HEAD
#   scripts/release.sh --dry-run  # say what it would do, change nothing
#
# Run it once the build from HEAD is the one sent to Google Play. It
#   1. checks that HEAD is main, clean, in step with GitHub, green on every
#      check, and that its version is not tagged yet;
#   2. tags HEAD v<version>;
#   3. moves appVersionName (app/build.gradle.kts) and the release name in
#      docs/play-store.md to the next version, the next patch in the same month
#      or <year>.<month>.0 in a new one, as "[Build] Version <next>";
#   4. pushes the tag and that commit together, and opens a draft GitHub
#      release for the notes.
#
# main is then never on a released version, and versionCode, derived from the
# name, is already above the one Play has.
set -euo pipefail

DRY_RUN=false
case "${1-}" in
  "") ;;
  --dry-run) DRY_RUN=true ;;
  *)
    echo "usage: scripts/release.sh [--dry-run]" >&2
    exit 2
    ;;
esac

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
GRADLE=app/build.gradle.kts
PLAY=docs/play-store.md

fail() {
  echo "$*" >&2
  exit 1
}

# yyyy.mm.patch -> yyyymm * 1000 + patch, the rule in app/build.gradle.kts.
version_code() {
  echo "$1" | awk -F. '{ printf "%d", ($1 * 100 + $2) * 1000 + $3 }'
}

[ "$(git branch --show-current)" = main ] || fail "Not on main."
[ -z "$(git status --porcelain)" ] || fail "Uncommitted changes: commit or stash them first."
git fetch --quiet --tags origin
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] ||
  fail "main is not in step with origin/main: pull or push first."
SHA=$(git rev-parse HEAD)
SHORT=$(git rev-parse --short HEAD)

VERSION=$(sed -n 's/^val appVersionName = "\(.*\)"$/\1/p' "$GRADLE")
echo "$VERSION" | grep -Eq '^[0-9]{4}\.[0-9]{2}\.[0-9]{1,3}$' ||
  fail "appVersionName in $GRADLE is not yyyy.mm.patch: ${VERSION:-missing}"
grep -q "^| Release name | $VERSION (versionCode $(version_code "$VERSION")) |$" "$PLAY" ||
  fail "The release name in $PLAY is not $VERSION: bring it in step first."

TAG="v$VERSION"
NEXT=$(echo "$VERSION" | awk -F. -v year="$(date +%Y)" -v month="$(date +%m)" '{
  if ($1 == year && $2 + 0 == month + 0) printf "%d.%02d.%d", $1, $2, $3 + 1
  else printf "%d.%02d.0", year, month
}')

if git rev-parse --quiet --verify "refs/tags/$TAG" >/dev/null; then
  fail "$TAG already exists: $VERSION is released. Bump appVersionName instead."
fi

# Every check on HEAD finished and passed.
CHECKS=$(gh api --paginate "repos/{owner}/{repo}/commits/$SHA/check-runs" \
  -q '.check_runs[] | "\(.status) \(.conclusion) \(.name)"')
[ -n "$CHECKS" ] || fail "No checks on $SHORT yet: wait for CI."
NOT_GREEN=$(echo "$CHECKS" | grep -Ev '^completed (success|skipped|neutral) ' || true)
[ -z "$NOT_GREEN" ] || fail "Not every check on $SHORT passed:
$NOT_GREEN"

echo "Release $VERSION (versionCode $(version_code "$VERSION")): tag $TAG on $(git log -1 --format='%h %s')"
echo "Then main moves to $NEXT (versionCode $(version_code "$NEXT")): \"[Build] Version $NEXT\""
if $DRY_RUN; then
  echo "Dry run: nothing changed."
  exit 0
fi

git tag -a "$TAG" -m "Splouch for Android $TAG"

sed "s/^val appVersionName = \"$VERSION\"$/val appVersionName = \"$NEXT\"/" "$GRADLE" >"$GRADLE.tmp"
mv "$GRADLE.tmp" "$GRADLE"
sed "s/^| Release name | $VERSION (versionCode [0-9]*) |$/| Release name | $NEXT (versionCode $(version_code "$NEXT")) |/" \
  "$PLAY" >"$PLAY.tmp"
mv "$PLAY.tmp" "$PLAY"
grep -q "^val appVersionName = \"$NEXT\"$" "$GRADLE" || fail "Could not set appVersionName to $NEXT."
grep -q "^| Release name | $NEXT " "$PLAY" || fail "Could not set the release name in $PLAY to $NEXT."
git commit --quiet -m "[Build] Version $NEXT" -- "$GRADLE" "$PLAY"

# One push for both, so main never has the tag without the bump.
git push --quiet --atomic origin main "refs/tags/$TAG"

gh release create "$TAG" --verify-tag --draft --generate-notes --title "$TAG" >/dev/null ||
  echo "Pushed, but the draft release failed: create it on GitHub from $TAG." >&2
echo "Released $TAG; main is now $NEXT."
echo "Edit and publish the draft: $(gh browse --no-browser --releases 2>/dev/null || echo 'the Releases page on GitHub')"
