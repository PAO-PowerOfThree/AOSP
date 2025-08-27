plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.testui"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.testui"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        useLibrary("android.car")
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
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.car.app:app:1.7.0")
    implementation("com.github.rohankandwal:indicatorseekbar:v2.1.4")

// Material Components
    implementation("com.google.android.material:material:1.10.0")

// Glide for image loading (without kapt/compiler)
    implementation("com.github.bumptech.glide:glide:4.15.1")

// CircleProgress library
    implementation("com.github.lzyzsd:circleprogress:1.2.1")

// CircularSeekBar library
    implementation ("me.tankery.lib:circularSeekBar:1.4.2")

    // Add these for voice functionality:
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

}





//    implementation (libs.material.v1120)
//    implementation (libs.circleprogress)
//    implementation (libs.circularseekbar)
//    implementation (libs.androidx.cardview)
//
//    implementation (libs.glide)
//
//
//
//    implementation (libs.glide.v4151)
//    annotationProcessor (libs.compiler)
//}