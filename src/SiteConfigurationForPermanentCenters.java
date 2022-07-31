import javax.swing.text.Position;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

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
        int adjustedPosition = searchParameters.getPermanentHLCentersCount()[level] + random.nextInt(newSites.size() - searchParameters.getPermanentHLCentersCount()[level]);
        newSites.set(adjustedPosition, unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = shiftSiteCost(level, newSites, adjustedPosition, newSites.get(adjustedPosition), minimumPositionsByOrigin,
                searchParameters.getPermanentHLCentersCount(), searchParameters.getMinPermanentHLPositionByOrigin(), searchParameters.getMinPermanentHLCostByOrigin(),
                minimumCases, searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Add a site to current configuration without regard for different levels
    public SiteConfigurationForPermanentCenters addSite(List<Integer> potentialSites, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Add site
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        newSites.add(unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute parameters
        ConfigurationCostAndPositions updatedResult = addSiteCost(newSites, minimumPositionsByOrigin,
                minimumCases, searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Multithreaded removeSite variant
    public SiteConfigurationForPermanentCenters removeSite(int level, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Remove site
        List<Integer> newSites = new ArrayList<>(sites);
        Random random = new Random();
        int removalPosition = searchParameters.getPermanentHLCentersCount()[level] + random.nextInt(newSites.size() - searchParameters.getPermanentHLCentersCount()[level]);
        newSites.remove(removalPosition);

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = removeSiteCost(level, newSites, removalPosition, minimumPositionsByOrigin,
                searchParameters.getPermanentHLCentersCount(), searchParameters.getMinPermanentHLPositionByOrigin(), searchParameters.getMinPermanentHLCostByOrigin(),
                minimumCases, searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * servicedProportion;
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //For base level
    public static ConfigurationCostAndPositions initialCost(List<Integer> sites, int permanentCentersCount, int[] minPermanentCenterByOrigin, double[] minPermanentCostByOrigin,
                                           double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
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
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionedOrigins.get(finalI)) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                    for (int k = permanentCentersCount; k < siteCount; ++k) {
                        double currentCostUnadjusted = graphArray[j][sites.get(k)];
                        if (currentCostUnadjusted < minimumCostUnadjusted) {
                            minimumCostPosition = k;
                            minimumCostUnadjusted = currentCostUnadjusted;
                        }
                    }
                    if (minPermanentCostByOrigin[j] < minimumCostUnadjusted) {
                        minimumCostPosition = minPermanentCenterByOrigin[j];
                        minimumCostUnadjusted = minPermanentCostByOrigin[j];
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

    //For higher levels
    public static ConfigurationCostAndPositions initialCost(int level, List<Integer> sites, int[] permanentHLCentersCount, int[][] minPermanentHLPositionByOrigin, double[][] minPermanentHLCostByOrigin,
                                           double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
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
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionedOrigins.get(finalI)) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                    for (int k = permanentHLCentersCount[level]; k < siteCount; ++k) {
                        double currentCostUnadjusted = graphArray[j][sites.get(k)];
                        if (currentCostUnadjusted < minimumCostUnadjusted) {
                            minimumCostPosition = k;
                            minimumCostUnadjusted = currentCostUnadjusted;
                        }
                    }
                    if (minPermanentHLCostByOrigin[level][j] < minimumCostUnadjusted) {
                        minimumCostPosition = minPermanentHLPositionByOrigin[level][j];
                        minimumCostUnadjusted = minPermanentHLCostByOrigin[level][j];
                    }
                    minimumCostPositionsByOrigin[j] = minimumCostPosition;
                    double currentCaseCount = caseCountByOrigin[j];
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount;
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

    //For base level
    public static ConfigurationCostAndPositions shiftSiteCost(List<Integer> sites, int movedPosition, Integer newSite, int[] oldMinimumCostPositionByOrigin,
                                             int permanentCentersCount, int[] minPermanentCenterByOrigin, double[] minPermanentCostByOrigin,
                                             double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
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
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j=0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionedOrigins.get(finalI)) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    if (movedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentCentersCount; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray[j][sites.get(k)];
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentCostByOrigin[j] < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentCenterByOrigin[j];
                            minimumCostUnadjusted = minPermanentCostByOrigin[j];
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

    //For higher levels
    public static ConfigurationCostAndPositions shiftSiteCost(int level, List<Integer> sites, int movedPosition, Integer newSite, int[] oldMinimumCostPositionByOrigin,
                                             int[] permanentHLCentersCount, int[][] minPermanentHLPositionByOrigin, double[][] minPermanentHLCostByOrigin,
                                             double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
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
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j=0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionedOrigins.get(finalI)) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    if (movedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentHLCentersCount[level]; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray[j][sites.get(k)];
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentHLCostByOrigin[level][j] < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentHLPositionByOrigin[level][j];
                            minimumCostUnadjusted = minPermanentHLCostByOrigin[level][j];
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

    //Using initialCost from site configuration as originally no sites implies that there were no permanent centers
    public static ConfigurationCostAndPositions addSiteCost(List<Integer> sites, int[] oldMinimumCostPositionByOrigin,
                                           double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
                                           int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        int newPosition = siteCount - 1;
        Integer newSite = sites.get(newPosition);
        //If there were originally no sites
        if (siteCount == 1) {
            return SiteConfiguration.initialCost(sites, minimumCases, originCount, caseCountByOrigin, graphArray, taskCount, partitionedOrigins, executor);
        }
        //If there were some sites
        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        HashMap<Integer, CasesAndCost>[] partitionedMinimumCostMap = new HashMap[taskCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionedOrigins.get(finalI)) {
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

    //For base level
    public static ConfigurationCostAndPositions removeSiteCost(List<Integer> sites, int removedPosition, int[] oldMinimumCostPositionByOrigin,
                                              int permanentCentersCount, int[] minPermanentCenterByOrigin, double[] minPermanentCostByOrigin,
                                              double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
                                              int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions(100000000.0, new int[originCount]);
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        int[] minimumCostPositionsByOrigin = new int[originCount];
        HashMap<Integer, CasesAndCost>[] partitionedMinimumCostMap = new HashMap[taskCount];
        ConcurrentHashMap<Integer, PositionsAndMap> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionedOrigins.get(finalI)) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    if (removedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentCentersCount; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray[j][sites.get(k)];
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentCostByOrigin[j] < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentCenterByOrigin[j];
                            minimumCostUnadjusted = minPermanentCostByOrigin[j];
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

    //For higher levels
    public static ConfigurationCostAndPositions removeSiteCost(int level, List<Integer> sites, int removedPosition, int[] oldMinimumCostPositionByOrigin,
                                              int[] permanentHLCentersCount, int[][] minPermanentHLPositionByOrigin, double[][] minPermanentHLCostByOrigin,
                                              double minimumCases, int originCount, double[] caseCountByOrigin, double[][] graphArray,
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
                HashMap<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>(10 * siteCount);
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                for (int j : partitionedOrigins.get(finalI)) {
                    int minimumCostPosition = 0;
                    double minimumCostUnadjusted;
                    int oldMinimumCostPosition = oldMinimumCostPositionByOrigin[j];
                    if (removedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = graphArray[j][sites.get(0)]; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentHLCentersCount[level]; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray[j][sites.get(k)];
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentHLCostByOrigin[level][j] < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentHLPositionByOrigin[level][j];
                            minimumCostUnadjusted = minPermanentHLCostByOrigin[level][j];
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