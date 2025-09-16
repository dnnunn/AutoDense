#!/usr/bin/env bash
set -euo pipefail
APP="AutoDense.app"
IDENTITY="Developer ID Application: YOUR NAME (TEAMID)"
BUNDLE_ID="com.betterdairy.autodense"

# 1) Sign embedded binaries (llama-server etc.)
find "$APP" -type f -perm +111 -print0 2>/dev/null | while IFS= read -r -d '' f; do
  codesign --force --options runtime --sign "$IDENTITY" "$f"
done || true

# 2) Sign the .app
codesign --deep --force --options runtime --sign "$IDENTITY" "$APP"
codesign --verify --deep --strict "$APP"

# 3) Notarize
# Update credentials before use
xcrun notarytool submit "$APP" --apple-id "you@appleid.com" \
  --team-id "TEAMID" --password "app-specific-password" --wait

# 4) Staple
xcrun stapler staple "$APP"
