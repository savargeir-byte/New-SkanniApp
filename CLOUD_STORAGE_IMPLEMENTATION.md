# Firebase Cloud Storage fyrir Reikninga Myndir

## Yfirlit

Þetta kerfi vistar allar myndir af reikningum í Firebase Cloud Storage og tengir þær við skannaða reikninginn.

## Eiginleikar

### 1. **Sjálfvirk Mynd Vistun** ☁️

Þegar reikningur er skannaður:
- Myndin er vistuð **bæði** staðbundið OG í skýinu
- Firebase Storage sér um hámarks stærð og geymslu
- Myndin er tengd við reikninginn með ID

### 2. **Sjálfvirk Rammagreining** 📐

EdgeDetectionUtil greinir sjálfkrafa ramm reikninga:
- **Sobel Edge Detection** - Finnur sterk brún
- **Contour Detection** - Greinir jaðra
- **Rectangle Approximation** - Finnur 4 hornpunkta
- **Quality Scoring** - Metur gæði myndar

**Status messag**es:
- 🟢 "Frábært! Tilbúið að skanna" (quality > 0.8, confidence > 0.7)
- 🟡 "Góð gæði - haldið kyrru" (quality > 0.6, confidence > 0.5)
- 🟠 "Reikningur greindur - bætið ljós"
- 🔴 "Farið nær og bætið ljós"

### 3. **Firebase Storage Uppbygging** 📂

```
receipts/
  ├── {userId}/
  │   ├── receipt_{invoiceId}_{timestamp}.jpg
  │   ├── receipt_{invoiceId}_{timestamp}.jpg
  │   └── ...
  ├── {userId}/
  │   └── ...
```

**Hver notandi:**
- Hefur sína eigin möppu
- Myndir eru private (aðeins notandi getur séð sínar myndir)
- Sjálfvirk eyðing þegar reikningur er eytt

## Notkun

### Uppsetning

1. **Bæta við Firebase Storage í `build.gradle`:**
```gradle
implementation 'com.google.firebase:firebase-storage-ktx'
implementation 'com.google.firebase:firebase-firestore-ktx'
```

2. **Initialize í MainActivity:**
```kotlin
val storageManager = FirebaseStorageManager(context)
```

### Vista Mynd

```kotlin
// Frá Bitmap
val imageUrl = storageManager.uploadReceiptImage(bitmap, invoiceId)

// Frá URI
val imageUrl = storageManager.uploadReceiptImage(imageUri, invoiceId)
```

### Sækja Mynd

```kotlin
val localFile = storageManager.downloadReceiptImage(imageUrl)
```

### Eyða Mynd

```kotlin
val success = storageManager.deleteReceiptImage(imageUrl)
```

## Edge Detection API

### Greina Ramm

```kotlin
// Frá ImageProxy (Camera)
val result = EdgeDetectionUtil.detectReceiptEdges(imageProxy)

// Frá Bitmap
val result = EdgeDetectionUtil.detectReceiptEdges(bitmap)
```

### EdgeDetectionResult

```kotlin
data class EdgeDetectionResult(
    val hasReceiptDetected: Boolean,    // Er reikningur greindur?
    val qualityScore: Float,            // 0.0 - 1.0
    val confidence: Float,              // 0.0 - 1.0
    val cropRect: Rect?,                // Rect til að skera
    val edgePoints: List<Point>         // 4 hornpunktar
)
```

### Helper Functions

```kotlin
result.getStatusMessage()  // "🟢 Frábært! Tilbúið að skanna"
result.getEdgeColor()      // Color.Green
result.shouldAutoCapture() // true ef tilbúið
```

## Dæmi: Full Integration

```kotlin
@Composable
fun EnhancedInvoiceScannerScreen() {
    val storageManager = remember { FirebaseStorageManager(context) }
    var edgeResult by remember { mutableStateOf<EdgeDetectionResult?>(null) }
    
    // Camera preview með edge detection
    CameraPreview(
        onFrameAnalyzed = { imageProxy ->
            // Greina ramm í real-time
            edgeResult = EdgeDetectionUtil.detectReceiptEdges(imageProxy)
        },
        onCapture = { bitmap ->
            scope.launch {
                // Vista í Firebase
                val imageUrl = storageManager.uploadReceiptImage(
                    bitmap, 
                    invoiceId
                )
                
                // Vista invoice með mynd URL
                saveInvoice(invoice.copy(imagePath = imageUrl))
            }
        }
    )
    
    // Show edge overlay
    edgeResult?.let { result ->
        if (result.hasReceiptDetected) {
            EdgeOverlay(
                rect = result.cropRect,
                color = result.getEdgeColor(),
                statusMessage = result.getStatusMessage()
            )
            
            // Auto-capture ef gæði eru góð
            if (result.shouldAutoCapture()) {
                autoCapture()
            }
        }
    }
}
```

## Firebase Security Rules

Bæta við í Firebase Console:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /receipts/{userId}/{allPaths=**} {
      // Allow read/write aðeins fyrir eigin myndir
      allow read, write: if request.auth != null && request.auth.uid == userId;
      
      // Max file size: 10MB
      allow write: if request.resource.size < 10 * 1024 * 1024;
      
      // Only images
      allow write: if request.resource.contentType.matches('image/.*');
    }
  }
}
```

## Performance

### Image Optimization

Myndir eru sjálfkrafa:
- **Resized** ef > 1920px
- **Compressed** til 85% JPEG quality
- **Optimized** fyrir fljótt upload

### Caching

```kotlin
// Cache myndir staðbundið
val localFile = File(context.cacheDir, "receipt_cache_${invoiceId}.jpg")
if (localFile.exists()) {
    // Use cached image
} else {
    // Download from Firebase
    storageManager.downloadReceiptImage(imageUrl)
}
```

### Storage Limits

```kotlin
// Skoða storage notkun
val sizeInBytes = storageManager.getUserStorageSize()
val sizeInMB = sizeInBytes / 1024 / 1024

if (sizeInMB > 100) {
    // Warn user about storage limit
}
```

## Edge Detection Algorithm

### Step 1: Grayscale Conversion
```kotlin
gray = 0.299*R + 0.587*G + 0.114*B
```

### Step 2: Gaussian Blur (Noise Reduction)
```
kernel = [1 2 1]
        [2 4 2]  / 16
        [1 2 1]
```

### Step 3: Sobel Edge Detection
```
Gx = [-1 0 1]    Gy = [-1 -2 -1]
     [-2 0 2]         [ 0  0  0]
     [-1 0 1]         [ 1  2  1]

magnitude = sqrt(Gx² + Gy²)
```

### Step 4: Contour Detection
- Find edge pixels (threshold > 128)
- Group nearby points (< 50px apart)
- Approximate as rectangle

### Step 5: Quality Assessment
```kotlin
aspectScore = 0.4 * aspectRatio  // Receipts are taller
sizeScore = 0.4 * areaRatio      // Not too small/big
positionScore = 0.2 * centerDistance  // Prefer center

finalScore = aspectScore + sizeScore + positionScore
```

## UI Integration

### Real-time Edge Overlay

```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    edgeResult?.cropRect?.let { rect ->
        drawRect(
            color = edgeResult.getEdgeColor(),
            topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
            size = Size(rect.width().toFloat(), rect.height().toFloat()),
            style = Stroke(width = 4.dp.toPx())
        )
    }
}
```

### Status Indicator

```kotlin
Row {
    Icon(
        when {
            quality > 0.8f -> Icons.Default.CheckCircle
            quality > 0.6f -> Icons.Default.Warning
            else -> Icons.Default.Error
        },
        tint = edgeResult.getEdgeColor()
    )
    Text(edgeResult.getStatusMessage())
}
```

## Testing

### Test Edge Detection

```kotlin
@Test
fun testEdgeDetection() {
    val bitmap = loadTestReceipt()
    val result = EdgeDetectionUtil.detectReceiptEdges(bitmap)
    
    assertTrue(result.hasReceiptDetected)
    assertTrue(result.qualityScore > 0.5f)
    assertNotNull(result.cropRect)
}
```

### Test Upload/Download

```kotlin
@Test
suspend fun testFirebaseStorage() {
    val bitmap = createTestBitmap()
    
    // Upload
    val url = storageManager.uploadReceiptImage(bitmap, "test_invoice")
    assertNotNull(url)
    
    // Download
    val file = storageManager.downloadReceiptImage(url!!)
    assertNotNull(file)
    assertTrue(file.exists())
    
    // Delete
    val deleted = storageManager.deleteReceiptImage(url)
    assertTrue(deleted)
}
```

## Troubleshooting

### Myndir uploadast ekki

**Athuguð:**
1. Firebase Auth - er notandi innskráður?
2. Storage Rules - eru þær rétt settar upp?
3. Internet connection - er nettenging?
4. File size - er myndin < 10MB?

**Debug:**
```kotlin
timber.log.Timber.plant(timber.log.Timber.DebugTree())
// Check logs with tag: FirebaseStorageManager
```

### Edge detection virkar ekki

**Athuguð:**
1. Ljós - nægt ljós á reikningnum?
2. Focus - er myndavél í fókus?
3. Distance - of nær eða of langt?
4. Angle - of hallað?

**Bæta:**
- Auka lighting með torch
- Hold camera steady
- Center receipt in frame

## Future Enhancements

1. **ML-based Edge Detection** - Nota TensorFlow Lite
2. **Perspective Correction** - Leiðrétta hallann sjálfkrafa
3. **OCR on Cloud** - Keyra OCR í skýinu fyrir betri nákvæmni
4. **Batch Upload** - Uploada margar myndir í einu
5. **Offline Mode** - Cache og sync síðar
6. **Image Gallery** - Skoða allar myndir í app

## Resources

- [Firebase Storage Docs](https://firebase.google.com/docs/storage)
- [Edge Detection Theory](https://en.wikipedia.org/wiki/Edge_detection)
- [Sobel Operator](https://en.wikipedia.org/wiki/Sobel_operator)
- [OpenCV Contours](https://docs.opencv.org/master/d4/d73/tutorial_py_contours_begin.html)

## Version Info

- **Implemented:** This commit
- **Dependencies:** 
  - Firebase Storage KTX
  - Firebase Firestore KTX
  - Timber logging
- **Minimum SDK:** 26 (Android 8.0)

## Credits

- Edge detection based on classical computer vision algorithms
- Firebase integration using official SDK
- Timber logging for better debugging

---

**Athugið:** Þetta kerfi þarf Firebase project setup með Storage enabled. Sjá Firebase Console fyrir setup instructions.
