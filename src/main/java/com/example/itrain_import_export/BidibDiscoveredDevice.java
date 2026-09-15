package com.example.itrain_import_export;

import javax.jmdns.ServiceInfo;
import java.net.InetAddress;

/**
 * Ein per mDNS/DNS-SD gefundener netBiDiB-Teilnehmer (siehe
 * {@link BidibDiscovery}) - entspricht einem SRV/TXT-Eintrag unter
 * "_bidib._tcp.local." (bidib.org/transport/bidib_net_e.html, Abschnitt
 * 3.1.1).
 */
public final class BidibDiscoveredDevice {

    private final String serviceName;
    private final InetAddress address;
    private final int port;
    private final String uid;
    private final String productName;
    private final String userName;
    private final boolean node;
    private final boolean interfaceRole;

    private BidibDiscoveredDevice(String serviceName, InetAddress address, int port, String uid,
                                   String productName, String userName, boolean node, boolean interfaceRole) {
        this.serviceName = serviceName;
        this.address = address;
        this.port = port;
        this.uid = uid;
        this.productName = productName;
        this.userName = userName;
        this.node = node;
        this.interfaceRole = interfaceRole;
    }

    /** Liest die für uns relevanten Felder aus einem aufgelösten mDNS-Dienst. */
    static BidibDiscoveredDevice fromServiceInfo(ServiceInfo info) {
        InetAddress[] v4 = info.getInet4Addresses();
        InetAddress[] all = info.getInetAddresses();
        InetAddress address = v4.length > 0 ? v4[0] : (all.length > 0 ? all[0] : null);
        return new BidibDiscoveredDevice(
                info.getName(),
                address,
                info.getPort(),
                info.getPropertyString("uid"),
                info.getPropertyString("prod"),
                info.getPropertyString("user"),
                info.getPropertyString("node") != null,
                info.getPropertyString("interface") != null);
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getUid() {
        return uid;
    }

    public boolean isNode() {
        return node;
    }

    public boolean isInterfaceRole() {
        return interfaceRole;
    }

    /** "host:port" - direkt als Zielangabe für {@code NetBidibClient.open(...)} verwendbar. */
    public String getHostPort() {
        String host = address != null ? address.getHostAddress() : serviceName;
        return host + ":" + port;
    }

    /** Anzeige-Text für die Geräteliste im Verbindungsdialog. */
    public String getDisplayText() {
        String label = notBlank(userName) ? userName : (notBlank(productName) ? productName : serviceName);
        String rolePart = node && interfaceRole ? "Node+Interface" : node ? "Node" : interfaceRole ? "Interface" : "";
        String host = address != null ? address.getHostAddress() : "?";
        StringBuilder sb = new StringBuilder(label).append("  (").append(host).append(':').append(port);
        if (!rolePart.isEmpty()) {
            sb.append(", ").append(rolePart);
        }
        sb.append(')');
        return sb.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
