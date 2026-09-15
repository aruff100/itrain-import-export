package com.example.itrain_import_export;

import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
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
 * Alles, was ein Fenster braucht, um EINE iTrain-Datei zu bearbeiten:
 * das geladene Dokument, Öffnen/Speichern samt Backup-Logik, der
 * Rückgängig-/Wiederholen-Verlauf, der Aufbau der Kategorie-Reiter und die
 * Statuszeile.
 * <p>
 * Bis Version 2.0.1 lag das alles direkt in {@link HelloController}. Seit dem
 * Umbau auf drei Fenster (Version 2.5) gibt es ZWEI Fenster, die eine Datei
 * bearbeiten - das Hauptfenster "Import/Export" mit allen Kategorien und das
 * Fenster "Decoder" mit nur Lokomotiven und Wagen. Beide benutzen diese
 * Klasse; sie unterscheiden sich nur in der Kategorieliste und darin, wie
 * sie Dateiname, Status und Rückgängig-Zustand anzeigen ({@link Host}).
 * <p>
 * Beide Fenster arbeiten auf DERSELBEN Datei: Die Sitzung des Hauptfensters
 * ist die Hauptsitzung und besitzt Dokument, Backup und Rückgängig-Verlauf;
 * die Sitzung des Decoder-Fensters ist eine Nebensitzung
 * ({@link #attachTo(DocumentSession)}), die alles Dokumentbezogene an die
 * Hauptsitzung weiterreicht und nur ihre eigenen Reiter aufbaut. Öffnet man
 * im Decoder-Fenster eine Datei, erscheint sie damit auch im Hauptfenster;
 * ist dort schon eine offen, zeigt das Decoder-Fenster genau diese. Die
 * Reiter beider Fenster hängen an denselben {@link XmlNode}-Listen, sodass
 * Änderungen in einem Fenster im anderen sofort sichtbar sind; nach
 * Öffnen, Rückgängig und Wiederholen werden die Reiter der Nebensitzungen
 * neu aufgebaut ({@link #notifySecondaries()}).
 */
public final class DocumentSession {

    /**
     * Rückkanal zum Fenster: Die Sitzung kennt weder Menüs noch Ribbon, sie
     * meldet nur, was sich geändert hat. Das Fenster entscheidet, wie es das
     * darstellt.
     */
    public interface Host {
        /** Fenster, über dem Dateidialoge und Meldungen erscheinen. */
        Stage stage();

        /** Text für die Dateiname-Zeile (Dateiname oder "Keine Datei geladen."). */
        void showFileName(String text);

        /** Text für die Statuszeile unten. */
        void showStatus(String text);

        /** Rückgängig/Wiederholen möglich oder nicht - für Menü und Ribbon. */
        void undoRedoStateChanged(boolean canUndo, boolean canRedo);

        /** Nach jedem Reiteraufbau und jedem Reiterwechsel - z.B. für den Ribbon-Zustand. */
        void selectionChanged();

        /** Die Liste "Zuletzt verwendet" hat sich geändert. */
        void recentFilesChanged();

        /** Eine Datei wurde geladen (oder das Dokument verworfen) - z.B. für das Startbild. */
        void documentChanged(boolean loaded);
    }

    /**
     * Trennung der drei Angaben in der Statuszeile (Einträge / Ausgewählt /
     * Verbundene Datensätze): breiter Abstand, senkrechter Strich, breiter
     * Abstand.
     */
    private static final String STATUS_SEPARATOR = "      |      ";

    /** Maximale Anzahl gespeicherter Rückgängig-Schritte (siehe {@link #recordUndoSnapshot()}). */
    private static final int MAX_UNDO_STEPS = 50;

    /** Maximale Anzahl gleichzeitiger Backup-Generationen je Originaldatei. */
    private static final int MAX_BACKUP_GENERATIONS = 10;

    private final I18n i18n = I18n.getInstance();
    private final TabPane tabPane;
    private final Host host;

    /**
     * Welche Kategorien als Reiter erscheinen, in dieser Reihenfolge - oder
     * {@code null} für "alle bekannten plus unbekannte aus der Datei" (das
     * Hauptfenster). Mit einer festen Liste (Decoder-Fenster: Lokomotiven,
     * Wagen) erscheinen ausschließlich diese; die Datei wird trotzdem
     * vollständig geladen und vollständig zurückgeschrieben.
     */
    private final List<String> categoryFilter;

    private TcdDocument document;
    private final Map<Tab, CategoryEditor> editorsByTab = new HashMap<>();

    /**
     * Hauptsitzung, an die diese Sitzung angehängt ist - oder null, wenn
     * diese Sitzung selbst die Hauptsitzung ist (siehe Klassenkommentar).
     */
    private DocumentSession primary;
    /** Angehängte Nebensitzungen (nur in der Hauptsitzung befüllt). */
    private final List<DocumentSession> secondaries = new ArrayList<>();

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

    /**
     * Zählt CSV-Importe innerhalb der aktuellen Sitzung (solange dieselbe
     * Datei geöffnet bleibt) - liefert an {@link CategoryEditor} die Nummer
     * für die zusätzliche "~1"/"~2"/...-Durchnummerierung bereits
     * verknüpfter Einträge (siehe
     * {@code CategoryEditor.renameLinkedEntriesForThisImport}), damit
     * mehrere Importe in derselben Sitzung nicht kollidieren. 0 beim ersten
     * Import (bleibt bei reinem "~"), wird beim Öffnen einer neuen Datei
     * zurückgesetzt - überlebt aber (anders als der Undo-Verlauf) bewusst
     * jeden {@code rebuildTabs()}, da dieses Feld hier liegt, nicht im
     * (dabei neu angelegten) CategoryEditor selbst.
     */
    private int importCounter = 0;

    /**
     * @param tabPane        Reiterleiste des Fensters, wird von der Sitzung befüllt
     * @param categoryFilter Kategorien, die als Reiter erscheinen sollen -
     *                       {@code null} für alle (siehe {@link #categoryFilter})
     * @param host           Rückkanal zum Fenster
     */
    public DocumentSession(TabPane tabPane, List<String> categoryFilter, Host host) {
        this.tabPane = tabPane;
        this.categoryFilter = categoryFilter == null ? null : List.copyOf(categoryFilter);
        this.host = host;
        // Nur einmal registrieren (nicht in rebuildTabs(), das bei jedem
        // Dateiöffnen/Sprachwechsel erneut läuft) - sonst würde sich bei
        // jedem Aufruf ein weiterer Listener anhäufen.
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateStatusForSelectedTab();
            host.selectionChanged();
        });
    }

    public TcdDocument getDocument() {
        return primary != null ? primary.getDocument() : document;
    }

    public boolean hasDocument() {
        return getDocument() != null;
    }

    private int nextImportSuffix() {
        if (primary != null) {
            return primary.nextImportSuffix();
        }
        return importCounter++;
    }

    // ------------------------------------------------------------------
    // Haupt-/Nebensitzung
    // ------------------------------------------------------------------

    /**
     * Macht diese Sitzung zur Nebensitzung von {@code mainSession}: Ab jetzt
     * gilt deren Dokument auch hier, Öffnen/Speichern/Rückgängig laufen über
     * sie, und die eigenen Reiter werden sofort aus ihrem Dokument aufgebaut.
     * Beim Schließen des Fensters {@link #detach()} aufrufen.
     */
    public void attachTo(DocumentSession mainSession) {
        this.primary = mainSession;
        mainSession.secondaries.add(this);
        syncFromPrimary();
    }

    public void detach() {
        if (primary != null) {
            primary.secondaries.remove(this);
            primary = null;
        }
    }

    /** Reiter, Dateiname und Rückgängig-Zustand vom Dokument der Hauptsitzung übernehmen. */
    private void syncFromPrimary() {
        rebuildTabs();
        TcdDocument doc = getDocument();
        if (doc != null && doc.getFile() != null) {
            host.showFileName(doc.getFile().getName());
        } else {
            host.showFileName(i18n.t("status.noFileLoaded"));
        }
        host.undoRedoStateChanged(!primary.undoStack.isEmpty(), !primary.redoStack.isEmpty());
        host.documentChanged(doc != null);
        host.recentFilesChanged();
    }

    /** Nach Öffnen, Rückgängig, Wiederholen: alle Nebensitzungen nachziehen. */
    private void notifySecondaries() {
        for (DocumentSession secondary : new ArrayList<>(secondaries)) {
            secondary.syncFromPrimary();
        }
    }

    // ------------------------------------------------------------------
    // Öffnen / Speichern
    // ------------------------------------------------------------------

    /** "Öffnen...": Dateidialog, dann {@link #openFile(File, boolean)}. */
    public void openFileDialog() {
        openFileDialog(host.stage());
    }

    private void openFileDialog(Stage owner) {
        if (primary != null) {
            primary.openFileDialog(owner);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("dialog.openTitle"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("iTrain (*.tcd, *.tcdz)", "*.tcd", "*.tcdz"),
                new FileChooser.ExtensionFilter("iTrain XML (*.tcd)", "*.tcd"),
                new FileChooser.ExtensionFilter("iTrain ZIP (*.tcdz)", "*.tcdz"),
                new FileChooser.ExtensionFilter("*.*", "*.*"));
        applyDefaultTcdDirectory(chooser);

        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        openFile(file, true);
    }

    /**
     * "Backup laden...": öffnet einen normalen Datei-Dialog, der aber im
     * Backup-Ordner (Einstellungen → Pfade) startet - Backup-Dateien heißen
     * {@code <original>.<N>.bak} und tragen deshalb nie die Endung
     * .tcd/.tcdz, tauchen im normalen "Öffnen"-Dialog also nicht auf. Die
     * gewählte Datei wird danach ganz normal in die Ansicht geladen (siehe
     * {@link #openFile}); ob es sich dabei um ein ursprüngliches .tcd oder
     * .tcdz handelte, wird beim Laden anhand der Datei selbst erkannt (siehe
     * {@link TcdDocument}), nicht anhand der .bak-Endung.
     */
    public void loadBackupDialog() {
        loadBackupDialog(host.stage());
    }

    private void loadBackupDialog(Stage owner) {
        if (primary != null) {
            primary.loadBackupDialog(owner);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("menu.loadBackup"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Backup (*.bak)", "*.bak"),
                new FileChooser.ExtensionFilter("*.*", "*.*"));
        String backupDir = AppSettings.getInstance().getBackupDirectory();
        if (backupDir != null && new File(backupDir).isDirectory()) {
            chooser.setInitialDirectory(new File(backupDir));
        }

        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        // Bewusst NICHT zu "Zuletzt verwendet" hinzufügen - Backup-Dateinamen
        // sind wenig aussagekräftig und würden die Liste nur unnötig
        // zumüllen; für Backups gibt es ja bereits diesen eigenen Eintrag.
        openFile(file, false);
    }

    /**
     * Wird von einem Eintrag in "Zuletzt verwendet..." aufgerufen. Existiert
     * die Datei nicht mehr (verschoben/gelöscht), wird gewarnt und der
     * Eintrag aus der Liste entfernt, statt einen unklaren Ladefehler zu
     * zeigen.
     */
    public void openRecentFile(String path) {
        File file = new File(path);
        if (!file.isFile()) {
            new Alert(Alert.AlertType.WARNING, i18n.t("error.recentFileMissing", path)).showAndWait();
            AppSettings.getInstance().removeRecentFile(path);
            host.recentFilesChanged();
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
     * bewusst nicht, siehe {@link #loadBackupDialog()}).
     */
    public void openFile(File file, boolean addToRecent) {
        if (primary != null) {
            primary.openFile(file, addToRecent);
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
            // Rückgängig-Verlauf gehört zum bisherigen Dokument - mit einer
            // neuen Datei ergibt er keinen Sinn mehr.
            undoStack.clear();
            redoStack.clear();
            // Neue Datei = neue Sitzung für die Import-Nummerierung (siehe
            // nextImportSuffix()).
            importCounter = 0;
            rebuildTabs();
            host.showFileName(file.getName());
            host.showStatus(i18n.t("status.fileLoaded", file.getName()));
            updateUndoRedoState();
            host.documentChanged(true);
            if (addToRecent) {
                AppSettings.getInstance().addRecentFile(file.getAbsolutePath());
                host.recentFilesChanged();
            }
            notifySecondaries();
        } catch (Exception ex) {
            deleteQuietly(backup);
            showError(i18n.t("error.loadTitle"), ex);
        }
    }

    /**
     * "Aktuelle schließen": verwirft das geladene Dokument (ohne zu
     * speichern), leert den Rückgängig-Verlauf und räumt ein unbenutztes
     * Backup auf. Das Fenster bleibt offen und zeigt wieder "Keine Datei
     * geladen." - in der Hauptsitzung wie in allen Nebensitzungen.
     */
    public void closeDocument() {
        if (primary != null) {
            primary.closeDocument();
            return;
        }
        if (document == null) {
            return;
        }
        cleanupUnusedBackup(document);
        document = null;
        undoStack.clear();
        redoStack.clear();
        importCounter = 0;
        rebuildTabs();
        host.showFileName(i18n.t("status.noFileLoaded"));
        host.showStatus("");
        updateUndoRedoState();
        host.documentChanged(false);
        notifySecondaries();
    }

    /** "Speichern unter...". */
    public void saveAsDialog() {
        saveAsDialog(host.stage());
    }

    private void saveAsDialog(Stage owner) {
        if (primary != null) {
            primary.saveAsDialog(owner);
            return;
        }
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

        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }

        try {
            document.save(target);
            host.showFileName(target.getName());
            host.showStatus(i18n.t("status.fileSaved", target.getName()));
            for (DocumentSession secondary : secondaries) {
                secondary.host.showFileName(target.getName());
                secondary.host.showStatus(i18n.t("status.fileSaved", target.getName()));
            }
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

    /**
     * "Export Dateien": der Export-Ordner enthält KEINE vollständigen
     * .tcd/.tcdz-Dateien, sondern Exporte einzelner Einträge - die lassen
     * sich nicht wie ein Dokument laden, sondern müssen in ein bereits
     * geöffnetes Dokument IMPORTIERT werden. Dieser Aufruf löst genau
     * denselben Import aus, den es je Reiter über "Importieren" gibt, ohne
     * dass man erst zu einem bestimmten Reiter wechseln muss - jede Zeile
     * wird ohnehin anhand ihrer eigenen Kategorie-Spalte einsortiert (siehe
     * {@link CategoryEditor#triggerImport()}).
     */
    public void importIntoAnyEditor() {
        if (getDocument() == null) {
            new Alert(Alert.AlertType.INFORMATION, i18n.t("error.pleaseOpenFirst")).showAndWait();
            return;
        }
        CategoryEditor editor = currentEditor();
        if (editor == null) {
            editor = editorsByTab.values().stream().findFirst().orElse(null);
        }
        if (editor != null) {
            editor.triggerImport();
        }
    }

    // ------------------------------------------------------------------
    // Backups
    // ------------------------------------------------------------------

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
     * Dokument) sowie beim Schließen des Fensters aufgerufen.
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

    /** Beim Schließen des Fensters aufrufen. */
    public void onClosing() {
        if (primary != null) {
            // Nebensitzung: Backup und Dokument gehören der Hauptsitzung.
            detach();
            return;
        }
        cleanupUnusedBackup(document);
    }

    // ------------------------------------------------------------------
    // Rückgängig / Wiederholen
    // ------------------------------------------------------------------

    /**
     * Wird von jedem {@link CategoryEditor} VOR jeder tatsächlichen
     * inhaltlichen Änderung aufgerufen (Konstruktor-Parameter
     * {@code beforeChange}) - sichert den aktuellen Stand aller
     * Kategorie-Knoten für "Rückgängig", bevor die Änderung passiert. Ein
     * neuer Änderungs-Vorgang macht den bisherigen Wiederholen-Verlauf
     * ungültig, daher wird {@code redoStack} geleert.
     */
    private void recordUndoSnapshot() {
        if (primary != null) {
            primary.recordUndoSnapshot();
            return;
        }
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
        notifySecondaries();
    }

    public void undo() {
        if (primary != null) {
            primary.undo();
            return;
        }
        if (document == null || undoStack.isEmpty()) {
            return;
        }
        redoStack.push(snapshotCurrentState());
        restoreState(undoStack.pop());
    }

    public void redo() {
        if (primary != null) {
            primary.redo();
            return;
        }
        if (document == null || redoStack.isEmpty()) {
            return;
        }
        undoStack.push(snapshotCurrentState());
        restoreState(redoStack.pop());
    }

    private void updateUndoRedoState() {
        host.undoRedoStateChanged(!undoStack.isEmpty(), !redoStack.isEmpty());
        for (DocumentSession secondary : secondaries) {
            secondary.host.undoRedoStateChanged(!undoStack.isEmpty(), !redoStack.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // Reiter und Statuszeile
    // ------------------------------------------------------------------

    /**
     * Liefert den Editor des gerade sichtbaren Kategorie-Reiters, oder
     * {@code null}, wenn keine Datei geöffnet ist. Grundlage für die
     * Ribbon-Schaltflächen, die immer auf den sichtbaren Reiter wirken.
     */
    public CategoryEditor currentEditor() {
        return editorsByTab.get(tabPane.getSelectionModel().getSelectedItem());
    }

    /**
     * Baut alle Reiter neu auf - nach dem Laden einer Datei, nach
     * Rückgängig/Wiederholen, nach Sprachwechsel und nach geänderten
     * Voreinstellungen (Spaltensichtbarkeit u.ä. werden erst beim Aufbau
     * eines CategoryEditor gelesen).
     */
    public void rebuildTabs() {
        tabPane.getTabs().clear();
        editorsByTab.clear();
        TcdDocument doc = getDocument();
        if (doc == null) {
            host.selectionChanged();
            return;
        }
        XmlNode controlItems = doc.getRoot().findChild("control-items");
        if (controlItems == null) {
            new Alert(Alert.AlertType.WARNING, i18n.t("error.noControlItems")).showAndWait();
            host.selectionChanged();
            return;
        }

        if (categoryFilter != null) {
            // Festes Fenster-Sortiment (Decoder-Fenster): nur diese
            // Kategorien, in genau dieser Reihenfolge, auch wenn die Datei
            // sie (noch) nicht enthält.
            for (String categoryName : categoryFilter) {
                addCategoryTab(controlItems, categoryName);
            }
        } else {
            Set<String> created = new LinkedHashSet<>();

            // Immer alle bekannten Kategorien als Reiter anzeigen, auch wenn
            // die Datei sie (noch) nicht enthält - der Reiter bleibt dann
            // leer, bis ein Eintrag hinzugefügt oder importiert wird.
            for (String categoryName : TcdDocument.TAB_DISPLAY_ORDER) {
                addCategoryTab(controlItems, categoryName);
                created.add(categoryName);
            }

            // Zusätzliche, uns nicht bekannte Kategorien (kommt vor, falls
            // künftige iTrain-Versionen neue Kategorien einführen) werden
            // ebenfalls angezeigt, in der Reihenfolge, in der sie in der
            // Datei stehen - ihre Position beim Speichern bleibt unverändert
            // (siehe TcdDocument.reorderChildren).
            for (XmlNode categoryNode : controlItems.getChildren()) {
                if (!created.contains(categoryNode.getTagName())) {
                    addCategoryTab(controlItems, categoryNode.getTagName());
                    created.add(categoryNode.getTagName());
                }
            }
        }

        if (!tabPane.getTabs().isEmpty()) {
            tabPane.getSelectionModel().select(0);
        }
        updateStatusForSelectedTab();
        host.selectionChanged();
    }

    private void addCategoryTab(XmlNode controlItems, String categoryName) {
        CategoryEditor editor = new CategoryEditor(categoryName, controlItems, getDocument()::markDirty,
                this::rebuildTabs, this::recordUndoSnapshot, this::nextImportSuffix);
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
        editor.linkedCountProperty().addListener((obs, oldV, newV) -> {
            if (tabPane.getSelectionModel().getSelectedItem() == tab) {
                updateStatusForSelectedTab();
            }
        });
        tabPane.getTabs().add(tab);
    }

    private void updateStatusForSelectedTab() {
        CategoryEditor editor = currentEditor();
        if (editor != null) {
            String entryCountText = i18n.t("status.entryCount", editor.getDisplayName(), editor.entryCountProperty().get());
            String selectedCountText = i18n.t("status.selectedCount", editor.selectedCountProperty().get());
            String linkedCountText = i18n.t("status.linkedCount", editor.linkedCountProperty().get());
            // Deutlich getrennte Angaben: breiter Abstand plus "|" als
            // Trennzeichen, damit die drei Zahlen nicht ineinander laufen.
            host.showStatus(entryCountText + STATUS_SEPARATOR + selectedCountText
                    + STATUS_SEPARATOR + linkedCountText);
        } else {
            host.showStatus("");
        }
    }

    private void showError(String title, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR, title + ":\n" + ex.getMessage());
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
