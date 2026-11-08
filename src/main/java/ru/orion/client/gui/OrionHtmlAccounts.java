package ru.orion.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionAccounts;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.List;

final class OrionHtmlAccounts {
    private static final int VISIBLE_ACCOUNTS = 4;
    private static final Identifier DEFAULT_AVATAR = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/cosmetics/orion_badge.png");

    private final OrionAccounts accounts = OrionAccounts.INSTANCE;
    private final Runnable refresh, openProfile, openConfigs, addAccount, logout, openSettings;
    private final Consumer<String> selectAccount;
    private final OrionLumenView view = new OrionLumenView("accounts.html", "lumen_accounts", null);
    private final Map<String, Avatar> avatars = new HashMap<>();
    private int avatarSerial, accountOffset;
    private boolean switching;
    private record Avatar(BufferedImage source, Identifier id) { }
    private long transitionAt;
    private int x, y, width, height, bottom;
    private int footerHeight, popupHeight;
    private float progress, transitionFrom, unit;
    private boolean pressed;
    private boolean open, copied;
    private List<?> lastState;

    OrionHtmlAccounts(Runnable refresh, Runnable openProfile, Runnable openConfigs, Runnable addAccount,
                      Consumer<String> selectAccount, Runnable logout, Runnable openSettings) {
        this.refresh = refresh;
        this.openProfile = openProfile;
        this.openConfigs = openConfigs;
        this.addAccount = addAccount;
        this.selectAccount = selectAccount;
        this.logout = logout;
        this.openSettings = openSettings;
    }

    private void toggle() {
        if (open) {
            close();
        } else {
            switching = false;
            animateTo(true);
            copied = false;
            refresh.run();
        }
    }

    boolean isOpen() { return open; }
    int top() { return y; }

    void close() {
        if (open) animateTo(false);
    }

    private void animateTo(boolean target) {
        transitionFrom = progress;
        transitionAt = System.nanoTime();
        open = target;
    }

    private void animate(long now) {
        float target = open ? 1F : 0F;
        if (progress == target) return;
        // Сохраняем текущую позицию при смене направления. Полный ход — 220 мс, как в Paradox.
        float duration = (open ? 220_000_000F : 160_000_000F) * Math.abs(target - transitionFrom);
        float time = Math.min(1F, (now - transitionAt) / duration);
        float eased = open ? OrionClickGuiScreen.accountEase(time) : 1F - OrionClickGuiScreen.accountEase(1F - time);
        progress = time == 1F ? target : transitionFrom + (target - transitionFrom) * eased;
    }

    void dispose() {
        open = pressed = false;
        progress = 0;
        lastState = null;
        var textures = Minecraft.getInstance().getTextureManager();
        for (Avatar avatar : avatars.values()) textures.release(avatar.id());
        avatars.clear();
        view.dispose();
    }

    boolean prepare(int left, int sidebar, int footer, float scale) {
        if (accounts.username().isEmpty()) {
            if (lastState != null) dispose();
            return false;
        }
        if (!view.ready()) return false;
        animate(System.nanoTime());
        x = left;
        width = sidebar;
        unit = Math.min(scale, width / 104F);
        bottom = footer;
        footerHeight = Math.max(1, Math.round(20F * unit));
        popupHeight = Math.round(popupUnits() * unit * progress);
        height = footerHeight + Math.max(1, Math.round(4F * unit)) + popupHeight;
        y = bottom - height;
        return true;
    }

    void render(GuiGraphicsExtractor graphics, double mouseX, double mouseY,
                Color panel, Color text, Color muted, Color border, Color hover) {
        int ratio = Minecraft.getInstance().getWindow().getGuiScale();
        view.resize(width, Math.max(1, (int) Math.ceil((popupUnits()+25F) * unit)), ratio);
        updatePage(panel, text, muted, border, hover);
        move(mouseX, mouseY);
        view.frame();
        if (view.draw(graphics, x, y, width, height, 1F, 1F)) drawAvatar(graphics);
        receive();
    }

    private void updatePage(Color panel, Color text, Color muted, Color border, Color hover) {
        var account = accounts.profile();
        boolean english = OrionGuiLanguage.english();
        String uid = account == null ? "" : copied ? (english ? "Copied" : "Скопировано") : "ID " + account.id();
        String plan = accounts.plusActive() ? "Plus" : "Free";
        List<String> names = switching ? visibleAccounts() : List.of();
        var state = List.of(width, height, unit, popupHeight, footerHeight, actionsEnabled(), english, switching, names,
                accounts.busy(), accounts.username(), uid, plan, panel, text, muted, border, hover);
        if (state.equals(lastState)) return;
        JsonObject json = new JsonObject();
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("unit", unit);
        json.addProperty("popupHeight", popupHeight);
        json.addProperty("footerY", height - footerHeight);
        json.addProperty("actions", actionsEnabled());
        json.addProperty("name", accounts.username());
        json.addProperty("uid", uid);
        json.addProperty("plan", plan);
        json.addProperty("profile", english ? "Profile" : "Профиль");
        json.addProperty("configs", english ? "My configs" : "Мои конфиги");
        json.addProperty("accounts", english ? "Accounts" : "Аккаунты");
        json.addProperty("switching", switching);
        json.addProperty("busy", accounts.busy());
        json.addProperty("add", english ? "Add" : "Добавить");
        json.addProperty("logout", english ? "Sign out" : "Выйти");
        var list = new JsonArray();
        for (String name : names) {
            var saved = new JsonObject();
            saved.addProperty("name",name);
            saved.addProperty("plan",accounts.plusActive(name) ? "Plus" : "Free");
            saved.addProperty("active",name.equals(accounts.username()));
            list.add(saved);
        }
        json.add("saved",list);

        json.addProperty("panel", hex(panel));
        json.addProperty("text", hex(text));
        json.addProperty("muted", hex(muted));
        json.addProperty("border", hex(border));
        json.addProperty("hover", hex(hover));
        view.update(json);
        lastState = state;
    }

    private boolean actionsEnabled() { return open && progress == 1F; }

    private void receive() {
        byte[] message = view.messages();
        if (message == null || message.length == 0 || message.length > 2048) return;
        var payload = new String(message, StandardCharsets.UTF_8);
        for (String line : payload.lines().toList()) {
            try {
                var event = JsonParser.parseString(line);
                if (event.isJsonArray()) {
                    for (var item : event.getAsJsonArray()) {
                        if (item.isJsonObject()) handle(item.getAsJsonObject());
                        else if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString())
                            handle(JsonParser.parseString(item.getAsString()).getAsJsonObject());
                    }
                } else if (event.isJsonObject()) handle(event.getAsJsonObject());
            } catch (RuntimeException invalid) {
                // Сообщения вне контракта страницы не обрабатываются.
            }
        }
    }

    private void handle(JsonObject event) {
        if (!event.has("action")) return;
        String action = event.get("action").getAsString();
        if ("toggle".equals(action)) {
            toggle();
            return;
        }
        if ("settings".equals(action)) {
            close();
            openSettings.run();
            return;
        }
        if (!actionsEnabled()) return;
        switch (action) {
            case "profile" -> { close(); openProfile.run(); }
            case "configs" -> { close(); openConfigs.run(); }
            case "accounts" -> {
                switching = true;
                accountOffset = 0;
                accounts.loadSavedProfiles();
            }
            case "back" -> switching = false;
            case "add" -> {
                if (!accounts.busy()) { close(); progress = 0; addAccount.run(); }
            }
            case "logout" -> { if (!accounts.busy()) logout.run(); }
            case "select" -> {
                if (switching && !accounts.busy() && event.has("name")) {
                    String name = event.get("name").getAsString();
                    if (!name.equals(accounts.username()) && visibleAccounts().contains(name)) {
                        selectAccount.accept(name);
                        switching = false;
                    }
                }
            }
            case "copy" -> {
                var account = accounts.profile();
                if (account != null && !account.id().isEmpty()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(account.id());
                    copied = true;
                }
            }
            default -> { }
        }
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < bottom;
    }

    void move(double mx, double my) {
        view.move((float) (mx - x), (float) (my - y));
    }

    void press(double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return;
        pressed = true;
        move(mx, my);
        view.press((float) (mx - x), (float) (my - y));
    }

    boolean release(double mx, double my, int button) {
        if (!pressed || button != InputConstants.MOUSE_BUTTON_LEFT) return false;
        pressed = false;
        move(mx, my);
        view.release((float) (mx - x), (float) (my - y));
        receive();
        return true;
    }

    private int popupUnits() {
        return switching ? Math.max(91, 50 + Math.min(VISIBLE_ACCOUNTS,accounts.usernames().size())*20) : 91;
    }

    private List<String> visibleAccounts() {
        var names = accounts.usernames();
        accountOffset = Math.clamp(accountOffset,0,Math.max(0,names.size()-VISIBLE_ACCOUNTS));
        return names.subList(accountOffset,Math.min(names.size(),accountOffset+VISIBLE_ACCOUNTS));
    }

    boolean scroll(double vertical) {
        if (switching && vertical != 0) {
            accountOffset = Math.clamp(accountOffset-(int)Math.signum(vertical),0,
                    Math.max(0,accounts.usernames().size()-VISIBLE_ACCOUNTS));
        }
        return true;
    }

    private void drawAvatar(GuiGraphicsExtractor graphics) {
        var names = accounts.usernames();
        var textures = Minecraft.getInstance().getTextureManager();
        avatars.entrySet().removeIf(entry -> {
            if (names.contains(entry.getKey())) return false;
            textures.release(entry.getValue().id());
            return true;
        });
        int footerSize = Math.max(1, Math.round(13F * unit));
        int footerX = x + Math.round(5F * unit), footerY = bottom - (footerHeight + footerSize) / 2;
        drawAvatar(graphics,accounts.username(),footerX,footerY,footerSize);
        if (switching) {
            var rows = visibleAccounts();
            for (int i=0; i<rows.size() && (40+i*20)*unit<=popupHeight; i++)
                drawAvatar(graphics,rows.get(i),x+Math.round(7*unit),y+Math.round((22+i*20)*unit),
                        Math.max(1,Math.round(12*unit)));
        } else if (popupHeight >= 30F * unit) {
            drawAvatar(graphics,accounts.username(),x+Math.round(8*unit),y+Math.round(6*unit),
                    Math.max(1,Math.round(20*unit)));
        }
    }

    void drawProfileAvatar(GuiGraphicsExtractor graphics, int x, int y, int size) {
        drawAvatar(graphics,accounts.username(),x,y,size);
    }

    private void drawAvatar(GuiGraphicsExtractor graphics, String name, int x, int y, int size) {
        BufferedImage image = accounts.avatar(name);
        Avatar cached = avatars.get(name);
        if (cached != null && cached.source() != image) {
            Minecraft.getInstance().getTextureManager().release(cached.id());
            avatars.remove(name);
            cached = null;
        }
        if (cached == null && image != null) {
            int crop = Math.min(image.getWidth(),image.getHeight());
            int arc = Math.max(1,Math.round(crop*.45F));
            var rounded = new BufferedImage(crop,crop,BufferedImage.TYPE_INT_ARGB);
            var g = rounded.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.fill(new RoundRectangle2D.Float(0,0,crop,crop,arc,arc));
                g.setComposite(AlphaComposite.SrcIn);
                g.drawImage(image,(crop-image.getWidth())/2,(crop-image.getHeight())/2,null);
            } finally {
                g.dispose();
            }
            Identifier id = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID,"lumen_accounts_avatar_"+avatarSerial++);
            OrionLumenView.registerTexture(id,rounded);
            cached = new Avatar(image,id);
            avatars.put(name,cached);
        }
        graphics.blit(cached == null ? DEFAULT_AVATAR : cached.id(),x,y,x+size,y+size,0F,1F,0F,1F);
    }

    private static String hex(Color color) { return OrionLumenView.hex(color); }
}
