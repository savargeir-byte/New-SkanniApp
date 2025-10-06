# 📱 SkanniApp - Íslenskur Reikningaskanni

**SkanniApp** er þróuð app til að skanna og vinna íslenska reikninga með OCR (Optical Character Recognition) tækni. Appið notar ML Kit fyrir textagreiningu og býður upp á fjölbreytta útflutnings og greiningarmöguleika.

## ✨ Helstu Eiginleikar

### 🤖 Smart Categorization System
- **ML-based flokkun** - Sjálfvirk flokkun reikninga í útgjaldaflokka
- **Íslenskar verslanir** - Greinir verslanir eins og Bónus, Olís, Byko
- **Learning Algorithm** - Lærir af notendavali og verður betri
- **VSK greining** - Sjálfvirk VSK útreikningar (24%, 11%, 0%)

### 📊 Enhanced Export Features  
- **PDF Reports** - Fallegar skýrslur með gröfum og tölfræði
- **Excel Analysis** - Ítarleg Excel skjöl með margblöðum
- **Email Sharing** - Beint deilingu í gegnum tölvupóst
- **Monthly Reports** - Sjálfvirkar mánaðarlegar skýrslur

### 🎨 Modern UI/UX
- **Material Design 3** - Nútímaleg og notendavæn hönnun
- **Dark/Light Theme** - Sjálfvirkt þemaskipti
- **Google Authentication** - Örugg innskráning
- **Íslenska viðmót** - Fullkomlega íslenskt

### 🔒 Privacy & Security
- **Local Processing** - Öll gögn unnin á tækinu
- **No Cloud Dependency** - Virkar án internets
- **GDPR Compliant** - Öll gögn í þinni stjórn
- **IceVeflausnir** - Þróað af íslenskum aðila

## 🛠️ Technical Stack

### Android Development
- **Kotlin** - Modern Android development
- **Jetpack Compose** - Declarative UI framework
- **Material Design 3** - Latest design system
- **CameraX** - Advanced camera functionality

### ML & OCR
- **Google ML Kit** - On-device text recognition
- **Hybrid OCR** - Multiple OCR engines
- **Local ML** - No cloud dependencies
- **Icelandic Optimized** - Tailored for Icelandic text

### Database & Storage
- **Room Database** - Local SQLite database
- **DataStore** - Preferences storage
- **File Provider** - Secure file sharing

### Firebase Integration
- **Authentication** - Google Sign-In
- **Firestore** - Optional cloud sync
- **Storage** - Optional cloud backup

## 🚀 Getting Started

### Prerequisites
- **Android Studio** Arctic Fox or newer
- **JDK 21** - Required for build
- **Android SDK 34** - Target SDK
- **Git** - Version control

### Build Setup

1. **Clone repository:**
```bash
git clone https://github.com/saeargeir/SkanniApp.git
cd SkanniApp
```

2. **Build Debug APK:**
```bash
# Windows PowerShell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-debug.ps1

# Or use Android Studio / VS Code tasks
```

3. **Run on Emulator:**
```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-emulator.ps1 -Rebuild
```

### Development
- **Package Structure:** `io.github.saeargeir.skanniapp`
- **Build Tool:** Gradle 8.7 + AGP 8.5.2
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)

## 📁 Project Structure

```
app/src/main/java/io/github/saeargeir/skanniapp/
├── categorization/          # Smart categorization system
├── export/                  # PDF/Excel export features
├── firebase/               # Firebase integration
├── ocr/                    # OCR and text recognition
├── ui/                     # Compose UI components
├── cloud/                  # Cloud services (optional)
└── model/                  # Data models
```

## 🎯 Features Roadmap

### ✅ Completed Features
- [x] Google-Only Authentication
- [x] Theme System Implementation  
- [x] Enhanced Export Features
- [x] Privacy Policy & Disclaimer Integration
- [x] App Icon Design & Implementation
- [x] Smart Categorization System

### 🚧 In Development
- [ ] Analytics Dashboard
- [ ] Camera Enhancement Features
- [ ] Search and Filter System

## 🔧 Configuration

### Required Files
- `google-services.json` - Firebase configuration
- `keystore.properties` - App signing configuration
- `local.properties` - SDK paths

### Build Variants
- **Debug** - Development builds with logging
- **Release** - Production builds, signed and optimized

## 📄 License

This project is owned by **IceVeflausnir** and is provided as-is. Users are responsible for their own data and usage.

## 🤝 Contributing

This is a private project. For issues or suggestions, please contact:
- **Email:** saeargeir@gmail.com
- **GitHub:** [@saeargeir](https://github.com/saeargeir)

## 📱 Download

Coming soon to Google Play Store!

---

**Made with ❤️ in Iceland 🇮🇸**