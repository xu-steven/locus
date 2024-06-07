import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class Simulator {
    //Simulated annealing instance
    static SimAnnealingWithoutPermanentCenters simAnnealer;

    //Simulates case by origin
    static CaseSimulator caseSimulator;

    //Number of tasks should be >= threads
    static int taskCount;

    public Simulator(String demographicsLocation, String caseIncidenceRateLocation, String censusFileLocation, String graphLocation, String azimuthLocation, String haversineLocation,
                     double[] minimumCasesByLevel, double[] servicedProportionByLevel, int[] minimumCenterCountByLevel, int[] maximumCenterCountByLevel, List<List<Integer>> levelSequences,
                     double initialTemp, double finalTemp, double coolingRate, int azimuthClassCount, int finalNeighborhoodSize, int finalNeighborhoodSizeIteration, int threadCount, int taskCount) {
        //Create simulated annealing instance
        this.simAnnealer = new SimAnnealingWithoutPermanentCenters(censusFileLocation, graphLocation, azimuthLocation, haversineLocation,
                minimumCasesByLevel, servicedProportionByLevel, minimumCenterCountByLevel, maximumCenterCountByLevel, levelSequences,
                initialTemp, finalTemp, coolingRate, azimuthClassCount, finalNeighborhoodSize, finalNeighborhoodSizeIteration, taskCount, threadCount);
        this.simAnnealer.searchParameters.setOneTimepoint();

        //Create a case simulator to simulate cases in population
        this.caseSimulator = new CaseSimulator(demographicsLocation, caseIncidenceRateLocation);

        //Task count
        this.taskCount = taskCount;
    }

    public static void main(String[] args) throws InterruptedException {
        //Demographics file
        String demographicsLocation = "C:\\Users\\Steven\\IdeaProjects\\Optimization Project Alpha\\demographic projections\\alberta2021_demographics.csv";

        //Case incidence rate file
        String caseIncidenceRateLocation = "C:\\Users\\Steven\\IdeaProjects\\Optimization Project Alpha\\cancer projection\\alberta_cancer_incidence.csv";

        //File locations
        String censusFileLocation = "C:\\Users\\Steven\\IdeaProjects\\Optimization Project Alpha\\alberta2021_origins.csv";
        String graphLocation = censusFileLocation.replace("_origins.csv", "_graph.csv");
        String azimuthLocation = censusFileLocation.replace("_origins.csv", "_potential_azimuth.csv");
        String haversineLocation = censusFileLocation.replace("_origins.csv", "_potential_haversine.csv");

        //Search space parameters
        double[] minimumCasesByLevel = {(double) 0, (double) 0, (double) 0};
        double[] servicedProportionByLevel = {0.2, 0.5, 0.3};
        int[] minimumCenterCountByLevel = {1, 1, 1};
        int[] maximumCenterCountByLevel = {17, 6, 2};
        List<List<Integer>> levelSequences = new ArrayList<>();
        levelSequences.add(Arrays.asList(0, 1));
        levelSequences.add(Arrays.asList(1, 2));

        //Simulated annealing configuration
        double initialTemp = 10000;
        double finalTemp = 0.01;
        double coolingRate = 0.9995;
        int azimuthClassCount = 6;
        int finalNeighborhoodSize = -1; //Determining finalNeighborhoodSize based on number of centers to optimize if -1
        int finalNeighborhoodSizeIteration = 20000;

        //Multithreading configuration
        int threadCount = 24;
        int taskCount = 24;

        //Create a simulator
        new Simulator(demographicsLocation, caseIncidenceRateLocation, censusFileLocation, graphLocation, azimuthLocation, haversineLocation,
                minimumCasesByLevel, servicedProportionByLevel, minimumCenterCountByLevel, maximumCenterCountByLevel, levelSequences,
                initialTemp, finalTemp, coolingRate, azimuthClassCount, finalNeighborhoodSize, finalNeighborhoodSizeIteration, threadCount, taskCount);

        //Unoptimized sites
        //List<Integer> unoptimizedSites = Arrays.asList(0, 1, 2, 3);
        //List<List<Integer>> unoptimizedSitesByLevel = new ArrayList<>(Arrays.asList(unoptimizedSites, unoptimizedSites, unoptimizedSites));
        List<Integer> academicCenters = new ArrayList<>(Arrays.asList(683, 4162)); //TBCC and CCI
        List<Integer> regionalCenters = new ArrayList<>(academicCenters); //Regional cancer centers
        regionalCenters.addAll(Arrays.asList(2818, 182, 55, 6179));
        List<Integer> communityCenters = new ArrayList<>(regionalCenters); //Community cancer centers
        communityCenters.addAll(Arrays.asList(5707, 6113, 5307, 5436, 5599, 4910, 3272, 3170, 528, 5660, 2085));
        List<List<Integer>> unoptimizedSitesByLevel = new ArrayList<>(Arrays.asList(communityCenters, regionalCenters, academicCenters));

        //Configuration
        int simulations = 2;
        int iterationsOfSimulatedAnnealingSearch = 2;

        //Compare with optimized cases
        System.out.println("Comparison result " + simulatedCostComparison(unoptimizedSitesByLevel, simulations, iterationsOfSimulatedAnnealingSearch));

        simAnnealer.executor.shutdown();
        return;
    }

    public static LeveledSiteConfiguration optimizeCentersWithSimulatedCases(int iterationsOfSimulatedAnnealingSearch) throws InterruptedException {
        //Run simulated annealing with expected case counts
        LeveledSiteConfiguration minimumSolution = null;
        for (int i = 0; i < iterationsOfSimulatedAnnealingSearch; i++) {//take best of n runs
            LeveledSiteConfiguration solution = simAnnealer.leveledOptimizeCenters(taskCount);
            System.out.println("Final cost is " + solution.getCost() + " on centers " + solution.getSitesByLevel());
            if (minimumSolution == null) {
                minimumSolution = solution;
            } else if (solution.getCost() < minimumSolution.getCost()) {
                minimumSolution = solution;
            }
        }
        return minimumSolution;
    }

    public static List<List<Double>> simulatedCostComparison(List<List<Integer>> unoptimizedSitesByLevel, int simulations, int iterationsOfSimulatedAnnealingSearch) throws InterruptedException {
        //Get number of levels
        int levels = unoptimizedSitesByLevel.size();

        List<List<Double>> costComparisonBySimulation = new ArrayList<>(simulations);
        for (int i = 0; i < simulations; i++) {
            //Replace with simulated case counts
            CaseCounts simulatedCaseCounts = caseSimulator.simulateCases();
            CaseCounts[] inputCaseCounts = new CaseCounts[levels];
            Arrays.fill(inputCaseCounts, simulatedCaseCounts);
            simAnnealer.searchParameters.setCaseCountsByLevel(inputCaseCounts);

            //Run simulated annealing
            LeveledSiteConfiguration minimumConfiguration = optimizeCentersWithSimulatedCases(iterationsOfSimulatedAnnealingSearch);

            //Simulate new case counts
            CaseCounts newSimulatedCaseCounts = caseSimulator.simulateCases();
            Arrays.fill(inputCaseCounts, newSimulatedCaseCounts);
            simAnnealer.searchParameters.setCaseCountsByLevel(inputCaseCounts);

            //Compute cost of optimized configuration
            double optimizedTotalCost = cost(minimumConfiguration.getSitesByLevel(), simAnnealer.searchParameters, taskCount, simAnnealer.executor);

            //Non-optimized sites to compare
            double unoptimizedTotalCost = cost(unoptimizedSitesByLevel, simAnnealer.searchParameters, taskCount, simAnnealer.executor);

            //Add triplet (simulated annealing optimized cost, unoptimized cost, total simulated case count) to cost comparison by simulation
            costComparisonBySimulation.add(new ArrayList<>(Arrays.asList(optimizedTotalCost, unoptimizedTotalCost, ArrayOperations.sumDoubleArray(simulatedCaseCounts.caseCountByOrigin))));
        }
        return costComparisonBySimulation;
    }

    public static ExpectedCostComparisonResult expectedCostComparison(List<List<Integer>> unoptimizedSitesByLevel, int iterationsOfSimulatedAnnealingSearch) throws InterruptedException {
        //Get number of levels
        int levels = unoptimizedSitesByLevel.size();

        //Replace with expected case counts
        CaseCounts expectedCaseCounts = caseSimulator.expectedCases();
        CaseCounts[] inputCaseCounts = new CaseCounts[levels];
        Arrays.fill(inputCaseCounts, expectedCaseCounts);
        simAnnealer.searchParameters.setCaseCountsByLevel(inputCaseCounts);
        simAnnealer.searchParameters.setOneTimepoint();

        //Run simulated annealing with expected case counts
        LeveledSiteConfiguration minimumConfiguration = optimizeCentersWithSimulatedCases(iterationsOfSimulatedAnnealingSearch);

        //Non-optimized sites to compare
        double unoptimizedTotalCost = cost(unoptimizedSitesByLevel, simAnnealer.searchParameters, taskCount, simAnnealer.executor);

        //Return pair (simulated annealing cost, unoptimized cost)
        List<Double> costComparison = new ArrayList<>(Arrays.asList(minimumConfiguration.getCost(),unoptimizedTotalCost));
        return new ExpectedCostComparisonResult(minimumConfiguration.getSitesByLevel(), costComparison);
    }

    //Compute cost
    public static double cost(List<List<Integer>> sitesByLevel, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Compute initial cost
        CasesAndCostMap[] costMapByLevel = new CasesAndCostMap[searchParameters.getCenterLevels()];
        for (int level = 0; level < searchParameters.getCenterLevels(); ++level) {
            CostMapAndPositions initialResult = createCostMap(sitesByLevel.get(level), searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                    taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
            costMapByLevel[level] = initialResult.getCasesAndCostMap();
        }
        return CostCalculator.computeCost(costMapByLevel, sitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
    }

    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static CostMapAndPositions createCostMap(List<Integer> sites, int timepointCount, int originCount, CaseCounts caseCountByOrigin, Graph graphArray,
                                                    int taskCount, int[] startingOrigins, int[] endingOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new CostMapAndPositions(null, null);
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        CasesAndCostMap[] partitionedMinimumCostMap = new CasesAndCostMap[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                CasesAndCostMap partitionMinimumCostMap = new CasesAndCostMap(timepointCount, siteCount);
                for (int j = startingOrigins[finalI]; j < endingOrigins[finalI]; j++) {
                    int minimumCostPosition = 0;
                    double minimumCostUnadjusted = graphArray.getEdgeLength(j, sites.get(0)); //Closest center travel cost, not adjusted for population or cancer center scaling
                    for (int k = 1; k < siteCount; ++k) {
                        double currentCostUnadjusted = graphArray.getEdgeLength(j, sites.get(k));
                        if (currentCostUnadjusted < minimumCostUnadjusted) {
                            minimumCostPosition = k;
                            minimumCostUnadjusted = currentCostUnadjusted;
                        }
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    partitionMinimumCostMap.updateCasesAndCost(minimumCostPosition, minimumCostUnadjusted, j, caseCountByOrigin);
                }
                partitionedMinimumCostMap[finalI] = partitionMinimumCostMap;
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e){
            throw new AssertionError("Unexpected interruption", e);
        }
        //HashMap<Integer, CasesAndCost> combinedMinimumCostMap = MultithreadingUtils.combinePartitionedMinimumCostMap(partitionedMinimumCostMap, siteCount, taskCount);
        CasesAndCostMap combinedMinimumCostMap = new CasesAndCostMap(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
    }

    //Add arrays together
    public static double[] addArrays(double[]... arrays) {
        double[] sumOfArrays = new double[arrays[0].length];
        for (int i = 0; i < sumOfArrays.length; i++) {
            double value = 0;
            for (double[] array : arrays) {
                value += array[i];
            }
            sumOfArrays[i] = value;
        }
        return sumOfArrays;
    }

    //Subtract two arrays
    public static double[] subtractArrays(double[] firstArray, double[] secondArray) {
        double[] firstMinusSecondArray = new double[firstArray.length];
        for (int i = 0; i < firstMinusSecondArray.length; i++) {
            firstMinusSecondArray[i] = firstArray[i] - secondArray[i];
        }
        return firstMinusSecondArray;
    }

    //Find average of double array
    public static double averageArray(double[] array) {
        double sum = 0;
        for (double d : array) {
            sum += d;
        }
        return sum / array.length;
    }

    public record ExpectedCostComparisonResult(List<List<Integer>> optimizedSitesByLevel, List<Double> costComparison) {
    }
}