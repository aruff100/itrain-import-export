package com.example.itrain_import_export;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Zeigt die eingebaute Hilfe: kurze Beschreibung des Programmzwecks, gefolgt
 * von Überschriften/Absätzen zu den einzelnen Menüpunkten sowie zur
 * Kategorie-Ansicht (Import/Export inkl. Referenzen, Anlegen/Löschen). Der
 * gesamte Text kommt aus {@link I18n} (alle 10 Sprachen), damit er automatisch
 * mit der Oberflächensprache wechselt.
 * <p>
 * Die Hilfetexte dürfen eine einfache Auszeichnung ("Stufe 1") enthalten, die
 * direkt in {@code translations.properties} geschrieben werden kann:
 * <ul>
 *   <li>{@code **fett**} - der eingeschlossene Teil wird fett dargestellt.</li>
 *   <li>Eine Zeile, die mit {@code "- "} beginnt, wird zu einem
 *       Aufzählungspunkt (mit • eingerückt).</li>
 *   <li>Eine Leerzeile (also {@code \n\n} in der Properties-Datei) beginnt
 *       einen neuen Absatz mit etwas Abstand; ein einzelnes {@code \n} ist
 *       ein einfacher Zeilenumbruch innerhalb desselben Absatzes.</li>
 * </ul>
 * Texte ohne jede Auszeichnung werden unverändert als einfacher Fließtext
 * dargestellt - die Erweiterung ist also rückwärtskompatibel.
 */
public final class HelpDialog {

    /** Einrückung der Aufzählungspunkte in Pixeln. */
    private static final double BULLET_INDENT = 12;

    /** Zusätzlicher Abstand über einem Folge-Absatz (durch Leerzeile getrennt). */
    private static final double PARAGRAPH_SPACING = 6;

    private HelpDialog() {
    }

    /** Hilfe des Hauptfensters - alles ausser den Decoder-Abschnitten, siehe {@link #showDecoder}. */
    public static void show(Stage owner) {
        I18n i18n = I18n.getInstance();

        VBox content = new VBox(4);
        content.setPadding(new Insets(15));

        addParagraph(content, null, i18n.t("help.introText"));
        // Direkt nach der Einleitung: Seit 2.5 gibt es drei Fenster, und
        // alle folgenden Abschnitte setzen voraus, dass man weiß, welches
        // gemeint ist.
        addParagraph(content, i18n.t("help.threeWindowsTitle"), i18n.t("help.threeWindowsText"));
        addParagraph(content, i18n.t("help.fileMenuTitle"), i18n.t("help.fileMenuText"));
        addParagraph(content, i18n.t("help.editMenuTitle"), i18n.t("help.editMenuText"));
        addParagraph(content, i18n.t("help.settingsMenuTitle"), i18n.t("help.settingsMenuText"));
        addParagraph(content, i18n.t("help.helpMenuTitle"), i18n.t("help.helpMenuText"));
        addParagraph(content, i18n.t("help.updateTitle"), i18n.t("help.updateText"));
        // Reihenfolge bewusst so: erst die Mehrfachauswahl (Grundlage für das
        // Exportieren), dann die Kategorie-Ansicht, die darauf aufbaut.
        addParagraph(content, i18n.t("help.selectionTitle"), i18n.t("help.selectionText"));
        addParagraph(content, i18n.t("help.categoryViewTitle"), i18n.t("help.categoryViewText"));
        addParagraph(content, i18n.t("help.explorerTitle"), i18n.t("help.explorerText"));
        // Die Decoder-Abschnitte stehen seit 2.5 in der Hilfe des
        // Decoder-Fensters (showDecoder), nicht mehr hier.
        // Fensterverwaltung am Ende der Bedienabschnitte: Sie betrifft alle
        // Fenster gleichermaßen und ist erst verständlich, wenn man weiß,
        // welche es überhaupt gibt.
        addParagraph(content, i18n.t("help.windowsTitle"), i18n.t("help.windowsText"));
        addParagraph(content, i18n.t("help.referenceRenameTitle"), i18n.t("help.referenceRenameText"));
        addParagraph(content, i18n.t("help.statusBarTitle"), i18n.t("help.statusBarText"));

        showDialog(owner, content);
    }

    /**
     * Hilfe des Decoder-Fensters: die drei Abschnitte rund um Decoder
     * (CV-Informationen/Vorlagen, Hinweisfenster, Decoder erfassen), die bis
     * 2.5 Teil der Hauptfenster-Hilfe waren.
     */
    public static void showDecoder(Stage owner) {
        I18n i18n = I18n.getInstance();

        VBox content = new VBox(4);
        content.setPadding(new Insets(15));
        addParagraph(content, i18n.t("help.decoderTitle"), i18n.t("help.decoderText"));
        addParagraph(content, i18n.t("help.decoderHintsTitle"), i18n.t("help.decoderHintsText"));
        addParagraph(content, i18n.t("help.captureTitle"), i18n.t("help.captureText"));
        showDialog(owner, content);
    }

    /** Hilfe des Systeme-Fensters. */
    public static void showSystems(Stage owner) {
        I18n i18n = I18n.getInstance();

        VBox content = new VBox(4);
        content.setPadding(new Insets(15));
        addParagraph(content, i18n.t("help.systemsTitle"), i18n.t("help.systemsText"));
        addParagraph(content, i18n.t("help.bidibTitle"), i18n.t("help.bidibText"));
        showDialog(owner, content);
    }

    private static void showDialog(Stage owner, VBox content) {
        I18n i18n = I18n.getInstance();
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(560, 480);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("menu.helpItem"));
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        // Die Scene existiert erst, sobald der Dialog tatsächlich angezeigt
        // wird - deshalb das Farbschema erst dann anwenden (siehe
        // SettingsDialog.applyThemeOnceShown für dasselbe Muster).
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));
        dialog.showAndWait();
    }

    /** Fügt einen Abschnitt an - mit fett gedruckter Überschrift (falls angegeben) und formatiertem Text. */
    private static void addParagraph(VBox container, String title, String text) {
        if (title != null) {
            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 10 0 2 0;");
            container.getChildren().add(titleLabel);
        }
        container.getChildren().addAll(renderBlocks(text));
    }

    /**
     * Zerlegt einen Hilfetext in darstellbare Blöcke (Absätze und
     * Aufzählungspunkte). Siehe Klassenkommentar für die unterstützte
     * Auszeichnung.
     */
    static List<Node> renderBlocks(String text) {
        List<Node> blocks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return blocks;
        }
        // \r\n / \r auf \n vereinheitlichen, damit die Zeilenlogik unabhängig
        // vom Editor ist, mit dem die Properties-Datei bearbeitet wurde.
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        List<String> current = new ArrayList<>();
        // true, sobald mindestens ein Block erzeugt wurde UND der nächste
        // Absatz durch eine Leerzeile abgetrennt ist (dann etwas mehr Abstand).
        boolean spacedParagraph = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                // Leerzeile: laufenden Absatz abschließen, nächster bekommt Abstand.
                if (!current.isEmpty()) {
                    blocks.add(paragraph(current, spacedParagraph));
                    current.clear();
                }
                spacedParagraph = !blocks.isEmpty();
                continue;
            }
            if (line.startsWith("- ")) {
                // Aufzählungspunkt: beendet einen ggf. laufenden Absatz.
                if (!current.isEmpty()) {
                    blocks.add(paragraph(current, spacedParagraph));
                    current.clear();
                    spacedParagraph = false;
                }
                blocks.add(bullet(line.substring(2).trim()));
                continue;
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            blocks.add(paragraph(current, spacedParagraph));
        }
        return blocks;
    }

    /** Baut einen Fließtext-Absatz aus (durch einfache Zeilenumbrüche getrennten) Zeilen. */
    private static TextFlow paragraph(List<String> lines, boolean extraSpaceAbove) {
        TextFlow flow = newTextFlow();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                flow.getChildren().add(new Text("\n"));
            }
            flow.getChildren().addAll(parseInline(lines.get(i)));
        }
        if (extraSpaceAbove) {
            flow.setPadding(new Insets(PARAGRAPH_SPACING, 0, 0, 0));
        }
        return flow;
    }

    /** Baut eine Aufzählungszeile: eingerücktes Bullet-Zeichen plus umbrechender Text. */
    private static HBox bullet(String content) {
        Label dot = new Label("•");
        TextFlow flow = newTextFlow();
        flow.getChildren().addAll(parseInline(content));
        // Ohne Hgrow bekäme der TextFlow nur seine bevorzugte Breite und
        // würde nicht am Fensterrand umbrechen.
        HBox.setHgrow(flow, Priority.ALWAYS);
        HBox row = new HBox(6, dot, flow);
        row.setPadding(new Insets(0, 0, 0, BULLET_INDENT));
        return row;
    }

    /**
     * Zerlegt eine Zeile in Text-Bausteine und wertet dabei {@code **fett**}
     * aus. Unvollständige Auszeichnung (öffnendes {@code **} ohne schließendes)
     * bleibt bewusst als normaler Text stehen, statt Text zu verschlucken.
     */
    private static List<Text> parseInline(String line) {
        List<Text> runs = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            int start = line.indexOf("**", index);
            if (start < 0) {
                addRun(runs, line.substring(index), false);
                break;
            }
            int end = line.indexOf("**", start + 2);
            if (end < 0) {
                addRun(runs, line.substring(index), false);
                break;
            }
            addRun(runs, line.substring(index, start), false);
            String bold = line.substring(start + 2, end);
            if (bold.isEmpty()) {
                // "****" ergibt keinen fetten Text - unverändert stehen lassen.
                addRun(runs, "****", false);
            } else {
                addRun(runs, bold, true);
            }
            index = end + 2;
        }
        if (runs.isEmpty()) {
            runs.add(new Text(""));
        }
        return runs;
    }

    /**
     * Erzeugt einen TextFlow mit eigener Stilklasse.
     * <p>
     * Die Schriftfarbe kommt <b>nicht</b> von dieser Stilklasse, sondern wird
     * je Textbaustein direkt gesetzt (siehe {@link #textFill()}) - der Versuch
     * über CSS hat nicht funktioniert. Die Klasse bleibt trotzdem: Sie ist der
     * Ansatzpunkt, falls am Hilfetext einmal etwas anderes gestaltet werden
     * soll (Abstände, Schriftgröße), und dokumentiert im Stylesheet, wo dieser
     * Text herkommt.
     */
    private static TextFlow newTextFlow() {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("help-text");
        return flow;
    }

    private static void addRun(List<Text> runs, String content, boolean bold) {
        if (content.isEmpty()) {
            return;
        }
        Text run = new Text(content);
        if (bold) {
            run.setStyle("-fx-font-weight: bold;");
        }
        // Schriftfarbe ausdrücklich setzen, nicht über CSS. Siehe textFill().
        run.setFill(textFill());
        runs.add(run);
    }

    /**
     * Schriftfarbe für die Textbausteine, passend zum eingestellten Farbschema.
     *
     * <h2>Warum hier und nicht im Stylesheet</h2>
     * {@link Text} ist eine <b>Form</b>, keine Beschriftung: Die Farbe heißt
     * dort {@code -fx-fill}, nicht {@code -fx-text-fill}, und ohne Angabe ist
     * sie <b>schwarz</b> - im dunklen Schema also unlesbar. Eine Regel
     * {@code .help-text .text} in {@code dark-theme.css} sollte das erledigen,
     * hat es in der Praxis aber nicht getan: Überschriften (echte
     * {@link Label}) erschienen weiß, der Fließtext daneben blieb schwarz.
     * <p>
     * Statt weiter zu raten, warum der Selektor nicht greift, wird die Farbe
     * jetzt direkt gesetzt. Das ist ohnehin die verlässlichere Variante -
     * ein programmatisch gesetzter Wert hat Vorrang vor jedem Stylesheet und
     * kann nicht von einer späteren CSS-Änderung versehentlich ausgehebelt
     * werden. Dieselbe Entscheidung wie bei der Skizze im Hinweis-Fenster
     * (siehe {@code DecoderCaptureWindow.buildIllustration}).
     * <p>
     * Die Farbe wird beim Aufbau des Textes festgelegt. Das genügt, weil alle
     * betroffenen Fenster (Hilfe, Hinweise, Decoder-Hinweise) modal sind und
     * jedes Mal neu aufgebaut werden - das Farbschema kann sich nicht ändern,
     * während eines davon offen steht.
     */
    private static Color textFill() {
        // Wirksame Textfarbe der Farbkombination (bzw. Hell/Dunkel als
        // Rueckfall) - nicht mehr an THEME_DARK festgemacht, sonst blieb der
        // Hilfetext nach Wahl einer hellen Kombination hell auf hell.
        return ThemeManager.textColor();
    }
}
