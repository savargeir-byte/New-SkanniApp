package io.github.saeargeir.skanniapp

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.content.ContentResolver
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.github.saeargeir.skanniapp.data.InvoiceStore
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import io.github.saeargeir.skanniapp.ocr.HybridOcrUtil
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
    var lastExcelPath by rememberSaveable { mutableStateOf("") }
    var cameraPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var askedPermissionOnce by rememberSaveable { mutableStateOf(false) }
    var showSettingsPrompt by rememberSaveable { mutableStateOf(false) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var showOverview by rememberSaveable { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var records by remember { mutableStateOf(store.loadAll()) }
    var menuExpanded by remember { mutableStateOf(false) }
    // Persisted cloud folder (Drive/OneDrive/Files) using SAF tree URI
    var cloudFolderUri by rememberSaveable { mutableStateOf<String?>(
        context.getSharedPreferences("cloud_prefs", 0).getString("treeUri", null)
    ) }
    val prefsCloud = remember { context.getSharedPreferences("cloud_prefs", 0) }
    var cloudSyncStatus by remember { mutableStateOf<String?>(null) }
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
            // If user linked a cloud folder, also copy the image there via SAF
            cloudFolderUri?.let { treeStr ->
                try {
                    val treeUri = Uri.parse(treeStr)
                    val pickedDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (pickedDoc != null && pickedDoc.canWrite()) {
                        val name = destFile.name
                        val mime = "image/jpeg"
                        val existing = pickedDoc.findFile(name)
                        val target = existing ?: pickedDoc.createFile(mime, name)
                        if (target != null) {
                            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                                destFile.inputStream().use { it.copyTo(out) }
                            }
                            cloudSyncStatus = "Mynd vistað í ský: ${target.name}"
                        } else {
                            cloudSyncStatus = "Villa: Gat ekki búið til skrá í ský"
                        }
                    } else {
                        cloudSyncStatus = "Villa: Engin skrifheimild í skýjamöppu"
                    }
                } catch (e: Exception) {
                    cloudSyncStatus = "Villa við vistun í ský: ${e.message}"
                    Log.w("CloudSync", "Failed to upload image to cloud", e)
                }
            }
            // Enhanced OCR with hybrid Tesseract + ML Kit for better Icelandic recognition
            HybridOcrUtil.recognizeTextHybrid(context, destFile, HybridOcrUtil.OcrEngine.AUTO) { hybridResult ->
                ocrResult = hybridResult.text
                Log.d("OCR", "Hybrid OCR completed with ${hybridResult.engine} (confidence: ${hybridResult.confidence})")
                
                val parsed = OcrUtil.parse(hybridResult.text)
                val vendor = parsed.vendor ?: hybridResult.text.lines().firstOrNull()?.take(64) ?: "Óþekkt"
                val invNo = parsed.invoiceNumber
                
                // Use enhanced VAT extraction based on the OCR engine used
                val vatExtract = HybridOcrUtil.extractVATFromHybridResult(hybridResult)
                val amount = vatExtract.total ?: parsed.amount ?: 0.0
                var vat = vatExtract.tax ?: parsed.vat ?: 0.0
                
                // Intelligent VAT fallback calculation if OCR failed to extract correct VAT
                if (amount > 100.0 && (vat < 10.0 || vat > amount * 0.5)) {
                    // VSK seems wrong - use intelligent estimation
                    // For 24% VAT: if total is 39254, then net = 39254/1.24 = 31656, VSK = 39254-31656 = 7598
                    val estimated24 = kotlin.math.round((amount - (amount / 1.24)) * 100) / 100  // 24% VAT, rounded
                    val estimated11 = kotlin.math.round((amount - (amount / 1.11)) * 100) / 100  // 11% VAT, rounded
                    
                    // Use 24% as default for most business transactions
                    vat = estimated24
                    Log.w("MainActivity", "OCR VSK seems incorrect ($vat), using 24% estimation: $estimated24")
                }
                
                val net = vatExtract.subtotal ?: (amount - vat)

                val excelFile = File(context.filesDir, "reikningar.xlsx")
                ExcelUtil.appendToExcel(
                    // Order: ReikningsNr, Fyrirtæki, Dagsetning, Mánuður, Nettó, VSK, Heild, Skrá
                    listOf(
                        invNo ?: "",
                        vendor,
                        today,
                        monthKey,
                        String.format("%.2f", net),
                        String.format("%.2f", vat),
                        String.format("%.2f", amount),
                        destFile.name
                    ),
                    excelFile
                )
                lastExcelPath = excelFile.absolutePath

                // Also sync Excel file to cloud if connected
                cloudFolderUri?.let { treeStr ->
                    try {
                        val treeUri = Uri.parse(treeStr)
                        val pickedDoc = DocumentFile.fromTreeUri(context, treeUri)
                        pickedDoc?.takeIf { it.canWrite() }?.let { doc ->
                            val excelName = "reikningar.xlsx"
                            val excelMime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            val existing = doc.findFile(excelName)
                            val target = existing ?: doc.createFile(excelMime, excelName)
                            target?.let { targetFile ->
                                context.contentResolver.openOutputStream(targetFile.uri)?.use { out ->
                                    excelFile.inputStream().use { it.copyTo(out) }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("CloudSync", "Failed to upload Excel to cloud", e)
                    }
                }

                val record = InvoiceRecord(
                    id = System.currentTimeMillis(),
                    date = today,
                    monthKey = monthKey,
                    vendor = vendor,
                    amount = amount,
                    vat = vat,
                    imagePath = destFile.absolutePath,
                    invoiceNumber = invNo
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

    // Storage Access Framework: Save CSV to user-selected location (Drive/OneDrive supported via their apps)
    val createCsvDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val csv = buildCsvString(records)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
            } catch (t: Throwable) {
                Log.w("NoteScanner", "Failed writing CSV to chosen location", t)
            }
        }
    }

    // SAF: pick a folder (Drive/OneDrive supported via their apps) and persist permission
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Persist access across restarts
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                cloudFolderUri = uri.toString()
                prefsCloud.edit().putString("treeUri", cloudFolderUri).apply()
                
                // Test the connection immediately and provide feedback
                val pickedDoc = DocumentFile.fromTreeUri(context, uri)
                if (pickedDoc != null && pickedDoc.canWrite()) {
                    val folderName = pickedDoc.name ?: "skýjamappa"
                    cloudSyncStatus = "✅ Tenging tókst! Skýjamappa: $folderName"
                    
                    // Test by creating a test file to verify write permissions
                    try {
                        val testFile = pickedDoc.createFile("text/plain", ".skanniapp_test")
                        if (testFile != null) {
                            testFile.delete() // Clean up test file
                            Log.i("CloudSync", "Write test successful for cloud folder")
                        }
                    } catch (e: Exception) {
                        Log.w("CloudSync", "Write test failed but folder seems accessible", e)
                    }
                } else {
                    cloudSyncStatus = "⚠️ Tengt en engin skrifheimild - prófaðu aðra möppu"
                }
            } catch (e: Exception) {
                cloudSyncStatus = "❌ Villa við tengingu: ${e.message}"
                cloudFolderUri = null // Reset on failure
                prefsCloud.edit().remove("treeUri").apply()
                Log.w("CloudSync", "Failed to connect to cloud folder", e)
            }
        } else {
            cloudSyncStatus = "Tengingu hætt við"
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Simple hamburger button (dropdown menu)
                    IconButton(onClick = { menuExpanded = true }) {
                        Text("☰", fontSize = 22.sp)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Senda CSV í tölvupósti") }, onClick = {
                            menuExpanded = false
                            exportCsv(records, context, chooserTitle = "Senda CSV")
                        })
                        DropdownMenuItem(text = { Text("Vista CSV í ský (Drive/OneDrive)") }, onClick = {
                            menuExpanded = false
                            val suggested = "notur-" + getTodayIso() + ".csv"
                            createCsvDocLauncher.launch(suggested)
                        })
                        DropdownMenuItem(text = { Text(if (cloudFolderUri != null) "🌥️ Aftengja skýjamöppu" else "🌥️ Tengja skýjamöppu (OneDrive/Drive)") }, onClick = {
                            menuExpanded = false
                            if (cloudFolderUri == null) {
                                // Ask user to pick a folder via SAF; their app (Drive/OneDrive) provides UI
                                pickFolderLauncher.launch(null)
                            } else {
                                // Unlink
                                cloudFolderUri = null
                                cloudSyncStatus = "Skýjatenging aftengt"
                                prefsCloud.edit().remove("treeUri").apply()
                            }
                        })
                        if (cloudFolderUri != null) {
                            DropdownMenuItem(text = { Text("🔍 Prófa skýjatengingu") }, onClick = {
                                menuExpanded = false
                                // Test cloud connection
                                cloudFolderUri?.let { treeStr ->
                                    try {
                                        val treeUri = Uri.parse(treeStr)
                                        val pickedDoc = DocumentFile.fromTreeUri(context, treeUri)
                                        if (pickedDoc != null && pickedDoc.canWrite()) {
                                            // Try to create a test file to verify write permissions
                                            val testFile = pickedDoc.createFile("text/plain", ".skanniapp_test_${System.currentTimeMillis()}")
                                            if (testFile != null) {
                                                testFile.delete() // Clean up immediately
                                                cloudSyncStatus = "✅ Skýjatenging virkar fullkomlega!"
                                            } else {
                                                cloudSyncStatus = "⚠️ Skýjatenging virkar en gæti haft takmörk"
                                            }
                                        } else {
                                            cloudSyncStatus = "❌ Skýjatenging virkar ekki - engin skrifheimild"
                                        }
                                    } catch (e: Exception) {
                                        cloudSyncStatus = "❌ Villa í skýjatengingu: ${e.message}"
                                        Log.w("CloudSync", "Cloud connection test failed", e)
                                    }
                                }
                            })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Persónuverndarstefna") }, onClick = {
                            menuExpanded = false
                            val url = "https://saeargeir.github.io/SkanniApp/privacy-policy.html"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        })
                        DropdownMenuItem(text = { Text(if (darkTheme) "Ljóst þema" else "Dökkt þema") }, onClick = {
                            menuExpanded = false
                            onToggleTheme()
                        })
                    }
                    Column {
                        Text(
                            "Velkomin í nótuskanna!",
                            modifier = Modifier.padding(bottom = 4.dp),
                            style = MaterialTheme.typography.titleLarge
                        )
                        // Cloud connection status prominently displayed
                        if (cloudFolderUri != null) {
                            Text(
                                "🌥️ Tengt við skýjamöppu",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "📁 Engin skýjatenging",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // Theme toggle (quick)
                OutlinedButton(onClick = onToggleTheme, modifier = Modifier.height(40.dp)) {
                    Text(if (darkTheme) "Ljóst" else "Dökkt")
                }
            }

            // Cloud setup prompt when not connected
            if (cloudFolderUri == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { pickFolderLauncher.launch(null) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🌥️",
                                fontSize = 24.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    "Tengja skýjamöppu",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Vista myndir og Excel skrár í OneDrive, Google Drive eða aðrar skýjamöppur",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
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

        // Show cloud sync status
        cloudSyncStatus?.let { status ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        status.contains("✅") -> MaterialTheme.colorScheme.primaryContainer
                        status.contains("❌") || status.contains("Villa") -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = { cloudSyncStatus = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("×", fontSize = 16.sp)
                    }
                }
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
        val logoTargetHeight = 44.dp // make logo visibly larger without being too tall
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    if (darkTheme) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (logoBmp != null) {
                // Scale by height to keep crisp aspect; will be much wider than before
                Image(
                    bitmap = logoBmp.asImageBitmap(),
                    contentDescription = "Logo",
                    modifier = Modifier.height(logoTargetHeight),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Fallback approximate aspect; use same target height
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.height(logoTargetHeight),
                    contentScale = ContentScale.Fit
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

    val months = remember(records) { records.map { it.monthKey }.distinct().sortedDescending() }
    val filtered = remember(records, selectedMonth, vendorQuery) {
        records.filter { rec ->
            (selectedMonth == null || rec.monthKey == selectedMonth) &&
            (vendorQuery.isBlank() || rec.vendor.contains(vendorQuery, ignoreCase = true))
        }
    }
    // Always sort by date descending (newest first)
    val sorted = remember(filtered) {
        filtered.sortedByDescending { it.date }
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
                    ) { Text("Til baka", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) }
                    Spacer(Modifier.size(8.dp))
                    // Month dropdown
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { monthFilterExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(selectedMonth ?: "Mánuður: Allir", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) }
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
                        label = { Text("Leita seljanda", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(56.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        shape = RoundedCornerShape(12.dp)
                    )
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
            Text(
                "Dagsetning",
                modifier = Modifier.weight(1.1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Seljandi",
                modifier = Modifier.weight(2.0f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Upphæð",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "VSK",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
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

    // persist filter preferences
    LaunchedEffect(selectedMonth, vendorQuery) {
        prefs.edit().putString("month", selectedMonth).putString("vendor", vendorQuery).apply()
    }
}

// CSV export helper
private fun buildCsvString(list: List<InvoiceRecord>): String {
    return buildString {
        // Match Excel columns: ReikningsNr,Fyrirtæki,Dagsetning,Mánuður,Nettó,VSK,Heild,Skrá
        appendLine("ReikningsNr,Fyrirtæki,Dagsetning,Mánuður,Nettó,VSK,Heild,Skrá")
        fun esc(s: String): String {
            val needs = s.contains(',') || s.contains('"') || s.contains('\n')
            val body = if (s.contains('"')) s.replace("\"", "\"\"") else s
            return if (needs) "\"$body\"" else body
        }
        list.forEach { r ->
            val net = r.amount - r.vat
            val row = listOf(
                r.invoiceNumber ?: "",
                r.vendor,
                r.date,
                r.monthKey,
                String.format("%.2f", net),
                String.format("%.2f", r.vat),
                String.format("%.2f", r.amount),
                r.imagePath
            ).joinToString(",") { esc(it) }
            appendLine(row)
        }
    }
}

private fun exportCsv(
    list: List<InvoiceRecord>,
    context: android.content.Context,
    chooserTitle: String = "Deila CSV"
) {
    val csv = buildCsvString(list)
    val file = File(context.filesDir, "notur.csv")
    file.writeText(csv, Charsets.UTF_8)
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        context.packageName + ".provider",
        file
    )
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Nótur CSV")
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = android.content.Intent.createChooser(send, chooserTitle)
    context.startActivity(chooser)
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
    
    // Intelligent VAT correction for obviously wrong values
    val correctedVat = remember(record.id) {
        val originalVat = record.vat
        val amt = record.amount
        if (amt > 100.0 && (originalVat < 10.0 || originalVat > amt * 0.5)) {
            // VSK seems wrong - use 24% estimation
            // For 24% VAT: if total is 39254, then net = 39254/1.24 = 31656, VSK = 39254-31656 = 7598
            val estimated = kotlin.math.round((amt - (amt / 1.24)) * 100) / 100
            Log.w("NoteDetailScreen", "Correcting obviously wrong VAT: $originalVat -> $estimated")
            estimated
        } else {
            originalVat
        }
    }
    
    var vat by rememberSaveable(record.id) { mutableStateOf(correctedVat.toString()) }
    var invoiceNumber by rememberSaveable(record.id) { mutableStateOf(record.invoiceNumber ?: "") }
    val bmp = remember(record.imagePath) { loadThumbnail(record.imagePath, 2048) }

    // Compute VAT breakdown from the image on demand to show 11%/24% sundurliðun
    var vatExtraction by rememberSaveable(record.id) { mutableStateOf<OcrUtil.VatExtraction?>(null) }
    var vatLoading by rememberSaveable(record.id) { mutableStateOf(false) }
    LaunchedEffect(record.id) {
        // Kick off enhanced OCR once when opening detail
        if (!vatLoading && vatExtraction == null) {
            vatLoading = true
            try {
                val f = File(record.imagePath)
                HybridOcrUtil.recognizeTextHybrid(context, f, HybridOcrUtil.OcrEngine.AUTO) { hybridResult ->
                    val ext = HybridOcrUtil.extractVATFromHybridResult(hybridResult)
                    vatExtraction = ext
                    vatLoading = false
                    Log.d("DetailOCR", "VAT extraction completed with ${hybridResult.engine}")
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
    OutlinedTextField(value = invoiceNumber, onValueChange = { invoiceNumber = it }, label = { Text("Reikningsnr./Nótunúmer") }, modifier = Modifier.fillMaxWidth())
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
                vat = vat.toDoubleOrNull() ?: record.vat,
                invoiceNumber = invoiceNumber.ifBlank { null }
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
