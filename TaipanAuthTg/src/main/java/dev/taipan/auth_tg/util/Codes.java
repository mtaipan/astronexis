package dev.taipan.auth_tg.util;

import java.security.SecureRandom;

public final class Codes {
    private static final SecureRandom R = new SecureRandom();
    private Codes() {}

    public static String code6() {
        int v = 100000 + R.nextInt(900000);
        return Integer.toString(v);
    }
}