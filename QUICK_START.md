# ⚡ Quick Start - Using the Improvements

## 🎯 5-Minute Integration Guide

This guide shows you how to use the new improvements in your existing code.

---

## 1️⃣ Using Room Database (Instead of JSON)

### Old Way ❌
```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var invoiceStore: InvoiceStore
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        invoiceStore = InvoiceStore(this)
        
        // Load all data
        val notes = invoiceStore.loadAll()
        
        // Add invoice
        invoiceStore.add(invoice)
    }
}
```

### New Way ✅
```kotlin
class MainActivity : ComponentActivity() {
    private val repository by lazy { 
        InvoiceRepository(this)
    }
    
    override fun onCreate(savedInstanceState: Bundle()) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            // Load with Flow (reactive)
            repository.getAllFlow().collect { invoices ->
                // Update UI automatically
                updateUI(invoices)
            }
            
            // Or load once
            val result = repository.getAll()
            if (result.isSuccess) {
                val invoices = result.getOrNull()
                updateUI(invoices)
            }
        }
    }
    
    private fun saveInvoice(invoice: InvoiceRecord) {
        lifecycleScope.launch {
            repository.insert(invoice).onSuccess {
                // Success!
            }
        }
    }
}
```

---

## 2️⃣ Using ViewModel (Recommended)

### Old Way ❌
```kotlin
@Composable
fun InvoiceScreen() {
    var invoices by remember { mutableStateOf<List<InvoiceRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isLoading = true
        // Load data
        invoices = repository.getAll().getOrNull() ?: emptyList()
        isLoading = false
    }
}
```

### New Way ✅
```kotlin
@Composable
fun InvoiceScreen(
    viewModel: InvoiceViewModel = viewModel()
) {
    val invoices by viewModel.invoices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    // Automatic updates, survives configuration changes
    
    if (error != null) {
        ErrorMessage(error!!)
    }
    
    if (isLoading) {
        LoadingIndicator()
    } else {
        InvoiceList(invoices)
    }
}
```

---

## 3️⃣ Error Handling

### Old Way ❌
```kotlin
try {
    val result = someOperation()
} catch (e: Exception) {
    Log.e(TAG, "Error", e)
    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
}
```

### New Way ✅
```kotlin
// Option 1: Using ErrorHandler
ErrorHandler.safeExecute(
    context = context,
    errorType = ErrorHandler.ErrorType.SAVE_FAILED
) {
    repository.insert(invoice)
}

// Option 2: Using Result wrapper
val result = repository.insert(invoice)
if (result.isFailure) {
    ErrorHandler.showError(
        context,
        ErrorHandler.ErrorType.SAVE_FAILED,
        result.exceptionOrNull()
    )
}

// Option 3: With Snackbar and retry
ErrorHandler.handleError(
    scope = lifecycleScope,
    snackbarHost = snackbarHostState,
    errorType = ErrorHandler.ErrorType.SAVE_FAILED,
    throwable = exception,
    actionLabel = "Reyni aftur",
    onAction = { retryOperation() }
)
```

---

## 4️⃣ Image Enhancement

### Old Way ❌
```kotlin
fun processImage(bitmap: Bitmap): String {
    val image = InputImage.fromBitmap(bitmap, 0)
    recognizer.process(image)
    // No enhancement
}
```

### New Way ✅
```kotlin
fun processImage(bitmap: Bitmap): String {
    // Check quality first
    val quality = ImageEnhancementUtil.assessQuality(bitmap)
    
    if (!quality.isGoodQuality()) {
        // Show recommendation to user
        Toast.makeText(context, quality.recommendation, Toast.LENGTH_SHORT).show()
    }
    
    // Enhance for better OCR
    val enhanced = if (quality.isExcellentQuality()) {
        bitmap // Already good
    } else {
        ImageEnhancementUtil.enhanceForOcr(bitmap)
    }
    
    // Now perform OCR
    val image = InputImage.fromBitmap(enhanced, 0)
    recognizer.process(image)
}
```

---

## 5️⃣ Using Timber Logging

### Old Way ❌
```kotlin
Log.d(TAG, "Processing invoice")
Log.e(TAG, "Error processing", exception)
```

### New Way ✅
```kotlin
import timber.log.Timber

// Simple logging
Timber.d("Processing invoice")
Timber.e(exception, "Error processing")

// With formatting
Timber.d("Processing invoice %d", invoiceId)

// Conditional
if (BuildConfig.DEBUG) {
    Timber.v("Verbose debug info")
}

// Detailed error
ErrorHandler.logDetailedError(
    tag = "InvoiceProcessor",
    message = "Failed to process",
    throwable = exception,
    additionalInfo = mapOf(
        "invoiceId" to invoiceId,
        "vendor" to vendor,
        "attempt" to attemptNumber
    )
)
```

---

## 6️⃣ Batch OCR with Enhancement

### Old Way ❌
```kotlin
suspend fun processBatch(receipts: List<ScannedReceiptData>) {
    receipts.forEach { receipt ->
        val bitmap = loadBitmap(receipt.imageUri)
        val text = performOCR(bitmap)
        // No enhancement, no quality check
    }
}
```

### New Way ✅
```kotlin
suspend fun processBatch(receipts: List<ScannedReceiptData>) {
    val processor = BatchOcrProcessor(context)
    
    processor.processBatchFlow(receipts) { progress ->
        // Update UI with progress
        updateProgress(progress)
    }.collect { result ->
        when (result) {
            is BatchProcessingResult.Started -> {
                showLoading()
            }
            is BatchProcessingResult.Completed -> {
                showResults(result.batchData)
                // Automatic enhancement applied!
            }
            is BatchProcessingResult.Failed -> {
                ErrorHandler.showError(
                    context,
                    ErrorHandler.ErrorType.OCR_FAILED,
                    details = result.error
                )
            }
        }
    }
}
```

---

## 7️⃣ Query with Flow (Reactive)

### Old Way ❌
```kotlin
// Load once, no updates
val invoices = repository.getAll().getOrNull()
```

### New Way ✅
```kotlin
// Option 1: Using Flow in Composable
@Composable
fun InvoiceList() {
    val invoices by repository.getAllFlow()
        .collectAsState(initial = emptyList())
    
    // Automatically updates when data changes!
    LazyColumn {
        items(invoices) { invoice ->
            InvoiceCard(invoice)
        }
    }
}

// Option 2: Using Flow with ViewModel
class InvoiceViewModel(app: Application) : AndroidViewModel(app) {
    val invoices: StateFlow<List<InvoiceRecord>> = 
        repository.getAllFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
}

// Option 3: Manual collection
lifecycleScope.launch {
    repository.getAllFlow().collect { invoices ->
        updateUI(invoices)
    }
}
```

---

## 8️⃣ Statistics Queries

### Old Way ❌
```kotlin
val invoices = repository.getAll().getOrNull()
val total = invoices?.sumOf { it.amount } ?: 0.0
val vat = invoices?.sumOf { it.vat } ?: 0.0
```

### New Way ✅
```kotlin
// Using ViewModel
val viewModel: InvoiceViewModel = viewModel()

@Composable
fun StatisticsScreen() {
    val stats by viewModel.statistics.collectAsState()
    val monthlyTotal by viewModel.monthlyTotal.collectAsState()
    val monthlyVat by viewModel.monthlyVat.collectAsState()
    
    Column {
        Text("Total: ${stats?.totalAmount?.formatCurrency()}")
        Text("VAT: ${stats?.totalVat?.formatCurrency()}")
        Text("Invoices: ${stats?.totalInvoices}")
        Text("Average: ${stats?.averageAmount?.formatCurrency()}")
        
        // Top vendors
        stats?.topVendors?.forEach { vendor ->
            Text("${vendor.vendor}: ${vendor.total.formatCurrency()}")
        }
    }
}

// Or direct repository queries
lifecycleScope.launch {
    val monthKey = "2024-01"
    val total = repository.getTotalAmountByMonth(monthKey).getOrNull()
    val vat = repository.getTotalVatByMonth(monthKey).getOrNull()
    val count = repository.getCountByMonth(monthKey).getOrNull()
    val vendors = repository.getVendorTotalsByMonth(monthKey).getOrNull()
}
```

---

## 9️⃣ Data Migration (One-time)

### Migrate from JSON to Room

```kotlin
// In MainActivity or migration screen
suspend fun migrateToRoom() {
    val oldStore = InvoiceStore(context)
    val repository = InvoiceRepository(context)
    
    try {
        // Load old data
        val oldInvoices = oldStore.loadAll()
        
        if (oldInvoices.isEmpty()) {
            Timber.d("No data to migrate")
            return
        }
        
        // Save to Room
        val result = repository.insertAll(oldInvoices)
        
        if (result.isSuccess) {
            // Clear old JSON
            oldStore.clearAll()
            
            Timber.i("Successfully migrated ${oldInvoices.size} invoices")
            Toast.makeText(
                context,
                "Fluttu ${oldInvoices.size} reikninga í nýja gagnagrunninn",
                Toast.LENGTH_LONG
            ).show()
        } else {
            throw result.exceptionOrNull() ?: Exception("Migration failed")
        }
        
    } catch (e: Exception) {
        Timber.e(e, "Migration failed")
        ErrorHandler.showError(
            context,
            ErrorHandler.ErrorType.DATABASE_ERROR,
            e
        )
    }
}

// Call once on first launch after update
lifecycleScope.launch {
    migrateToRoom()
}
```

---

## 🔟 Complete Example: Adding a New Invoice

### Complete Flow with All Improvements

```kotlin
@Composable
fun AddInvoiceScreen(
    viewModel: InvoiceViewModel = viewModel()
) {
    var vendor by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    Column {
        // Image capture with quality check
        Button(onClick = { 
            captureImage { bitmap ->
                // Assess quality
                val quality = ImageEnhancementUtil.assessQuality(bitmap)
                
                if (!quality.isGoodQuality()) {
                    // Warn user
                    showDialog("Gæði: ${quality.recommendation}")
                }
                
                // Enhance and process
                val enhanced = ImageEnhancementUtil.enhanceForOcr(bitmap)
                performOCR(enhanced) { ocrText ->
                    // Parse with Icelandic parser
                    val parsed = IcelandicInvoiceParser.parseInvoiceText(ocrText)
                    
                    // Fill form
                    vendor = parsed.vendor
                    amount = parsed.amount.toString()
                    date = parsed.date ?: ""
                    
                    imageBitmap = enhanced
                }
            }
        }) {
            Text("Skanna reikning")
        }
        
        // Form fields with validation
        OutlinedTextField(
            value = vendor,
            onValueChange = { vendor = it },
            label = { Text("Seljandi") },
            isError = vendor.isBlank()
        )
        
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Upphæð") },
            isError = ErrorHandler.validateAmount(amount).isFailure
        )
        
        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Dagsetning (YYYY-MM-DD)") },
            isError = ErrorHandler.validateDate(date).isFailure
        )
        
        // Error display
        error?.let { errorType ->
            Text(
                text = errorType.userMessage,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        // Save button
        Button(
            onClick = {
                // Validate
                val amountResult = ErrorHandler.validateAmount(amount)
                val dateResult = ErrorHandler.validateDate(date)
                
                if (amountResult.isSuccess && dateResult.isSuccess) {
                    // Create invoice
                    val invoice = InvoiceRecord(
                        id = System.currentTimeMillis(),
                        date = dateResult.getOrThrow(),
                        monthKey = dateResult.getOrThrow().substring(0, 7),
                        vendor = vendor,
                        amount = amountResult.getOrThrow(),
                        vat = amountResult.getOrThrow() * 0.24 / 1.24,
                        imagePath = saveImage(imageBitmap),
                        invoiceNumber = null,
                        ocrText = null
                    )
                    
                    // Save with ViewModel
                    viewModel.insertInvoice(invoice) {
                        // Success!
                        navigateBack()
                    }
                } else {
                    // Show validation errors
                    ErrorHandler.showError(
                        context,
                        ErrorHandler.ErrorType.INVALID_INPUT
                    )
                }
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text("Vista")
            }
        }
    }
}
```

---

## ✅ Quick Checklist

Use this checklist when adding new features:

- [ ] Use `InvoiceRepository` instead of `InvoiceStore`
- [ ] Use `InvoiceViewModel` for screen state
- [ ] Handle errors with `ErrorHandler`
- [ ] Enhance images with `ImageEnhancementUtil`
- [ ] Log with `Timber` instead of `Log`
- [ ] Use `Flow` for reactive data
- [ ] Validate inputs with `ErrorHandler.validate*`
- [ ] Show loading states
- [ ] Use `Result<T>` wrapper
- [ ] Test error scenarios

---

## 🎯 Common Patterns

### Pattern 1: Load and Display
```kotlin
val invoices by repository.getAllFlow().collectAsState(initial = emptyList())
LazyColumn {
    items(invoices) { invoice -> InvoiceCard(invoice) }
}
```

### Pattern 2: Save with Error Handling
```kotlin
lifecycleScope.launch {
    ErrorHandler.safeExecute(context, ErrorHandler.ErrorType.SAVE_FAILED) {
        repository.insert(invoice)
    }
}
```

### Pattern 3: Search and Filter
```kotlin
val searchQuery by remember { mutableStateOf("") }
val filtered by repository.searchByVendorFlow(searchQuery)
    .collectAsState(initial = emptyList())
```

### Pattern 4: Monthly Statistics
```kotlin
val viewModel: InvoiceViewModel = viewModel()
viewModel.setSelectedMonth(YearMonth.now())
val monthlyTotal by viewModel.monthlyTotal.collectAsState()
val monthlyVat by viewModel.monthlyVat.collectAsState()
```

---

**That's it! You're ready to use all the improvements!** 🚀

For more details, see:
- 📖 [IMPROVEMENTS.md](IMPROVEMENTS.md) - Technical details
- 📊 [FIXES_SUMMARY.md](FIXES_SUMMARY.md) - What was fixed
- 🔨 [BUILD_GUIDE.md](BUILD_GUIDE.md) - How to build
