#!/usr/bin/env bash
set -euo pipefail
# Requires create-dmg (brew install create-dmg)
create-dmg \
  --volname "AutoDense" \
  --window-pos 200 120 --window-size 640 400 \
  --icon-size 128 \
  --icon "AutoDense.app" 200 200 \
  --app-drop-link 440 200 \
  AutoDense.dmg \
  "packaging/resources/"
