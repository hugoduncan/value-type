package investigation;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.util.function.Supplier;
import jdk.internal.value.ValueClass;

/** Measurement helpers only; arrays and traversal are built in Clojure. */
public final class Probe {
    private static Instrumentation instrumentation;
    private static volatile Object sink;
    private static final com.sun.management.ThreadMXBean THREADS =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void premain(String args, Instrumentation agent) {
        instrumentation = agent;
    }

    public static long shallow(Object object) {
        return instrumentation.getObjectSize(object);
    }

    // Each non-flat slot is constructed separately with a distinct long value.
    // There are no references inside the measured elements.
    public static long storage(Object[] array) {
        long bytes = shallow(array);
        if (!ValueClass.isFlatArray(array)) {
            for (Object element : array) bytes += shallow(element);
        }
        return bytes;
    }

    public static double allocation(Supplier<Object> factory, int repetitions) {
        if (!THREADS.isThreadAllocatedMemorySupported())
            throw new IllegalStateException("Thread allocation counter unavailable");
        THREADS.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 30; i++) sink = factory.get();
        long thread = Thread.currentThread().threadId();
        long before = THREADS.getThreadAllocatedBytes(thread);
        for (int i = 0; i < repetitions; i++) sink = factory.get();
        long bytes = THREADS.getThreadAllocatedBytes(thread) - before;
        if (bytes <= 0) throw new IllegalStateException("Invalid allocation count");
        return (double) bytes / repetitions;
    }
}
