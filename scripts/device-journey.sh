#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-artifacts
adb wait-for-device
prepare() {
  adb shell settings put system screen_off_timeout 1800000
  adb shell svc power stayon true
  adb shell input keyevent 224
  adb shell input keyevent 82
}
collect() {
  rc=$?
  trap - EXIT
  set +e
  adb shell settings put system font_scale 1.0
  adb shell dumpsys package com.jonkryl.cutledger > ci-artifacts/package.txt
  adb logcat -d -v threadtime > ci-artifacts/logcat.txt
  adb exec-out screencap -p > ci-artifacts/device-screen.png
  exit "$rc"
}
trap collect EXIT
prepare
if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 24 ]; then
  adb shell am force-stop com.google.android.apps.messaging
  adb shell pm disable-user --user 0 com.google.android.apps.messaging
fi
adb install -r ci-apks/debug/app-debug.apk
adb install -r ci-apks/androidTest/debug/app-debug-androidTest.apk
adb shell pm clear com.jonkryl.cutledger
run_tests() {
  name="$1"
  class="$2"
  prepare
  adb shell am instrument -w -r -e class "$class" com.jonkryl.cutledger.test/androidx.test.runner.AndroidJUnitRunner | tee "ci-artifacts/$name.txt"
  python3 scripts/check-instrumentation.py "ci-artifacts/$name.txt"
}
run_tests 01-journey com.jonkryl.cutledger.CutJourneyTest
adb shell am force-stop com.jonkryl.cutledger
run_tests 02-real-process-restart com.jonkryl.cutledger.RestartTest
adb shell settings put system font_scale 2.0
adb shell am force-stop com.jonkryl.cutledger
run_tests 03-large-font com.jonkryl.cutledger.LargeFontTest
adb shell settings put system font_scale 1.0
adb shell am force-stop com.jonkryl.cutledger
adb shell am start -W -n com.jonkryl.cutledger/.MainActivity
adb shell uiautomator dump /sdcard/cut-ui.xml
adb pull /sdcard/cut-ui.xml ci-artifacts/cut-ui.xml
adb exec-out screencap -p > ci-artifacts/cut-screen.png
printf 'source=%s\nrun=%s\napi=%s\n' "${SOURCE_SHA:-$GITHUB_SHA}" "$GITHUB_RUN_ID" "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" > ci-artifacts/provenance.txt
