package io.github.saeargeir.skanniapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    onNotes: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var sortType by remember { mutableStateOf(SortType.DATE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8FA))
            .padding(8.dp)
    ) {
        // Top bar: same as Home for consistency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = onOverview,
                shape = RoundedCornerShape(18.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B46C1))
            ) {
                Text("Skoða yfirlit", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            OutlinedButton(
                onClick = onNotes,
                shape = RoundedCornerShape(18.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B46C1))
            ) {
                Text("Skoða nótur", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Controls group
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3EDFC), shape = RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(18.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B46C1))
                    ) {
                        Text("...", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            // Month picker dialog could be implemented
                            val next = selectedMonth.plusMonths(1)
                            onMonthChange(next)
                        },
                        shape = RoundedCornerShape(18.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B46C1))
                    ) {
                        Text("Mán...", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onSearchSeller(searchText) },
                        shape = RoundedCornerShape(18.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B46C1))
                    ) {
                        Text("Leita selj...", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = { sortType = SortType.VENDOR; onSortBy(SortType.VENDOR) },
                        shape = RoundedCornerShape(18.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B46C1))
                    ) {
                        Text("Rað...", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { sortType = SortType.AMOUNT; onSortBy(SortType.AMOUNT) },
                        shape = RoundedCornerShape(18.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B46C1))
                    ) {
                        Text("Rað...", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { sortType = SortType.DATE; onSortBy(SortType.DATE) },
                        shape = RoundedCornerShape(18.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B46C1))
                    ) {
                        Text("Rað...", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onExportCsv,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B46C1)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Flytja út CSV", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Table header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Dagset...", fontWeight = FontWeight.Bold)
            Text("Seljandi", fontWeight = FontWeight.Bold)
            Text("Upphæð", fontWeight = FontWeight.Bold)
            Text("VSK", fontWeight = FontWeight.Bold)
        }
    HorizontalDivider()
        // Note list
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(notes) { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color.White)
                        .clickable { onNoteClick(note) },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(note.date, fontSize = 16.sp)
                    Text(note.vendor, fontSize = 16.sp)
                    Text("${note.amount} kr", fontSize = 16.sp)
                    Text("${note.vat} kr", fontSize = 16.sp)
                }
                HorizontalDivider()
            }
        }
    }
}

enum class SortType { SELLER, AMOUNT, DATE }
