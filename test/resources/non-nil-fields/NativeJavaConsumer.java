package fields;

import checked.aot_fixture.Holder;
import clojure.lang.IFn;

public final class NativeJavaConsumer {
    public static void runChecks() throws Exception {
        Holder value = new Holder(Long.valueOf(42), 7L, 2.5, null);
        if (!Long.valueOf(42).equals(value.required) || value.count != 7L
                || value.weight != 2.5 || value.nullable != null
                || !Long.valueOf(42).equals(((IFn) value.call()).invoke())) {
            throw new AssertionError("Saved fields or companion reads changed");
        }
        if (Holder.class.getField("required").getType() != Long.class) {
            throw new AssertionError("Concrete storage was lost");
        }
        try {
            new Holder(null, 7L, 2.5, null);
            throw new AssertionError("Constructor accepted null");
        } catch (NullPointerException expected) {
            // The actual constructor, not a Clojure factory, enforces this check.
        }
        try {
            new Holder("wrong", 7L, 2.5, null);
            throw new AssertionError("Constructor accepted the wrong type");
        } catch (ClassCastException expected) {
            // Reference constructor parameters remain Object.
        }
    }
}
