import org.paukov.combinatorics3.Generator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SiteOptimizer {
    //Frequency of updates. Recommend 1000 for Exhaustive and >1000 depending on number of threads for heuristic.
    public static Integer updateFrequency = 1000;

    //Exhaustive (brute force) or Heuristic (sim annealing)
    private static String searchStrategy = "Heuristic";

    //Number of threads to be used
    private static Integer threadCount = 3;

    //Repetitions per center count, only used in heuristic mode
    private static Integer redundancy = 50; //Final check to maximize convergence for most relevant solutions

    //File locations
    static String censusFileLocation = "M:\\Optimization Project\\alberta2016.csv";
    static String graphLocation = censusFileLocation.replace(".csv", "_graph.csv");
    static String azimuthLocation = censusFileLocation.replace(".csv", "_azimuth.csv");
    static String haversineLocation = censusFileLocation.replace(".csv", "_haversine.csv");

    //Soft minimum cases per cancer center
    static double minimumCases = 10000; //Soft minimum on cases

    //Center lower and upper bounds
    static Integer minNewCenters = 6; //Usually 1.
    static Integer maxNewCenters = 7; //Usually put large number for SA as algorithm will stop automatically. Do not recommend possible sites choose maxNewCenters to exceed 500m (around 100 hours) for exhaustive.

    //Initial and final simulated annealing temperatures
    static double initialTemp = 1000000;
    static double finalTemp = 1;

    //Cooling rate
    static double coolingRate = 0.9995;

    //Final neighborhood size and iteration achieved. Put finalNeighborhoodSize = -1 if desire final neighborhood size to be determined by the number of centers to optimize.
    static Integer finalNeighborhoodSize = -1;
    static Integer finalNeighborhoodSizeIteration = 20000;

    //Generate arrays. Ultimately, this can be done in a constructor for SiteOptimizer class.
    static List<List<String>> censusArray = parseCSV(censusFileLocation);
    static List<List<String>> graphArray = parseCSV(graphLocation);
    static List<List<String>> azimuthArray = parseCSV(azimuthLocation);
    static List<List<String>> haversineArray = parseCSV(haversineLocation);

    //Number of sites at which a cancer center can be situated. Can be internalized in constructor.
    static int potentialSitesCount = graphArray.get(0).size() - 1;

    public static void main(String[] args) throws InterruptedException {
        double minimumCost = Double.POSITIVE_INFINITY;
        List<Integer> minimumPositions = new ArrayList<>();
        if (searchStrategy == "Exhaustive") {
            new ExhaustiveSearch(minimumCases, censusArray, graphArray);
            ExhaustiveSearch.setUpdateFrequency(updateFrequency); //development only
            for (int i = minNewCenters; i <= maxNewCenters; ++i) {
                List<Object> sol = exhaustiveOptimizeNCenters(i);
                double currentMinimumCost = (double) sol.get(0);
                if (currentMinimumCost < minimumCost) {
                    minimumCost = currentMinimumCost;
                    minimumPositions = (List<Integer>) sol.get(1);
                } else {
                    break;
                }
            }
        } else if (searchStrategy == "Heuristic") {
            //new SimAnnealingShrinkingSearch(minimumCases, initialTemp, finalTemp, finalNeighborhoodSize, finalNeighborhoodSizeIteration, coolingRate, censusArray, graphArray, azimuthArray, haversineArray);
            DevelopmentUtils.setUpdateFrequency(updateFrequency); //development only
            List<Object> sol = simAnnealingOptimizeCenters(minNewCenters, maxNewCenters); //lower/upper bounds in number of centers to test
            minimumCost = (double) sol.get(0);
            minimumPositions = (List<Integer>) sol.get(1);
        }
        System.out.println("Minimum cost " + minimumCost + " at positions " + minimumPositions);
    }

    //Multithreaded variant of optimization, optimize with n centers
    public static List<Object> exhaustiveOptimizeNCenters(Integer n) throws InterruptedException {
        //Map of minimum positions and costs by partition, total number of partitions = threadCount
        ConcurrentHashMap<List<Integer>, Double> minimumMap = new ConcurrentHashMap<>();

        //Create list of all potential sites
        List<Integer> potentialSites = IntStream.rangeClosed(1, potentialSitesCount).boxed().collect(Collectors.toList());

        //Partition possible site combinations into threadCount sublists
        List<List<List<Integer>>> partitionedSiteCombinations = partitionList(Generator.combination(potentialSites).simple(n).stream().toList(), threadCount);

        //Assign one partition to each thread to compute minimum cost per partition. Put in minimumMap with entry corresponding to minimum in partition.
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            List<List<Integer>> partitionToOptimize = partitionedSiteCombinations.get(i);
            executor.submit(() -> {
                long startTimer = System.currentTimeMillis(); // development only
                long timer = System.currentTimeMillis(); // development only
                Integer positionsComputed = 0; // development only
                Integer totalPositions = partitionToOptimize.size(); // development only
                List<Integer> minimumPositions = new ArrayList<>();
                double minimumCost = Double.POSITIVE_INFINITY;
                for (List positions : partitionToOptimize) {
                    double currentCost = ExhaustiveSearch.totalCost(positions, graphArray, censusArray);
                    if (currentCost < minimumCost) {
                        minimumPositions = positions;
                        minimumCost = currentCost;
                    }
                    long elapsedTime = System.currentTimeMillis() - timer; //development only
                    positionsComputed += 1; //development only
                    if (elapsedTime > updateFrequency) { //development only
                        System.out.println("Done computing " + positionsComputed + " of " + totalPositions + " on thread" + Thread.currentThread().getName()); // development only
                        timer = System.currentTimeMillis(); // development only
                    } //development only
                }
                minimumMap.put(minimumPositions, minimumCost);
                long totalTime = System.currentTimeMillis() - startTimer; // development only
                System.out.println("Done running in " + totalTime + "ms"); // development only
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown(); //does not accept new jobs after completing above submissions, can switch order with latch.await() if desired.
        System.out.println("Minimum in each partition " + minimumMap);

        //Get minimum of all partitions as output
        Map.Entry<List<Integer>, Double> minimumEntry = getMinimumEntry(minimumMap);
        List<Integer> finalMinimumPositions = minimumEntry.getKey();
        double finalMinimumCost = minimumEntry.getValue();
        List<Object> output = new ArrayList<>(Arrays.asList(finalMinimumCost, finalMinimumPositions)); //contains two elements: Double minimum cost and List<Integer> minimum positions.
        return output;
    }

    //Multithreaded variant of optimization by adding new centers with lower and upper bounds in number of centers
    public static List<Object> simAnnealingOptimizeCenters(Integer minNewCenters, Integer maxNewCenters) {
        //Maximum number of cancers needed to be searched. Once there is a case of two increases
        AtomicInteger maxNewCentersNeededToOptimize = new AtomicInteger(maxNewCenters);

        //Map of minimum positions and costs by number of new centers, to the extent of the search
        ConcurrentHashMap<List<Integer>, Double> minimumMap = new ConcurrentHashMap<>();

        //Optimize with each thread performing simulated annealing on different number of centers, starting from minimum and working up to maximum new centers
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = minNewCenters; i <= maxNewCenters; i++) {
            int finalI = i;
            executor.submit(() -> {
                try {
                    //Optimize for i centers. Add optimal i positions and its cost to minimumMap.
                    System.out.println("Starting optimization with " + finalI + " center(s).");
                    SiteConfiguration solutionWithNCenters = SimAnnealingWithoutPermanentCenters.optimizeNCenters(finalI, 6);
                    double minimumCost = solutionWithNCenters.getCost();
                    List<Integer> minimumPositions = solutionWithNCenters.getSites();
                    minimumMap.put(minimumPositions, minimumCost);
                    System.out.println("Done optimizing with " + finalI + " center(s). Minimum cost " + minimumCost + " at " + minimumPositions + ".");

                    //Check if we can reduce maxNewCenterSearch, i.e. clear enough that center count is excessive (see checkEnoughCenters method).
                    if (checkEnoughCenters(minimumMap)) {
                        //Change maxNewCenterSearch if finalI (current number of centers) less than maxNewCenters.
                        maxNewCentersNeededToOptimize.getAndUpdate(x -> finalI < x ? finalI : x);
                        System.out.println("Found smaller maxNeededNewCenter count to search " + maxNewCentersNeededToOptimize);
                        //Check if every number of centers from minimum to maxNeededNewCenterSearch already optimized.
                        if (isAdequateMinimumMap(minimumMap, minNewCenters, maxNewCentersNeededToOptimize)) {
                            System.out.println("Minimum map is adequate. Interrupting ongoing tasks and clearing all queued tasks.");
                            executor.shutdownNow();
                        }
                    }
                } catch (InterruptedException e) {
                    System.out.println("Canceling optimization with " + finalI + " centers as optimization of fewer centers is expected to allow lower cost.");
                }
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(100000, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        //Find the lowest cost arrangement in minimumMap, which maps different optimal positions (all different number of positions) to cost.
        Map.Entry<List<Integer>, Double> minimumEntry = getMinimumEntry(minimumMap);
        List<Integer> finalMinimumPositions = minimumEntry.getKey();
        double finalMinimumCost = minimumEntry.getValue();
        List<Object> output = new ArrayList<>(Arrays.asList(finalMinimumCost, finalMinimumPositions)); //contains two elements: Double minimum cost and List<Integer> minimum positions.
        return output;
    }

    //Multithreaded variant of optimization by adding new centers with lower and upper bounds in number of centers, with redundancy
    public static List<Object> redundantOptimizeCenters(Integer minNewCenters, Integer maxNewCenters) {
        //Maximum number of cancers needed to be searched. Once there is a case of two increases
        AtomicInteger maxNewCentersNeededToOptimize = new AtomicInteger(maxNewCenters);

        //Map of minimum positions and costs by number of new centers, to the extent of the search
        ConcurrentHashMap<List<Integer>, Double> minimumMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<List<Integer>, Double> allMap = new ConcurrentHashMap<>(); //development only

        //List of costs temporary minima on repeated sampling, sorted by the number of centers
        AtomicInteger firstFailureCenterCount = new AtomicInteger();
        firstFailureCenterCount.set(-1);

        //Scanning redundancy in step 1
        Integer scanningRedundancy = 3;

        //Optimize with each thread performing simulated annealing on different number of centers, starting from minimum and working up to maximum new centers
        //Step 1: find number of centers leading failure to converge to same cost or return optimal number of centers if no failures are detected
        ExecutorService executorStepOne = Executors.newFixedThreadPool(threadCount);
        for (int i = minNewCenters; i <= maxNewCenters; i++) {
            int finalI = i;
            executorStepOne.submit(() -> {
                try {
                    //Optimize for i centers. Add optimal i positions and its cost to minimumMap.
                    System.out.println("Starting optimization with " + finalI + " center(s).");
                    SiteConfiguration solutionWithNCenters = SimAnnealingWithoutPermanentCenters.optimizeNCenters(finalI, 6);
                    double minimumCost = solutionWithNCenters.getCost();
                    List<Integer> minimumPositions = solutionWithNCenters.getSites();
                    System.out.println("Done optimizing with " + finalI + " center(s). Minimum cost " + minimumCost + " at " + minimumPositions + ".");
                    allMap.put(minimumPositions, minimumCost); //development only

                    //Check if current number of centers is in minimum map
                    boolean firstAttempt = true;
                    double previousAttemptCost = -1;
                    for (List<Integer> key : minimumMap.keySet()) {
                        if (key.size() == finalI) {
                          firstAttempt = false;
                          previousAttemptCost = minimumMap.get(key);
                          break;
                        }
                    }

                    //Add to map if first attempt or check if equivalent to previous attempt. Shut down if not.
                    if (firstAttempt) {
                        minimumMap.put(minimumPositions, minimumCost);
                    } else {
                        if (previousAttemptCost != minimumCost) {
                            firstFailureCenterCount.set(finalI);
                            System.out.println("Current cost " + minimumCost + " is not equal to cost on previous attempt " + previousAttemptCost + " with " + finalI + " centers. Continuing on to more redundant search.");
                            executorStepOne.shutdownNow();
                        }
                    }

                    //Check if we can reduce maxNewCenterSearch, i.e. clear enough that center count is excessive (see checkEnoughCenters method).
                    if (checkEnoughCenters(minimumMap)) {
                        //Change maxNewCenterSearch if finalI (current number of centers) less than maxNewCenters.
                        maxNewCentersNeededToOptimize.getAndUpdate(x -> finalI < x ? finalI : x);
                        System.out.println("Found smaller maxNeededNewCenter count to search " + maxNewCentersNeededToOptimize);
                        //Check if every number of centers from minimum to maxNeededNewCenterSearch already optimized.
                        if (isAdequateMinimumMap(minimumMap, minNewCenters, maxNewCentersNeededToOptimize)) {
                            System.out.println("Minimum map is adequate. Interrupting ongoing tasks and clearing all queued tasks.");
                            executorStepOne.shutdownNow();
                        }
                    }


                } catch (InterruptedException e) {
                    System.out.println("Canceling optimization with " + finalI + " centers as optimization of fewer centers is expected to allow lower cost.");
                }
            });
        }
        executorStepOne.shutdown();
        try {
            executorStepOne.awaitTermination(100000, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            executorStepOne.shutdownNow();
            Thread.currentThread().interrupt();
        }

        //Step 2:
        ExecutorService executorStepTwo = Executors.newFixedThreadPool(threadCount);
        for (int i = minNewCenters; i <= maxNewCenters; i++) {
            int finalI = i;
            executorStepTwo.submit(() -> {
                try {
                    //Optimize for i centers. Add optimal i positions and its cost to minimumMap.
                    System.out.println("Starting optimization with " + finalI + " center(s).");
                    SiteConfiguration solutionWithNCenters = SimAnnealingWithoutPermanentCenters.optimizeNCenters(finalI, 6);
                    double minimumCost = solutionWithNCenters.getCost();
                    List<Integer> minimumPositions = solutionWithNCenters.getSites();
                    minimumMap.put(minimumPositions, minimumCost);
                    System.out.println("Done optimizing with " + finalI + " center(s). Minimum cost " + minimumCost + " at " + minimumPositions + ".");

                    //Check if we can reduce maxNewCenterSearch, i.e. clear enough that center count is excessive (see checkEnoughCenters method).
                    if (checkEnoughCenters(minimumMap)) {
                        //Change maxNewCenterSearch if finalI (current number of centers) less than maxNewCenters.
                        maxNewCentersNeededToOptimize.getAndUpdate(x -> finalI < x ? finalI : x);
                        System.out.println("Found smaller maxNeededNewCenter count to search " + maxNewCentersNeededToOptimize);
                        //Check if every number of centers from minimum to maxNeededNewCenterSearch already optimized.
                        if (isAdequateMinimumMap(minimumMap, minNewCenters, maxNewCentersNeededToOptimize)) {
                            System.out.println("Minimum map is adequate. Interrupting ongoing tasks and clearing all queued tasks.");
                            executorStepTwo.shutdownNow();
                        }
                    }


                } catch (InterruptedException e) {
                    System.out.println("Canceling optimization with " + finalI + " centers as optimization of fewer centers is expected to allow lower cost.");
                }
            });
        }
        executorStepTwo.shutdown();
        try {
            executorStepTwo.awaitTermination(100000, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            executorStepTwo.shutdownNow();
            Thread.currentThread().interrupt();
        }

        //Find the lowest cost arrangement in minimumMap, which maps different optimal positions (all different number of positions) to cost.
        Map.Entry<List<Integer>, Double> minimumEntry = getMinimumEntry(minimumMap);
        List<Integer> finalMinimumPositions = minimumEntry.getKey();
        double finalMinimumCost = minimumEntry.getValue();
        List<Object> output = new ArrayList<>(Arrays.asList(finalMinimumCost, finalMinimumPositions)); //contains two elements: Double minimum cost and List<Integer> minimum positions.
        return output;
    }

    //Gets minimum value and its corresponding key in a hashmap
    private static Map.Entry<List<Integer>, Double> getMinimumEntry(ConcurrentHashMap<List<Integer>, Double> map) {
        Map.Entry<List<Integer>, Double> minimumEntry = null;
        for (Map.Entry<List<Integer>, Double> entry : map.entrySet()) {
            if (minimumEntry == null || minimumEntry.getValue() > entry.getValue()) {
                minimumEntry = entry;
            }
        }
        return minimumEntry;
    }

    //Partitions into n sublists of same size +/- 1. The longer sublists are first.
    public static List<List<List<Integer>>> partitionList(List<List<Integer>> list, Integer n) {
        Integer listSize = list.size();
        Integer minimumSublistSize = (int) Math.floor(listSize/n);
        Integer numberOfLongerSublist = listSize - minimumSublistSize * n;
        List<List<List<Integer>>> partitionedList = new ArrayList<>();
        for (int i=0; i < numberOfLongerSublist; i++) {
            partitionedList.add(list.subList((minimumSublistSize + 1) * i, (minimumSublistSize + 1) * (i + 1)));
        }
        for (int i=numberOfLongerSublist; i < n; i++) {
            partitionedList.add(list.subList(numberOfLongerSublist + minimumSublistSize * i, numberOfLongerSublist + minimumSublistSize * (i + 1)));
        }
        return partitionedList;
    };

    //Checks 3 arrangements with most new centers. If adding to number of new centers leads to increase in cost twice in a row AND largest center cost is 10% greater than minimum cost, then return true.
    private static boolean checkEnoughCenters(ConcurrentHashMap<List<Integer>, Double> minimumMap) {
        //Make a local copy of ConcurrentHashMap to free it up
        Map<List<Integer>, Double> localMinimumMap = new HashMap<>(minimumMap);

        //Check to ensure there are at least 3 arrangements to compare. If not, return false.
        if (localMinimumMap.size() < 3) {
            return false;
        }

        //Create TreeMap to sort HashMap by number of centers
        final NavigableMap<List<Integer>, Double> sortedMinimumMap = new TreeMap<>(
                Comparator.comparingInt(l -> l.size())
        );
        sortedMinimumMap.putAll(localMinimumMap);

        //Check to ensure TreeMap is equal in size to HashMap. If smaller, then there are two items in HashMap with same number of centers, which should not occur by simAnnealingOptimizeCenters.s
        if (sortedMinimumMap.size() < localMinimumMap.size()) {
            System.out.println("Sorted tree map is smaller than original map. Check why there are two candidate arrangements with equal number of centers.");
            if (sortedMinimumMap.size() < 3) {
                return false;
            }
        }

        //Pick three arrangements with most new centers and compare costs. Return true if increasing centers leads to increasing cost for these arrangements AND max center cost is 10% greater than minimum cost.
        double costMinimumSoFar = Collections.min(localMinimumMap.values());
        double costMostNewCenters = sortedMinimumMap.pollLastEntry().getValue();
        double costSecondMostNewCenters = sortedMinimumMap.pollLastEntry().getValue();
        return ((costMostNewCenters > costSecondMostNewCenters) && (costSecondMostNewCenters > sortedMinimumMap.pollLastEntry().getValue()) && (costMostNewCenters > costMinimumSoFar * 1.1));
    }

    //Check if every number of centers tested from minNewCenters to maxNewCenterSearch, i.e. adequate search
    private static boolean isAdequateMinimumMap(ConcurrentHashMap<List<Integer>, Double> minimumMap, Integer minNewCenters, AtomicInteger maxNewCentersNeededToOptimize) {
        //Make a local copy of ConcurrentHashMap and AtomicInteger
        Map<List<Integer>, Double> localMinimumMap = new HashMap<>(minimumMap);
        Integer localMaxNeededNewCenterSearch = maxNewCentersNeededToOptimize.get();

        //Create centerCounts with all numbers of centers that have been tried
        List<Integer> centerCounts = new ArrayList<>();
        for (List<Integer> centers : localMinimumMap.keySet()) centerCounts.add(centers.size());

        //Sort and check if every integer is covered to maxNeededNewCenterSearch
        Collections.sort(centerCounts);
        return (centerCounts.get(localMaxNeededNewCenterSearch - minNewCenters) == localMaxNeededNewCenterSearch);
    }

    //Parses CSV file into array
    public static List<List<String>> parseCSV(String fileLocation) {
        BufferedReader reader;
        String currentLine;
        List<List<String>> csvArray = new ArrayList<>();
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                csvArray.add(Arrays.asList(values));
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return csvArray;
    }
}


