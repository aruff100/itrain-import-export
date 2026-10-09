package com.example.itrain_import_export;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gemeinsame Grundlage für jede Subnetz-Abtastung dieses Programms (siehe
 * {@link EcosDiscovery}, {@link Mc2Discovery}) - beide suchen ein Gerät, das
 * kein zuverlässiges Discovery-Protokoll anbietet, indem sie stattdessen das
 * lokale /24-Netz durchprobieren.
 * <p>
 * Ursprünglich in {@code EcosDiscovery} entstanden, beim Hinzukommen der
 * zweiten Abtastung (mc2 per FTP) hierher ausgelagert, damit beide dieselbe,
 * einmal geprüfte Logik verwenden statt einer zweiten, leicht abweichenden
 * Kopie.
 */
final class LocalSubnetUtil {

    private LocalSubnetUtil() {
    }

    /**
     * Alle Host-Adressen im lokalen /24-Netz jeder geeigneten
     * IPv4-Schnittstelle (kein Loopback, keine Punkt-zu-Punkt-Verbindung,
     * kein APIPA-Bereich 169.254.0.0/16 - dieselbe Begründung wie in
     * {@link BidibDiscovery#localIpv4Addresses()}), ohne die eigene
     * Adresse. Ist die tatsächliche Schnittstellen-Netzmaske ENGER als /24
     * (z.B. /28, weniger als 254 Host-Adressen), wird genau dieses engere
     * Netz verwendet; ist sie WEITER (z.B. /16, mehr Adressen), wird bewusst
     * nur das /24-Netz um die eigene Adresse herum abgetastet - sonst
     * könnten es je Schnittstelle zehntausende Adressen werden.
     */
    static List<InetAddress> subnetAddresses() {
        Set<String> seen = new LinkedHashSet<>();
        List<InetAddress> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!isUsable(nic)) {
                    continue;
                }
                for (InterfaceAddress ifAddr : nic.getInterfaceAddresses()) {
                    InetAddress local = ifAddr.getAddress();
                    if (!(local instanceof Inet4Address) || local.isLoopbackAddress()
                            || local.isLinkLocalAddress()) {
                        continue;
                    }
                    int prefix = Math.max(ifAddr.getNetworkPrefixLength(), 24);
                    for (InetAddress candidate : hostsInSubnet(local, prefix)) {
                        if (candidate.equals(local)) {
                            continue;
                        }
                        if (seen.add(candidate.getHostAddress())) {
                            result.add(candidate);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // Ruecklaeufig: leere Liste - der Aufrufer meldet dann sofort "fertig, nichts gefunden".
        }
        return result;
    }

    /**
     * Die eigenen IPv4-Adressen (ohne Loopback/APIPA) - {@link #subnetAddresses()}
     * laesst sie bewusst aus, fuer Dienste auf dem EIGENEN Rechner (z.B. die
     * iTrain-BiDiB-Weiterleitung, die nur an der Netzadresse lauscht) werden
     * sie aber gebraucht.
     */
    static List<InetAddress> localAddresses() {
        List<InetAddress> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!isUsable(nic)) {
                    continue;
                }
                for (InterfaceAddress ifAddr : nic.getInterfaceAddresses()) {
                    InetAddress local = ifAddr.getAddress();
                    if (local instanceof Inet4Address && !local.isLoopbackAddress() && !local.isLinkLocalAddress()) {
                        result.add(local);
                    }
                }
            }
        } catch (Exception ex) {
            // leere Liste
        }
        return result;
    }

    private static boolean isUsable(NetworkInterface nic) {
        try {
            return nic.isUp() && !nic.isLoopback() && !nic.isPointToPoint();
        } catch (Exception ex) {
            return false;
        }
    }

    /** Alle Host-Adressen (ohne Netz- und Broadcast-Adresse) des Subnetzes, das {@code local} mit {@code prefix} Bits beschreibt. */
    private static List<InetAddress> hostsInSubnet(InetAddress local, int prefix) {
        List<InetAddress> hosts = new ArrayList<>();
        int hostCount = (1 << (32 - prefix)) - 2;
        if (hostCount <= 0) {
            return hosts;
        }
        int base = toInt(local.getAddress()) & maskFor(prefix);
        for (int i = 1; i <= hostCount; i++) {
            try {
                hosts.add(InetAddress.getByAddress(toBytes(base + i)));
            } catch (Exception ignored) {
                // Ungueltige Adresse (bei einem korrekten Prefix sollte das nicht vorkommen) - ueberspringen.
            }
        }
        return hosts;
    }

    private static int maskFor(int prefix) {
        return prefix >= 32 ? -1 : ~((1 << (32 - prefix)) - 1);
    }

    private static int toInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    private static byte[] toBytes(int value) {
        return new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }
}
