import java.util.*;
import java.util.concurrent.ExecutorService;

public class SearchSpace {
    //New center properties
    private static int[] minNewCentersByLevel; //Usually 1.
    private static int[] maxNewCentersByLevel; //Maximum number of cancer centers to try
    private static double[] minimumCasesByLevel; //list in ascending order minimum cases for increasingly tertiary cancer center services
    private static double[] servicedProportionByLevel; //list in ascending order
    private static int[][] sublevelsByLevel;
    private static int[][] superlevelsByLevel;

    //Time-dependent variables
    private static CaseCounts[] caseCountsByLevel; //Leveled array of (timepoint, origin) -> caseCount, 1D array for speed.
    private static double[] timepointWeights;

    //Non-configurable class variables
    private static double minimumCases;// = 10000; //Collapse into minimumCasesByLevel at end
    private static int centerLevels;
    private static int timepointCount;
    private static int originCount;
    private static int potentialSitesCount;// = graphArray.get(0).size() - 1;
    private static Graph graphArray;// = parseCSV(graphLocation);
    private final int[] startingOrigins; //originally private final int[][] partitionedOrigins; //Origins are represented by int
    private final int[] endingOrigins;
    private static List<List<Integer>> sortedNeighbors;// = SimAnnealingNeighbor.sortNeighbors(azimuthArray, haversineArray);

    //Permanent centers by levels to maintain
    private static List<List<Integer>> permanentCentersByLevel; //Sites are represented by Integer

    //Non-configurable permanent center class variables
    private static int[] permanentCentersCountByLevel;
    private static int[][] minPermanentPositionByLevelAndOrigin; //minimum existing centers that must be maintained
    private static double[][] minPermanentCostByLevelAndOrigin; //minimum cost

    public SearchSpace(int[] minNewCentersByLevel, int[] maxNewCentersByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel, List<List<Integer>> levelSequences, int azimuthClassCount,
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
        this.graphArray = new Graph(FileUtils.getInnerDoubleArrayFromCSV(graphLocation, originCount, potentialSitesCount));
        this.startingOrigins = MultithreadingUtils.getTaskSpecificStartingOrigins(originCount, taskCount); //originally this.partitionedOrigins = MultithreadingUtils.orderedPartitionArray(IntStream.range(0, originCount).toArray(), taskCount);
        this.endingOrigins = MultithreadingUtils.getTaskSpecificEndingOrigins(originCount, taskCount);
        this.sortedNeighbors = SimAnnealingNeighbor.sortNeighbors(FileUtils.getInnerAzimuthArrayFromCSV(azimuthLocation, originCount, potentialSitesCount), FileUtils.getInnerDoubleArrayFromCSV(haversineLocation, originCount, potentialSitesCount), azimuthClassCount, taskCount, executor);

        //Time-dependent variables
        double[][] caseCountByTimeAndOrigin = FileUtils.getCaseCountsFromCSV(censusFileLocation, "Cases", originCount);
        this.caseCountsByLevel = new CaseCounts[centerLevels];
        Arrays.fill(caseCountsByLevel, new CaseCounts(caseCountByTimeAndOrigin));
        this.timepointCount = caseCountByTimeAndOrigin.length;
        this.timepointWeights = new double[timepointCount];
        Arrays.fill(timepointWeights, 1 / (double) timepointCount);
    }

    //When there are permanent centers to put in graphArray
    public SearchSpace(int[] minNewCentersByLevel, int[] maxNewCentersByLevel, List<List<Integer>> permanentCentersByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel, List<List<Integer>> levelSequences, int azimuthClassCount,
                                           String censusFileLocation, String permanentGraphLocation, String potentialGraphLocation, String azimuthLocation, String haversineLocation,
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
        this.originCount = FileUtils.getOriginCount(potentialGraphLocation);
        this.potentialSitesCount = FileUtils.getSitesCount(potentialGraphLocation);
        int permanentSitesCount = FileUtils.getSitesCount(permanentGraphLocation);
        double[][] permanentGraphArray = FileUtils.getInnerDoubleArrayFromCSV(permanentGraphLocation, originCount, permanentSitesCount);
        double[][] potentialGraphArray = FileUtils.getInnerDoubleArrayFromCSV(potentialGraphLocation, originCount, potentialSitesCount);
        this.graphArray = new Graph(ArrayOperations.mergeDoubleArrays(potentialGraphArray, permanentGraphArray));
        this.startingOrigins = MultithreadingUtils.getTaskSpecificStartingOrigins(originCount, taskCount); //originally this.partitionedOrigins = MultithreadingUtils.orderedPartitionArray(IntStream.range(0, originCount).toArray(), taskCount);
        this.endingOrigins = MultithreadingUtils.getTaskSpecificEndingOrigins(originCount, taskCount);
        this.sortedNeighbors = SimAnnealingNeighbor.sortNeighbors(FileUtils.getInnerAzimuthArrayFromCSV(azimuthLocation, originCount, potentialSitesCount), FileUtils.getInnerDoubleArrayFromCSV(haversineLocation, originCount, potentialSitesCount), azimuthClassCount, taskCount, executor);

        //Time-dependent variables
        double[][] caseCountByTimeAndOrigin = FileUtils.getCaseCountsFromCSV(censusFileLocation, "Cases", originCount);
        this.caseCountsByLevel = new CaseCounts[centerLevels];
        Arrays.fill(caseCountsByLevel, new CaseCounts(caseCountByTimeAndOrigin));
        this.timepointCount = caseCountByTimeAndOrigin.length;
        this.timepointWeights = new double[timepointCount];
        Arrays.fill(timepointWeights, 1 / (double) timepointCount);

        //Determine permanent centers by level with sites incremented by potential sites count
        List<List<Integer>> adjustedPermanentCentersByLevel = checkPermanentCentersLevelRelations(permanentCentersByLevel);
        this.permanentCentersByLevel = ArrayOperations.incrementList(adjustedPermanentCentersByLevel, potentialSitesCount); //check and enforce consistency with level relations

        //Determine remaining permanent center variables
        permanentCentersCountByLevel = new int[this.getCenterLevels()];
        for (int i = 0; i < this.getCenterLevels(); i++) {
            permanentCentersCountByLevel[i] = adjustedPermanentCentersByLevel.get(i).size();
        }
        PositionsAndCostsByLevelAndOrigin minPermanentCenterInfo = getMinimumCenterInfo(permanentGraphArray, adjustedPermanentCentersByLevel);
        minPermanentPositionByLevelAndOrigin = minPermanentCenterInfo.getPositionsByLevelAndOrigin();
        minPermanentCostByLevelAndOrigin = minPermanentCenterInfo.getCostsByLevelAndOrigin();
    }

    private static LevelRelations parseLevelRelations(List<List<Integer>> levelSequences, int centerLevels) {
        List<Set<Integer>> temporarySublevelsByLevel = new ArrayList<>();
        List<Set<Integer>> temporarySuperlevelsByLevel = new ArrayList<>();
        for (int i = 0; i < centerLevels; i++) {
            temporarySublevelsByLevel.add(new HashSet<>());
            temporarySuperlevelsByLevel.add(new HashSet<>());
        }
        for (List<Integer> levelSequence : levelSequences) {
            if (levelSequence.size() < 2) continue; //trivial sequences
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

    //These sites are adjusted by candidate site count (added sequentially afterward)
    public static PositionsAndCostsByLevelAndOrigin getMinimumCenterInfo(double[][] permanentGraphArray, List<List<Integer>> permanentCenters) {
        int[][] minCenterPositionByLevelAndOrigin = new int[permanentCenters.size()][permanentGraphArray.length];
        double[][] minCostsByLevelAndOrigin = new double[permanentCenters.size()][permanentGraphArray.length];
        for (int level = 0; level < permanentCenters.size(); level++) {
            for (int j = 0; j < permanentGraphArray.length; j++) {
                int levelMinCenter = -1;
                double levelMinCost = Double.POSITIVE_INFINITY;
                for (int position = 0; position < permanentCenters.get(level).size(); position++) {
                    double currentCost = permanentGraphArray[j][permanentCenters.get(level).get(position)];
                    if (currentCost < levelMinCost) {
                        levelMinCenter = position;
                        levelMinCost = currentCost;
                    }
                }
                minCenterPositionByLevelAndOrigin[level][j] = levelMinCenter;
                minCostsByLevelAndOrigin[level][j] = levelMinCost;
            }
        }

        return new PositionsAndCostsByLevelAndOrigin(minCenterPositionByLevelAndOrigin, minCostsByLevelAndOrigin);
    }

    //Adjusts permanent centers to satisfy level relations if not already satisfied.
    public List<List<Integer>> checkPermanentCentersLevelRelations(List<List<Integer>> permanentCentersByLevel) {
        List<List<Integer>> adjustedPermanentCentersByLevel = new ArrayList<>();
        for (int level = 0; level < permanentCentersByLevel.size(); level++) {
            ArrayList<Integer> adjustedPermanentCenters = new ArrayList<>(permanentCentersByLevel.get(level));
            for (int sublevel : sublevelsByLevel[level]) {
                for (Integer permanentCenter : permanentCentersByLevel.get(sublevel)) {
                    if (!permanentCentersByLevel.get(level).contains(permanentCenter)) {
                        System.out.println("Warning: level relations and permanent centers inconsistent. Center " + permanentCenter + " from sublevel " + sublevel + " is not in level " + level + ".");
                        adjustedPermanentCenters.add(permanentCenter);
                    }
                }
            }
            adjustedPermanentCentersByLevel.add(adjustedPermanentCenters);
        }
        return adjustedPermanentCentersByLevel;
    }

    public static int[] getMinNewCentersByLevel() {
        return minNewCentersByLevel;
    }

    public static int[] getMaxNewCentersByLevel() {
        return maxNewCentersByLevel;
    }

    public static double[] getMinimumCasesByLevel() {
        return minimumCasesByLevel;
    }

    public static double[] getServicedProportionByLevel() {
        return servicedProportionByLevel;
    }

    public static double getMinimumCases() {
        return minimumCases;
    }

    public static int getCenterLevels() {
        return centerLevels;
    }

    public static int[][] getSublevelsByLevel() {
        return sublevelsByLevel;
    }

    public static int[][] getSuperlevelsByLevel() {
        return superlevelsByLevel;
    }

    public static int getTimepointCount() {
        return timepointCount;
    }

    public static int getOriginCount() {
        return originCount;
    }

    public int[] getStartingOrigins() {
        return startingOrigins;
    }

    public int[] getEndingOrigins() {
        return endingOrigins;
    }

    public static CaseCounts[] getCaseCountsByLevel() {
        return caseCountsByLevel;
    }

    public static Graph getGraphArray() {
        return graphArray;
    }

    public static double[] getTimepointWeights() {
        return timepointWeights;
    }

    public static int getPotentialSitesCount() {
        return potentialSitesCount;
    }

    public static List<List<Integer>> getSortedNeighbors() {
        return sortedNeighbors;
    }

    public static List<List<Integer>> getPermanentCentersByLevel() {
        return permanentCentersByLevel;
    }

    public static int[][] getMinPermanentPositionByLevelAndOrigin() {
        return minPermanentPositionByLevelAndOrigin;
    }

    public static double[][] getMinPermanentCostByLevelAndOrigin() {
        return minPermanentCostByLevelAndOrigin;
    }

    public static int[] getPermanentCentersCountByLevel() {
        return permanentCentersCountByLevel;
    }

    //Set case counts by level to directly inputted caseCounts
    public void setCaseCountsByLevel(CaseCounts[] caseCountsByLevel){
        this.caseCountsByLevel = caseCountsByLevel;
    }

    //Use one timepoint
    public void setOneTimepoint() {
        this.timepointCount = 1;
        this.timepointWeights = new double[1];
        this.timepointWeights[0] = 1;
    }

    //Arrays of positions and corresponding costs by origin
    private record PositionsAndCostsByLevelAndOrigin(int[][] positionsByLevelAndOrigin, double[][] costsByLevelAndOrigin) {
        public int[][] getPositionsByLevelAndOrigin() {
            return positionsByLevelAndOrigin;
        }
        public double[][] getCostsByLevelAndOrigin() {
            return costsByLevelAndOrigin;
        }
    }
}