package com.example.itrain_import_export;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verdrahtet das Hauptfenster: Datei öffnen/Speichern, Einstellungen
 * (Sprache, Pfade, Farbschema) sowie den Aufbau je eines Reiters pro
 * control-items-Kategorie (siehe {@link CategoryEditor}). Alle sichtbaren
 * Texte kommen aus {@link I18n}; bei Sprachwechsel wird die komplette
 * Oberfläche neu aufgebaut ({@link #applyLanguage()}).
 * <p>
 * Es werden immer alle bekannten Kategorien als Reiter angezeigt (siehe
 * {@link TcdDocument#TAB_DISPLAY_ORDER}), auch wenn die geladene Datei sie
 * nicht enthält - der Reiter bleibt dann einfach leer. Das zugehörige
 * XML-Element wird erst angelegt, sobald tatsächlich ein Eintrag hinzugefügt
 * oder importiert wird (siehe {@link CategoryEditor#ensureCategoryNode()}),
 * damit niemals leere Kategorien in eine Datei geschrieben werden, die sie
 * vorher nicht hatte.
 */
public class HelloController {

    @FXML
    private TabPane tabPane;

    @FXML
    private Label statusLabel;

    @FXML
    private Label fileNameLabel;

    @FXML
    private Menu fileMenu;

    @FXML
    private MenuItem openMenuItem;

    @FXML
    private MenuItem saveMenuItem;

    @FXML
    private Menu settingsMenu;

    @FXML
    private MenuItem pathsMenuItem;

    @FXML
    private MenuItem languageMenuItem;

    @FXML
    private MenuItem viewMenuItem;

    private final I18n i18n = I18n.getInstance();
    private TcdDocument document;
    private final Map<Tab, CategoryEditor> editorsByTab = new HashMap<>();

    @FXML
    private void initialize() {
        openMenuItem.setGraphic(loadIcon("icons/open-icon.png"));
        saveMenuItem.setGraphic(loadIcon("icons/save-icon.png"));
        // Nur einmal registrieren (nicht in rebuildTabs(), das bei jedem
        // Dateiöffnen/Sprachwechsel erneut läuft) - sonst würde sich bei
        // jedem Aufruf ein weiterer Listener anhäufen.
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateStatusForSelectedTab());
        i18n.addLanguageChangeListener(this::applyLanguage);
        applyLanguage();
    }

    private static ImageView loadIcon(String resourcePath) {
        try (InputStream in = HelloController.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            ImageView view = new ImageView(new Image(in));
            view.setFitWidth(16);
            view.setFitHeight(16);
            view.setPreserveRatio(true);
            return view;
        } catch (Exception ex) {
            return null;
        }
    }

    private void applyLanguage() {
        fileMenu.setText(i18n.t("menu.file"));
        openMenuItem.setText(i18n.t("menu.open"));
        saveMenuItem.setText(i18n.t("menu.saveAs"));
        settingsMenu.setText(i18n.t("menu.settingsMenu"));
        pathsMenuItem.setText(i18n.t("menu.settingsPaths"));
        languageMenuItem.setText(i18n.t("menu.settingsLanguage"));
        viewMenuItem.setText(i18n.t("menu.settingsView"));
        if (document == null) {
            fileNameLabel.setText(i18n.t("status.noFileLoaded"));
            statusLabel.setText("");
        }
        rebuildTabs();
    }

    @FXML
    private void onOpenFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("dialog.openTitle"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("iTrain (*.tcd, *.tcdz)", "*.tcd", "*.tcdz"),
                new FileChooser.ExtensionFilter("iTrain XML (*.tcd)", "*.tcd"),
                new FileChooser.ExtensionFilter("iTrain ZIP (*.tcdz)", "*.tcdz"),
                new FileChooser.ExtensionFilter("*.*", "*.*"));
        applyDefaultTcdDirectory(chooser);

        Stage stage = (Stage) tabPane.getScene().getWindow();
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        File backup = createBackup(file);

        try {
            TcdDocument newDocument = TcdDocument.load(file);
            newDocument.setBackupFile(backup);
            // Erst jetzt, nach erfolgreichem Laden, das Backup der bisher
            // offenen Datei aufräumen (falls unbenutzt) und sie tatsächlich
            // ersetzen - schlägt das Laden fehl, bleibt die bisherige Datei
            // inkl. ihres Backups unangetastet geöffnet.
            cleanupUnusedBackup(document);
            document = newDocument;
            rebuildTabs();
            fileNameLabel.setText(file.getName());
            statusLabel.setText(i18n.t("status.fileLoaded", file.getName()));
        } catch (Exception ex) {
            deleteQuietly(backup);
            showError(i18n.t("error.loadTitle"), ex);
        }
    }

    /** Maximale Anzahl gleichzeitiger Backup-Generationen je Originaldatei. */
    private static final int MAX_BACKUP_GENERATIONS = 10;

    /**
     * Legt, falls ein Backup-Ordner eingestellt ist, eine unveränderte
     * 1:1-Kopie der zu öffnenden Datei dort ab, bevor irgendetwas bearbeitet
     * wird. Name: {@code <originalDateiname>.<N>.bak}, mit N von 1 bis
     * {@value #MAX_BACKUP_GENERATIONS} durchnummeriert - wird dieselbe Datei
     * erneut geöffnet, zählt N weiter hoch; nach Erreichen von
     * {@value #MAX_BACKUP_GENERATIONS} beginnt die Zählung wieder bei 1 (die
     * älteste Generation wird also überschrieben), sodass nie mehr als
     * {@value #MAX_BACKUP_GENERATIONS} Backups derselben Datei im Ordner
     * liegen - unterscheidbar dann nur noch über den Datei-Zeitstempel.
     * Schlägt die Sicherung fehl, wird nur gewarnt; das eigentliche Öffnen
     * der Datei wird dadurch nicht blockiert.
     */
    private File createBackup(File file) {
        String backupDir = AppSettings.getInstance().getBackupDirectory();
        if (backupDir == null || backupDir.isBlank()) {
            return null;
        }
        File backupFolder = new File(backupDir);
        if (!backupFolder.isDirectory()) {
            return null;
        }
        try {
            int nextNumber = nextBackupNumber(backupFolder, file.getName());
            File target = new File(backupFolder, file.getName() + "." + nextNumber + ".bak");
            Files.copy(file.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return target;
        } catch (IOException ex) {
            new Alert(Alert.AlertType.WARNING,
                    i18n.t("error.backupFailed", ex.getMessage())).showAndWait();
            return null;
        }
    }

    /**
     * Ermittelt anhand der zuletzt geänderten passenden Backup-Datei im
     * Ordner die zuletzt für {@code originalName} verwendete Generation
     * (1..{@value #MAX_BACKUP_GENERATIONS}) und gibt die nächste zurück -
     * beginnend bei 1, nach Erreichen des Maximums wieder bei 1.
     */
    private static int nextBackupNumber(File backupFolder, String originalName) {
        Pattern pattern = Pattern.compile(Pattern.quote(originalName) + "\\.(\\d{1,2})\\.bak");
        File[] files = backupFolder.listFiles();
        int lastNumber = 0;
        long lastModified = -1;
        if (files != null) {
            for (File candidate : files) {
                Matcher matcher = pattern.matcher(candidate.getName());
                if (matcher.matches()) {
                    int number = Integer.parseInt(matcher.group(1));
                    if (number >= 1 && number <= MAX_BACKUP_GENERATIONS && candidate.lastModified() > lastModified) {
                        lastModified = candidate.lastModified();
                        lastNumber = number;
                    }
                }
            }
        }
        return lastNumber == 0 ? 1 : (lastNumber % MAX_BACKUP_GENERATIONS) + 1;
    }

    /**
     * Löscht das Backup eines Dokuments, falls seit dem Öffnen weder etwas
     * geändert noch gespeichert wurde - dann ist die Sicherheitskopie
     * überflüssig. Wird beim Öffnen einer anderen Datei (für das bisherige
     * Dokument) sowie beim Beenden des Programms aufgerufen.
     */
    private void cleanupUnusedBackup(TcdDocument doc) {
        if (doc == null) {
            return;
        }
        File backup = doc.getBackupFile();
        if (backup == null) {
            return;
        }
        if (!doc.isDirty() && !doc.wasSavedSinceOpen()) {
            deleteQuietly(backup);
        }
    }

    private static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
            // Aufräumen ist best-effort - ein Fehlschlag soll den
            // eigentlichen Workflow nicht blockieren.
        }
    }

    /** Wird beim Schließen des Programmfensters aufgerufen (siehe {@link HelloApplication}). */
    public void onAppClosing() {
        cleanupUnusedBackup(document);
    }

    @FXML
    private void onSaveAs() {
        if (document == null) {
            new Alert(Alert.AlertType.INFORMATION, i18n.t("error.pleaseOpenFirst")).showAndWait();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("dialog.saveTitle"));
        FileChooser.ExtensionFilter tcdFilter = new FileChooser.ExtensionFilter("iTrain XML (*.tcd)", "*.tcd");
        FileChooser.ExtensionFilter tcdzFilter = new FileChooser.ExtensionFilter("iTrain ZIP (*.tcdz)", "*.tcdz");
        chooser.getExtensionFilters().addAll(tcdzFilter, tcdFilter,
                new FileChooser.ExtensionFilter("*.*", "*.*"));

        boolean wasZipped = document.getFile() != null
                && document.getFile().getName().toLowerCase().endsWith(".tcdz");
        chooser.setSelectedExtensionFilter(wasZipped ? tcdzFilter : tcdFilter);

        if (document.getFile() != null) {
            chooser.setInitialDirectory(document.getFile().getParentFile());
            chooser.setInitialFileName(document.getFile().getName());
        } else {
            applyDefaultTcdDirectory(chooser);
        }

        Stage stage = (Stage) tabPane.getScene().getWindow();
        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            return;
        }

        try {
            document.save(target);
            fileNameLabel.setText(target.getName());
            statusLabel.setText(i18n.t("status.fileSaved", target.getName()));
        } catch (Exception ex) {
            showError(i18n.t("error.saveTitle"), ex);
        }
    }

    private void applyDefaultTcdDirectory(FileChooser chooser) {
        String tcdDir = AppSettings.getInstance().getTcdDirectory();
        if (tcdDir != null && new File(tcdDir).isDirectory()) {
            chooser.setInitialDirectory(new File(tcdDir));
        }
    }

    @FXML
    private void onSettingsPaths() {
        Stage stage = (Stage) tabPane.getScene().getWindow();
        SettingsDialog.showPaths(stage);
    }

    @FXML
    private void onSettingsLanguage() {
        Stage stage = (Stage) tabPane.getScene().getWindow();
        SettingsDialog.showLanguage(stage);
    }

    @FXML
    private void onSettingsView() {
        Stage stage = (Stage) tabPane.getScene().getWindow();
        SettingsDialog.showView(stage);
        // Spaltensichtbarkeit (Typ / Auswahlbox) wird erst beim Aufbau eines
        // CategoryEditor gelesen - nach Schließen des Dialogs alle Reiter neu
        // aufbauen, damit eine Änderung sofort sichtbar wird (wie beim
        // Sprachwechsel).
        rebuildTabs();
    }

    private void rebuildTabs() {
        tabPane.getTabs().clear();
        editorsByTab.clear();
        if (document == null) {
            return;
        }
        XmlNode controlItems = document.getRoot().findChild("control-items");
        if (controlItems == null) {
            new Alert(Alert.AlertType.WARNING, i18n.t("error.noControlItems")).showAndWait();
            return;
        }

        Set<String> created = new LinkedHashSet<>();

        // Immer alle bekannten Kategorien als Reiter anzeigen, auch wenn die
        // Datei sie (noch) nicht enthält - der Reiter bleibt dann leer, bis
        // ein Eintrag hinzugefügt oder importiert wird.
        for (String categoryName : TcdDocument.TAB_DISPLAY_ORDER) {
            addCategoryTab(controlItems, categoryName);
            created.add(categoryName);
        }

        // Zusätzliche, uns nicht bekannte Kategorien (kommt vor, falls
        // künftige iTrain-Versionen neue Kategorien einführen) werden
        // ebenfalls angezeigt, in der Reihenfolge, in der sie in der Datei
        // stehen - ihre Position beim Speichern bleibt unverändert (siehe
        // TcdDocument.reorderChildren).
        for (XmlNode categoryNode : controlItems.getChildren()) {
            if (!created.contains(categoryNode.getTagName())) {
                addCategoryTab(controlItems, categoryNode.getTagName());
                created.add(categoryNode.getTagName());
            }
        }

        if (!tabPane.getTabs().isEmpty()) {
            tabPane.getSelectionModel().select(0);
        }
        updateStatusForSelectedTab();
    }

    private void addCategoryTab(XmlNode controlItems, String categoryName) {
        CategoryEditor editor = new CategoryEditor(categoryName, controlItems, document::markDirty);
        Tab tab = editor.createTab();
        editorsByTab.put(tab, editor);
        editor.entryCountProperty().addListener((obs, oldV, newV) -> {
            if (tabPane.getSelectionModel().getSelectedItem() == tab) {
                updateStatusForSelectedTab();
            }
        });
        tabPane.getTabs().add(tab);
    }

    private void updateStatusForSelectedTab() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        CategoryEditor editor = editorsByTab.get(selected);
        if (editor != null) {
            statusLabel.setText(i18n.t("status.entryCount", editor.getDisplayName(), editor.entryCountProperty().get()));
        } else {
            statusLabel.setText("");
        }
    }

    private void showError(String title, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR, title + ":\n" + ex.getMessage());
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
