package com.example.notescanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.widget.FrameLayout
import androidx.compose.runtime.saveable.rememberSaveable
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setContent {
            NoteScannerApp()
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
    var ocrResult by remember { mutableStateOf("") }
    var isCameraStarted by rememberSaveable { mutableStateOf(false) }
    var lastPhotoPath by rememberSaveable { mutableStateOf("") }
    var lastExcelPath by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Velkomin í nótuskanna!", modifier = Modifier.padding(bottom = 16.dp))

        if (!isCameraStarted) {
            Button(onClick = { isCameraStarted = true }) {
                Text("Opna myndavél")
            }
        } else {
            CameraPreview(
                onPhotoCaptured = { photoPath ->
                    lastPhotoPath = photoPath
                    isCameraStarted = false

                    // Vista mynd í möppu eftir mánuði
                    val now = java.time.LocalDate.now()
                    val monthFolder = File(context.filesDir, now.toString().substring(0,7))
                    if (!monthFolder.exists()) monthFolder.mkdirs()
                    val destFile = File(monthFolder, File(photoPath).name)
                    File(photoPath).copyTo(destFile, overwrite = true)

                    // OCR á mynd
                    OcrUtil.recognizeTextFromImage(context, destFile) { text ->
                        ocrResult = text
                        // Hér má bæta við regex til að finna haus og upphæð
                        val haus = text.lines().firstOrNull() ?: "Óþekkt haus"
                        val upphæð = Regex("[0-9]+[.,]?[0-9]* ?kr").find(text)?.value ?: "Óþekkt upphæð"
                        val dagsetning = now.toString()

                        // Skrá í Excel (bæta við nýja línu)
                        val excelFile = File(context.filesDir, "reikningar.xlsx")
                        ExcelUtil.appendToExcel(
                            listOf(destFile.name, dagsetning, haus, upphæð),
                            excelFile
                        )
                        lastExcelPath = excelFile.absolutePath
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
}

@Composable
fun CameraPreview(onPhotoCaptured: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = rememberUpdatedState(LocalContext.current as androidx.lifecycle.LifecycleOwner)
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    AndroidView(factory = { ctx ->
        val frameLayout = FrameLayout(ctx)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(null)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner.value,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraPreview", "Camera binding failed", exc)
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
}
