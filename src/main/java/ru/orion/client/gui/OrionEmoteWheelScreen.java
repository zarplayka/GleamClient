package ru.orion.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.orion.client.GleamMinecraft;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.effect.OrionEmotes;
import ru.orion.client.keybind.OrionKeybinds;

/** Shared hit-testing for the HTML view, hotkeys and the Minecraft fallback. */
public final class OrionEmoteWheelScreen extends Screen {
    private static final int WIDTH = 392, HEIGHT = 420;
    private static final int CENTER_X = WIDTH / 2, CENTER_Y = 196;
    private static final int RADIUS = 110, INNER = 74, OUTER = 146;
    private static final int CONTROL_Y = 354;
    private static final int OPTION_TOP = 117, OPTION_STEP = 36, PICKER_CONTROLS = 310;
    private static final long OPEN_NANOS = 210_000_000L;
    private static final String[] LABELS = {"Эмоции", "Остановить", "Изменить круг", "Готово", "Сбросить",
            "Выбери слот", "Выбери эмоцию", "Пусто", "Назад", "Отпусти клавишу или нажми 1–6"};
    private final OrionLumenView view = new OrionLumenView("emote-wheel.html", "emote_wheel",
            "/assets/orion-client/fonts/paradox/ParadoxSans-Regular.ttf");
    private final long openedAt = System.nanoTime();
    private JsonObject previous;
    private boolean editing, introComplete;
    private int slotToEdit = -1;

    public OrionEmoteWheelScreen() { super(Component.literal("GleamClient Emotes")); }

    @Override public boolean isPauseScreen() { return false; }

    private float scale() { return Math.max(.01F, Math.min(1F, Math.min((width - 8F) / WIDTH, (height - 8F) / HEIGHT))); }
    private int left() { return Math.round((width - WIDTH * scale()) * .5F); }
    private int top() { return Math.round((height - HEIGHT * scale()) * .5F); }
    private double pageX(double x) { return (x - left()) / scale(); }
    private double pageY(double y) { return (y - top()) / scale(); }
    private float opening() { return Math.clamp((System.nanoTime() - openedAt) / (float) OPEN_NANOS, 0F, 1F); }
    private float ease() { return 1F - (float) Math.pow(1F - opening(), 3); }
    private float zoom() { return .92F + .08F * ease(); }

    static int sector(double x, double y) {
        double radius = Math.hypot(x, y);
        if (radius > OUTER || radius < INNER) return -1;
        double angle = Math.toDegrees(Math.atan2(x, -y)) + 30;
        return Math.floorMod((int) Math.floor(angle / 60), 6);
    }

    private static int slotX(int slot) { return CENTER_X + (int) Math.round(Math.sin(slot * Math.PI / 3) * RADIUS); }
    private static int slotY(int slot) { return CENTER_Y - (int) Math.round(Math.cos(slot * Math.PI / 3) * RADIUS); }

    private double wheelX(double x) { return CENTER_X + (pageX(x) - CENTER_X) / zoom(); }
    private double wheelY(double y) { return CENTER_Y + (pageY(y) - CENTER_Y) / zoom(); }
    private int slotAt(double x, double y) {
        double px = wheelX(x), py = wheelY(y);
        return sector(px - CENTER_X, py - CENTER_Y);
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, Math.round(76F * ease()) << 24 | 0x0b1217);
    }

    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    //?} else {
    /*public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    *///?}
        super.extractRenderState(context, mouseX, mouseY, delta);
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isEmotesEnabled() || !OrionEmotes.canDance(minecraft.player)) {
            if (GleamMinecraft.screen(minecraft) == this) onClose();
            return;
        }
        int active = slotToEdit == -1 ? slotAt(mouseX, mouseY) : -1;
        int[] slots = config.getEmoteSlots();
        if (view.ready()) {
            float scale = scale();
            view.resize(WIDTH, HEIGHT, Math.max(.5F, scale * minecraft.getWindow().getGuiScale()));
            JsonObject state = state(config, active, slots, wheelX(mouseX), wheelY(mouseY));
            if (!state.equals(previous)) {
                view.update(state);
                previous = state;
            }
            if (!introComplete) {
                float progress = opening();
                view.opening(progress);
                introComplete = progress >= 1F;
            }
            view.frame();
            if (view.draw(context, left(), top(), Math.round(WIDTH * scale), Math.round(HEIGHT * scale), scale, scale)) return;
        }
        fallback(context, active, slots);
    }

    private JsonObject state(OrionConfig config, int active, int[] slots, double x, double y) {
        JsonObject state = new JsonObject();
        var labels = new JsonArray();
        for (String text : LABELS) labels.add(OrionGuiLanguage.translate(text));
        state.add("labels", labels);
        var names = new JsonArray();
        names.add(OrionGuiLanguage.translate(LABELS[7]));
        for (String name : OrionEmotes.NAMES) names.add(OrionGuiLanguage.translate(name));
        state.add("names", names);
        var buttons = new JsonArray();
        for (int slot = 0; slot < slots.length; slot++) {
            JsonObject button = new JsonObject();
            button.addProperty("x", slotX(slot));
            button.addProperty("y", slotY(slot));
            button.addProperty("id", slots[slot]);
            buttons.add(button);
        }
        state.add("slots", buttons);
        state.addProperty("active", active);
        state.addProperty("light", config.getClickGuiTheme() == 4);
        state.addProperty("accent", config.getClickGuiAccent());
        state.addProperty("editing", editing);
        state.addProperty("slotToEdit", slotToEdit);
        state.addProperty("option", slotToEdit < 0 ? 0 : optionAt(x, y));
        String control = "";
        if (slotToEdit >= 0 && y >= PICKER_CONTROLS && y < PICKER_CONTROLS + 28) {
            if (x >= 66 && x < 192) control = "clear";
            if (x >= 200 && x < 326) control = "back";
        } else if (slotToEdit < 0) {
            if (primaryControl(x, y)) control = editing ? "done" : "edit";
            else if (resetControl(x, y)) control = "reset";
        }
        state.addProperty("control", control);
        return state;
    }

    private void choose(int slot) {
        if (slot >= 0) {
            var config = OrionClient.getConfigOrNull();
            if (config != null && config.isEmotesEnabled()) OrionEmotes.start(config.getEmoteSlots()[slot]);
        }
        onClose();
    }

    private void selectSlot(int slot) {
        OrionClickGuiScreen.playInterfaceSound(minecraft, "click");
        if (editing) slotToEdit = slot;
        else choose(slot);
    }

    private void assign(int id) {
        OrionClickGuiScreen.playInterfaceSound(minecraft, "click");
        var manager = OrionClient.getInstance().getConfigManager();
        manager.getConfig().setEmoteSlot(slotToEdit, id);
        manager.save();
        slotToEdit = -1;
    }

    private void toggleEditor() {
        OrionClickGuiScreen.playInterfaceSound(minecraft, "click");
        editing = !editing;
        slotToEdit = -1;
    }

    private boolean primaryControl(double x, double y) {
        return y >= CONTROL_Y && y < CONTROL_Y + 28 && x >= (editing ? 216 : 117) && x < (editing ? 348 : 275);
    }

    private boolean resetControl(double x, double y) {
        return editing && x >= 44 && x < 176 && y >= CONTROL_Y && y < CONTROL_Y + 28;
    }

    private static int optionAt(double x, double y) {
        for (int id = 1; id <= OrionEmotes.NAMES.length; id++) {
            int col = (id - 1) % 2, row = (id - 1) / 2;
            int left = 66 + col * 134, top = OPTION_TOP + row * OPTION_STEP;
            if (x >= left && x < left + 126 && y >= top && y < top + 30) return id;
        }
        return 0;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            back();
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double x = wheelX(event.x()), y = wheelY(event.y());
        if (slotToEdit >= 0) {
            int id = optionAt(x, y);
            if (id > 0) assign(id);
            else if (y >= PICKER_CONTROLS && y < PICKER_CONTROLS + 28 && x >= 66 && x < 192) assign(0);
            else if (y >= PICKER_CONTROLS && y < PICKER_CONTROLS + 28 && x >= 200 && x < 326) slotToEdit = -1;
        } else if (primaryControl(x, y)) {
            toggleEditor();
        } else if (resetControl(x, y)) {
            var manager = OrionClient.getInstance().getConfigManager();
            manager.getConfig().resetEmoteSlots();
            manager.save();
        } else if (!editing && Math.hypot(x - CENTER_X, y - CENTER_Y) < INNER) {
            OrionEmotes.stop();
            onClose();
        } else {
            int slot = slotAt(event.x(), event.y());
            if (slot >= 0) selectSlot(slot);
            else if (!editing) onClose();
        }
        return true;
    }

    private void back() {
        if (slotToEdit >= 0) slotToEdit = -1;
        else if (editing) toggleEditor();
        else onClose();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) { back(); return true; }
        if (key == InputConstants.KEY_TAB) { toggleEditor(); return true; }
        if (key >= InputConstants.KEY_1 && key <= (slotToEdit >= 0 ? InputConstants.KEY_9 : InputConstants.KEY_6)) {
            if (slotToEdit >= 0) assign(key - InputConstants.KEY_1 + 1);
            else selectSlot(key - InputConstants.KEY_1);
            return true;
        }
        if (key == InputConstants.KEY_SPACE && !editing) { OrionEmotes.stop(); onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override public boolean keyReleased(KeyEvent event) {
        var key = OrionKeybinds.getEmoteKey();
        if (key != null && key.matches(event)) {
            if (!editing) {
                var window = minecraft.getWindow();
                double pointerX = minecraft.mouseHandler.getScaledXPos(window);
                double pointerY = minecraft.mouseHandler.getScaledYPos(window);
                if (primaryControl(wheelX(pointerX), wheelY(pointerY))) toggleEditor();
                else {
                    int slot = slotAt(pointerX, pointerY);
                    if (slot >= 0) selectSlot(slot);
                    else onClose();
                }
            }
            return true;
        }
        return super.keyReleased(event);
    }

    private void fallback(GuiGraphicsExtractor context, int active, int[] slots) {
        if (slotToEdit >= 0) {
            label(context, LABELS[6], CENTER_X, 96);
            for (int i = 0; i < OrionEmotes.NAMES.length; i++) {
                label(context, (i + 1) + "  " + OrionGuiLanguage.translate(OrionEmotes.NAMES[i]),
                        129 + i % 2 * 134, OPTION_TOP + 15 + i / 2 * OPTION_STEP);
            }
            label(context, LABELS[7], 129, PICKER_CONTROLS + 14);
            label(context, LABELS[8], 263, PICKER_CONTROLS + 14);
            return;
        }
        for (int slot = 0; slot < slots.length; slot++) {
            int id = slots[slot];
            String name = id == 0 ? LABELS[7] : OrionEmotes.NAMES[id - 1];
            label(context, (slot == active ? "> " : "") + (slot + 1) + "  " + OrionGuiLanguage.translate(name),
                    slotX(slot), slotY(slot));
        }
        label(context, editing ? LABELS[5] : LABELS[1], CENTER_X, CENTER_Y);
        label(context, editing ? LABELS[3] : LABELS[2], editing ? 282 : CENTER_X, CONTROL_Y + 14);
        if (editing) label(context, LABELS[4], 110, CONTROL_Y + 14);
    }

    private void label(GuiGraphicsExtractor context, String text, int center, int middle) {
        String label = OrionGuiLanguage.translate(text);
        int x = left() + Math.round((CENTER_X + (center - CENTER_X) * zoom()) * scale()) - font.width(label) / 2;
        int y = top() + Math.round((CENTER_Y + (middle - CENTER_Y) * zoom()) * scale()) - 4;
        int color = Math.max(4, Math.round(ease() * 255)) << 24 | 0x00F4F5F8;
        //? if >=26.1 {
        context.text(font, label, x, y, color);
        //?} else {
        /*context.drawString(font, label, x, y, color);
        *///?}
    }

    @Override public void removed() {
        view.dispose();
        previous = null;
        super.removed();
    }
}
