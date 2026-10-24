package investigation;

/** Java-only control: reference arrays and checked arithmetic, as in Clojure. */
public final class InterfaceControl {
    public interface Norm { long norm(); }

    public static final class IdentityPoint implements Norm {
        final long x;
        final long y;
        public IdentityPoint(long x, long y) { this.x = x; this.y = y; }
        public long norm() { return Math.addExact(x, y); }
    }

    public static value class ValuePoint implements Norm {
        final long x;
        final long y;
        public ValuePoint(long x, long y) { this.x = x; this.y = y; }
        public long norm() { return Math.addExact(x, y); }
    }

    public static Object[] instances(int n, boolean value) {
        Object[] result = new Object[n];
        for (int i = 0; i < n; i++) {
            result[i] = value ? new ValuePoint(i, 2L * i) : new IdentityPoint(i, 2L * i);
        }
        return result;
    }

    // Separate sites prevent the two receiver kinds from sharing type profiles.
    public static long identityPass(Object[] a) {
        long sum = 0;
        for (long i = 0; i < a.length; i = Math.addExact(i, 1L)) {
            sum = Math.addExact(sum, ((Norm) a[(int) i]).norm());
        }
        return sum;
    }

    public static long valuePass(Object[] a) {
        long sum = 0;
        for (long i = 0; i < a.length; i = Math.addExact(i, 1L)) {
            sum = Math.addExact(sum, ((Norm) a[(int) i]).norm());
        }
        return sum;
    }
}
