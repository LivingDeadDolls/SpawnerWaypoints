package spawnerwaypoints.spawner;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import seedengine.api.Spawner;
import spawnerwaypoints.Constants;

/** The group (the mob) of a spawner and the names shown for it. */
public final class SpawnerNames {
    /** The group of a spawner whose mob is not known (one whose entity id could not be read). */
    public static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "unknown");
    /** The group of every trial spawner, whichever mob its configuration names (a trial spawner seen in the world has none: it is not sent). */
    public static final Identifier TRIAL = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "trial");
    /** The mobs of the spawners world generation places, and the trial spawners: dungeons, mineshafts, strongholds, nether fortresses. */
    private static final List<Identifier> POSSIBLE = List.of(Identifier.withDefaultNamespace("zombie"), Identifier.withDefaultNamespace("skeleton"),
            Identifier.withDefaultNamespace("spider"), Identifier.withDefaultNamespace("cave_spider"), Identifier.withDefaultNamespace("silverfish"),
            Identifier.withDefaultNamespace("blaze"), TRIAL);

    private SpawnerNames() {
    }

    /** Every group a spawner can have, whether or not one has been found: listed in the settings and the list's filter. */
    public static List<Identifier> possibleGroups() {
        return POSSIBLE;
    }

    /** The group of a spawner: the trial spawners, or the mob a spawner spawns. */
    public static Identifier group(Spawner spawner) {
        if (spawner.trial()) return TRIAL;
        return spawner.mob().orElse(UNKNOWN);
    }

    /**
     * The mob of a trial spawner's configuration ({@code minecraft:trial_chamber/small_melee/slime}): the last part of
     * its path that names an entity type.
     */
    public static Optional<Identifier> trialMob(Identifier config) {
        String[] parts = config.getPath().split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            Identifier part = Identifier.tryBuild(config.getNamespace(), parts[i]);
            if (part != null && BuiltInRegistries.ENTITY_TYPE.containsKey(part)) return Optional.of(part);
        }
        return Optional.empty();
    }

    /** A group's name: the mob's name, or the trial spawners'. */
    public static Component groupName(Identifier group) {
        if (group.equals(UNKNOWN)) return Component.translatable("spawnerwaypoints.group.unknown");
        if (group.equals(TRIAL)) return Component.translatable("spawnerwaypoints.group.trial");
        return BuiltInRegistries.ENTITY_TYPE.getOptional(group).map(EntityType::getDescription)
                .orElseGet(() -> Component.literal(group.getPath()));
    }

    /** A group's item in Waypoints API's list and on JourneyMap's marks: the mob's spawn egg, a trial spawner; null for none. */
    public static Item icon(Identifier group) {
        if (group.equals(TRIAL)) return Items.TRIAL_SPAWNER;
        // By its id: finding the egg by its components needs them bound, which they are not until the game has loaded its data.
        return BuiltInRegistries.ITEM.getOptional(group.withSuffix("_spawn_egg")).orElse(null);
    }

    /**
     * The letter in a group's diamond: {@code spawnerwaypoints.initial.<namespace>.<path>} from the language files,
     * given where the first letters of two groups' names would be the same; null for the others, which get the first
     * letter of their names.
     */
    public static String initial(Identifier group) {
        String key = "spawnerwaypoints.initial." + group.getNamespace() + "." + group.getPath();
        Language language = Language.getInstance();
        return language.has(key) ? language.getOrDefault(key) : null;
    }

    /**
     * A waypoint's name: "Zombie Spawner", "Breeze Trial Spawner" (just "Trial Spawner" when its configuration names no
     * mob), marked when it may be elsewhere instead.
     */
    public static Component placeName(Spawner spawner, boolean certain) {
        Component name;
        if (!spawner.trial()) {
            name = Component.translatable("spawnerwaypoints.place", groupName(group(spawner)));
        } else {
            name = spawner.mob().flatMap(SpawnerNames::trialMob)
                    .map(mob -> Component.translatable("spawnerwaypoints.place.trial", groupName(mob)))
                    .orElseGet(() -> Component.translatable("spawnerwaypoints.place.trial.unknown"));
        }
        return certain ? name : Component.translatable("spawnerwaypoints.place.uncertain", name);
    }
}
