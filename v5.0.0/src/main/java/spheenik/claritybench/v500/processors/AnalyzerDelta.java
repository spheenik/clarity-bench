package spheenik.claritybench.v500.processors;

import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.entities.OnEntityDeleted;
import skadistats.clarity.processor.entities.OnEntityPropertyCountChanged;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.entities.UsesEntities;
import skadistats.clarity.state.EntityState;

/**
 * Mirrors the proposed clarity-analyzer sparse-state-delta-updates allocation
 * shape. Create/count-change keep the full {@code state.copy()} seed (once
 * per entity lifetime / rare layout-reshape event); per-tick updates capture
 * a sparse {@link skadistats.clarity.state.StateDelta} sized to the changed-
 * field count and merge it into a persistent per-entity state via
 * {@code EntityState.applyFrom(...)}.
 *
 * <p>Direct head-to-head with {@link AnalyzerCopy}: same replay, same
 * dispatch point, different allocation/merge shape. Diff reveals the
 * actual savings of replacing full-copy-per-tick with sparse-delta-merge.
 */
@UsesEntities
public class AnalyzerDelta {

    private static final int MAX_SLOTS = 1 << 14;

    public final EntityState[] slots = new EntityState[MAX_SLOTS];
    public long createdCount;
    public long updatedCount;
    public long countChangedCount;
    public long deletedCount;

    @OnEntityCreated
    public void onCreate(Entity e) {
        slots[e.getIndex()] = e.getState().copy();
        createdCount++;
    }

    @OnEntityUpdated
    public void onUpdate(Entity e, FieldPath[] fps, int n) {
        var delta = EntityState.captureChanged(e.getState(), fps, n);
        var target = slots[e.getIndex()];
        for (var i = 0; i < n; i++) {
            EntityState.applyFrom(target, delta, fps[i]);
        }
        updatedCount++;
    }

    @OnEntityPropertyCountChanged
    public void onCountChanged(Entity e) {
        slots[e.getIndex()] = e.getState().copy();
        countChangedCount++;
    }

    @OnEntityDeleted
    public void onDelete(Entity e) {
        slots[e.getIndex()] = null;
        deletedCount++;
    }
}
