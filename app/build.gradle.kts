import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val secretsPropsFile = rootProject.file("secrets.properties")
val secrets = Properties()
if (secretsPropsFile.exists()){
    secrets.load(secretsPropsFile.inputStream())
}


android {
    namespace = "com.example.loamomo"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.loamomo"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MOMO_PACKAGE_NAME", "\"${secrets["MOMO_PACKAGE_NAME"]}\"")
        buildConfigField("String", "BLUETOOTH_MAC_ADDRESS", "\"${secrets["BLUETOOTH_MAC_ADDRESS"]}\"")
        buildConfigField("String", "BLUETOOTH_UUID", "\"${secrets["BLUETOOTH_UUID"]}\"")
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
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}