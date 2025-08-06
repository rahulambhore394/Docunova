plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
        kotlin("kapt")
}

android {
    namespace = "com.developer_rahul.docunova"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.developer_rahul.docunova"
        minSdk = 28
        targetSdk = 35
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
    buildFeatures {
        viewBinding = true
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

//    retrfit
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation ("com.android.volley:volley:1.2.1")
// ml kit
    implementation ("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

//    roomdb
    // Room components
    implementation ("androidx.room:room-runtime:2.6.1")
    kapt ("androidx.room:room-compiler:2.6.1")

// Kotlin coroutine support for Room
    implementation ("androidx.room:room-ktx:2.6.1")
    implementation ("com.github.bumptech.glide:glide:4.16.0")
//    kapt "com.github.bumptech.glide:compiler:4.16.0"
    implementation ("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

//    OCR with ML kit
    implementation ("com.google.mlkit:text-recognition:16.0.0")
    implementation ("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
//    for image lebeling
    implementation ("com.google.mlkit:image-labeling:17.0.7")

//    for pdf formating
    implementation ("com.itextpdf:itext7-core:7.2.5")
    //for word file
    implementation ("org.apache.poi:poi-ooxml:5.2.3")
    implementation ("org.apache.xmlbeans:xmlbeans:5.1.1")
    implementation ("org.apache.commons:commons-compress:1.23.0")
    implementation ("commons-io:commons-io:2.11.0")

    // ML Kit Translation
    implementation ("com.google.mlkit:translate:17.0.2")
    implementation ("com.google.mlkit:language-id:17.0.4")


    // For image rotation handling
    implementation ("androidx.exifinterface:exifinterface:1.3.6")

}