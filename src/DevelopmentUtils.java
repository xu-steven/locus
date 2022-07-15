import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class DevelopmentUtils {
    public static long getGarbageCollectionTime() {
        long collectionTime = 0;
        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectionTime += garbageCollectorMXBean.getCollectionTime();
        }
        return collectionTime;
    }

    public static long getGarbageCollectionCycles() {
        long collectionCount = 0;
        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            collectionCount += garbageCollectorMXBean.getCollectionCount();
        }
        return collectionCount;
    }

    public static void setUpdateFrequency(Integer updateFrequency) {
        SimAnnealingSearch.updateFrequency = updateFrequency;
    }
}



