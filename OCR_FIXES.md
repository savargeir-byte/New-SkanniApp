buls app# OCR Number Reading Fixes

## Problem
The receipt scanner was not reading numbers correctly from invoices. This was due to several issues:

### Root Causes Identified

1. **Number Format Parsing Issues**
   - Previous implementation removed ALL commas and dots before parsing
   - No proper handling of different number formats (European vs US)
   - Incorrect decimal place adjustment heuristics
   - No distinction between thousands separators and decimal separators

2. **Insufficient Pattern Matching**
   - Limited regex patterns didn't cover all invoice formats
   - Missing common Icelandic invoice terminology
   - No prioritization of total amounts vs item prices

3. **Lack of Debugging Information**
   - No logging to understand what was being detected
   - Difficult to diagnose OCR failures

## Solutions Implemented

### 1. Smart Number Parser (`parseIcelandicNumber`)

Created a sophisticated number parser that handles:

```kotlin
- 1234 (no separators)
- 1,234 (comma as thousands)
- 1.234 (dot as thousands)
- 1234.56 (dot as decimal)
- 1234,56 (comma as decimal)
- 1.234,56 (European: dot=thousands, comma=decimal)
- 1,234.56 (US: comma=thousands, dot=decimal)
- 1 234 (space as thousands)
```

**Logic:**
- Counts comma, dot, and space occurrences
- Determines which separator is decimal vs thousands based on:
  - Position (rightmost is usually decimal)
  - Digit count after separator (≤2 means decimal)
  - Multiple occurrences (means thousands separator)
- Applies sanity checks for Icelandic currency (usually whole króna)

### 2. Enhanced Pattern Matching

Added comprehensive regex patterns in priority order:

**High Priority (Most Specific):**
- `samtals: 1234 kr` (total with label)
- `til greiðslu: 1234 kr` (amount to pay)
- `að greiða: 1234 kr` (amount to pay)

**Medium Priority:**
- Card payment patterns (kort, debet, kredit)
- ISK currency patterns
- Various total/sum keywords

**Low Priority (Fallback):**
- Generic `1234 kr` patterns
- Patterns without "kr" suffix

**Improvement:** Now captures amounts with spaces: `[0-9., ]+` instead of `[0-9.,]+`

### 3. Multiple Amount Handling

Instead of returning the first match:
- Collects ALL potential amounts from text
- Filters out unrealistic values (>10M kr)
- Returns the LARGEST amount (typically the total)
- This prioritizes totals over individual item prices

### 4. Comprehensive Logging

Added detailed logging at every step:
- Raw OCR text preview
- Pattern matching attempts and results
- Number parsing steps and intermediate values
- Final selected amounts and confidence scores

**Log Tags:**
- `IcelandicInvoiceParser` - Main parsing logic
- `InvoiceScanner` - OCR and camera operations
- `BatchOcrProcessor` - Batch scanning operations

### 5. VAT Calculation Improvements

- Uses same smart number parser for VAT amounts
- Added more VAT keywords (mvsk, vsk without percentage)
- Falls back to 24% calculation if no explicit VAT found

## Testing Recommendations

### Test Cases to Verify

1. **Different Number Formats:**
   ```
   - "Samtals: 1.234 kr" → 1234.0
   - "Total: 1,234 kr" → 1234.0
   - "Upphæð: 1234.56 kr" → 1234.56
   - "Til greiðslu: 5.678,90 kr" → 5678.90
   ```

2. **Edge Cases:**
   ```
   - Very large amounts (50.000 kr)
   - Small amounts (99 kr)
   - Multiple amounts on receipt
   - Amounts with OCR errors (1.2S4 detected as 1.234)
   ```

3. **Real Icelandic Receipts:**
   - Bónus receipts
   - N1 gas station receipts
   - Restaurant receipts (Nonnabiti, Subway)
   - Online store invoices

### How to Test

1. **Enable Verbose Logging:**
   - Open LogCat in Android Studio
   - Filter by tags: `IcelandicInvoiceParser`, `InvoiceScanner`
   - Look for parsing details

2. **Test Scanning:**
   ```
   - Scan a receipt
   - Check LogCat for "=== Starting Invoice Parsing ==="
   - Verify detected amounts in logs
   - Confirm final amount matches receipt
   ```

3. **Check Edge Cases:**
   - Scan receipts with multiple prices
   - Verify it selects the total, not individual items
   - Test with poor quality images
   - Test with tilted/angled receipts

## Additional Improvements Made

### Image Enhancement
The existing `ImageEnhancementUtil` is already comprehensive and includes:
- Brightness/contrast adjustment
- Sharpening
- Noise reduction  
- Adaptive thresholding
- Quality assessment

### OCR Pipeline
The batch processor now:
- Enhances images before OCR
- Retries failed scans with exponential backoff
- Processes multiple receipts concurrently (max 3)
- Provides real-time progress updates

## Expected Results

**Before:**
- Numbers like "1.234 kr" parsed as 0 or incorrect values
- Decimal points and commas caused confusion
- Missing many invoice amounts

**After:**
- Correctly handles European and US number formats
- Smart detection of thousands vs decimal separators
- Prioritizes total amounts over item prices
- Detailed logs for debugging
- Higher success rate on real receipts

## Monitoring

To verify fixes are working:

1. Check LogCat after each scan
2. Look for pattern: `Selected amount: XXX kr`
3. Verify it matches the receipt total
4. Check confidence score (should be >0.6 for good scans)

## Future Enhancements

If issues persist, consider:

1. **ML-based number recognition** - Train a model specifically for receipt numbers
2. **Post-OCR correction** - Use dictionary of common OCR errors (O→0, S→5, etc.)
3. **User confirmation** - Show detected amount and ask user to verify
4. **Receipt template matching** - Create templates for common stores
5. **Multiple OCR engines** - Fallback to Tesseract if ML Kit fails

## Configuration

No configuration changes needed. The fixes are automatic.

## Rollback

If issues occur, previous behavior can be restored by:
1. Reverting `IcelandicInvoiceParser.kt`
2. The old logic used simpler string replacement

## Version
- **Fixed in:** This commit
- **Tested on:** Android emulator and physical devices
- **ML Kit version:** 16.0.0 (text-recognition)
