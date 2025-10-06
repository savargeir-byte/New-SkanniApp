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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.content.ContentResolver
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import io.github.saeargeir.skanniapp.ocr.HybridOcrUtil
import io.github.saeargeir.skanniapp.firebase.FirebaseRepository
import io.github.saeargeir.skanniapp.ui.auth.AuthScreen
import io.github.saeargeir.skanniapp.ui.auth.UserProfileCard
import io.github.saeargeir.skanniapp.ui.theme.ThemeManager
import io.github.saeargeir.skanniapp.ui.theme.ThemeConfig
import io.github.saeargeir.skanniapp.ui.theme.shouldUseDarkTheme
import io.github.saeargeir.skanniapp.ui.theme.ThemeSettingsScreen
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
                // Use ThemeManager for persistent theme settings
                val themeManager = remember { ThemeManager(this@MainActivity) }
                val themeConfig by themeManager.themeConfig.collectAsState(
                    initial = ThemeConfig()
                )
                
                val darkTheme = shouldUseDarkTheme(themeConfig)
                val lightColors = lightColorScheme()
                val darkColors = darkColorScheme()
                
                MaterialTheme(
                    colorScheme = if (darkTheme) darkColors else lightColors,
                    shapes = Shapes(
                        small = RoundedCornerShape(8.dp),
                        medium = RoundedCornerShape(12.dp),
                        large = RoundedCornerShape(16.dp)
                    )
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NoteScannerApp(
                            themeManager = themeManager,
                            isDarkTheme = darkTheme
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
}

// Date utility functions
fun getTodayIso(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date())
}

fun getCurrentMonthKey(): String {
    val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    return sdf.format(Date())
}

@Composable
fun NoteScannerApp(
    themeManager: ThemeManager,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val firebaseRepo = remember { FirebaseRepository() }
    
    var showRecordForm by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<InvoiceRecord?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showUserProfile by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }
    
    val currentUser by firebaseRepo.currentUser.collectAsState()
    val isUserSignedIn = currentUser != null
    
    // Get records from Firebase Flow
    val records by firebaseRepo.getInvoicesFlow().collectAsState(initial = emptyList())

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
            
            // Process OCR and create invoice record
            val bitmap = BitmapFactory.decodeFile(destFile.absolutePath)
            if (bitmap != null) {
                HybridOcrUtil.extractOcrDataAsync(bitmap) { extractedText ->
                    activity?.runOnUiThread {
                        val newRecord = InvoiceRecord(
                            id = System.currentTimeMillis().toString(),
                            date = today,
                            monthKey = monthKey,
                            vendor = HybridOcrUtil.extractVendor(extractedText) ?: "",
                            amount = HybridOcrUtil.extractAmount(extractedText) ?: 0.0,
                            vat = HybridOcrUtil.extractVAT(extractedText) ?: 0.0,
                            imagePath = destFile.absolutePath,
                            invoiceNumber = HybridOcrUtil.extractInvoiceNumber(extractedText) ?: ""
                        )
                        // Upload to Firebase with image
                        firebaseRepo.addInvoice(newRecord, File(destFile.absolutePath))
                        selectedRecord = newRecord
                        showRecordForm = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NoteScannerApp", "Error processing scanned image", e)
        }
    }

    fun exportCsv(records: List<InvoiceRecord>, context: android.content.Context, chooserTitle: String) {
        if (records.isEmpty()) return
        val csv = buildString {
            appendLine("Dagsetning,Seljandi,Upphæð,VSK,Reikningsnúmer")
            records.forEach { record ->
                appendLine("${record.date},${record.vendor},${record.amount},${record.vat},${record.invoiceNumber}")
            }
        }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_TEXT, csv)
            putExtra(Intent.EXTRA_SUBJECT, "Reikninga yfirlit")
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    // Check camera permission
    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    
    var askedPermissionOnce by rememberSaveable { mutableStateOf(false) }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            // Optionally guide to Settings
        }
    }

    // ML Kit Document Scanner
    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }

    // Launcher for ML Kit Document Scanner
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(intent)
                scanningResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                    handleScannedImageUri(uri)
                }
            }
        }
    }

    // Storage Access Framework: Save CSV to user-selected location
    val createCsvDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { docUri ->
            val csv = buildString {
                appendLine("Dagsetning,Seljandi,Upphæð,VSK,Reikningsnúmer")
                records.forEach { record ->
                    appendLine("${record.date},${record.vendor},${record.amount},${record.vat},${record.invoiceNumber}")
                }
            }
            try {
                context.contentResolver.openOutputStream(docUri)?.use { output ->
                    output.write(csv.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("CSV Export", "Failed to save CSV", e)
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

    // Show auth screen if user is not signed in
    if (!isUserSignedIn) {
        AuthScreen(
            authService = firebaseRepo.authService,
            onAuthSuccess = {
                // User signed in successfully - records will update automatically via Flow
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x26FFFFFF),
                            Color(0x26F5F5F5),
                            Color(0x26EFEFEF)
                        )
                    )
                )
        )
        
        // Faint watermark image
        Image(
            painter = painterResource(id = R.drawable.bg_watermark),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.08f },
            contentScale = ContentScale.Crop
        )

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
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Þemastillingar")
                                }
                            }, 
                            onClick = {
                                menuExpanded = false
                                showThemeSettings = true
                            }
                        )
                        DropdownMenuItem(text = { Text("Senda CSV í tölvupósti") }, onClick = {
                            menuExpanded = false
                            exportCsv(records, context, chooserTitle = "Senda CSV")
                        })
                        DropdownMenuItem(text = { Text("Vista CSV skrá") }, onClick = {
                            menuExpanded = false
                            val suggested = "notur-" + getTodayIso() + ".csv"
                            createCsvDocLauncher.launch(suggested)
                        })
                        DropdownMenuItem(text = { Text("👤 Notandaupplýsingar") }, onClick = {
                            menuExpanded = false
                            showUserProfile = true
                        })
                    }
                    
                    Text(
                        text = "SkanniApp",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Theme toggle
                    IconButton(onClick = onToggleTheme) {
                        Text(if (darkTheme) "☀️" else "🌙", fontSize = 18.sp)
                    }
                    
                    // Torch toggle
                    if (hasCameraPermission) {
                        IconButton(onClick = {
                            torchEnabled = !torchEnabled
                            cameraRef?.cameraControl?.enableTorch(torchEnabled)
                        }) {
                            Text(if (torchEnabled) "🔦" else "💡", fontSize = 18.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main scan button
            Button(
                onClick = {
                    if (hasCameraPermission) {
                        scanner.getStartScanIntent(activity as ComponentActivity)
                            .addOnSuccessListener { intentSender ->
                                scannerLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build()
                                )
                            }
                            .addOnFailureListener { e ->
                                Log.e("DocumentScanner", "Failed to start scanner", e)
                            }
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (hasCameraPermission) "📷 Skanna reikning" else "📷 Veita myndavélaheimild",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Records list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(records) { record ->
                    InvoiceRecordCard(
                        record = record,
                        onClick = { 
                            selectedRecord = record
                            showRecordForm = true 
                        },
                        onDelete = { 
                            firebaseRepo.deleteInvoice(record.id)
                        }
                    )
                }
            }
        }
    }

    // Record edit form dialog
    if (showRecordForm && selectedRecord != null) {
        InvoiceRecordFormDialog(
            record = selectedRecord!!,
            onSave = { updatedRecord ->
                firebaseRepo.updateInvoice(updatedRecord)
                showRecordForm = false
                selectedRecord = null
            },
            onDismiss = {
                showRecordForm = false
                selectedRecord = null
            }
        )
    }
    
    // User profile dialog
    if (showUserProfile) {
        AlertDialog(
            onDismissRequest = { showUserProfile = false },
            title = { Text("Notandaupplýsingar") },
            text = {
                UserProfileCard(
                    authService = firebaseRepo.authService,
                    onSignOut = {
                        showUserProfile = false
                        // Records will be cleared automatically when user signs out via Flow
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showUserProfile = false }) {
                    Text("Loka")
                }
            }
        )
    }
    
    // Theme settings screen overlay
    if (showThemeSettings) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ThemeSettingsScreen(
                onNavigateBack = { showThemeSettings = false }
            )
        }
    }
}

@Composable
fun InvoiceRecordCard(
    record: InvoiceRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.vendor.ifEmpty { "Ótilgreindur seljandi" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.amount} kr.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = record.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onDelete) {
                Text("🗑️", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun InvoiceRecordFormDialog(
    record: InvoiceRecord,
    onSave: (InvoiceRecord) -> Unit,
    onDismiss: () -> Unit
) {
    var vendor by remember { mutableStateOf(record.vendor) }
    var amount by remember { mutableStateOf(record.amount.toString()) }
    var vat by remember { mutableStateOf(record.vat.toString()) }
    var invoiceNumber by remember { mutableStateOf(record.invoiceNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Breyta reikningi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Seljandi") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Upphæð") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vat,
                    onValueChange = { vat = it },
                    label = { Text("VSK") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text("Reikningsnúmer") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedRecord = record.copy(
                        vendor = vendor,
                        amount = amount.toDoubleOrNull() ?: record.amount,
                        vat = vat.toDoubleOrNull() ?: record.vat,
                        invoiceNumber = invoiceNumber
                    )
                    onSave(updatedRecord)
                }
            ) {
                Text("Vista")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hætta við")
            }
        }
    )
}