package fields;

import checked.controlled.ControlledHolder;

public final class ControlledJavaConsumer {
    public static void runChecks() throws Exception {
        Class<?> owner = ControlledHolder.class;
        java.lang.reflect.Field field = owner.getField("value");
        if (owner.isIdentity() || Long.class.isIdentity()
                || field.getType() != Long.class
                || !java.lang.reflect.Modifier.isFinal(field.getModifiers())
                || field.getDeclaredAnnotations().length != 0) {
            throw new AssertionError("Expected generated value storage");
        }
        Long value = Long.valueOf(7);
        if (!value.equals(new ControlledHolder(value).value)) {
            throw new AssertionError("Direct constructor lost value");
        }
        try {
            new ControlledHolder(null);
            throw new AssertionError("Accepted null");
        } catch (NullPointerException expected) {
        }
        try {
            new ControlledHolder("wrong");
            throw new AssertionError("Accepted wrong type");
        } catch (ClassCastException expected) {
        }
    }
}
