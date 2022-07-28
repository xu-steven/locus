import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class SiteConfiguration {
    protected List<Integer> sites; //list of the lowest level sites
    protected double cost; //total cost
    protected int[] minimumPositionsByOrigin; //for each origin, the position in the lowest level sites that minimizes travel cost from that origin to sites

    public SiteConfiguration() {    }

    public SiteConfiguration(List<Integer> sites, double cost, int[] minimumPositionsByOrigin) {
        this.sites = sites;
        this.cost = cost;
        this.minimumPositionsByOrigin = minimumPositionsByOrigin;
    }

    //Generates site configuration
    public SiteConfiguration(int minimumCenterCount, int maximumCenterCount, List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Create random list of current cancer center positions and list of remaining potential positions.
        Random random = new Random();
        sites = new ArrayList<>(pickNRandomFromList(potentialSites, random.nextInt(maximumCenterCount - minimumCenterCount + 1) + minimumCenterCount, random));

        //Compute initial cost and list of the closest of current positions for each originating population center
        ConfigurationCostAndPositions initialCostAndPositions = initialCost(sites, searchParameters.getMinimumCasesByLevel().get(0), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        cost = initialCostAndPositions.getCost();
        minimumPositionsByOrigin = initialCostAndPositions.getPositions();
    }

    //Get new leveled site configuration by shifting one of the lowest level sites. Only used for optimization without levels.
    public SiteConfiguration shiftSiteWithoutLevels(int positionToShift, int neighborhoodSize, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Get shifted sites
        Integer siteToShift = sites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(sites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newSites = new ArrayList<>(sites);
        newSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        ConfigurationCostAndPositions updatedResult = shiftSiteCost(newSites, positionToShift, newSite, minimumPositionsByOrigin, searchParameters.getMinimumCases(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost();
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfiguration(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Shift site according to a potential site
    public SiteConfiguration shiftSite(List<Integer> potentialSites, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Randomly shift a site to one of potential sites
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        int adjustedPosition = random.nextInt(newSites.size());
        newSites.set(adjustedPosition, unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = shiftSiteCost(newSites, adjustedPosition, newSites.get(adjustedPosition), minimumPositionsByOrigin, minimumCases, searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfiguration(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Add a site to current configuration without regard for different levels
    public SiteConfiguration addSite(List<Integer> potentialSites, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Add site
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        newSites.add(unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute parameters
        ConfigurationCostAndPositions updatedResult = addSiteCost(newSites, minimumPositionsByOrigin, minimumCases, searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfiguration(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Remove a site without regard for other levels
    public SiteConfiguration removeSite(double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Remove site
        List<Integer> newSites = new ArrayList<>(sites);
        Random random = new Random();
        int removalPosition = random.nextInt(newSites.size());
        newSites.remove(removalPosition);

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = removeSiteCost(newSites, removalPosition, minimumPositionsByOrigin, minimumCases, searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfiguration(newSites, newCost, newMinimumPositionsByOrigin);
    }


    //Variation of totalCost to save compute resources. For initial sites.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static ConfigurationCostAndPositions initialCost(List<Integer> sites, double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
                                           int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions(100000000.0, new int[originCount]);
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        HashMap<Integer, CasesAndCost>[] partitionedMinimumCostMap = new HashMap[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionToOptimize) {
                    int minimumCostPosition = 0;
                    double minimumCostUnadjusted = graphArray[j][sites.get(0)]; //Closest center travel cost, not adjusted for population or cancer center scaling
                    for (int k = 1; k < siteCount; ++k) {
                        double currentCostUnadjusted = graphArray[j][sites.get(k)];
                        if (currentCostUnadjusted < minimumCostUnadjusted) {
                            minimumCostPosition = k;
                            minimumCostUnadjusted = currentCostUnadjusted;
                        }
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    double currentCaseCount = caseCountByOrigin[j];
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount, centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
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
        HashMap<Integer, CasesAndCost> combinedMinimumCostMap = MultithreadingUtils.combinePartitionedMinimumCostMap(partitionedMinimumCostMap, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCases), minimumCostPositionsByOrigin);
    }

    //Variation of totalCost to save compute resources. For subsequent sites.
    //Input movedPosition is index from [0, 1, 2, ..., n-1] for n centers that was shifted to a new site; newSite is actual indexed position of new site; oldMinimumCostPositionByOrigin is list of the lowest travel cost centers for each population center using previous iteration sites prior to shift.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static ConfigurationCostAndPositions shiftSiteCost(List<Integer> sites, int movedPosition, Integer newSite, int[] oldMinimumCostPositionByOrigin, double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
                                             int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions(100000000.0, new int[originCount]);
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        HashMap<Integer, CasesAndCost>[] partitionedMinimumCostMap = new HashMap[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j=0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionToOptimize) {
                    int minimumCostPosition = 0;
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    if (movedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = graphArray[j][sites.get(0)]; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = 1; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray[j][sites.get(k)];
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                    } else {
                        double oldMinimumCost = graphArray[j][sites.get(oldMinimumCostPosition)];
                        double newPositionCost = graphArray[j][newSite];
                        if (newPositionCost < oldMinimumCost) {
                            minimumCostPosition = movedPosition;
                            minimumCostUnadjusted = newPositionCost;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                            minimumCostUnadjusted = oldMinimumCost;
                        }
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    double currentCaseCount = caseCountByOrigin[j];
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount, centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
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
        HashMap<Integer, CasesAndCost> combinedMinimumCostMap = MultithreadingUtils.combinePartitionedMinimumCostMap(partitionedMinimumCostMap, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCases), minimumCostPositionsByOrigin);
    }

    //Variation of totalCost to save compute resources. For added sites at the end of list.
    //Input movedPosition is index from [0, 1, 2, ..., n-1] for n centers that was shifted to a new site; newSite is actual indexed position of new site; oldMinimumCostPositionByOrigin is list of the lowest travel cost centers for each population center using previous iteration sites prior to shift.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static ConfigurationCostAndPositions addSiteCost(List<Integer> sites, int[] oldMinimumCostPositionByOrigin, double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
                                           int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        int newPosition = siteCount - 1;
        Integer newSite = sites.get(newPosition);
        //If there were originally no sites
        if (siteCount == 1) {
            return initialCost(sites, minimumCases, originCount, caseCountByOrigin, graphArray, taskCount, partitionedOrigins, executor);
        }
        //If there were some sites
        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        HashMap<Integer, CasesAndCost>[] partitionedMinimumCostMap = new HashMap[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionToOptimize) {
                    int minimumCostPosition;
                    double minimumCostUnadjusted; //Closest center travel cost, not adjusted for population or cancer center scaling
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    double oldMinimumCost = graphArray[j][sites.get(oldMinimumCostPosition)];
                    double newPositionCost = graphArray[j][newSite];
                    if (newPositionCost < oldMinimumCost) {
                        minimumCostPosition = newPosition;
                        minimumCostUnadjusted = newPositionCost;
                    } else {
                        minimumCostPosition = oldMinimumCostPosition;
                        minimumCostUnadjusted = oldMinimumCost;
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    double currentCaseCount = caseCountByOrigin[j];
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount, centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
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
        HashMap<Integer, CasesAndCost> combinedMinimumCostMap = MultithreadingUtils.combinePartitionedMinimumCostMap(partitionedMinimumCostMap, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCases), minimumCostPositionsByOrigin);
    }

    //Variation of totalCost to save compute resources. For subsequent sites.
    //Input movedPosition is index from [0, 1, 2, ..., n-1] for n centers that was shifted to a new site; newSite is actual indexed position of new site; oldMinimumCostPositionByOrigin is list of the lowest travel cost centers for each population center using previous iteration sites prior to shift.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static ConfigurationCostAndPositions removeSiteCost(List<Integer> sites, int removedPosition, int[] oldMinimumCostPositionByOrigin, double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
                                              int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions((double) 100000000, new int[originCount]);
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        HashMap<Integer, CasesAndCost>[] partitionedMinimumCostMap = new HashMap[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionToOptimize) {
                    int minimumCostPosition = 0;
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    if (removedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = graphArray[j][sites.get(0)]; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = 1; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray[j][sites.get(k)];
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                    } else {
                        if (removedPosition < oldMinimumCostPosition) {
                            minimumCostPosition = oldMinimumCostPosition - 1;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                        }
                        minimumCostUnadjusted = graphArray[j][sites.get(minimumCostPosition)];
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    double currentCaseCount = caseCountByOrigin[j];
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount,centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
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
        HashMap<Integer, CasesAndCost> combinedMinimumCostMap = MultithreadingUtils.combinePartitionedMinimumCostMap(partitionedMinimumCostMap, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCases), minimumCostPositionsByOrigin);
    }

    //Pick random sublist
    public static <E> List<E> pickNRandomFromList(List<E> list, int n, Random r) {
        int length = list.size();
        for (int i = length - 1; i >= length - n; --i) {
            Collections.swap(list, i, r.nextInt(i + 1));
        }
        return list.subList(length - n, length);
    }

    public List<Integer> getSites() {
        return sites;
    }

    public double getCost() {
        return cost;
    }

    public int[] getMinimumPositionsByOrigin() {
        return minimumPositionsByOrigin;
    }

}