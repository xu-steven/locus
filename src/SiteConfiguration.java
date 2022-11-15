import java.util.*;
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
        CostMapAndPositions initialCostAndPositions = initialCost(sites, searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getTotalSitesCount(), searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        cost = CostCalculator.computeCost(initialCostAndPositions.getCasesAndCostMap(), searchParameters.getMinimumCases(), searchParameters.getTimepointWeights());
        minimumPositionsByOrigin = initialCostAndPositions.getPositions();
    }

    //Get new leveled site configuration by shifting one of the lowest level sites. Only used for optimization without levels.
    public void tryShiftToNeighbor(int positionToShift, int neighborhoodSize, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Get shifted sites
        Integer siteToShift = sites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(sites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newSites = new ArrayList<>(sites);
        newSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        CostMapAndPositions updatedResult = shiftSiteCost(newSites, positionToShift, newSite, minimumPositionsByOrigin, searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getTotalSitesCount(), searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCases(), searchParameters.getTimepointWeights());

        //Decide whether to accept new positions
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sites = newSites;
            cost = newCost;
            minimumPositionsByOrigin = updatedResult.getPositions();
        }
    }

    //Shift site according to a potential site
    public void tryShiftSite(int positionToShift, Integer newSite, double servicedProportion, double minimumCases, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Randomly shift a site to one of potential sites
        List<Integer> newSites = new ArrayList<>(sites);
        newSites.set(positionToShift, newSite);

        //Compute new parameters
        CostMapAndPositions updatedResult = shiftSiteCost(newSites, positionToShift, newSite, minimumPositionsByOrigin, searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getTotalSitesCount(), searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), minimumCases, searchParameters.getTimepointWeights()) * servicedProportion;

        //Decide whether to accept new positions
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sites = newSites;
            cost = newCost;
            minimumPositionsByOrigin = updatedResult.getPositions();
        }
    }

    //Add a site to current configuration without regard for different levels
    public void tryAddSite(Integer newSite, double servicedProportion, double minimumCases, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Add site
        List<Integer> newSites = new ArrayList<>(sites);
        newSites.add(newSite);

        //Compute parameters
        CostMapAndPositions updatedResult = addSiteCost(newSites, minimumPositionsByOrigin, searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getTotalSitesCount(), searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), minimumCases, searchParameters.getTimepointWeights()) * servicedProportion;

        //Decide whether to accept new positions
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sites = newSites;
            cost = newCost;
            minimumPositionsByOrigin = updatedResult.getPositions();
        }
    }

    //Remove a site without regard for other levels
    public void tryRemovePosition(int removalPosition, double servicedProportion, double minimumCases, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Remove site
        List<Integer> newSites = new ArrayList<>(sites);
        newSites.remove(removalPosition);

        //Compute new parameters
        CostMapAndPositions updatedResult = removeSiteCost(newSites, removalPosition, minimumPositionsByOrigin, searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getTotalSitesCount(), searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), minimumCases, searchParameters.getTimepointWeights()) * servicedProportion;

        //Decide whether to accept new positions
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sites = newSites;
            cost = newCost;
            minimumPositionsByOrigin = updatedResult.getPositions();
        }
    }

    //Variation of totalCost to save compute resources. For initial sites.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static CostMapAndPositions initialCost(List<Integer> sites, int timepointCount, int originCount, double[] caseCountByOrigin, int totalSitesCount, double[] graphArray,
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
                    double minimumCostUnadjusted = SearchSpace.getEdgeLength(j, sites.get(0), totalSitesCount, graphArray); //Closest center travel cost, not adjusted for population or cancer center scaling
                    for (int k = 1; k < siteCount; ++k) {
                        double currentCostUnadjusted = SearchSpace.getEdgeLength(j, sites.get(k), totalSitesCount, graphArray);
                        if (currentCostUnadjusted < minimumCostUnadjusted) {
                            minimumCostPosition = k;
                            minimumCostUnadjusted = currentCostUnadjusted;
                        }
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    partitionMinimumCostMap.updateCasesAndCost(minimumCostPosition, minimumCostUnadjusted, j, originCount, caseCountByOrigin);
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

    //Variation of totalCost to save compute resources. For subsequent sites.
    //Input movedPosition is index from [0, 1, 2, ..., n-1] for n centers that was shifted to a new site; newSite is actual indexed position of new site; oldMinimumCostPositionByOrigin is list of the lowest travel cost centers for each population center using previous iteration sites prior to shift.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static CostMapAndPositions shiftSiteCost(List<Integer> sites, int movedPosition, Integer newSite, int[] oldMinimumCostPositionByOrigin, int timepointCount, int originCount, double[] caseCountByOrigin, int totalSitesCount, double[] graphArray,
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
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    if (movedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = SearchSpace.getEdgeLength(j, sites.get(0), totalSitesCount, graphArray); //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = 1; k < siteCount; ++k) {
                            double currentCostUnadjusted = SearchSpace.getEdgeLength(j, sites.get(k), totalSitesCount, graphArray);
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                    } else {
                        double oldMinimumCost = SearchSpace.getEdgeLength(j, sites.get(oldMinimumCostPosition), totalSitesCount, graphArray);
                        double newPositionCost = SearchSpace.getEdgeLength(j, newSite, totalSitesCount, graphArray);
                        if (newPositionCost < oldMinimumCost) {
                            minimumCostPosition = movedPosition;
                            minimumCostUnadjusted = newPositionCost;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                            minimumCostUnadjusted = oldMinimumCost;
                        }
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    partitionMinimumCostMap.updateCasesAndCost(minimumCostPosition, minimumCostUnadjusted, j, originCount, caseCountByOrigin);
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
        CasesAndCostMap combinedMinimumCostMap = new CasesAndCostMap(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
    }

    //Variation of totalCost to save compute resources. For added sites at the end of list.
    //Input movedPosition is index from [0, 1, 2, ..., n-1] for n centers that was shifted to a new site; newSite is actual indexed position of new site; oldMinimumCostPositionByOrigin is list of the lowest travel cost centers for each population center using previous iteration sites prior to shift.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static CostMapAndPositions addSiteCost(List<Integer> sites, int[] oldMinimumCostPositionByOrigin, int timepointCount, int originCount, double[] caseCountByOrigin, int totalSitesCount, double[] graphArray,
                                           int taskCount, int[] startingOrigins, int[] endingOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        int newPosition = siteCount - 1;
        Integer newSite = sites.get(newPosition);
        //If there were originally no sites
        if (siteCount == 1) {
            return initialCost(sites, timepointCount, originCount, caseCountByOrigin, totalSitesCount, graphArray, taskCount, startingOrigins, endingOrigins, executor);
        }
        //If there were some sites
        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        CasesAndCostMap[] partitionedMinimumCostMap = new CasesAndCostMap[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                CasesAndCostMap partitionMinimumCostMap = new CasesAndCostMap(timepointCount, siteCount);
                for (int j = startingOrigins[finalI]; j < endingOrigins[finalI]; j++) {
                    int minimumCostPosition;
                    double minimumCostUnadjusted; //Closest center travel cost, not adjusted for population or cancer center scaling
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    double oldMinimumCost = SearchSpace.getEdgeLength(j, sites.get(oldMinimumCostPosition), totalSitesCount, graphArray);
                    double newPositionCost = SearchSpace.getEdgeLength(j, newSite, totalSitesCount, graphArray);
                    if (newPositionCost < oldMinimumCost) {
                        minimumCostPosition = newPosition;
                        minimumCostUnadjusted = newPositionCost;
                    } else {
                        minimumCostPosition = oldMinimumCostPosition;
                        minimumCostUnadjusted = oldMinimumCost;
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    partitionMinimumCostMap.updateCasesAndCost(minimumCostPosition, minimumCostUnadjusted, j, originCount, caseCountByOrigin);
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
        CasesAndCostMap combinedMinimumCostMap = new CasesAndCostMap(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
    }

    //Variation of totalCost to save compute resources. For subsequent sites.
    //Input movedPosition is index from [0, 1, 2, ..., n-1] for n centers that was shifted to a new site; newSite is actual indexed position of new site; oldMinimumCostPositionByOrigin is list of the lowest travel cost centers for each population center using previous iteration sites prior to shift.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static CostMapAndPositions removeSiteCost(List<Integer> sites, int removedPosition, int[] oldMinimumCostPositionByOrigin, int timepointCount, int originCount, double[] caseCountByOrigin, int totalSitesCount, double[] graphArray,
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
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    if (removedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = SearchSpace.getEdgeLength(j, sites.get(0), totalSitesCount, graphArray); //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = 1; k < siteCount; ++k) {
                            double currentCostUnadjusted = SearchSpace.getEdgeLength(j, sites.get(k), totalSitesCount, graphArray);
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
                        minimumCostUnadjusted = SearchSpace.getEdgeLength(j, sites.get(minimumCostPosition), totalSitesCount, graphArray);
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    partitionMinimumCostMap.updateCasesAndCost(minimumCostPosition, minimumCostUnadjusted, j, originCount, caseCountByOrigin);
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
        CasesAndCostMap combinedMinimumCostMap = new CasesAndCostMap(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
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