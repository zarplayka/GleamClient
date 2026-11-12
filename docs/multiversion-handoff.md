# Multiversion client handoff

The current source includes the local cosmetic pets and Stonecutter targets 26.2 and 26.3. `gradlew.bat build` builds both; target properties live in `versions/<version>/gradle.properties`. Active sources are 26.3. Java 25 and the existing Windows media-helper prerequisites are required.

Keep the compile-only Fabric Mixin 0.17.3 pin: it preserves the Redirect annotation representation required by Loader 0.19.3 / MixinExtras 0.5.4. Runtime libraries remain supplied by the launcher.

## Open issue: Mirage

The user still reports no Mirage effect in their actual 26.2 profile. This issue is **not resolved**, despite the isolated OpenGL fixture producing reflections.

The 26.2 hook in `GameRendererMotionBlurMixin` now runs after `LevelRenderer.render`, before the vanilla depth clear. The old hook before `renderItemInHand` received empty depth. The build audit checks this order.

The fixture used reflection strength 0.4, roughness 0, bloom/grade 0 and a colored wall above a stone floor. It measured 23079 reflection hits and 41158 changed pixels on both versions. Default reflection strength is 0.16 and roughness 0.55. These fixture results do not explain the user's remaining report.

Next investigation needs the actual installed JAR hash, latest.log, render backend, Mirage configuration and scene. A running local profile was found under `.aw/instances/builds/26.1.2`; its directory name alone does not establish its Minecraft version. These details were not inspected before the user requested the handoff.

Main implementation: `effect/OrionMirage.java`, `mixin/GameRendererMotionBlurMixin.java`, and `assets/orion-client/shaders/core/mirage_{reflections,composite}.fsh`. The existing Iris shaderpack detection intentionally skips the internal pass while an external shaderpack is active.

See `ChatGPT.md` for implementation history and the distinction between completed fixture checks and the unresolved user report. Local diagnostic probes/logs/screenshots are ignored build artifacts and are not part of this commit.
