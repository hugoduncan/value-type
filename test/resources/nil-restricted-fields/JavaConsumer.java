package fields;

import restricted.aot_fixture.Holder;
import restricted.aot_fixture.Leaf;

public final class JavaConsumer {
    public static void runChecks() {
        Leaf leaf = new Leaf(null);
        Holder holder = new Holder(leaf, null, 42L, 2.5);
        if (holder.required != leaf || holder.nullable != null
                || holder.count != 42L || holder.weight != 2.5
                || holder.required.payload != null) {
            throw new AssertionError("Java field reads differ from constructor arguments");
        }
        try {
            new Holder(null, null, 42L, 2.5);
            throw new AssertionError("Java constructor accepted null");
        } catch (NullPointerException expected) {
            // The native field rejects null, not a Clojure factory.
        }
        try {
            new Holder("wrong", null, 42L, 2.5);
            throw new AssertionError("Java constructor accepted the wrong type");
        } catch (ClassCastException expected) {
            // Reference constructor parameters remain Object.
        }
    }
}
