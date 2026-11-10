plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.infinitum.throne.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.infinitum.throne.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
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
}

dependencies {
    implementation(project(":libs:throne-core"))
    implementation(project(":libs:throne-ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
