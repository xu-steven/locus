import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

//Used to override methods only
public class SiteConfigurationForPermanentCenters {
    protected List<Integer> sites; //list of the lowest level sites
    protected double cost; //total cost
    protected int[] minimumPositionsByOrigin; //for each origin, the position in the lowest level sites that minimizes travel cost from that origin to sites

    public SiteConfigurationForPermanentCenters() {    }

    public SiteConfigurationForPermanentCenters(List<Integer> sites, double cost, int[] minimumPositionsByOrigin) {
        this.sites = sites;
        this.cost = cost;
        this.minimumPositionsByOrigin = minimumPositionsByOrigin;
    }

    //Multithreaded variant of shiftToPotentialSite
    public SiteConfigurationForPermanentCenters shiftSite(int level, List<Integer> potentialSites, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Randomly shift a site to one of potential sites
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        int adjustedPosition = searchParameters.getPermanentCentersCountByLevel()[level] + random.nextInt(newSites.size() - searchParameters.getPermanentCentersCountByLevel()[level]);
        newSites.set(adjustedPosition, unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute new parameters
        CostMapAndPositions updatedResult = shiftSiteCost(level, newSites, adjustedPosition, newSites.get(adjustedPosition), searchParameters.getMinPermanentPositionByLevelAndOrigin(),
                searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), minimumCases, searchParameters.getTimepointWeights()) * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Add a site to current configuration without regard for different levels
    public SiteConfigurationForPermanentCenters addSite(int level, List<Integer> potentialSites, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Add site
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        newSites.add(unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute parameters
        CostMapAndPositions updatedResult = addSiteCost(level, newSites, searchParameters.getMinPermanentPositionByLevelAndOrigin(),
                searchParameters.getMinimumCasesByLevel(), searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), minimumCases, searchParameters.getTimepointWeights()) * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Multithreaded removeSite variant
    public SiteConfigurationForPermanentCenters removeSite(int level, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Remove site
        List<Integer> newSites = new ArrayList<>(sites);
        Random random = new Random();
        int removalPosition = searchParameters.getPermanentCentersCountByLevel()[level] + random.nextInt(newSites.size() - searchParameters.getPermanentCentersCountByLevel()[level]);
        newSites.remove(removalPosition);

        //Compute new parameters
        CostMapAndPositions updatedResult = removeSiteCost(level, newSites, removalPosition, searchParameters.getMinPermanentPositionByLevelAndOrigin(),
                searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), minimumCases, searchParameters.getTimepointWeights()) * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //For higher levels
    public static CostMapAndPositions initialCost(int level, List<Integer> sites, int[] permanentCentersCountByLevel, int[][] minPermanentPositionByLevelAndOrigin, double[][] minPermanentCostByLevelAndOrigin,
                                           int timepointCount, int originCount, double[] caseCountByOrigin, SearchSpace searchParameters,
                                           int taskCount, int[][] partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new CostMapAndPositions(new CasesAndCostMapWithTime(), new int[originCount]); //No sites
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        CasesAndCostMapWithTime[] partitionedMinimumCostMap = new CasesAndCostMapWithTime[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                CasesAndCostMapWithTime partitionMinimumCostMap = new CasesAndCostMapWithTime(timepointCount, siteCount);
                for (int j : partitionedOrigins[finalI]) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                    for (int k = permanentCentersCountByLevel[level]; k < siteCount; ++k) {
                        double currentCostUnadjusted = searchParameters.getEdgeLength(j, sites.get(k));
                        if (currentCostUnadjusted < minimumCostUnadjusted) {
                            minimumCostPosition = k;
                            minimumCostUnadjusted = currentCostUnadjusted;
                        }
                    }
                    if (minPermanentCostByLevelAndOrigin[level][j] < minimumCostUnadjusted) {
                        minimumCostPosition = minPermanentPositionByLevelAndOrigin[level][j];
                        minimumCostUnadjusted = minPermanentCostByLevelAndOrigin[level][j];
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    partitionMinimumCostMap.updateCasesAndCost(minimumCostPosition, minimumCostUnadjusted, j, searchParameters);
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
        CasesAndCostMapWithTime combinedMinimumCostMap = new CasesAndCostMapWithTime(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
        //return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCasesByLevel[level]), minimumCostPositionsByOrigin);
    }

    //For higher levels
    public static CostMapAndPositions shiftSiteCost(int level, List<Integer> sites, int movedPosition, Integer newSite, int[][] oldMinimumCostPositionByLevelAndOrigin,
                                             int[] permanentCentersCountByLevel, int[][] minPermanentPositionByLevelAndOrigin, double[][] minPermanentCostByLevelAndOrigin,
                                             int timepointCount, int originCount, double[] caseCountByOrigin, SearchSpace searchParameters,
                                             int taskCount, int[][] partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new CostMapAndPositions(new CasesAndCostMapWithTime(), new int[originCount]);
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        CasesAndCostMapWithTime[] partitionedMinimumCostMap = new CasesAndCostMapWithTime[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                CasesAndCostMapWithTime partitionMinimumCostMap = new CasesAndCostMapWithTime(timepointCount, siteCount);
                for (int j : partitionedOrigins[finalI]) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByLevelAndOrigin[level][j];
                    if (movedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentCentersCountByLevel[level]; k < siteCount; ++k) {
                            double currentCostUnadjusted = searchParameters.getEdgeLength(j, sites.get(k));
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentCostByLevelAndOrigin[level][j] < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentPositionByLevelAndOrigin[level][j];
                            minimumCostUnadjusted = minPermanentCostByLevelAndOrigin[level][j];
                        }
                    } else {
                        double oldMinimumCost = searchParameters.getEdgeLength(j, sites.get(oldMinimumCostPosition));
                        double newPositionCost = searchParameters.getEdgeLength(j, newSite);
                        if (newPositionCost < oldMinimumCost) {
                            minimumCostPosition = movedPosition;
                            minimumCostUnadjusted = newPositionCost;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                            minimumCostUnadjusted = oldMinimumCost;
                        }
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    partitionMinimumCostMap.updateCasesAndCost(minimumCostPosition, minimumCostUnadjusted, j, searchParameters);
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
        CasesAndCostMapWithTime combinedMinimumCostMap = new CasesAndCostMapWithTime(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
        //return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCasesByLevel[level]), minimumCostPositionsByOrigin);
    }

    //Using initialCost from site configuration as originally no sites implies that there were no permanent centers
    public static CostMapAndPositions addSiteCost(int level, List<Integer> sites, int[][] oldMinimumCostPositionByLevelAndOrigin,
                                           double[] minimumCasesByLevel, int timepointCount, int originCount, double[] caseCountByOrigin, SearchSpace searchParameters,
                                           int taskCount, int[][] partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        int newPosition = siteCount - 1;
        Integer newSite = sites.get(newPosition);
        //If there were originally no sites
        if (siteCount == 1) {
            return SiteConfiguration.initialCost(sites, timepointCount, originCount, caseCountByOrigin, searchParameters, taskCount, partitionedOrigins, executor);
        }
        //If there were some sites
        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        CasesAndCostMapWithTime[] partitionedMinimumCostMap = new CasesAndCostMapWithTime[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                CasesAndCostMapWithTime partitionMinimumCostMap = new CasesAndCostMapWithTime(timepointCount, siteCount);
                for (int j : partitionedOrigins[finalI]) {
                    int minimumCostPosition;
                    double minimumCostUnadjusted; //Closest center travel cost, not adjusted for population or cancer center scaling
                    int oldMinimumCostPosition = oldMinimumCostPositionByLevelAndOrigin[level][j];
                    double oldMinimumCost = searchParameters.getEdgeLength(j, sites.get(oldMinimumCostPosition));
                    double newPositionCost = searchParameters.getEdgeLength(j, newSite);
                    if (newPositionCost < oldMinimumCost) {
                        minimumCostPosition = newPosition;
                        minimumCostUnadjusted = newPositionCost;
                    } else {
                        minimumCostPosition = oldMinimumCostPosition;
                        minimumCostUnadjusted = oldMinimumCost;
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    partitionMinimumCostMap.updateCasesAndCost(minimumCostPosition, minimumCostUnadjusted, j, searchParameters);
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
        CasesAndCostMapWithTime combinedMinimumCostMap = new CasesAndCostMapWithTime(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
        //return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCasesByLevel[level]), minimumCostPositionsByOrigin);
    }

    //For higher levels
    public static CostMapAndPositions removeSiteCost(int level, List<Integer> sites, int removedPosition, int[][] oldMinimumCostPositionByLevelAndOrigin,
                                              int[] permanentCentersCountByLevel, int[][] minPermanentPositionByLevelAndOrigin, double[][] minPermanentCostByLevelAndOrigin,
                                              int timepointCount, int originCount, double[] caseCountByOrigin, SearchSpace searchParameters,
                                              int taskCount, int[][] partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new CostMapAndPositions(new CasesAndCostMapWithTime(), new int[originCount]);
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        CasesAndCostMapWithTime[] partitionedMinimumCostMap = new CasesAndCostMapWithTime[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                CasesAndCostMapWithTime partitionMinimumCostMap = new CasesAndCostMapWithTime(timepointCount, siteCount);
                for (int j : partitionedOrigins[finalI]) {
                    int minimumCostPosition = 0;
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByLevelAndOrigin[level][j];
                    if (removedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = searchParameters.getEdgeLength(j, sites.get(0)); //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentCentersCountByLevel[level]; k < siteCount; ++k) {
                            double currentCostUnadjusted = searchParameters.getEdgeLength(j, sites.get(k));
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentCostByLevelAndOrigin[level][j] < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentPositionByLevelAndOrigin[level][j];
                            minimumCostUnadjusted = minPermanentCostByLevelAndOrigin[level][j];
                        }
                    } else {
                        if (removedPosition < oldMinimumCostPosition) {
                            minimumCostPosition = oldMinimumCostPosition - 1;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                        }
                        minimumCostUnadjusted = searchParameters.getEdgeLength(j, sites.get(minimumCostPosition));
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    partitionMinimumCostMap.updateCasesAndCost(minimumCostPosition, minimumCostUnadjusted, j, searchParameters);
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
        CasesAndCostMapWithTime combinedMinimumCostMap = new CasesAndCostMapWithTime(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
        //return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCasesByLevel[level]), minimumCostPositionsByOrigin);
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

    //Decompose sites into permanent and non-permanent (potential) sites
    public DecomposedSites decomposeSites(int permanentCentersCount, int potentialSitesCount) {
        return new DecomposedSites(sites.stream().limit(permanentCentersCount).map(x -> x - potentialSitesCount).collect(Collectors.toList()), sites.subList(permanentCentersCount, sites.size()));
    }

    public record DecomposedSites(List<Integer> permanentSites, List<Integer> newSites) {
        public List<Integer> getPermanentSites() {
            return permanentSites;
        }
        public List<Integer> getNewSites() {
            return newSites;
        }
    }
}