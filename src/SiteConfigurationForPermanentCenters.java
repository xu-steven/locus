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
    public SiteConfigurationForPermanentCenters tryShiftSite(int level, List<Integer> potentialSites, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Randomly shift a site to one of potential sites
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        int adjustedPosition = searchParameters.getPermanentCentersCountByLevel()[level] + random.nextInt(newSites.size() - searchParameters.getPermanentCentersCountByLevel()[level]);
        newSites.set(adjustedPosition, unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute new parameters
        CostMapAndPositions updatedResult = shiftSiteCost(newSites, adjustedPosition, newSites.get(adjustedPosition), searchParameters.getMinPermanentPositionByLevelAndOrigin()[level],
                searchParameters.getPermanentCentersCountByLevel()[level], searchParameters.getMinPermanentPositionByLevelAndOrigin()[level], searchParameters.getMinPermanentCostByLevelAndOrigin()[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), minimumCases, searchParameters.getTimepointWeights()) * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Add a site to current configuration without regard for different levels
    public SiteConfigurationForPermanentCenters tryAddSite(int level, List<Integer> potentialSites, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Add site
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        newSites.add(unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute parameters
        CostMapAndPositions updatedResult = addSiteCost(newSites, searchParameters.getMinPermanentPositionByLevelAndOrigin()[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), minimumCases, searchParameters.getTimepointWeights()) * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Multithreaded removeSite variant
    public SiteConfigurationForPermanentCenters tryRemoveSite(int level, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Remove site
        List<Integer> newSites = new ArrayList<>(sites);
        Random random = new Random();
        int removalPosition = searchParameters.getPermanentCentersCountByLevel()[level] + random.nextInt(newSites.size() - searchParameters.getPermanentCentersCountByLevel()[level]);
        newSites.remove(removalPosition);

        //Compute new parameters
        CostMapAndPositions updatedResult = removeSiteCost(newSites, removalPosition, searchParameters.getMinPermanentPositionByLevelAndOrigin()[level],
                searchParameters.getPermanentCentersCountByLevel()[level], searchParameters.getMinPermanentPositionByLevelAndOrigin()[level], searchParameters.getMinPermanentCostByLevelAndOrigin()[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newCost = CostCalculator.computeCost(updatedResult.getCasesAndCostMap(), minimumCases, searchParameters.getTimepointWeights()) * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //For higher levels
    public static CostMapAndPositions initialCost(List<Integer> sites, int permanentCentersCount, int[] minPermanentPositionByOrigin, double[] minPermanentCostByOrigin,
                                           int timepointCount, int originCount, CaseCounts caseCountByOrigin, Graph graphArray,
                                           int taskCount, int[] startingOrigins, int[] endingOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new CostMapAndPositions(new CasesAndCostMap(), new int[originCount]); //No sites
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        CasesAndCostMap[] partitionedMinimumCostMap = new CasesAndCostMap[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                CasesAndCostMap partitionMinimumCostMap = new CasesAndCostMap(timepointCount, siteCount);
                for (int j = startingOrigins[finalI]; j < endingOrigins[finalI]; j++) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                    for (int k = permanentCentersCount; k < siteCount; ++k) {
                        double currentCostUnadjusted = graphArray.getEdgeLength(j, sites.get(k));
                        if (currentCostUnadjusted < minimumCostUnadjusted) {
                            minimumCostPosition = k;
                            minimumCostUnadjusted = currentCostUnadjusted;
                        }
                    }
                    if (minPermanentCostByOrigin[j] < minimumCostUnadjusted) {
                        minimumCostPosition = minPermanentPositionByOrigin[j];
                        minimumCostUnadjusted = minPermanentCostByOrigin[j];
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
        CasesAndCostMap combinedMinimumCostMap = new CasesAndCostMap(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
        //return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCasesByLevel[level]), minimumCostPositionsByOrigin);
    }

    //For higher levels
    public static CostMapAndPositions shiftSiteCost(List<Integer> sites, int movedPosition, Integer newSite, int[] oldMinimumCostPositionByOrigin,
                                             int permanentCentersCount, int[] minPermanentPositionOrigin, double[] minPermanentCostAndOrigin,
                                             int timepointCount, int originCount, CaseCounts caseCountByOrigin, Graph graphArray,
                                             int taskCount, int[] startingOrigins, int[] endingOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new CostMapAndPositions(new CasesAndCostMap(), new int[originCount]);
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        CasesAndCostMap[] partitionedMinimumCostMap = new CasesAndCostMap[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                CasesAndCostMap partitionMinimumCostMap = new CasesAndCostMap(timepointCount, siteCount);
                for (int j = startingOrigins[finalI]; j < endingOrigins[finalI]; j++) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    if (movedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentCentersCount; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray.getEdgeLength(j, sites.get(k));
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentCostAndOrigin[j] < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentPositionOrigin[j];
                            minimumCostUnadjusted = minPermanentCostAndOrigin[j];
                        }
                    } else {
                        double oldMinimumCost = graphArray.getEdgeLength(j, sites.get(oldMinimumCostPosition));
                        double newPositionCost = graphArray.getEdgeLength(j, newSite);
                        if (newPositionCost < oldMinimumCost) {
                            minimumCostPosition = movedPosition;
                            minimumCostUnadjusted = newPositionCost;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                            minimumCostUnadjusted = oldMinimumCost;
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
        CasesAndCostMap combinedMinimumCostMap = new CasesAndCostMap(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
        //return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCasesByLevel[level]), minimumCostPositionsByOrigin);
    }

    //Using initialCost from site configuration as originally no sites implies that there were no permanent centers
    public static CostMapAndPositions addSiteCost(List<Integer> sites, int[] oldMinimumCostPositionByOrigin,
                                           int timepointCount, int originCount, CaseCounts caseCountByOrigin, Graph graphArray,
                                           int taskCount, int[] startingOrigins, int[] endingOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        int newPosition = siteCount - 1;
        Integer newSite = sites.get(newPosition);
        //If there were originally no sites
        if (siteCount == 1) {
            return SiteConfiguration.initialCost(sites, timepointCount, originCount, caseCountByOrigin, graphArray, taskCount, startingOrigins, endingOrigins, executor);
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
                    double oldMinimumCost = graphArray.getEdgeLength(j, sites.get(oldMinimumCostPosition));
                    double newPositionCost = graphArray.getEdgeLength(j, newSite);
                    if (newPositionCost < oldMinimumCost) {
                        minimumCostPosition = newPosition;
                        minimumCostUnadjusted = newPositionCost;
                    } else {
                        minimumCostPosition = oldMinimumCostPosition;
                        minimumCostUnadjusted = oldMinimumCost;
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
        CasesAndCostMap combinedMinimumCostMap = new CasesAndCostMap(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
        return new CostMapAndPositions(combinedMinimumCostMap, minimumCostPositionsByOrigin);
        //return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedMinimumCostMap, minimumCasesByLevel[level]), minimumCostPositionsByOrigin);
    }

    //For higher levels
    public static CostMapAndPositions removeSiteCost(List<Integer> sites, int removedPosition, int[] oldMinimumCostPositionByOrigin,
                                              int permanentCentersCount, int[] minPermanentPositionByOrigin, double[] minPermanentCostByOrigin,
                                              int timepointCount, int originCount, CaseCounts caseCountByOrigin, Graph graphArray,
                                              int taskCount, int[] startingOrigins, int[] endingOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new CostMapAndPositions(new CasesAndCostMap(), new int[originCount]);
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
                        minimumCostUnadjusted = graphArray.getEdgeLength(j, sites.get(0)); //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentCentersCount; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray.getEdgeLength(j, sites.get(k));
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentCostByOrigin[j] < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentPositionByOrigin[j];
                            minimumCostUnadjusted = minPermanentCostByOrigin[j];
                        }
                    } else {
                        if (removedPosition < oldMinimumCostPosition) {
                            minimumCostPosition = oldMinimumCostPosition - 1;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                        }
                        minimumCostUnadjusted = graphArray.getEdgeLength(j, sites.get(minimumCostPosition));
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
        CasesAndCostMap combinedMinimumCostMap = new CasesAndCostMap(partitionedMinimumCostMap, timepointCount, siteCount, taskCount);
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