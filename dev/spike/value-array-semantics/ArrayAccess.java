/** Ordinary reference-array bytecodes; no reflective access. */
public final class ArrayAccess {
    public static int length(Object[] array) { return array.length; }
    public static Object read(Object[] array, int index) { return array[index]; }
    public static void write(Object[] array, int index, Object value) {
        array[index] = value;
    }
}
