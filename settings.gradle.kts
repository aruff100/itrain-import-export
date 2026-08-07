// Erlaubt Gradle, das in build.gradle.kts geforderte JDK 21 bei Bedarf selbst
// herunterzuladen, statt den Build abzubrechen ("Cannot find a Java
// installation ... matching languageVersion=21"). Nötig, weil das Projekt
// per Toolchain fest JDK 21 verlangt - ohne dieses Plugin muss auf jedem
// Rechner vorher von Hand ein JDK 21 installiert und auffindbar sein.
// In IntelliJ fiel das bisher nicht auf, weil dort ein JDK konfiguriert ist;
// beim Aufruf über gradlew von der Kommandozeile sucht Gradle selbst.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "iTrain_import_export"
