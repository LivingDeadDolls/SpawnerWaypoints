package spawnerwaypoints.locate;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import seedengine.api.Dungeon;
import seedengine.api.FoundStructure;
import seedengine.api.SeedEngineApi;
import seedengine.api.StructureSpawner;
import seedengine.api.WorldCheck;

/**
 * Which generation to ask Seed Engine API about: singleplayer's own server (with its world type and data packs), or
 * elsewhere the vanilla data for the seed and the world type Seed Engine API works out from the terrain. The calls
 * work on the calling thread, as the API's do.
 *
 * @param worldType the world preset for the vanilla data; null with a server level
 * @param level     the integrated server's level; null for the vanilla data
 */
public record WorldSource(ResourceKey<Level> dimension, long seed, Identifier worldType, ServerLevel level) {
    /**
     * The source for the client's world, on the client thread. Empty while the world type is not known yet, and when
     * the seed and world type do not fit the terrain (a wrong seed, generation the vanilla data does not have): the
     * spawners worked out would not be where the world has them.
     */
    public static Optional<WorldSource> of(Minecraft mc, ResourceKey<Level> dimension, long seed) {
        IntegratedServer server = mc.getSingleplayerServer();
        ServerLevel level = server != null ? server.getLevel(dimension) : null;
        if (level != null && level.getSeed() == seed) return Optional.of(new WorldSource(dimension, seed, null, level));
        if (SeedEngineApi.currentWorldCheck() == WorldCheck.MISMATCH) return Optional.empty();
        return SeedEngineApi.currentWorldType().map(worldType -> new WorldSource(dimension, seed, worldType, null));
    }

    /** What the answers come from, telling sources apart. */
    public String describe() {
        return level != null ? "integrated server" : "vanilla data, " + worldType;
    }

    public List<Dungeon> dungeons(ChunkPos from, ChunkPos to) {
        return level != null ? SeedEngineApi.dungeons(level, from, to) : SeedEngineApi.dungeons(seed, worldType, dimension, from, to);
    }

    public List<FoundStructure> structures(ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        return level != null ? SeedEngineApi.structures(level, from, to, sets)
                : SeedEngineApi.structures(seed, worldType, dimension, from, to, sets);
    }

    public List<StructureSpawner> possibleSpawners(FoundStructure structure) {
        return level != null ? SeedEngineApi.possibleSpawners(level, structure)
                : SeedEngineApi.possibleSpawners(seed, worldType, dimension, structure);
    }
}
