import java.util.*;

public final class MultithreadingUtils {
    private MultithreadingUtils(){}

    //Partitions list into n sublists of same size +/- 1. The longer sublists are first.
    public static <T> List<List<T>> partitionList(List<T> list, int n) {
        int listSize = list.size();
        int minimumSublistSize = (int) Math.floor(listSize/n);
        int numberOfLongerSublist = listSize - minimumSublistSize * n;
        List<List<T>> partitionedList = new ArrayList<>();
        for (int i = 0; i < numberOfLongerSublist; i++) {
            partitionedList.add(list.subList((minimumSublistSize + 1) * i, (minimumSublistSize + 1) * (i + 1)));
        }
        for (int i = numberOfLongerSublist; i < n; i++) {
            partitionedList.add(list.subList(numberOfLongerSublist + minimumSublistSize * i, numberOfLongerSublist + minimumSublistSize * (i + 1)));
        }
        return partitionedList;
    }

    //Partitions list into n partitions of same size +/- 1 preserving original order in map. The longer sublists are first.
    public static <T> Map<Integer, List<T>> orderedPartitionList(List<T> list, int n) {
        int listSize = list.size();
        int minimumSublistSize = (int) Math.floor(listSize/n);
        int numberOfLongerSublist = listSize - minimumSublistSize * n;
        Map<Integer, List<T>> partitionedList = new HashMap<>();
        for (int i = 0; i < numberOfLongerSublist; i++) {
            partitionedList.put(i, list.subList((minimumSublistSize + 1) * i, (minimumSublistSize + 1) * (i + 1)));
        }
        for (int i = numberOfLongerSublist; i < n; i++) {
            partitionedList.put(i, list.subList(numberOfLongerSublist + minimumSublistSize * i, numberOfLongerSublist + minimumSublistSize * (i + 1)));
        }
        return partitionedList;
    }

    //Combining partitioned outputs
    public static PositionsAndMap combinePartitionedOutput (Map<Integer, PositionsAndMap> partitionedOutput, Integer siteCount, Integer taskCount) {
        HashMap<Integer, CasesAndCost> minimumCostMap = new HashMap<>(partitionedOutput.get(0).getMap()); //Map from centre to (cases, minimum travel cost)
        ArrayList<Integer> minimumCostPositionByOrigin = new ArrayList<>(partitionedOutput.get(0).getPositions()); //List of the closest positions, in the order of origins/start population centers.
        for (int i = 1; i < taskCount; i++) {
            Map<Integer, CasesAndCost> partitionMinimumCostMap = partitionedOutput.get(i).getMap();
            for (int j = 0; j < siteCount; j++) {
                CasesAndCost partitionPositionCasesCost = partitionMinimumCostMap.get(j);
                double positionCaseCount = minimumCostMap.get(j).getCases() + partitionPositionCasesCost.getCases();
                double positionCost = minimumCostMap.get(j).getCost() + partitionPositionCasesCost.getCost();
                minimumCostMap.put(j, new CasesAndCost(positionCaseCount, positionCost));
            }
            minimumCostPositionByOrigin.addAll(partitionedOutput.get(i).getPositions());
        }
        return new PositionsAndMap(minimumCostPositionByOrigin, minimumCostMap);
    }
}
