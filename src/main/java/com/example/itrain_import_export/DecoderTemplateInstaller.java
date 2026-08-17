package com.example.itrain_import_export;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Menüpunkt Einstellungen → "Decoder-Vorlagen installieren": entpackt eine
 * zuvor heruntergeladene {@code decoder.zip} in den Vorlagen-Ordner.
 * <p>
 * Ablauf: Rückfrage, ob das Archiv überhaupt schon heruntergeladen wurde
 * (sonst hat der Nutzer nichts zum Auswählen) → Dateiauswahl → falls noch
 * kein Vorlagen-Ordner eingestellt ist, wird er jetzt abgefragt und bei
 * Bedarf angelegt → alle {@code .csv}-Einträge des Archivs werden in den
 * Ordner geschrieben.
 * <p>
 * <b>Versionierung</b>: Enthält das Archiv eine Datei {@code version.txt},
 * wird deren erste Zeile als Versionskennung gespeichert; zusätzlich immer
 * der Zeitpunkt der Installation. Beides zeigt das Vorlagen-Fenster an
 * ({@link DecoderTemplateBrowser}), damit erkennbar bleibt, welcher Stand
 * installiert ist.
 * <p>
 * <b>Sicherheit</b>: Beim Entpacken wird ausschließlich der Dateiname eines
 * Eintrags verwendet, nie ein im Archiv enthaltener Pfad - so kann ein
 * präpariertes Archiv nicht aus dem Zielordner ausbrechen ("Zip Slip").
 * Unterordner im Archiv werden dadurch flach in den Vorlagen-Ordner gelegt.
 */
public final class DecoderTemplateInstaller {

    /** Name der optionalen Versionsdatei im Archiv. */
    private static final String VERSION_ENTRY = "version.txt";

    // Die Adresse der decoder.zip stand hier früher fest im Quelltext. Sie
    // kommt jetzt zur Laufzeit aus dem Update-Manifest (siehe UpdateChecker)
    // und wird nach dem ersten Abruf gemerkt. Grund: Eine einkompilierte
    // Freigabe-Adresse der Form ".../urls/TOKEN#SCHLUESSEL" liess Windows
    // Defender die fertige .msi als "Trojan:Win32/MalUri.A!cl" blockieren -
    // ausführlich in STATUS.md. Die Dateien liegen unverändert auf
    // derselben Freigabe; nur die Adresse steht nicht mehr im Programm.

    private DecoderTemplateInstaller() {
    }

    public static void install(Stage owner) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        ButtonType cancelButton = new ButtonType(i18n.t("decoder.installCancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType downloadButton = new ButtonType(i18n.t("update.downloadButton"), ButtonBar.ButtonData.OTHER);
        ButtonType installButton = new ButtonType(i18n.t("decoder.installSingle"), ButtonBar.ButtonData.OK_DONE);

        // Drei Knöpfe, nicht vier. Früher gab es neben "Decoder-Datei..."
        // noch ein "Weiter", das einen eigenen Dateidialog nur für die
        // decoder.zip öffnete. Das war doppelt gemoppelt: installSingleFiles
        // nimmt .zip längst genauso entgegen - samt Versionsprüfung und
        // Entpacken - und zusätzlich einzelne .csv sowie mehrere Dateien auf
        // einmal. Der Anwender musste sich also zwischen zwei Knöpfen
        // entscheiden, von denen einer alles konnte, was der andere konnte.
        //
        // Die Rückfrage wird so lange erneut gezeigt, wie "Herunterladen"
        // gewählt wird: Der Browser öffnet sich, der Anwender lädt die Datei,
        // kommt zurück und kann dann installieren - ohne den Menüpunkt neu
        // aufrufen zu müssen.
        while (true) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, i18n.t("decoder.installQuestion"),
                    downloadButton, cancelButton, installButton);
            confirm.initOwner(owner);
            confirm.setTitle(i18n.t("menu.decoderInstall"));
            confirm.setHeaderText(null);
            applyThemeOnceShown(confirm);
            Optional<ButtonType> answer = confirm.showAndWait();
            if (answer.isEmpty() || answer.get() == cancelButton) {
                return;
            }
            if (answer.get() == installButton) {
                installSingleFiles(owner, i18n, settings);
                return;
            }
            openDownloadPage(owner, i18n);
        }
    }

    /**
     * Installiert einzeln vorliegende Vorlagen - also den Fall abseits der
     * gesammelten {@code decoder.zip}: eine selbst erstellte Vorlage, eine
     * per E-Mail zugeschickte, oder mehrere auf einmal.
     * <p>
     * Angenommen werden {@code .csv}-Dateien (eine Vorlage je Datei) und
     * {@code .zip}-Archive (dann wird wie beim Sammelpaket entpackt, samt
     * Versionsprüfung). Beides im selben Dialog, weil der Anwender sonst
     * vorher wissen müsste, welcher Knopf zu welcher Dateiendung gehört.
     * <p>
     * Jede {@code .csv} wird vor dem Kopieren geprüft: Nur was
     * {@link DecoderTemplate#read} als Vorlage erkennt, landet im Ordner -
     * eine versehentlich gewählte Export-CSV aus dem normalen Import/Export
     * würde dort sonst nur stören.
     * <p>
     * Version und Installationszeitpunkt des Sammelpakets bleiben dabei
     * bewusst unangetastet: Sie beschreiben die {@code decoder.zip}, und eine
     * einzeln hinzugefügte Vorlage ändert daran nichts.
     */
    private static void installSingleFiles(Stage owner, I18n i18n, AppSettings settings) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("decoder.chooseSingleTitle"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(i18n.t("decoder.filterTemplates"), "*.csv", "*.zip"),
                new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"),
                new FileChooser.ExtensionFilter("ZIP (*.zip)", "*.zip"),
                new FileChooser.ExtensionFilter("*.*", "*.*"));
        List<File> chosen = chooser.showOpenMultipleDialog(owner);
        if (chosen == null || chosen.isEmpty()) {
            return;
        }

        File targetDir = resolveTargetDirectory(owner, i18n, settings);
        if (targetDir == null) {
            return;
        }

        int installed = 0;
        int skipped = 0;
        List<String> problems = new ArrayList<>();
        for (File file : chosen) {
            try {
                if (file.getName().toLowerCase().endsWith(".zip")) {
                    if (!confirmVersion(owner, i18n, settings, file)) {
                        skipped++;
                        continue;
                    }
                    installed += unpack(file, targetDir, settings);
                    settings.setDecoderPackInstalled(
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    continue;
                }
                if (DecoderTemplate.read(file) == null) {
                    problems.add(i18n.t("decoder.singleNotATemplate", file.getName()));
                    skipped++;
                    continue;
                }
                File target = new File(targetDir, file.getName());
                if (target.exists() && !target.equals(file) && !confirmOverwrite(owner, i18n, target)) {
                    skipped++;
                    continue;
                }
                if (!target.equals(file)) {
                    Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                installed++;
            } catch (Exception ex) {
                problems.add(file.getName() + ": " + ex.getMessage());
                skipped++;
            }
        }

        StringBuilder message = new StringBuilder(
                i18n.t("decoder.singleResult", installed, skipped, targetDir.getAbsolutePath()));
        for (String problem : problems) {
            message.append("\n\n").append(problem);
        }
        Alert done = new Alert(problems.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                message.toString());
        done.initOwner(owner);
        done.setTitle(i18n.t("menu.decoderInstall"));
        done.setHeaderText(null);
        applyThemeOnceShown(done);
        done.showAndWait();
    }

    private static boolean confirmOverwrite(Stage owner, I18n i18n, File target) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                i18n.t("decoder.singleOverwrite", target.getName()),
                ButtonType.YES, ButtonType.NO);
        confirm.initOwner(owner);
        confirm.setHeaderText(null);
        applyThemeOnceShown(confirm);
        Optional<ButtonType> answer = confirm.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.YES;
    }

    /**
     * Vergleicht die Version im gewählten Archiv mit der zuletzt
     * installierten und fragt nach, wenn das Archiv <b>nicht</b> neuer ist.
     * <p>
     * Der Grund: Die Vorlagen werden vom Anwender selbst aus der
     * Proton-Drive-Freigabe geladen, oft liegen dort mehrere Fassungen im
     * Download-Ordner nebeneinander. Ohne diese Prüfung überschreibt ein
     * versehentlich gewähltes altes Archiv die bereits installierten,
     * neueren Vorlagen stillschweigend - und der Anwender merkt es erst,
     * wenn eine Vorlage fehlt.
     * <p>
     * Die Prüfung <em>verbietet</em> nichts: Ein Zurückgehen auf eine ältere
     * Fassung kann durchaus gewollt sein, es muss nur bewusst geschehen.
     *
     * @return {@code true}, wenn installiert werden soll; {@code false} bei
     *         "Alte behalten" bzw. Abbruch.
     */
    private static boolean confirmVersion(Stage owner, I18n i18n, AppSettings settings, File zipFile) {
        String installed = settings.getDecoderPackVersion();
        if (installed == null || installed.isBlank()) {
            // Noch nie etwas installiert (oder Fassung unbekannt) - dann gibt
            // es nichts zu vergleichen und nichts zu verlieren.
            return true;
        }
        String archive = readVersionFromZip(zipFile);
        String question;
        if (archive == null || archive.isBlank()) {
            question = i18n.t("decoder.versionUnknown", installed);
        } else if (isNumericVersion(installed) && isNumericVersion(archive)) {
            int comparison = UpdateChecker.compareVersions(archive, installed);
            if (comparison > 0) {
                return true;
            }
            question = comparison == 0
                    ? i18n.t("decoder.versionSame", installed)
                    : i18n.t("decoder.versionOlder", archive, installed);
        } else {
            // Freitext-Kennungen (z.B. ein Datum) lassen sich nicht ordnen -
            // bei Gleichheit ist es dieselbe Fassung, sonst bleibt nur die
            // ehrliche Rückfrage.
            if (!archive.equals(installed)) {
                return true;
            }
            question = i18n.t("decoder.versionSame", installed);
        }

        ButtonType keepOld = new ButtonType(i18n.t("decoder.keepOld"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType installAnyway = new ButtonType(i18n.t("decoder.installAnyway"), ButtonBar.ButtonData.OK_DONE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, question, keepOld, installAnyway);
        confirm.initOwner(owner);
        confirm.setTitle(i18n.t("menu.decoderInstall"));
        confirm.setHeaderText(null);
        applyThemeOnceShown(confirm);
        Optional<ButtonType> answer = confirm.showAndWait();
        return answer.isPresent() && answer.get() == installAnyway;
    }

    /** Liest die {@code version.txt} eines Archivs, ohne es zu entpacken. */
    private static String readVersionFromZip(File zipFile) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String fileName = new File(entry.getName()).getName();
                if (VERSION_ENTRY.equalsIgnoreCase(fileName)) {
                    return readFirstLine(zip, entry);
                }
            }
        } catch (IOException ignored) {
            // Unlesbares Archiv fällt spätestens beim Entpacken auf.
        }
        return null;
    }

    /** Nur reine Zahlen-Versionen wie "1.4" lassen sich der Größe nach ordnen. */
    private static boolean isNumericVersion(String version) {
        return version != null && version.trim().matches("\\d+(\\.\\d+)*");
    }

    /**
     * Öffnet die Proton-Drive-Freigabe mit den Decoder-Vorlagen im
     * Standard-Browser. Dieselbe Adresse, unter der auch das Programm selbst
     * bereitliegt - deshalb bewusst fest hinterlegt und nicht aus dem
     * Update-Manifest gelesen: Die Vorlagen sollen sich auch dann
     * herunterladen lassen, wenn gerade keine Internetverbindung zum
     * Manifest besteht oder dort etwas fehlt.
     * <p>
     * Das Herunterladen selbst übernimmt wie beim Programm-Update der
     * Browser; das Programm lädt nichts eigenständig und braucht dafür auch
     * keine Zugangsdaten.
     */
    private static void openDownloadPage(Stage owner, I18n i18n) {
        // Adresse aus dem Manifest holen (beim ersten Mal über das Netz,
        // danach aus dem gemerkten Wert - siehe UpdateChecker).
        UpdateChecker.resolveLinkAsync(UpdateChecker.Link.DECODER, url -> {
            if (url == null) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, i18n.t("update.linkUnavailable"));
                alert.initOwner(owner);
                alert.setHeaderText(null);
                applyThemeOnceShown(alert);
                alert.showAndWait();
                return;
            }
            browseTo(owner, i18n, url);
        });
    }

    private static void browseTo(Stage owner, I18n i18n, String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ex) {
            System.err.println("Download-Seite konnte nicht geöffnet werden: " + ex.getMessage());
        }
        // Kein Browser verfügbar: Adresse wenigstens anzeigen, damit sie von
        // Hand aufgerufen werden kann (gleiches Muster wie im UpdateDialog).
        Alert fallback = new Alert(Alert.AlertType.INFORMATION,
                i18n.t("update.openLinkManually", url));
        fallback.initOwner(owner);
        fallback.setHeaderText(null);
        applyThemeOnceShown(fallback);
        fallback.showAndWait();
    }

    /**
     * Liefert den Vorlagen-Ordner. Ist noch keiner eingestellt (z.B. weil das
     * Programm schon vor Einführung der Decoder-Vorlagen genutzt wurde und
     * der Ersteinrichtungs-Dialog deshalb nicht mehr erscheint), wird er
     * jetzt abgefragt und - nach Rückfrage - angelegt.
     */
    private static File resolveTargetDirectory(Stage owner, I18n i18n, AppSettings settings) {
        String configured = settings.getDecoderDirectory();
        if (configured != null && !configured.isBlank()) {
            File dir = new File(configured);
            if (dir.isDirectory()) {
                return dir;
            }
            // Eingestellt, aber (noch) nicht vorhanden: anlegen anbieten.
            if (createAfterConfirm(owner, i18n, dir)) {
                return dir;
            }
            return null;
        }

        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(i18n.t("settings.decoderPath"));
        File suggested = new File(System.getProperty("user.home"), "iTrain");
        if (suggested.isDirectory()) {
            dirChooser.setInitialDirectory(suggested);
        }
        File chosen = dirChooser.showDialog(owner);
        if (chosen == null) {
            return null;
        }
        settings.setDecoderDirectory(chosen.getAbsolutePath());
        return chosen;
    }

    private static boolean createAfterConfirm(Stage owner, I18n i18n, File dir) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                i18n.t("firstRun.createFolderMessage", dir.getAbsolutePath()),
                ButtonType.YES, ButtonType.NO);
        confirm.initOwner(owner);
        confirm.setTitle(i18n.t("firstRun.createFolderTitle"));
        confirm.setHeaderText(null);
        applyThemeOnceShown(confirm);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                Files.createDirectories(dir.toPath());
                return true;
            } catch (IOException ex) {
                Alert error = new Alert(Alert.AlertType.ERROR,
                        i18n.t("firstRun.createFolderFailed", String.valueOf(ex.getMessage())));
                error.initOwner(owner);
                error.setHeaderText(null);
                applyThemeOnceShown(error);
                error.showAndWait();
            }
        }
        return false;
    }

    /**
     * Entpackt alle {@code .csv}-Einträge flach in den Zielordner und liest
     * dabei eine etwaige {@code version.txt} aus. Gibt die Anzahl der
     * geschriebenen Vorlagen zurück.
     */
    private static int unpack(File zipFile, File targetDir, AppSettings settings) throws IOException {
        int copied = 0;
        String version = null;
        try (ZipFile zip = new ZipFile(zipFile)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                // Bewusst nur der reine Dateiname, nie der Pfad aus dem
                // Archiv - siehe Klassenkommentar (Zip Slip).
                String fileName = new File(entry.getName()).getName();
                if (fileName.isBlank()) {
                    continue;
                }
                if (VERSION_ENTRY.equalsIgnoreCase(fileName)) {
                    version = readFirstLine(zip, entry);
                    continue;
                }
                if (!fileName.toLowerCase().endsWith(".csv")) {
                    continue;
                }
                Path target = targetDir.toPath().resolve(fileName);
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        }
        settings.setDecoderPackVersion(version);
        return copied;
    }

    private static String readFirstLine(ZipFile zip, ZipEntry entry) {
        try (InputStream in = zip.getInputStream(entry)) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // BOM entfernen, falls die Datei mit einem Editor erzeugt wurde,
            // der eine Byte-Order-Mark schreibt.
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            String[] lines = content.split("\\R", 2);
            return lines.length > 0 ? lines[0].trim() : null;
        } catch (IOException ex) {
            return null;
        }
    }

    private static void applyThemeOnceShown(Alert dialog) {
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));
    }
}
