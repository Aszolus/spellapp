plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.spellapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spellapp"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val checkNoInternetPermission = tasks.register("checkNoInternetPermission") {
    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    inputs.file(manifestFile)
    doLast {
        val manifestText = manifestFile.asFile.readText()
        if (manifestText.contains("android.permission.INTERNET")) {
            throw GradleException("INTERNET permission is not allowed for this app.")
        }
    }
}

val checkNoBannedNetworkDependencies = tasks.register("checkNoBannedNetworkDependencies") {
    val bannedGroups = listOf(
        "com.squareup.okhttp3",
        "com.squareup.retrofit2",
        "io.ktor",
        "com.android.volley",
    )
    doLast {
        val configurationsToCheck = listOf(
            "implementation",
            "api",
            "debugImplementation",
            "releaseImplementation",
        )

        val violations = mutableListOf<String>()
        configurationsToCheck.forEach { configName ->
            val config = configurations.findByName(configName) ?: return@forEach
            config.dependencies.forEach { dependency ->
                val group = dependency.group ?: return@forEach
                if (bannedGroups.any { group.startsWith(it) }) {
                    violations += "$configName -> $group:${dependency.name}"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Banned networking dependencies detected:\n${violations.joinToString("\n")}",
            )
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(checkNoInternetPermission)
    dependsOn(checkNoBannedNetworkDependencies)
}

dependencies {
    implementation(project(":core-data"))
    implementation(project(":core-model"))
    implementation(project(":core-ui"))
    implementation(project(":feature-spells"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
