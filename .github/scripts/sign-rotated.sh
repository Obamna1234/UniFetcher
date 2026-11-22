#!/usr/bin/env bash
# Android signing key rotation (APK Signature Scheme v3), Nov 2026. Based on SEVBY's tested
# android/sign-rotated.sh.
#
# Re-signs the release APKs in <dir> (already signed by Gradle with the ORIGINAL key) so that:
#   - Android 7-8 (SDK 24-27) still see the original key (v1/v2 signatures), and
#   - Android 9+ (SDK 28+) see the NEW key plus a proof of rotation signed by the original key (v3),
#     so the update installs over the existing app without uninstalling. The original key is marked
#     --set-installed-data false: once updated, an APK signed only with the original key can't
#     update the app on Android 9+.
#
# Needs OLD_KEYSTORE / OLD_KEY_ALIAS / OLD_KEYSTORE_PASSWORD / OLD_KEY_PASSWORD and
#       NEW_KEYSTORE / NEW_KEY_ALIAS / NEW_KEYSTORE_PASSWORD (NEW_KEY_PASSWORD defaults to it).
# If no new key is set, the APKs are left exactly as Gradle signed them.
set -euo pipefail
dir=${1:?usage: sign-rotated.sh <apk folder>}
if [ -z "${NEW_KEYSTORE:-}" ] || [ ! -s "${NEW_KEYSTORE}" ] || [ -z "${OLD_KEYSTORE:-}" ] || [ ! -s "${OLD_KEYSTORE}" ]; then
  echo "Key rotation: no new key set - APKs keep the original key only."
  exit 0
fi
export NEW_KEY_PASSWORD="${NEW_KEY_PASSWORD:-$NEW_KEYSTORE_PASSWORD}"
bt=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
signer="$bt/apksigner"
lineage="${RUNNER_TEMP:-/tmp}/signing.lineage"
old=(--ks "$OLD_KEYSTORE" --ks-key-alias "$OLD_KEY_ALIAS" --ks-pass env:OLD_KEYSTORE_PASSWORD --key-pass env:OLD_KEY_PASSWORD)
new=(--ks "$NEW_KEYSTORE" --ks-key-alias "$NEW_KEY_ALIAS" --ks-pass env:NEW_KEYSTORE_PASSWORD --key-pass env:NEW_KEY_PASSWORD)
"$signer" rotate --out "$lineage" --old-signer "${old[@]}" --set-installed-data false --new-signer "${new[@]}"
shopt -s nullglob
apks=("$dir"/*.apk)
[ ${#apks[@]} -gt 0 ] || { echo "::error::Key rotation: no APKs in $dir"; exit 1; }
for apk in "${apks[@]}"; do
  out="${apk%.apk}.rotated.apk"
  "$signer" sign "${old[@]}" --next-signer "${new[@]}" --lineage "$lineage" \
    --rotation-min-sdk-version 28 --out "$out" "$apk"
  "$signer" verify --min-sdk-version 24 "$out"
  mv "$out" "$apk"
done
rm -f "$dir"/*.idsig "$lineage" 2>/dev/null || true
echo "Key rotation: ${#apks[@]} APK(s) signed with the original key (Android 7-8) and the new key (Android 9+)."
