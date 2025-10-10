# 🔨 SkanniApp Build Guide

## Prerequisites

### Required Software
- **Android Studio** - Flamingo (2022.2.1) or newer
- **JDK** - Version 8 or higher
- **Android SDK** - API 26 (Android 8.0) or higher

### Required Files
✅ `google-services.json` - Firebase configuration  
✅ `keystore.properties` - Signing configuration  
✅ `upload-keystore.jks` - Release signing keystore  

---

## 🚀 Quick Start

### 1. Clone Repository
```bash
git clone https://github.com/saeargeir-byte/New-SkanniApp.git
cd New-SkanniApp
```

### 2. Open in Android Studio
1. Open Android Studio
2. Click **File → Open**
3. Select the `New-SkanniApp` folder
4. Wait for Gradle sync to complete

### 3. Configure Firebase
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create/select your project
3. Download `google-services.json`
4. Place in `app/` directory

### 4. Build APK

#### Debug Build (Testing)
```bash
# Command line
./gradlew assembleDebug

# Or in Android Studio
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

Output: `app/build_android*/outputs/apk/debug/app-debug.apk`

#### Release Build (Production)
```bash
# Command line
./gradlew assembleRelease

# Or in Android Studio
Build → Generate Signed Bundle / APK → APK
```

Output: `app/build_android*/outputs/apk/release/app-release.apk`

---

## 🔧 Configuration

### Keystore Setup

Create `keystore.properties` in project root:
```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=upload
storeFile=../upload-keystore.jks
```

### Generate Keystore (if needed)
```bash
keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

---

## 📦 Dependencies

All dependencies are handled automatically by Gradle. Key libraries:

### Core
- Kotlin 1.9.24
- Jetpack Compose BOM 2024.02.00
- Material Design 3

### Database & Storage
- Room 2.6.1
- DataStore 1.0.0

### Camera & ML
- CameraX 1.3.4
- ML Kit Text Recognition 16.0.0

### Firebase
- Firebase Auth
- Firebase Analytics

### Utilities
- Timber 5.0.1 (Logging)
- Coroutines 1.7.3

---

## 🐛 Common Issues

### Issue: Gradle Sync Failed
**Solution:**
```bash
# Clean and rebuild
./gradlew clean
./gradlew build --refresh-dependencies
```

### Issue: Missing google-services.json
**Solution:**
1. Download from Firebase Console
2. Place in `app/` directory
3. Sync Gradle

### Issue: Signing Error
**Solution:**
1. Check `keystore.properties` exists
2. Verify paths are correct
3. Ensure keystore file exists

### Issue: Build Directory Locked (Windows)
**Solution:**
The project uses dynamic build directories to avoid Windows file locks:
```gradle
buildDir = "build_android${System.currentTimeMillis()}"
```

### Issue: Room Schema Export
**Solution:**
Room schemas are exported for version control. Location:
```
app/schemas/
```

---

## 🧪 Testing

### Run Unit Tests
```bash
./gradlew test
```

### Run Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Run Specific Test
```bash
./gradlew test --tests "InvoiceRepositoryTest"
```

---

## 📱 Running on Device

### Via USB
1. Enable **Developer Options** on Android device
2. Enable **USB Debugging**
3. Connect device via USB
4. Click **Run** in Android Studio

### Via Wireless
1. Connect device to same WiFi
2. In Android Studio: **Run → Edit Configurations**
3. Select **Deploy over network**
4. Click **Run**

---

## 🔍 Build Variants

### Debug
- Detailed logging
- Debugging enabled
- No ProGuard
- **Fast build time**

### Release
- Minimal logging
- Optimized code
- ProGuard enabled
- Code signing required
- **Smaller APK size**

---

## 📊 Build Performance

### Optimize Build Speed

#### Enable Gradle Daemon
Add to `gradle.properties`:
```properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.configureondemand=true
```

#### Increase Heap Size
```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m
```

#### Use Build Cache
```properties
android.enableBuildCache=true
org.gradle.caching=true
```

---

## 📦 Release Checklist

Before releasing to production:

- [ ] Update version code in `build.gradle`
- [ ] Update version name
- [ ] Test on multiple devices
- [ ] Verify Firebase configuration
- [ ] Check ProGuard rules
- [ ] Test signing configuration
- [ ] Review privacy policy
- [ ] Update changelog
- [ ] Test all features
- [ ] Check crash reporting
- [ ] Verify analytics
- [ ] Test in release mode
- [ ] Generate signed APK
- [ ] Test signed APK

---

## 🔐 Security

### Keystore Security
⚠️ **Never commit keystores to Git!**

Add to `.gitignore`:
```gitignore
*.jks
*.keystore
keystore.properties
google-services.json
```

### API Keys
Store sensitive keys in:
1. `local.properties` (not in Git)
2. Environment variables
3. Firebase Remote Config

---

## 📈 Version Management

### Version Format
```gradle
versionCode = 1  // Integer, increment for each release
versionName = "1.0.0"  // String, semantic versioning
```

### Semantic Versioning
- **Major** (1.0.0): Breaking changes
- **Minor** (1.1.0): New features
- **Patch** (1.0.1): Bug fixes

---

## 🛠️ Build Scripts

### Clean All
```bash
./gradlew clean
```

### Build Debug + Install
```bash
./gradlew installDebug
```

### Build Release Bundle (for Play Store)
```bash
./gradlew bundleRelease
```

### List All Tasks
```bash
./gradlew tasks
```

---

## 📚 Additional Resources

- [Android Developer Guide](https://developer.android.com/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Firebase Android Setup](https://firebase.google.com/docs/android/setup)
- [Room Database Guide](https://developer.android.com/training/data-storage/room)

---

## 🆘 Support

### Build Issues
1. Check [Common Issues](#-common-issues) above
2. Clean and rebuild project
3. Invalidate caches: **File → Invalidate Caches / Restart**
4. Check Android Studio logs

### Contact
- **Issues**: [GitHub Issues](https://github.com/saeargeir-byte/New-SkanniApp/issues)
- **Email**: support@iceveflausnir.is

---

## ✅ Build Success Indicators

After successful build, you should see:
```
BUILD SUCCESSFUL in 45s
87 actionable tasks: 87 executed
```

APK location:
```
app/build_android*/outputs/apk/debug/app-debug.apk
```

APK size should be approximately **25-35 MB**.

---

**Ready to build! 🚀**
