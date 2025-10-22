plugins {
    id("com.android.application")
}

android {
    namespace = "com.tuempresa.acbrujula16_10_25"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tuempresa.acbrujula16_10_25"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.core:core-ktx:1.17.0")
    
    // ML Kit
    implementation("com.google.mlkit:object-detection:17.0.1")
    // implementation("com.google.mlkit:pose-detection:18.0.0-beta3")
    // implementation("com.google.mlkit:pose-detection:17.0.0-beta5") // Versión más estable
    // implementation("com.google.mlkit:pose-detection:16.0.0-beta3") // Versión no existe
    // implementation("com.google.mlkit:pose-detection:17.0.0-beta5") // Versión que existe
    // implementation("com.google.mlkit:pose-detection:17.0.0-beta4") // Versión estable que existe
    // implementation("com.google.mlkit:pose-detection:17.0.0-beta3") // Versión más estable disponible
    // Temporalmente deshabilitado hasta resolver problemas de dependencias

    // CameraX
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("androidx.camera:camera-extensions:1.4.0")

    // Para permisos de cámara fácilmente
    implementation("androidx.activity:activity:1.9.2")
}
