package com.example.notescanner

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.camera.view.PreviewView
import android.widget.FrameLayout
import androidx.compose.runtime.saveable.rememberSaveable
import android.util.Log
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import android.graphics.BitmapFactory
import com.example.notescanner.data.InvoiceStore
import com.example.notescanner.model.InvoiceRecord
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

class MainActivity : ComponentActivity() {
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        try {
            setContent {
                // Wrap in Material3 theme to ensure required CompositionLocals exist on all devices
                // Provide a slightly boxier shape theme across the app
                var darkMode by rememberSaveable { mutableStateOf(false) }
                val lightColors = lightColorScheme()
                val darkColors = darkColorScheme()
                MaterialTheme(
                    colorScheme = if (darkMode) darkColors else lightColors,
                    shapes = Shapes(
                        small = RoundedCornerShape(8.dp),
                        medium = RoundedCornerShape(12.dp),
                        large = RoundedCornerShape(16.dp)
                    )
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NoteScannerApp(
                            darkTheme = darkMode,
                            onToggleTheme = { darkMode = !darkMode }
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            // If something fails very early on certain devices, show the error instead of instant crash
            android.util.Log.e("MainActivity", "Startup crash", t)
            setContent {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Villa við ræsingu forrits:")
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(t.message ?: t.toString())
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun NoteScannerApp(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val store = remember { InvoiceStore(context) }
    var ocrResult by remember { mutableStateOf("") }
    var isCameraStarted by rememberSaveable { mutableStateOf(false) }
    var lastPhotoPath by rememberSaveable { mutableStateOf("") }
    var lastExcelPath by rememberSaveable { mutableStateOf("") }
    var cameraPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var askedPermissionOnce by rememberSaveable { mutableStateOf(false) }
    var showSettingsPrompt by rememberSaveable { mutableStateOf(false) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var showOverview by rememberSaveable { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var records by remember { mutableStateOf(store.loadAll()) }
    fun refreshRecords() { records = store.loadAll() }

    // Expose ImageCapture from CameraPreview so the main Scan button can trigger capture
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }

    fun handleScannedImageUri(imageUri: Uri) {
        val today = getTodayIso()
        val monthKey = getCurrentMonthKey()
        val monthFolder = File(context.filesDir, monthKey)
        if (!monthFolder.exists()) monthFolder.mkdirs()
        val destFile = File(monthFolder, "nota_${System.currentTimeMillis()}.jpg")
        try {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            // OCR and persist
            OcrUtil.recognizeTextFromImage(context, destFile) { text ->
                ocrResult = text
                val parsed = OcrUtil.parse(text)
                val vendor = parsed.vendor ?: text.lines().firstOrNull()?.take(64) ?: "Óþekkt"
                val vatExtract = OcrUtil.extractVatAmounts(text)
                val amount = vatExtract.total ?: parsed.amount ?: 0.0
                val vat = vatExtract.tax ?: parsed.vat ?: 0.0

                val excelFile = File(context.filesDir, "reikningar.xlsx")
                ExcelUtil.appendToExcel(
                    listOf(destFile.name, today, monthKey, vendor, String.format("%.2f", amount), String.format("%.2f", vat)),
                    excelFile
                )
                lastExcelPath = excelFile.absolutePath

                val record = InvoiceRecord(
                    id = System.currentTimeMillis(),
                    date = today,
                    monthKey = monthKey,
                    vendor = vendor,
                    amount = amount,
                    vat = vat,
                    imagePath = destFile.absolutePath
                )
                store.add(record)
                refreshRecords()
            }
        } catch (e: Exception) {
            Log.e("NoteScanner", "Failed to save scanned image", e)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            isCameraStarted = true
            showSettingsPrompt = false
        } else {
            cameraPermissionDenied = true
            // If the system won't show rationale anymore, it's likely permanently denied
            val shouldShow = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } ?: false
            showSettingsPrompt = !shouldShow
        }
        askedPermissionOnce = true
    }

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    val shouldShowRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } == true
    val permanentlyDenied = !hasCameraPermission && !shouldShowRationale && askedPermissionOnce

    // Launcher for ML Kit Document Scanner (beta1) using IntentSender-based flow
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val res = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uri = res?.pages?.firstOrNull()?.imageUri
            if (uri != null) {
                handleScannedImageUri(uri)
            }
        }
    }

    // Auto-request CAMERA on first open
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission && !askedPermissionOnce) {
            askedPermissionOnce = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background gradient (Compose) to avoid XML Shape not supported by painterResource
        // Equivalent to drawable/bg_invoice.xml with subtle transparency
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x26FFFFFF), // ~15% alpha white
                            Color(0x26F5F5F5),
                            Color(0x26EFEFEF)
                        )
                    )
                )
        )
        // Faint watermark image (user-provided) over the gradient but behind content
        Image(
            painter = painterResource(id = R.drawable.bg_watermark),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.08f }, // very faint
            contentScale = ContentScale.Crop
        )
        // Optional: Add a watermark later using a Compose Canvas. Avoid painterResource here to prevent
        // crashes when a non-vector XML drawable is accidentally resolved on some devices.

        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Velkomin í nótuskanna!",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                // Theme toggle
                OutlinedButton(onClick = onToggleTheme, modifier = Modifier.height(40.dp)) {
                    Text(if (darkTheme) "Ljóst" else "Dökkt")
                }
            }

            // Secondary navigation buttons
            // (Primary scan button moved to the bottom of the screen)
            Spacer(modifier = Modifier.size(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    showOverview = true; showList = false; isCameraStarted = false
                }, modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Skoða yfirlit") }
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(onClick = {
                    showList = true; showOverview = false; isCameraStarted = false
                }, modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Skoða nótur") }
            }

            if (selectedRecord != null) {
                NoteDetailScreen(record = selectedRecord!!, onBack = { selectedRecord = null })
                return@Column
            }

            if (showOverview) {
                OverviewScreen(records = records, onOpen = { selectedRecord = it }, onBack = { showOverview = false })
                return@Column
            }
            if (showList) {
                InvoiceListScreen(records = records, onOpen = { selectedRecord = it }, onBack = { showList = false })
                return@Column
            }

            if (!isCameraStarted) {
                // Permission guidance and actions before starting camera
                if (!hasCameraPermission) {
                    when {
                        permanentlyDenied || showSettingsPrompt -> {
                            Text(
                                "Forritið þarf aðgang að myndavél til að skanna nótur.",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Row(modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedButton(onClick = {
                                    // Open app settings
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }) { Text("Opna stillingar") }
                                Spacer(modifier = Modifier.size(8.dp))
                                Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Reyna aftur") }
                            }
                        }
                        shouldShowRationale -> {
                            Text(
                                "Við notum myndavélina til að taka mynd af nótu og vinna úr henni.",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Veita leyfi fyrir myndavél")
                            }
                        }
                        cameraPermissionDenied -> {
                            Text(
                                "Vantar leyfi fyrir myndavél. Leyfðu og prófaðu aftur.",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Biðja um leyfi")
                            }
                        }
                    }
                }
            } else {
                CameraPreview(
                    onImageCaptureReady = { cap -> imageCaptureRef = cap },
                    onCameraReady = { cam -> cameraRef = cam },
                    torchOn = torchEnabled
                )
            }

        if (ocrResult.isNotEmpty()) {
            Text("Niðurstaða OCR:", modifier = Modifier.padding(top = 16.dp))
            Text(ocrResult)
        }

        if (lastExcelPath.isNotEmpty()) {
            Button(onClick = {
                // Senda Excel skjal í email
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                intent.type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Reikningar")
                intent.putExtra(android.content.Intent.EXTRA_TEXT, "Hér eru reikningar í Excel.")
                val excelFile = File(lastExcelPath)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    excelFile
                )
                intent.putExtra(android.content.Intent.EXTRA_STREAM, uri)
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
            }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Senda Excel í email")
            }
        }
        }

        // Bottom primary scan button — only on the home screen (no lists/overview/detail)
        if (!showList && !showOverview && selectedRecord == null) Button(
            onClick = {
                showList = false
                showOverview = false
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    return@Button
                }

                // Launch ML Kit Document Scanner instead of manual CameraX capture
                val options = GmsDocumentScannerOptions.Builder()
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .setGalleryImportAllowed(true)
                    .setPageLimit(1)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                    )
                    .build()
                val scanner: GmsDocumentScanner = GmsDocumentScanning.getClient(options)
                val act = activity
                if (act != null) {
                    scanner.getStartScanIntent(act)
                        .addOnSuccessListener { intentSender: IntentSender ->
                            val request = IntentSenderRequest.Builder(intentSender).build()
                            scannerLauncher.launch(request)
                        }
                        .addOnFailureListener { err ->
                            Log.w("NoteScanner", "Document scanner failed to get intent, falling back to CameraX", err)
                            // Fallback to CameraX preview flow
                            isCameraStarted = true
                        }
                } else {
                    // If no activity available, fallback
                    isCameraStarted = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .fillMaxWidth()
                .height(56.dp)
        ) { Text("Skanna nótu") }

        // Flash toggle overlay when in CameraX fallback
        if (isCameraStarted && cameraRef?.cameraInfo?.hasFlashUnit() == true) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 110.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (torchEnabled) "Flash: ON" else "Flash: OFF", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = {
                    torchEnabled = !torchEnabled
                }, modifier = Modifier.height(36.dp)) {
                    Text(if (torchEnabled) "Slökkva" else "Kveikja")
                }
            }
        }

        // Footer logo area refined (larger, adaptive to theme, reduced whitespace)
        val logoBmp: android.graphics.Bitmap? = remember {
            try { context.assets.open("logo.png").use { android.graphics.BitmapFactory.decodeStream(it) } }
            catch (_: Exception) { null }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    if (darkTheme) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (logoBmp != null) {
                Image(
                    bitmap = logoBmp.asImageBitmap(),
                    contentDescription = "Logo",
                    modifier = Modifier.size(width = 96.dp, height = 28.dp)
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(width = 96.dp, height = 28.dp)
                )
            }
        }
    }
}

private fun getTodayIso(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date())
}

private fun getCurrentMonthKey(): String {
    val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
    return sdf.format(Date())
}

@Composable
fun InvoiceListScreen(records: List<InvoiceRecord>, onOpen: (InvoiceRecord) -> Unit, onBack: () -> Unit = {}) {
    val grouped = remember(records) { records.groupBy { it.monthKey }.toSortedMap(compareByDescending { it }) }
    LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
        item(key = "back-header") {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack) { Text("Til baka") }
            }
        }
        grouped.forEach { (month, list) ->
            item(key = "header-$month") {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Text(
                        text = month,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
            items(list, key = { it.id }) { rec ->
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onOpen(rec) }) {
                    val thumb = remember(rec.imagePath) { loadThumbnail(rec.imagePath, 128) }
                    if (thumb != null) {
                        Image(bitmap = thumb.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp))
                    } else {
                        BoxPlaceholder()
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rec.vendor)
                        Text("Dagsetning: ${rec.date}")
                        Text("Upphæð: ${String.format("%.2f", rec.amount)} kr  |  VSK: ${String.format("%.2f", rec.vat)} kr")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BoxPlaceholder() {
    Surface(tonalElevation = 1.dp) { Spacer(modifier = Modifier.size(64.dp)) }
}

private fun loadThumbnail(path: String, maxDim: Int): android.graphics.Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val (w, h) = opts.outWidth to opts.outHeight
        if (w <= 0 || h <= 0) return null
        val maxSide = maxOf(w, h)
        var sample = 1
        while ((maxSide / sample) > maxDim) sample *= 2
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts2)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun OverviewScreen(
    records: List<InvoiceRecord>,
    onOpen: (InvoiceRecord) -> Unit,
    onBack: () -> Unit
) {
    // Filters state
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("overview_prefs", 0) }
    var monthFilterExpanded by remember { mutableStateOf(false) }
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(prefs.getString("month", null)) }
    var vendorQuery by rememberSaveable { mutableStateOf(prefs.getString("vendor", "") ?: "") }
    var sortBy by rememberSaveable { mutableStateOf(SortBy.valueOf(prefs.getString("sort", SortBy.DATE_DESC.name)!!)) }

    val months = remember(records) { records.map { it.monthKey }.distinct().sortedDescending() }
    val filtered = remember(records, selectedMonth, vendorQuery) {
        records.filter { rec ->
            (selectedMonth == null || rec.monthKey == selectedMonth) &&
            (vendorQuery.isBlank() || rec.vendor.contains(vendorQuery, ignoreCase = true))
        }
    }
    val sorted = remember(filtered, sortBy) {
        when (sortBy) {
            SortBy.VENDOR_ASC -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.vendor })
            SortBy.VENDOR_DESC -> filtered.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.vendor })
            SortBy.AMOUNT_ASC -> filtered.sortedBy { it.amount }
            SortBy.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
            SortBy.DATE_ASC -> filtered.sortedBy { it.date }
            SortBy.DATE_DESC -> filtered.sortedByDescending { it.date }
        }
    }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        // Controls panel wrapped in a Card for a cleaner, boxy look
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Back + Controls row
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(0.7f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Til baka") }
                    Spacer(Modifier.size(8.dp))
                    // Month dropdown
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { monthFilterExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(selectedMonth ?: "Mánuður: Allir")
                        }
                        DropdownMenu(expanded = monthFilterExpanded, onDismissRequest = { monthFilterExpanded = false }) {
                            DropdownMenuItem(text = { Text("Allir mánuðir") }, onClick = {
                                selectedMonth = null; monthFilterExpanded = false
                            })
                            months.forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = {
                                    selectedMonth = m; monthFilterExpanded = false
                                })
                            }
                        }
                    }

                    Spacer(Modifier.size(8.dp))

                    // Vendor filter
                    OutlinedTextField(
                        value = vendorQuery,
                        onValueChange = { vendorQuery = it },
                        label = { Text("Leita seljanda") },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(Modifier.size(8.dp))

                // Sort buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            sortBy = when (sortBy) {
                                SortBy.VENDOR_ASC -> SortBy.VENDOR_DESC
                                SortBy.VENDOR_DESC -> SortBy.VENDOR_ASC
                                else -> SortBy.VENDOR_ASC
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Raða: Seljandi") }

                    Spacer(Modifier.size(8.dp))

                    OutlinedButton(
                        onClick = {
                            sortBy = when (sortBy) {
                                SortBy.AMOUNT_ASC -> SortBy.AMOUNT_DESC
                                SortBy.AMOUNT_DESC -> SortBy.AMOUNT_ASC
                                else -> SortBy.AMOUNT_DESC
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Raða: Upphæð") }

                    Spacer(Modifier.size(8.dp))

                    OutlinedButton(
                        onClick = {
                            sortBy = when (sortBy) {
                                SortBy.DATE_ASC -> SortBy.DATE_DESC
                                SortBy.DATE_DESC -> SortBy.DATE_ASC
                                else -> SortBy.DATE_DESC
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Raða: Dagsetning") }
                }

                Spacer(Modifier.size(8.dp))

                // Export CSV
                Row(modifier = Modifier.fillMaxWidth()) {
                    ExportCsvButton(
                        sorted,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )
                }
            }
        }

        // Table header
        Spacer(Modifier.size(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text("Dagsetning", modifier = Modifier.weight(1.1f))
            Text("Seljandi", modifier = Modifier.weight(2.0f))
            Text("Upphæð", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("VSK", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    HorizontalDivider()

        // Rows
        LazyColumn {
            items(sorted, key = { it.id }) { rec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onOpen(rec) }
                ) {
                    Text(rec.date, modifier = Modifier.weight(1.1f))
                    Text(rec.vendor, modifier = Modifier.weight(2.0f), maxLines = 1)
                    Text(String.format("%.2f", rec.amount) + " kr", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(String.format("%.2f", rec.vat) + " kr", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                HorizontalDivider()
            }
        }
    }

    // persist
    LaunchedEffect(selectedMonth, vendorQuery, sortBy) {
        prefs.edit().putString("month", selectedMonth).putString("vendor", vendorQuery).putString("sort", sortBy.name).apply()
    }
}

private enum class SortBy { VENDOR_ASC, VENDOR_DESC, AMOUNT_ASC, AMOUNT_DESC, DATE_ASC, DATE_DESC }

// CSV export helper
private fun exportCsv(list: List<InvoiceRecord>, context: android.content.Context) {
    val csv = buildString {
        appendLine("id,date,month,vendor,amount,vat,imagePath")
        list.forEach { r ->
            appendLine("${r.id},${r.date},${r.monthKey},\"${r.vendor.replace("\"", "\"\"")}\",${String.format("%.2f", r.amount)},${String.format("%.2f", r.vat)},${r.imagePath}")
        }
    }
    val file = File(context.filesDir, "notur.csv")
    file.writeText(csv, Charsets.UTF_8)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Nótur CSV")
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

@Composable
private fun ExportCsvButton(list: List<InvoiceRecord>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Button(
        onClick = { exportCsv(list, context) },
        modifier = modifier,
        shape = RoundedCornerShape(14.dp)
    ) { Text("Flytja út CSV") }
}

@Composable
fun NoteDetailScreen(record: InvoiceRecord, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { InvoiceStore(context) }
    var vendor by rememberSaveable(record.id) { mutableStateOf(record.vendor) }
    var date by rememberSaveable(record.id) { mutableStateOf(record.date) }
    var amount by rememberSaveable(record.id) { mutableStateOf(record.amount.toString()) }
    var vat by rememberSaveable(record.id) { mutableStateOf(record.vat.toString()) }
    val bmp = remember(record.imagePath) { loadThumbnail(record.imagePath, 2048) }

    // Compute VAT breakdown from the image on demand to show 11%/24% sundurliðun
    var vatExtraction by rememberSaveable(record.id) { mutableStateOf<OcrUtil.VatExtraction?>(null) }
    var vatLoading by rememberSaveable(record.id) { mutableStateOf(false) }
    LaunchedEffect(record.id) {
        // Kick off OCR once when opening detail
        if (!vatLoading && vatExtraction == null) {
            vatLoading = true
            try {
                val f = File(record.imagePath)
                OcrUtil.recognizeTextFromImage(context, f) { text ->
                    val ext = OcrUtil.extractVatAmounts(text)
                    vatExtraction = ext
                    vatLoading = false
                }
            } catch (t: Throwable) {
                vatLoading = false
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Til baka") }
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = {
                val file = File(record.imagePath)
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }, modifier = Modifier.weight(1f)) { Text("Deila mynd") }
        }

        Spacer(Modifier.size(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                val file = File(record.imagePath)
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }, modifier = Modifier.weight(1f)) { Text("Opna mynd") }

            Spacer(Modifier.size(8.dp))

            OutlinedButton(onClick = {
                store.deleteById(record.id)
                onBack()
            }, modifier = Modifier.weight(1f)) { Text("Eyða") }
        }

        Spacer(Modifier.size(12.dp))
        OutlinedTextField(value = vendor, onValueChange = { vendor = it }, label = { Text("Seljandi") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Dagsetning (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Upphæð") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(value = vat, onValueChange = { vat = it }, label = { Text("VSK") }, modifier = Modifier.fillMaxWidth())

        // VSK sundurliðun — sýna 24% og 11%, nettó og heild ef fáanlegt
        Spacer(Modifier.size(12.dp))
        Text("VSK sundurliðun", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        if (vatLoading && vatExtraction == null) {
            Text("Les VSK úr mynd…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        vatExtraction?.let { ext ->
            val r24 = ext.rates[24.0] ?: 0.0
            val r11 = ext.rates[11.0] ?: 0.0
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("VSK 24%")
                    Text(String.format("%.2f kr", r24))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("VSK 11%")
                    Text(String.format("%.2f kr", r11))
                }
                Spacer(Modifier.size(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Nettó")
                    Text(String.format("%.2f kr", ext.subtotal ?: (record.amount - record.vat)))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Heild")
                    Text(String.format("%.2f kr", ext.total ?: record.amount))
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Button(onClick = {
            val updated = record.copy(
                vendor = vendor,
                date = date,
                amount = amount.toDoubleOrNull() ?: record.amount,
                vat = vat.toDoubleOrNull() ?: record.vat
            )
            store.update(updated)
            onBack()
        }) { Text("Vista") }

        Spacer(Modifier.size(12.dp))
        if (bmp != null) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
        } else {
            Text("Mynd fannst ekki: ${record.imagePath}")
        }
    }
}

@Composable
fun CameraPreview(
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraReady: (androidx.camera.core.Camera) -> Unit,
    torchOn: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraObj by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    AndroidView(factory = { ctx ->
        val frameLayout = FrameLayout(ctx)
        val previewView = PreviewView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        frameLayout.addView(previewView)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                cameraObj = cam
                onCameraReady(cam)
            } catch (exc: Exception) {
                Log.w("CameraPreview", "Back camera binding failed, trying front", exc)
                try {
                    cameraProvider.unbindAll()
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    val cam = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    cameraObj = cam
                    onCameraReady(cam)
                } catch (exc2: Exception) {
                    Log.e("CameraPreview", "Front camera binding also failed", exc2)
                }
            }
        }, ContextCompat.getMainExecutor(ctx))
        // Expose ImageCapture to caller when available
        imageCapture?.let { onImageCaptureReady(it) }
        frameLayout
    }, modifier = Modifier.height(300.dp))

    // Torch control side-effect
    LaunchedEffect(torchOn, cameraObj) {
        try {
            cameraObj?.cameraControl?.enableTorch(torchOn)
        } catch (t: Throwable) {
            Log.w("CameraPreview", "Torch toggle failed", t)
        }
    }
}
