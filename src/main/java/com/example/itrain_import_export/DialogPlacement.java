package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.Locale;

/**
 * Sorgt dafür, dass Dialoge und Meldungsfenster auf dem Bildschirm des
 * Hauptfensters erscheinen - und nicht dort, wo das Betriebssystem sie
 * gerade hinlegt.
 *
 * <h2>Das Problem</h2>
 * Ein JavaFX-Dialog zentriert sich über seinem Besitzerfenster, aber nur wenn
 * einer gesetzt ist. Ohne {@code initOwner(...)} landet er auf dem
 * Hauptbildschirm - bei zwei Monitoren also gern woanders als das Programm.
 * Im Bestand gibt es über 60 Dialoge an einem Dutzend Stellen, und ein Teil
 * davon setzt keinen Besitzer.
 *
 * <h2>Der Weg hierhin</h2>
 * Statt alle Aufrufstellen anzufassen (leicht einen zu übersehen, und jeder
 * neue Dialog müsste daran denken), hängt sich diese Klasse <b>einmal</b> an
 * die Liste aller offenen Fenster ({@link Window#getWindows()}). Jedes neu
 * erscheinende Fenster, dessen Wurzel ein {@link DialogPane} ist, wird
 * gerückt. Damit sind auch künftige Dialoge automatisch erfasst.
 * <p>
 * Eigene Fenster wie das Erfassungsfenster haben keinen {@code DialogPane}
 * als Wurzel und bleiben unangetastet - die merken sich ihre Lage selbst über
 * {@link WindowState}.
 *
 * <h2>Verschieben wird gemerkt</h2>
 * Rückt der Anwender einen Dialog beiseite, wird beim Schließen der
 * <b>Abstand zur Mitte des Hauptfensters</b> gespeichert, und der nächste
 * Dialog erscheint wieder dort. Bewusst ein Abstand und keine feste
 * Bildschirmposition: Dialoge sind unterschiedlich groß, und das Hauptfenster
 * kann inzwischen auf einem anderen Bildschirm stehen. Eine gemerkte absolute
 * Lage zeigte nach einem solchen Umzug ins Leere - der Abstand nie.
 * <p>
 * <b>Alle Fenster zentrieren</b> (Menü Bearbeiten) setzt diesen Abstand
 * zurück ({@link #resetOffset()}). Sonst käme der nächste Dialog sofort
 * wieder abseits heraus, obwohl der Anwender gerade um das Gegenteil gebeten
 * hat.
 */
public final class DialogPlacement {

    /**
     * Ab dieser Abweichung in Bildpunkten gilt ein Dialog als vom Anwender
     * verschoben. Ein kleiner Spielraum ist nötig, weil Fensterverwaltungen
     * die Lage um Bruchteile eines Punktes verändern können - ohne ihn würde
     * jeder geschlossene Dialog eine "Verschiebung" von 0,4 Punkten
     * hinterlassen.
     */
    private static final double MOVE_TOLERANCE = 3;

    /**
     * Schlüssel, unter dem an jedem betreuten Dialog hinterlegt wird, wohin
     * <em>wir</em> ihn gesetzt haben ({@code double[]{x, y}}). Beim Schließen
     * wird die tatsächliche Lage damit verglichen - weicht sie ab, war es der
     * Anwender.
     * <p>
     * Der Wert liegt bewusst am Fenster selbst und nicht in einem Feld dieser
     * Klasse: "Alle Fenster zentrieren" verschiebt offene Dialoge ebenfalls
     * und muss den Vergleichswert mitziehen können (siehe
     * {@link #notePlaced}). Ohne das würde ausgerechnet das Zentrieren als
     * Nutzer-Verschiebung gewertet und der eben zurückgesetzte Abstand sofort
     * wieder gesetzt.
     */
    private static final String BASELINE_KEY = "iTrain.dialogBaseline";

    /** Merker, dass an diesem Fenster schon ein Schließen-Handler hängt. */
    private static final String HANDLER_KEY = "iTrain.dialogHandlerAttached";

    /** Hauptfenster, an dem sich alle Dialoge ausrichten. */
    private static Stage mainStage;

    private DialogPlacement() {
    }

    /**
     * Einmal beim Programmstart aufzurufen, nachdem das Hauptfenster
     * existiert. Ab dann wird jeder neu geöffnete Dialog ausgerichtet.
     */
    public static void install(Stage main) {
        mainStage = main;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) {
                    continue;
                }
                for (Window added : change.getAddedSubList()) {
                    if (added instanceof Stage stage && isDialog(stage)) {
                        prepare(stage);
                    }
                }
            }
        });
    }

    /**
     * Vergisst eine gemerkte Verschiebung - Dialoge erscheinen danach wieder
     * mittig über dem Hauptfenster. Wird von "Alle Fenster zentrieren"
     * aufgerufen.
     */
    public static void resetOffset() {
        AppSettings.getInstance().setDialogOffset(null);
    }

    /**
     * Ist die Wurzel der Szene ein {@link DialogPane}, handelt es sich um
     * einen Dialog ({@code Alert}, {@code TextInputDialog}, {@code Dialog}).
     * Zusätzlich wird per Suche im Szenenbaum nachgefasst, falls eine
     * JavaFX-Fassung den DialogPane einbettet statt ihn als Wurzel zu setzen.
     */
    private static boolean isDialog(Stage stage) {
        Scene scene = stage.getScene();
        if (scene == null || scene.getRoot() == null) {
            return false;
        }
        return scene.getRoot() instanceof DialogPane
                || scene.getRoot().lookup(".dialog-pane") != null;
    }

    /**
     * Beim Hinzufügen zur Fensterliste steht die endgültige Größe des Dialogs
     * noch nicht fest - sie ergibt sich erst aus dem Textumbruch und den
     * Schaltflächen. Deshalb wird erst nach dem Anzeigen gerückt, und dort
     * noch einmal über {@link Platform#runLater} nachgefasst: JavaFX
     * zentriert einen Dialog mit Besitzer selbst, und zwar genau in dieser
     * Phase. Ohne das Nachfassen gewänne mal die eine, mal die andere Seite.
     */
    private static void prepare(Stage stage) {
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event ->
                Platform.runLater(() -> place(stage)));
    }

    private static void place(Stage stage) {
        if (mainStage == null || !mainStage.isShowing() || !stage.isShowing()) {
            return;
        }
        if (stage.getWidth() <= 0 || stage.getHeight() <= 0) {
            return;
        }

        double[] offset = readOffset();
        double mainCenterX = mainStage.getX() + mainStage.getWidth() / 2;
        double mainCenterY = mainStage.getY() + mainStage.getHeight() / 2;

        double x = mainCenterX + offset[0] - stage.getWidth() / 2;
        double y = mainCenterY + offset[1] - stage.getHeight() / 2;

        // Auf den Bildschirm begrenzen, auf dem das Hauptfenster steht: Eine
        // große gemerkte Verschiebung dürfte den Dialog sonst wieder aus dem
        // Sichtbaren schieben - genau das, was hier verhindert werden soll.
        Rectangle2D area = screenOfMain().getVisualBounds();
        x = clamp(x, area.getMinX(), area.getMaxX() - stage.getWidth());
        y = clamp(y, area.getMinY(), area.getMaxY() - stage.getHeight());

        stage.setX(x);
        stage.setY(y);
        stage.getProperties().put(BASELINE_KEY, new double[]{x, y});

        // Nur einmal anhängen: Ein wiederverwendeter Dialog wird mehrfach
        // angezeigt und liefe sonst durch immer mehr gleiche Handler.
        if (stage.getProperties().put(HANDLER_KEY, Boolean.TRUE) == null) {
            stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> rememberIfMoved(stage));
        }
    }

    /**
     * Meldet, dass ein Fenster von anderer Stelle bewusst verschoben wurde -
     * genutzt von {@link WindowState#centerAll(Stage)}. Für Fenster, die hier
     * nicht betreut werden, passiert nichts.
     */
    static void notePlaced(Window window, double x, double y) {
        if (window.getProperties().containsKey(BASELINE_KEY)) {
            window.getProperties().put(BASELINE_KEY, new double[]{x, y});
        }
    }

    /**
     * Beim Schließen prüfen, ob der Dialog woanders steht als dort, wo wir ihn
     * hingesetzt haben. Nur dann hat der Anwender ihn bewegt, und nur dann
     * wird gemerkt.
     */
    private static void rememberIfMoved(Stage stage) {
        if (mainStage == null || !mainStage.isShowing()) {
            return;
        }
        if (!(stage.getProperties().get(BASELINE_KEY) instanceof double[] baseline)) {
            return;
        }
        double dx = stage.getX() - baseline[0];
        double dy = stage.getY() - baseline[1];
        if (Math.abs(dx) < MOVE_TOLERANCE && Math.abs(dy) < MOVE_TOLERANCE) {
            return;
        }
        double mainCenterX = mainStage.getX() + mainStage.getWidth() / 2;
        double mainCenterY = mainStage.getY() + mainStage.getHeight() / 2;
        double offsetX = stage.getX() + stage.getWidth() / 2 - mainCenterX;
        double offsetY = stage.getY() + stage.getHeight() / 2 - mainCenterY;
        AppSettings.getInstance().setDialogOffset(
                String.format(Locale.ROOT, "%.0f;%.0f", offsetX, offsetY));
    }

    /** Gemerkte Verschiebung, oder {@code {0, 0}} für "mittig". */
    private static double[] readOffset() {
        String stored = AppSettings.getInstance().getDialogOffset();
        if (stored == null || stored.isBlank()) {
            return new double[]{0, 0};
        }
        String[] parts = stored.split(";");
        if (parts.length < 2) {
            return new double[]{0, 0};
        }
        try {
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (NumberFormatException ex) {
            // Beschädigter Eintrag - dann eben wieder mittig.
            return new double[]{0, 0};
        }
    }

    private static Screen screenOfMain() {
        double x = mainStage.getX() + mainStage.getWidth() / 2;
        double y = mainStage.getY() + mainStage.getHeight() / 2;
        for (Screen screen : Screen.getScreens()) {
            if (screen.getVisualBounds().contains(x, y)) {
                return screen;
            }
        }
        return Screen.getPrimary();
    }

    private static double clamp(double value, double min, double max) {
        // max < min, wenn der Dialog breiter ist als der Bildschirm - dann
        // gewinnt die linke bzw. obere Kante, sonst rutschte er nach außen.
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }
}
