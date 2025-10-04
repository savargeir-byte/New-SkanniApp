# v1.0.30 - VSK Calculation Fix

## 🎯 Major VSK Fix Release

### ✅ VSK Calculation Fixed
- **Fixed critical bug** where VSK showed incorrect values like '5.0' instead of proper amounts
- **Intelligent fallback calculation** now estimates 24% VSK when OCR fails
- **Example:** 39,254 kr transaction now correctly shows ~7,597 kr VSK instead of 5.0

### 🔧 Enhanced OCR
- **OCR error correction** for common misreads (24→28, 11→17, etc.)
- **Improved percentage parsing** with validation for Icelandic VAT rates (24%, 11%)
- **Enhanced debugging** with detailed logging for troubleshooting

### 📱 Release Files
- **SkanniApp-v1.0.30-debug.apk** (92.4 MB) - Debug version with logging
- **SkanniApp-v1.0.30-release.apk** (83.4 MB) - Production APK
- **SkanniApp-v1.0.30-release.aab** (68.8 MB) - Play Store bundle

### 🧮 Technical Details
- Automatic VSK detection for transactions > 100 kr
- Fallback to 24% calculation when detected VAT < 10 kr or > 50% of total
- Enhanced validation in both scanning and editing views
- Hybrid OCR system with ML Kit + Tesseract engines

### 🔄 How to Install
1. Download the **SkanniApp-v1.0.30-release.apk** for regular use
2. Or use **SkanniApp-v1.0.30-debug.apk** if you need logging for troubleshooting
3. Enable "Install from Unknown Sources" in Android settings
4. Install the APK file

### 🧪 Testing
Test with the TMC Bifreiðaverk receipt that previously showed VSK as 5.0 - it should now show the correct VAT amount of approximately 7,597 kr for the 39,254 kr total.