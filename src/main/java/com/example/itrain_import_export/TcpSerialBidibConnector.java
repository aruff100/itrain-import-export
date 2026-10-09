package com.example.itrain_import_export;

import org.bidib.jbidibc.messages.MessageReceiver;
import org.bidib.jbidibc.messages.base.AbstractBaseBidib;
import org.bidib.jbidibc.messages.base.RawMessageListener;
import org.bidib.jbidibc.messages.exception.PortNotFoundException;
import org.bidib.jbidibc.messages.exception.PortNotOpenedException;
import org.bidib.jbidibc.messages.helpers.Context;
import org.bidib.jbidibc.messages.utils.ByteUtils;
import org.bidib.jbidibc.serial.LineStatusListener;
import org.bidib.jbidibc.serial.SerialMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Transportschicht "BiDiB seriell ueber TCP": dasselbe Byte-Format wie am
 * COM-Port (Magic 0xFE als Rahmen, Escape-Bytes, CRC - bidib.org/transport/
 * bidib_seriell_e.html), nur ueber eine TCP-Verbindung statt ueber eine
 * serielle Schnittstelle.
 * <p>
 * Anlass: iTrain kann BiDiB an andere Programme "weiterleiten" (einstellbarer
 * Port, beim Anwender 62800). Der Mitschnitt vom 09.10.2026
 * ({@code bidib_IFnet-BiDiB-IFnet_20261009-112045_log.txt}) zeigt: dieser Port
 * nimmt TCP-Verbindungen an, spricht aber KEIN netBiDiB - auf die
 * netBiDiB-Protokollsignatur kam keine Antwort, stattdessen schickte iTrain
 * ungefragt Rahmen der Form {@code FE 0A 01 00 00 B2 ... CRC FE}, also
 * serielles BiDiB-Framing. Deshalb diese eigene Transportschicht.
 * <p>
 * Aufbau bewusst wie {@code JSerialCommSerialConnector} aus jbidibc (dort ist
 * es ein COM-Port, hier ein Socket): Empfangene Bytes gehen unveraendert an
 * {@link #receive(byte[], int)} - der {@code SerialMessageReceiver} von
 * jbidibc puffert unvollstaendige Rahmen selbst.
 */
final class TcpSerialBidibConnector extends AbstractBaseBidib<MessageReceiver> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpSerialBidibConnector.class);

    private static final int CONNECT_TIMEOUT_MS = 5000;

    private volatile Socket socket;
    private volatile OutputStream out;
    private Thread readerThread;
    private LineStatusListener lineStatusListener;

    void setLineStatusListener(LineStatusListener lineStatusListener) {
        this.lineStatusListener = lineStatusListener;
    }

    boolean isImplAvailable() {
        return socket != null;
    }

    @Override
    public boolean isOpened() {
        Socket s = socket;
        return s != null && s.isConnected() && !s.isClosed();
    }

    /** {@code portName} ist hier "Adresse:Port", z.B. "192.168.0.171:62800". */
    @Override
    protected void internalOpen(String portName, Context context) throws PortNotFoundException, PortNotOpenedException {
        super.internalOpen(portName, context);

        int colon = portName.lastIndexOf(':');
        if (colon <= 0 || colon == portName.length() - 1) {
            throw new PortNotFoundException("Adresse:Port erwartet, erhalten: " + portName);
        }
        String host = portName.substring(0, colon).trim();
        int port;
        try {
            port = Integer.parseInt(portName.substring(colon + 1).trim());
        } catch (NumberFormatException ex) {
            throw new PortNotFoundException("Ungueltiger Port: " + portName);
        }

        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
            out = s.getOutputStream();
        } catch (IOException ex) {
            try {
                s.close();
            } catch (IOException ignored) {
                // nichts mehr zu tun
            }
            LOGGER.warn("TCP-Verbindung zu {} fehlgeschlagen.", portName, ex);
            // Ursache mitgeben - der Verbindungsdialog erkennt daran
            // "Connection refused" (Port belegt bzw. nichts lauscht).
            throw new PortNotOpenedException(portName + ": " + ex.getMessage(), ex);
        }
        socket = s;

        startReceiverAndQueues(getMessageReceiver(), context);

        InputStream in;
        try {
            in = s.getInputStream();
        } catch (IOException ex) {
            close();
            throw new PortNotOpenedException(portName + ": " + ex.getMessage(), PortNotOpenedException.UNKNOWN);
        }
        readerThread = new Thread(() -> readLoop(s, in), "bidib-tcp-serial-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        notifyLineStatus(true);
        setConnected(true);
        LOGGER.info("BiDiB seriell ueber TCP verbunden: {}", portName);
    }

    private void readLoop(Socket s, InputStream in) {
        byte[] buffer = new byte[2048];
        try {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n > 0) {
                    receive(buffer, n);
                }
            }
            LOGGER.info("Gegenstelle hat die TCP-Verbindung beendet.");
        } catch (IOException ex) {
            if (socket == s) {
                LOGGER.warn("Lesen von der TCP-Verbindung abgebrochen.", ex);
            }
        }
        // Nur aufraeumen, wenn nicht ohnehin gerade geschlossen wird.
        if (socket == s) {
            Thread closer = new Thread(this::close, "bidib-tcp-serial-close");
            closer.setDaemon(true);
            closer.start();
        }
    }

    @Override
    public boolean close() {
        Socket s = socket;
        if (s == null) {
            return false;
        }
        socket = null;
        out = null;
        try {
            s.close();
        } catch (IOException ignored) {
            // Socket war schon zu.
        }
        stopReceiverAndQueues(getMessageReceiver());
        firstPacketSent = false;
        notifyLineStatus(false);
        setConnected(false);
        return true;
    }

    private void notifyLineStatus(boolean ready) {
        if (lineStatusListener != null) {
            try {
                lineStatusListener.notifyLineStatusChanged(ready, true);
            } catch (RuntimeException ex) {
                LOGGER.warn("Leitungsstatus-Meldung fehlgeschlagen.", ex);
            }
        }
    }

    private final ByteArrayOutputStream sendBuffer = new ByteArrayOutputStream(100);

    @Override
    protected void sendData(ByteArrayOutputStream data, RawMessageListener rawMessageListener) {
        OutputStream target = out;
        if (target == null || data == null) {
            return;
        }
        try {
            if (!firstPacketSent) {
                // Wie am COM-Port: zuerst ein Magic-Byte, damit die
                // Gegenseite ihren Empfangspuffer sauber neu beginnt.
                byte[] initial = {ByteUtils.MAGIC};
                if (rawMessageListener != null) {
                    rawMessageListener.notifySend(initial);
                }
                target.write(initial);
                firstPacketSent = true;
            }
            sendBuffer.reset();
            SerialMessageEncoder.encodeMessage(data, sendBuffer);
            byte[] bytes = sendBuffer.toByteArray();
            if (rawMessageListener != null) {
                rawMessageListener.notifySend(bytes);
            }
            target.write(bytes);
            target.flush();
        } catch (IOException ex) {
            throw new RuntimeException("Senden ueber TCP fehlgeschlagen: " + ByteUtils.bytesToHex(data.toByteArray()), ex);
        } finally {
            sendBuffer.reset();
        }
    }
}
