package com.example.itrain_import_export;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Sucht eine Tams mc2 im lokalen Netz über ihren FTP-Zugang - eine
 * ZUSÄTZLICHE Erkennung neben dem netBiDiB-mDNS von {@link BidibDiscovery}:
 * die mc2 meldet sich derzeit (Stand 16.09.2026, Andre: "wird das mDNS
 * Protokoll demnächst wieder unterstützen") nicht zuverlässig per mDNS,
 * bleibt über FTP aber trotzdem auffindbar. Genau wie {@link EcosDiscovery}
 * für die ECoS ist das KEIN echtes Discovery-Protokoll, sondern eine
 * zweistufige Abtastung des lokalen /24-Netzes (siehe {@link
 * LocalSubnetUtil}):
 * <ol>
 * <li>Schnelle, parallele TCP-Verbindungsprobe auf FTP-Port
 * {@value Mc2FtpClient#PORT} mit kurzer Zeitgrenze - verwirft die meisten
 * Adressen sofort (kein FTP-Dienst dort).</li>
 * <li>Nur für Adressen, an denen das gelang: die bereits vorhandene,
 * vollständige Prüfung {@link Mc2LocoReader#isAvailable} (kompletter
 * FTP-Dialog samt USER/PASS/CWD/PASV/RETR auf {@code config/loco.ini}) -
 * erst die bestätigt echt eine mc2, nicht nur "irgendein FTP-Server auf
 * Port 21".</li>
 * </ol>
 * Ein Fund fließt NICHT automatisch in eine bestehende netBiDiB-Verbindung -
 * er füllt im Verbindungsdialog nur das Feld "Manuell" mit der gefundenen
 * Adresse; das eigentliche Verbinden geschieht wie gewohnt über netBiDiB
 * (Standard-Port {@code 62875}), die FTP-Prüfung dient ausschließlich dem
 * Auffinden der Adresse.
 */
public final class Mc2Discovery {

    private static final int PROBE_CONNECT_TIMEOUT_MS = 250;
    private static final int MAX_THREADS = 64;

    private final Consumer<Mc2DiscoveredDevice> onFound;
    private final Runnable onFinished;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private ExecutorService pool;

    /**
     * {@code onFound} wird für jeden Fund aufgerufen, {@code onFinished},
     * sobald alle Adressen geprüft sind (auch wenn nichts gefunden wurde) -
     * beide NICHT im JavaFX-Anwendungsfaden, der Aufrufer muss selbst auf
     * {@code Platform.runLater} wechseln.
     */
    public Mc2Discovery(Consumer<Mc2DiscoveredDevice> onFound, Runnable onFinished) {
        this.onFound = onFound;
        this.onFinished = onFinished;
    }

    /**
     * Startet die Abtastung im Hintergrund. Ein zweiter Aufruf ohne
     * vorheriges {@link #stop()} legt einen weiteren, parallelen Lauf an -
     * der Aufrufer (siehe {@link BidibConnectionDialog}) ruft deshalb vor
     * "Erneut suchen" immer erst {@link #stop()}.
     */
    public void start() {
        stopped.set(false);
        List<InetAddress> targets = LocalSubnetUtil.subnetAddresses();
        if (targets.isEmpty()) {
            onFinished.run();
            return;
        }
        pool = Executors.newFixedThreadPool(Math.min(MAX_THREADS, targets.size()));
        AtomicInteger remaining = new AtomicInteger(targets.size());
        for (InetAddress target : targets) {
            pool.submit(() -> {
                try {
                    if (!stopped.get() && hasOpenFtpPort(target) && !stopped.get()
                            && Mc2LocoReader.isAvailable(target.getHostAddress()) && !stopped.get()) {
                        onFound.accept(new Mc2DiscoveredDevice(target));
                    }
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        onFinished.run();
                    }
                }
            });
        }
    }

    /** Beendet einen laufenden Lauf sofort - noch nicht begonnene Prüfungen werden verworfen. */
    public void stop() {
        stopped.set(true);
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    /**
     * Stufe 1 - nur eine kurze TCP-Verbindungsprobe auf Port
     * {@value Mc2FtpClient#PORT}, sofort wieder geschlossen. Verwirft die
     * meisten Adressen des Subnetzes ohne die vergleichsweise teure
     * Stufe 2 ({@link Mc2LocoReader#isAvailable}, voller FTP-Dialog mit bis
     * zu 6 s Zeitgrenze je Schritt).
     */
    private static boolean hasOpenFtpPort(InetAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, Mc2FtpClient.PORT), PROBE_CONNECT_TIMEOUT_MS);
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }
}
