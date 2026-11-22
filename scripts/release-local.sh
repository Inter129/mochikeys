#!/usr/bin/env bash
# GitHub Actions를 쓸 수 없을 때, 이 컴퓨터에서 서명된 릴리스를 빌드해 GitHub Release에 올린다.
# 사용: scripts/release-local.sh v0.1.0
# 비밀번호는 실행 중에 물어보며 화면이나 파일에 남기지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."
TAG="${1:?사용법: scripts/release-local.sh <태그, 예: v0.1.0>}"
export MOCHI_KEYSTORE="${MOCHI_KEYSTORE:-$HOME/mochikeys-release.jks}"
export MOCHI_KEY_ALIAS="${MOCHI_KEY_ALIAS:-mochikeys}"
[ -f "$MOCHI_KEYSTORE" ] || { echo "키 저장소가 없습니다: $MOCHI_KEYSTORE"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "커밋하지 않은 변경이 있습니다. 먼저 커밋하세요."; exit 1; }
git rev-parse "$TAG" >/dev/null 2>&1 || { echo "태그 $TAG 가 없습니다. 먼저 git tag $TAG 로 만드세요."; exit 1; }
[ "$(git rev-parse HEAD)" = "$(git rev-parse "$TAG^{commit}")" ] || { echo "현재 커밋이 태그 $TAG 와 다릅니다."; exit 1; }

read -r -s -p "키 저장소 비밀번호: " MOCHI_KEYSTORE_PASSWORD; echo
export MOCHI_KEYSTORE_PASSWORD
export MOCHI_KEY_PASSWORD="${MOCHI_KEY_PASSWORD:-$MOCHI_KEYSTORE_PASSWORD}"
export MOCHI_REQUIRE_SIGNING=1

./gradlew -q clean :core:test :app:assembleRelease
rm -rf dist && mkdir dist
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  cp "app/build/outputs/apk/release/app-$abi-release.apk" "dist/mochikeys-$TAG-$abi.apk"
done
(cd dist && shasum -a 256 *.apk > SHA256SUMS.txt)
gh release create "$TAG" dist/* --title "Mochi Keys $TAG" --generate-notes
echo "올렸습니다: $(gh release view "$TAG" --json url -q .url)"
