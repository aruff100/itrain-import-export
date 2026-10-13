package com.example.itrain_import_export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nur-lesender FTP-Zugriff auf die Tams mc2 (Konfigurationsordner
 * "/config", Port 21) - fuer {@link Mc2LocoReader}. Andre am 15.09.: die
 * Lok-Daten der mc2 liegen dort in der Datei "loco.ini".
 * <p>
 * Der FTP-Server der mc2 antwortet auf USER/PASS abweichend vom Standard
 * (immer "220 Service ready for new user." statt 331/230) - jede
 * Anmeldung wird trotzdem akzeptiert (Handtest 15.09. per rohem
 * Kommandodialog, {@code tools/mc2_ftp_raw.ps1}). Deshalb wertet dieser
 * Client die Antwortcodes von USER/PASS nicht aus; SYST/PWD/PASV/RETR
 * funktionieren danach normal. Nur passiver Modus (PASV) - die mc2 sitzt
 * hinter keiner NAT, ein Datenport auf Klientenseite waere zusaetzliche
 * Firewall-Komplexitaet ohne Nutzen.
 */
public final class Mc2FtpClient {

    public static final int PORT = 21;
    private static final int TIMEOUT_MS = 6000;
    private static final Pattern PASV_PATTERN =
            Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");

    private Mc2FtpClient() {
    }

    /**
     * Liest eine Datei aus einem Ordner (z.B. "/config", "loco.ini") als
     * Text. Baut je Aufruf eine eigene Verbindung auf - fuer die seltenen,
     * kleinen Konfigurationsdateien reicht das; eine dauerhafte
     * FTP-Verbindung wie bei {@link BidibConnection}/{@link EcosConnection}
     * ist hier nicht noetig.
     */
    public static String fetchText(String host, String dir, String fileName) throws IOException {
        try (Socket control = new Socket()) {
            control.connect(new InetSocketAddress(host, PORT), TIMEOUT_MS);
            control.setSoTimeout(TIMEOUT_MS);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = control.getOutputStream();
            readReply(reader); // Begruessung
            command(out, reader, "USER anonymous");
            command(out, reader, "PASS anonymous@");
            if (dir != null && !dir.isBlank()) {
                String cwdReply = command(out, reader, "CWD " + dir);
                if (!cwdReply.startsWith("2")) {
                    throw new IOException("CWD " + dir + " fehlgeschlagen: " + cwdReply);
                }
            }
            command(out, reader, "TYPE I");
            String pasvReply = command(out, reader, "PASV");
            Matcher m = PASV_PATTERN.matcher(pasvReply);
            if (!m.find()) {
                throw new IOException("PASV-Antwort nicht verstanden: " + pasvReply);
            }
            String dataHost = m.group(1) + "." + m.group(2) + "." + m.group(3) + "." + m.group(4);
            int dataPort = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));

            byte[] content;
            try (Socket data = new Socket()) {
                data.connect(new InetSocketAddress(dataHost, dataPort), TIMEOUT_MS);
                data.setSoTimeout(TIMEOUT_MS);
                String retrReply = command(out, reader, "RETR " + fileName);
                if (!retrReply.startsWith("1")) {
                    throw new IOException("RETR " + fileName + " fehlgeschlagen: " + retrReply);
                }
                content = data.getInputStream().readAllBytes();
            }
            readReply(reader); // 226 Transfer complete
            command(out, reader, "QUIT");
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------
    // Schreibzugriffe (seit 13.10.2026, "Lokomotiven zur mc2 uebertragen").
    // Handtest 13.10.2026 im Ordner /tmp der mc2: STOR, RNFR/RNTO und DELE
    // funktionieren; die Rechte im Listing sind ueberall rw-rw-rw-.
    // ------------------------------------------------------------------

    /** Eine angemeldete Steuerverbindung, optional schon im Ordner {@code dir}. */
    private static final class Session implements AutoCloseable {
        final Socket control = new Socket();
        final BufferedReader reader;
        final OutputStream out;

        Session(String host, String dir) throws IOException {
            control.connect(new InetSocketAddress(host, PORT), TIMEOUT_MS);
            control.setSoTimeout(TIMEOUT_MS);
            reader = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
            out = control.getOutputStream();
            readReply(reader);
            command(out, reader, "USER anonymous");
            command(out, reader, "PASS anonymous@");
            if (dir != null && !dir.isBlank()) {
                String cwdReply = command(out, reader, "CWD " + dir);
                if (!cwdReply.startsWith("2")) {
                    throw new IOException("CWD " + dir + " fehlgeschlagen: " + cwdReply);
                }
            }
        }

        String cmd(String text) throws IOException {
            return command(out, reader, text);
        }

        /** PASV und Datenverbindung oeffnen (vor dem eigentlichen Befehl). */
        Socket openData() throws IOException {
            String pasvReply = cmd("PASV");
            Matcher m = PASV_PATTERN.matcher(pasvReply);
            if (!m.find()) {
                throw new IOException("PASV-Antwort nicht verstanden: " + pasvReply);
            }
            String dataHost = m.group(1) + "." + m.group(2) + "." + m.group(3) + "." + m.group(4);
            int dataPort = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
            Socket data = new Socket();
            data.connect(new InetSocketAddress(dataHost, dataPort), TIMEOUT_MS);
            data.setSoTimeout(TIMEOUT_MS);
            return data;
        }

        @Override
        public void close() throws IOException {
            try {
                cmd("QUIT");
            } catch (IOException ignored) {
                // Verbindung ohnehin am Ende
            } finally {
                control.close();
            }
        }
    }

    /** Dateinamen eines Ordners (LIST, letzte Spalte des Unix-Listings). */
    public static java.util.List<String> listNames(String host, String dir) throws IOException {
        try (Session session = new Session(host, dir)) {
            session.cmd("TYPE A");
            byte[] content;
            try (Socket data = session.openData()) {
                String reply = session.cmd("LIST");
                if (!reply.startsWith("1")) {
                    throw new IOException("LIST fehlgeschlagen: " + reply);
                }
                content = data.getInputStream().readAllBytes();
            }
            readReply(session.reader);
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String line : new String(content, StandardCharsets.UTF_8).split("\r?\n")) {
                String[] parts = line.trim().split("\\s+", 9);
                if (parts.length == 9 && !parts[0].startsWith("d")) {
                    names.add(parts[8]);
                }
            }
            return names;
        }
    }

    /** Schreibt {@code text} (UTF-8) als Datei - ueberschreibt eine vorhandene gleichnamige Datei. */
    public static void uploadText(String host, String dir, String fileName, String text) throws IOException {
        try (Session session = new Session(host, dir)) {
            session.cmd("TYPE I");
            try (Socket data = session.openData()) {
                String reply = session.cmd("STOR " + fileName);
                if (!reply.startsWith("1")) {
                    throw new IOException("STOR " + fileName + " fehlgeschlagen: " + reply);
                }
                data.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
                data.getOutputStream().flush();
            }
            String done = readReply(session.reader);
            if (!done.startsWith("2")) {
                throw new IOException("STOR " + fileName + " nicht abgeschlossen: " + done);
            }
        }
    }

    /** Benennt eine Datei im Ordner um (RNFR/RNTO). */
    public static void rename(String host, String dir, String from, String to) throws IOException {
        try (Session session = new Session(host, dir)) {
            // Wie bei USER/PASS weicht die mc2 bei Antwortcodes evtl. vom
            // Standard ab - jede Antwort unter 400 gilt als Erfolg.
            String rnfr = session.cmd("RNFR " + from);
            if (!isOk(rnfr)) {
                throw new IOException("RNFR " + from + " fehlgeschlagen: " + rnfr);
            }
            String rnto = session.cmd("RNTO " + to);
            if (!isOk(rnto)) {
                throw new IOException("RNTO " + to + " fehlgeschlagen: " + rnto);
            }
        }
    }

    /** Loescht eine Datei im Ordner (DELE). */
    public static void delete(String host, String dir, String fileName) throws IOException {
        try (Session session = new Session(host, dir)) {
            String reply = session.cmd("DELE " + fileName);
            if (!isOk(reply)) {
                throw new IOException("DELE " + fileName + " fehlgeschlagen: " + reply);
            }
        }
    }

    /** Antwortcode 1xx/2xx/3xx. */
    private static boolean isOk(String reply) {
        return reply != null && !reply.isEmpty() && reply.charAt(0) >= '1' && reply.charAt(0) <= '3';
    }

    private static String command(OutputStream out, BufferedReader reader, String command) throws IOException {
        out.write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        return readReply(reader);
    }

    /** Liest eine (ggf. mehrzeilige) FTP-Antwort und liefert die letzte Zeile. */
    private static String readReply(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Verbindung geschlossen.");
        }
        String last = line;
        // Mehrzeilige Antwort: "123-..." leitet ein, "123 ..." (gleicher Code, Leerzeichen) beendet sie.
        if (line.length() > 3 && line.charAt(3) == '-') {
            String code = line.substring(0, 3);
            while (true) {
                String next = reader.readLine();
                if (next == null) {
                    throw new IOException("Verbindung geschlossen.");
                }
                last = next;
                if (next.startsWith(code + " ")) {
                    break;
                }
            }
        }
        return last;
    }
}
