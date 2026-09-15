package com.example.itrain_import_export;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sucht netBiDiB-Server im lokalen Netz per DNS Service Discovery (mDNS,
 * Dienst-Typ "_bidib._tcp.local.", siehe
 * bidib.org/transport/bidib_net_e.html Abschnitt 3.1.1). Läuft nur, solange
 * {@link #start()} aktiv ist - {@link #stop()} gibt den mDNS-Dienst wieder
 * frei.
 * <p>
 * Wichtig bei mehreren Netzwerkschnittstellen: {@code JmDNS.create()} ohne
 * Angabe wählt selbstständig GENAU EINE lokale Adresse. Auf Rechnern mit
 * mehreren Netzwerkkarten (VPN, virtuelle Adapter von Docker/VMware/Hyper-V,
 * gleichzeitig aktivem WLAN und LAN, ...) ist das häufig nicht die
 * Schnittstelle, an der die BiDiB-Geräte tatsächlich hängen - dann wird
 * nichts gefunden, obwohl die Geräte erreichbar sind. Deshalb wird hier auf
 * JEDER geeigneten Netzwerkschnittstelle einzeln gesucht.
 * <p>
 * Wichtig generell: Das findet nur etwas, wenn die Geräte selbst per mDNS/
 * DNS-SD announct werden - laut bidib.org ist das nur "empfohlen", nicht
 * vorgeschrieben. Manche (v.a. einfachere oder ältere) Geräte kennen nur die
 * feste IP-Adresse und müssen von Hand eingetragen werden (Feld "manuell" im
 * Verbindungsdialog).
 */
public final class BidibDiscovery {

    /** Dienst-Typ nach DNS-SD (IANA-Eintrag "bidib", siehe bidib.org). */
    private static final String SERVICE_TYPE = "_bidib._tcp.local.";

    private static final int RESOLVE_TIMEOUT_MS = 3000;

    private final List<JmDNS> instances = new ArrayList<>();
    private final Map<String, BidibDiscoveredDevice> found = new LinkedHashMap<>();
    private final Consumer<Map<String, BidibDiscoveredDevice>> onChange;

    /** {@code onChange} wird bei jeder Änderung mit dem aktuellen, vollständigen Stand aufgerufen - NICHT im JavaFX-Thread. */
    public BidibDiscovery(Consumer<Map<String, BidibDiscoveredDevice>> onChange) {
        this.onChange = onChange;
    }

    /**
     * Startet die Suche im Hintergrund auf jeder geeigneten IPv4-Schnittstelle
     * (siehe Klassenkommentar). Findet sich keine passende Schnittstelle,
     * wird ersatzweise {@code JmDNS.create()} ohne Angabe verwendet.
     */
    public void start() throws IOException {
        List<InetAddress> addresses = localIpv4Addresses();
        if (addresses.isEmpty()) {
            addresses.add(null);
        }
        IOException firstError = null;
        for (InetAddress address : addresses) {
            try {
                JmDNS jmdns = address != null ? JmDNS.create(address) : JmDNS.create();
                jmdns.addServiceListener(SERVICE_TYPE, createListener(jmdns));
                instances.add(jmdns);
            } catch (IOException ex) {
                if (firstError == null) {
                    firstError = ex;
                }
            }
        }
        // Scheitern ALLE konkret angesprochenen Adressen (beobachtet unter
        // Windows als "Invalid argument: setsockopt" - typischerweise ein
        // virtueller Adapter, der sich zwar als "up"/multicast-faehig
        // meldet, den eigentlichen Multicast-Gruppenbeitritt aber ablehnt),
        // noch ersatzweise JmDNS.create() ohne Adressangabe versuchen: dessen
        // eigene, andere Auswahllogik trifft manchmal eine Adresse, an der es
        // tatsaechlich funktioniert. Erst wenn auch das scheitert, wird
        // aufgegeben - die manuelle IP-Eingabe bleibt in jedem Fall nutzbar.
        if (instances.isEmpty() && firstError != null) {
            try {
                JmDNS jmdns = JmDNS.create();
                jmdns.addServiceListener(SERVICE_TYPE, createListener(jmdns));
                instances.add(jmdns);
            } catch (IOException ex) {
                throw firstError;
            }
        }
    }

    private ServiceListener createListener(JmDNS jmdns) {
        return new ServiceListener() {

            @Override
            public void serviceAdded(ServiceEvent event) {
                // Nur Ankündigung ohne TXT/SRV-Daten - erst auflösen bringt Adresse und Port.
                jmdns.requestServiceInfo(event.getType(), event.getName(), RESOLVE_TIMEOUT_MS);
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                synchronized (found) {
                    found.remove(event.getName());
                }
                fireChange();
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                ServiceInfo info = event.getInfo();
                synchronized (found) {
                    found.put(event.getName(), BidibDiscoveredDevice.fromServiceInfo(info));
                }
                fireChange();
            }
        };
    }

    /**
     * Alle IPv4-Adressen "echter" aktiver Netzwerkschnittstellen (kein
     * Loopback, keine Punkt-zu-Punkt-Verbindung, multicast-fähig) - eine
     * JmDNS-Instanz je Adresse, damit auch Rechner mit mehreren
     * Netzwerkkarten zuverlässig auf der richtigen suchen.
     */
    private static List<InetAddress> localIpv4Addresses() {
        List<InetAddress> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!isUsable(nic)) {
                    continue;
                }
                Enumeration<InetAddress> nicAddresses = nic.getInetAddresses();
                while (nicAddresses.hasMoreElements()) {
                    InetAddress address = nicAddresses.nextElement();
                    // Adressen aus 169.254.0.0/16 (APIPA) sind eine
                    // Selbstzuweisung fuer Adapter ohne echte Netzanbindung
                    // (z.B. eine getrennte VPN-/Hyper-V-/WLAN-Karte, die
                    // Windows trotzdem als "up" und multicast-faehig
                    // meldet) - genau auf solchen Adaptern schlaegt der
                    // Multicast-Gruppenbeitritt unter Windows mit "Invalid
                    // argument: setsockopt" fehl, deshalb werden sie hier
                    // von vornherein ausgeschlossen.
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        result.add(address);
                    }
                }
            }
        } catch (Exception ex) {
            // Ruecklaeufig: leere Liste - der Aufrufer weicht dann auf JmDNS.create() aus.
        }
        return result;
    }

    private static boolean isUsable(NetworkInterface nic) {
        try {
            return nic.isUp() && !nic.isLoopback() && !nic.isPointToPoint() && nic.supportsMulticast();
        } catch (Exception ex) {
            return false;
        }
    }

    private void fireChange() {
        Map<String, BidibDiscoveredDevice> snapshot;
        synchronized (found) {
            snapshot = new LinkedHashMap<>(found);
        }
        onChange.accept(snapshot);
    }

    /** Beendet die Suche auf allen Schnittstellen. Danach kann {@link #start()} erneut aufgerufen werden. */
    public void stop() {
        for (JmDNS jmdns : instances) {
            try {
                jmdns.close();
            } catch (IOException ignored) {
                // beim Beenden unerheblich
            }
        }
        instances.clear();
        synchronized (found) {
            found.clear();
        }
    }
}
