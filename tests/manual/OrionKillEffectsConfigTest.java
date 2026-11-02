import com.google.gson.Gson;
import ru.orion.client.config.OrionConfig;

/** Запуск с OrionConfig и Gson в classpath, без запуска Minecraft. */
public final class OrionKillEffectsConfigTest {
    private static int checks;

    public static void main(String[] args) {
        Gson gson = new Gson();
        OrionConfig defaults = OrionConfig.createDefault();
        defaults(defaults);
        OrionConfig oldConfig = gson.fromJson("{\"hudEnabled\":false}", OrionConfig.class);
        oldConfig.sanitize();
        defaults(oldConfig);
        check(!oldConfig.isHudEnabled(), "Старые настройки сохраняются");

        OrionConfig config = OrionConfig.createDefault();
        config.setKillEffectsEnabled(true);
        config.setKillEffectsStyle(8);
        config.setKillEffectsSize(1.75F);
        config.setKillEffectsSpeed(0.75F);
        config.setKillEffectsParticles(0.25F);
        config.setKillEffectsDistance(96);
        config.setKillEffectsQuality(2);
        config.setKillEffectsLimit(7);
        config.setKillEffectsVolume(37);
        OrionConfig restored = gson.fromJson(gson.toJson(config), OrionConfig.class);
        restored.sanitize();
        check(restored.isKillEffectsEnabled(), "Включение сохраняется");
        check(restored.getKillEffectsStyle() == 8, "Выбор эффекта сохраняется");
        check(restored.getKillEffectsSize() == 1.75F, "Размер сохраняется");
        check(restored.getKillEffectsSpeed() == 0.75F, "Скорость сохраняется");
        check(restored.getKillEffectsParticles() == 0.25F, "Частицы сохраняются");
        check(restored.getKillEffectsDistance() == 96, "Дальность сохраняется");
        check(restored.getKillEffectsQuality() == 2, "Качество сохраняется");
        check(restored.getKillEffectsLimit() == 7, "Лимит сохраняется");
        check(restored.getKillEffectsVolume() == 37, "Громкость сохраняется");

        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            config.setKillEffectsSize(invalid);
            config.setKillEffectsSpeed(invalid);
            config.setKillEffectsParticles(invalid);
            config.sanitize();
            check(config.getKillEffectsSize() == 1F, "Некорректный размер заменяется");
            check(config.getKillEffectsSpeed() == 1F, "Некорректная скорость заменяется");
            check(config.getKillEffectsParticles() == 0.65F, "Некорректная интенсивность заменяется");
        }
        config.setKillEffectsStyle(-1);
        config.setKillEffectsSize(-100F);
        config.setKillEffectsSpeed(100F);
        config.setKillEffectsParticles(100F);
        config.setKillEffectsDistance(-1);
        config.setKillEffectsQuality(100);
        config.setKillEffectsLimit(100);
        config.setKillEffectsVolume(1000);
        config.sanitize();
        check(config.getKillEffectsStyle() == 0, "Нижняя граница эффекта");
        check(config.getKillEffectsSize() == 0.5F, "Нижняя граница размера");
        check(config.getKillEffectsSpeed() == 2F, "Верхняя граница скорости");
        check(config.getKillEffectsParticles() == 1F, "Верхняя граница частиц");
        check(config.getKillEffectsDistance() == 16, "Нижняя граница дальности");
        check(config.getKillEffectsQuality() == 2, "Верхняя граница качества");
        check(config.getKillEffectsLimit() == 8, "Верхняя граница лимита");
        check(config.getKillEffectsVolume() == 100, "Верхняя граница громкости");
        config.setKillEffectsStyle(100);
        config.setKillEffectsSize(100F);
        config.setKillEffectsSpeed(-100F);
        config.setKillEffectsParticles(-100F);
        config.setKillEffectsDistance(1000);
        config.setKillEffectsQuality(-1);
        config.setKillEffectsLimit(-1);
        config.setKillEffectsVolume(-1);
        config.sanitize();
        check(config.getKillEffectsStyle() == 8, "Верхняя граница эффекта");
        check(config.getKillEffectsSize() == 2F, "Верхняя граница размера");
        check(config.getKillEffectsSpeed() == 0.5F, "Нижняя граница скорости");
        check(config.getKillEffectsParticles() == 0F, "Нижняя граница частиц");
        check(config.getKillEffectsDistance() == 128, "Верхняя граница дальности");
        check(config.getKillEffectsQuality() == 0, "Нижняя граница качества");
        check(config.getKillEffectsLimit() == 1, "Нижняя граница лимита");
        check(config.getKillEffectsVolume() == 0, "Отключение звука громкостью");
        System.out.println("Kill Effects config: " + checks + " checks passed");
    }

    private static void defaults(OrionConfig config) {
        check(!config.isKillEffectsEnabled(), "Эффекты выключены по умолчанию");
        check(config.getKillEffectsStyle() == 0, "Призрак по умолчанию");
        check(config.getKillEffectsSize() == 1F, "Размер по умолчанию");
        check(config.getKillEffectsSpeed() == 1F, "Скорость по умолчанию");
        check(config.getKillEffectsParticles() == 0.65F, "Частицы по умолчанию");
        check(config.getKillEffectsDistance() == 64, "Дальность по умолчанию");
        check(config.getKillEffectsQuality() == 1, "Качество по умолчанию");
        check(config.getKillEffectsLimit() == 4, "Лимит по умолчанию");
        check(config.getKillEffectsVolume() == 60, "Громкость по умолчанию");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
