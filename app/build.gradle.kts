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
	namespace = "com.example.notescanner"
	compileSdk = 34
	buildToolsVersion = "35.0.0"

	defaultConfig {
		applicationId = "com.example.notescanner"
		minSdk = 24
		targetSdk = 34
		versionCode = 2
		versionName = "1.0.1"
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
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			if (hasSigning) {
				signingConfig = signingConfigs.getByName("release")
			}
		}
		debug {
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

	// Workaround Windows file-lock issues by using a fresh build dir
	setBuildDir("build_android")
}

dependencies {
	implementation("androidx.core:core-ktx:1.12.0")
	implementation("androidx.activity:activity-compose:1.9.2")

	implementation(platform("androidx.compose:compose-bom:2024.04.01"))
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-graphics")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.compose.ui:ui-tooling-preview")

	implementation("androidx.camera:camera-camera2:1.3.0")
	implementation("androidx.camera:camera-lifecycle:1.3.0")
	implementation("androidx.camera:camera-view:1.3.0")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")

	// ML Kit OCR via Google Play services delivery to avoid bundling native .so in APK
	// This mitigates 16 KB page alignment warnings on x86_64 emulator and keeps the API unchanged
	implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

	coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

	testImplementation("junit:junit:4.13.2")
	androidTestImplementation("androidx.test.ext:junit:1.1.5")
	androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
	androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
	androidTestImplementation("androidx.compose.ui:ui-test-junit4")
	debugImplementation("androidx.compose.ui:ui-tooling")
	debugImplementation("androidx.compose.ui:ui-test-manifest")
}