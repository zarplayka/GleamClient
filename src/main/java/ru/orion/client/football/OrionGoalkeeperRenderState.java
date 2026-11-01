package ru.orion.client.football;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Снимок позы вратаря для отложенного рендера. */
public final class OrionGoalkeeperRenderState extends AvatarRenderState {
    public OrionGoalkeeperAI.Action goalkeeperAction;
    public float keeperHeadYaw;
    public float keeperGait;
    public boolean keeperHandsHigh;
}
