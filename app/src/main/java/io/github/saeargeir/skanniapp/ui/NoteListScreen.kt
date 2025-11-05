package io.github.saeargeir.skanniapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import io.github.saeargeir.skanniapp.model.SortType
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun NoteListScreen(
    notes: List<InvoiceRecord>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onSearchSeller: (String) -> Unit,
    onSortBy: (SortType) -> Unit,
    onNoteClick: (InvoiceRecord) -> Unit,
    onOverview: () -> Unit,
    onNotes: () -> Unit,
    onExportJson: () -> Unit = {},
    onOpenImage: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var searchText by remember { mutableStateOf("") }
    var sortType by remember { mutableStateOf(SortType.DATE) }
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(bottom = 120.dp) // Add padding for navigation bar
        ) {
            // Header - sama og InvoiceFormScreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF6B46C1))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Velkomin í nótuskanna!",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Skrá út") },
                            onClick = {
                                showMenu = false
                                onSignOut()
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Flytja út CSV") },
                            onClick = { showMenu = false; onExportCsv() },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Flytja út JSON") },
                            onClick = { showMenu = false; onExportJson() },
                            leadingIcon = { Icon(Icons.Default.DataObject, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Senda í pósti") },
                            onClick = { showMenu = false; onShare() },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                        )
                    }
                }
            }

            // Top buttons með CSV og Send í pósti - sama og InvoiceFormScreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExportCsv,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A085)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flytja út CSV", color = Color.White)
                    }
                }
                
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A085)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Senda í pósti", color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cloud connection status - sama og InvoiceFormScreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0E7FF))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = "Cloud",
                    tint = Color(0xFF6B46C1),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tengt við skýjamöppu",
                    color = Color(0xFF6B46C1),
                    fontSize = 14.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Action buttons - sama skipulag og InvoiceFormScreen
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOverview,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Skoða yfirlitt", color = Color.White)
                    }
                    
                    Button(
                        onClick = onNotes,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Skoða nótur", color = Color.White)
                    }
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Til baka", color = Color.White)
                    }
                    
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Deila mynd", color = Color.White)
                    }
                }

                // Row 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenImage,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Skanna reikning", color = Color.White)
                    }
                    
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Eyða", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Form fields section - show notes list
                if (notes.isNotEmpty()) {
                    Text(
                        text = "Reikningar í ${selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Show list of notes
                    notes.forEach { note ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onNoteClick(note) },
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = note.vendor.ifEmpty { "Óþekktur seljandi" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF333333)
                                )
                                Text(
                                    text = "${note.amount} kr - ${note.date}",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                                if (note.imagePath?.isNotEmpty() == true) {
                                    Text(
                                        text = "📸 Mynd til staðar",
                                        fontSize = 12.sp,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Engir reikningar í ${selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}",
                        fontSize = 16.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Seljandi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                
                OutlinedTextField(
                    value = "",
                    onValueChange = { },
                    placeholder = { Text("Óþekkkt seljandi") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6B46C1),
                        unfocusedBorderColor = Color(0xFFCCCCCC)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Reikningsnr./Nótunúmer",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                
                OutlinedTextField(
                    value = "",
                    onValueChange = { },
                    placeholder = { Text("ina") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6B46C1),
                        unfocusedBorderColor = Color(0xFFCCCCCC)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Dagsetning (yyyy-MM-dd)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                
                OutlinedTextField(
                    value = "",
                    onValueChange = { },
                    placeholder = { Text("yyyy-MM-dd") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6B46C1),
                        unfocusedBorderColor = Color(0xFFCCCCCC)
                    )
                )
            }
        }
    }
}
