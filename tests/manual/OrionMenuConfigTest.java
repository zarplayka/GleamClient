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
        System.out.println("Menu and KillEffect config: OK");
    }
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
}
