package spheenik.claritybench.v313.processors;

import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.entities.UsesEntities;

/** Single OnEntityUpdated listener — measures per-update dispatch cost. */
@UsesEntities
public class Updated1 {
    public long hits;

    @OnEntityUpdated
    public void on(Entity e, FieldPath[] fp, int n) { hits++; }
}
