#!/usr/bin/env bash
set -euo pipefail
SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
mkdir -p "$SDK_ROOT"
if [ ! -d "$SDK_ROOT/cmdline-tools/latest" ]; then
  mkdir -p "$SDK_ROOT/cmdline-tools"
  curl -sSLo /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
  unzip -q /tmp/cmdline-tools.zip -d "$SDK_ROOT/cmdline-tools"
  mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm /tmp/cmdline-tools.zip
fi
export ANDROID_SDK_ROOT="$SDK_ROOT"
set +o pipefail
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/build-tools/34.0.0:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
if [[ -n "${GITHUB_PATH:-}" ]]; then
  echo "$ANDROID_SDK_ROOT/platform-tools" >> "$GITHUB_PATH"
  echo "$ANDROID_SDK_ROOT/build-tools/34.0.0" >> "$GITHUB_PATH"
  echo "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin" >> "$GITHUB_PATH"
fi
set -o pipefail
echo "sdk.dir=$ANDROID_SDK_ROOT" > "Fucker/local.properties"
