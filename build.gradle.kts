import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.internal.os.OperatingSystem

plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("org.beryx.jlink") version "2.25.0"
}

group = "com.example"
version = "1.13"

repositories {
    mavenCentral()
}

val junitVersion = "5.12.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Erzeugt bei jedem Build eine build-info.properties mit Version und
// Zeitstempel (Basis für die "Version"-Angabe im Über-Dialog, siehe
// AppInfo.java). Wird aus der Vorlage src/main/resources/.../build-info.properties
// (mit ${version}/${buildTimestamp}-Platzhaltern) durch Gradles Standard-
// Token-Ersetzung beim Kopieren ins Ressourcenverzeichnis erzeugt.
val buildTimestamp: String = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    .withZone(ZoneOffset.UTC)
    .format(Instant.now())

tasks.processResources {
    inputs.property("buildTimestamp", buildTimestamp)
    filesMatching("**/build-info.properties") {
        expand("version" to project.version.toString(), "buildTimestamp" to buildTimestamp)
    }
}

application {
    mainModule.set("com.example.itrain_import_export")
    mainClass.set("com.example.itrain_import_export.HelloApplication")
}

javafx {
    version = "21.0.6"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jlink {
    imageZip.set(layout.buildDirectory.file("/distributions/app-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "app"
    }

    // Erzeugt echte, plattformspezifische Installationsdateien über das im
    // JDK enthaltene jpackage-Tool (Windows: .msi, macOS: .dmg, Linux:
    // .deb) - Aufruf über "./gradlew jpackage". WICHTIG: jpackage kann NUR
    // für die Plattform bauen, auf der es läuft (kein Cross-Compiling) -
    // für alle drei Plattformen gleichzeitig siehe
    // .github/workflows/release.yml (baut auf einem Windows-, Mac- und
    // Linux-Runner parallel).
    jpackage {
        imageName = "iTrain-Import-Export"
        installerName = "iTrain-Import-Export"
        // Bewusst identisch zu "version" oben (project.version ist jetzt
        // schon suffixfrei, "1.13") - eigenes Feld bleibt trotzdem
        // bestehen, falls App- und Projekt-Version sich künftig einmal
        // unterscheiden sollen; jpackage verlangt ohnehin ein reines
        // Zahlen-/Punkt-Format ohne Suffix wie "-SNAPSHOT".
        appVersion = "1.13"
        vendor = "Andre Ruff"

        val os = OperatingSystem.current()
        icon = when {
            os.isWindows -> file("packaging/icons/app-icon.ico")
            os.isMacOsX -> file("packaging/icons/app-icon.icns")
            else -> file("packaging/icons/app-icon-256.png")
        }.toString()

        installerType = when {
            os.isWindows -> "msi"
            os.isMacOsX -> "dmg"
            else -> "deb"
        }

        if (os.isWindows) {
            installerOptions = listOf("--win-menu", "--win-shortcut", "--win-dir-chooser")
        } else if (os.isLinux) {
            installerOptions = listOf("--linux-shortcut")
        }
    }
}
