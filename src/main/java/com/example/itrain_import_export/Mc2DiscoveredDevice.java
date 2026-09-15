package com.example.itrain_import_export;

import java.net.InetAddress;

/**
 * Eine per FTP-Subnetz-Abtastung gefundene Tams mc2 (siehe {@link
 * Mc2Discovery}). Eigenständig von {@link BidibDiscoveredDevice} (mDNS) und
 * {@link EcosDiscoveredDevice} (ECoS-Portabtastung): dieselbe mc2 kann
 * unabhängig über netBiDiB-mDNS UND über diese FTP-Abtastung gefunden
 * werden - dann erscheint sie im Verbindungsdialog zweimal, einmal in der
 * "Netz"-Liste als BiDiB-Gerät und einmal in dieser Liste als "mc2 (FTP)".
 */
public final class Mc2DiscoveredDevice {

    private final InetAddress address;

    Mc2DiscoveredDevice(InetAddress address) {
        this.address = address;
    }

    public InetAddress getAddress() {
        return address;
    }

    /** Die reine IP-Adresse - direkt in das manuelle IP-Feld des Verbindungsdialogs übernehmbar. */
    public String getHost() {
        return address.getHostAddress();
    }

    /** Anzeige-Text für die Fundliste, z.B. "mc2 (FTP)  (192.168.0.90)". */
    public String getDisplayText() {
        return "mc2 (FTP)  (" + address.getHostAddress() + ")";
    }
}
