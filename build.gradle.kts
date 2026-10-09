import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.internal.os.OperatingSystem

plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "2.0.1"
    id("org.openjfx.javafxplugin") version "0.1.0"
    // 4.1.0 statt der urspruenglichen 2.25.0 (von 2022): Die alte Fassung
    // kannte Java 21 noch nicht ("Unsupported class file major version 65").
    // Aufgefallen ist das erst beim Zusammenfassen einer nicht-modularen
    // Abhängigkeit; die gibt es inzwischen nicht mehr, aber eine aktuelle
    // Plugin-Fassung ist ohnehin die bessere Grundlage.
    id("org.beryx.jlink") version "4.1.1"
}

group = "com.example"
version = "3.1"

repositories {
    mavenCentral()
}

val junitVersion = "5.12.1"

// Reiner IPv4-Netzwerkstapel. Wird an ALLE Startwege weitergereicht
// ("gradle run", jlink-Startprogramm, jpackage-Installer), weil die
// mDNS-Suche im Menü "BiDiB" sonst unter Windows auf Schnittstellen ohne
// IPv6 mit "Invalid argument: setsockopt" abbricht - ausführliche
// Begründung in HelloApplication.preferIpv4Stack().
val PREFER_IPV4_STACK = "-Djava.net.preferIPv4Stack=true"

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
    // netBiDiB (Menü "BiDiB"): jbidibc ist die von bidib.org selbst empfohlene
    // Java-Referenzbibliothek fuer das BiDiB-Protokoll (auch von JMRI genutzt),
    // siehe bidib.org/support/intro_e.html. Sie uebernimmt die eigentliche
    // Nachrichten-Kodierung samt Pairing-Zustandsautomat - deren genaue Byte-
    // Werte stehen nur in einer nicht oeffentlichen Kopfdatei (bidib_messages.h,
    // nur ueber die Implementierer-Mailgroup erhaeltlich).
    //
    // Lizenz: jbidibc steht unter der GPL 3.0 (Copyleft). Deshalb steht seit
    // 3.1 das ganze Programm unter der GPL 3.0 (LICENSE im Projektstamm,
    // Quellcode oeffentlich auf GitHub). Die Lizenzen ALLER mitgelieferten
    // Bibliotheken stehen in src/main/resources/.../third-party-licenses.txt
    // (Hilfe -> Ueber -> Lizenzhinweise) - bei neuen oder geaenderten
    // Abhaengigkeiten dort nachziehen.
    implementation("org.bidib.jbidib:jbidibc-netbidib:2.0.44") {
        // jbidibc-core zieht zwei widerspruechliche JAXB-Implementierungen mit
        // (altes javax.xml.bind + neues jakarta.xml.bind) - beide melden sich
        // beim jlink-Schritt als dasselbe Modul "java.xml.bind" und blockieren
        // sich gegenseitig. Die alte javax-Fassung ausschliessen, jakarta reicht.
        exclude(group = "javax.xml.bind", module = "jaxb-api")
        // Gleiches Problem bei "javax.activation" vs. "jakarta.activation" -
        // beide exportieren das Paket javax.activation, jakarta reicht auch hier.
        exclude(group = "javax.activation", module = "javax.activation-api")
    }

    // Serielles BiDiB (USB-Interfaces wie GBMboost/IF2 am virtuellen COM-Port,
    // siehe bidib.org/transport/bidib_seriell_e.html): jbidibc-jserialcomm
    // ist die Anbindung von jbidibc an jSerialComm - eine reine Java-
    // Bibliothek mit mitgelieferten nativen Treibern fuer Windows/Linux/macOS,
    // ohne Installation eines eigenen COM-Port-Treibers (Apache-2.0/LGPL).
    // Zieht jbidibc-serial (Framing, CRC, Magic) transitiv mit. Auswahl
    // gegenueber rxtx/purejavacomm: aktiv gepflegt, JPMS-faehig.
    implementation("org.bidib.jbidib:jbidibc-jserialcomm:2.0.44") {
        exclude(group = "javax.xml.bind", module = "jaxb-api")
        exclude(group = "javax.activation", module = "javax.activation-api")
    }

    // DNS Service Discovery (mDNS) zum automatischen Finden von netBiDiB-
    // Servern im lokalen Netz (siehe BidibDiscovery.java). jbidibc bringt das
    // nicht mit - JmDNS ist die uebliche, schlanke Java-Bibliothek dafuer
    // (Apache-2.0-Lizenz, unproblematisch).
    implementation("org.jmdns:jmdns:3.5.9")

    // JmDNS ist selbst nur ein automatisches Modul (kein eigenes module-info)
    // und kann daher seine eigene Abhaengigkeit zu slf4j-api nicht deklarieren
    // - obwohl slf4j-api ueber jbidibc-netbidib transitiv mitkommt, landet es
    // ohne einen EIGENEN, direkten requires-Eintrag (siehe module-info.java)
    // nicht zuverlaessig auf dem Modulpfad. Ohne diese Zeile: beim Oeffnen von
    // "BiDiB verbinden" ein NoClassDefFoundError fuer org.slf4j.LoggerFactory.
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Gleiches Muster wie bei slf4j-api oben, nur fuer jbidibc-core statt
    // JmDNS: jbidibc-core ist ebenfalls nur ein automatisches Modul und kann
    // seine eigenen Laufzeit-Abhaengigkeiten (JSON-Pairing-Speicher via
    // Jackson, XML-Kodierung via JAXB/Woodstox) nicht selbst einfordern.
    // Versionen wie von "./gradlew dependencies" fuer jbidibc-netbidib:2.0.44
    // aufgeloest, damit hier keine zweite, abweichende Fassung hineinkommt.
    // Ohne diese Zeilen: NoClassDefFoundError fuer
    // com.fasterxml.jackson.databind.Module beim Verbinden.
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.5")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.9")
    implementation("com.fasterxml.woodstox:woodstox-core:7.1.0")
    implementation("commons-io:commons-io:2.22.0")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    implementation("org.apache.commons:commons-collections4:4.5.0")
    implementation("org.bushe:eventbus:1.4")
    // jaxb-runtime und jakarta.xml.bind-api verlangen "requires transitive
    // jakarta.activation" - der Anbieter dieses Moduls steht in seiner
    // eigenen POM aber nur im Laufzeit-, nicht im Kompilier-Bereich. Ohne
    // diese Zeile bricht bereits "compileJava" ab ("module not found:
    // jakarta.activation"), noch vor jedem Programmstart.
    implementation("com.sun.activation:jakarta.activation:1.2.2")

    // Anbieter-Implementierung fuer slf4j-api (siehe module-info.java) - ohne
    // sie verschwinden alle Protokollmeldungen von jbidibc kommentarlos.
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Mitgelieferte Decoder-Vorlagen (seit 2.5): je Sprachordner unter
// src/main/resources/.../decoder-templates/<sprache>/ wird beim Build eine
// index.txt mit den Dateinamen aller .csv erzeugt. Grund: Ressourcen lassen
// sich zur Laufzeit nicht auflisten - weder im JAR noch im jlink-Abbild -,
// das Programm muss also wissen, welche Dateien es kopieren soll (siehe
// DecoderTemplateBundle). Die Datei wird NUR ins Build-Verzeichnis
// geschrieben, nie in den Quellbaum; eine neue Vorlage im Ordner ist damit
// beim naechsten Build automatisch dabei.
tasks.processResources {
    doLast {
        val root = destinationDir.resolve("com/example/itrain_import_export/decoder-templates")
        root.listFiles { f -> f.isDirectory }?.forEach { dir ->
            val names = dir.listFiles { f -> f.isFile && f.name.endsWith(".csv", ignoreCase = true) }
                ?.map { it.name }?.sorted() ?: emptyList()
            dir.resolve("index.txt").writeText(names.joinToString("\n") + "\n", Charsets.UTF_8)
            logger.lifecycle("decoder-templates/${dir.name}: ${names.size} Vorlagen im Index")
        }
    }
}

// Nur die BiDiB-Bibliothek (org.bidib.*) ausfuehrlich protokollieren - der
// eigentliche Pairing-Ablauf (wer hat wem eine Anfrage geschickt, welche
// Antwort kam zurueck, wo genau bleibt es haengen) steht ausschliesslich in
// deren LOGGER.info(...)-Aufrufen. Alles andere bleibt bei "warn" ruhig,
// sonst verschwindet die eigentliche Meldung im Rauschen. Wirkt nur bei
// "./gradlew run" - der fertige Installer/jlink-Start bleibt unverändert
// leise (siehe jlink.launcher weiter unten, falls das je noetig wird).
tasks.named<JavaExec>("run") {
    jvmArgs(
        "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn",
        "-Dorg.slf4j.simpleLogger.log.org.bidib=debug",
        // ECoS-Datenverkehr (Befehle/Antworten im Klartext, siehe EcosConnection).
        "-Dorg.slf4j.simpleLogger.log.ecos=info",
        "-Dorg.slf4j.simpleLogger.log.com.example.itrain_import_export=info",
        "-Dorg.slf4j.simpleLogger.showDateTime=true",
        "-Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss.SSS",
        // Siehe HelloApplication.preferIpv4Stack(): ohne diesen Schalter
        // scheitert die mDNS-Suche unter Windows auf Schnittstellen ohne
        // IPv6 mit "Invalid argument: setsockopt".
        PREFER_IPV4_STACK
    )
}

jlink {
    // ACHTUNG: KEIN fuehrender Schraegstrich. "/distributions/..." ist ein
    // absoluter Pfad - Gradle legt die ZIP dann NICHT unter build/distributions
    // ab, sondern im Wurzelverzeichnis des Laufwerks (Windows: C:\distributions\,
    // Linux: /distributions, wo der Build mangels Rechten scheitert).
    imageZip.set(layout.buildDirectory.file("distributions/app-${javafx.platform.classifier}.zip"))
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

        // Gilt für das von jlink erzeugte Startprogramm; der
        // jpackage-Installer bekommt denselben Schalter weiter unten
        // gesondert mit (jpackage übernimmt diesen hier nicht automatisch).
        jvmArgs = listOf(PREFER_IPV4_STACK)
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
        // Bis "2.4.1" identisch zu "version" oben; jetzt zum ersten Mal
        // bewusst unterschiedlich, weil jpackage ein reines Zahlen-/
        // Punkt-Format ohne Suffix verlangt ("-SNAPSHOT", "beta" etc.
        // waeren ein Fehler) - "version" oben darf das "beta" tragen (zeigt
        // im Ueber-Dialog und beim Update-Check als Text an), appVersion
        // hier bleibt rein numerisch.
        appVersion = "3.1"
        vendor = "Andre Ruff"

        // Wird von jpackage als "--java-options" in das installierte
        // Startprogramm eingebacken (siehe launcher.jvmArgs oben - jpackage
        // erbt diese Angabe nicht, sie muss hier wiederholt werden).
        jvmArgs = listOf(PREFER_IPV4_STACK)

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
