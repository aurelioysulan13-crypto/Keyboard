# Custom Keyboard (Android)

A custom on-screen keyboard, like Gboard / SwiftKey / Samsung Keyboard.

**Features:** QWERTY, numbers/symbols (2 pages), emoji panel, word suggestions with
next-word prediction, auto-correct for common typos, auto-capitalize, double-space period,
shift + caps-lock, hold-to-repeat backspace, hold a top-row letter for its number,
slide along the space bar to move the cursor, globe key to switch keyboards, haptics/sound,
light / dark / system theme, 5 accent colors, adjustable key height, learns your words
(stored on-device only). The app has **no internet permission**.

Min Android: 7.0 (API 24). Package: `com.custom.keyboard`.

---------------------------------------------------------------------------
## 1. Get the APK  (pick ONE way)

### A) Android Studio (most reliable)
1. Install Android Studio (free): https://developer.android.com/studio
2. Unzip this project -> File > Open -> choose the `CustomKeyboard` folder.
3. Wait for Gradle sync to finish (first time downloads things; needs internet).
   - If Studio asks which Gradle to use, accept "Use Gradle wrapper / recommended" (Gradle 8.9).
4. Menu: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Click "locate" in the popup. File: `app/build/outputs/apk/debug/app-debug.apk`
6. Copy that APK to your phone and open it (allow "Install unknown apps" if asked).

### B) GitHub (no installs - builds in the cloud)
1. Create a free GitHub account and a new repository.
2. Upload ALL files from this folder, **including the hidden `.github` folder**.
   (If the upload skips `.github`: in GitHub click Add file > Create new file, type the name
   `.github/workflows/build-apk.yml` and paste the contents of that file.)
3. Open the repo's **Actions** tab -> "Build APK" -> wait for the green check (about 3-5 min).
   (If nothing runs: Actions > Build APK > Run workflow.)
4. Open the finished run, scroll to **Artifacts**, download `CustomKeyboard-APK`
   (a zip containing `app-debug.apk`). Unzip, copy the APK to your phone, install it.

### C) Command line
Needs JDK 17 + Android SDK (platform 34) + Gradle 8.9:
    gradle assembleDebug

---------------------------------------------------------------------------
## 2. Turn it on as your keyboard
1. Open **Custom Keyboard** from your app drawer.
2. Step 1 - tap **Open keyboard settings**, switch **Custom Keyboard** ON, accept the warning
   (Android shows it for every third-party keyboard).
3. Step 2 - tap **Choose Custom Keyboard** and select it.
4. Tap the "Try it out" box and type.

(Manual path: Settings > System > Languages & input > On-screen keyboard > Manage keyboards.)

## 3. Customizing
- Words: edit `app/src/main/assets/words.txt` (one word per line, most common first).
- Auto-correct pairs: `FIXES` in `Suggester.java`.
- Layouts: `lettersLayout()`, `symbols1Layout()`, `symbols2Layout()` in `KeyboardService.java`.
- Colors: `loadColors()` in `KeyboardService.java`.

## 4. Notes
- Debug-signed APK: fine for personal use. For Play Store you would build a signed release.
- Password fields: suggestions, auto-correct and learning are switched off automatically.
