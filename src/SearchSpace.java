import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

public class SearchSpace {
    //New center properties
    private final int minNewCenters; //Usually 1.
    private final int maxNewCenters; //Maximum number of cancer centers to try
    private final double[] minimumCasesByLevel; //list in ascending order minimum cases for increasingly tertiary cancer center services
    private final double[] servicedProportionByLevel; //list in ascending order

    //Permanent higher level services to maintain
    private List<List<Integer>> permanentHLCenters; //Sites are represented by Integer

    //Non-configurable class variables
    private final double minimumCases;// = 10000;
    private final int higherCenterLevels;
    private final int originCount;
    private int potentialSitesCount;// = graphArray.get(0).size() - 1;
    private final double[] caseCountByOrigin;
    private final double[][] graphArray;// = parseCSV(graphLocation);
    private final int[][] partitionedOrigins; //Origins are represented by int
    private final List<List<Integer>> sortedNeighbors;// = SimAnnealingNeighbor.sortNeighbors(azimuthArray, haversineArray);

    //Non-configurable permanent center class variables
    private int[] minPermanentPositionByOrigin; //minimum existing centers that must be maintained
    private double[] minPermanentCostByOrigin; //minimum cost
    private int[][] minPermanentHLPositionByOrigin;
    private double[][] minPermanentHLCostByOrigin;
    private int permanentCentersCount;
    private int[] permanentHLCentersCount;
    private int permanentAllHLCentersCount;

    public SearchSpace(int minNewCenters, int maxNewCenters, double[] minimumCasesByLevel, double[] servicedProportionByLevel,
                       String censusFileLocation, String graphLocation, String azimuthLocation, String haversineLocation,
                       int taskCount, ExecutorService executor) {
        this.minNewCenters = minNewCenters;
        this.maxNewCenters = maxNewCenters;
        this.minimumCasesByLevel = minimumCasesByLevel;
        this.servicedProportionByLevel = servicedProportionByLevel;

        //Determining remaining variables
        this.minimumCases = minimumCasesByLevel[0];
        this.higherCenterLevels = minimumCasesByLevel.length - 1;
        this.potentialSitesCount = FileUtils.getSitesCount(graphLocation);
        this.originCount = FileUtils.getOriginCount(graphLocation);
        this.caseCountByOrigin = FileUtils.getCaseCountsFromCSV(censusFileLocation, "Population", originCount);
        this.graphArray = FileUtils.getInnerDoubleArrayFromCSV(graphLocation, originCount, potentialSitesCount);
        this.partitionedOrigins = MultithreadingUtils.orderedPartitionArray(IntStream.range(0, originCount).toArray(), taskCount);
        this.sortedNeighbors = SimAnnealingNeighbor.sortNeighbors(FileUtils.getInnerAzimuthArrayFromCSV(azimuthLocation, originCount, potentialSitesCount), FileUtils.getInnerDoubleArrayFromCSV(haversineLocation, originCount, potentialSitesCount), taskCount, executor);
    }

    //When there are permanent centers to put in graphArray
    public SearchSpace(int minNewCenters, int maxNewCenters, List<List<Integer>> permanentHLCenters, double[] minimumCasesByLevel, double[] servicedProportionByLevel,
                                           String censusFileLocation, String permanentGraphLocation, String potentialGraphLocation, String azimuthLocation, String haversineLocation,
                                           int taskCount, ExecutorService executor) {
        this.minNewCenters = minNewCenters;
        this.maxNewCenters = maxNewCenters;
        this.minimumCasesByLevel = minimumCasesByLevel;
        this.servicedProportionByLevel = servicedProportionByLevel;
        this.permanentHLCenters = permanentHLCenters;

        //Determining remaining variables
        this.minimumCases = minimumCasesByLevel[0];
        this.higherCenterLevels = minimumCasesByLevel.length - 1;
        this.originCount = FileUtils.getOriginCount(potentialGraphLocation);
        this.potentialSitesCount = FileUtils.getSitesCount(potentialGraphLocation);
        this.caseCountByOrigin = FileUtils.getCaseCountsFromCSV(censusFileLocation, "Population", originCount);
        double[][] permanentGraphArray = FileUtils.getInnerDoubleArrayFromCSV(permanentGraphLocation, originCount, FileUtils.getSitesCount(permanentGraphLocation));
        double[][] potentialGraphArray = FileUtils.getInnerDoubleArrayFromCSV(potentialGraphLocation, originCount, potentialSitesCount);
        this.graphArray = ArrayOperations.mergeDoubleArrays(potentialGraphArray, permanentGraphArray);
        partitionedOrigins = MultithreadingUtils.orderedPartitionArray(IntStream.range(0, originCount).toArray(), taskCount);
        this.sortedNeighbors = SimAnnealingNeighbor.sortNeighbors(FileUtils.getInnerAzimuthArrayFromCSV(azimuthLocation, originCount, potentialSitesCount), FileUtils.getInnerDoubleArrayFromCSV(haversineLocation, originCount, potentialSitesCount), taskCount, executor);

        //Determining remaining permanent center variables
        permanentCentersCount = permanentGraphArray[0].length;
        PositionsAndCostsByOrigin minimumPermanentCenterInfo = getMinimumCenterInfo(permanentGraphArray);
        minPermanentPositionByOrigin = minimumPermanentCenterInfo.getPositionsByOrigin();
        minPermanentCostByOrigin = minimumPermanentCenterInfo.getCostsByOrigin();
        permanentHLCentersCount = new int[this.getHigherCenterLevels()];
        Set permanentAllHLCenters = new HashSet<>();
        for (int i = 0; i < this.getHigherCenterLevels(); i++) {
            permanentHLCentersCount[i] = permanentHLCenters.get(i).size();
            permanentAllHLCenters.addAll(permanentHLCenters.get(i));
        }
        permanentAllHLCentersCount = permanentAllHLCenters.size();
        HLPositionsAndCostsByOrigin minHLPermanentCenterInfo = getMinimumHLCenterInfo(permanentGraphArray, permanentHLCenters);
        minPermanentHLPositionByOrigin = minHLPermanentCenterInfo.getPositionsByOrigin();
        minPermanentHLCostByOrigin = minHLPermanentCenterInfo.getCostsByOrigin();
    }

    //Output is pair consisting of the closest existing centers by origin and cost to travel to those centers. These sites are adjusted by candidate site count (added sequentially afterward).
    private static PositionsAndCostsByOrigin getMinimumCenterInfo(double[][] permanentGraphArray) {
        int[] minimumCostCenterByOrigin = new int[permanentGraphArray.length];
        double[] minimumCostByOrigin = new double[permanentGraphArray.length];
        for (int i = 0; i < permanentGraphArray.length; i++) {
            int minimumCostCenter = 0;
            double minimumCost = permanentGraphArray[i][0];
            for (int j = 1; j < permanentGraphArray[i].length; j++) {
                double currentCost = permanentGraphArray[i][j];
                if (currentCost < minimumCost) {
                    minimumCostCenter = j;
                    minimumCost = currentCost;
                }
            }
            minimumCostCenterByOrigin[i] = minimumCostCenter;
            minimumCostByOrigin[i] = minimumCost;
        }
        return new PositionsAndCostsByOrigin(minimumCostCenterByOrigin, minimumCostByOrigin);
    }

    //Arrays of positions and corresponding costs by origin
    private record PositionsAndCostsByOrigin(int[] positionsByOrigin, double[] costsByOrigin) {
        public int[] getPositionsByOrigin() {
            return positionsByOrigin;
        }
        public double[] getCostsByOrigin() {
            return costsByOrigin;
        }
    }

    //These sites are adjusted by candidate site count (added sequentially afterward)
    public static HLPositionsAndCostsByOrigin getMinimumHLCenterInfo(double[][] permanentGraphArray, List<List<Integer>> permanentHLCenters) {
        int[][] minHLCenterPositionByOrigin = new int[permanentHLCenters.size()][permanentGraphArray.length];
        double[][] minHLCostsByOrigin = new double[permanentHLCenters.size()][permanentGraphArray.length];
        for (int i = 0; i < permanentHLCenters.size(); i++) {
            for (int j = 0; j < permanentGraphArray.length; j++) {
                int levelMinCenter = -1;
                double levelMinCost = Double.POSITIVE_INFINITY;
                for (int k = 0; k < permanentHLCenters.get(i).size(); k++) {
                    double currentCost = permanentGraphArray[j][permanentHLCenters.get(i).get(k)];
                    if (currentCost < levelMinCost) {
                        levelMinCenter = k;
                        levelMinCost = currentCost;
                    }
                }
                minHLCenterPositionByOrigin[i][j] = levelMinCenter;
                minHLCostsByOrigin[i][j] = levelMinCost;
            }
        }
        return new HLPositionsAndCostsByOrigin(minHLCenterPositionByOrigin, minHLCostsByOrigin);
    }

    //Higher level positions and costs by level and origin
    private record HLPositionsAndCostsByOrigin(int[][] positionsByLevelAndOrigin, double[][] costsByLevelAndOrigin) {
        public int[][] getPositionsByOrigin() {
            return positionsByLevelAndOrigin;
        }
        public double[][] getCostsByOrigin() {
            return costsByLevelAndOrigin;
        }
    }

    public int getMinNewCenters() {
        return minNewCenters;
    }

    public int getMaxNewCenters() {
        return maxNewCenters;
    }

    public double[] getMinimumCasesByLevel() {
        return minimumCasesByLevel;
    }

    public double[] getServicedProportionByLevel() {
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

    public int[][] getPartitionedOrigins() {
        return partitionedOrigins;
    }

    public double[] getCaseCountByOrigin() {
        return caseCountByOrigin;
    }

    public double[][] getGraphArray() {
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

    public int[] getMinPermanentPositionByOrigin() {
        return minPermanentPositionByOrigin;
    }

    public double[] getMinPermanentCostByOrigin() {
        return minPermanentCostByOrigin;
    }

    public int[][] getMinPermanentHLPositionByOrigin() {
        return minPermanentHLPositionByOrigin;
    }

    public double[][] getMinPermanentHLCostByOrigin() {
        return minPermanentHLCostByOrigin;
    }

    public int getPermanentCentersCount() {
        return permanentCentersCount;
    }

    public int[] getPermanentHLCentersCount() {
        return permanentHLCentersCount;
    }

    public int getPermanentAllHLCentersCount() {
        return permanentAllHLCentersCount;
    }
}