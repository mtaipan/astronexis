package dev.taipan.auth_tg.tg;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TelegramApi {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final String token;
    private final String prefix;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public TelegramApi(String token, String prefix, Logger log) {
        this.token = token;
        this.prefix = prefix;
        this.log = log;
    }

    /** Отправка без обратной связи о сбое (когда сообщить некому). */
    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    /**
     * Отправляет сообщение и проверяет результат (AUTH-9): POST с телом вместо
     * fire-and-forget GET. При сбое пишет в лог и, если передан {@code onFailure},
     * уведомляет вызывающий код (chatId), чтобы игроку сообщили о проблеме доставки.
     */
    public void sendMessage(long chatId, String text, LongConsumer onFailure) {
        if (token == null || token.isBlank()) {
            warn(chatId, "bot token пуст — сообщение не отправлено", onFailure);
            return;
        }

        String msg = (prefix == null ? "" : prefix + " ") + text;
        String body = "chat_id=" + chatId
                + "&text=" + URLEncoder.encode(msg, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        // Тело ответа не логируем целиком — там может быть эхо текста (код 2FA).
                        warn(chatId, "HTTP " + resp.statusCode(), onFailure);
                    }
                })
                .exceptionally(e -> {
                    warn(chatId, String.valueOf(e.getCause() != null ? e.getCause() : e), onFailure);
                    return null;
                });
    }

    private void warn(long chatId, String reason, LongConsumer onFailure) {
        if (log != null) {
            log.log(Level.WARNING, "Telegram sendMessage failed (chatId=" + chatId + "): " + reason);
        }
        if (onFailure != null) {
            onFailure.accept(chatId);
        }
    }
}
