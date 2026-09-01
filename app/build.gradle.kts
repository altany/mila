import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

// Signing: CI provides env vars (from GitHub secrets); local builds provide
// keystore.properties + keystore/mila.jks via scripts/build-release.sh.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env) ?: keystoreProps.getProperty(prop)

val signingStoreFile = signingValue("MILA_KEYSTORE_FILE", "storeFile")
val signingStorePassword = signingValue("MILA_KEYSTORE_PASSWORD", "storePassword")
val signingKeyAlias = signingValue("MILA_KEY_ALIAS", "keyAlias")
val signingKeyPassword = signingValue("MILA_KEY_PASSWORD", "keyPassword")
val hasSigning = signingStoreFile != null && signingStorePassword != null &&
    signingKeyAlias != null && signingKeyPassword != null

android {
    namespace = "dev.altany.mila"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.altany.mila"
        minSdk = 28
        targetSdk = 36
        versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        versionName = versionProps.getProperty("VERSION_NAME")
    }

    if (hasSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.car.app)
    implementation(libs.car.app.projected)
    implementation(libs.androidx.activity)

    testImplementation(libs.junit)
}
