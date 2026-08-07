package com.example.itrain_import_export;

import javafx.application.Application;
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
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setTitle(I18n.getInstance().t("app.title"));
        stage.getIcons().addAll(loadAppIcons());
        stage.setScene(scene);
        // Beim Schließen ggf. das Backup der aktuell offenen Datei
        // aufräumen, falls seither weder geändert noch gespeichert wurde.
        stage.setOnCloseRequest(event -> controller.onAppClosing());

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
        launch(args);
    }
}
