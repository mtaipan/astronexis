package dev.taipan.server_site.model;

public enum GrantStatus {
    /** Ожидает доставки на игровой сервер. */
    PENDING,
    /** Захвачен одним из доставщиков (webhook или планировщик) — RCON-команда в полёте (SITE-9). */
    DELIVERING,
    /** Успешно выдан. */
    DELIVERED,
    /** Доставка провалилась после исчерпания попыток — требует ручного разбора. */
    FAILED
}
