import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 서명 정보는 저장소에 올리지 않는 keystore.properties(루트, .gitignore) 에서 읽는다.
// 파일이 없으면 release 는 서명 없이 빌드된다(심사용 APK 는 반드시 파일이 있는 상태에서 빌드).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.otiumframe"
    compileSdk = 35
    // AAOS Car API(CarUxRestrictionsManager, CarPropertyManager). SDK Platform 의 optional/android.car.jar 를 쓴다.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.otiumframe"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // mock : 차량 신호를 화면 속 DEMO 패널/adb 로 흉내 내는 데모 빌드 (SDK 없이 빌드 가능)
    // pleos: Pleos Vehicle SDK 로 실제 차량(또는 Pleos 에뮬레이터) 신호를 받는 빌드
    flavorDimensions += "vehicle"
    productFlavors {
        create("mock") {
            dimension = "vehicle"
            isDefault = true
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
        }
        create("pleos") {
            dimension = "vehicle"
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    "pleosImplementation"(libs.vehicle.sdk)
    testImplementation(libs.junit)
}
