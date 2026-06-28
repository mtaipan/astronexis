package dev.taipan.auth_tg.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

public final class Fingerprints {
    private Fingerprints() {}

    public static String fingerprint(Player p, String trustBind, int trustSubnetV4) {
        boolean bedrock = isBedrockPlayer(p.getUniqueId());
        String platform = bedrock ? "bedrock" : "java";

        String ipPart = switch (trustBind) {
            case "none" -> "none";
            case "ip" -> ipString(p);
            case "ip-subnet" -> ipSubnetString(p, trustSubnetV4);
            default -> ipSubnetString(p, trustSubnetV4);
        };

        return platform + "|" + ipPart;
    }

    private static String ipString(Player p) {
        InetSocketAddress a = p.getAddress();
        if (a == null) return "unknown";
        InetAddress ip = a.getAddress();
        if (ip == null) return "unknown";
        return ip.getHostAddress();
    }

    private static String ipSubnetString(Player p, int prefixV4) {
        InetSocketAddress a = p.getAddress();
        if (a == null) return "unknown";
        InetAddress ip = a.getAddress();
        if (ip == null) return "unknown";

        byte[] b = ip.getAddress();
        // only IPv4 subnetting here; IPv6 -> fall back to full IP
        if (b.length != 4) return ip.getHostAddress();

        int v = ByteBuffer.wrap(b).getInt();
        int mask = prefixV4 == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefixV4));
        int net = v & mask;

        byte[] out = ByteBuffer.allocate(4).putInt(net).array();
        try {
            return InetAddress.getByAddress(out).getHostAddress() + "/" + prefixV4;
        } catch (Exception e) {
            return ip.getHostAddress();
        }
    }

    /**
     * Soft-dep: Floodgate. Используем reflection, чтобы не требовать floodgate-api в compile/runtime.
     * Hypothesis: FloodgateApi#getInstance().isFloodgatePlayer(UUID) exists (standard in Floodgate).
     */
    private static boolean isBedrockPlayer(UUID uuid) {
        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return false;

            Class<?> apiCl = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiCl.getMethod("getInstance");
            Object api = getInstance.invoke(null);

            Method isFloodgatePlayer = apiCl.getMethod("isFloodgatePlayer", UUID.class);
            Object res = isFloodgatePlayer.invoke(api, uuid);
            return (res instanceof Boolean b) && b;
        } catch (Throwable t) {
            return false;
        }
    }
}