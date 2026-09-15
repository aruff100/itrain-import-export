package com.example.itrain_import_export;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                HelloApplication.class.getResource("hello-view.fxml"));
        Parent root = fxmlLoader.load();
        HelloController controller = fxmlLoader.getController();

        Scene scene = new Scene(root);
        // Für das Menü "Funktionen" der Decoder- und Systeme-Fenster: Die
        // brauchen einen Weg zurück zum Hauptfenster, das selbst - anders als
        // die beiden Zusatzfenster - kein Singleton-Öffnen-oder-Vorn-Muster
        // kennt. Muss vor dem ersten möglichen Öffnen eines Zusatzfensters
        // stehen, also gleich hier.
        controller.registerMainWindow(stage);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setTitle(I18n.getInstance().t("app.title"));
        stage.getIcons().addAll(loadAppIcons());
        stage.setScene(scene);
        // Beim Schließen ggf. das Backup der aktuell offenen Datei
        // aufräumen, falls seither weder geändert noch gespeichert wurde -
        // und die Zusatzfenster mitschließen: Sie haben bewusst keinen
        // Besitzer (sonst lägen sie dauerhaft über dem Hauptfenster), würden
        // das Programm also sonst am Leben halten. Das Decoder-Fenster räumt
        // sein eigenes Backup dabei selbst auf (eigener OnCloseRequest).
        stage.setOnCloseRequest(event -> {
            controller.onAppClosing();
            DecoderWindow.closeIfOpen();
            SystemsWindow.closeIfOpen();
            BidibConnectionManager.getInstance().closeAll();
        });

        // Lage und Größe vom letzten Mal übernehmen. Beim allerersten Start
        // gilt die Vorgabe: doppelte Breite und 30% mehr Höhe gegenüber der
        // früheren Größe (1200x750) - im Ribbon stehen jetzt mehrere
        // beschriftete Schaltflächen, und die Kategorie-Tabellen sind breiter
        // besser lesbar. WindowState begrenzt das auf den tatsächlich
        // vorhandenen Platz (2400px sind breiter als viele Bildschirme) und
        // prüft eine gemerkte Lage gegen die heute angeschlossenen Monitore.
        WindowState.apply(stage, "main", 2400, 975);

        // Ab hier erscheinen alle Dialoge über dem Hauptfenster - auch die,
        // die keinen Besitzer setzen und sonst auf dem Hauptbildschirm
        // landeten. Muss vor stage.show() stehen, damit schon der
        // Ersteinrichtungs-Dialog weiter unten erfasst ist.
        DialogPlacement.install(stage);

        // Und ab hier bekommt jedes neu geöffnete Fenster das eingestellte
        // Farbschema, ohne dass die jeweilige Aufrufstelle daran denken muss -
        // siehe ThemeManager. Ebenfalls vor stage.show(), aus demselben Grund.
        ThemeManager.install();

        stage.show();

        // Nur beim allerersten Start (solange noch keiner der drei Pfade
        // gesetzt ist) - schlägt OS-abhängige Standardordner vor, siehe
        // FirstRunDialog. Bewusst NACH stage.show(), damit der Dialog als
        // modales Fenster über dem bereits sichtbaren Hauptfenster erscheint.
        FirstRunDialog.showIfNeeded(stage);

        // Mitgelieferte Decoder-Vorlagen in den Decoder-Ordner übertragen -
        // beim ersten Start, nach einer neuen Programmversion und bei jedem
        // Sprachwechsel (siehe DecoderTemplateBundle). Nach dem
        // Ersteinrichtungs-Dialog, damit der Ordner schon bekannt ist.
        DecoderTemplateBundle.syncIfNeeded();
        I18n.getInstance().addLanguageChangeListener(DecoderTemplateBundle::syncIfNeeded);

        // Stiller Update-Check im Hintergrund (siehe UpdateChecker/AppSettings.
        // getAutoUpdateCheckEnabled(), Standard: an, abschaltbar in den
        // Voreinstellungen → Ansicht). Bewusst nur bei tatsächlich verfügbarem
        // Update ein Fenster zeigen - bei Offline-Betrieb oder sonstigem
        // Fehlschlag bleibt der Start-Check komplett unauffällig; der manuelle
        // Menüpunkt Hilfe → Update zeigt in diesem Fall trotzdem eine
        // Fehlermeldung, siehe HelloController.onCheckForUpdate().
        if (AppSettings.getInstance().getAutoUpdateCheckEnabled()) {
            UpdateChecker.checkAsync(result -> {
                if (result.success && result.updateAvailable) {
                    UpdateDialog.showUpdateAvailable(stage, result);
                }
            });
        }
    }

    /**
     * Lädt das Programm-Icon (app-icon.png, eigene Grafik) aus den Ressourcen.
     * Stage.getIcons() setzt darüber das Fenster-/Taskleisten-Icon
     * plattformübergreifend (Windows, Linux, macOS-Dock als Fallback).
     * Für ein natives .ico/.icns über jpackage müsste die Datei zusätzlich
     * konvertiert und im jlink/jpackage-Build referenziert werden.
     */
    private static Image[] loadAppIcons() {
        try (InputStream in = HelloApplication.class.getResourceAsStream("app-icon.png")) {
            if (in == null) {
                System.err.println("Programm-Icon app-icon.png nicht in den Ressourcen gefunden.");
                return new Image[0];
            }
            return new Image[]{new Image(in)};
        } catch (IOException ex) {
            System.err.println("Programm-Icon konnte nicht geladen werden: " + ex.getMessage());
            return new Image[0];
        }
    }

    public static void main(String[] args) {
        preferIpv4Stack();
        // Muss vor der ersten Protokollausgabe stehen (slf4j-simple merkt
        // sich sein Ausgabeziel beim ersten Zugriff), siehe BidibLog.
        BidibLog.start();
        launch(args);
    }

    /**
     * Wird von JavaFX beim Beenden aufgerufen (letztes Fenster geschlossen).
     * <p>
     * Die BiDiB-Bibliothek startet eigene Faeden, die keine Daemon-Faeden
     * sind (Port-, Sende- und Empfangsarbeiter). Bleibt davon einer haengen -
     * etwa weil die Gegenseite nicht mehr antwortet -, laeuft die JVM nach
     * dem Schliessen des letzten Fensters weiter und das Programm "beendet
     * sich nicht". Deshalb erst die Verbindungen trennen und danach
     * ausdruecklich beenden.
     */
    @Override
    public void stop() {
        BidibConnectionManager.getInstance().closeAll();
        Platform.exit();
        System.exit(0);
    }

    /**
     * Erzwingt einen reinen IPv4-Netzwerkstapel, BEVOR irgendetwas im Programm
     * das Netzwerk anfasst.
     * <p>
     * Hintergrund: Unter Windows legt die JVM standardmaessig Sockets im
     * Doppelbetrieb (IPv6 mit IPv4-Abbildung) an. Die mDNS-Suche
     * ({@link BidibDiscovery}) muss auf einer Multicast-Gruppe lauschen und
     * dafuer die Netzwerkschnittstelle am Socket setzen. Ist auf der
     * betreffenden Schnittstelle IPv6 abgeschaltet oder nicht eingerichtet,
     * lehnt Windows genau diesen Aufruf ab - sichtbar als
     * "java.net.SocketException: Invalid argument: setsockopt". Die Suche
     * schlaegt dann auf JEDER Schnittstelle fehl, obwohl die Geraete
     * erreichbar sind. Mit einem reinen IPv4-Socket tritt das Problem nicht auf.
     * <p>
     * Die Einstellung wird von der JVM nur EINMAL ausgewertet, naemlich beim
     * ersten Laden der Netzwerkklassen - deshalb steht sie hier ganz am Anfang
     * von {@code main} und nicht erst in {@link BidibDiscovery}. Zusaetzlich
     * setzt der Startbefehl sie schon als JVM-Schalter (siehe
     * {@code build.gradle.kts}); diese Zeile ist die Absicherung fuer Starts
     * ohne diesen Schalter (z.B. direkt aus der Entwicklungsumgebung).
     * Bereits gesetzte Werte werden nicht ueberschrieben, damit sich das bei
     * Bedarf von aussen abschalten laesst.
     */
    private static void preferIpv4Stack() {
        if (System.getProperty("java.net.preferIPv4Stack") == null) {
            System.setProperty("java.net.preferIPv4Stack", "true");
        }
    }
}
