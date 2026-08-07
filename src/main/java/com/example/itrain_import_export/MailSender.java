package com.example.itrain_import_export;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Bereitet den Versand einer Decoder-Vorlage per E-Mail vor.
 * <p>
 * <b>Warum die Datei nicht automatisch angehängt wird:</b> Der Aufruf des
 * Standard-Mailprogramms läuft über {@code mailto:}. Dieses Verfahren kennt
 * Empfänger, Betreff und Text - aber <b>keine Dateianhänge</b>; das ist eine
 * Festlegung des Standards, keine Einschränkung dieses Programms. Ein
 * automatischer Versand mit Anhang ginge nur, wenn das Programm selbst als
 * Mailserver-Client aufträte (SMTP-Server, Benutzer und Passwort müssten
 * dann eingerichtet werden) - dann käme aber gerade nicht das gewohnte
 * Mailprogramm des Anwenders zum Einsatz.
 * <p>
 * Deshalb dieser Weg: Das Mailprogramm öffnet sich mit fertigem Empfänger,
 * Betreff und Text (die Signatur ergänzt das Mailprogramm selbst), der
 * vollständige Dateipfad liegt in der Zwischenablage und der Ordner wird
 * geöffnet - die Datei ist damit mit einem Handgriff angehängt.
 */
public final class MailSender {

    /**
     * Voreingestellter Empfänger: der Autor des Programms. Anwender können
     * ihm so ihre erfassten Decoder zusenden, damit sie in die allgemeine
     * Vorlagen-Sammlung (decoder.zip) aufgenommen werden können.
     */
    private static final String DEFAULT_RECIPIENT = "aruff@allesruff.de";

    private MailSender() {
    }

    /**
     * Öffnet das Mailprogramm mit vorbereiteter Nachricht und stellt die
     * Datei zum Anhängen bereit.
     *
     * @param file die gespeicherte Vorlagen-Datei
     * @param templateName Bezeichnung der Vorlage (steht in Betreff und Text)
     */
    public static void sendDecoderTemplate(Stage owner, File file, String templateName) {
        I18n i18n = I18n.getInstance();

        // Dateipfad in die Zwischenablage - damit lässt er sich im
        // Anhang-Dialog des Mailprogramms direkt einfügen.
        ClipboardContent content = new ClipboardContent();
        content.putString(file.getAbsolutePath());
        content.putFiles(List.of(file));
        Clipboard.getSystemClipboard().setContent(content);

        String subject = i18n.t("mail.subject", templateName);
        String body = i18n.t("mail.body", templateName);

        boolean mailOpened = openMailClient(subject, body);
        openFolder(file);

        Alert done = new Alert(Alert.AlertType.INFORMATION,
                i18n.t(mailOpened ? "mail.prepared" : "mail.preparedNoClient",
                        file.getName(), file.getParent()),
                new ButtonType(i18n.t("decoder.installContinue"), ButtonBar.ButtonData.OK_DONE));
        done.initOwner(owner);
        done.setTitle(i18n.t("capture.saveAndSend"));
        done.setHeaderText(null);
        done.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));
        done.showAndWait();
    }

    private static boolean openMailClient(String subject, String body) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                return false;
            }
            String uri = "mailto:" + DEFAULT_RECIPIENT
                    + "?subject=" + encode(subject)
                    + "&body=" + encode(body);
            Desktop.getDesktop().mail(new URI(uri));
            return true;
        } catch (Exception ex) {
            System.err.println("Mailprogramm konnte nicht geöffnet werden: " + ex.getMessage());
            return false;
        }
    }

    /** Öffnet den Ordner der Datei, damit sie schnell angehängt werden kann. */
    private static void openFolder(File file) {
        try {
            File folder = file.getParentFile();
            if (folder != null && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder);
            }
        } catch (Exception ex) {
            System.err.println("Ordner konnte nicht geöffnet werden: " + ex.getMessage());
        }
    }

    /**
     * Kodiert für eine mailto-Adresse. {@link URLEncoder} macht aus
     * Leerzeichen ein "+", was in mailto-Parametern als Pluszeichen
     * ankommt - deshalb die Nachbesserung auf "%20". Zeilenumbrüche werden
     * als %0A kodiert, damit der Text im Mailprogramm mehrzeilig ankommt.
     */
    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%0D%0A", "%0A");
    }
}
