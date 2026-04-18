package skadistats.clarity.model.state;

import skadistats.clarity.io.s1.ReceiveProp;
import skadistats.clarity.io.s2.field.SerializerField;

/**
 * <strong>SHADOW</strong> of clarity 3.1.3's {@code EntityStateFactory}.
 * Compiled into this subproject's classes/ directory and placed ahead of
 * {@code clarity-3.1.3.jar} on the runtime classpath, so the JVM resolves
 * to this class instead of the one inside the released jar. Public method
 * signatures match the released class verbatim — see
 * {@code javap -c clarity-3.1.3.jar/skadistats/clarity/model/state/EntityStateFactory.class}.
 *
 * <p>The default behavior (when {@link EntityStateFactoryShim#getRequestedImpl()}
 * returns {@code null} or {@code "DEFAULT"}) replicates the original bytecode
 * exactly: {@code forS1} returns {@link ObjectArrayEntityState} sized by
 * the prop array length; {@code forS2} returns {@link NestedArrayEntityState}
 * constructed from the {@link SerializerField}.
 *
 * <p>When the shim has a non-default impl set, this class routes to:
 * <ul>
 *   <li>{@code OBJECT_ARRAY} → {@link ObjectArrayEntityState} (S1 family)</li>
 *   <li>{@code NESTED_ARRAY} → {@link NestedArrayEntityState} (S2 family)</li>
 *   <li>{@code TREE_MAP}     → {@link TreeMapEntityState} no-arg (S2 family;
 *       the {@link SerializerField} argument is ignored — TreeMap's
 *       constructor takes none)</li>
 * </ul>
 *
 * <p>Brittleness note: this shim covers {@code forS1}/{@code forS2} only.
 * If a clarity 3.1.x patch had reorganized internal callers off this static
 * factory (e.g. inlined construction inside {@code DTClasses} or
 * {@code S2DTClass}), those code paths would silently fall back to the
 * released-jar default. The build-time smoke test that constructs an
 * {@link EntityState} for every declared impl and asserts the concrete
 * class is the guard for this.
 */
public class EntityStateFactory {

    public EntityStateFactory() {}

    public static EntityState forS1(ReceiveProp[] props) {
        String impl = EntityStateFactoryShim.getRequestedImpl();
        if (impl == null || "DEFAULT".equals(impl) || "OBJECT_ARRAY".equals(impl)) {
            return new ObjectArrayEntityState(props.length);
        }
        throw new IllegalStateException(
                "Shadowed EntityStateFactory got impl='" + impl + "' for an S1 entity, but only "
                + "OBJECT_ARRAY exists in clarity 3.1.3. (S1_FLAT is a 5.x-only addition.)");
    }

    public static EntityState forS2(SerializerField sf) {
        String impl = EntityStateFactoryShim.getRequestedImpl();
        if (impl == null || "DEFAULT".equals(impl) || "NESTED_ARRAY".equals(impl)) {
            return new NestedArrayEntityState(sf);
        }
        if ("TREE_MAP".equals(impl)) {
            // TreeMapEntityState's no-arg constructor — SerializerField is unused
            // for this impl in 4.0.0.
            return new TreeMapEntityState();
        }
        throw new IllegalStateException(
                "Shadowed EntityStateFactory got impl='" + impl + "' for an S2 entity. "
                + "Valid in clarity 3.1.3: NESTED_ARRAY, TREE_MAP. (S2_FLAT is a 5.x-only addition.)");
    }
}
