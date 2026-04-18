package spheenik.claritybench.v313.processors;

import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityPropertyChanged;
import skadistats.clarity.processor.entities.UsesEntities;

/** One OnEntityPropertyChanged listener with no patterns — worst case for predicate calls. */
@UsesEntities
public class WildcardSingle {
    public long hits;

    @OnEntityPropertyChanged
    public void on(Entity e, FieldPath fp) { hits++; }
}
