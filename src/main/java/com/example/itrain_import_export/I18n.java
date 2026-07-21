package com.example.itrain_import_export;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Lädt die übersetzten Bezeichner aus der externen Datei
 * {@code translations.properties} und stellt sie dem Rest der Anwendung
 * zur Verfügung.
 * <p>
 * Format je Zeile: {@code schluessel.sprachcode=Text}, z.B.
 * {@code category.blocks.de=Blöcke}. Die Datei kann von Hand angepasst
 * werden; gesucht wird zuerst im aktuellen Arbeitsverzeichnis (Projekt-
 * Wurzel bei "gradle run"), damit Änderungen ohne Neu-Build wirksam
 * werden. Existiert dort keine Datei, wird ersatzweise die im
 * Programm mitgelieferte Kopie aus den Ressourcen verwendet.
 */
public final class I18n {

    private static final String EXTERNAL_FILE_NAME = "translations.properties";

    /** Reihenfolge, in der die Sprache z.B. im Einstellungen-Dropdown erscheint. */
    public static final List<String> LANGUAGE_CODES = List.of("de", "en", "nl", "fr", "es", "it");

    private static final Map<String, String> NATIVE_NAMES = new LinkedHashMap<>();
    static {
        NATIVE_NAMES.put("de", "Deutsch");
        NATIVE_NAMES.put("en", "English");
        NATIVE_NAMES.put("nl", "Nederlands");
        NATIVE_NAMES.put("fr", "Français");
        NATIVE_NAMES.put("es", "Español");
        NATIVE_NAMES.put("it", "Italiano");
    }

    private static I18n instance;

    private final Map<String, Map<String, String>> table = new HashMap<>();
    private final List<Runnable> languageChangeListeners = new ArrayList<>();
    private String currentLanguage = "de";

    private I18n() {
        load();
        String systemLanguage = Locale.getDefault().getLanguage();
        if (LANGUAGE_CODES.contains(systemLanguage)) {
            currentLanguage = systemLanguage;
        }
    }

    public static synchronized I18n getInstance() {
        if (instance == null) {
            instance = new I18n();
        }
        return instance;
    }

    private void load() {
        File external = new File(EXTERNAL_FILE_NAME);
        try (InputStream in = external.isFile()
                ? new FileInputStream(external)
                : I18n.class.getResourceAsStream(EXTERNAL_FILE_NAME)) {
            if (in == null) {
                System.err.println("translations.properties wurde weder im Arbeitsverzeichnis "
                        + "noch als Ressource gefunden – es werden nur die Schlüssel angezeigt.");
                return;
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String propertyKey : props.stringPropertyNames()) {
                int lastDot = propertyKey.lastIndexOf('.');
                if (lastDot < 0) {
                    continue;
                }
                String baseKey = propertyKey.substring(0, lastDot);
                String lang = propertyKey.substring(lastDot + 1);
                if (!LANGUAGE_CODES.contains(lang)) {
                    continue;
                }
                table.computeIfAbsent(baseKey, k -> new HashMap<>()).put(lang, props.getProperty(propertyKey));
            }
        } catch (IOException ex) {
            System.err.println("Fehler beim Laden von translations.properties: " + ex.getMessage());
        }
    }

    /** Übersetzt {@code key} in die aktuelle Sprache, mit Platzhaltern {0},{1},... */
    public String t(String key, Object... args) {
        Map<String, String> variants = table.get(key);
        String template = null;
        if (variants != null) {
            template = variants.get(currentLanguage);
            if (template == null) {
                template = variants.get("en");
            }
            if (template == null) {
                template = variants.get("de");
            }
        }
        if (template == null) {
            return key;
        }
        return substitute(template, args);
    }

    /**
     * Einfache {0}/{1}/...-Ersetzung ohne die Anführungszeichen-Sonderregeln
     * von java.text.MessageFormat – mehrere unserer Übersetzungen (fr, it)
     * enthalten Apostrophe (z.B. "l'attribut"), die MessageFormat sonst als
     * Escape-Zeichen fehlinterpretieren würde.
     */
    private static String substitute(String template, Object... args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setLanguage(String code) {
        if (!LANGUAGE_CODES.contains(code) || code.equals(currentLanguage)) {
            return;
        }
        currentLanguage = code;
        for (Runnable listener : new ArrayList<>(languageChangeListeners)) {
            listener.run();
        }
    }

    /** Wird aufgerufen, sobald sich die Sprache ändert, damit die Oberfläche sich neu aufbauen kann. */
    public void addLanguageChangeListener(Runnable listener) {
        languageChangeListeners.add(listener);
    }

    /**
     * Meldet einen zuvor über {@link #addLanguageChangeListener} registrierten
     * Listener wieder ab - z.B. wenn ein einzelner, kurzlebiger Dialog (wie
     * {@link FirstRunDialog}) geschlossen wird und nicht dauerhaft auf
     * Sprachwechsel reagieren soll.
     */
    public void removeLanguageChangeListener(Runnable listener) {
        languageChangeListeners.remove(listener);
    }

    public String getNativeName(String code) {
        return NATIVE_NAMES.getOrDefault(code, code);
    }

    /** Klassenpfad-relativer Pfad zum kleinen Flaggen-Icon der Sprache. */
    public String getFlagResourcePath(String code) {
        return "flags/" + code + ".png";
    }
}
