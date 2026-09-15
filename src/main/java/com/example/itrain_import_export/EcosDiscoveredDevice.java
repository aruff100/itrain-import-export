package com.example.itrain_import_export;

import java.net.InetAddress;

/**
 * Eine per Subnetz-Abtastung gefundene ESU ECoS (siehe {@link EcosDiscovery}
 * für den Grund, warum das keine echte Gerätesuche über ein
 * Discovery-Protokoll ist, sondern eine Portabtastung mit
 * Protokoll-Bestätigung).
 */
public final class EcosDiscoveredDevice {

    private final InetAddress address;
    private final String deviceName;
    private final String version;
    private final String hardware;

    EcosDiscoveredDevice(InetAddress address, String deviceName, String version, String hardware) {
        this.address = address;
        this.deviceName = deviceName;
        this.version = version;
        this.hardware = hardware;
    }

    public InetAddress getAddress() {
        return address;
    }

    /** Die reine IP-Adresse, z.B. "192.168.0.50" - direkt als Zielangabe für {@link EcosConnection#open} verwendbar. */
    public String getHost() {
        return address.getHostAddress();
    }

    /** Anzeige-Text für die Fundliste im Verbindungsdialog, z.B. "ECoS2 - Version 4.2.11 (192.168.0.50)". */
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder(deviceName != null && !deviceName.isBlank() ? deviceName : "ECoS");
        if (version != null && !version.isBlank()) {
            sb.append(" - Version ").append(version);
        }
        if (hardware != null && !hardware.isBlank()) {
            sb.append(", HW ").append(hardware);
        }
        sb.append("  (").append(address.getHostAddress()).append(')');
        return sb.toString();
    }
}
