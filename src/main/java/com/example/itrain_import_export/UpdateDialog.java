package com.example.itrain_import_export;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;

/**
 * Zeigt die drei möglichen Ergebnisse einer {@link UpdateChecker}-Prüfung:
 * neue Version verfügbar (mit Download-Knopf, öffnet den Proton-Drive-
 * Freigabelink im System-Browser), bereits aktuell, oder die Prüfung ist
 * fehlgeschlagen (z.B. offline). Wie {@link AboutDialog}/{@link HelpDialog}
 * wendet auch dieser Dialog das aktuelle Farbschema erst an, sobald seine
 * Scene tatsächlich existiert.
 */
public final class UpdateDialog {

    private UpdateDialog() {
    }

    /** Neue Version gefunden - zeigt Versionsnummern, optionale Notizen und einen Download-Knopf. */
    public static void showUpdateAvailable(Stage owner, UpdateChecker.UpdateResult result) {
        I18n i18n = I18n.getInstance();

        Label headerLabel = new Label(i18n.t("update.availableHeader", result.latestVersion, result.currentVersion));
        headerLabel.setStyle("-fx-font-weight: bold;");
        headerLabel.setWrapText(true);

        VBox content = new VBox(8, headerLabel);
        content.setPadding(new Insets(15));
        content.setPrefWidth(420);
        if (result.notes != null && !result.notes.isBlank()) {
            Label notesLabel = new Label(result.notes);
            notesLabel.setWrapText(true);
            content.getChildren().add(notesLabel);
        }

        ButtonType laterButton = new ButtonType(i18n.t("update.laterButton"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType downloadButton = new ButtonType(i18n.t("update.downloadButton"), ButtonBar.ButtonData.OK_DONE);

        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("update.availableTitle"));
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(content);
        dialog.getButtonTypes().setAll(downloadButton, laterButton);
        applyThemeOnceShown(dialog);

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isPresent() && choice.get() == downloadButton) {
            openInBrowser(owner, result.downloadUrl);
        }
    }

    /** Manuelle Prüfung über Hilfe → Update: bereits die neueste Version installiert. */
    public static void showUpToDate(Stage owner, String currentVersion) {
        I18n i18n = I18n.getInstance();
        Alert dialog = new Alert(Alert.AlertType.INFORMATION, i18n.t("update.upToDateMessage", currentVersion));
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("update.upToDateTitle"));
        dialog.setHeaderText(null);
        applyThemeOnceShown(dialog);
        dialog.showAndWait();
    }

    /**
     * Manuelle Prüfung über Hilfe → Update: Prüfung fehlgeschlagen (z.B.
     * offline, Server nicht erreichbar, HTTPS-/Zertifikatsproblem). Da der
     * Nutzer diese Prüfung bewusst selbst ausgelöst hat, wird hier - anders
     * als beim stillen Start-Check, der bei Fehlern komplett unauffällig
     * bleibt - der tatsächliche technische Grund mit angezeigt (und
     * zusätzlich auf der Konsole ausgegeben). Das ist beim Einrichten/Testen
     * nützlich; sobald alles läuft, kann diese Meldung bei Bedarf wieder
     * knapper gehalten werden.
     */
    public static void showError(Stage owner, String errorMessage) {
        I18n i18n = I18n.getInstance();
        System.err.println("Update-Prüfung fehlgeschlagen: " + errorMessage);
        Alert dialog = new Alert(Alert.AlertType.ERROR, i18n.t("update.errorMessage", errorMessage));
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("update.errorTitle"));
        dialog.setHeaderText(null);
        applyThemeOnceShown(dialog);
        dialog.showAndWait();
    }

    /**
     * Öffnet {@code url} im Standard-Browser des Betriebssystems
     * ({@link Desktop#browse}) - der eigentliche Download läuft danach ganz
     * normal über Proton Drive im Browser, die App lädt selbst nichts
     * herunter. Schlägt das Öffnen fehl (z.B. kein Browser konfiguriert),
     * wird der Link stattdessen in einer Fehlermeldung angezeigt, damit er
     * von Hand kopiert werden kann.
     */
    private static void openInBrowser(Stage owner, String url) {
        I18n i18n = I18n.getInstance();
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) {
            // Fällt unten auf die Anzeige des reinen Links zurück.
        }
        Alert fallback = new Alert(Alert.AlertType.INFORMATION, i18n.t("update.openLinkManually", url));
        fallback.initOwner(owner);
        fallback.setHeaderText(null);
        applyThemeOnceShown(fallback);
        fallback.showAndWait();
    }

    private static void applyThemeOnceShown(Alert dialog) {
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));
    }
}
