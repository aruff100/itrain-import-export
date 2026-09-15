package com.example.itrain_import_export;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Window;

/**
 * Wendet das gewählte Farbschema (hell/dunkel) an. Hell ist einfach das
 * JavaFX-Standardaussehen (kein zusätzliches Stylesheet); dunkel lädt
 * {@code dark-theme.css} aus den Ressourcen oben drauf.
 *
 * <h2>Warum eine zentrale Anmeldung an der Fensterliste</h2>
 * Ein Stylesheet gilt immer nur für <b>eine</b> Scene. Jedes Fenster und jeder
 * Dialog bringt seine eigene mit, also müsste jede Aufrufstelle daran denken -
 * und im Bestand gibt es über 60 {@code new Alert(...)} an einem Dutzend
 * Stellen, die es nicht taten: im dunklen Schema erschienen sie weiß.
 * <p>
 * Statt alle anzufassen (leicht einen zu übersehen, und jeder neue Dialog
 * müsste erneut daran denken), hängt sich {@link #install()} <b>einmal</b> an
 * {@link Window#getWindows()}. Jedes neu erscheinende Fenster wird eingefärbt,
 * auch künftige. Dasselbe Muster wie bei {@link DialogPlacement} - dort für
 * die Lage, hier für die Farbe.
 *
 * <h2>Umschalten bei laufendem Programm</h2>
 * {@link #applyToAllWindows(String)} zieht alle bereits offenen Fenster nach.
 * Vorher wurden beim Umschalten in den Voreinstellungen nur das Hauptfenster
 * und der Einstellungsdialog selbst umgestellt - ein nebenher offenes
 * Erfassungsfenster ("Decoder-Konfiguration") blieb hell stehen.
 */
public final class ThemeManager {

    private static final String DARK_STYLESHEET = "dark-theme.css";

    /**
     * Merker am Fenster, dass dessen Scene bereits überwacht wird. Ein Dialog
     * kann mehrfach angezeigt werden und liefe sonst durch immer mehr gleiche
     * Zuhörer.
     */
    private static final String WATCHED_KEY = "iTrain.themeWatched";

    /**
     * Vorschau-Farben aus dem geoeffneten {@link CustomColorDialog}: Solange
     * gesetzt, zeigen ALLE Fenster diese Farben, ohne dass sie schon
     * gespeichert waeren. "Speichern" uebernimmt sie in die Einstellungen,
     * "Zurueck" verwirft sie - beides beendet die Vorschau ({@link #endPreview}).
     */
    private static String previewBackground;
    private static String previewText;

    private ThemeManager() {
    }

    /** Zeigt die zwei Farben sofort in allen offenen Fenstern, ohne sie zu speichern. */
    public static void preview(String backgroundWeb, String textWeb) {
        previewBackground = backgroundWeb;
        previewText = textWeb;
        applyToAllWindows(AppSettings.getInstance().getTheme());
    }

    /** Beendet die Vorschau; alle Fenster zeigen wieder die gespeicherten Farben. */
    public static void endPreview() {
        previewBackground = null;
        previewText = null;
        applyToAllWindows(AppSettings.getInstance().getTheme());
    }

    /**
     * Die gerade wirksame Hintergrund-/Textfarbe als Web-Wert - Vorschau vor
     * gespeicherter Farbkombination vor Hell/Dunkel-Rueckfall. Fuer Stellen,
     * die Farben programmatisch setzen muessen (Formen, Text-Knoten), weil
     * CSS dort nicht greift; siehe {@link #textColor()} und {@link #isDark()}.
     */
    private static String[] effectiveColors() {
        if (previewBackground != null && previewText != null) {
            return new String[]{previewBackground, previewText};
        }
        AppSettings settings = AppSettings.getInstance();
        if (settings.isCustomColorActive()
                && settings.getCustomBackgroundColor() != null && settings.getCustomTextColor() != null) {
            return new String[]{settings.getCustomBackgroundColor(), settings.getCustomTextColor()};
        }
        return AppSettings.THEME_DARK.equals(settings.getTheme())
                ? new String[]{"#2b2b2b", "#e0e0e0"}
                : new String[]{"#ececec", "#000000"};
    }

    /** Wirksame Textfarbe des aktuellen Farbschemas. */
    public static Color textColor() {
        return Color.web(effectiveColors()[1]);
    }

    /** Wirksame Hintergrundfarbe des aktuellen Farbschemas. */
    public static Color backgroundColor() {
        return Color.web(effectiveColors()[0]);
    }

    /**
     * Ob der wirksame Hintergrund dunkel ist - Grundlage fuer Stellen, die
     * zwischen einer hellen und einer dunklen Farbvariante waehlen
     * (Fuehrungsfarben im Daten-Explorer, Skizze im Hinweisfenster).
     */
    public static boolean isDark() {
        Color bg = backgroundColor();
        double luminance = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return luminance < 0.5;
    }

    /**
     * Einmal beim Programmstart aufzurufen. Ab dann bekommt jedes neu
     * geöffnete Fenster das aktuelle Farbschema, ohne dass die Aufrufstelle
     * etwas tun muss.
     */
    public static void install() {
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) {
                    continue;
                }
                for (Window added : change.getAddedSubList()) {
                    watch(added);
                }
            }
        });
    }

    /**
     * Färbt alle gerade offenen Fenster um. Wird beim Wechsel des Farbschemas
     * in den Voreinstellungen und in der Ersteinrichtung aufgerufen.
     */
    public static void applyToAllWindows(String theme) {
        // Über eine Kopie laufen: Das Anwenden eines Stylesheets kann ein
        // Neuzeichnen anstoßen, und die Fensterliste ist beobachtbar - eine
        // Änderung währenddessen würde die laufende Schleife stören.
        for (Window window : Window.getWindows().stream().toList()) {
            apply(window.getScene(), theme);
        }
    }

    public static void apply(Scene scene, String theme) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().clear();
        // Laufende Vorschau aus dem Farbkombination-Dialog geht vor allem anderen.
        if (previewBackground != null && previewText != null) {
            String previewCss = CustomColorTheme.buildStylesheet(previewBackground, previewText);
            if (previewCss != null) {
                scene.getStylesheets().add(previewCss);
                return;
            }
        }
        // Eine gespeicherte Farbkombination (siehe CustomColorDialog) hat
        // Vorrang vor Hell/Dunkel und gilt unabhaengig vom uebergebenen
        // theme-Wert. Hell/Dunkel bleibt nur noch die Rueckfallebene, solange
        // noch nie eine Farbkombination gespeichert wurde (bzw. die
        // Ersteinrichtung sie zurueckgesetzt hat).
        AppSettings settings = AppSettings.getInstance();
        if (settings.isCustomColorActive()) {
            String customCss = CustomColorTheme.buildStylesheet(
                    settings.getCustomBackgroundColor(), settings.getCustomTextColor());
            if (customCss != null) {
                scene.getStylesheets().add(customCss);
                return;
            }
        }
        if (AppSettings.THEME_DARK.equals(theme)) {
            String url = HelloApplication.class.getResource(DARK_STYLESHEET) != null
                    ? HelloApplication.class.getResource(DARK_STYLESHEET).toExternalForm()
                    : null;
            if (url != null) {
                scene.getStylesheets().add(url);
            }
        }
    }

    /**
     * Färbt das Fenster jetzt und noch einmal, sobald es (später) eine Scene
     * bekommt. Beides ist nötig: Ein {@code Stage} mit fertig gesetzter Scene
     * ist beim Eintrag in die Fensterliste schon vollständig, ein
     * {@code Dialog} hängt seine Scene dagegen erst danach ein - ohne das
     * Nachfassen bliebe er ungefärbt.
     */
    private static void watch(Window window) {
        String theme = AppSettings.getInstance().getTheme();
        apply(window.getScene(), theme);
        if (window.getProperties().put(WATCHED_KEY, Boolean.TRUE) == null) {
            window.sceneProperty().addListener((obs, oldScene, newScene) ->
                    apply(newScene, AppSettings.getInstance().getTheme()));
        }
    }
}
