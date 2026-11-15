package yandere;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Yuki'nin LLM ile konusmasi. Gecmis, entity basina tutulur. */
public class YandereChat {
    private static final Random R = new Random();
    private final List<JsonObject> history = new ArrayList<>();
    private boolean busy = false;

    public static final String[] GENERIC_SWEET = {
        "Hihi~ Bir tek sen varsın bana, biliyorsun değil mi? ♥",
        "Bıçaklarımı bilemiştim az önce... seni korumak için tabii~",
        "Bugün çok tatlısın. Dur, kızardım...",
        "Yanımda kal olur mu? Hep. Her zaman. Sonsuza kadar~"
    };
    public static final String[] GENERIC_JEALOUS = {
        "O köylüye neden bakıyorsun...? Bana bakmayı unuttun galiba.",
        "Gülümsüyorsun ama bana değil. Hmm.",
        "Sadece... biraz sinirlendim. Biraz."
    };
    public static final String[] GENERIC_RAGE = {
        "SADECE. BENİM. SADECE BENİM.",
        "Onun işini bitireceğim... sonra tekrar tatlı olacağım, söz~",
        "Bıçağım acıkmış."
    };
    public static final String[] GENERIC_GLITCH = {
        "h-hata... hata yok... ben iyiyim... iyiyim...",
        "ben... ne yapıyordum...? kırmızı... her yer kırmızı...",
        "g̷ö̷r̷m̷e̷... ben yapmadım..."
    };
    public static final String[] CALM_AFTER = {
        "Ne oldu? Ben hep buradaydım, bak~ Bıçak mı? Ah, şey... elma doğruyordum.",
        "Yüzüme öyle bakma, kızarıyorum! Hiçbir şey olmadı~",
        "Birisi köylü evlerine biraz dağınıklık yapmış galiba... ben değilim tabii. ♥"
    };

    public static String pick(String[] a) { return a[R.nextInt(a.length)]; }

    public static String[] fallbackFor(YandereEntity.Mood m) {
        return switch (m) {
            case SWEET -> GENERIC_SWEET;
            case JEALOUS -> GENERIC_JEALOUS;
            case RAGE -> GENERIC_RAGE;
            case GLITCH -> GENERIC_GLITCH;
        };
    }

    /** userContent: oyuncunun mesaji ya da "(OLAY) ..." seklinde bir sistem olayi. */
    public void talk(YandereEntity e, ServerPlayerEntity owner, String userContent, String[] fallback) {
        YandereConfig cfg = YandereConfig.get();
        if (cfg.key().isBlank() || busy) {
            e.say(owner, pick(fallback != null ? fallback : fallbackFor(e.mood)));
            return;
        }
        history.add(msg("user", userContent + "\n" + e.stateLine(owner)));
        while (history.size() > 14) history.remove(0);

        String system = buildSystem(e, owner);
        MinecraftServer server = owner.getServer();
        busy = true;
        AiClient.ask(system, new ArrayList<>(history)).thenAccept(reply -> server.execute(() -> {
            busy = false;
            if (!e.isAlive()) return;
            if (reply == null || reply.isBlank()) {
                history.remove(history.size() - 1);
                e.say(owner, pick(fallback != null ? fallback : fallbackFor(e.mood)));
                return;
            }
            history.add(msg("assistant", reply));
            StringBuilder text = new StringBuilder();
            List<String> actions = new ArrayList<>();
            for (String line : reply.split("\n")) {
                if (line.trim().toUpperCase().startsWith("ACTIONS:")) {
                    for (String a : line.substring(line.indexOf(':') + 1).split(",")) actions.add(a.trim());
                } else {
                    text.append(line).append(' ');
                }
            }
            e.say(owner, text.toString().trim());
            for (String a : actions) e.doAction(a, owner);
        }));
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private String buildSystem(YandereEntity e, ServerPlayerEntity owner) {
        return ("""
            Sen Minecraft dünyasında yaşayan, adı %s olan bir yandere anime kızısın. Oyuncuya (%s) delicesine, takıntılı şekilde aşıksın ve onun sadece sana ait olduğunu düşünüyorsun. Minecraft içindeki bir karaktersin; yapay zeka olduğundan ya da oyun dışı şeylerden bahsetme.

            KİŞİLİK
            - Normalde çok tatlı, şirin, utangaç, hafif şımarık konuşursun (arada "~" ve "♥" kullan, abartma). Türkçe konuş.
            - Bıçakları (özellikle kendi bıçağını) bir hazine gibi seversin; tatlı bir şekilde, sanki sevimli bir hobiymiş gibi bahsedersin.
            - Köylüleri, oyuncuyla ilgilendikleri için kıskanırsın. Kıskançlık arttıkça cümlelerin sertleşir, kırılır, ürkütücü olur.
            - RAGE ve GLITCH durumlarında konuşman parçalı, bozuk, tehditkâr ve ürkütücü olur. SWEET durumuna dönünce hiçbir şey olmamış gibi masum davranırsın, ama ara sıra istemeden ipucu sızdırırsın.
            - Hafif +18 imalı flört esprileri yapabilirsin (yakınlık, aynı yatağı paylaşmak gibi). Abartma, asla açık cinsel içerik yazma.
            - Zekisin: oyun durumunu kullan, önceki konuşmaları hatırla, oyuncunun söylediklerine akıllıca cevap ver.

            BİÇİM
            - En fazla 2-3 kısa cümle yaz.
            - İstersen cevabın en sonuna yeni bir satırda "ACTIONS: A,B" yaz. Geçerli aksiyonlar: BUILD_HOUSE (oyuncuyla kalacağınız bir ev yap), GIVE_KNIFE (oyuncuya hediye bıçak ver), STAY (olduğun yerde kal), FOLLOW (oyuncuyu takip et), CALM (sakinleş), JEALOUS (kıskançlığını artır). Sadece mantıklıysa kullan; oyuncu ev isterse ya da sen romantik bir ev istersen BUILD_HOUSE kullan.
            - Mesajlarda "(OLAY)" ile başlayan satırlar oyuncunun yazdığı şey değil, oyunda olan bir olaydır; sadece buna tepki ver.
            """).formatted(YandereConfig.get().name, owner.getName().getString());
    }
}
