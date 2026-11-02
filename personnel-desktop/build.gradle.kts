import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":personnel-core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

compose.desktop {
    application {
        mainClass = "com.personnel.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "GestionPersonnel"
            // CI passes -PdesktopVersion=1.0.<run number> so each installer upgrades the previous one.
            packageVersion = (findProperty("desktopVersion") as String?) ?: "1.0.0"
            description = "Gestion du personnel"
            vendor = "Gestion du personnel"
            // Bundle the whole Java runtime: the .xlsx reader needs java.xml and old .xls files
            // may use encodings from jdk.charsets. Safer than listing modules one by one.
            includeAllModules = true

            windows {
                menu = true
                shortcut = true
                menuGroup = "Gestion du personnel"
                // Keep the same id so a new installer upgrades the installed version.
                upgradeUuid = "6f1c2d1e-8a7b-4c33-9b51-2f6e0c9a4d17"
                iconFile.set(project.file("icons/icon.ico"))
            }
        }
    }
}
