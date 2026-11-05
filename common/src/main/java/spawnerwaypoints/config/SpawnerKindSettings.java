package spawnerwaypoints.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import spawnerwaypoints.spawner.SpawnerNames;
import waypointsapi.api.KindSettings;

/**
 * How the spawners' waypoints are shown, kept in this mod's config ({@link SpawnerWaypointsConfig}). Read anew from the
 * config's current instance on every call: a reload replaces it.
 */
public final class SpawnerKindSettings implements KindSettings {
    public static final int DEFAULT_DISPLAY_DISTANCE = 512;
    public static final int DEFAULT_MAX_SHOWN = 16;
    /** The farthest a spawner is found: the structure range's largest setting (32 chunks), with room to spare. */
    private static final int MAX_DISPLAY_DISTANCE = 1024;

    private static SpawnerWaypointsConfig config() {
        return SpawnerWaypointsConfig.get();
    }

    @Override
    public boolean shown() {
        return config().shown;
    }

    /** Not when spawners are not looked for at all, so that those found before are not shown either. */
    @Override
    public boolean active() {
        return config().enabled;
    }

    @Override
    public void setShown(boolean shown) {
        config().shown = shown;
    }

    @Override
    public int displayDistance() {
        return config().displayDistance;
    }

    @Override
    public void setDisplayDistance(int blocks) {
        config().displayDistance = blocks;
    }

    @Override
    public int defaultDisplayDistance() {
        return DEFAULT_DISPLAY_DISTANCE;
    }

    @Override
    public int maxDisplayDistance() {
        return MAX_DISPLAY_DISTANCE;
    }

    @Override
    public int maxShown() {
        return config().maxShown;
    }

    @Override
    public void setMaxShown(int count) {
        config().maxShown = count;
    }

    @Override
    public int defaultMaxShown() {
        return DEFAULT_MAX_SHOWN;
    }

    @Override
    public boolean groupShown(Identifier group) {
        return !config().hiddenGroups.contains(group.toString());
    }

    @Override
    public void setGroupShown(Identifier group, boolean shown) {
        String id = group.toString();
        config().hiddenGroups.remove(id);
        if (!shown) config().hiddenGroups.add(id);
    }

    @Override
    public int groupColor(Identifier group) {
        return config().groupColors.getOrDefault(group.toString(), defaultGroupColor(group));
    }

    @Override
    public void setGroupColor(Identifier group, int rgb) {
        if (rgb == -1) config().groupColors.remove(group.toString());
        else config().groupColors.put(group.toString(), rgb & 0xFFFFFF);
    }

    @Override
    public boolean groupNearestOnly(Identifier group) {
        return config().groupNearestOnly.getOrDefault(group.toString(), defaultGroupNearestOnly(group));
    }

    @Override
    public void setGroupNearestOnly(Identifier group, boolean nearestOnly) {
        if (nearestOnly == defaultGroupNearestOnly(group)) config().groupNearestOnly.remove(group.toString());
        else config().groupNearestOnly.put(group.toString(), nearestOnly);
    }

    /**
     * Every group a spawner can have, found yet or not, and those with a setting of their own (those of saved waypoints
     * Waypoints API adds).
     */
    @Override
    public Collection<Identifier> groups() {
        Set<String> ids = new TreeSet<>(config().hiddenGroups);
        ids.addAll(config().groupColors.keySet());
        ids.addAll(config().groupNearestOnly.keySet());
        Set<Identifier> groups = new LinkedHashSet<>(SpawnerNames.possibleGroups());
        ids.stream().map(Identifier::tryParse).filter(id -> id != null).forEach(groups::add);
        return List.copyOf(groups);
    }

    @Override
    public void save() {
        SpawnerWaypointsConfig.HANDLER.save();
    }

    @Override
    public Optional<Screen> settingsScreen(Screen parent) {
        return Optional.of(SpawnerWaypointsConfig.createScreen(parent));
    }
}
