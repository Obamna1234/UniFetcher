# UniFetcher how-to guide

Simple step-by-step help for installing, updating and releasing UniFetcher. Everything happens on your phone and on github.com. No computer needed.

**Contents**

1. [Install UniFetcher from Releases](#1-install-unifetch-from-releases)
2. [Allow "Install unknown apps"](#2-allow-install-unknown-apps)
3. [Install a test build](#3-install-a-test-build)
4. [Update UniFetcher](#4-update-unifetch)
5. [If an update fails to install](#5-if-an-update-fails-to-install)
6. [Copy an error log and report a bug](#6-copy-an-error-log-and-report-a-bug)
7. [Set up the signing key (one time)](#7-set-up-the-signing-key-one-time)
8. [Release a new version](#8-release-a-new-version)
9. [App lock and privacy settings](#9-app-lock-and-privacy-settings)
10. [Saving to an SD card or USB drive, and temporary files](#10-saving-to-an-sd-card-or-usb-drive-and-temporary-files)
11. [Cookies, sessions and why a download failed](#11-cookies-sessions-and-why-a-download-failed)

---

## 1. Install UniFetcher from Releases

1. On your phone, open **https://github.com/Obamna1234/UniFetcher/releases/latest** in Chrome or Samsung Internet.
2. Scroll down to **Assets** and tap to expand it if needed.
3. Tap the file ending in **`-arm64-v8a.apk`**. This fits almost every phone from the last several years, including the Samsung Galaxy A55.
   - Older or unusual phone? Tap **`-universal.apk`** instead. It works everywhere but is bigger.
4. When the download finishes, tap **Open** (or find it in **My Files → Downloads**).
5. If your phone asks about installing unknown apps, follow [section 2](#2-allow-install-unknown-apps), then come back.
6. Tap **Install**. When it's done, tap **Open**.

> If Google Play Protect shows a warning, that's normal for apps not from the Play Store. Tap **More details → Install anyway**.

## 2. Allow "Install unknown apps"

Android only installs apps from places you've allowed. You only need to do this once per app you install from (your browser or My Files).

**When the pop-up appears during install:**

1. Tap **Settings** on the pop-up.
2. Turn on **Allow from this source**.
3. Press back. The install screen comes back.

**Or set it up beforehand (Samsung):**

1. Open **Settings → Security and privacy → More security settings → Install unknown apps**.
   (On some phones: **Settings → Apps → ⋮ menu → Special access → Install unknown apps**.)
2. Tap **Chrome** (or **Samsung Internet**, or **My Files**).
3. Turn on **Allow from this source**.

You can turn it off again after installing if you like.

## 3. Install a test build

Every time new code is pushed, GitHub builds a test version of UniFetcher. Test builds have a separate app ID, so they install as **"UniFetcher Debug"** next to the normal app and don't touch its data.

1. Sign in to github.com on your phone (test builds can only be downloaded when signed in).
2. Open **https://github.com/Obamna1234/UniFetcher/actions**.
3. Tap the newest run named **Build** that has a **green tick ✅**.
4. Scroll down to **Artifacts** and tap the file ending in **`-arm64-v8a.apk`** (or **`-universal.apk`** for other phones).
5. When it has downloaded, tap **Open** (or find it in **My Files → Downloads**), then **Install**.

> Until the signing key is set up ([section 7](#7-set-up-the-signing-key-one-time)), each test build is signed differently. To install a newer test build, first uninstall the old "UniFetcher Debug" (long-press its icon → **Uninstall**). After the key is set up, newer test builds install straight over older ones.

## 4. Update UniFetcher

**From inside the app:** UniFetcher checks this repository's Releases page for updates (Settings → About → Auto update). When an update is found, tap **Update**, then **Install**.

**By hand:** follow [section 1](#1-install-unifetch-from-releases) again with the newer APK. Android installs it over the old version and **keeps all your data**, as long as it's an official release from this repository.

## 5. If an update fails to install

| What you see | What to do |
|---|---|
| **"App not installed"** or **"conflicts with an existing package"** | The installed copy was signed with a different key (for example an old test build, or a copy built somewhere else). Uninstall UniFetcher, then install the new APK. ⚠️ Uninstalling deletes UniFetcher's settings and history; downloaded videos in your Downloads folder stay. |
| **"App not installed"** and it's the same release you already have, or older | Android won't install an older or identical version over a newer one. Download the latest release instead. |
| **"There was a problem parsing the package"** | The download was incomplete or you picked a file for a different phone type. Delete it, download again, and try the **universal** APK. |
| **Nothing happens / install button greyed out** | Check [section 2](#2-allow-install-unknown-apps), and make sure you have at least 500 MB free storage. |
| **Play Protect blocked it** | Tap **More details → Install anyway**. |

Still stuck? [Report a bug](#6-copy-an-error-log-and-report-a-bug) and say which step failed.

## 6. Copy an error log and report a bug

**Copy the log in UniFetcher:**

- **A download failed:** tap the failed download in the list and choose **Copy report**.
- **The app crashed:** a crash screen appears. Tap **Copy and exit**.
- **Your phone details:** go to **Settings → About** and tap **Version**. Your app version and phone info are copied.
- **Need a screenshot?** UniFetcher blocks screenshots by default. Turn off **Settings → Privacy & security → Hide from recent apps and screenshots** while you take it, then turn it back on.

**Report it on GitHub:**

1. Open **https://github.com/Obamna1234/UniFetcher/issues** (sign in first).
2. Tap **New issue → Bug report**.
3. Describe what you did and what went wrong.
4. In **Error log**, long-press and **Paste** what you copied.
5. ⚠️ Before submitting, delete anything private: cookies, passwords, or links to private videos. Issues on this repository are public.
6. Tap **Submit new issue**.

## 7. Set up the signing key (one time)

Release APKs must be "signed" with a private key so Android knows updates really come from you. Every release must use **the same key**, otherwise updates can't install over the old version.

The key is stored in **GitHub Secrets**: hidden values that only the build can use and nobody (not even you) can read back. It is never saved in the code.

**What you need:** a keystore file encoded as text (base64), its password, the key's name (alias) and the key's password. Claude can create these for you in a session and give you the four values.

**Add them to GitHub:**

1. Open **https://github.com/Obamna1234/UniFetcher/settings/secrets/actions**
2. Tap **New repository secret**, and add each of these (name exactly as shown, then paste the value):

   | Name | Value |
   |---|---|
   | `UNIFETCH_KEYSTORE_BASE64` | the long base64 text of the keystore |
   | `UNIFETCH_KEYSTORE_PASSWORD` | the keystore password |
   | `UNIFETCH_KEY_ALIAS` | the key name |
   | `UNIFETCH_KEY_PASSWORD` | the key password |

3. **Back up the keystore file and passwords** somewhere private and safe, such as a password manager. If they're lost, future versions can't update the installed app. You'd have to uninstall and lose UniFetcher's data.

⚠️ Never put the keystore or passwords in the code, an issue, or any public place.

## 8. Release a new version

Pushing a **version tag** tells GitHub to build signed APKs and publish them on the Releases page with automatic release notes. The tag also sets the app's version number, so there's nothing else to edit.

Version tags look like **`v1.2.3`**: raise the last number for fixes, the middle number for new features. For a test release, add `-beta.1` (e.g. `v1.3.0-beta.1`); it's marked as a pre-release.

**Do it on github.com:**

1. Make sure the newest **Build** run on the [Actions page](https://github.com/Obamna1234/UniFetcher/actions) has a green tick ✅, and the signing key is set up ([section 7](#7-set-up-the-signing-key-one-time)).
2. Open **https://github.com/Obamna1234/UniFetcher/releases/new**
3. Tap **Choose a tag**, type the new version (e.g. `v0.1.0`) and tap **Create new tag: v0.1.0 on publish**. Leave the target as **main**.
4. Type anything in the title (it will be replaced) and tap **Publish release**.
5. Open the [Actions page](https://github.com/Obamna1234/UniFetcher/actions). A run named **Release** starts. It takes about 10–20 minutes.
6. When it shows a green tick ✅, the APKs appear on the release automatically, ready to install ([section 1](#1-install-unifetch-from-releases)).

> Publishing the release on github.com creates the tag, which starts the Release build. The build then fills in the APKs and notes on that same release.

**If the Release run fails (red ✗):** tap it to see which step failed. "Missing GitHub Secrets" means [section 7](#7-set-up-the-signing-key-one-time) isn't finished. Fix it, delete the release *and* its tag (on the [Tags page](https://github.com/Obamna1234/UniFetcher/tags)), and try again.

## 9. App lock and privacy settings

Everything here is in **Settings → Privacy & security**.

**Turn on the app lock**

1. Turn on **App lock**.
2. Choose **PIN** or **Pattern**, enter it, then enter it again to confirm.
3. Optional: turn on **Unlock with fingerprint** and touch the sensor once to confirm it works.
4. Optional: tap **Lock after leaving the app** to choose how long UniFetcher can be in the background before it asks again.

⚠️ **If you forget your PIN or pattern**, there is no reset. The only way back in is to clear UniFetcher's storage (phone **Settings → Apps → UniFetcher → Storage → Clear storage**). That erases UniFetcher's history and settings, but your downloaded videos stay where they are.

**Other settings**

- **Hide from recent apps and screenshots** (on by default): UniFetcher shows as a blank card in the app switcher and can't be screenshotted.
- **Discreet notifications:** notifications only say "UniFetcher", with no video titles or links, and don't show on the lock screen. To turn download notifications off completely, use **Settings → General → Download notification**.
- **Quick wipe:** clears download history, saved cookies and logins, built-in browser data, cached thumbnails and temporary files. Your downloaded files, settings and app lock are kept. It won't run while downloads are in progress.

## 10. Saving to an SD card or USB drive, and temporary files

**Where temporary files go.** While a video downloads, its unfinished video and audio pieces (and then the step that joins them) are kept in a temporary folder. A long video can need several GB. Each download has its own folder, which is deleted when the download finishes or when you remove it from the list. A stopped download keeps its pieces so it can carry on later, even after a restart.

| Setting | Temporary files go to |
|---|---|
| **Same as download folder** (default) | Phone downloads: **Internal storage → Download → UniFetcher → tmp**. SD card / USB drive downloads: UniFetcher's own folder on that drive (`Android/data/com.unifetch.app/files/tmp`) |
| **Internal storage** | **Internal storage → Download → UniFetcher → tmp** |
| **SD card or USB drive** | UniFetcher's own folder on the card or drive. Only offered when one is plugged in |

Change it in **Settings → Download directory → Temporary files location**, which also shows the free space for each choice.

- UniFetcher's own folder on a card or drive needs **no extra permission**. Android deletes it if you uninstall UniFetcher, which is fine for temporary files.
- If temporary files are on a different storage from the download folder, each finished video is moved across. That takes a little longer and briefly needs space on both.

**Save downloads to an SD card or USB drive**

1. Plug in the card or drive (for USB, through a USB-C adapter) and check it shows in your file manager. Use **exFAT** for drives: FAT32 can't hold files over 4 GB.
2. In UniFetcher, go to **Settings → Download directory → SD card or USB drive folder**.
3. Open the drive in the picker, choose or create a folder, then tap **Use this folder → Allow**. The switch turns on.
4. Check **Temporary files location**. If it says the phone doesn't let UniFetcher keep temporary files on the drive (some phones, such as some Xiaomi models with USB drives), choose **Internal storage**. Finished videos still go to the drive, but each one needs room on the phone while it downloads.

**When downloads pause**

UniFetcher pauses the queue instead of letting downloads fail, shows a banner and notification, and carries on by itself once it's fixed:

- **Storage almost full:** less free space than your limit where temporary files go. Free up space, or change **Pause downloads when free space is below** (2 GB, 1 GB, 500 MB or Off).
- **SD card or USB drive removed:** plug it back in.
- **Choose where temporary files go:** see step 4 above.

A single video that's too big for the free space fails with a message saying how much it needs. Free space (or change the location), then tap **Retry**. If the drive was unplugged as a video finished, the video is kept safely in the temporary folder: plug the drive back in and tap **Retry** to copy it across.

## 11. Cookies, sessions and why a download failed

**Automatic site sessions (on by default).** Some sites only let a real browser download their videos. When a site refuses a download (errors like "HTTP Error 403" or "410"), UniFetcher quietly opens that link in a hidden built-in browser, lets the site set its cookies, and tries once more. Sites that needed this are remembered, so their later downloads use the session straight away. While a download uses cookies, its progress shows a 🍪.

- You don't need to add cookies by hand anymore. If you have, and they stop working, UniFetcher tries an automatic session once.
- Logins and "verify you are human" checks are **never** done for you.
- See or delete saved sessions: **Settings → Network → Sites with saved sessions** (**Forget** one site, or **Forget all**). Turn the feature off with **Automatic site sessions**.
- Cookies stay on your phone. Quick wipe deletes them.

**"Needs you": human checks and logins.** If a site wants you to tick "I'm not a robot", solve a CAPTCHA or log in, its videos move to the **Needs you** tab on the download screen (you also get one notification). Everything else keeps downloading.

1. Open the **Needs you** tab. Each site shows how many videos are waiting.
2. Tap **Open** next to a site. It opens in UniFetcher's built-in browser.
3. Pass the check (or log in), then tap **Done**. That site's videos retry by themselves.

Nothing expires while videos wait there, even for days or after the app is closed: UniFetcher keeps only the page links, and the session is made fresh when you tap Done. UniFetcher never solves these checks for you.

**Why a download failed.** A failed download shows one reason:

| Reason | What it means | What to try |
|---|---|---|
| Site not supported | yt-dlp can't read this site (or the site just changed). Cookies won't help | Wait for a yt-dlp update (automatic) |
| Blocked by site security | A CAPTCHA, bot check or too many requests | Open the site in a browser and pass the check, try another VPN server, or try later |
| Session rejected | A browser session was tried and refreshed, and the site still refused | The site may block VPNs or this kind of app. Try another VPN server |
| Needs login | The video needs an account | Log in on the cookies screen (Settings → Network → Cookies), then Retry |
| Video unavailable | Removed, private or blocked in your region | Nothing to fix. Check it plays in a browser |
| DRM: not supported | The video is copy-protected | UniFetcher doesn't support DRM |
| Network problem | The connection dropped or timed out | Check your connection or VPN, then Retry |
| Not enough space | Not enough room for temporary files | See section 10 |

**Copy diagnostic.** Tap a failed download and choose **Copy diagnostic**. It copies the reason, whether cookies were used, the steps UniFetcher tried, and yt-dlp's error, with cookie values and link tokens removed, ready to paste into a bug report.
