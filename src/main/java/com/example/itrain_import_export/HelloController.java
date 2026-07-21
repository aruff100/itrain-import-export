package com.example.itrain_import_export;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verdrahtet das Hauptfenster: Menüzeile, Ribbon-Werkzeugleiste (Symbole für
 * Öffnen/Speichern/Voreinstellungen), Dateiname-Zeile sowie den Aufbau je
 * eines Reiters pro control-items-Kategorie (siehe {@link CategoryEditor}).
 * Alle sichtbaren Texte kommen aus {@link I18n}; bei Sprachwechsel wird die
 * komplette Oberfläche neu aufgebaut ({@link #applyLanguage()}).
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

    /**
     * Eigenständiges Untermenü "Zuletzt verwendet..." - Inhalt (bis zu 5
     * zuletzt geöffnete Dateien, oder ein deaktivierter Platzhalter) wird
     * zur Laufzeit aufgebaut, siehe {@link #refreshRecentFilesMenu()}.
     * "Backup laden..." und "Export Dateien" sind eigene, gleichrangige
     * Menüpunkte direkt danach (nicht mehr darin verschachtelt).
     */
    @FXML
    private Menu recentFilesMenu;

    @FXML
    private MenuItem loadBackupMenuItem;

    @FXML
    private MenuItem exportFilesMenuItem;

    @FXML
    private Menu editMenu;

    @FXML
    private MenuItem undoMenuItem;

    @FXML
    private MenuItem redoMenuItem;

    @FXML
    private Menu settingsMenu;

    @FXML
    private MenuItem preferencesMenuItem;

    @FXML
    private Menu helpMenu;

    @FXML
    private MenuItem helpMenuItem;

    @FXML
    private MenuItem updateMenuItem;

    @FXML
    private MenuItem aboutMenuItem;

    @FXML
    private ToolBar ribbonToolBar;

    @FXML
    private Button openToolButton;

    @FXML
    private Button saveToolButton;

    @FXML
    private Button undoToolButton;

    @FXML
    private Button redoToolButton;

    @FXML
    private Button preferencesToolButton;

    private final I18n i18n = I18n.getInstance();
    private TcdDocument document;
    private final Map<Tab, CategoryEditor> editorsByTab = new HashMap<>();

    /** Maximale Anzahl gespeicherter Rückgängig-Schritte (siehe {@link #recordUndoSnapshot()}). */
    private static final int MAX_UNDO_STEPS = 50;
    /**
     * Rückgängig-/Wiederholen-Verlauf: jeder Eintrag ist eine tiefe Kopie
     * aller Kategorie-Knoten unter control-items zu einem bestimmten
     * Zeitpunkt. {@link #recordUndoSnapshot()} wird von jedem
     * {@link CategoryEditor} VOR jeder tatsächlichen Änderung aufgerufen
     * (Konstruktor-Parameter {@code beforeChange}) und sichert damit den
     * Stand unmittelbar davor.
     */
    private final Deque<List<XmlNode>> undoStack = new ArrayDeque<>();
    private final Deque<List<XmlNode>> redoStack = new ArrayDeque<>();

    @FXML
    private void initialize() {
        openMenuItem.setGraphic(loadIcon("icons/open-icon.png", 16));
        saveMenuItem.setGraphic(loadIcon("icons/save-icon.png", 16));

        // Ribbon: dieselben Aktionen wie im Datei-/Einstellungen-Menü, nur
        // als Symbol-Buttons für schnellen Zugriff. Aussagekräftig durch
        // Icon + Tooltip statt zusätzlichem Text, damit die Leiste kompakt
        // bleibt.
        openToolButton.setGraphic(loadIcon("icons/open-icon.png", 22));
        saveToolButton.setGraphic(loadIcon("icons/save-icon.png", 22));
        undoToolButton.setGraphic(loadIcon("icons/undo-icon.png", 22));
        redoToolButton.setGraphic(loadIcon("icons/redo-icon.png", 22));
        preferencesToolButton.setGraphic(loadIcon("icons/settings-icon.png", 22));

        // Nur einmal registrieren (nicht in rebuildTabs(), das bei jedem
        // Dateiöffnen/Sprachwechsel erneut läuft) - sonst würde sich bei
        // jedem Aufruf ein weiterer Listener anhäufen.
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> updateStatusForSelectedTab());
        i18n.addLanguageChangeListener(this::applyLanguage);
        applyLanguage();
        updateUndoRedoState();
    }

    private static ImageView loadIcon(String resourcePath, int size) {
        try (InputStream in = HelloController.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            ImageView view = new ImageView(new Image(in));
            view.setFitWidth(size);
            view.setFitHeight(size);
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
        recentFilesMenu.setText(i18n.t("menu.recentFiles"));
        loadBackupMenuItem.setText(i18n.t("menu.loadBackup"));
        exportFilesMenuItem.setText(i18n.t("menu.exportFiles"));
        refreshRecentFilesMenu();
        editMenu.setText(i18n.t("menu.edit"));
        undoMenuItem.setText(i18n.t("menu.undo"));
        redoMenuItem.setText(i18n.t("menu.redo"));
        settingsMenu.setText(i18n.t("menu.settingsMenu"));
        preferencesMenuItem.setText(i18n.t("menu.preferences"));
        helpMenu.setText(i18n.t("menu.help"));
        helpMenuItem.setText(i18n.t("menu.helpItem"));
        updateMenuItem.setText(i18n.t("menu.updateItem"));
        aboutMenuItem.setText(i18n.t("menu.aboutItem"));

        openToolButton.setTooltip(new Tooltip(i18n.t("menu.open")));
        saveToolButton.setTooltip(new Tooltip(i18n.t("menu.saveAs")));
        undoToolButton.setTooltip(new Tooltip(i18n.t("menu.undo")));
        redoToolButton.setTooltip(new Tooltip(i18n.t("menu.redo")));
        preferencesToolButton.setTooltip(new Tooltip(i18n.t("menu.preferences")));

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
        openFile(file, true);
    }

    /**
     * Menüpunkt "Backup laden...": öffnet einen normalen Datei-Dialog, der
     * aber im Backup-Ordner (Einstellungen → Pfade) startet - Backup-Dateien
     * heißen {@code <original>.<N>.bak} und tragen
     * deshalb nie die Endung .tcd/.tcdz, tauchen im normalen
     * "Öffnen"-Dialog also nicht auf. Die gewählte Datei wird danach ganz
     * normal in die Ansicht geladen (siehe {@link #openFile}); ob es sich
     * dabei um ein ursprüngliches .tcd oder .tcdz handelte, wird beim Laden
     * anhand der Datei selbst erkannt (siehe {@link TcdDocument}), nicht
     * anhand der .bak-Endung.
     */
    @FXML
    private void onLoadBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("menu.loadBackup"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Backup (*.bak)", "*.bak"),
                new FileChooser.ExtensionFilter("*.*", "*.*"));
        String backupDir = AppSettings.getInstance().getBackupDirectory();
        if (backupDir != null && new File(backupDir).isDirectory()) {
            chooser.setInitialDirectory(new File(backupDir));
        }

        Stage stage = (Stage) tabPane.getScene().getWindow();
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        // Bewusst NICHT zu "Zuletzt verwendet" hinzufügen - Backup-Dateinamen
        // sind wenig aussagekräftig und würden die Liste nur unnötig
        // zumüllen; für Backups gibt es ja bereits diesen eigenen Eintrag.
        openFile(file, false);
    }

    /**
     * Menüpunkt "Export Dateien": der Export-Ordner enthält
     * KEINE vollständigen .tcd/.tcdz-Dateien, sondern CSV-Exporte einzelner
     * Einträge (siehe "Markierte exportieren"/"Importieren" je Kategorie-
     * Reiter) - eine CSV-Datei lässt sich nicht wie ein normales Dokument
     * laden (kein XML), sondern muss in ein bereits geöffnetes Dokument
     * IMPORTIERT werden. Dieser Menüpunkt ist deshalb nur eine schnellere
     * Zugriffsmöglichkeit auf genau denselben Import, den es je Reiter über
     * den Button "Importieren" schon gibt (inkl. Start im Export-Ordner) -
     * ohne dass man erst zu einem bestimmten Reiter wechseln muss, da jede
     * CSV-Zeile ohnehin anhand ihrer eigenen Kategorie-Spalte einsortiert
     * wird (siehe {@link CategoryEditor#triggerImport()}).
     */
    @FXML
    private void onOpenExportFiles() {
        if (document == null) {
            new Alert(Alert.AlertType.INFORMATION, i18n.t("error.pleaseOpenFirst")).showAndWait();
            return;
        }
        CategoryEditor editor = editorsByTab.get(tabPane.getSelectionModel().getSelectedItem());
        if (editor == null) {
            editor = editorsByTab.values().stream().findFirst().orElse(null);
        }
        if (editor != null) {
            editor.triggerImport();
        }
    }

    /**
     * Wird von einem Eintrag in "Zuletzt verwendet..." aufgerufen. Existiert
     * die Datei nicht mehr (verschoben/gelöscht), wird gewarnt und der
     * Eintrag aus der Liste entfernt, statt einen unklaren Ladefehler zu
     * zeigen.
     */
    private void openRecentFile(String path) {
        File file = new File(path);
        if (!file.isFile()) {
            new Alert(Alert.AlertType.WARNING, i18n.t("error.recentFileMissing", path)).showAndWait();
            AppSettings.getInstance().removeRecentFile(path);
            refreshRecentFilesMenu();
            return;
        }
        openFile(file, true);
    }

    /**
     * Gemeinsame Lade-Logik für "Öffnen...", "Zuletzt verwendet...",
     * "Backup laden..." und "Export Dateien" - lädt {@code file} exakt
     * gleich in die Ansicht, unabhängig davon, über welchen Menüpunkt die
     * Datei gewählt wurde. {@code addToRecent} steuert, ob die Datei danach
     * in "Zuletzt verwendet..." aufgenommen wird (bei Backup/Export-Dateien
     * bewusst nicht, siehe {@link #onLoadBackup()}/{@link #onOpenExportFiles()}).
     */
    private void openFile(File file, boolean addToRecent) {
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
            // Rückgängig-Verlauf gehört zum bisherigen Dokument - mit einer
            // neuen Datei ergibt er keinen Sinn mehr.
            undoStack.clear();
            redoStack.clear();
            rebuildTabs();
            fileNameLabel.setText(file.getName());
            statusLabel.setText(i18n.t("status.fileLoaded", file.getName()));
            updateUndoRedoState();
            if (addToRecent) {
                AppSettings.getInstance().addRecentFile(file.getAbsolutePath());
                refreshRecentFilesMenu();
            }
        } catch (Exception ex) {
            deleteQuietly(backup);
            showError(i18n.t("error.loadTitle"), ex);
        }
    }

    /**
     * Baut den Inhalt des eigenständigen Untermenüs "Zuletzt verwendet..."
     * komplett neu auf: bis zu 5 zuletzt geöffnete Dateien, oder ein
     * deaktivierter Platzhalter-Eintrag, falls die Liste leer ist. "Backup
     * laden..." und "Export Dateien" sind eigene Menüpunkte direkt im
     * Datei-Menü (siehe {@code loadBackupMenuItem}/{@code exportFilesMenuItem})
     * und nicht mehr Teil dieses Untermenüs. Wird bei Sprachwechsel (für die
     * übersetzten Texte) sowie nach jeder Änderung der Liste (neue/entfernte
     * Datei) neu aufgerufen.
     */
    private void refreshRecentFilesMenu() {
        recentFilesMenu.getItems().clear();

        List<String> recentFiles = AppSettings.getInstance().getRecentFiles();
        if (recentFiles.isEmpty()) {
            MenuItem placeholder = new MenuItem(i18n.t("menu.recentFilesEmpty"));
            placeholder.setDisable(true);
            recentFilesMenu.getItems().add(placeholder);
        } else {
            for (String path : recentFiles) {
                MenuItem item = new MenuItem(path);
                item.setOnAction(e -> openRecentFile(path));
                recentFilesMenu.getItems().add(item);
            }
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
    private void onPreferences() {
        Stage stage = (Stage) tabPane.getScene().getWindow();
        SettingsDialog.showPreferences(stage);
        // Spaltensichtbarkeit, "Daten ändern"-Bereich usw. werden erst beim
        // Aufbau eines CategoryEditor gelesen - nach Schließen des Dialogs
        // alle Reiter neu aufbauen, damit Änderungen sofort sichtbar werden
        // (wie beim Sprachwechsel).
        rebuildTabs();
    }

    /**
     * Wird von jedem {@link CategoryEditor} VOR jeder tatsächlichen
     * inhaltlichen Änderung aufgerufen (Konstruktor-Parameter
     * {@code beforeChange}) - sichert den aktuellen Stand aller
     * Kategorie-Knoten für "Rückgängig", bevor die Änderung passiert. Ein
     * neuer Änderungs-Vorgang macht den bisherigen Wiederholen-Verlauf
     * ungültig, daher wird {@code redoStack} geleert.
     */
    private void recordUndoSnapshot() {
        if (document == null) {
            return;
        }
        undoStack.push(snapshotCurrentState());
        while (undoStack.size() > MAX_UNDO_STEPS) {
            undoStack.removeLast();
        }
        redoStack.clear();
        updateUndoRedoState();
    }

    /** Tiefe Kopie aller aktuellen Kategorie-Knoten unter control-items. */
    private List<XmlNode> snapshotCurrentState() {
        XmlNode controlItems = document.getRoot().findChild("control-items");
        List<XmlNode> snapshot = new ArrayList<>();
        for (XmlNode category : controlItems.getChildren()) {
            snapshot.add(category.deepCopy());
        }
        return snapshot;
    }

    /**
     * Ersetzt den Inhalt von control-items durch eine (erneut tief kopierte)
     * Momentaufnahme - so bleiben die im Undo-/Redo-Stack gespeicherten
     * Zustände von der live bearbeiteten Baumstruktur unabhängig. Da alle
     * Reiter über {@code CategoryEditor} an die bisherigen XmlNode-Objekte
     * gebunden sind, müssen sie danach komplett neu aufgebaut werden.
     */
    private void restoreState(List<XmlNode> snapshot) {
        XmlNode controlItems = document.getRoot().findChild("control-items");
        controlItems.getChildren().clear();
        for (XmlNode category : snapshot) {
            controlItems.getChildren().add(category.deepCopy());
        }
        document.markDirty();
        rebuildTabs();
        updateUndoRedoState();
    }

    @FXML
    private void onUndo() {
        if (document == null || undoStack.isEmpty()) {
            return;
        }
        redoStack.push(snapshotCurrentState());
        restoreState(undoStack.pop());
    }

    @FXML
    private void onRedo() {
        if (document == null || redoStack.isEmpty()) {
            return;
        }
        undoStack.push(snapshotCurrentState());
        restoreState(redoStack.pop());
    }

    private void updateUndoRedoState() {
        boolean canUndo = !undoStack.isEmpty();
        boolean canRedo = !redoStack.isEmpty();
        undoMenuItem.setDisable(!canUndo);
        redoMenuItem.setDisable(!canRedo);
        undoToolButton.setDisable(!canUndo);
        redoToolButton.setDisable(!canRedo);
    }

    @FXML
    private void onHelp() {
        Stage stage = (Stage) tabPane.getScene().getWindow();
        HelpDialog.show(stage);
    }

    @FXML
    private void onAbout() {
        Stage stage = (Stage) tabPane.getScene().getWindow();
        AboutDialog.show(stage);
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
        CategoryEditor editor = new CategoryEditor(categoryName, controlItems, document::markDirty,
                this::rebuildTabs, this::recordUndoSnapshot);
        Tab tab = editor.createTab();
        editorsByTab.put(tab, editor);
        editor.entryCountProperty().addListener((obs, oldV, newV) -> {
            if (tabPane.getSelectionModel().getSelectedItem() == tab) {
                updateStatusForSelectedTab();
            }
        });
        editor.selectedCountProperty().addListener((obs, oldV, newV) -> {
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
            String entryCountText = i18n.t("status.entryCount", editor.getDisplayName(), editor.entryCountProperty().get());
            String selectedCountText = i18n.t("status.selectedCount", editor.selectedCountProperty().get());
            statusLabel.setText(entryCountText + "   " + selectedCountText);
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
