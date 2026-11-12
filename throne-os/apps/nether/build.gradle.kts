plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.infinitum.nether"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.infinitum.nether"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/throne-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "edition"
    productFlavors {
        create("keter") {
            dimension = "edition"
            versionNameSuffix = "-keter"
            buildConfigField("String", "EDITION", "\"^KETER\"")
        }
        create("archangel") {
            dimension = "edition"
            versionNameSuffix = "-archangel"
            buildConfigField("String", "EDITION", "\"ARCH//ANGEL\"")
        }
    }

    val designIcons = rootProject.file("design/nether/android")
    sourceSets.getByName("main") {
        if (designIcons.isDirectory) {
            res.srcDir(designIcons)
        } else {
            res.srcDir("src/placeholder/res")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**"
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(project(":libs:nether-ui"))
    implementation(project(":libs:throne-ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
}
