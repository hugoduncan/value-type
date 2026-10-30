package fields;

import checked.aot_fixture.Holder;
import clojure.lang.IFn;

public final class IdentityJavaConsumer {
    public static void runChecks() throws Exception {
        Holder value = new Holder("valid", 7L, 2.5, null);
        if (!"valid".equals(value.required) || value.count != 7L
                || value.weight != 2.5 || value.nullable != null
                || !"valid".equals(((IFn) value.call()).invoke())) {
            throw new AssertionError("Saved fields or companion reads changed");
        }
        if (Holder.class.getField("required").getType() != String.class) {
            throw new AssertionError("Concrete storage was lost");
        }
        try {
            new Holder(null, 7L, 2.5, null);
            throw new AssertionError("Constructor accepted null");
        } catch (NullPointerException expected) {
            // The actual constructor, not a Clojure factory, enforces this check.
        }
        try {
            new Holder(Integer.valueOf(42), 7L, 2.5, null);
            throw new AssertionError("Constructor accepted the wrong type");
        } catch (ClassCastException expected) {
            // Reference constructor parameters remain Object.
        }
    }
}
