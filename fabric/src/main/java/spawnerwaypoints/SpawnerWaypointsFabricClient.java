package spawnerwaypoints;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import spawnerwaypoints.display.SpawnerFrames;
import spawnerwaypoints.spawner.SpawnerFinder;

/** The mod's only entrypoint (it is client-only): settings, and the spawners worked out and outlined every tick. */
public final class SpawnerWaypointsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpawnerWaypointsCommon.init();
        SpawnerWaypointsCommon.initClient();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            SpawnerFinder.tick(mc);
            SpawnerFrames.tick(mc);
        });
    }
}
