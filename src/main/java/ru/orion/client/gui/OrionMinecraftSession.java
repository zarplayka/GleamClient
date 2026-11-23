package ru.orion.client.gui;

import net.minecraft.client.User;

/** Replaces session-owned services together while the client is disconnected. */
public interface OrionMinecraftSession {
    boolean gleam$useSession(User user, boolean offline);
}
