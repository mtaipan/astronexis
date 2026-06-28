package dev.taipan.server_site.rcon;

/** Ошибка доставки команды по RCON (сеть, аутентификация, протокол). */
public class RconException extends RuntimeException {

    public RconException(String message) {
        super(message);
    }

    public RconException(String message, Throwable cause) {
        super(message, cause);
    }
}
