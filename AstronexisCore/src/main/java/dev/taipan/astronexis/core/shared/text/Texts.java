package dev.taipan.astronexis.core.shared.text;

public final class Texts {

    private Texts() {
    }

    public static String color(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "§");
    }
}