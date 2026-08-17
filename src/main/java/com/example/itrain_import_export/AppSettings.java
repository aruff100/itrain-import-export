package com.example.itrain_import_export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Dauerhaft gespeicherte Anwendungseinstellungen (aktuell: Standard-Ordner
 * für iTrain-Dateien und für Exports). Wird über die Java-Preferences-API
 * abgelegt (unter Windows im Registry-Zweig
 * HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\example\itrain_import_export),
 * bleibt also automatisch zwischen Programmstarts erhalten, ohne dass wir
 * selbst eine Konfigurationsdatei verwalten müssen.
 */
public final class AppSettings {

    private static final String KEY_TCD_DIRECTORY = "tcdDirectory";
    private static final String KEY_EXPORT_DIRECTORY = "exportDirectory";
    private static final String KEY_BACKUP_DIRECTORY = "backupDirectory";
    private static final String KEY_THEME = "theme";
    private static final String KEY_SHOW_TYPE_COLUMN = "showTypeColumn";
    private static final String KEY_SHOW_SELECTION_CHECKBOX = "showSelectionCheckbox";
    private static final String KEY_SHOW_DATA_EDITOR = "showDataEditor";
    private static final String KEY_RECENT_FILES = "recentFiles";
    private static final String KEY_AUTO_UPDATE_CHECK = "autoUpdateCheck";
    private static final String KEY_SHOW_DECODER_HINTS = "showDecoderHints";
    private static final String KEY_DECODER_DIRECTORY = "decoderDirectory";
    private static final String KEY_DECODER_PACK_VERSION = "decoderPackVersion";
    private static final String KEY_DECODER_PACK_INSTALLED = "decoderPackInstalled";
    private static final String KEY_PREFERRED_SCREEN = "preferredScreen";
    private static final String KEY_DIALOG_OFFSET = "dialogOffset";
    private static final String KEY_CACHED_MANUAL_URL = "cachedManualUrl";
    private static final String KEY_CACHED_DECODER_URL = "cachedDecoderUrl";

    /**
     * Vorsatz für die gemerkte Fensterlage. Je Fenster ein eigener Eintrag
     * ({@code window.main}, {@code window.decoderCapture}, ...), siehe
     * {@link WindowState}.
     */
    private static final String WINDOW_PREFIX = "window.";

    /**
     * Wert für "Bildschirm": Fenster erscheinen dort, wo sie zuletzt
     * standen. Voreinstellung - alles andere waere fuer Anwender mit nur
     * einem Bildschirm nur Ballast.
     */
    public static final String SCREEN_REMEMBER = "remember";

    /** Maximale Anzahl gemerkter zuletzt geöffneter Dateien (Menü "Zuletzt verwendet..."). */
    private static final int MAX_RECENT_FILES = 5;

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private static AppSettings instance;

    private final Preferences prefs = Preferences.userNodeForPackage(AppSettings.class);

    private AppSettings() {
    }

    public static synchronized AppSettings getInstance() {
        if (instance == null) {
            instance = new AppSettings();
        }
        return instance;
    }

    /** Standard-Ordner für iTrain-.tcd/.tcdz-Dateien, oder null falls nicht gesetzt. */
    public String getTcdDirectory() {
        return prefs.get(KEY_TCD_DIRECTORY, null);
    }

    public void setTcdDirectory(String path) {
        prefs.put(KEY_TCD_DIRECTORY, path);
    }

    /** Standard-Ordner, in dem CSV-Exports gesammelt werden, oder null falls nicht gesetzt. */
    public String getExportDirectory() {
        return prefs.get(KEY_EXPORT_DIRECTORY, null);
    }

    public void setExportDirectory(String path) {
        prefs.put(KEY_EXPORT_DIRECTORY, path);
    }

    /**
     * Ordner, in den beim Öffnen einer Datei automatisch eine unveränderte
     * Sicherheitskopie des Originals abgelegt wird, oder null falls nicht
     * gesetzt (dann findet keine Sicherung statt).
     */
    public String getBackupDirectory() {
        return prefs.get(KEY_BACKUP_DIRECTORY, null);
    }

    public void setBackupDirectory(String path) {
        prefs.put(KEY_BACKUP_DIRECTORY, path);
    }

    /** "light" oder "dark", Standard ist "light". */
    public String getTheme() {
        return prefs.get(KEY_THEME, THEME_LIGHT);
    }

    public void setTheme(String theme) {
        prefs.put(KEY_THEME, theme);
    }

    /** Ob die Typ-Spalte in der Kategorie-Tabelle angezeigt wird. Standard: ausgeblendet. */
    public boolean getShowTypeColumn() {
        return prefs.getBoolean(KEY_SHOW_TYPE_COLUMN, false);
    }

    public void setShowTypeColumn(boolean show) {
        prefs.putBoolean(KEY_SHOW_TYPE_COLUMN, show);
    }

    /** Ob die Auswahlbox-Spalte in der Kategorie-Tabelle angezeigt wird. Standard: ausgeblendet. */
    public boolean getShowSelectionCheckbox() {
        return prefs.getBoolean(KEY_SHOW_SELECTION_CHECKBOX, false);
    }

    public void setShowSelectionCheckbox(boolean show) {
        prefs.putBoolean(KEY_SHOW_SELECTION_CHECKBOX, show);
    }

    /**
     * Ob der "Daten ändern"-Bereich (rechter Bereich, in dem Daten eines
     * ausgewählten Eintrags bearbeitet werden können) angezeigt wird.
     * Standard: ausgeblendet, damit die Ansicht beim Start nicht
     * dreigeteilt, sondern nur zweigeteilt erscheint.
     */
    public boolean getShowDataEditor() {
        return prefs.getBoolean(KEY_SHOW_DATA_EDITOR, false);
    }

    public void setShowDataEditor(boolean show) {
        prefs.putBoolean(KEY_SHOW_DATA_EDITOR, show);
    }

    /**
     * Bis zu {@value #MAX_RECENT_FILES} zuletzt geöffnete Dateipfade, neuester
     * zuerst - für das Menü "Zuletzt verwendet..." (siehe HelloController).
     * Intern als ein einzelner, zeilenweise getrennter Preferences-Wert
     * abgelegt (die Preferences-API kennt keine echten Listen); Dateipfade
     * enthalten keine Zeilenumbrüche, daher ist "\n" als Trenner sicher.
     */
    public List<String> getRecentFiles() {
        String joined = prefs.get(KEY_RECENT_FILES, "");
        if (joined.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(joined.split("\n")));
    }

    /**
     * Trägt {@code path} ganz vorne ein (neuester Eintrag). War der Pfad
     * bereits in der Liste, wird er zunächst entfernt und dann wieder vorne
     * eingefügt (kein doppelter Eintrag, "zuletzt benutzt" rückt er trotzdem
     * an die erste Stelle). Ist die Liste danach länger als
     * {@value #MAX_RECENT_FILES}, wird der/die älteste(n) Eintrag/Einträge
     * am Ende verworfen.
     */
    public void addRecentFile(String path) {
        List<String> current = getRecentFiles();
        current.remove(path);
        current.add(0, path);
        while (current.size() > MAX_RECENT_FILES) {
            current.remove(current.size() - 1);
        }
        prefs.put(KEY_RECENT_FILES, String.join("\n", current));
    }

    /** Entfernt einen Pfad aus der Liste (z.B. weil die Datei nicht mehr existiert). */
    public void removeRecentFile(String path) {
        List<String> current = getRecentFiles();
        if (current.remove(path)) {
            prefs.put(KEY_RECENT_FILES, String.join("\n", current));
        }
    }

    /**
     * Ob bei jedem Programmstart still im Hintergrund auf eine neue Version
     * geprüft wird (siehe {@link UpdateChecker}, ausgelöst von
     * {@link HelloApplication#start}). Standard: an. Der manuelle Menüpunkt
     * Hilfe → Update funktioniert unabhängig davon immer.
     */
    public boolean getAutoUpdateCheckEnabled() {
        return prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, true);
    }

    public void setAutoUpdateCheckEnabled(boolean enabled) {
        prefs.putBoolean(KEY_AUTO_UPDATE_CHECK, enabled);
    }

    /**
     * Ob vor dem Bearbeiten einer Decoder-Konfiguration der Hinweistext
     * erscheint (siehe {@link DecoderHintsDialog}). Standard: an - beim ersten
     * Mal ist der Hinweis nützlich, danach schaltet ihn das Ankreuzfeld im
     * Dialog selbst ab. Wieder einschalten geht in den Voreinstellungen →
     * Ansicht.
     */
    public boolean getShowDecoderHints() {
        return prefs.getBoolean(KEY_SHOW_DECODER_HINTS, true);
    }

    public void setShowDecoderHints(boolean show) {
        prefs.putBoolean(KEY_SHOW_DECODER_HINTS, show);
    }

    /**
     * Ordner, in dem die Decoder-Vorlagen (einzelne CSV-Dateien, entpackt aus
     * der decoder.zip) liegen, oder null falls nicht gesetzt. Standardvorschlag
     * ist {@code <Benutzerverzeichnis>/iTrain/Decoder} - siehe
     * {@link FirstRunDialog} bzw. Voreinstellungen → Pfade.
     */
    public String getDecoderDirectory() {
        return prefs.get(KEY_DECODER_DIRECTORY, null);
    }

    public void setDecoderDirectory(String path) {
        prefs.put(KEY_DECODER_DIRECTORY, path);
    }

    /**
     * Versionskennung der zuletzt installierten decoder.zip - stammt aus der
     * Datei {@code version.txt} im Archiv (siehe
     * {@link DecoderTemplateInstaller}). Fehlt sie im Archiv, bleibt der Wert
     * leer; angezeigt wird dann nur das Installationsdatum.
     */
    /**
     * Zuletzt aus dem Update-Manifest gelesene Adresse des Handbuchs, oder
     * {@code null}, solange noch nie eine abgerufen wurde.
     * <p>
     * <b>Warum gemerkt:</b> Die Adressen für Handbuch und Decoder-Vorlagen
     * stehen bewusst NICHT mehr fest im Programm, sondern im Manifest
     * (siehe {@link UpdateChecker}) - eine Freigabe-Adresse im Programm
     * liess Windows Defender die fertige .msi blockieren. Damit Handbuch und
     * Vorlagen trotzdem erreichbar bleiben, wenn das Manifest gerade nicht
     * abrufbar ist, wird die zuletzt gelesene Adresse hier behalten.
     */
    public String getCachedManualUrl() {
        return prefs.get(KEY_CACHED_MANUAL_URL, null);
    }

    public void setCachedManualUrl(String url) {
        if (url == null || url.isBlank()) {
            prefs.remove(KEY_CACHED_MANUAL_URL);
        } else {
            prefs.put(KEY_CACHED_MANUAL_URL, url);
        }
    }

    /** Zuletzt aus dem Update-Manifest gelesene Adresse der Decoder-Vorlagen, siehe {@link #getCachedManualUrl()}. */
    public String getCachedDecoderUrl() {
        return prefs.get(KEY_CACHED_DECODER_URL, null);
    }

    public void setCachedDecoderUrl(String url) {
        if (url == null || url.isBlank()) {
            prefs.remove(KEY_CACHED_DECODER_URL);
        } else {
            prefs.put(KEY_CACHED_DECODER_URL, url);
        }
    }

    public String getDecoderPackVersion() {
        return prefs.get(KEY_DECODER_PACK_VERSION, null);
    }

    public void setDecoderPackVersion(String version) {
        prefs.put(KEY_DECODER_PACK_VERSION, version == null ? "" : version);
    }

    /**
     * Zeitpunkt der letzten Vorlagen-Installation als ISO-Text
     * (z.B. "2026-08-01T14:32"), oder null falls noch nie installiert.
     */
    public String getDecoderPackInstalled() {
        return prefs.get(KEY_DECODER_PACK_INSTALLED, null);
    }

    public void setDecoderPackInstalled(String isoTimestamp) {
        prefs.put(KEY_DECODER_PACK_INSTALLED, isoTimestamp);
    }

    /**
     * Bevorzugter Bildschirm für neu geöffnete Fenster:
     * {@link #SCREEN_REMEMBER} (die gemerkte Lage entscheidet) oder die
     * Nummer eines Bildschirms als Text ("0", "1", ...).
     */
    public String getPreferredScreen() {
        return prefs.get(KEY_PREFERRED_SCREEN, SCREEN_REMEMBER);
    }

    public void setPreferredScreen(String screen) {
        prefs.put(KEY_PREFERRED_SCREEN, screen == null ? SCREEN_REMEMBER : screen);
    }

    /**
     * Gemerkte Lage eines Fensters (Position, Größe, Vollbild) als
     * zusammengesetzter Text - siehe {@link WindowState}. Der Aufbau ist
     * bewusst dort gekapselt: Hier wird nur abgelegt und geholt.
     */
    public String getWindowState(String windowKey) {
        return prefs.get(WINDOW_PREFIX + windowKey, null);
    }

    public void setWindowState(String windowKey, String value) {
        if (value == null || value.isBlank()) {
            prefs.remove(WINDOW_PREFIX + windowKey);
        } else {
            prefs.put(WINDOW_PREFIX + windowKey, value);
        }
    }

    /**
     * Verschiebung der Dialoge gegenüber der Mitte des Hauptfensters, als
     * {@code "dx;dy"} in Bildpunkten - siehe {@link DialogPlacement}.
     * <p>
     * Bewusst ein <em>Abstand</em> statt einer festen Bildschirmposition:
     * Dialoge sind unterschiedlich groß und das Hauptfenster wandert zwischen
     * Bildschirmen. Eine gemerkte absolute Lage zeigte nach einem Umzug ins
     * Leere, ein Abstand zur Fenstermitte nie.
     *
     * @return {@code null}, wenn der Anwender noch keinen Dialog verschoben hat
     */
    public String getDialogOffset() {
        return prefs.get(KEY_DIALOG_OFFSET, null);
    }

    /** {@code null} löscht die Verschiebung - Dialoge erscheinen dann wieder mittig. */
    public void setDialogOffset(String value) {
        if (value == null || value.isBlank()) {
            prefs.remove(KEY_DIALOG_OFFSET);
        } else {
            prefs.put(KEY_DIALOG_OFFSET, value);
        }
    }
}
