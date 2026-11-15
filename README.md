# Yandere Yuki — Minecraft Java (Fabric 1.21.1)

## Kurulum
1. JDK 21 kur. Gradle wrapper yoksa https://fabricmc.net/develop adresinden 1.21.1 şablonu indirip
   `gradlew`, `gradlew.bat` ve `gradle/` klasörünü bu klasöre kopyala.
2. `./gradlew build` -> `build/libs/yandere-mod-1.0.0.jar`
3. Jar'ı `mods/` klasörüne at (Fabric Loader + Fabric API gerekli).
4. Oyunu bir kez aç, `config/yandere_mod.json` oluşur. `apiKey` alanına Anthropic API anahtarını yaz
   (ya da ANTHROPIC_API_KEY ortam değişkeni). Anahtar yoksa hazır replikler kullanılır.
5. Dünyada: `/yandere spawn`

## Komutlar
- `/yandere spawn` — Yuki'yi çağır
- `/yandere ev` — Ev yaptır (chat'te "ev yapsana" demen de yeter, AI karar verir)
- `/yandere kiskan` — (op) kıskançlığı hemen fulle, test için
- `/yandere gitsin` — Kaldır

## Davranış
SWEET (takip eder, bakarsan donar) -> JEALOUS (köylüye yakın durdukça) -> RAGE (bıçakla köylüyü avlar)
-> GLITCH (ekran kararır, bozuk konuşur) -> SWEET (masum numarası). Ceset köylünün yatağına bırakılır.

## Kolay yol: jar'ı GitHub derlesin
1. github.com'da ücretsiz hesap aç, yeni bir repo oluştur.
2. Bu klasörün içindekileri (zip'i açıp) repoya yükle ("Add file > Upload files").
3. Repoda "Actions" sekmesine gir, "build" çalışmasının bitmesini bekle (2-3 dk).
4. Çalışmaya tıkla, en altta "yandere-mod-jar" dosyasını indir, içinden .jar'ı `mods/` klasörüne at.
