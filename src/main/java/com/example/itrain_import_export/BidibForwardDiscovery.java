package com.example.itrain_import_export;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Sucht die BiDiB-Weiterleitung von iTrain ("BiDiB seriell ueber TCP", siehe
 * {@link TcpSerialBidibConnector}) im lokalen Netz und auf dem eigenen
 * Rechner (127.0.0.1).
 * <p>
 * Wie bei {@link EcosDiscovery}/{@link Mc2Discovery} KEIN echtes Discovery
 * (iTrain meldet die Weiterleitung nirgends an), sondern eine Abtastung des
 * /24-Netzes auf dem eingestellten Port (Vorgabe {@value #DEFAULT_PORT}):
 * <ol>
 * <li>kurze TCP-Verbindungsprobe auf den Port,</li>
 * <li>danach bis zu {@value #LISTEN_MS} ms zuhoeren: Die Weiterleitung schickt
 * den laufenden Busverkehr ungefragt mit, in serieller BiDiB-Rahmung (Rahmen
 * beginnen/enden mit 0xFE) - kommt so ein Byte, gilt sie als bestaetigt.</li>
 * </ol>
 * Herrscht auf dem Bus gerade kein Verkehr, kommt nichts - dann erscheint der
 * offene Port trotzdem, aber als "nicht bestaetigt" markiert.
 */
public final class BidibForwardDiscovery {

    /** Port der iTrain-BiDiB-Weiterleitung beim Anwender (in iTrain einstellbar). */
    public static final int DEFAULT_PORT = 62800;

    private static final int CONNECT_TIMEOUT_MS = 250;
    private static final int LISTEN_MS = 1500;
    private static final int MAX_THREADS = 64;

    /** Ein Fund: Adresse, Port, und ob BiDiB-Rahmen empfangen wurden. */
    public record Found(String host, int port, boolean confirmed) {
        public String hostPort() {
            return host + ":" + port;
        }
    }

    private final int port;
    private final Consumer<Found> onFound;
    private final Runnable onFinished;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private ExecutorService pool;

    public BidibForwardDiscovery(int port, Consumer<Found> onFound, Runnable onFinished) {
        this.port = port;
        this.onFound = onFound;
        this.onFinished = onFinished;
    }

    public void start() {
        stopped.set(false);
        List<InetAddress> targets = new ArrayList<>();
        try {
            targets.add(InetAddress.getByName("127.0.0.1"));
        } catch (IOException ignored) {
            // kommt nicht vor
        }
        // Eigener Rechner (iTrain laeuft oft auf demselben PC) plus Netz.
        targets.addAll(LocalSubnetUtil.localAddresses());
        targets.addAll(LocalSubnetUtil.subnetAddresses());
        pool = Executors.newFixedThreadPool(Math.min(MAX_THREADS, targets.size()));
        AtomicInteger remaining = new AtomicInteger(targets.size());
        for (InetAddress target : targets) {
            pool.submit(() -> {
                try {
                    if (!stopped.get()) {
                        Found found = probe(target);
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

    public void stop() {
        stopped.set(true);
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    private Found probe(InetAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
            return new Found(address.getHostAddress(), port, sawBidibFrame(socket));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /** Bis zu {@link #LISTEN_MS} ms lesen und auf ein 0xFE (BiDiB-Rahmenzeichen) achten. */
    private static boolean sawBidibFrame(Socket socket) {
        long end = System.currentTimeMillis() + LISTEN_MS;
        byte[] buffer = new byte[256];
        try {
            InputStream in = socket.getInputStream();
            while (true) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                socket.setSoTimeout((int) left);
                int n = in.read(buffer);
                if (n < 0) {
                    return false;
                }
                for (int i = 0; i < n; i++) {
                    if ((buffer[i] & 0xFF) == 0xFE) {
                        return true;
                    }
                }
            }
        } catch (SocketTimeoutException timeout) {
            return false;
        } catch (IOException ex) {
            return false;
        }
    }
}
