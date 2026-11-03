import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val uiTestBuild = providers.gradleProperty("DSH_UI_TEST_BUILD").orNull == "true"

val releaseStoreFile = localProps.getProperty("release.storeFile")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank()

// 版本号统管：由根 gradle.properties 提供（避免散落硬编码，CI 可按 tag 覆盖）
val dsvVersionName =
    (project.findProperty("DSH_VERSION_NAME") as String?)?.takeIf { it.isNotBlank() } ?: "3.2.7"
val dsvVersionCode =
    ((project.findProperty("DSH_VERSION_CODE") as String?)?.takeIf { it.isNotBlank() } ?: "71").toInt()

android {
    namespace = "com.dsh.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = if (uiTestBuild) "com.dsh.mobile.uitest" else "com.dsh.mobile"
        buildConfigField("boolean", "UI_TEST_BUILD", uiTestBuild.toString())
        minSdk = 29
        targetSdk = 36
        versionCode = dsvVersionCode
        versionName = dsvVersionName

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // debug 签名不覆写：交给 AGP 默认（自动生成 ~/.android/debug.keystore，CI/本机均可）。
        // 若在此显式指向该 keystore 路径，全新 runner 上文件不存在会导致 :app:validateSigningDebug 失败。
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("compact") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.maxHeapSize = "2g"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
