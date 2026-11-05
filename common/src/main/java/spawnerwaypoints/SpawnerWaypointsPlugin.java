package spawnerwaypoints;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import spawnerwaypoints.config.SpawnerKindSettings;
import spawnerwaypoints.spawner.SpawnerNames;
import waypointsapi.api.KindDefinition;
import waypointsapi.api.WaypointKind;
import waypointsapi.api.WaypointRegistry;
import waypointsapi.api.WaypointsPlugin;

/** Registers the spawners with Waypoints API (its {@code waypointsapi} entrypoint): one kind, grouped by the mob. */
public final class SpawnerWaypointsPlugin implements WaypointsPlugin {
    public static final Identifier KIND_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "spawners");

    private static volatile WaypointKind kind;

    @Override
    public void register(WaypointRegistry registry) {
        kind = registry.register(KindDefinition.builder(KIND_ID, Component.translatable("spawnerwaypoints.kind"))
                .icon(Items.SPAWNER)
                .groupName(SpawnerNames::groupName)
                .groupIcon(SpawnerNames::icon)
                .groupInitial(SpawnerNames::initial)
                .settings(new SpawnerKindSettings())
                .build());
    }

    /** The kind, once Waypoints API has registered it (empty if Waypoints API skipped the plugin). */
    public static Optional<WaypointKind> kind() {
        return Optional.ofNullable(kind);
    }
}
