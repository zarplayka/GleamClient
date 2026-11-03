import com.google.gson.Gson;
import ru.orion.client.config.OrionConfig;

/** Совместимость старых конфигов после удаления тотема и добавления фильтра целей. */
public final class OrionMenuConfigTest {
    public static void main(String[] args) {
        Gson gson = new Gson();
        OrionConfig defaults = OrionConfig.createDefault();
        check(defaults.getClickGuiTheme() == 3, "Paradox default");
        check(defaults.getKillEffectsTargets() == 0, "players default");
        OrionConfig old = gson.fromJson("{\"killEffectsStyle\":7,\"killEffectsTargets\":55,\"killEffectsPlayerLift\":-4}", OrionConfig.class);
        old.sanitize();
        check(old.getKillEffectsStyle() == 0, "removed totem migration");
        check(old.getKillEffectsTargets() == 2, "target bounds");
        check(old.getKillEffectsPlayerLift() == 0F, "height bounds");
        old.setKillEffectsStyle(8);
        old.setKillEffectsTargets(1);
        old.setKillEffectsPlayerLift(.75F);
        OrionConfig roundtrip = gson.fromJson(gson.toJson(old), OrionConfig.class);
        roundtrip.sanitize();
        check(roundtrip.getKillEffectsStyle() == 8, "dragon keeps stable id");
        check(roundtrip.getKillEffectsTargets() == 1 && roundtrip.getKillEffectsPlayerLift() == .75F, "settings roundtrip");
        OrionConfig legacy = gson.fromJson("{}", OrionConfig.class);
        legacy.sanitize();
        check(legacy.isHudElementGlass("hud_watermark"), "existing HUD defaults to glass");
        check(legacy.isWaypointEdgeIndicators() && legacy.isWaypointShowCoordinates() && legacy.isWaypointSmartLabels(), "waypoint display defaults");
        legacy.setHudElementGlass("hud_watermark", false);
        legacy.setHudElementGlass("inventory_hud", true);
        OrionConfig restored = gson.fromJson(gson.toJson(legacy), OrionConfig.class);
        restored.sanitize();
        check(!restored.isHudElementGlass("hud_watermark") && restored.isHudElementGlass("inventory_hud"), "independent HUD styles survive save");
        OrionConfig malformed = gson.fromJson("{\"hudElementGlass\":null,\"waypointVisibleLimit\":999}", OrionConfig.class);
        malformed.sanitize();
        check(malformed.isHudElementGlass("hud_watermark") && malformed.getWaypointVisibleLimit() == 64, "invalid display settings sanitized");
        System.out.println("Menu and KillEffect config: OK");
    }
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
}
