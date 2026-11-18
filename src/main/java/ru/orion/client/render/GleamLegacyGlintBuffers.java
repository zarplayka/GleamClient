//? if <26.1 {
/*package ru.orion.client.render;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
// Буферы живут вместе с постоянными BufferSource клиента и освобождаются при остановке.
public final class GleamLegacyGlintBuffers {
    private static final Set<ByteBufferBuilder> OWNED = Collections.newSetFromMap(new IdentityHashMap<>());
    static {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            OWNED.forEach(ByteBufferBuilder::close);
            OWNED.clear();
        });
    }
    private GleamLegacyGlintBuffers() {}
    public static ByteBufferBuilder create(int size) {
        var buffer = new ByteBufferBuilder(size);
        OWNED.add(buffer);
        return buffer;
    }
}
*///?}
