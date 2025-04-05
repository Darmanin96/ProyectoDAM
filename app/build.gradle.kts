plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.aplicacionmovilproyecto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aplicacionmovilproyecto"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // SMBJ para conectarse al servidor SMB
    implementation("com.hierynomus:smbj:0.11.5")

    // Bibliotecas estándar de Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Dependencias explícitas (no es necesario si ya están en libs)
    // Si no usas la declaración `libs` para estas, entonces mantenlas
    // implementation("androidx.activity:activity:1.10.0")
    // implementation("androidx.core:core:1.15.0")
    // implementation("androidx.core:core-ktx:1.15.0")
}
