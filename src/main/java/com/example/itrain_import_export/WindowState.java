package com.example.itrain_import_export;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Merkt sich Lage, Größe und Bildschirm eines Fensters und stellt sie beim
 * nächsten Öffnen wieder her. Jedes Fenster bekommt dafür einen eigenen
 * Schlüssel ({@code "main"}, {@code "decoderCapture"}, ...), gespeichert wird
 * über {@link AppSettings} (also in den Anwendungseinstellungen, nicht in
 * einer eigenen Datei).
 * <p>
 * <b>Warum das nicht trivial ist:</b> Eine gespeicherte Position kann beim
 * nächsten Start ins Leere zeigen - der zweite Bildschirm ist abgezogen, die
 * Auflösung wurde geändert, das Notebook läuft ohne Dock. Ein Fenster würde
 * dann außerhalb des sichtbaren Bereichs erscheinen und wäre praktisch nicht
 * mehr erreichbar. Deshalb wird jede wiederhergestellte Lage gegen die
 * <em>aktuell vorhandenen</em> Bildschirme geprüft
 * ({@link #isVisibleSomewhere}) und notfalls verworfen.
 * <p>
 * <b>Bildschirmwahl:</b> Ist unter Voreinstellungen ein fester Bildschirm
 * eingestellt, erscheinen Fenster ohne gemerkte Lage dort. Sonst gilt
 * {@link AppSettings#SCREEN_REMEMBER}: Ein Zusatzfenster öffnet auf dem
 * Bildschirm des Fensters, aus dem heraus es aufgerufen wurde - das ist das
 * Verhalten, das man erwartet, wenn man auf dem zweiten Bildschirm arbeitet.
 */
public final class WindowState {

    /** Kleinster sichtbarer Bereich, damit ein Fenster als erreichbar gilt. */
    private static final double MIN_VISIBLE = 80;

    /** Versatz beim Stapeln in {@link #centerAll(Stage)}, in Bildpunkten. */
    private static final double CASCADE_STEP = 32;

    private WindowState() {
    }

    /**
     * Stellt die gemerkte Lage her (oder setzt die angegebene Standardgröße)
     * und sorgt dafür, dass Änderungen beim Schließen gespeichert werden.
     *
     * @param stage         das Fenster
     * @param windowKey     Schlüssel, unter dem die Lage abgelegt wird
     * @param defaultWidth  Breite, falls nichts gemerkt ist
     * @param defaultHeight Höhe, falls nichts gemerkt ist
     */
    public static void apply(Stage stage, String windowKey, double defaultWidth, double defaultHeight) {
        AppSettings settings = AppSettings.getInstance();
        boolean restored = restore(stage, settings.getWindowState(windowKey));
        if (!restored) {
            placeOnPreferredScreen(stage, defaultWidth, defaultHeight);
        }
        // Beim Schließen sichern. Bewusst nicht bei jeder Bewegung: Das
        // schriebe während des Ziehens dutzendfach in die Einstellungen.
        stage.setOnHidden(joinHandler(stage.getOnHidden(), event -> save(stage, windowKey)));
    }

    /**
     * Holt alle offenen Fenster auf den Bildschirm des Hauptfensters zurück -
     * der Ausweg, wenn ein Fenster außerhalb des sichtbaren Bereichs steht
     * und mit der Maus nicht mehr zu erreichen ist (etwa nachdem ein
     * zweiter Bildschirm abgezogen wurde oder ein Fenster versehentlich an
     * den Rand geschoben wurde).
     * <p>
     * Die Zusatzfenster werden dabei bewusst leicht <b>versetzt</b>
     * gestapelt statt exakt übereinander: Lägen sie deckungsgleich, sähe man
     * nur das oberste und wüsste nicht, dass darunter weitere liegen.
     *
     * @return Anzahl der bewegten Fenster (das Hauptfenster mitgezählt)
     */
    public static int centerAll(Stage mainStage) {
        Rectangle2D bereich = screenOf(mainStage).getVisualBounds();
        center(mainStage, bereich, 0);
        mainStage.toFront();

        int bewegt = 1;
        int stufe = 1;
        // Kopie der Liste: toFront() kann die Reihenfolge der offenen
        // Fenster verändern, während wir darüber laufen.
        for (Window fenster : new ArrayList<>(Window.getWindows())) {
            // Nur echte Fenster und Dialoge (beides sind Stages). Alles andere
            // in dieser Liste sind PopupWindows - Tooltips, Kontextmenüs,
            // aufgeklappte Auswahllisten. Die verschwinden ohnehin von selbst
            // und dürfen keinesfalls in die Bildschirmmitte gerückt werden.
            if (!(fenster instanceof Stage stage) || fenster == mainStage || !fenster.isShowing()) {
                continue;
            }
            center(stage, bereich, stufe);
            stage.toFront();
            stufe++;
            bewegt++;
        }
        return bewegt;
    }

    /** Bildschirm, auf dem das Fenster gerade steht - sonst der Hauptbildschirm. */
    private static Screen screenOf(Stage stage) {
        Screen treffer = screenAt(stage.getX() + stage.getWidth() / 2,
                stage.getY() + stage.getHeight() / 2);
        return treffer != null ? treffer : Screen.getPrimary();
    }

    /**
     * Rückt ein Fenster in die Mitte des Bereichs, um {@code stufe} Schritte
     * nach rechts unten versetzt. Größe und Versatz werden so begrenzt, dass
     * das Fenster vollständig im sichtbaren Bereich bleibt - sonst wäre es
     * nach dem "Zentrieren" schlimmstenfalls wieder halb draußen.
     */
    private static void center(Stage fenster, Rectangle2D bereich, int stufe) {
        // Ein maximiertes Fenster füllt seinen Bildschirm bereits aus, ist also
        // sichtbar. Es zu entmaximieren, nur um es zu zentrieren, wäre eine
        // unerwartete Nebenwirkung - es bleibt, wie es ist.
        if (fenster.isMaximized()) {
            return;
        }
        double breite = Math.min(fenster.getWidth(), bereich.getWidth());
        double hoehe = Math.min(fenster.getHeight(), bereich.getHeight());
        double versatz = Math.min(stufe * CASCADE_STEP, Math.max(0, bereich.getWidth() - breite) / 2);
        double x = bereich.getMinX() + (bereich.getWidth() - breite) / 2 + versatz;
        double y = bereich.getMinY() + (bereich.getHeight() - hoehe) / 2 + versatz;
        x = Math.max(bereich.getMinX(), Math.min(x, bereich.getMaxX() - breite));
        y = Math.max(bereich.getMinY(), Math.min(y, bereich.getMaxY() - hoehe));
        fenster.setWidth(breite);
        fenster.setHeight(hoehe);
        fenster.setX(x);
        fenster.setY(y);
        // Handelt es sich um einen Dialog, dessen Lage DialogPlacement
        // betreut, muss dort der Vergleichswert mitwandern - sonst gälte
        // dieses Zentrieren beim Schließen als Verschiebung durch den
        // Anwender.
        DialogPlacement.notePlaced(fenster, x, y);
    }

    /**
     * Zerlegt den gemerkten Text und wendet ihn an. Gibt {@code false}
     * zurück, wenn nichts gemerkt war oder die Lage heute nicht mehr auf
     * einem vorhandenen Bildschirm liegt.
     */
    private static boolean restore(Stage stage, String stored) {
        if (stored == null || stored.isBlank()) {
            return false;
        }
        String[] parts = stored.split(";");
        if (parts.length < 4) {
            return false;
        }
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double w = Double.parseDouble(parts[2]);
            double h = Double.parseDouble(parts[3]);
            boolean maximized = parts.length > 4 && Boolean.parseBoolean(parts[4]);
            if (w < 200 || h < 150 || !isVisibleSomewhere(x, y, w, h)) {
                return false;
            }
            stage.setX(x);
            stage.setY(y);
            stage.setWidth(w);
            stage.setHeight(h);
            if (maximized) {
                stage.setMaximized(true);
            }
            return true;
        } catch (NumberFormatException ex) {
            // Beschädigter Eintrag (von Hand bearbeitet?) - dann eben neu anfangen.
            return false;
        }
    }

    private static void save(Stage stage, String windowKey) {
        // Im Vollbild liefern getX/getWidth die Vollbildmaße; die eigentliche
        // Fenstergröße wäre damit verloren. Deshalb wird bei maximiertem
        // Fenster nur der Zustand gemerkt und die zuletzt bekannte Größe
        // beibehalten.
        AppSettings settings = AppSettings.getInstance();
        if (stage.isMaximized()) {
            String previous = settings.getWindowState(windowKey);
            String[] parts = previous == null ? new String[0] : previous.split(";");
            if (parts.length >= 4) {
                settings.setWindowState(windowKey,
                        parts[0] + ";" + parts[1] + ";" + parts[2] + ";" + parts[3] + ";true");
                return;
            }
        }
        settings.setWindowState(windowKey, String.format(java.util.Locale.ROOT, "%.0f;%.0f;%.0f;%.0f;%s",
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), stage.isMaximized()));
    }

    /**
     * Liegt ein nennenswerter Teil des Rechtecks auf einem der vorhandenen
     * Bildschirme? Nur dann ist das Fenster für den Anwender erreichbar.
     */
    private static boolean isVisibleSomewhere(double x, double y, double w, double h) {
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getVisualBounds();
            double overlapX = Math.min(x + w, bounds.getMaxX()) - Math.max(x, bounds.getMinX());
            double overlapY = Math.min(y + h, bounds.getMaxY()) - Math.max(y, bounds.getMinY());
            if (overlapX >= MIN_VISIBLE && overlapY >= MIN_VISIBLE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Setzt Standardgröße und rückt das Fenster auf den passenden Bildschirm:
     * den fest eingestellten, sonst den des aufrufenden Fensters, sonst den
     * Hauptbildschirm. Die Größe wird dabei auf den verfügbaren Platz
     * begrenzt - sonst ragt ein für einen großen Monitor gedachtes Fenster
     * auf einem kleinen Notebook über den Rand.
     */
    private static void placeOnPreferredScreen(Stage stage, double defaultWidth, double defaultHeight) {
        Rectangle2D area = targetScreen(stage).getVisualBounds();
        double w = Math.min(defaultWidth, area.getWidth() - 40);
        double h = Math.min(defaultHeight, area.getHeight() - 40);
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setX(area.getMinX() + (area.getWidth() - w) / 2);
        stage.setY(area.getMinY() + (area.getHeight() - h) / 2);
    }

    /** Bildschirm, auf dem ein neues Fenster erscheinen soll. */
    private static Screen targetScreen(Stage stage) {
        List<Screen> screens = Screen.getScreens();
        String preferred = AppSettings.getInstance().getPreferredScreen();
        if (preferred != null && !AppSettings.SCREEN_REMEMBER.equals(preferred)) {
            try {
                int index = Integer.parseInt(preferred.trim());
                if (index >= 0 && index < screens.size()) {
                    return screens.get(index);
                }
            } catch (NumberFormatException ignored) {
                // Ungültige Einstellung (etwa nach dem Abziehen eines
                // Bildschirms) - dann wie "zuletzt verwendet" behandeln.
            }
        }
        Window owner = stage.getOwner();
        if (owner != null && owner.getWidth() > 0) {
            Screen ownerScreen = screenAt(owner.getX() + owner.getWidth() / 2,
                    owner.getY() + owner.getHeight() / 2);
            if (ownerScreen != null) {
                return ownerScreen;
            }
        }
        return Screen.getPrimary();
    }

    private static Screen screenAt(double x, double y) {
        for (Screen screen : Screen.getScreens()) {
            if (screen.getVisualBounds().contains(x, y)) {
                return screen;
            }
        }
        return null;
    }

    /**
     * Hängt einen weiteren Handler an einen möglicherweise schon
     * vorhandenen - so überschreibt das Merken der Fensterlage nicht das
     * Aufräumen, das ein Fenster beim Schließen sonst noch erledigt.
     */
    private static <T extends javafx.event.Event> javafx.event.EventHandler<T> joinHandler(
            javafx.event.EventHandler<T> existing, javafx.event.EventHandler<T> added) {
        if (existing == null) {
            return added;
        }
        return event -> {
            existing.handle(event);
            added.handle(event);
        };
    }
}
