package dev.taipan.auth_tg.tg;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class TelegramApi {

    private final String token;
    private final String prefix;
    private final HttpClient http = HttpClient.newHttpClient();

    public TelegramApi(String token, String prefix) {
        this.token = token;
        this.prefix = prefix;
    }

    public void sendMessage(long chatId, String text) {
        if (token == null || token.isBlank()) return;

        String msg = (prefix == null ? "" : prefix + " ") + text;
        String url = "https://api.telegram.org/bot" + token + "/sendMessage"
                + "?chat_id=" + chatId
                + "&text=" + URLEncoder.encode(msg, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
    }
}