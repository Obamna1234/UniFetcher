#!/usr/bin/env bash
# Picks the Android signing key from the "release" environment's secrets and hands it to Gradle
# (through $GITHUB_ENV). Usage: choose-signing-key.sh required|optional
#   - only UNIFETCH_NEW_* set      -> sign with the new key (the key retired in Nov 2026 isn't used)
#   - only UNIFETCH_* set          -> sign with that key
#   - both set                     -> the new key only, unless ROTATE_FROM_OLD_KEY=true (repository
#                                     variable): then UNIFETCH_* plus rotation to UNIFETCH_NEW_* (sign-rotated.sh)
# Inputs (env): OLD_B64 OLD_PW OLD_ALIAS OLD_KPW NEW_B64 NEW_PW NEW_ALIAS
set -euo pipefail
mode="${1:-required}"
have_old=""; [ -n "${OLD_B64:-}" ] && [ -n "${OLD_PW:-}" ] && [ -n "${OLD_ALIAS:-}" ] && [ -n "${OLD_KPW:-}" ] && have_old=1
have_new=""; [ -n "${NEW_B64:-}" ] && [ -n "${NEW_PW:-}" ] && [ -n "${NEW_ALIAS:-}" ] && have_new=1
out="$RUNNER_TEMP/unifetch.jks"
# The old key was exposed: never use it while the new one exists, unless rotation is asked for explicitly
if [ -n "$have_old" ] && [ -n "$have_new" ] && [ "${ROTATE_FROM_OLD_KEY:-}" != "true" ]; then
  echo "::notice title=Signing::Old UNIFETCH_* signing secrets are still set but ignored (new key used). They can be deleted."
  have_old=""
fi
if [ -n "$have_old" ]; then
  echo "$OLD_B64" | base64 --decode > "$out"
  { echo "UNIFETCH_KEYSTORE_FILE=$out"; echo "UNIFETCH_KEYSTORE_PASSWORD=$OLD_PW"; echo "UNIFETCH_KEY_ALIAS=$OLD_ALIAS"; echo "UNIFETCH_KEY_PASSWORD=$OLD_KPW"; } >> "$GITHUB_ENV"
  if [ -n "$have_new" ]; then
    echo "$NEW_B64" | base64 --decode > "$RUNNER_TEMP/unifetch-new.jks"
    { echo "ROTATE=1"; echo "OLD_KEYSTORE=$out"; echo "OLD_KEY_ALIAS=$OLD_ALIAS"; echo "OLD_KEYSTORE_PASSWORD=$OLD_PW"; echo "OLD_KEY_PASSWORD=$OLD_KPW"
      echo "NEW_KEYSTORE=$RUNNER_TEMP/unifetch-new.jks"; echo "NEW_KEY_ALIAS=$NEW_ALIAS"; echo "NEW_KEYSTORE_PASSWORD=$NEW_PW"; } >> "$GITHUB_ENV"
    echo "Signing with UNIFETCH_* and rotating to UNIFETCH_NEW_*."
  else
    echo "Signing with UNIFETCH_*."
  fi
elif [ -n "$have_new" ]; then
  echo "$NEW_B64" | base64 --decode > "$out"
  { echo "UNIFETCH_KEYSTORE_FILE=$out"; echo "UNIFETCH_KEYSTORE_PASSWORD=$NEW_PW"; echo "UNIFETCH_KEY_ALIAS=$NEW_ALIAS"; echo "UNIFETCH_KEY_PASSWORD=$NEW_PW"; } >> "$GITHUB_ENV"
  echo "Signing with the new key (UNIFETCH_NEW_*)."
elif [ "$mode" = "required" ]; then
  echo "::error::No signing key in the 'release' environment. Add UNIFETCH_NEW_KEYSTORE_BASE64, UNIFETCH_NEW_KEY_ALIAS and UNIFETCH_NEW_KEYSTORE_PASSWORD (HOW_TO.md section 7)."
  exit 1
else
  echo "No signing key set: building unsigned."
fi
