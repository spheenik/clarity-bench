package spheenik.claritybench.v500.processors;

import skadistats.clarity.model.Entity;
import skadistats.clarity.processor.entities.OnEntityCreated;
import skadistats.clarity.processor.entities.OnEntityDeleted;
import skadistats.clarity.processor.entities.UsesEntities;

/** Lifecycle listeners — much rarer than updates, sanity check on dispatch cost. */
@UsesEntities
public class Lifecycle {
    public long created;
    public long deleted;

    @OnEntityCreated public void onC(Entity e) { created++; }
    @OnEntityDeleted public void onD(Entity e) { deleted++; }
}
