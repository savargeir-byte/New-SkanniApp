package io.github.saeargeir.skanniapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saeargeir.skanniapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { 
                Text(
                    text = "Persónuverndarstefna",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                ) 
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "←"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // IceVeflausnir Disclaimer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ábyrgðaryfirlýsing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Þetta app og kóði er í eigu IceVeflausnir. Við tökum ekki ábyrgð á gögnum eða notkun appsins. Notendur eru ábyrgir fyrir eigin gögnum og notkun.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // Privacy Policy Content
            Text(
                text = "Persónuverndarstefna SkanniApp",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "Síðast uppfært: ${java.time.LocalDate.now()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            PrivacySection(
                title = "1. Gagnasöfnun",
                content = listOf(
                    "SkanniApp vinnur eingöngu með gögn á tækinu þínu",
                    "Við söfnum ekki persónuupplýsingum í gegnum netið",
                    "Mynd- og textagreining fer fram staðbundið",
                    "Eingöngu þú hefur aðgang að þínum gögnum"
                )
            )
            
            PrivacySection(
                title = "2. Gagnanotkun",
                content = listOf(
                    "Myndir og texti eru eingöngu notaðir fyrir OCR greiningu",
                    "Gögn eru vistuð staðbundið á tækinu",
                    "Engin gögn eru send til þriðja aðila",
                    "Firebase authentication er valfrjálst fyrir cloud sync"
                )
            )
            
            PrivacySection(
                title = "3. Gagnaöryggi",
                content = listOf(
                    "Öll OCR vinnsla fer fram án internettenglingar",
                    "Gögn eru dulkóðuð í staðbundinni gagnageymslu",
                    "Google authentication er eingöngu notað fyrir auðkenningu",
                    "Cloud sync er valfrjálst og undir þinni stjórn"
                )
            )
            
            PrivacySection(
                title = "4. Þín réttindi",
                content = listOf(
                    "Þú getur eytt öllum gögnum hvenær sem er",
                    "Þú hefur fulla stjórn á þínum gögnum",
                    "Þú getur slökkt á cloud sync hvenær sem er",
                    "Þú getur eytt appinu og öllum gögnum"
                )
            )
            
            PrivacySection(
                title = "5. Þriðja aðila þjónusta",
                content = listOf(
                    "Google ML Kit fyrir OCR (staðbundið)",
                    "Firebase Authentication (valfrjálst)",
                    "Firebase Firestore (valfrjálst fyrir cloud sync)",
                    "Engin auglýsingakerfi eða greiningarkerfi"
                )
            )
            
            Text(
                text = "Hafðu samband ef þú hefur spurningar um persónuvernd: iceveflausnir@gmail.com",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    content: List<String>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary
        )
        
        content.forEach { item ->
            Row(
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "• ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}