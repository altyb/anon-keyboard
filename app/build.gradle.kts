plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.altayeb.anon"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.altayeb.anon"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    // Release key lives outside the repo; set ANON_KS / ANON_KS_PASS / ANON_KEY_ALIAS to sign.
    signingConfigs {
        create("release") {
            System.getenv("ANON_KS")?.let { storeFile = file(it) }
            storePassword = System.getenv("ANON_KS_PASS")
            keyAlias = System.getenv("ANON_KEY_ALIAS")
            keyPassword = System.getenv("ANON_KS_PASS")
        }
    }
    buildTypes {
        release {
            if (System.getenv("ANON_KS") != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += "tsv" }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    testImplementation("junit:junit:4.13.2")
}

// Privacy guarantee: the app must never be able to reach the network. Fails the build if any
// dependency or edit adds the INTERNET permission to the merged manifest.
androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val manifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        val check = tasks.register("check${name}NoInternet") {
            inputs.file(manifest)
            doLast {
                val text = manifest.get().asFile.readText()
                check("android.permission.INTERNET" !in text) { "INTERNET permission found in merged manifest" }
            }
        }
        afterEvaluate { tasks.named("assemble$name") { dependsOn(check) } }
    }
}
