package yandere;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/yandere_mod.json — API anahtarini buraya (ya da ANTHROPIC_API_KEY ortam degiskenine) yaz. */
public class YandereConfig {
    public String apiKey = "";
    public String model = "claude-haiku-5-5";
    public String name = "Yuki";

    private static YandereConfig INSTANCE;

    public static YandereConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    private static void load() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("yandere_mod.json");
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(p)) INSTANCE = g.fromJson(Files.readString(p), YandereConfig.class);
        } catch (Exception ignored) {}
        if (INSTANCE == null) {
            INSTANCE = new YandereConfig();
            try { Files.writeString(p, g.toJson(INSTANCE)); } catch (IOException ignored) {}
        }
    }

    public String key() {
        if (apiKey != null && !apiKey.isBlank()) return apiKey;
        String env = System.getenv("ANTHROPIC_API_KEY");
        return env == null ? "" : env;
    }
}
