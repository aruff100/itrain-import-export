package com.example.itrain_import_export;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.InputStream;

/**
 * Fenster, das während des netBiDiB-Pairings erscheint - anstelle einer
 * bloßen Statuszeile, die beim Hantieren am Gerät leicht übersehen wird.
 * <p>
 * Ablauf: Das Programm schickt seine Pairing-Anfrage und zeigt diesen Dialog
 * mit einem Zähler, der die vom Protokoll vorgegebenen Sekunden
 * herunterzählt (siehe {@code requestedPairingTimeout} in
 * {@code BidibConnectionDialog}). Innerhalb dieser Zeit muss das Pairing
 * zusätzlich AM GERÄT SELBST bestätigt werden - je nach Modell per
 * Tastendruck oder in dessen eigener Bedienoberfläche.
 * <ul>
 * <li>Klappt es, ruft der Verbindungsaufbau {@link #succeeded()} auf und der
 * Dialog verschwindet von selbst.</li>
 * <li>Klappt es nicht (Zeit abgelaufen oder Gegenseite meldet "ungepaart"),
 * bleibt der Dialog STEHEN, bis der Nutzer sich entscheidet - denn genau dann
 * braucht er die Information, was schiefgelaufen ist. Dafür
 * {@link #failed(String)}.</li>
 * </ul>
 * Zwei Knöpfe: "Abbruch" schließt und trennt, "Erneut versuchen" startet das
 * Pairing auf derselben Adresse noch einmal.
 */
public final class BidibPairingDialog {

    private final Stage stage;
    private final Label countdownLabel;
    private final Label extraHintLabel;
    private final Button cancelButton;
    private final Button retryButton;

    /**
     * Zusatzhinweis unter dem Haupttext einblenden - z.B. wenn das Geraet
     * trotz gespeichertem Pairing erneut fragt (siehe BidibConnectionDialog).
     */
    public void setExtraHint(String text) {
        extraHintLabel.setText(text);
        extraHintLabel.setVisible(text != null && !text.isBlank());
        extraHintLabel.setManaged(extraHintLabel.isVisible());
        stage.sizeToScene();
    }

    private Timeline countdown;
    private int remainingSeconds;

    /** Wird gesetzt, sobald das Pairing erfolgreich war - unterdrückt das Trennen beim Schließen. */
    private boolean finished;

    private final Runnable onCancel;
    private final Runnable onRetry;

    /**
     * @param owner    Elternfenster
     * @param hostPort Adresse, mit der gerade gepairt wird (nur zur Anzeige)
     * @param seconds  Startwert des Zählers
     * @param onCancel wird bei "Abbruch" und beim Schließen über das Fensterkreuz
     *                 aufgerufen (Verbindung abbauen)
     * @param onRetry  wird bei "Erneut versuchen" aufgerufen (Pairing auf
     *                 derselben Adresse neu anstoßen)
     */
    public BidibPairingDialog(Window owner, String hostPort, int seconds, Runnable onCancel, Runnable onRetry) {
        this.onCancel = onCancel;
        this.onRetry = onRetry;
        this.remainingSeconds = seconds;

        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Label hintLabel = new Label(i18n.t("bidib.pairingDialogHint", hostPort));
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(420);

        // Zusatzhinweis (siehe setExtraHint): erscheint nur, wenn das Geraet
        // trotz gespeichertem Pairing erneut fragt - dann steht hier, woran
        // das meist liegt (volle Partnerliste im Geraet).
        extraHintLabel = new Label();
        extraHintLabel.setWrapText(true);
        extraHintLabel.setMaxWidth(420);
        extraHintLabel.setStyle("-fx-font-style: italic;");
        extraHintLabel.setVisible(false);
        extraHintLabel.setManaged(false);

        countdownLabel = new Label(i18n.t("bidib.pairingCountdown", seconds));
        countdownLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // Das Fenster muss VOR den Knopf-Aktionen entstehen: Die Lambdas
        // greifen darauf zu, und Java verlangt, dass ein finales Feld dann
        // bereits belegt ist.
        stage = new Stage();

        cancelButton = new Button(i18n.t("bidib.pairingCancelButton"));
        retryButton = new Button(i18n.t("bidib.pairingRetryButton"));
        // Solange der Zähler läuft, ist ein zweiter Versuch sinnlos - die
        // Gegenseite wartet ja noch auf die Bestätigung am Gerät. Erst nach
        // einem Fehlschlag freigeben (siehe failed(...)).
        retryButton.setDisable(true);

        cancelButton.setOnAction(e -> {
            stopCountdown();
            stage.close();
        });

        retryButton.setOnAction(e -> {
            // Als "erledigt" markieren, damit das Schließen NICHT zusätzlich
            // onCancel auslöst - der neue Versuch baut die Verbindung selbst
            // wieder auf.
            finished = true;
            stopCountdown();
            stage.close();
            if (onRetry != null) {
                onRetry.run();
            }
        });

        HBox buttonRow = new HBox(8, cancelButton, retryButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12, hintLabel, extraHintLabel, countdownLabel, buttonRow);
        content.setPadding(new Insets(16));

        BorderPane root = new BorderPane();
        root.setCenter(content);

        stage.initOwner(owner);
        // Modal: Solange das Pairing läuft, gibt es im Verbindungsdialog
        // ohnehin nichts Sinnvolles zu tun, und ein versehentlicher zweiter
        // Verbindungsversuch währenddessen würde die Gegenseite verwirren.
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(i18n.t("bidib.pairingDialogTitle"));
        stage.getIcons().addAll(loadAppIcons());
        stage.setResizable(false);
        stage.setOnHidden(e -> {
            stopCountdown();
            if (!finished && onCancel != null) {
                onCancel.run();
            }
        });

        Scene scene = new Scene(root);
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
    }

    /** Zeigt den Dialog und startet den Zähler. Nur im JavaFX-Thread aufrufen. */
    public void show() {
        startCountdown();
        stage.show();
    }

    private void startCountdown() {
        I18n i18n = I18n.getInstance();
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            if (remainingSeconds > 0) {
                countdownLabel.setText(i18n.t("bidib.pairingCountdown", remainingSeconds));
            } else {
                stopCountdown();
                failed(i18n.t("bidib.pairingTimedOut"));
            }
        }));
        countdown.setCycleCount(Animation.INDEFINITE);
        countdown.play();
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }

    /**
     * Pairing erfolgreich - Dialog schließt sich von selbst. Darf aus einem
     * beliebigen Thread aufgerufen werden.
     */
    public void succeeded() {
        Platform.runLater(() -> {
            finished = true;
            stopCountdown();
            stage.close();
        });
    }

    /**
     * Pairing nicht zustande gekommen. Der Dialog BLEIBT offen und zeigt den
     * Grund; erst ein Knopfdruck des Nutzers beendet ihn. Darf aus einem
     * beliebigen Thread aufgerufen werden.
     */
    public void failed(String reason) {
        Platform.runLater(() -> {
            stopCountdown();
            countdownLabel.setText(reason);
            countdownLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            retryButton.setDisable(false);
        });
    }

    /** {@code true}, solange der Dialog noch sichtbar ist. */
    public boolean isShowing() {
        return stage.isShowing();
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = BidibPairingDialog.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (Exception ex) {
            return new Image[0];
        }
    }
}
