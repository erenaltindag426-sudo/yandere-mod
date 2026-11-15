package yandere;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Claude Messages API'ye asenkron istek atar. Hata olursa null doner. */
public final class AiClient {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private AiClient() {}

    public static CompletableFuture<String> ask(String system, List<JsonObject> history) {
        YandereConfig cfg = YandereConfig.get();
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model);
        body.addProperty("max_tokens", 300);
        body.addProperty("system", system);
        JsonArray msgs = new JsonArray();
        history.forEach(msgs::add);
        body.add("messages", msgs);

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .header("content-type", "application/json")
                .header("x-api-key", cfg.key())
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(AiClient::parse)
                .exceptionally(e -> {
                    YandereMod.LOGGER.warn("AI istegi basarisiz: {}", e.toString());
                    return null;
                });
    }

    private static String parse(HttpResponse<String> r) {
        if (r.statusCode() / 100 != 2) {
            YandereMod.LOGGER.warn("AI HTTP {}: {}", r.statusCode(), r.body());
            return null;
        }
        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : o.getAsJsonArray("content")) {
            JsonObject b = e.getAsJsonObject();
            if ("text".equals(b.get("type").getAsString())) sb.append(b.get("text").getAsString());
        }
        return sb.toString().trim();
    }
}
