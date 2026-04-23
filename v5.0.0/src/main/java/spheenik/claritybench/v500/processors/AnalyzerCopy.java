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
 * Mirrors clarity-analyzer's ObservableEntityList allocation shape: on every
 * create / update / property-count-change, call {@code state.copy()} and
 * retain the result indexed by entity. This is the cost we want to know —
 * whether replacing it with a sparse StateDelta has meaningful upside.
 *
 * Slot array is sized for the largest engine's index space (S2 = 14 bits
 * = 16384). S1 engines only use the lower 11 bits; unused slots stay null.
 */
@UsesEntities
public class AnalyzerCopy {

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
        slots[e.getIndex()] = e.getState().copy();
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
