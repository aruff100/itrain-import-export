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
    private static final String KEY_SYSTEM_FILES_DIRECTORY = "systemFilesDirectory";
    private static final String KEY_DECODER_PACK_VERSION = "decoderPackVersion";
    private static final String KEY_DECODER_PACK_INSTALLED = "decoderPackInstalled";
    private static final String KEY_PREFERRED_SCREEN = "preferredScreen";
    private static final String KEY_DIALOG_OFFSET = "dialogOffset";
    private static final String KEY_CACHED_MANUAL_URL = "cachedManualUrl";
    private static final String KEY_CACHED_DECODER_URL = "cachedDecoderUrl";
    private static final String KEY_BUNDLED_TEMPLATES_FINGERPRINT = "bundledTemplatesFingerprint";
    private static final String KEY_BIDIB_LOCAL_UID = "bidibLocalUid";
    private static final String KEY_BIDIB_USER_NAME = "bidibUserName";
    private static final String KEY_BIDIB_LAST_IP = "bidibLastIp";
    private static final String KEY_BIDIB_LAST_PORT = "bidibLastPort";
    private static final String KEY_CUSTOM_COLOR_ACTIVE = "customColorActive";
    private static final String KEY_CUSTOM_BACKGROUND_COLOR = "customBackgroundColor";
    private static final String KEY_CUSTOM_TEXT_COLOR = "customTextColor";

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

    /**
     * Einstellungszweig des fruegeren, eigenstaendigen BiDiB-Programms
     * (iTrain_import_export_BiDiB). Aus ihm werden die netBiDiB-Werte einmalig
     * uebernommen, falls hier noch keine stehen - siehe
     * {@link #inheritedBidibValue}.
     */
    private static final String BIDIB_LEGACY_PREFS_NODE = "com/example/itrain_import_export_bidib";

    /** Merker, dass die netBiDiB-Werte aus dem alten Zweig bereits geholt wurden. */
    private static final String KEY_BIDIB_LEGACY_MIGRATED = "bidibLegacyMigrated";

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

    /**
     * Ob statt Hell/Dunkel eine eigene, vom Anwender per {@code
     * CustomColorDialog} gewaehlte Hintergrund-/Textfarbe gilt. Diese wirkt
     * ueberall dort, wo sonst Hell/Dunkel wirkt (siehe {@link ThemeManager}),
     * und wird abgeschaltet, sobald in der Farbschema-Auswahl erneut "Hell"
     * oder "Dunkel" gewaehlt wird.
     */
    public boolean isCustomColorActive() {
        return prefs.getBoolean(KEY_CUSTOM_COLOR_ACTIVE, false);
    }

    public void setCustomColorActive(boolean active) {
        prefs.putBoolean(KEY_CUSTOM_COLOR_ACTIVE, active);
    }

    /**
     * Eigene, dauerhaft gemerkte netBiDiB-Unique-ID dieses Programms (7 Byte,
     * als Hex-Text), oder {@code null}, wenn noch keine erzeugt wurde - siehe
     * {@link BidibConnectionDialog}. Muss über Neustarts hinweg stabil
     * bleiben, weil die Pairing-Vertrauensstellung an genau diese ID gebunden
     * ist (bei jedem Programmstart eine neue UID würde bei jedem Verbinden
     * ein erneutes Pairing verlangen).
     */
    public String getBidibLocalUid() {
        return inheritedBidibValue(KEY_BIDIB_LOCAL_UID);
    }

    /**
     * Liest einen netBiDiB-Wert - und holt ihn einmalig aus dem Zweig des
     * fruegeren BiDiB-Programms, wenn hier noch keiner steht.
     * <p>
     * Wichtig fuer die eigene Unique-ID: Das Pairing im Geraet haengt an
     * genau dieser Kennung. Eine hier neu erzeugte ID kennt das Geraet nicht,
     * es antwortet dann mit "unpaired" und verlangt trotz gefuelltem
     * Pairing-Speicher eine neue Bestaetigung. Mit der uebernommenen ID
     * erkennt es das Programm dagegen wieder.
     */
    private String inheritedBidibValue(String key) {
        migrateBidibValuesOnce();
        return prefs.get(key, null);
    }

    /**
     * Holt die netBiDiB-Werte EINMALIG aus dem Zweig des frueheren
     * BiDiB-Programms - auch dann, wenn hier bereits welche stehen.
     * <p>
     * Genau das ist der Punkt: Beim ersten Verbindungsversuch im Hauptprogramm
     * wurde bereits eine neue Unique-ID erzeugt und gespeichert. Ein Rueckfall
     * "nur wenn hier nichts steht" haette danach nie mehr in den alten Zweig
     * gesehen - und das Geraet haette das Programm bei jedem Verbinden erneut
     * als fremd behandelt und ein Pairing verlangt. Die Uebernahme laeuft
     * deshalb einmalig ueber einen eigenen Merker und ersetzt dabei die
     * inzwischen erzeugte Kennung.
     */
    private void migrateBidibValuesOnce() {
        if (prefs.getBoolean(KEY_BIDIB_LEGACY_MIGRATED, false)) {
            return;
        }
        try {
            Preferences legacy = Preferences.userRoot().node(BIDIB_LEGACY_PREFS_NODE);
            for (String key : new String[]{KEY_BIDIB_LOCAL_UID, KEY_BIDIB_USER_NAME}) {
                String value = legacy.get(key, null);
                if (value != null && !value.isBlank()) {
                    prefs.put(key, value);
                }
            }
        } catch (Exception ignored) {
            // Kein Zugriff auf den alten Zweig: dann bleibt es bei den eigenen Werten.
        }
        prefs.putBoolean(KEY_BIDIB_LEGACY_MIGRATED, true);
    }

    public void setBidibLocalUid(String hex) {
        prefs.put(KEY_BIDIB_LOCAL_UID, hex);
    }

    /**
     * Name, mit dem sich dieses Programm bei anderen netBiDiB-Teilnehmern
     * vorstellt (DESCRIPTOR_USER_STRING), oder {@code null} für den
     * Standardvorschlag (Rechnername) - siehe {@link BidibConnectionDialog}.
     */
    public String getBidibUserName() {
        return inheritedBidibValue(KEY_BIDIB_USER_NAME);
    }

    public void setBidibUserName(String name) {
        if (name == null || name.isBlank()) {
            prefs.remove(KEY_BIDIB_USER_NAME);
        } else {
            prefs.put(KEY_BIDIB_USER_NAME, name);
        }
    }

    /**
     * Zuletzt verwendete netBiDiB-Adresse/Port (siehe {@link BidibConnectionDialog}) -
     * damit die IP-Adresse nicht bei jedem Programmstart neu eingetippt werden
     * muss. {@code null}/leer, wenn noch nie verbunden wurde.
     */
    public String getBidibLastIp() {
        return prefs.get(KEY_BIDIB_LAST_IP, null);
    }

    public void setBidibLastIp(String ip) {
        if (ip == null || ip.isBlank()) {
            prefs.remove(KEY_BIDIB_LAST_IP);
        } else {
            prefs.put(KEY_BIDIB_LAST_IP, ip);
        }
    }

    /** Zuletzt verwendeter netBiDiB-Port, oder {@code null} für den Standard (62875). */
    public String getBidibLastPort() {
        return prefs.get(KEY_BIDIB_LAST_PORT, null);
    }

    public void setBidibLastPort(String port) {
        if (port == null || port.isBlank()) {
            prefs.remove(KEY_BIDIB_LAST_PORT);
        } else {
            prefs.put(KEY_BIDIB_LAST_PORT, port);
        }
    }

    /** Gemerkte eigene Hintergrundfarbe als Web-Farbwert ({@code "#rrggbb"}), oder null falls nie gespeichert. */
    public String getCustomBackgroundColor() {
        return prefs.get(KEY_CUSTOM_BACKGROUND_COLOR, null);
    }

    public void setCustomBackgroundColor(String webColor) {
        prefs.put(KEY_CUSTOM_BACKGROUND_COLOR, webColor);
    }

    /** Gemerkte eigene Textfarbe als Web-Farbwert ({@code "#rrggbb"}), oder null falls nie gespeichert. */
    public String getCustomTextColor() {
        return prefs.get(KEY_CUSTOM_TEXT_COLOR, null);
    }

    public void setCustomTextColor(String webColor) {
        prefs.put(KEY_CUSTOM_TEXT_COLOR, webColor);
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
     * Ordner fuer System-Dateien (seit 2.5): Dort legt das Systeme-Fenster
     * die ausgelesenen Interfaces samt Knoten ab ("Speichern" im Knotenbaum),
     * und von dort oeffnet "Interface Datei oeffnen" sie wieder. Oder null,
     * falls nicht gesetzt. Standardvorschlag {@code <Benutzerverzeichnis>/
     * iTrain/System-Dateien} - wie die uebrigen Pfade unterhalb des
     * iTrain-Ordners, siehe {@link FirstRunDialog} und Voreinstellungen → Pfade.
     */
    public String getSystemFilesDirectory() {
        return prefs.get(KEY_SYSTEM_FILES_DIRECTORY, null);
    }

    public void setSystemFilesDirectory(String path) {
        prefs.put(KEY_SYSTEM_FILES_DIRECTORY, path);
    }

    /** Zuletzt benutzte IP-Adresse einer ESU ECoS (siehe EcosConnectionDialog). */
    public String getEcosHost() {
        return prefs.get("ecosHost", "192.168.0.99");
    }

    public void setEcosHost(String host) {
        prefs.put("ecosHost", host);
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

    /**
     * Kennung des zuletzt in den Decoder-Ordner übertragenen Satzes
     * mitgelieferter Vorlagen (Sprachordner + Zielordner + Inhalts-Hash,
     * siehe {@link DecoderTemplateBundle}). Weicht die Kennung des
     * laufenden Programms davon ab - neue Version, andere Sprache, anderer
     * Ordner -, werden die Vorlagen erneut übertragen.
     */
    public String getBundledTemplatesFingerprint() {
        return prefs.get(KEY_BUNDLED_TEMPLATES_FINGERPRINT, null);
    }

    public void setBundledTemplatesFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            prefs.remove(KEY_BUNDLED_TEMPLATES_FINGERPRINT);
        } else {
            prefs.put(KEY_BUNDLED_TEMPLATES_FINGERPRINT, fingerprint);
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
