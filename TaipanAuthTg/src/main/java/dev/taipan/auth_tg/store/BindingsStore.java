package dev.taipan.auth_tg.store;

public interface BindingsStore {
    Long getChatIdByNick(String nick);
}