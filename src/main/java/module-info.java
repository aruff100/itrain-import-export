module com.example.itrain_import_export {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.xml;
    requires java.prefs;
    requires java.net.http;
    // java.desktop: Desktop.browse (Download-Links im Browser oeffnen) und
    // Desktop.mail (Speichern & Senden), siehe UpdateDialog und MailSender.
    requires java.desktop;

    // Menü "BiDiB" (netBiDiB-Verbindung + Pairing, siehe BidibConnectionDialog):
    // jbidibc-netbidib/-messages/-core sind unbenannte (automatische) Module -
    // ihr Name leitet sich aus dem Jar-Dateinamen ab (Bindestriche -> Punkte).
    // javax.jmdns bringt seinen Modulnamen selbst im Manifest mit.
    requires jbidibc.netbidib;
    requires jbidibc.messages;
    requires jbidibc.core;
    requires javax.jmdns;
    // Serielles BiDiB (USB-Interfaces am COM-Port, siehe BidibConnectionDialog
    // und build.gradle.kts): jbidibc-jserialcomm/-serial sind wieder
    // automatische Module; jSerialComm selbst bringt eine module-info mit
    // (echtes Modul com.fazecast.jSerialComm, mit eingebetteten nativen
    // Treibern). Die beiden automatischen koennen es nicht einfordern -
    // deshalb hier ausdruecklich, sonst fehlt es auf dem Modulpfad.
    requires jbidibc.jserialcomm;
    requires jbidibc.serial;
    requires com.fazecast.jSerialComm;
    // javax.jmdns braucht das zur Laufzeit (siehe Kommentar in build.gradle.kts),
    // kann es aber als automatisches Modul nicht selbst einfordern.
    requires org.slf4j;

    // jbidibc-core braucht diese zur Laufzeit ebenfalls (LocalPairingStore
    // speichert das Pairing als JSON ueber Jackson; die XML-Kodierung der
    // BiDiB-Konfiguration laeuft ueber JAXB/Woodstox) - als automatisches
    // Modul kann es das aber genauso wenig selbst einfordern wie org.slf4j
    // oben. Jeweils nur das oberste Modul der Abhaengigkeitskette ist noetig,
    // der Rest kommt darueber transitiv mit (siehe "requires transitive" in
    // deren eigenen module-info, mit "jar --describe-module" nachvollzogen):
    // - com.fasterxml.jackson.datatype.jsr310 zieht jackson.databind/.core/.annotation.
    // - org.glassfish.jaxb.runtime zieht java.xml.bind, com.sun.istack.runtime,
    //   com.sun.xml.txw2 und jakarta.activation.
    // - com.ctc.wstx (Woodstox) zieht org.codehaus.stax2.
    // Ohne diese Zeilen: NoClassDefFoundError fuer com.fasterxml.jackson.databind.Module
    // beim Verbinden (LocalPairingStore.load()).
    requires com.fasterxml.jackson.datatype.jsr310;
    requires org.glassfish.jaxb.runtime;
    requires com.ctc.wstx;
    requires org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires org.apache.commons.collections4;
    requires eventbus;

    // Ohne eigene Anbieter-Implementierung meldet slf4j-api nur "No SLF4J
    // providers were found" und verwirft jede Protokollmeldung von jbidibc
    // (Pairing-Ablauf, Fehler beim Verbindungsaufbau) kommentarlos - genau
    // die Meldungen, die bei einem haengenden Pairing die Ursache zeigen
    // wuerden. slf4j-simple schreibt diese Meldungen auf die Konsole (siehe
    // Log-Filter fuer "org.bidib" in build.gradle.kts).
    requires org.slf4j.simple;

    opens com.example.itrain_import_export to javafx.fxml;
    exports com.example.itrain_import_export;
}