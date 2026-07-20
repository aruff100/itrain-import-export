package com.example.itrain_import_export;

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
}
