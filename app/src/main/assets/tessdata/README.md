# Tesseract Language Data Files

This directory should contain the trained data files for Tesseract OCR:

## Required Files:
- `isl.traineddata` - Icelandic language data
- `eng.traineddata` - English language data (fallback)

## Download Sources:
1. **Official Tesseract repository:**
   - https://github.com/tesseract-ocr/tessdata_best
   - Download `isl.traineddata` and `eng.traineddata`

2. **Alternative (smaller files):**
   - https://github.com/tesseract-ocr/tessdata
   - Smaller but less accurate files

## Installation:
1. Download the `.traineddata` files
2. Place them in this `app/src/main/assets/tessdata/` directory
3. The app will automatically copy them to the device on first run

## File Sizes:
- `isl.traineddata`: ~15-20MB (best quality)
- `eng.traineddata`: ~15-20MB (best quality)

## Note:
The current implementation includes fallback handling if these files are not found,
but for optimal Icelandic text recognition, these files should be included.

## Build Impact:
Adding these files will increase the APK size by ~30-40MB, but significantly
improve OCR accuracy for Icelandic receipts.