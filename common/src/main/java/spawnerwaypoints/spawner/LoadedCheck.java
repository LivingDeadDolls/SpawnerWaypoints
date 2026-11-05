package spawnerwaypoints.spawner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import seedengine.api.Spawner;

/**
 * Puts the known spawners right with the client's loaded chunks, where the world counts: a known spawner that is not
 * there (worked out where the world put none, or broken since) is dropped, and a spawner that is there is shown,
 * whether it was worked out or not.
 */
public final class LoadedCheck {
    /** What to change: spawners to show (as they stand in the world) and positions to drop. */
    public record Result(List<Spawner> put, List<BlockPos> remove) {
    }

    private LoadedCheck() {
    }

    /**
     * @param known  the spawners shown now, by position
     * @param actual the spawners in the loaded chunks, by position
     * @param loaded whether a position is in a loaded chunk
     */
    public static Result compare(Map<BlockPos, Spawner> known, Map<BlockPos, Spawner> actual, Predicate<BlockPos> loaded) {
        List<BlockPos> remove = new ArrayList<>();
        for (Map.Entry<BlockPos, Spawner> entry : known.entrySet()) {
            Spawner there = actual.get(entry.getKey());
            if (loaded.test(entry.getKey()) && (there == null || there.trial() != entry.getValue().trial())) remove.add(entry.getKey());
        }
        List<Spawner> put = new ArrayList<>();
        for (Spawner there : actual.values()) {
            Spawner before = known.get(there.pos());
            // A trial spawner's configuration is not sent to clients: the one worked out stays.
            Spawner shown = there.mob().isEmpty() && before != null && before.trial() == there.trial()
                    ? new Spawner(there.pos(), before.mob(), there.trial()) : there;
            if (!shown.equals(before)) put.add(shown);
        }
        return new Result(put, remove);
    }
}
