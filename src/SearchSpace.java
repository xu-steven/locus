import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SearchSpace {
    //New center properties
    private final int minNewCentersByLevel[]; //Usually 1.
    private final int maxNewCentersByLevel[]; //Maximum number of cancer centers to try
    private final double[] minimumCasesByLevel; //list in ascending order minimum cases for increasingly tertiary cancer center services
    private final double[] servicedProportionByLevel; //list in ascending order
    private final int[][] sublevelsByLevel;
    private final int[][] superlevelsByLevel;

    //Permanent higher level services to maintain
    private List<List<Integer>> permanentHLCenters; //Sites are represented by Integer

    //Non-configurable class variables
    private final double minimumCases;// = 10000;
    private final int centerLevels;
    private final int originCount;
    private int potentialSitesCount;// = graphArray.get(0).size() - 1;
    private final double[] caseCountByOrigin;
    private final double[][] graphArray;// = parseCSV(graphLocation);
    private final int[][] partitionedOrigins; //Origins are represented by int
    private final List<List<Integer>> sortedNeighbors;// = SimAnnealingNeighbor.sortNeighbors(azimuthArray, haversineArray);
    //private final int[][] levelRelations;

    //Non-configurable permanent center class variables
    private int[] minPermanentPositionByOrigin; //minimum existing centers that must be maintained
    private double[] minPermanentCostByOrigin; //minimum cost
    private int[][] minPermanentHLPositionByOrigin;
    private double[][] minPermanentHLCostByOrigin;
    private int permanentCentersCount;
    private int[] permanentHLCentersCount;
    private int permanentAllHLCentersCount;

    public SearchSpace(int[] minNewCentersByLevel, int[] maxNewCentersByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel, List<List<Integer>> levelSequences,
                       String censusFileLocation, String graphLocation, String azimuthLocation, String haversineLocation,
                       int taskCount, ExecutorService executor) {
        this.minNewCentersByLevel = minNewCentersByLevel;
        this.maxNewCentersByLevel = maxNewCentersByLevel;
        this.minimumCasesByLevel = minimumCasesByLevel;
        this.servicedProportionByLevel = servicedProportionByLevel;
        LevelRelations parsedLevelRelations = parseLevelRelations(levelSequences, minimumCasesByLevel.length);
        this.sublevelsByLevel = parsedLevelRelations.getSublevelsByLevel();
        this.superlevelsByLevel = parsedLevelRelations.getSuperlevelsByLevel();

        //Determining remaining variables
        this.minimumCases = minimumCasesByLevel[0];
        this.centerLevels = minimumCasesByLevel.length;
        this.potentialSitesCount = FileUtils.getSitesCount(graphLocation);
        this.originCount = FileUtils.getOriginCount(graphLocation);
        this.caseCountByOrigin = FileUtils.getCaseCountsFromCSV(censusFileLocation, "Population", originCount);
        this.graphArray = FileUtils.getInnerDoubleArrayFromCSV(graphLocation, originCount, potentialSitesCount);
        this.partitionedOrigins = MultithreadingUtils.orderedPartitionArray(IntStream.range(0, originCount).toArray(), taskCount);
        this.sortedNeighbors = SimAnnealingNeighbor.sortNeighbors(FileUtils.getInnerAzimuthArrayFromCSV(azimuthLocation, originCount, potentialSitesCount), FileUtils.getInnerDoubleArrayFromCSV(haversineLocation, originCount, potentialSitesCount), taskCount, executor);
    }

    //When there are permanent centers to put in graphArray
    public SearchSpace(int[] minNewCentersByLevel, int[] maxNewCentersByLevel, List<List<Integer>> permanentHLCenters, double[] minimumCasesByLevel, double[] servicedProportionByLevel, List<List<Integer>> levelSequences,
                                           String censusFileLocation, String permanentGraphLocation, String potentialGraphLocation, String azimuthLocation, String haversineLocation,
                                           int taskCount, ExecutorService executor) {
        this.minNewCentersByLevel = minNewCentersByLevel;
        this.maxNewCentersByLevel = maxNewCentersByLevel;
        this.minimumCasesByLevel = minimumCasesByLevel;
        this.servicedProportionByLevel = servicedProportionByLevel;
        this.permanentHLCenters = permanentHLCenters;
        LevelRelations parsedLevelRelations = parseLevelRelations(levelSequences, minimumCasesByLevel.length);
        this.sublevelsByLevel = parsedLevelRelations.getSublevelsByLevel();
        this.superlevelsByLevel = parsedLevelRelations.getSuperlevelsByLevel();

        //Determining remaining variables
        this.minimumCases = minimumCasesByLevel[0];
        this.centerLevels = minimumCasesByLevel.length;
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
        permanentHLCentersCount = new int[this.getCenterLevels()];
        Set permanentAllHLCenters = new HashSet<>();
        for (int i = 0; i < this.getCenterLevels(); i++) {
            permanentHLCentersCount[i] = permanentHLCenters.get(i).size();
            permanentAllHLCenters.addAll(permanentHLCenters.get(i));
        }
        permanentAllHLCentersCount = permanentAllHLCenters.size();
        HLPositionsAndCostsByOrigin minHLPermanentCenterInfo = getMinimumHLCenterInfo(permanentGraphArray, permanentHLCenters);
        minPermanentHLPositionByOrigin = minHLPermanentCenterInfo.getPositionsByOrigin();
        minPermanentHLCostByOrigin = minHLPermanentCenterInfo.getCostsByOrigin();
    }

    private static LevelRelations parseLevelRelations(List<List<Integer>> levelSequences, int centerLevels) {
        List<Set<Integer>> temporarySublevelsByLevel = new ArrayList<>();
        List<Set<Integer>> temporarySuperlevelsByLevel = new ArrayList<>();
        for (int i = 0; i < centerLevels; i++) {
            temporarySublevelsByLevel.add(new HashSet<>());
            temporarySuperlevelsByLevel.add(new HashSet<>());
        }
        for (List<Integer> levelSequence : levelSequences) {
            if (levelSequence.size() == 1) continue; //trivial sequence
            List<Integer> sublevels = new ArrayList<>(levelSequence.subList(1, levelSequence.size()));
            List<Integer> superlevels = new ArrayList<>();
            temporarySublevelsByLevel.get(levelSequence.get(0)).addAll(sublevels);
            for (int i = 1; i < levelSequence.size() - 1; i++) {
                sublevels.remove(0);
                superlevels.add(levelSequence.get(i - 1));
                temporarySublevelsByLevel.get(levelSequence.get(i)).addAll(sublevels);
                temporarySuperlevelsByLevel.get(levelSequence.get(i)).addAll(superlevels);
            }
            superlevels.add(levelSequence.get(levelSequence.size() - 2));
            temporarySuperlevelsByLevel.get(levelSequence.get(levelSequence.size() - 1)).addAll(superlevels);
        }

        //Update with high order sub and superlevels
        for (int i = 0; i < centerLevels; i++) {
            //Update sublevels with higher order sublevels
            Set<Integer> levelsWithSublevelsToProcess = new HashSet<>(temporarySublevelsByLevel.get(i));
            Set<Integer> processedLevels = new HashSet<>();
            processedLevels.add(i);
            processedLevels.addAll(levelsWithSublevelsToProcess);

            while (!levelsWithSublevelsToProcess.isEmpty()) {
                Integer levelWithSublevelsToProcess = levelsWithSublevelsToProcess.iterator().next();

                //Sublevels to process in future iteration
                Set<Integer> sublevelsToProcess = new HashSet<>(temporarySublevelsByLevel.get(levelWithSublevelsToProcess));
                sublevelsToProcess.removeAll(processedLevels);
                levelsWithSublevelsToProcess.addAll(sublevelsToProcess);

                //Update sublevels by level
                temporarySublevelsByLevel.get(i).addAll(sublevelsToProcess);
                processedLevels.addAll(sublevelsToProcess);

                //Remove processed level
                levelsWithSublevelsToProcess.remove(levelWithSublevelsToProcess);
            }

            //Update superlevels with higher order superlevels
            Set<Integer> levelsWithSuperlevelsToProcess = new HashSet<>(temporarySuperlevelsByLevel.get(i));
            processedLevels = new HashSet<>();
            processedLevels.add(i);
            processedLevels.addAll(levelsWithSuperlevelsToProcess);
            while (!levelsWithSuperlevelsToProcess.isEmpty()) {
                Integer levelWithSuperlevelsToProcess = levelsWithSuperlevelsToProcess.iterator().next();

                //Superlevels to process in future iteration
                Set<Integer> superlevelsToProcess = new HashSet<>(temporarySuperlevelsByLevel.get(levelWithSuperlevelsToProcess));
                superlevelsToProcess.removeAll(processedLevels);
                levelsWithSuperlevelsToProcess.addAll(superlevelsToProcess);

                //Update superlevels by level
                temporarySuperlevelsByLevel.get(i).addAll(superlevelsToProcess);
                processedLevels.addAll(superlevelsToProcess);

                //Remove processed level
                levelsWithSuperlevelsToProcess.remove(levelWithSuperlevelsToProcess);
            }
        }

        //All sublevels and superlevels by level
        int[][] sublevelsByLevel = new int[centerLevels][];
        int[][] superlevelsByLevel = new int[centerLevels][];
        for (int i = 0; i < centerLevels; i++) {
            sublevelsByLevel[i] = temporarySublevelsByLevel.get(i).stream().mapToInt(Integer::intValue).toArray();
            superlevelsByLevel[i] = temporarySuperlevelsByLevel.get(i).stream().mapToInt(Integer::intValue).toArray();
        }

        //Generate output as an array where int[a][b] = 1 if sublevel, 0 if unrelated, and -1 if superlevel
        int[][] levelRelations = new int[centerLevels][centerLevels];
        for (int i = 0; i < centerLevels; i++) {
            Set<Integer> currentLevelSublevels = temporarySublevelsByLevel.get(i);
            Set<Integer> currentLevelSuperlevels = temporarySuperlevelsByLevel.get(i);
            for (int j = 0; j < centerLevels; j++) {
                if (currentLevelSublevels.contains(j) && currentLevelSuperlevels.contains(j)) {
                    System.out.println("Circular level from level " + i + " to level " + j + ".");
                    levelRelations[i][j] = -1;
                } else if (currentLevelSublevels.contains(j)) {
                    levelRelations[i][j] = 1;
                } else if (currentLevelSuperlevels.contains(j)) {
                    levelRelations[i][j] = -1;
                } else {
                    levelRelations[i][j] = 0;
                }
            }
        }

        return new LevelRelations(sublevelsByLevel, superlevelsByLevel);
    }

    private record LevelRelations(int[][] sublevelsByLevel, int[][] superlevelsByLevel) {
        public int[][] getSublevelsByLevel() {
            return sublevelsByLevel;
        }
        public int[][] getSuperlevelsByLevel() {
            return superlevelsByLevel;
        }
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

    public int[] getMinNewCentersByLevel() {
        return minNewCentersByLevel;
    }

    public int[] getMaxNewCentersByLevel() {
        return maxNewCentersByLevel;
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

    public int getCenterLevels() {
        return centerLevels;
    }

    public int[][] getSublevelsByLevel() {
        return sublevelsByLevel;
    }

    public int[][] getSuperlevelsByLevel() {
        return superlevelsByLevel;
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