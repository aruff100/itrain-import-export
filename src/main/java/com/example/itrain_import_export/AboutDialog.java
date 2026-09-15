package com.example.itrain_import_export;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Zeigt den "Über"-Dialog: Programmname, Autor, aktuelle Build-Nummer und Kontakt-Link. */
public final class AboutDialog {

    private static final String APP_NAME = "iTrain Import/Export";
    private static final String AUTHOR = "Andre Ruff";
    private static final String CONTACT_EMAIL = "aruff@allesruff.de";

    private AboutDialog() {
    }

    public static void show(Stage owner) {
        I18n i18n = I18n.getInstance();

        Label nameLabel = new Label(APP_NAME);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        // Programm-Icon vor dem Namen, in Textgroesse - dieselbe app-icon.png
        // wie ueberall sonst (siehe HelloController.loadIcon).
        ImageView nameIcon = HelloController.loadIcon("app-icon.png", 16);
        if (nameIcon != null) {
            nameLabel.setGraphic(nameIcon);
            nameLabel.setContentDisplay(ContentDisplay.LEFT);
            nameLabel.setGraphicTextGap(6);
        }
        Label authorLabel = new Label(AUTHOR);
        Hyperlink contactLink = new Hyperlink(i18n.t("about.contactLabel"));
        contactLink.setOnAction(e -> openContactMail());
        Label versionLabel = new Label(i18n.t("about.versionLabel", AppInfo.getBuildNumber()));
        Label licenseLabel = new Label(i18n.t("about.licenseNotice"));
        licenseLabel.setWrapText(true);
        licenseLabel.setMaxWidth(320);
        licenseLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");

        // Die Lizenztexte der mitgelieferten fremden Komponenten sind über
        // einen eigenen Knopf einsehbar. Die Apache-Lizenz verlangt, dass
        // Empfänger eine Kopie der Lizenz erhalten - das ist damit erfüllt
        // und für den Anwender leichter zugänglich als eine Datei irgendwo
        // im Installationsordner.
        Button licenseButton = new Button(i18n.t("about.licenseDetails"));
        licenseButton.setOnAction(e -> showLicenses(owner));

        VBox content = new VBox(6, nameLabel, authorLabel, contactLink, versionLabel,
                licenseLabel, licenseButton);
        content.setPadding(new Insets(15));

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("menu.aboutItem"));
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(content);
        dialog.getButtonTypes().setAll(ButtonType.CLOSE);
        // Die Scene existiert erst, sobald der Dialog tatsächlich angezeigt
        // wird - deshalb das Farbschema erst dann anwenden (siehe
        // SettingsDialog.applyThemeOnceShown für dasselbe Muster).
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));
        dialog.showAndWait();
    }

    /**
     * Zeigt die vollständigen Lizenzhinweise der mitgelieferten fremden
     * Komponenten (Java-Laufzeitumgebung und JavaFX) aus der Ressourcendatei
     * {@code third-party-licenses.txt} in einem eigenen, scrollbaren
     * Fenster.
     */
    private static void showLicenses(Stage owner) {
        I18n i18n = I18n.getInstance();
        String text;
        try (InputStream in = AboutDialog.class.getResourceAsStream("third-party-licenses.txt")) {
            text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            text = String.valueOf(ex.getMessage());
        }

        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'monospaced'; -fx-font-size: 11px;");
        area.setPrefSize(700, 520);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("about.licenseDetails"));
        dialog.setResizable(true);
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));
        dialog.showAndWait();
    }

    /**
     * Öffnet das Standard-E-Mail-Programm des Anwenders mit vorausgefüllter
     * Empfängeradresse und Betreff ("iTrain Import-Export Version 1.xx",
     * mit der tatsächlich installierten Versionsnummer). Nutzt
     * {@link Desktop#mail(URI)} - funktioniert nur, wenn das Betriebssystem
     * einen Standard-Mail-Client kennt; ansonsten passiert nichts (nur eine
     * Fehlermeldung auf der Konsole), es wird bewusst kein eigener Dialog
     * als Ersatz gebaut.
     */
    private static void openContactMail() {
        try {
            String subject = URLEncoder.encode(
                    "iTrain Import-Export Version " + AppInfo.getVersion(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            URI mailtoUri = URI.create("mailto:" + CONTACT_EMAIL + "?subject=" + subject);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                Desktop.getDesktop().mail(mailtoUri);
            } else {
                System.err.println("Kein Standard-Mail-Programm verfügbar, Kontakt-Link konnte nicht geöffnet werden.");
            }
        } catch (Exception ex) {
            System.err.println("Kontakt-E-Mail konnte nicht geöffnet werden: " + ex.getMessage());
        }
    }
}
