package spawnerwaypoints.display;

import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import spawnerwaypoints.SpawnerWaypointsPlugin;
import spawnerwaypoints.config.SpawnerWaypointsConfig;
import spawnerwaypoints.spawner.SpawnerFinder;
import waypointsapi.api.WaypointKind;

/**
 * Outlines the spawners near the player, drawn on top of everything so they show through walls (Waypoints API draws a
 * beam and a label for each; the outline shows the block itself), only those whose waypoint Waypoints API draws (not
 * of a hidden mob, not set never to show), explored or not: the outline is for the spawners the player is near. An uncertain one (it may be elsewhere, depending on the order the world
 * generated the chunks in) has a thin outline only.
 */
public final class SpawnerFrames {
    public static final int SPAWNER_COLOR = 0xFF4040;
    public static final int TRIAL_SPAWNER_COLOR = 0xFFA020;
    /** At most this many spawners are outlined, the nearest. */
    private static final int MAX_FRAMES = 64;

    private static volatile List<SpawnerFinder.Known> framed = List.of();

    private SpawnerFrames() {
    }

    /** Client tick: picks the spawners to outline. */
    public static void tick(Minecraft mc) {
        SpawnerWaypointsConfig config = SpawnerWaypointsConfig.get();
        WaypointKind kind = SpawnerWaypointsPlugin.kind().orElse(null);
        if (!config.enabled || !config.showFrames || mc.player == null || mc.level == null || kind == null) {
            framed = List.of();
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        double max = (double) config.frameDistance * config.frameDistance;
        ResourceKey<Level> dimension = mc.level.dimension();
        framed = SpawnerFinder.shown().stream()
                .filter(known -> Vec3.atCenterOf(known.spawner().pos()).distanceToSqr(eye) <= max)
                .filter(known -> kind.isDrawnIgnoringExplored(dimension, SpawnerFinder.placeId(known.spawner().pos())))
                .sorted(Comparator.comparingDouble(known -> Vec3.atCenterOf(known.spawner().pos()).distanceToSqr(eye)))
                .limit(MAX_FRAMES)
                .toList();
    }

    /** Every frame, while the level renderer collects the frame's gizmos. */
    public static void frame() {
        for (SpawnerFinder.Known known : framed) {
            int color = 0xFF000000 | (known.spawner().trial() ? TRIAL_SPAWNER_COLOR : SPAWNER_COLOR);
            GizmoStyle style = known.certain() ? GizmoStyle.strokeAndFill(color, 2.5f, (color & 0xFFFFFF) | 0x30000000)
                    : GizmoStyle.stroke(color, 1.0f);
            Gizmos.cuboid(known.spawner().pos(), style).setAlwaysOnTop();
        }
    }
}
