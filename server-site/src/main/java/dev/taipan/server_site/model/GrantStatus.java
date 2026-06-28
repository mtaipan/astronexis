package dev.taipan.server_site.model;

public enum GrantStatus {
    /** Ожидает доставки на игровой сервер. */
    PENDING,
    /** Успешно выдан. */
    DELIVERED,
    /** Доставка провалилась после исчерпания попыток — требует ручного разбора. */
    FAILED
}
