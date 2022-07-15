import java.util.*;

public final class MultithreadingUtils {
    private MultithreadingUtils(){}

    //Partitions list into n sublists of same size +/- 1. The longer sublists are first.
    public static <T> List<List<T>> partitionList(List<T> list, Integer n) {
        Integer listSize = list.size();
        Integer minimumSublistSize = (int) Math.floor(listSize/n);
        Integer numberOfLongerSublist = listSize - minimumSublistSize * n;
        List<List<T>> partitionedList = new ArrayList<>();
        for (int i=0; i < numberOfLongerSublist; i++) {
            partitionedList.add(list.subList((minimumSublistSize + 1) * i, (minimumSublistSize + 1) * (i + 1)));
        }
        for (int i=numberOfLongerSublist; i < n; i++) {
            partitionedList.add(list.subList(numberOfLongerSublist + minimumSublistSize * i, numberOfLongerSublist + minimumSublistSize * (i + 1)));
        }
        return partitionedList;
    }

    //Partitions list into n partitions of same size +/- 1 preserving original order in map. The longer sublists are first.
    public static <T> Map<Integer, List<T>> orderedPartitionList(List<T> list, Integer n) {
        Integer listSize = list.size();
        Integer minimumSublistSize = (int) Math.floor(listSize/n);
        Integer numberOfLongerSublist = listSize - minimumSublistSize * n;
        Map<Integer, List<T>> partitionedList = new HashMap<>();
        for (int i=0; i < numberOfLongerSublist; i++) {
            partitionedList.put(i, list.subList((minimumSublistSize + 1) * i, (minimumSublistSize + 1) * (i + 1)));
        }
        for (int i=numberOfLongerSublist; i < n; i++) {
            partitionedList.put(i, list.subList(numberOfLongerSublist + minimumSublistSize * i, numberOfLongerSublist + minimumSublistSize * (i + 1)));
        }
        return partitionedList;
    }

    //Combining partitioned outputs
    public static List<Object> combinePartitionedOutput (Map<Integer, List<Object>> partitionedOutput, Integer siteCount, Integer taskCount) {
        Map<Integer, List<Double>> minimumCostMap = new HashMap<>(); //Map from centre to (cases, minimum travel cost)
        List<Integer> minimumCostPositionByOrigin = new ArrayList<>(); //List of the closest positions, in the order of origins/start population centers.
        List<Double> initialCasesCost = new ArrayList<>(Arrays.asList((double) 0, (double) 0));
        for (int i = 0; i < siteCount; ++i) {
            minimumCostMap.put(i, initialCasesCost);
        }
        for (int i = 0; i < taskCount; i++) {
            Map<Integer, List<Double>> partitionMinimumCostMap = (HashMap<Integer, List<Double>>) partitionedOutput.get(i).get(0);
            for (int j = 0; j < siteCount; j++) {
                List<Double> partitionPositionCasesCost = partitionMinimumCostMap.get(j);
                double positionCaseCount = minimumCostMap.get(j).get(0) + partitionPositionCasesCost.get(0);
                double positionCost = minimumCostMap.get(j).get(1) + partitionPositionCasesCost.get(1);
                minimumCostMap.put(j, Arrays.asList(positionCaseCount, positionCost));
            }
            minimumCostPositionByOrigin.addAll((ArrayList<Integer>) partitionedOutput.get(i).get(1));
        }
        return Arrays.asList(minimumCostMap, minimumCostPositionByOrigin);
    }
}
