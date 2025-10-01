import java.util.Properties
import java.io.File

plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
var hasSigning = false
var resolvedStoreFile: File? = null
if (keystorePropertiesFile.exists()) {
	keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
	val storePath = keystoreProperties["storeFile"] as String?
	if (!storePath.isNullOrBlank()) {
		resolvedStoreFile = rootProject.file(storePath)
		hasSigning = resolvedStoreFile?.exists() == true
	}
}

android {
	// Use a unique, publishable namespace (avoid com.example)
	namespace = "io.github.saeargeir.skanniapp"
	compileSdk = 34
	buildToolsVersion = "35.0.0"

	defaultConfig {
		// This is the Play Store package name
		applicationId = "io.github.saeargeir.skanniapp"
		minSdk = 24
		targetSdk = 34
	versionCode = 6
	versionName = "1.0.5"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	if (hasSigning) {
		signingConfigs {
			create("release") {
				storeFile = resolvedStoreFile
				storePassword = keystoreProperties["storePassword"] as String?
				keyAlias = keystoreProperties["keyAlias"] as String?
				keyPassword = keystoreProperties["keyPassword"] as String?
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			isDebuggable = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			if (hasSigning) {
				signingConfig = signingConfigs.getByName("release")
			}
		}
		debug {
			isDebuggable = true
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
		isCoreLibraryDesugaringEnabled = true
	}
	kotlinOptions {
		jvmTarget = "21"
	}
	buildFeatures {
		compose = true
	}
	composeOptions {
		kotlinCompilerExtensionVersion = "1.5.14"
	}

	// Workaround Windows file-lock issues by using a fresh build dir (bypass R.jar lock)
	setBuildDir("build_android3")
}

dependencies {
	implementation("androidx.core:core-ktx:1.12.0")
	implementation("androidx.activity:activity-compose:1.9.2")

	implementation(platform("androidx.compose:compose-bom:2024.04.01"))
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-graphics")
	implementation("androidx.compose.foundation:foundation")
	// Include foundation-layout for Modifier.weight and other layout utilities
	implementation("androidx.compose.foundation:foundation-layout")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.compose.ui:ui-tooling-preview")

	implementation("androidx.camera:camera-camera2:1.3.0")
	implementation("androidx.camera:camera-lifecycle:1.3.0")
	implementation("androidx.camera:camera-view:1.3.0")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")

	// ML Kit OCR via Google Play services delivery to avoid bundling native .so in APK
	// This mitigates 16 KB page alignment warnings on x86_64 emulator and keeps the API unchanged
	implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

	// ML Kit Document Scanner (Play services) for better receipt/document capture UX
	// Use beta1 which is available on Maven Central/Google and works with IntentSender API
	implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

	coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test.ext:junit:1.1.5")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
	androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
	androidTestImplementation("androidx.compose.ui:ui-test-junit4")
	debugImplementation("androidx.compose.ui:ui-tooling")
	debugImplementation("androidx.compose.ui:ui-test-manifest")
}