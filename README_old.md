# SkanniApp

## Build with Java 21
- The project is configured for Java 21.
- Gradle uses JDK 21 pinned in `gradle.properties` (org.gradle.java.home).
- Android Gradle Plugin 8.5.2 + Gradle 8.7 ensure D8/R8 support for classfile 65.

### Build Debug APK
- VS Code task: "Build Debug APK (JDK21)" or run:
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-debug.ps1
```

## Run on Android Emulator
- VS Code task: "Run on Android Emulator" (builds, starts emulator, installs and launches app).
- Or manually:
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-emulator.ps1 -Rebuild
```

If no AVD name is provided, the script picks the first available AVD. Create one via Android Studio > Device Manager if needed.

## Notes
- If Gradle still prints JVM 17 for `gradlew -version`, that’s the launcher JRE; the build daemon runs with JDK 21 as pinned. Check build logs for JAVA_VERSION=21.
- If SDK components are missing, use Android Studio > SDK Manager to install Platform 34 and Build-Tools 34.0.0.
