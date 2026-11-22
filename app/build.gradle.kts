plugins { id("com.android.application"); id("com.google.protobuf") }
android {
    namespace = "dev.inter129.mochikeys"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.inter129.mochikeys"
        minSdk = 30
        targetSdk = 36
        // 릴리스마다 versionCode를 1씩 올린다(Obtainium·시스템이 업데이트를 판단하는 기준)
        versionCode = 3
        versionName = "0.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources { noCompress += "data" }
    // 릴리스 서명: 키 저장소 위치와 비밀번호는 환경 변수로만 받는다(저장소에 넣지 않는다).
    // 값이 없으면 로컬 확인용으로 디버그 키로 서명한다. 배포용 빌드는 MOCHI_REQUIRE_SIGNING=1로 이를 막는다.
    val keystorePath = System.getenv("MOCHI_KEYSTORE")
    val hasReleaseKey = !keystorePath.isNullOrEmpty() && file(keystorePath).exists()
    if (!hasReleaseKey && System.getenv("MOCHI_REQUIRE_SIGNING") == "1") error("MOCHI_KEYSTORE is not set; refusing to build an unsigned release")
    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = file(keystorePath!!)
            storePassword = System.getenv("MOCHI_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("MOCHI_KEY_ALIAS")
            keyPassword = System.getenv("MOCHI_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false   // Mozc JNI·protobuf 클래스 이름이 바뀌면 안 되므로 난독화하지 않는다
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }
    // 릴리스는 CPU별 APK로 나눈다(Mozc 라이브러리가 CPU마다 약 15MB라 하나로 합치면 80MB가 넘는다).
    // 디버그 빌드는 개발 편의를 위해 나누지 않고 전부 담는다.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release") }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies { implementation(project(":core")) }
dependencies { implementation("androidx.core:core-ktx:1.17.0") }
dependencies { implementation("com.google.protobuf:protobuf-javalite:4.36.2") }
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.36.2" }
    generateProtoTasks { all().configureEach { builtins { create("java") { option("lite") } } } }
}
dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
dependencies {
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
}
