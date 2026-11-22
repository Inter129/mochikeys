#!/usr/bin/env bash
# 빌드 → 설치 → IME 활성화·선택 → 테스트 화면 실행
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -q :app:assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
IME=dev.inter129.mochikeys/.ime.KeyboardService
# 설치 직후에는 시스템이 IME를 아직 모를 수 있어 잠깐 재시도한다
for i in 1 2 3 4 5 6 7 8; do adb shell ime enable "$IME" 2>&1 | grep -q "now enabled\|already enabled" && break; sleep 1; done
adb shell ime set "$IME" >/dev/null
adb shell am start -n dev.inter129.mochikeys/.debug.ImeTestActivity >/dev/null
echo "installed and selected $IME"
