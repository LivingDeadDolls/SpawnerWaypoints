package spawnerwaypoints;

import spawnerwaypoints.config.SpawnerWaypointsConfig;
import spawnerwaypoints.platform.Services;

/** Loader-independent startup, called from each loader's entrypoints. */
public final class SpawnerWaypointsCommon {
    private SpawnerWaypointsCommon() {
    }

    /** Startup, called first from the client entrypoint. */
    public static void init() {
        Constants.LOG.info("{} loaded on {}", Constants.MOD_NAME, Services.PLATFORM.getPlatformName());
    }

    /** The client only, after {@link #init()}. */
    public static void initClient() {
        SpawnerWaypointsConfig.HANDLER.load();
    }
}
