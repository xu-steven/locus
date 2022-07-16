import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SearchSpace {
    //New center properties
    private final int minNewCenters; //Usually 1.
    private final int maxNewCenters; //Maximum number of cancer centers to try
    private final List<Double> minimumCasesByLevel; //list in ascending order minimum cases for increasingly tertiary cancer center services
    private final List<Double> servicedProportionByLevel; //list in ascending order

    //Permanent higher level services to maintain
    private List<List<Integer>> permanentHLCenters;

    //Non-configurable class variables
    private final double minimumCases;// = 10000;
    private final int higherCenterLevels;
    private final int originCount;
    private final Map<Integer, List<Integer>> partitionedOrigins;
    private final List<Double> caseCountByOrigin;
    private final List<List<Double>> graphArray;// = parseCSV(graphLocation);
    private int potentialSitesCount;// = graphArray.get(0).size() - 1;
    private final List<List<Integer>> sortedNeighbors;// = SimAnnealingNeighbor.sortNeighbors(azimuthArray, haversineArray);

    //Non-configurable permanent center class variables
    private List<Integer> minPermanentPositionByOrigin; //minimum existing centers that must be maintained
    private List<Double> minPermanentCostByOrigin; //minimum cost
    private List<List<Integer>> minPermanentHLPositionByOrigin;
    private List<List<Double>> minPermanentHLCostByOrigin;
    private int permanentCentersCount;
    private List<Integer> permanentHLCentersCount;
    private int permanentAllHLCentersCount;

    public SearchSpace(int minNewCenters, int maxNewCenters, List<Double> minimumCasesByLevel, List<Double> servicedProportionByLevel,
                       String censusFileLocation, String graphLocation, String azimuthLocation, String haversineLocation,
                       int threadCount, int taskCount, ExecutorService executor) {
        this.minNewCenters = minNewCenters;
        this.maxNewCenters = maxNewCenters;
        this.minimumCasesByLevel = minimumCasesByLevel;
        this.servicedProportionByLevel = servicedProportionByLevel;

        //Determining remaining variables
        this.minimumCases = minimumCasesByLevel.get(0);
        this.higherCenterLevels = minimumCasesByLevel.size() - 1;
        this.caseCountByOrigin = FileUtils.getCaseCountsFromCSV(censusFileLocation, "Population");
        this.graphArray = FileUtils.getInnerDoubleArrayFromCSV(graphLocation);
        this.potentialSitesCount = graphArray.get(0).size();
        this.originCount = graphArray.size();
        partitionedOrigins = MultithreadingUtils.orderedPartitionList(IntStream.range(0, originCount).boxed().collect(Collectors.toList()), threadCount);
        this.sortedNeighbors = SimAnnealingNeighbor.sortNeighbors(FileUtils.getInnerAzimuthArrayFromCSV(azimuthLocation), FileUtils.getInnerDoubleArrayFromCSV(haversineLocation), taskCount, executor);
    }

    //When there are permanent centers to put in graphArray
    public SearchSpace(int minNewCenters, int maxNewCenters, List<List<Integer>> permanentHLCenters, List<Double> minimumCasesByLevel, List<Double> servicedProportionByLevel,
                                           String censusFileLocation, String permanentGraphLocation, String potentialGraphLocation, String azimuthLocation, String haversineLocation,
                                           int threadCount, int taskCount, ExecutorService executor) {
        this.minNewCenters = minNewCenters;
        this.maxNewCenters = maxNewCenters;
        this.minimumCasesByLevel = minimumCasesByLevel;
        this.servicedProportionByLevel = servicedProportionByLevel;
        this.permanentHLCenters = permanentHLCenters;

        //Determining remaining variables
        List<List<Double>> permanentGraphArray = FileUtils.getInnerDoubleArrayFromCSV(permanentGraphLocation);
        List<List<Double>> potentialGraphArray = FileUtils.getInnerDoubleArrayFromCSV(potentialGraphLocation);
        this.minimumCases = minimumCasesByLevel.get(0);
        this.higherCenterLevels = minimumCasesByLevel.size() - 1;
        this.caseCountByOrigin = FileUtils.getCaseCountsFromCSV(censusFileLocation, "Population");
        this.graphArray = ArrayOperations.mergeArrays(potentialGraphArray, permanentGraphArray);
        this.potentialSitesCount = potentialGraphArray.get(0).size();
        this.originCount = graphArray.size();
        partitionedOrigins = MultithreadingUtils.orderedPartitionList(IntStream.range(0, originCount).boxed().collect(Collectors.toList()), threadCount);
        this.sortedNeighbors = SimAnnealingNeighbor.sortNeighbors(FileUtils.getInnerAzimuthArrayFromCSV(azimuthLocation), FileUtils.getInnerDoubleArrayFromCSV(haversineLocation), taskCount, executor);

        //Determining remaining permanent center variables
        permanentCentersCount = permanentGraphArray.get(0).size();
        List<Object> minimumPermanentCenterInfo = getMinimumCenterInfo(permanentGraphArray);
        minPermanentPositionByOrigin = (List<Integer>) minimumPermanentCenterInfo.get(0);
        minPermanentCostByOrigin = (List<Double>) minimumPermanentCenterInfo.get(1);
        permanentHLCentersCount = new ArrayList<>();
        Set permanentAllHLCenters = new HashSet<>();
        for (int i = 0; i < this.getHigherCenterLevels(); i++) {
            permanentHLCentersCount.add(permanentHLCenters.get(i).size());
            permanentAllHLCenters.addAll(permanentHLCenters.get(i));
        }
        permanentAllHLCentersCount = permanentAllHLCenters.size();
        List<Object> minHLPermanentCenterInfo = getMinimumHLCenterInfo(permanentGraphArray, permanentHLCenters);
        minPermanentHLPositionByOrigin = (List<List<Integer>>) minHLPermanentCenterInfo.get(0);
        minPermanentHLCostByOrigin = (List<List<Double>>) minHLPermanentCenterInfo.get(1);
    }

    //Output is pair consisting of the closest existing centers by origin and cost to travel to those centers
    //These sites are adjusted by candidate site count (added sequentially afterward)
    private static List<Object> getMinimumCenterInfo(List<List<Double>> permanentGraphArray) {
        List<Integer> minimumCostCenterByOrigin = new ArrayList<>();
        List<Double> minimumCostByOrigin = new ArrayList<>();
        for (int i = 0; i < permanentGraphArray.size(); i++) {
            int minimumCostCenter = 0;
            double minimumCost = permanentGraphArray.get(i).get(0);
            for (int j = 1; j < permanentGraphArray.get(0).size(); j++) {
                double currentCost = permanentGraphArray.get(i).get(j);
                if (currentCost < minimumCost) {
                    minimumCostCenter = j;
                    minimumCost = currentCost;
                }
            }
            minimumCostCenterByOrigin.add(minimumCostCenter);
            minimumCostByOrigin.add(minimumCost);
        }
        return Arrays.asList(minimumCostCenterByOrigin, minimumCostByOrigin);
    }

    //These sites are adjusted by candidate site count (added sequentially afterward)
    public static List<Object> getMinimumHLCenterInfo(List<List<Double>> permanentGraphArray, List<List<Integer>> permanentHLCenters) {
        List<List<Integer>> minHLCenterPositionByOrigin = new ArrayList<>();
        List<List<Double>> minHLCostsByOrigin = new ArrayList<>();
        for (int i = 0; i < permanentHLCenters.size(); i++) {
            List<Integer> levelMinCenterPositionByOrigin = new ArrayList<>();
            List<Double> levelMinCostByOrigin = new ArrayList<>();
            for (int j = 0; j < permanentGraphArray.size(); j++) {
                int levelMinCenter = -1;
                double levelMinCost = Double.POSITIVE_INFINITY;
                for (int k = 0; k < permanentHLCenters.get(i).size(); k++) {
                    double currentCost = permanentGraphArray.get(j).get(permanentHLCenters.get(i).get(k));
                    if (currentCost < levelMinCost) {
                        levelMinCenter = k;
                        levelMinCost = currentCost;
                    }
                }
                levelMinCenterPositionByOrigin.add(levelMinCenter);
                levelMinCostByOrigin.add(levelMinCost);
            }
            minHLCenterPositionByOrigin.add(levelMinCenterPositionByOrigin);
            minHLCostsByOrigin.add(levelMinCostByOrigin);
        }
        return Arrays.asList(minHLCenterPositionByOrigin, minHLCostsByOrigin);
    }

    public int getMinNewCenters() {
        return minNewCenters;
    }

    public int getMaxNewCenters() {
        return maxNewCenters;
    }

    public List<Double> getMinimumCasesByLevel() {
        return minimumCasesByLevel;
    }

    public List<Double> getServicedProportionByLevel() {
        return servicedProportionByLevel;
    }

    public double getMinimumCases() {
        return minimumCases;
    }

    public int getHigherCenterLevels() {
        return higherCenterLevels;
    }

    public int getOriginCount() {
        return originCount;
    }

    public Map<Integer, List<Integer>> getPartitionedOrigins() {
        return partitionedOrigins;
    }

    public List<Double> getCaseCountByOrigin() {
        return caseCountByOrigin;
    }

    public List<List<Double>> getGraphArray() {
        return graphArray;
    }

    public int getPotentialSitesCount() {
        return potentialSitesCount;
    }

    public List<List<Integer>> getSortedNeighbors() {
        return sortedNeighbors;
    }

    public List<List<Integer>> getPermanentHLCenters() {
        return permanentHLCenters;
    }

    public List<Integer> getMinPermanentPositionByOrigin() {
        return minPermanentPositionByOrigin;
    }

    public List<Double> getMinPermanentCostByOrigin() {
        return minPermanentCostByOrigin;
    }

    public List<List<Integer>> getMinPermanentHLPositionByOrigin() {
        return minPermanentHLPositionByOrigin;
    }

    public List<List<Double>> getMinPermanentHLCostByOrigin() {
        return minPermanentHLCostByOrigin;
    }

    public int getPermanentCentersCount() {
        return permanentCentersCount;
    }

    public List<Integer> getPermanentHLCentersCount() {
        return permanentHLCentersCount;
    }

    public int getPermanentAllHLCentersCount() {
        return permanentAllHLCentersCount;
    }
}