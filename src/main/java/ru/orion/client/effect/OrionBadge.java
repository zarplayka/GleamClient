package ru.orion.client.effect;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;

/** Значок для предварительного отображения у локального игрока. */
public final class OrionBadge {
    private static final FontDescription FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "orion_badge"));

    private OrionBadge() { }

    public static Component prepend(Component name) {
        return Component.empty()
                .append(Component.literal("\uE000").withStyle(style -> style.withFont(FONT)))
                .append(Component.literal(" "))
                .append(name);
    }
}
