import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

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

    //Partitions array into n sub arrays of same size +/- 1 preserving original order. The longer sub arrays are found first.
    public static int[][] orderedPartitionArray(int[] array, int n) {
        int arrayLength = array.length;
        int minimumSublistSize = (int) Math.floor(arrayLength/n);
        int numberOfLongerSublist = arrayLength - minimumSublistSize * n;
        int[][] partitionedArray = new int[n][];
        for (int i = 0; i < numberOfLongerSublist; i++) {
            partitionedArray[i] = Arrays.copyOfRange(array, (minimumSublistSize + 1) * i, (minimumSublistSize + 1) * (i + 1));
        }
        for (int i = numberOfLongerSublist; i < n; i++) {
            partitionedArray[i] = Arrays.copyOfRange(array, numberOfLongerSublist + minimumSublistSize * i, numberOfLongerSublist + minimumSublistSize * (i + 1));
        }
        return partitionedArray;
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

    //Get starting origin based on task count
    public static int[] getTaskSpecificStartingOrigins(int originCount, int taskCount) {
        int minimumTaskSize = (int) Math.floor(originCount/taskCount);
        int numberOfLongerTasks = originCount - minimumTaskSize * taskCount;
        int[] lowerOrigins = new int[taskCount];
        for (int i = 0; i < numberOfLongerTasks; i++) {
            lowerOrigins[i] = (minimumTaskSize + 1) * i;
        }
        for (int i = numberOfLongerTasks; i < taskCount; i++) {
            lowerOrigins[i] = numberOfLongerTasks + minimumTaskSize * i;
        }
        return lowerOrigins;
    }

    //Get finishing origin (exclusive) based on task count
    public static int[] getTaskSpecificEndingOrigins(int originCount, int taskCount) {
        int minimumTaskSize = (int) Math.floor(originCount/taskCount);
        int numberOfLongerTasks = originCount - minimumTaskSize * taskCount;
        int[] upperOrigins = new int [taskCount];
        for (int i = 0; i < numberOfLongerTasks; i++) {
            upperOrigins[i] = (minimumTaskSize + 1) * (i + 1);
        }
        for (int i = numberOfLongerTasks; i < taskCount; i++) {
            upperOrigins[i] = numberOfLongerTasks + minimumTaskSize * (i + 1);
        }
        return upperOrigins;
    }

    @Deprecated
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

    @Deprecated
    //Combining partitioned outputs
    public static HashMap<Integer, CasesAndCost> combinePartitionedMinimumCostMapTaskCountFirst (HashMap<Integer, CasesAndCost>[] partitionedMinimumCostMap, Integer siteCount, Integer taskCount) {
        HashMap<Integer, CasesAndCost> minimumCostMap = new HashMap<>(partitionedMinimumCostMap[0]); //Map from centre to (cases, minimum travel cost)
        for (int i = 1; i < taskCount; i++) {
            Map<Integer, CasesAndCost> partitionMinimumCostMap = partitionedMinimumCostMap[i];
            for (int j = 0; j < siteCount; j++) {
                CasesAndCost partitionPositionCasesCost = partitionMinimumCostMap.get(j);
                double positionCaseCount = minimumCostMap.get(j).getCases() + partitionPositionCasesCost.getCases();
                double positionCost = minimumCostMap.get(j).getCost() + partitionPositionCasesCost.getCost();
                minimumCostMap.put(j, new CasesAndCost(positionCaseCount, positionCost));
            }
        }
        return minimumCostMap;
    }

    @Deprecated
    //Combining partitioned outputs
    public static HashMap<Integer, CasesAndCost> combinePartitionedMinimumCostMap(HashMap<Integer, CasesAndCost>[] partitionedMinimumCostMap, Integer siteCount, Integer taskCount) {
        HashMap<Integer, CasesAndCost> minimumCostMap = new HashMap<>(); //Map from centre to (cases, minimum travel cost)
        for (int j = 0; j < siteCount; j++) {
            double positionCaseCount = partitionedMinimumCostMap[0].get(j).getCases();
            double positionCost = partitionedMinimumCostMap[0].get(j).getCost();
            for (int i = 1; i < taskCount; i++) {
                positionCaseCount += partitionedMinimumCostMap[i].get(j).getCases();
                positionCost += partitionedMinimumCostMap[i].get(j).getCost();
            }
            minimumCostMap.put(j, new CasesAndCost(positionCaseCount, positionCost));
        }
        return minimumCostMap;
    }

    //Combining partitioned outputs
    public static CasesAndCost[][] combinePartitionedMinimumCostMap(CasesAndCost[][][] partitionedMinimumCostMap, int timepointCount, int siteCount, int taskCount) {
        CasesAndCost[][] minimumCostMap = new CasesAndCost[timepointCount][siteCount]; //Map from centre to (cases, minimum travel cost)
        for (int timepoint = 0; timepoint < timepointCount; timepoint++) {
            for (int j = 0; j < siteCount; j++) {
                double positionCaseCount = partitionedMinimumCostMap[0][timepoint][j].getCases();
                double positionCost = partitionedMinimumCostMap[0][timepoint][j].getCost();
                for (int i = 1; i < taskCount; i++) {
                    positionCaseCount += partitionedMinimumCostMap[i][timepoint][j].getCases();
                    positionCost += partitionedMinimumCostMap[i][timepoint][j].getCost();
                }
                minimumCostMap[timepoint][j] = new CasesAndCost(positionCaseCount, positionCost);
            }
        }
        return minimumCostMap;
    }

    //Combining partitioned outputs
    public static CasesAndCost[][] combinePartitionedMinimumCostMap(CasesAndCost[][][] partitionedMinimumCostMap, int timepointCount, int siteCount, int taskCount, ExecutorService executor) {
        CountDownLatch latch = new CountDownLatch(timepointCount * siteCount);
        CasesAndCost[][] minimumCostMap = new CasesAndCost[timepointCount][siteCount]; //Map from centre to (cases, minimum travel cost)
        for (int timepoint = 0; timepoint < timepointCount; timepoint++) {
            for (int j = 0; j < siteCount; j++) {
                int finalJ = j;
                int finalTimepoint = timepoint;
                executor.execute(() -> {
                    double positionCaseCount = partitionedMinimumCostMap[0][finalTimepoint][finalJ].getCases();
                    double positionCost = partitionedMinimumCostMap[0][finalTimepoint][finalJ].getCost();
                    for (int i = 1; i < taskCount; i++) {
                        positionCaseCount += partitionedMinimumCostMap[i][finalTimepoint][finalJ].getCases();
                        positionCost += partitionedMinimumCostMap[i][finalTimepoint][finalJ].getCost();
                    }
                    minimumCostMap[finalTimepoint][finalJ] = new CasesAndCost(positionCaseCount, positionCost);
                    latch.countDown();
                });
            }
        }
        return minimumCostMap;
    }

    @Deprecated
    //Merge two outputs, use comment in thread to revive
    public static CasesAndCost addCasesAndCosts (CasesAndCost casesAndCostOne, CasesAndCost casesAndCostTwo) {
        return new CasesAndCost(casesAndCostOne.getCases() + casesAndCostTwo.getCases(), casesAndCostOne.getCost() + casesAndCostTwo.getCost());
    }
    //In place of HashMap<Integer, CasesAndCost>[] partitionedMinimumCostMap
    //ConcurrentHashMap<Integer, CasesAndCost> combinedMinimumCostMap = new ConcurrentHashMap<>();

    //After partitionMinimumCostMap is created in each thread (before task completed)
    //for (int j = 0; j < siteCount; j++) {
    //    int finalJ = j;
    //    combinedMinimumCostMap.compute(j, (position, positionCasesAndCost) -> (positionCasesAndCost == null) ? partitionMinimumCostMap.get(finalJ) : MultithreadingUtils.addCasesAndCosts(positionCasesAndCost, partitionMinimumCostMap.get(finalJ)));
    //}
}