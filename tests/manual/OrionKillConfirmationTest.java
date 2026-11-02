package ru.orion.client.effect;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityTypes;
import ru.orion.client.effect.OrionKillEffects;

/** Ручная проверка на runtime classpath Minecraft, без запуска игрового окна. */
public final class OrionKillConfirmationTest {
    private static final UUID LOCAL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VICTIM = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static int checks;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Component victim = player("Противник", VICTIM);
        Component local = player("Игрок", LOCAL);
        Component foreign = player("Другой", OTHER);
        check("подтверждённое убийство", VICTIM, Component.translatable("death.attack.player", victim, local));
        check("убийство чужим игроком", null, Component.translatable("death.attack.player", victim, foreign));
        check("убийца без UUID", null, Component.translatable("death.attack.player", victim, Component.literal("Игрок")));
        check("жертва без UUID", null, Component.translatable("death.attack.player", Component.literal("Противник"), local));
        check("обычная строка", null, Component.literal("Противник был убит Игрок"));
        check("смерть от окружения", null, Component.translatable("death.attack.fall", victim));
        check("самоубийство", null, Component.translatable("death.attack.player", local, local));
        check("посторонний ключ перевода", null, Component.translatable("chat.type.text", victim, local));
        check("оружие с hover игрока", null, Component.translatable("death.attack.player.item", victim, foreign, local));
        Component nestedWeapon = Component.translatable("death.attack.player", victim, local);
        check("сообщение внутри оружия", null, Component.translatable("death.attack.player.item", victim, foreign, nestedWeapon));
        check("убийство оружием", VICTIM, Component.translatable("death.attack.player.item", victim, local, Component.literal("Меч")));
        check("падение после удара", VICTIM, Component.translatable("death.fell.assist", victim, local));
        check("падение с чужой помощью", null, Component.translatable("death.fell.assist.item", victim, foreign, local));
        check("серверный префикс", VICTIM, Component.literal("[Сервер] ")
                .append(Component.empty().append(Component.translatable("death.attack.player", victim, local))));
        check("пустое сообщение", null, Component.empty());
        long time = 1_000_000_000L;
        var evidence = new OrionKillEffects.DamageEvidence(42, 100, time);
        evidenceCheck("смерть сразу после серверного урона", true, evidence.matches(42, 100, time));
        evidenceCheck("смерть через два тика", true, evidence.matches(42, 102, time + 100_000_000L));
        evidenceCheck("граница времени", true, evidence.matches(42, 102, time + 500_000_000L));
        evidenceCheck("другая сущность после респавна", false, evidence.matches(43, 100, time));
        evidenceCheck("устаревший урон", false, evidence.matches(42, 103, time + 150_000_000L));
        evidenceCheck("долгая пауза клиента", false, evidence.matches(42, 100, time + 500_000_001L));
        evidenceCheck("смерть раньше урона", false, evidence.matches(42, 99, time));
        evidenceCheck("обратное время", false, evidence.matches(42, 100, time - 1));
        System.out.println("Kill confirmation checks passed: " + checks);
    }

    private static MutableComponent player(String name, UUID id) {
        HoverEvent hover = new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(EntityTypes.PLAYER, id, Component.literal(name)));
        return Component.literal(name).withStyle(style -> style.withHoverEvent(hover));
    }

    private static void check(String label, UUID expected, Component message) {
        UUID result = OrionKillEffects.confirmedVictim(message, LOCAL);
        if (!Objects.equals(expected, result)) throw new AssertionError(label + ": expected " + expected + ", got " + result);
        checks++;
    }

    private static void evidenceCheck(String label, boolean expected, boolean actual) {
        if (expected != actual) throw new AssertionError(label);
        checks++;
    }
}
