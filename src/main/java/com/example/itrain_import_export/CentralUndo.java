package com.example.itrain_import_export;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sicherung und "Rueckgaengig" fuer das Schreiben von Lokomotiven in eine
 * Zentrale (Tams mc2, ESU ECoS) - seit 13.10.2026.
 * <p>
 * Vor jedem Schreiben wird der Lokbestand der Zentrale als Datei auf dem PC
 * abgelegt ({@link #writeBackup}, hoechstens {@value #MAX_BACKUPS} je
 * Zentrale). Das Ergebnis einer Uebertragung ist ein CentralUndo: welche
 * Loks neu angelegt und welche ueberschrieben wurden. "Bearbeiten ->
 * Rueckgaengig" zeigt den Inhalt der Sicherung ({@link #confirm}) und
 * stellt nur die letzte Uebertragung zurueck (Andre, 13.10.2026): neu
 * angelegte Loks werden geloescht, ueberschriebene aus der Sicherung
 * wiederhergestellt.
 */
final class CentralUndo {

    static final int MAX_BACKUPS = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    enum Kind {
        MC2("mc2", "ini"), ECOS("ECoS", "txt");

        final String label;
        final String extension;

        Kind(String label, String extension) {
            this.label = label;
            this.extension = extension;
        }
    }

    /** Eine Lok fuer die Anzeige/Wiederherstellung: Schluessel = mc2-Adresse bzw. ECoS-Objektnummer. */
    record Entry(int key, int address, String name, String format) {
    }

    final Kind kind;
    final String host;
    final File backupFile;
    /** Bei dieser Uebertragung neu angelegt - werden beim Rueckgaengig geloescht. */
    final List<Entry> created = new ArrayList<>();
    /** Bei dieser Uebertragung ueberschrieben (Schluessel) - werden aus der Sicherung wiederhergestellt. */
    final List<Integer> overwritten = new ArrayList<>();
    /** Adressen, die nicht korrekt uebernommen wurden (fuer die Meldung). */
    final List<String> failed = new ArrayList<>();

    CentralUndo(Kind kind, String host, File backupFile) {
        this.kind = kind;
        this.host = host;
        this.backupFile = backupFile;
    }

    int transferredCount() {
        return created.size() + overwritten.size();
    }

    // ------------------------------------------------------------------
    // Sicherungsdateien auf dem PC
    // ------------------------------------------------------------------

    /** Ordner der Sicherungen: im Backup-Ordner der Einstellungen, sonst im Benutzerordner. */
    static File backupDirectory() {
        String base = AppSettings.getInstance().getBackupDirectory();
        File root = base != null && !base.isBlank() && new File(base).isDirectory()
                ? new File(base)
                : new File(System.getProperty("user.home"), "iTrain_import_export");
        return new File(root, "Zentralen-Sicherungen");
    }

    private static String prefix(Kind kind, String host) {
        return kind.label.toLowerCase(Locale.ROOT) + "_" + host.replaceAll("[^A-Za-z0-9.-]", "_") + "_";
    }

    /**
     * Legt den Lokbestand als neue Sicherung ab, liest sie zur Kontrolle
     * zurueck und loescht alte Sicherungen derselben Zentrale ueber
     * {@value #MAX_BACKUPS} hinaus.
     */
    static File writeBackup(Kind kind, String host, String content) throws IOException {
        File dir = backupDirectory();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException(I18n.getInstance().t("central.backupFailed") + " (" + dir + ")");
        }
        String prefix = prefix(kind, host);
        File file = new File(dir, prefix + LocalDateTime.now().format(STAMP) + "." + kind.extension);
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        if (!Files.readString(file.toPath(), StandardCharsets.UTF_8).equals(content)) {
            throw new IOException(I18n.getInstance().t("central.backupFailed") + " (" + file + ")");
        }
        File[] all = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith("." + kind.extension));
        if (all != null && all.length > MAX_BACKUPS) {
            // Der Zeitstempel im Namen sortiert alphabetisch = zeitlich.
            Arrays.sort(all, (a, b) -> a.getName().compareTo(b.getName()));
            for (int i = 0; i < all.length - MAX_BACKUPS; i++) {
                if (!all[i].delete()) {
                    break;
                }
            }
        }
        return file;
    }

    String readBackup() throws IOException {
        return Files.readString(backupFile.toPath(), StandardCharsets.UTF_8);
    }

    /** Die Loks der Sicherung (Schluessel -&gt; Eintrag). */
    Map<Integer, Entry> backupEntries() throws IOException {
        Map<Integer, Entry> result = new LinkedHashMap<>();
        String text = readBackup();
        if (kind == Kind.MC2) {
            for (Mc2LocoReader.LocoEntry e : Mc2LocoReader.parse(text)) {
                result.put(e.address(), new Entry(e.address(), e.address(),
                        e.name() == null ? "" : e.name(), e.fmt() == null ? "" : e.fmt()));
            }
        } else {
            for (String line : text.split("\r?\n")) {
                if (line.isBlank()) {
                    continue;
                }
                EcosClient.Line parsed = EcosClient.parseLine(line.trim());
                if (parsed.objectId() < 0) {
                    continue;
                }
                result.put(parsed.objectId(), new Entry(parsed.objectId(), parsed.getInt("addr", 0),
                        parsed.get("name") == null ? "" : parsed.get("name"),
                        parsed.get("protocol") == null ? "" : parsed.get("protocol")));
            }
        }
        return result;
    }

    /**
     * Rueckfrage vor dem Rueckgaengig: Tabelle mit allem, was in die
     * Zentrale geschrieben wird - "Diesen Inhalt in die Zentralen
     * uebertragen?" mit "Abbruch" und "Austauschen".
     */
    boolean confirm(Window owner) throws IOException {
        I18n i18n = I18n.getInstance();
        Map<Integer, Entry> backup = backupEntries();

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        String[] headers = {i18n.t("systems.fieldAddress"), i18n.t("central.undoColName"),
                i18n.t("mc2.writeColFormat"), i18n.t("mc2.writeColAction")};
        for (int c = 0; c < headers.length; c++) {
            Label header = new Label(headers[c]);
            header.setStyle("-fx-font-weight: bold;");
            grid.add(header, c, 0);
        }
        Separator separator = new Separator();
        grid.add(separator, 0, 1, headers.length, 1);
        int r = 2;
        for (Integer key : overwritten) {
            Entry entry = backup.get(key);
            if (entry == null) {
                continue;
            }
            r = addRow(grid, r, entry, i18n.t("central.undoRestore"), "#2e7d32");
        }
        for (Entry entry : created) {
            r = addRow(grid, r, entry, i18n.t("central.undoDelete"), "#c62828");
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(420, 30 * (r + 1)));
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        Label file = new Label(i18n.t("central.undoBackupFile", backupFile.getAbsolutePath()));
        file.setWrapText(true);
        file.setStyle("-fx-opacity: 0.75;");
        VBox content = new VBox(10, scroll, file);
        content.setPadding(new Insets(4, 0, 0, 0));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("central.undoTitle", kind.label + " (" + host + ")"));
        dialog.setHeaderText(i18n.t("central.undoQuestion"));
        ButtonType replace = new ButtonType(i18n.t("central.undoReplace"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(i18n.t("bidib.abortButton"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(replace, cancel);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(560);
        ThemeManager.apply(dialog.getDialogPane().getScene(), AppSettings.getInstance().getTheme());
        return dialog.showAndWait().orElse(cancel) == replace;
    }

    private static int addRow(GridPane grid, int r, Entry entry, String action, String color) {
        Label address = new Label(String.valueOf(entry.address()));
        grid.add(address, 0, r);
        GridPane.setHalignment(address, HPos.RIGHT);
        grid.add(new Label(entry.name()), 1, r);
        grid.add(new Label(entry.format()), 2, r);
        Label actionLabel = new Label(action);
        actionLabel.setStyle("-fx-text-fill: " + color + ";");
        grid.add(actionLabel, 3, r);
        return r + 1;
    }
}
