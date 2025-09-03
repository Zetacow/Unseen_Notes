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
  "platform-tools" "platforms;android-31" "build-tools;31.0.0"
set -o pipefail
echo "sdk.dir=$ANDROID_SDK_ROOT" > "Fucker/local.properties"
