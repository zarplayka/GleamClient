package ru.orion.client.gui;

import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/** Реальные начертания темы; размер шрифта задаётся в координатах её макета. */
public final class OrionInterfaceFonts {
    private static final Font[] FACES = load();
    private static final ConcurrentHashMap<Long, Font> CACHE = new ConcurrentHashMap<>();
    private OrionInterfaceFonts() { }

    private static Font[] load() {
        String[] names = {"Regular", "Medium", "SemiBold", "Bold"};
        Font[] fonts = new Font[names.length];
        for (int i = 0; i < names.length; i++) {
            String path = "/assets/orion-client/fonts/paradox/ParadoxSans-" + names[i] + ".ttf";
            try (InputStream input = OrionInterfaceFonts.class.getResourceAsStream(path)) {
                if (input == null) throw new IOException("Отсутствует шрифт " + path);
                fonts[i] = Font.createFont(Font.TRUETYPE_FONT, input);
                if (!fonts[i].canDisplay('Я') || !fonts[i].canDisplay('ё'))
                    throw new IOException("В шрифте нет кириллицы: " + path);
            } catch (java.awt.FontFormatException | IOException exception) {
                throw new IllegalStateException("Не удалось загрузить шрифт интерфейса", exception);
            }
        }
        return fonts;
    }

    public static Font face(int face, float size) {
        long key = ((long) face << 32) | (Float.floatToIntBits(size) & 0xffffffffL);
        return CACHE.computeIfAbsent(key, ignored -> FACES[face].deriveFont(Font.PLAIN, size));
    }
    public static Font css(int face, float pixels) { return face(face, pixels * 660F / 1240F); }
}
