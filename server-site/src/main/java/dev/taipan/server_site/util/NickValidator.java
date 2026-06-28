package dev.taipan.server_site.util;

import dev.taipan.server_site.model.Platform;

import java.util.regex.Pattern;

public final class NickValidator {
    private NickValidator() {}

    private static final Pattern P = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public static boolean isValidBaseNick(String nick) {
        if (nick == null) return false;
        return P.matcher(nick.trim()).matches();
    }

    public static String norm(String nick) {
        return nick == null ? "" : nick.trim();
    }

    public static String normalizeForPlatform(Platform platform, String rawNick) {
        String n = norm(rawNick);

        if (platform == Platform.BEDROCK) {
            // если уже ввел bed_ — режем, чтобы не было bed_bed_
            String base = n;
            if (base.regionMatches(true, 0, "bed_", 0, 4)) {
                base = base.substring(4);
            }
            if (!isValidBaseNick(base)) {
                throw new IllegalArgumentException("Некорректный ник.");
            }
            return "bed_" + base;
        }

        // JAVA
        if (!isValidBaseNick(n)) {
            throw new IllegalArgumentException("Некорректный ник.");
        }
        return n;
    }
}