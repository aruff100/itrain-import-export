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

        Scene scene = new Scene(root, 1200, 750);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setTitle(I18n.getInstance().t("app.title"));
        stage.getIcons().addAll(loadAppIcons());
        stage.setScene(scene);
        // Beim Schließen ggf. das Backup der aktuell offenen Datei
        // aufräumen, falls seither weder geändert noch gespeichert wurde.
        stage.setOnCloseRequest(event -> controller.onAppClosing());
        stage.show();
    }

    /**
     * Lädt das Programm-Icon (icons8-eisenbahn-60.png) aus den Ressourcen.
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
