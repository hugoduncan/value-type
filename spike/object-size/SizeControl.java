package investigation;

import java.lang.instrument.Instrumentation;

/** Java-only size control, independent of Clojure and Criterium. */
public final class SizeControl {
    private static Instrumentation instrumentation;

    public static void premain(String args, Instrumentation agent) {
        instrumentation = agent;
    }

    static final class Identity0 {}
    static value class Value0 {}
    static final class Identity1 {
        final long f0;
        Identity1(long x) { f0 = x; }
    }
    static value class Value1 {
        final long f0;
        Value1(long x) { f0 = x; }
    }
    static final class Identity2 {
        final long f0, f1;
        Identity2(long x) { f0 = x; f1 = x + 1; }
    }
    static value class Value2 {
        final long f0, f1;
        Value2(long x) { f0 = x; f1 = x + 1; }
    }
    static final class Identity3 {
        final long f0, f1, f2;
        Identity3(long x) { f0 = x; f1 = x + 1; f2 = x + 2; }
    }
    static value class Value3 {
        final long f0, f1, f2;
        Value3(long x) { f0 = x; f1 = x + 1; f2 = x + 2; }
    }

    static final class IdentityRef1 {
        final Object f0;
        IdentityRef1(Object x) { f0 = x; }
    }
    static value class ValueRef1 {
        final Object f0;
        ValueRef1(Object x) { f0 = x; }
    }
    static final class IdentityRef2 {
        final Object f0, f1;
        IdentityRef2(Object x) { f0 = x; f1 = x; }
    }
    static value class ValueRef2 {
        final Object f0, f1;
        ValueRef2(Object x) { f0 = x; f1 = x; }
    }
    static final class IdentityRef3 {
        final Object f0, f1, f2;
        IdentityRef3(Object x) { f0 = x; f1 = x; f2 = x; }
    }
    static value class ValueRef3 {
        final Object f0, f1, f2;
        ValueRef3(Object x) { f0 = x; f1 = x; f2 = x; }
    }

    public static long size(Object object) {
        return instrumentation.getObjectSize(object);
    }

    public static void main(String[] args) {
        Object[] objects = {new Identity0(), new Value0(),
            new Identity1(1), new Value1(1), new Identity2(1), new Value2(1),
            new Identity3(1), new Value3(1),
            new IdentityRef1(args), new ValueRef1(args),
            new IdentityRef2(args), new ValueRef2(args),
            new IdentityRef3(args), new ValueRef3(args),
            new IdentityRef1(null), new ValueRef1(null),
            new IdentityRef2(null), new ValueRef2(null),
            new IdentityRef3(null), new ValueRef3(null)};
        for (Object object : objects) {
            String row = "SIZE " + object.getClass().getSimpleName()
                + " value=" + object.getClass().isValue()
                + " bytes=" + instrumentation.getObjectSize(object);
            System.out.println(row);
        }
        java.lang.ref.Reference.reachabilityFence(objects);
    }
}
