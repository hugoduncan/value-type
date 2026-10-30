package fields;

import checked.aot_fixture.Holder;
import clojure.lang.IFn;
import clojure.lang.IPersistentVector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class MixedJavaConsumer {
    private static void checkField(String name, Class<?> type, boolean nativeCheck)
            throws Exception {
        Field field = Holder.class.getField(name);
        boolean restricted = false;
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            restricted |= annotation.annotationType().getName()
                    .equals("jdk.internal.vm.annotation.NullRestricted");
        }
        if (field.getType() != type || !Modifier.isFinal(field.getModifiers())
                || restricted != nativeCheck) {
            throw new AssertionError("Saved field contract changed: " + name);
        }
    }

    public static void runChecks() throws Exception {
        Holder value = new Holder(Long.valueOf(42), 7L, "text", 2.5, null);
        IPersistentVector captured = (IPersistentVector) ((IFn) value.call()).invoke();
        if (!Long.valueOf(42).equals(value.required) || !"text".equals(value.text)
                || value.count != 7L || value.weight != 2.5 || value.nullable != null
                || !Long.valueOf(42).equals(captured.nth(0))
                || !"text".equals(captured.nth(1))) {
            throw new AssertionError("Saved fields or companion reads changed");
        }
        checkField("required", Long.class, true);
        checkField("text", String.class, false);
        for (Object[] arguments : new Object[][] {
                {null, "text"}, {Long.valueOf(42), null}}) {
            try {
                new Holder(arguments[0], 7L, arguments[1], 2.5, null);
                throw new AssertionError("Constructor accepted null");
            } catch (NullPointerException expected) {
                // Each constrained position must reject null independently.
            }
        }
        for (Object[] arguments : new Object[][] {
                {"wrong", "text"}, {Long.valueOf(42), new Object()}}) {
            try {
                new Holder(arguments[0], 7L, arguments[1], 2.5, null);
                throw new AssertionError("Constructor accepted the wrong type");
            } catch (ClassCastException expected) {
                // Reference constructor parameters remain Object.
            }
        }
    }
}
