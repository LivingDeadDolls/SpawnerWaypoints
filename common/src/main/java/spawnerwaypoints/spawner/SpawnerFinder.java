package spawnerwaypoints.spawner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import seedengine.api.Dungeon;
import seedengine.api.FoundStructure;
import seedengine.api.Spawner;
import seedengine.api.StructureSpawner;
import spawnerwaypoints.Constants;
import spawnerwaypoints.SpawnerWaypointsPlugin;
import spawnerwaypoints.config.SpawnerWaypointsConfig;
import spawnerwaypoints.locate.WorldSource;
import waypointsapi.api.Place;
import waypointsapi.api.WaypointKind;
import waypointsapi.api.WaypointScope;

/**
 * Works out the spawners around the player with Seed Engine API on a thread of its own and gives them to the current
 * {@link WaypointScope}: the dungeons in growing squares of chunks around the player (nearest first, the order the
 * world most likely generated them in), then the spawners of the structures that place them. In loaded chunks the
 * world counts instead ({@link LoadedCheck}). What was worked out is kept while the server, seed and dimension stay.
 */
public final class SpawnerFinder {
    /** The vanilla structure sets whose structures place spawners. */
    public static final Set<Identifier> SPAWNER_SETS = Set.of(Identifier.withDefaultNamespace("mineshafts"),
            Identifier.withDefaultNamespace("strongholds"), Identifier.withDefaultNamespace("nether_complexes"),
            Identifier.withDefaultNamespace("trial_chambers"));
    /** How long a job works before it looks again at where the player is (nanoseconds). */
    private static final long JOB_NANOS = 250_000_000L;
    /** The squares are worked out around a new middle once the player is this many chunks from the old one. */
    private static final int RECENTER_CHUNKS = 4;
    private static final int LOADED_CHECK_TICKS = 20;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Spawner Waypoints");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    /** A spawner shown, and whether it is there whichever order the world generated the chunks in. */
    public record Known(Spawner spawner, boolean certain) {
    }

    private static volatile Session session;
    private static int ticks;

    private SpawnerFinder() {
    }

    /** Client tick: follows the scope, the dimension and the player, and checks the loaded chunks now and then. */
    public static void tick(Minecraft mc) {
        Optional<WaypointScope> scope = SpawnerWaypointsPlugin.kind().flatMap(WaypointKind::scope);
        SpawnerWaypointsConfig config = SpawnerWaypointsConfig.get();
        if (!config.enabled || mc.level == null || mc.player == null || scope.isEmpty()) {
            session = null;
            return;
        }
        ResourceKey<Level> dimension = mc.level.dimension();
        // Until the world type is known (or when the seed does not fit the terrain), only the loaded chunks count.
        WorldSource source = WorldSource.of(mc, dimension, scope.get().seed()).orElse(null);
        String key = scope.get().server() + "|" + scope.get().seed() + "|" + dimension.identifier() + "|"
                + (source == null ? "none" : source.describe());
        Session current = session;
        if (current == null || !current.key.equals(key)) {
            current = new Session(key, scope.get(), dimension, source);
            session = current;
        }
        current.want(mc.player.chunkPosition(), config.dungeonRadius, config.structureRadius);
        if (++ticks >= LOADED_CHECK_TICKS) {
            ticks = 0;
            current.checkLoaded(mc.level, mc.player.chunkPosition(), mc.options.getEffectiveRenderDistance());
        }
        current.schedule();
    }

    /** The spawners shown in the current dimension: those seen in loaded chunks, and those worked out elsewhere. */
    public static List<Known> shown() {
        Session current = session;
        return current == null ? List.of() : current.shown();
    }

    /** Whether everything around the player is worked out. */
    public static boolean idle() {
        Session current = session;
        return current != null && current.done && !current.running;
    }

    /** The id of the waypoint at a position: unique within the kind and the dimension, and the same on every visit. */
    public static String placeId(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static final class Session {
        final String key;
        final WaypointScope scope;
        final ResourceKey<Level> dimension;
        /** Null while nothing is worked out (see {@link WorldSource#of}). */
        final WorldSource source;
        /** Worked out, by position, as last given to the scope. */
        final Map<BlockPos, Known> workedOut = new ConcurrentHashMap<>();
        /** Seen in loaded chunks: these win over what was worked out. */
        final Map<BlockPos, Spawner> seen = new ConcurrentHashMap<>();
        /** Worked out, but found missing in a loaded chunk. */
        final Set<BlockPos> absent = ConcurrentHashMap.newKeySet();
        volatile ChunkPos center;
        volatile int dungeonRadius;
        volatile int structureRadius;
        volatile boolean running;
        volatile boolean done;
        // Used by the worker thread only.
        ChunkPos dungeonCenter;
        int dungeonRing = -1;
        final Map<Long, List<BlockPos>> dungeonsByChunk = new HashMap<>();
        ChunkPos structureCenter;
        int structureRadiusDone = -1;
        final Deque<FoundStructure> structures = new ArrayDeque<>();
        final Set<String> structuresDone = new HashSet<>();

        Session(String key, WaypointScope scope, ResourceKey<Level> dimension, WorldSource source) {
            this.key = key;
            this.scope = scope;
            this.dimension = dimension;
            this.source = source;
        }

        void want(ChunkPos at, int dungeonRadius, int structureRadius) {
            ChunkPos was = center;
            boolean moved = was == null || Math.max(Math.abs(at.x() - was.x()), Math.abs(at.z() - was.z())) > RECENTER_CHUNKS;
            if (moved || dungeonRadius != this.dungeonRadius || structureRadius != this.structureRadius) {
                if (moved) center = at;
                this.dungeonRadius = dungeonRadius;
                this.structureRadius = structureRadius;
                done = false;
            }
        }

        void schedule() {
            if (source != null && !running && !done) {
                running = true;
                WORKER.execute(this::work);
            }
        }

        private boolean stale(ChunkPos at, int dungeons, int structures) {
            return session != this || !at.equals(center) || dungeons != dungeonRadius || structures != structureRadius;
        }

        private void work() {
            ChunkPos at = center;
            int dungeons = dungeonRadius;
            int structureRange = structureRadius;
            long begin = System.nanoTime();
            try {
                if (!at.equals(dungeonCenter)) {
                    dungeonCenter = at;
                    dungeonRing = -1;
                }
                while (dungeonRing < dungeons) {
                    if (stale(at, dungeons, structureRange) || System.nanoTime() - begin > JOB_NANOS) return;
                    dungeonSquare(at, dungeonRing + 1);
                    dungeonRing++;
                }
                if (!at.equals(structureCenter) || structureRange != structureRadiusDone) {
                    structureCenter = at;
                    structureRadiusDone = structureRange;
                    structures.clear();
                    List<FoundStructure> found = new ArrayList<>(source.structures(
                            new ChunkPos(at.x() - structureRange, at.z() - structureRange),
                            new ChunkPos(at.x() + structureRange, at.z() + structureRange), SPAWNER_SETS::contains));
                    found.sort(Comparator.comparingInt(structure -> distance(structure.chunk(), at)));
                    for (FoundStructure structure : found) {
                        if (structuresDone.add(structure.set() + "@" + structure.chunk().x() + "," + structure.chunk().z())) structures.add(structure);
                    }
                }
                while (!structures.isEmpty()) {
                    if (stale(at, dungeons, structureRange) || System.nanoTime() - begin > JOB_NANOS) return;
                    for (StructureSpawner spawner : source.possibleSpawners(structures.poll())) putWorkedOut(spawner.spawner(), spawner.certain());
                }
                if (!stale(at, dungeons, structureRange)) done = true;
            } catch (RuntimeException | LinkageError e) {
                Constants.LOG.error("Could not work out the spawners around {}", at, e);
                done = true;
            } finally {
                running = false;
            }
        }

        /**
         * The dungeons of a square: they replace those found before in its chunks, since decorating the chunks next to
         * a dungeon may take it away.
         */
        private void dungeonSquare(ChunkPos at, int ring) {
            Map<Long, List<Dungeon>> byChunk = new HashMap<>();
            for (Dungeon dungeon : source.dungeons(new ChunkPos(at.x() - ring, at.z() - ring), new ChunkPos(at.x() + ring, at.z() + ring))) {
                BlockPos pos = dungeon.spawner().pos();
                byChunk.computeIfAbsent(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4), chunk -> new ArrayList<>()).add(dungeon);
            }
            for (int z = at.z() - ring; z <= at.z() + ring; z++) {
                for (int x = at.x() - ring; x <= at.x() + ring; x++) {
                    long chunk = ChunkPos.pack(x, z);
                    List<Dungeon> now = byChunk.getOrDefault(chunk, List.of());
                    List<BlockPos> positions = now.stream().map(dungeon -> dungeon.spawner().pos()).toList();
                    for (BlockPos before : dungeonsByChunk.getOrDefault(chunk, List.of())) {
                        if (!positions.contains(before)) dropWorkedOut(before);
                    }
                    for (Dungeon dungeon : now) putWorkedOut(dungeon.spawner(), dungeon.certain());
                    dungeonsByChunk.put(chunk, positions);
                }
            }
        }

        private void putWorkedOut(Spawner spawner, boolean certain) {
            if (session != this) return;
            Known known = new Known(spawner, certain);
            if (known.equals(workedOut.put(spawner.pos(), known))) return;
            // In a loaded chunk the world has decided already.
            if (absent.contains(spawner.pos()) || seen.containsKey(spawner.pos())) return;
            scope.put(place(spawner, certain));
        }

        private void dropWorkedOut(BlockPos pos) {
            if (workedOut.remove(pos) != null && !seen.containsKey(pos)) scope.remove(dimension, placeId(pos));
        }

        /** Client thread: puts what is shown right with the spawners in the loaded chunks around the player. */
        void checkLoaded(ClientLevel level, ChunkPos at, int radius) {
            Map<BlockPos, Spawner> actual = new HashMap<>();
            Set<Long> loaded = new HashSet<>();
            for (int z = at.z() - radius; z <= at.z() + radius; z++) {
                for (int x = at.x() - radius; x <= at.x() + radius; x++) {
                    // A client level's hasChunk answers true for any chunk, loaded or not.
                    if (!(level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                    loaded.add(ChunkPos.pack(x, z));
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        try {
                            Spawner.of(blockEntity, level.registryAccess()).ifPresent(spawner -> actual.put(spawner.pos(), spawner));
                        } catch (RuntimeException e) {
                            // A block entity that cannot be read is no spawner to show.
                        }
                    }
                }
            }
            Map<BlockPos, Spawner> known = new HashMap<>();
            for (Known spawner : shown()) known.put(spawner.spawner().pos(), spawner.spawner());
            LoadedCheck.Result result = LoadedCheck.compare(known, actual, pos -> loaded.contains(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4)));
            for (BlockPos pos : result.remove()) {
                seen.remove(pos);
                absent.add(pos);
                scope.remove(dimension, placeId(pos));
            }
            for (Spawner spawner : result.put()) {
                seen.put(spawner.pos(), spawner);
                absent.remove(spawner.pos());
                scope.put(place(spawner, true));
            }
        }

        List<Known> shown() {
            List<Known> shown = new ArrayList<>();
            seen.values().forEach(spawner -> shown.add(new Known(spawner, true)));
            for (Known known : workedOut.values()) {
                BlockPos pos = known.spawner().pos();
                if (!seen.containsKey(pos) && !absent.contains(pos)) shown.add(known);
            }
            return shown;
        }

        private Place place(Spawner spawner, boolean certain) {
            return Place.builder(placeId(spawner.pos()), dimension, spawner.pos())
                    .name(SpawnerNames.placeName(spawner, certain))
                    .group(SpawnerNames.group(spawner))
                    .build();
        }

        private static int distance(ChunkPos a, ChunkPos b) {
            return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
        }
    }
}
