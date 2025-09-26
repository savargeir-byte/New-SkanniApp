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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import android.graphics.BitmapFactory
import com.example.notescanner.data.InvoiceStore
import com.example.notescanner.model.InvoiceRecord
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        try {
            setContent {
                // Wrap in Material3 theme to ensure required CompositionLocals exist on all devices
                MaterialTheme(colorScheme = lightColorScheme()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NoteScannerApp()
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
fun NoteScannerApp() {
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
    var records by remember { mutableStateOf(store.loadAll()) }
    fun refreshRecords() { records = store.loadAll() }

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

    // Auto-request CAMERA on first open
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission && !askedPermissionOnce) {
            askedPermissionOnce = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image if present: place a drawable named bg_invoice in res/drawable
        val bgResId = remember {
            context.resources.getIdentifier("bg_invoice", "drawable", context.packageName)
        }
        if (bgResId != 0) {
            Image(
                painter = painterResource(id = bgResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.15f
            )
        }
        // Centered receipt watermark
        val receiptId = remember { context.resources.getIdentifier("ic_receipt_bg", "drawable", context.packageName) }
        if (receiptId != 0) {
            Image(
                painter = painterResource(id = receiptId),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(200.dp),
                contentScale = ContentScale.Fit,
                alpha = 0.10f
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
        Text("Velkomin í nótuskanna!", modifier = Modifier.padding(bottom = 16.dp))

            // Big primary scan button
            Button(
                onClick = {
                    showList = false
                    showOverview = false
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        isCameraStarted = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Skanna nótu")
            }

            Spacer(modifier = Modifier.size(12.dp))

            // Secondary navigation buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    showOverview = true; showList = false; isCameraStarted = false
                }, modifier = Modifier.weight(1f)) { Text("Skoða yfirlit") }
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(onClick = {
                    showList = true; showOverview = false; isCameraStarted = false
                }, modifier = Modifier.weight(1f)) { Text("Skoða nótur") }
            }

            if (showOverview) {
                OverviewScreen(records = records)
                return@Column
            }
            if (showList) {
                InvoiceListScreen(records = records)
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
                onPhotoCaptured = { photoPath ->
                    lastPhotoPath = photoPath
                    isCameraStarted = false

                    // Vista mynd í möppu eftir mánuði
                    val now = java.time.LocalDate.now()
                    val monthKey = now.toString().substring(0,7)
                    val monthFolder = File(context.filesDir, monthKey)
                    if (!monthFolder.exists()) monthFolder.mkdirs()
                    val destFile = File(monthFolder, File(photoPath).name)
                    File(photoPath).copyTo(destFile, overwrite = true)

                    // OCR á mynd
                    OcrUtil.recognizeTextFromImage(context, destFile) { text ->
                        ocrResult = text
                        // Greina seljanda, upphæð og VSK
                        val parsed = OcrUtil.parse(text)
                        val vendor = parsed.vendor ?: text.lines().firstOrNull()?.take(64) ?: "Óþekkt"
                        val amount = parsed.amount ?: 0.0
                        val vat = parsed.vat ?: 0.0
                        val dagsetning = now.toString()

                        // Skrá í Excel (bæta við nýja línu)
                        val excelFile = File(context.filesDir, "reikningar.xlsx")
                        ExcelUtil.appendToExcel(
                            listOf(destFile.name, dagsetning, monthKey, vendor, String.format("%.2f", amount), String.format("%.2f", vat)),
                            excelFile
                        )
                        lastExcelPath = excelFile.absolutePath

                        // Vista í JSON gagnagrunn
                        val record = InvoiceRecord(
                            id = System.currentTimeMillis(),
                            date = dagsetning,
                            monthKey = monthKey,
                            vendor = vendor,
                            amount = amount,
                            vat = vat,
                            imagePath = destFile.absolutePath
                        )
                        store.add(record)
                        refreshRecords()
                    }
                }
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

        // Footer branding at bottom
        Text(
            text = "IceVeflausnir",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun InvoiceListScreen(records: List<InvoiceRecord>) {
    val grouped = remember(records) { records.groupBy { it.monthKey }.toSortedMap(compareByDescending { it }) }
    LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
        grouped.forEach { (month, list) ->
            item(key = "header-$month") {
                Text(text = month, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            items(list, key = { it.id }) { rec ->
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)) {
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
                Divider()
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
fun OverviewScreen(records: List<InvoiceRecord>) {
    val grouped = remember(records) { records.groupBy { it.monthKey }.toSortedMap(compareByDescending { it }) }
    LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
        grouped.forEach { (month, list) ->
            val total = list.sumOf { it.amount }
            val totalVat = list.sumOf { it.vat }
            item(key = "ovh-$month") {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)) {
                    Text(text = month, style = MaterialTheme.typography.titleMedium)
                    Text("Fjöldi nóta: ${list.size}")
                    Text("Samtals: ${String.format("%.2f", total)} kr | VSK: ${String.format("%.2f", totalVat)} kr")
                }
                Divider()
            }
        }
    }
}

@Composable
fun CameraPreview(onPhotoCaptured: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

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
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.w("CameraPreview", "Back camera binding failed, trying front", exc)
                try {
                    cameraProvider.unbindAll()
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (exc2: Exception) {
                    Log.e("CameraPreview", "Front camera binding also failed", exc2)
                }
            }
        }, ContextCompat.getMainExecutor(ctx))
        frameLayout
    }, modifier = Modifier.height(300.dp))

    Button(onClick = {
        val photoFile = File(context.filesDir, "nota.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onPhotoCaptured(photoFile.absolutePath)
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraPreview", "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }, modifier = Modifier.padding(top = 16.dp)) {
        Text("Taka mynd af nótu")
    }
}
