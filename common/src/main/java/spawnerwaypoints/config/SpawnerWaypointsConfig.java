package spawnerwaypoints.config;

import spawnerwaypoints.Constants;
import spawnerwaypoints.platform.Services;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import spawnerwaypoints.SpawnerWaypointsPlugin;
import waypointsapi.api.KindSettingsOptions;

/**
 * Settings saved to config/spawnerwaypoints.json5: how spawners are found and outlined, and how their waypoints are
 * shown (Waypoints API reads and writes those through {@link SpawnerKindSettings}).
 */
public final class SpawnerWaypointsConfig {
    public static final ConfigClassHandler<SpawnerWaypointsConfig> HANDLER = ConfigClassHandler.createBuilder(SpawnerWaypointsConfig.class)
            .id(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();

    /** Whether spawners are worked out and outlined at all; when off, their waypoints are not shown either. */
    @SerialEntry
    public boolean enabled = true;

    /** How far around the player dungeons are worked out, in chunks (the square's half side). */
    @SerialEntry
    public int dungeonRadius = 8;

    /** How far around the player the structures with spawners are looked for, in chunks. */
    @SerialEntry
    public int structureRadius = 16;

    /** Whether the spawners near the player are outlined through walls. */
    @SerialEntry
    public boolean showFrames = true;

    /** How far from the player spawners are outlined, in blocks. */
    @SerialEntry
    public int frameDistance = 48;

    /** Whether the spawners' waypoints are shown (they are still worked out when not). */
    @SerialEntry
    public boolean shown = true;

    /** How far (horizontally, in blocks) the spawners' waypoints are shown. */
    @SerialEntry
    public int displayDistance = SpawnerKindSettings.DEFAULT_DISPLAY_DISTANCE;

    /** How many of the spawners' waypoints are shown at most, the nearest. */
    @SerialEntry
    public int maxShown = SpawnerKindSettings.DEFAULT_MAX_SHOWN;

    /** The groups (mobs) whose waypoints are not shown, by id. */
    @SerialEntry
    public List<String> hiddenGroups = new ArrayList<>();

    /** The colors the player chose for groups (opaque RGB), by the group's id. */
    @SerialEntry
    public Map<String, Integer> groupColors = new HashMap<>();

    /** Whether only the nearest waypoint of a group is shown, where the player changed it, by the group's id. */
    @SerialEntry
    public Map<String, Boolean> groupNearestOnly = new HashMap<>();

    public static SpawnerWaypointsConfig get() {
        return HANDLER.instance();
    }

    public static Screen createScreen(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> builder
                .title(Component.translatable("spawnerwaypoints.config.title"))
                .category(generalCategory(defaults, config))).generateScreen(parent);
    }

    /** This mod's options, then how the waypoints are shown (Waypoints API's options for the kind and each mob). */
    private static ConfigCategory generalCategory(SpawnerWaypointsConfig defaults, SpawnerWaypointsConfig config) {
        ConfigCategory.Builder category = ConfigCategory.createBuilder()
                .name(Component.translatable("spawnerwaypoints.config.category.general"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("spawnerwaypoints.config.enabled"))
                        .description(OptionDescription.of(Component.translatable("spawnerwaypoints.config.enabled.desc")))
                        .binding(defaults.enabled, () -> config.enabled, value -> config.enabled = value)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Component.translatable("spawnerwaypoints.config.dungeonRadius"))
                        .description(OptionDescription.of(Component.translatable("spawnerwaypoints.config.dungeonRadius.desc")))
                        .binding(defaults.dungeonRadius, () -> config.dungeonRadius, value -> config.dungeonRadius = value)
                        // Seed Engine API takes at most 64x64 chunks of dungeons at once.
                        .controller(option -> IntegerSliderControllerBuilder.create(option).range(0, 16).step(1))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Component.translatable("spawnerwaypoints.config.structureRadius"))
                        .description(OptionDescription.of(Component.translatable("spawnerwaypoints.config.structureRadius.desc")))
                        .binding(defaults.structureRadius, () -> config.structureRadius, value -> config.structureRadius = value)
                        .controller(option -> IntegerSliderControllerBuilder.create(option).range(0, 32).step(1))
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("spawnerwaypoints.config.showFrames"))
                        .description(OptionDescription.of(Component.translatable("spawnerwaypoints.config.showFrames.desc")))
                        .binding(defaults.showFrames, () -> config.showFrames, value -> config.showFrames = value)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Component.translatable("spawnerwaypoints.config.frameDistance"))
                        .description(OptionDescription.of(Component.translatable("spawnerwaypoints.config.frameDistance.desc")))
                        .binding(defaults.frameDistance, () -> config.frameDistance, value -> config.frameDistance = value)
                        .controller(option -> IntegerSliderControllerBuilder.create(option).range(8, 128).step(8))
                        .build());
        SpawnerWaypointsPlugin.kind().ifPresent(kind -> category.groups(KindSettingsOptions.of(kind)));
        return category.build();
    }
}
