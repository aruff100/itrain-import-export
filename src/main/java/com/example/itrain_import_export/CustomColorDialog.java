package com.example.itrain_import_export;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "Farbkombination": Der Anwender wählt Hintergrund- und Textfarbe über zwei
 * Farbwähler. Jede Änderung - auch die Vorbelegung über "Hell"/"Dunkel" -
 * wirkt sofort auf die GANZE Anwendung (Vorschau, siehe
 * {@link ThemeManager#preview}), zusätzlich auf das Testfeld. "Speichern"
 * übernimmt die Farben dauerhaft, "Zurück" (und das Schließen über das
 * Fenster-X) stellt die Farben von vor dem Öffnen wieder her.
 * <p>
 * Bewusst ein eigenes {@link Stage} und kein {@code Dialog}: Ein Dialog ohne
 * ButtonTypes im DialogPane lässt sich weder über {@code close()} noch über
 * das X schließen - JavaFX verweigert das, solange kein Ergebnis vorliegt.
 * Da er zugleich modal war, reagierte danach kein Fenster mehr.
 */
final class CustomColorDialog {

    /** Testtext im Vorschaufeld - bewusst nicht übersetzt (Programmname). */
    private static final String TEST_TEXT = "iTrain Import/Export";

    // Vorbelegungen der Knöpfe "Hell" und "Dunkel".
    static final String LIGHT_BACKGROUND = "#e6e6e6";
    static final String LIGHT_TEXT = "#1a1a1a";
    static final String DARK_BACKGROUND = "#4d4d4d";
    static final String DARK_TEXT = "#f2f2f2";

    private CustomColorDialog() {
    }

    static void show(Stage owner) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        // Namen der Vorbelegungsfarben, angezeigt neben dem jeweiligen
        // Farbwähler. Der Farbwähler selbst kennt nur die Standard-Web-
        // Farbnamen und zeigt für alles andere "Benutzerdefinierte Farbe" -
        // deshalb sein eigener Text ausgeblendet und hier ein eigener.
        Map<String, String> colorNames = new LinkedHashMap<>();
        colorNames.put(LIGHT_BACKGROUND, i18n.t("settings.themeLight"));
        colorNames.put(LIGHT_TEXT, i18n.t("settings.themeDark"));
        colorNames.put(DARK_BACKGROUND, i18n.t("settings.themeDark"));
        colorNames.put(DARK_TEXT, i18n.t("settings.themeLight"));

        ColorPicker backgroundPicker = new ColorPicker();
        ColorPicker textPicker = new ColorPicker();
        for (ColorPicker picker : new ColorPicker[]{backgroundPicker, textPicker}) {
            picker.setStyle("-fx-color-label-visible: false;");
            for (String web : colorNames.keySet()) {
                picker.getCustomColors().add(Color.web(web));
            }
        }
        Label backgroundName = new Label();
        Label textName = new Label();

        String startBackground;
        String startText;
        if (settings.isCustomColorActive()
                && settings.getCustomBackgroundColor() != null && settings.getCustomTextColor() != null) {
            startBackground = settings.getCustomBackgroundColor();
            startText = settings.getCustomTextColor();
        } else if (AppSettings.THEME_DARK.equals(settings.getTheme())) {
            startBackground = DARK_BACKGROUND;
            startText = DARK_TEXT;
        } else {
            startBackground = LIGHT_BACKGROUND;
            startText = LIGHT_TEXT;
        }
        backgroundPicker.setValue(Color.web(startBackground));
        textPicker.setValue(Color.web(startText));

        HBox backgroundRow = new HBox(8, backgroundPicker, backgroundName);
        backgroundRow.setAlignment(Pos.CENTER_LEFT);
        HBox textRow = new HBox(8, textPicker, textName);
        textRow.setAlignment(Pos.CENTER_LEFT);
        VBox backgroundColumn = new VBox(6, new Label(i18n.t("customColor.background")), backgroundRow);
        VBox textColumn = new VBox(6, new Label(i18n.t("customColor.textColor")), textRow);
        HBox pickerRow = new HBox(30, backgroundColumn, textColumn);

        // Testfeld, durch einen vertieften Rahmen (hell unten/rechts, dunkel
        // oben/links, plus Innenschatten) räumlich vom Fenstergrund abgesetzt.
        Label previewLabel = new Label(TEST_TEXT);
        previewLabel.setStyle("-fx-font-size: 14px;");
        StackPane preview = new StackPane(previewLabel);
        preview.setPrefSize(400, 80);
        preview.setMinHeight(80);

        Runnable applyPickers = () -> {
            String bg = toWeb(backgroundPicker.getValue());
            String txt = toWeb(textPicker.getValue());
            preview.setStyle("-fx-background-color: " + bg + ";"
                    + " -fx-border-color: #5a5a5a #ffffff #ffffff #5a5a5a;"
                    + " -fx-border-width: 2;"
                    + " -fx-effect: innershadow(gaussian, rgba(0,0,0,0.45), 8, 0, 2, 2);");
            previewLabel.setTextFill(textPicker.getValue());
            backgroundName.setText(colorNames.getOrDefault(bg, bg));
            textName.setText(colorNames.getOrDefault(txt, txt));
            // Sofort im ganzen Programm zeigen - noch nicht gespeichert.
            ThemeManager.preview(bg, txt);
        };
        backgroundPicker.valueProperty().addListener((obs, oldV, newV) -> applyPickers.run());
        textPicker.valueProperty().addListener((obs, oldV, newV) -> applyPickers.run());

        Button lightButton = new Button(i18n.t("settings.themeLight"));
        lightButton.setOnAction(e -> {
            backgroundPicker.setValue(Color.web(LIGHT_BACKGROUND));
            textPicker.setValue(Color.web(LIGHT_TEXT));
        });
        Button darkButton = new Button(i18n.t("settings.themeDark"));
        darkButton.setOnAction(e -> {
            backgroundPicker.setValue(Color.web(DARK_BACKGROUND));
            textPicker.setValue(Color.web(DARK_TEXT));
        });

        Stage stage = new Stage();
        stage.initOwner(owner);
        // Anwendungsmodal, weil der Voreinstellungen-Dialog, aus dem dieses
        // Fenster kommt, selbst anwendungsmodal ist - ein nicht-modales
        // Fenster bekäme daneben keine Eingaben.
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(i18n.t("customColor.title"));
        stage.getIcons().addAll(loadAppIcons());
        stage.setResizable(false);

        Button saveButton = new Button(i18n.t("customColor.save"));
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            settings.setCustomBackgroundColor(toWeb(backgroundPicker.getValue()));
            settings.setCustomTextColor(toWeb(textPicker.getValue()));
            settings.setCustomColorActive(true);
            ThemeManager.endPreview();
            stage.close();
        });
        Button backButton = new Button(i18n.t("customColor.cancel"));
        backButton.setCancelButton(true);
        backButton.setOnAction(e -> {
            // Vorschau verwerfen: alle Fenster zeigen wieder die gespeicherten
            // Farben - also die von vor dem Öffnen dieses Fensters.
            ThemeManager.endPreview();
            stage.close();
        });
        // Fenster-X wirkt wie "Zurück".
        stage.setOnCloseRequest(e -> ThemeManager.endPreview());

        HBox buttonRow = new HBox(10, lightButton, darkButton, saveButton, backButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(15, pickerRow, preview, buttonRow);
        content.setPadding(new Insets(15));

        Scene scene = new Scene(content);
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        // Über dem Besitzerfenster zentrieren - ein Stage tut das, anders als
        // ein Dialog, nicht von selbst.
        stage.setOnShown(e -> {
            stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
        });
        // Testfeld und Namen erstmalig befüllen; ThemeManager.preview darin
        // ist beim Start unschädlich (gleiche Farben wie gespeichert bzw. Standard).
        applyPickers.run();
        stage.showAndWait();
    }

    private static String toWeb(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = CustomColorDialog.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (IOException ex) {
            return new Image[0];
        }
    }
}
