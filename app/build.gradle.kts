plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase(구글 로그인·클라우드 저장)는 app/google-services.json 이 있을 때만 켠다.
// 파일이 없으면 src/nocloud(로그인 없이 기기 안에만 저장), 있으면 src/cloud(Firebase) 소스가 컴파일된다.
val hasCloud = file("google-services.json").exists()
if (hasCloud) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "kr.landscapebid.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.landscapebid.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets.getByName("main").java.srcDir(if (hasCloud) "src/cloud/java" else "src/nocloud/java")
}

dependencies {
    implementation(project(":engine"))

    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    if (hasCloud) {
        implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
        implementation("com.google.firebase:firebase-auth")
        implementation("com.google.firebase:firebase-firestore")
        implementation("androidx.credentials:credentials:1.3.0")
        implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
        implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    }
}
