plugins {
    id("com.android.application")
}

android {
    namespace = "dk.kasvan.gladbooth"
    compileSdk = 35

    defaultConfig {
        applicationId = "dk.kasvan.gladbooth"
        minSdk = 29          // Android 10: billeder kan skrives til billedmappen uden ekstra tilladelser
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.0.0-lokal"
    }

    // Fast nøgle, så nye versioner kan installeres oven på de gamle.
    // Nøglen ligger åbent i repo'et (se README) – den beskytter ikke noget hemmeligt.
    signingConfigs {
        create("fast") {
            storeFile = file("gladbooth.keystore")
            storePassword = "gladbooth"
            keyAlias = "gladbooth"
            keyPassword = "gladbooth"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fast")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
