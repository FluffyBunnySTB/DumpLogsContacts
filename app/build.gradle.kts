plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.za.digiscan.dumpcontact"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.za.digiscan.dumpcontact"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("C:/Users/schelteg/Documents/OneDrive - gerhardus03/OneDrive/Documents/Keys/AndroidDevelopmentKeystore.jks")
            storePassword = "Ul47sjYXanQru4i8ouL80q7QwwXIJl"
            keyAlias = "LogDump"
            keyPassword = "JLf4yCQMNgMgtpKD4pA2Ch8vZjmzoA"
        }
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}