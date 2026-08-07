package com.example.itrain_import_export;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Zeigt vor der ersten Decoder-Bearbeitung einen Hinweistext: was die Funktion
 * tut, welche Wege es gibt und worauf man achten muss (Digital-Adresse,
 * Protokoll-Grenzen).
 *
 * <h2>Woher der Text kommt</h2>
 * Nicht aus {@code translations.properties}, sondern aus einer eigenen,
 * bewusst von Hand bearbeitbaren Datei je Sprache - siehe {@link DecoderHints}.
 * Der Text ist mehrere Absätze lang und soll vom Anwender angepasst werden
 * können; in einer Properties-Datei stünde er als eine einzige Zeile voller
 * {@code \n}.
 *
 * <h2>Wann er erscheint</h2>
 * Vor jedem Öffnen einer Decoder-Bearbeitung, solange
 * {@link AppSettings#getShowDecoderHints()} an ist. Das Ankreuzfeld unten im
 * Dialog schaltet ihn ab; in den Voreinstellungen → Ansicht lässt er sich
 * wieder einschalten. Beide Stellen greifen auf dieselbe Einstellung zu, es
 * gibt also keinen zweiten Zustand, der aus dem Tritt geraten könnte.
 */
public final class DecoderHintsDialog {

    private DecoderHintsDialog() {
    }

    /**
     * Zeigt den Hinweis, falls er nicht abgeschaltet wurde.
     *
     * @return {@code true}, wenn weitergemacht werden soll. {@code false} nur,
     *         wenn der Anwender den Dialog abbricht - dann unterbleibt die
     *         Decoder-Bearbeitung. Ist der Hinweis abgeschaltet, kommt sofort
     *         {@code true} zurück, ohne dass etwas angezeigt wird.
     */
    public static boolean confirm(Stage owner) {
        AppSettings settings = AppSettings.getInstance();
        if (!settings.getShowDecoderHints()) {
            return true;
        }
        I18n i18n = I18n.getInstance();

        VBox content = new VBox(4);
        content.setPadding(new Insets(14));
        // Dieselbe einfache Auszeichnung wie in der Hilfe (**fett**, "- " für
        // Aufzählungen) - deshalb hier derselbe Aufbereiter statt eines
        // eigenen. Der TextFlow trägt darüber auch die Stilklasse, die das
        // dunkle Farbschema zum Einfärben braucht.
        content.getChildren().addAll(HelpDialog.renderBlocks(DecoderHints.text()));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(620, 460);

        CheckBox suppress = new CheckBox(i18n.t("decoderHints.suppress"));
        VBox.setMargin(suppress, new Insets(10, 0, 0, 0));

        VBox root = new VBox(scroll, suppress);
        root.setPadding(new Insets(0, 14, 4, 14));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("decoderHints.title"));
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // Farbschema und Fensterlage besorgen ThemeManager bzw. DialogPlacement
        // über die Fensterliste - hier ist dafür nichts zu tun.

        ButtonType answer = dialog.showAndWait().orElse(ButtonType.CANCEL);

        // Das Ankreuzfeld gilt auch dann, wenn der Anwender abbricht: Er hat
        // es bewusst gesetzt, und ihn beim nächsten Versuch erneut zu fragen
        // wäre genau das, was er gerade abbestellt hat.
        if (suppress.isSelected()) {
            settings.setShowDecoderHints(false);
        }
        return answer == ButtonType.OK;
    }
}
