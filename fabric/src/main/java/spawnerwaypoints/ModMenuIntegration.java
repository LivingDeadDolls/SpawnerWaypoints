package spawnerwaypoints;

import spawnerwaypoints.config.SpawnerWaypointsConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Opens the settings screen from Mod Menu's config button. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SpawnerWaypointsConfig::createScreen;
    }
}
