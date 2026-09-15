package com.example.itrain_import_export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sucht ESU-ECoS-Geräte im lokalen Netz - AUSDRÜCKLICH NICHT über ein echtes
 * Discovery-Protokoll: die ECoS kennt weder mDNS/Bonjour noch einen
 * dokumentierten Broadcast-Announce (recherchiert am 16.09.2026 anhand der
 * ESU-eigenen Anleitung sowie JMRI, Rocrail, ModelRailPro und
 * ModelTrainScript - alle vier verlangen dort ausnahmslos die IP-Adresse von
 * Hand; keine Quelle nennt einen UDP-Broadcast oder ein Zeroconf-Verfahren
 * für die ECoS). Stattdessen wird das lokale /24-Netz abgetastet: jede
 * Adresse wird parallel kurz auf TCP-Port {@value EcosClient#PORT}
 * angesprochen; antwortet dort etwas, wird zusätzlich {@code get(1, info)}
 * gesendet (dasselbe Kommando wie beim echten Verbindungsaufbau, siehe
 * {@link EcosConnection#open}) - nur eine gültige ECoS-Antwort (Endcode 0)
 * zählt als Fund, eine offene Portnummer allein reicht nicht (damit kein
 * zufällig auf demselben Port lauschender anderer Dienst als ECoS
 * erscheint).
 * <p>
 * Das ist bewusst kein echtes Discovery, sondern eine Portabtastung mit
 * Protokoll-Bestätigung: langsamer als BiDiBs mDNS (rund 1-2 Sekunden für ein
 * /24-Netz statt sofort), dafür ohne erfundene Protokoll-Annahmen über die
 * ECoS.
 */
public final class EcosDiscovery {

    private static final int CONNECT_TIMEOUT_MS = 250;
    private static final int READ_TIMEOUT_MS = 800;
    private static final int MAX_THREADS = 64;
    private static final Pattern END_CODE = Pattern.compile("<END\\s+(-?\\d+)");

    private final Consumer<EcosDiscoveredDevice> onFound;
    private final Runnable onFinished;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private ExecutorService pool;

    /**
     * {@code onFound} wird für jeden Fund aufgerufen, {@code onFinished},
     * sobald alle Adressen geprüft sind (auch wenn nichts gefunden wurde) -
     * beide NICHT im JavaFX-Anwendungsfaden, der Aufrufer muss selbst auf
     * {@code Platform.runLater} wechseln.
     */
    public EcosDiscovery(Consumer<EcosDiscoveredDevice> onFound, Runnable onFinished) {
        this.onFound = onFound;
        this.onFinished = onFinished;
    }

    /**
     * Startet die Abtastung im Hintergrund. Ein zweiter Aufruf ohne
     * vorheriges {@link #stop()} legt einen weiteren, parallelen Lauf an -
     * der Aufrufer (siehe {@link EcosConnectionDialog}) ruft deshalb vor
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
                    if (!stopped.get()) {
                        EcosDiscoveredDevice found = probe(target);
                        if (found != null && !stopped.get()) {
                            onFound.accept(found);
                        }
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
     * Verbindet kurz auf Port {@value EcosClient#PORT} und schickt bei
     * Erfolg {@code get(1, info)} - Antwortmuster wie in {@link EcosClient}
     * (Zeilen im Muster {@code schluessel[wert]}, Abschluss {@code <END
     * code>}), hier aber mit kurzen, zum Abtasten gedachten Zeitgrenzen statt
     * der für eine echte Verbindung gedachten {@link EcosClient}-Werte, und
     * ohne dauerhafte Verbindung - der Socket schließt sich mit dem
     * try-with-resources sofort wieder.
     */
    private static EcosDiscoveredDevice probe(InetAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, EcosClient.PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            socket.getOutputStream().write("get(1, info)\n".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String deviceName = null;
            String version = null;
            String hardware = null;
            int endCode = -1;
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("<REPLY")) {
                    continue;
                }
                if (line.startsWith("<END")) {
                    Matcher m = END_CODE.matcher(line);
                    endCode = m.find() ? Integer.parseInt(m.group(1)) : -1;
                    break;
                }
                EcosClient.Line parsed = EcosClient.parseLine(line);
                String n = parsed.get("Name");
                if (n == null || n.isBlank()) {
                    n = parsed.get("_text");
                }
                if (n != null && !n.isBlank() && deviceName == null) {
                    deviceName = n;
                }
                String v = parsed.get("ApplicationVersion");
                if (v != null) {
                    version = v;
                }
                String h = parsed.get("HardwareVersion");
                if (h != null) {
                    hardware = h;
                }
            }
            if (endCode != 0) {
                // Kein "<END 0 (OK)>" - entweder gar keine ECoS-Antwort
                // (ein anderer Dienst haengt zufaellig auf demselben Port)
                // oder ein Fehlercode: in beiden Faellen kein Fund.
                return null;
            }
            return new EcosDiscoveredDevice(address, deviceName != null ? deviceName : "ECoS", version, hardware);
        } catch (IOException | RuntimeException ex) {
            // Verbindung abgelehnt/Zeitueberschreitung/kein gueltiges
            // Antwortmuster - schlicht keine ECoS an dieser Adresse.
            return null;
        }
    }
}
