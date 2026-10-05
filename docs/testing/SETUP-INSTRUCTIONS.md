<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Retarget — turning Advertising's own toolbox toward your goals.
Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
-->

# Environment Setup Instructions — Retarget User Testing

**Last Updated:** 2026-01-XX  
**Base Branch:** `integrate/phase2-notifications`  
**Minimum Requirements for Testing**

---

## Hardware Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| RAM | 8GB | 16GB |
| Storage | 10GB free | 20GB free (for SDK + emulator) |
| OS | Windows 10 / macOS 11+ / Ubuntu 20.04+ | Latest stable |
| Device for testing | Android device with USB debugging or emulator | Physical Android device (API 30+) |

---

## Software Prerequisites

### 1. Java Development Kit (JDK 17)

**SDKMan approach (recommended):**
```bash
# Install SDKMan if not present
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Amazon Corretto 17
sdk install java 17.0.20-amzn
sdk use java 17.0.20-amzn
```

**Alternative (system package managers):**

- **Ubuntu/Debian:**
  ```bash
  sudo apt-get update
  sudo apt-get install openjdk-17-jdk
  ```

- **macOS (Homebrew):**
  ```bash
  brew install openjdk@17
  ```

- **Windows (Chocolatey):**
  ```powershell
  choco install openjdk17
  ```

Verify installation:
```bash
java --version
# Should show: 17.x.x (Amazon Corretto or OpenJDK)
```

### 2. Android SDK

**Minimum SDK components required:**
- Platform Tools (ADB, fastboot)
- Platform: Android API 34+ (tested with API 37)
- Build Tools: 34.0.0+
- Emulator (if using virtual device)

**Installation via SDK Manager:**
```bash
# Assuming ANDROID_HOME is set
$ANDROID_HOME/tools/bin/sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" "emulator"
```

**Manual download:**
- Download from [Android Developer Site](https://developer.android.com/studio#command-tools)
- Extract to `~/Android/Sdk` (Linux/macOS) or `C:\Users\<name>\AppData\Local\Android\Sdk` (Windows)

### 3. Android Studio (Optional but helpful)

- Download from [Android Studio](https://developer.android.com/studio)
- Install for GUI-based SDK management and emulator creation
- Not required for headless builds

---

## Project Setup

### 1. Clone Repository

```bash
git clone https://github.com/jimspurgeon/retarget.git
cd retarget
git checkout integrate/phase2-notifications
```

### 2. Configure Android SDK Path

Create `local.properties` in project root:

**Windows:**
```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

**macOS/Linux:**
```properties
sdk.dir=/Users/YourName/Library/Android/sdk
# or
sdk.dir=/home/yourname/Android/Sdk
```

**Template available:** Copy `local.properties.template` to `local.properties` and edit.

### 3. SDK Version Selection

Before each build, ensure Java 17 is active:
```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java 17.0.20-amzn
```

---

## Build & Verify

### Debug Build (for testing)

```bash
# Set Java version
source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java 17.0.20-amzn

# Build APK
./gradlew assembleDebug

# Check APK exists and inspect size
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

**Expected output:**
- APK size: ~85MB (due to creative asset images in debug build)
- Build time: 3-10 minutes (first build), 30-60 seconds (cached)

### Run Tests

```bash
./gradlew test
```

**Expected output:**
- BUILD SUCCESSFUL
- All unit tests pass (cached from previous runs if unchanged)

### Release Build (optional, smaller APK)

```bash
./gradlew assembleRelease
```

Note: Requires signing configuration (see `secrets.properties.template`).

---

## Emulator Setup

### Create Virtual Device (AVD)

**Via command line:**
```bash
# List available system images
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list | grep "system-images"

# Create AVD with Pixel 4 API 34
echo "no" | $ANDROID_HOME/tools/bin/avdmanager create avd \
  --name "test-pixel-34" \
  --package "system-images;android-34;google_apis;x86_64" \
  --device "pixel_4"
```

**Via Android Studio:**
1. Tools → AVD Manager
2. Create Virtual Device
3. Select hardware (Pixel 4 recommended)
4. Select system image (x86_64, API 34+)
5. Finish

### Launch Emulator

```bash
# List emulators
$ANDROID_HOME/tools/bin/avdmanager list avd

# Start emulator
$ANDROID_HOME/emulator/emulator -avd test-pixel-34 -no-boot-anim -no-audio

# Wait for boot (approx 60-120 seconds)
adb wait-for-device
adb shell getprop sys.boot_completed
# Should return: 1
```

### Configure for Testing

```bash
# Disable battery optimization (prevents WorkManager drift)
adb shell dumpsys battery set level 100

# Enable Wi-Fi (some features may require network connectivity)
adb shell svc wifi enable

# Allow background processes
adb shell settings put global background_process_limit 4
```

---

## Physical Device Setup

### Enable Developer Options

1. Settings → About Phone
2. Tap "Build Number" 7 times
3. Return to Settings → System → Developer Options

### Enable USB Debugging

1. Developer Options → USB Debugging → Enable
2. Confirm RSA key fingerprint dialog when connecting

### Connect via USB

```bash
# Verify device detected
adb devices
# Should show: XXXXXXXX    device

# Install app
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Wireless Debugging (Android 11+)

```bash
# On device: Developer Options → Wireless Debugging → Enable
# Pair with code, then:
adb pair 192.168.1.XXX:XXXX
adb connect 192.168.1.XXX:XXXX
```

---

## Environment Sanity Checks

### 1. Notification Permission State

**Android 13+ (API 33+):**
- `POST_NOTIFICATIONS` permission is NOT granted by default
- User must explicitly grant via system dialog
- Verify: App should NOT show notification permission dialog on first launch

**Check:**
```bash
adb shell appops get com.retarget.advertapp POST_NOTIFICATIONS
# Should show: DENIED or ALLOWED (based on user choice)
```

### 2. Quiet Hours Enforcement

**Default setting:** 22:00–07:00 (10 PM to 7 AM)

**Verification:**
1. Set device time to within quiet hours
2. Enable notifications for a goal
3. Wait for scheduled slot time
4. No notification should fire

**Location:** Settings screen → Quiet Hours toggle

### 3. Dashboard Pacing Accuracy

**Expected behavior:**
- Pacing counters show "X/Y today" per channel
- Increment after each exposure
- Reset at midnight (local time)

**Verification:**
1. Launch app after fresh install
2. Navigate to dashboard
3. All channels should show "0/X today"
4. After notification fires, counter increments to "1/X today"

---

## Test User Profile

**Recommended test account setup:**
- Use preset campaigns only (Hydration, Fresh Air, More Fruit, More Vegetables)
- Avoid custom goal names that might leak personal information
- Clear app data between test sessions to maintain clean state

**Reset procedures:**
```bash
# Soft reset (clear app data)
adb shell pm clear com.retarget.advertapp

# Hard reset (wipe emulator)
adb emu kill
emulator -avd <name> -wipe-data
```

---

## Troubleshooting

### Build Fails

**Issue:** `SDK location not found`
```bash
# Solution: Create local.properties with correct sdk.dir path
echo "sdk.dir=C:/Users/Jim/AppData/Local/Android/Sdk" > local.properties
```

**Issue:** `Could not resolve all dependencies`
```bash
# Solution: Check internet connection, retry build
./gradlew clean assembleDebug --refresh-dependencies
```

**Issue:** `SDK 34 not found`
```bash
# Solution: Install platform
$ANDROID_HOME/tools/bin/sdkmanager "platforms;android-34"
```

### Emulator Issues

**Issue:** Slow boot (>5 min)
```bash
# Solution: Use cold boot with -no-snapshot-save
emulator -avd <name> -no-snapshot-save -no-boot-anim
```

**Issue:** ADB disconnects
```bash
# Solution: Restart ADB server
adb kill-server
adb start-server
adb devices
```

### Notification Not Firing

**Possible causes:**
1. Battery optimization killing WorkManager → Disable for app
2. App in deep sleep → Bring to foreground
3. Quiet hours active → Change device time or disable quiet hours

**Debug commands:**
```bash
# Check scheduled jobs
adb shell dumpsys job_scheduler | grep retarget

# Check notification log
adb shell dumpsys notification | grep -A 20 com.retarget.advertapp
```

---

## Security Considerations

**Never commit:**
- `local.properties` (contains SDK path, potentially personal)
- `secrets.properties` (contains API keys)
- `app/*.keystore` or `app/*.jks` (signing keys)
- Real device IDs or personal data from exports

**Git status check before committing:**
```bash
git status
git diff --staged
```

---

## Version Compatibility Matrix

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 17.0.20-amzn | Required |
| Android Gradle Plugin | 9.4.1 | Latest stable |
| Kotlin | 2.4.20 | Compose + serialization |
| Target SDK | 34 | Android 14 |
| Min SDK | 26 | Android 8.0 Oreo |
| Gradle | 9.8.0 | Wrapper included |

---

## Support Resources

- **Documentation:** `docs/plans/PHASE2-CAMPAIGN.md`
- **Testing Guide:** `docs/testing/USER-TESTING-SCRIPT.md`
- **Contributor Guide:** `AGENTS.md`, `DEVELOPMENT.md`
- **Issues:** https://github.com/jimspurgeon/retarget/issues

---

*End of Setup Instructions*
