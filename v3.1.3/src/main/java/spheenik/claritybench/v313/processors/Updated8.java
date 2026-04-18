package spheenik.claritybench.v313.processors;

import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.OnEntityUpdated;
import skadistats.clarity.processor.entities.UsesEntities;

/** Eight OnEntityUpdated listeners — exposes how dispatch scales with listener count. */
@UsesEntities
public class Updated8 {
    public long hits;

    @OnEntityUpdated public void h1(Entity e, FieldPath[] fp, int n) { hits++; }
    @OnEntityUpdated public void h2(Entity e, FieldPath[] fp, int n) { hits++; }
    @OnEntityUpdated public void h3(Entity e, FieldPath[] fp, int n) { hits++; }
    @OnEntityUpdated public void h4(Entity e, FieldPath[] fp, int n) { hits++; }
    @OnEntityUpdated public void h5(Entity e, FieldPath[] fp, int n) { hits++; }
    @OnEntityUpdated public void h6(Entity e, FieldPath[] fp, int n) { hits++; }
    @OnEntityUpdated public void h7(Entity e, FieldPath[] fp, int n) { hits++; }
    @OnEntityUpdated public void h8(Entity e, FieldPath[] fp, int n) { hits++; }
}
