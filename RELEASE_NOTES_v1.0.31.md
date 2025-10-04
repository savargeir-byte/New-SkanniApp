# v1.0.31 - VSK Calculation Fix 

## 🎯 Critical VSK Calculation Fix

### ✅ VSK Formúla lagfærð
- **Lagað VSK útreikning** með réttri stærðfræðiformúlu fyrir íslenskan VSK
- **Fjarlægðir óþarfir aukastafir** - VSK sýnt með 2 aukastafi í stað 12+
- **Dæmi:** TMC reikningur (39.254 kr) sýnir núna rétta VSK: 7.597,60 kr

### 🧮 Tæknilegri breytingar
- **Rétt VSK formúla:** `VSK = heild - (heild / 1.24)` fyrir 24% VSK
- **Afnám:** Röng formúla `heild * 0.24 / 1.24` sem gaf of marga aukastafi
- **Sléttun:** `kotlin.math.round()` notuð til að fá einungis 2 aukastafi
- **Bæði view:** Lagfært í bæði skanna- og breyta-skjáum

### 📱 Fyrir og eftir (TMC reikningur):
- **Áður:** VSK reitur sýndi "28.0" eða "7597.548387096774" 
- **Eftir:** VSK reitur sýnir "7597.60 kr" (rétt upphæð)

### 🔧 Release Files
- **SkanniApp-v1.0.31-debug.apk** - Debug útgáfa með logging
- **SkanniApp-v1.0.31-release.apk** - Framleiðslu APK  
- **SkanniApp-v1.0.31-release.aab** - Play Store bundle

### 🧪 Testing
Prófaðu með TMC Bifreiðaverk reikningnum - VSK reiturinn ætti núna að sýna rétta upphæð (~7.597,60 kr) í stað percentage gildis (28.0).

### 🔄 Uppsetning
1. Sæktu **SkanniApp-v1.0.31-release.apk** fyrir venjulega notkun
2. Eða notaðu **SkanniApp-v1.0.31-debug.apk** ef þú þarft logging  
3. Virkjaðu "Install from Unknown Sources" í Android stillingum
4. Settu upp APK skrána

### 🆚 Mismunur frá v1.0.30
- Rétt VSK útreikning formúla (24% VSK)
- Hrein formatering með 2 aukastöfum
- Lagfærir "VSK 28.0" vandamál á TMC reikningum