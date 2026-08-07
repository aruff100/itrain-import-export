import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.internal.os.OperatingSystem

plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.openjfx.javafxplugin") version "0.0.13"
    // 4.1.0 statt der urspruenglichen 2.25.0 (von 2022): Die alte Fassung
    // kannte Java 21 noch nicht ("Unsupported class file major version 65").
    // Aufgefallen ist das erst beim Zusammenfassen einer nicht-modularen
    // Abhängigkeit; die gibt es inzwischen nicht mehr, aber eine aktuelle
    // Plugin-Fassung ist ohnehin die bessere Grundlage.
    id("org.beryx.jlink") version "4.1.0"
}

group = "com.example"
version = "2.0"

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

// Das Projekt kommt bewusst ohne Fremdbibliotheken aus (abgesehen von
// JavaFX und JUnit). Eine PDF-Anzeige über Apache PDFBox war zeitweise
// eingebaut und wurde wieder entfernt: Sie brachte ein zusammengefasstes
// Zusatzmodul, mehrere jlink-Sonderregeln und Reflection-Freigaben mit sich
// - viel Aufwand für eine Anleitung, die sich ebenso gut daneben in einem
// PDF-Programm öffnen lässt. Vor dem Einbinden einer neuen Abhängigkeit
// bitte den Abschnitt dazu in STATUS.md lesen.
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jlink {
    imageZip.set(layout.buildDirectory.file("/distributions/app-${javafx.platform.classifier}.zip"))
    // "--bind-services" nimmt die per ServiceLoader gefundenen Anbieter mit
    // ins Laufzeitabbild auf - insbesondere die TLS-/Krypto-Sicherheits-
    // anbieter (SunEC u.a.), die von jlink/jpackage sonst NICHT automatisch
    // erkannt werden (jdeps sieht nur statisch referenzierte Module). Ohne
    // sie schlägt jede HTTPS-Verbindung im fertigen Installer fehl, während
    // sie unter "gradle run" (volle JDK) funktioniert - genau der Fall bei der
    // Update-Prüfung (UpdateChecker ruft die Gist-URL per HTTPS ab).
    // "--compress zip-6" statt des alten "--compress 2": Die Zahlenform ist
    // seit JDK 21 veraltet ("Das Argument 2 für --compress ist veraltet")
    // und soll in einer künftigen Java-Version entfallen. zip-6 entspricht
    // der bisherigen Stufe 2 (ausgewogen zwischen Größe und Geschwindigkeit).
    options.set(listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages", "--bind-services"))

    launcher {
        // MUSS mit jpackage.imageName weiter unten übereinstimmen.
        //
        // Stand hier "app", während imageName "iTrain-Import-Export" war,
        // brach der Windows-Installer beim Verpacken ab:
        //   error LGHT0204 : ICE67: The shortcut '...' is a non-advertised
        //   shortcut with a file target, but the target file does not exist.
        //   error LGHT0204 : ICE69: 'file...' references invalid file.
        //
        // Grund: Aus dem Namensunterschied entstehen ZWEI Startprogramme.
        // jpackage legt fuer jedes eine Verknuepfung im Startmenue und auf
        // dem Desktop an (--win-menu, --win-shortcut), also vier Stueck -
        // im Protokoll als bundle.wxf Zeile 11/21/30/40 zu sehen. Zwei davon
        // zeigen auf "app.exe", die es im Abbild gar nicht gibt: Dort liegt
        // nur "iTrain-Import-Export.exe". Die ICE-Pruefung von WiX faellt
        // genau darueber.
        //
        // Bis Version 1.16 lief der Build mit demselben Namensunterschied
        // durch; das jlink-Plugin 2.25.0 hat ihn offenbar verdeckt. Der
        // Wechsel auf 4.1.0 (noetig fuer Java 21) legt ihn offen. Linux und
        // macOS stoert er nicht - .deb und .dmg pruefen Verknuepfungsziele
        // nicht so streng wie eine .msi.
        name = "iTrain-Import-Export"
    }

    // Erzeugt echte, plattformspezifische Installationsdateien über das im
    // JDK enthaltene jpackage-Tool (Windows: .msi, macOS: .dmg, Linux:
    // .deb) - Aufruf über "./gradlew jpackage". WICHTIG: jpackage kann NUR
    // für die Plattform bauen, auf der es läuft (kein Cross-Compiling) -
    // für alle drei Plattformen gleichzeitig siehe
    // .github/workflows/release.yml (baut auf einem Windows-, Mac- und
    // Linux-Runner parallel).
    jpackage {
        val os = OperatingSystem.current()
        // Betriebssystem-Kürzel für den Installer-Dateinamen, damit ein
        // Anwender im Proton-Drive-Ordner sofort sieht, welche Datei zu
        // seinem System gehört (nicht nur an der .msi/.dmg/.deb-Endung). Der
        // fertige Installer heißt dann z.B. "iTrain-Import-Export-Windows-1.18.msi"
        // (jpackage hängt die appVersion automatisch hinten an). Der
        // installierte Programmname selbst bleibt "iTrain-Import-Export"
        // (imageName), nur der Name der Installationsdatei bekommt das Kürzel.
        val osLabel = when {
            os.isWindows -> "Windows"
            os.isMacOsX -> "macOS"
            else -> "Linux"
        }
        imageName = "iTrain-Import-Export"
        installerName = "iTrain-Import-Export-$osLabel"
        // Bewusst identisch zu "version" oben (project.version ist jetzt
        // schon suffixfrei, "1.18") - eigenes Feld bleibt trotzdem
        // bestehen, falls App- und Projekt-Version sich künftig einmal
        // unterscheiden sollen; jpackage verlangt ohnehin ein reines
        // Zahlen-/Punkt-Format ohne Suffix wie "-SNAPSHOT".
        appVersion = "2.0"
        vendor = "Andre Ruff"

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
            // Falls hier je wieder "light.exe ... exited with 204" auftaucht:
            // Das ist WiX-Code LGHT0204, eine fehlgeschlagene ICE-Pruefung.
            // Welche, verschweigt jpackage - dann voruebergehend "--verbose"
            // anhaengen, damit die ICE-Nummer im Protokoll steht.
            installerOptions = listOf("--win-menu", "--win-shortcut", "--win-dir-chooser")
        } else if (os.isLinux) {
            installerOptions = listOf("--linux-shortcut")
        }
    }
}
