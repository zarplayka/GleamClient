package ru.orion.client.cosmetic;

import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;

import java.util.Set;

/** Питомцы используют отдельный слот косметики. */
public enum GleamPet {
    EMBER_FOX("ember_fox", true, .21D, .38D),
    MOON_MOTH("moon_moth", false, .21D, .38D),
    MOSS_TURTLE("moss_turtle", true, .10D, .23D),
    TARNKAPPE("rhm_tarnkappe", true, .17D, .31D),
    SNOW_FERRET("snow_ferret", true, .23D, .40D),
    SPACE_SLIME("space_slime", true, .17D, .30D),
    TRACKED_SCOUT("tracked_scout", true, .19D, .34D);

    private static final GleamPet[] VALUES = values();
    private final String id;
    private final boolean grounded;
    private final Identifier texture;
    private final double walkSpeed, catchUpSpeed;

    GleamPet(String id, boolean grounded, double walkSpeed, double catchUpSpeed) {
        this.id = id;
        this.grounded = grounded;
        this.walkSpeed = walkSpeed;
        this.catchUpSpeed = catchUpSpeed;
        this.texture = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "textures/cosmetics/pets/" + id + ".png");
    }

    public String id() { return id; }
    boolean grounded() { return grounded; }
    Identifier texture() { return texture; }
    double walkSpeed() { return walkSpeed; }
    double catchUpSpeed() { return catchUpSpeed; }

    public static GleamPet byId(String id) {
        for (GleamPet pet : VALUES) {
            if (pet.id.equals(id)) return pet;
        }
        return null;
    }

    public static GleamPet in(Set<String> items) {
        for (GleamPet pet : VALUES) {
            if (items.contains(pet.id)) return pet;
        }
        return null;
    }
}
