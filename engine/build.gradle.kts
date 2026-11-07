import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 안드로이드와 무관한 순수 Kotlin(JVM) 계산 엔진. :app 이 이 모듈을 가져다 쓴다.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    testLogging { events("passed", "failed"); showStandardStreams = true }
}
