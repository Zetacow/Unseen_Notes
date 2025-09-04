#!/usr/bin/env bash
set -euo pipefail
TMP_DIR=$(mktemp -d)
mkdir -p "$TMP_DIR/cmdline-tools/latest/bin"
cat <<'SCRIPT' > "$TMP_DIR/cmdline-tools/latest/bin/sdkmanager"
#!/usr/bin/env bash
echo "sdkmanager stub $@"
exit 0
SCRIPT
chmod +x "$TMP_DIR/cmdline-tools/latest/bin/sdkmanager"
cleanup() {
  rm -rf "$TMP_DIR"
  rm -f Fucker/local.properties
}
trap cleanup EXIT
ANDROID_SDK_ROOT="$TMP_DIR" ./scripts/setup-android-sdk.sh >/tmp/setup-run1.log 2>&1
ANDROID_SDK_ROOT="$TMP_DIR" ./scripts/setup-android-sdk.sh >/tmp/setup-run2.log 2>&1
echo "setup script ran twice successfully"
