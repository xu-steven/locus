import javax.swing.text.Position;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

//Used to override methods only
public class SiteConfigurationForPermanentCenters {
    protected List<Integer> sites; //list of the lowest level sites
    protected double cost; //total cost
    protected List<Integer> minimumPositionsByOrigin; //for each origin, the position in the lowest level sites that minimizes travel cost from that origin to sites

    public SiteConfigurationForPermanentCenters() {    }

    public SiteConfigurationForPermanentCenters(List<Integer> sites, double cost, List<Integer> minimumPositionsByOrigin) {
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
        Integer adjustedPosition = searchParameters.getPermanentHLCentersCount().get(level) + random.nextInt(newSites.size() - searchParameters.getPermanentHLCentersCount().get(level));
        newSites.set(adjustedPosition, unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = shiftSiteCost(level, newSites, adjustedPosition, newSites.get(adjustedPosition), minimumPositionsByOrigin,
                searchParameters.getPermanentHLCentersCount(), searchParameters.getMinPermanentHLPositionByOrigin(), searchParameters.getMinPermanentHLCostByOrigin(),
                minimumCases, searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * servicedProportion;
        List<Integer> newMinimumPositionsByOrigin = updatedResult.getPositions();

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
                minimumCases, searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * servicedProportion;
        List<Integer> newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //Multithreaded removeSite variant
    public SiteConfigurationForPermanentCenters removeSite(int level, double servicedProportion, double minimumCases, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Remove site
        List<Integer> newSites = new ArrayList<>(sites);
        Random random = new Random();
        Integer removalPosition = searchParameters.getPermanentHLCentersCount().get(level) + random.nextInt(newSites.size() - searchParameters.getPermanentHLCentersCount().get(level));
        newSites.remove((int) removalPosition);

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = removeSiteCost(level, newSites, removalPosition, minimumPositionsByOrigin,
                searchParameters.getPermanentHLCentersCount(), searchParameters.getMinPermanentHLPositionByOrigin(), searchParameters.getMinPermanentHLCostByOrigin(),
                minimumCases, searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * servicedProportion;
        List<Integer> newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new SiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin);
    }

    //For base level
    public static ConfigurationCostAndPositions initialCost(List<Integer> sites, Integer permanentCentersCount, List<Integer> minPermanentCenterByOrigin, List<Double> minPermanentCostByOrigin,
                                           double minimumCases, List<Double> caseCountByOrigin, List<List<Double>> graphArray,
                                           int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions(100000000.0, new ArrayList<>());
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentHashMap<Integer, PositionsAndMap> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                Map<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>();
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                List<Integer> partitionMinimumCostPositionByOrigin = new ArrayList<>();
                for (int j : partitionToOptimize) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                    for (int k = permanentCentersCount; k < siteCount; ++k) {
                        double currentCostUnadjusted = graphArray.get(j).get(sites.get(k));
                        if (currentCostUnadjusted < minimumCostUnadjusted) {
                            minimumCostPosition = k;
                            minimumCostUnadjusted = currentCostUnadjusted;
                        }
                    }
                    if (minPermanentCostByOrigin.get(j) < minimumCostUnadjusted) {
                        minimumCostPosition = minPermanentCenterByOrigin.get(j);
                        minimumCostUnadjusted = minPermanentCostByOrigin.get(j);
                    }
                    partitionMinimumCostPositionByOrigin.add(minimumCostPosition);
                    double currentCaseCount = caseCountByOrigin.get(j);
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount, centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
                }
                partitionedOutput.put(finalI, new PositionsAndMap(partitionMinimumCostPositionByOrigin, partitionMinimumCostMap));
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e){
            throw new AssertionError("Unexpected interruption", e);
        }
        PositionsAndMap combinedOutput = MultithreadingUtils.combinePartitionedOutput(partitionedOutput, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedOutput.getMap(), minimumCases), combinedOutput.getPositions());
    }

    //For higher levels
    public static ConfigurationCostAndPositions initialCost(int level, List<Integer> sites, List<Integer> permanentHLCentersCount, List<List<Integer>> minPermanentHLPositionByOrigin, List<List<Double>> minPermanentHLCostByOrigin,
                                           double minimumCases, List<Double> caseCountByOrigin, List<List<Double>> graphArray,
                                           int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions(100000000.0, new ArrayList<>());
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentHashMap<Integer, PositionsAndMap> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                Map<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>();
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                List<Integer> partitionMinimumCostPositionByOrigin = new ArrayList<>();
                for (int j : partitionToOptimize) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                    for (int k = permanentHLCentersCount.get(level); k < siteCount; ++k) {
                        double currentCostUnadjusted = graphArray.get(j).get(sites.get(k));
                        if (currentCostUnadjusted < minimumCostUnadjusted) {
                            minimumCostPosition = k;
                            minimumCostUnadjusted = currentCostUnadjusted;
                        }
                    }
                    if (minPermanentHLCostByOrigin.get(level).get(j) < minimumCostUnadjusted) {
                        minimumCostPosition = minPermanentHLPositionByOrigin.get(level).get(j);
                        minimumCostUnadjusted = minPermanentHLCostByOrigin.get(level).get(j);
                    }
                    partitionMinimumCostPositionByOrigin.add(minimumCostPosition);
                    double currentCaseCount = caseCountByOrigin.get(j);
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount;
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount, centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
                }
                partitionedOutput.put(finalI, new PositionsAndMap(partitionMinimumCostPositionByOrigin, partitionMinimumCostMap));
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e){
            throw new AssertionError("Unexpected interruption", e);
        }
        PositionsAndMap combinedOutput = MultithreadingUtils.combinePartitionedOutput(partitionedOutput, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedOutput.getMap(), minimumCases), combinedOutput.getPositions());
    }

    //For base level
    public static ConfigurationCostAndPositions shiftSiteCost(List<Integer> sites, Integer movedPosition, Integer newSite, List<Integer> oldMinimumCostPositionByOrigin,
                                             int permanentCentersCount, List<Integer> minPermanentCenterByOrigin, List<Double> minPermanentCostByOrigin,
                                             double minimumCases, List<Double> caseCountByOrigin, List<List<Double>> graphArray,
                                             int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions(100000000.0, new ArrayList<>());
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentHashMap<Integer, PositionsAndMap> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                Map<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>();
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j=0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                List<Integer> partitionMinimumCostPositionByOrigin = new ArrayList<>();
                for (int j : partitionToOptimize) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted;
                    Integer oldMinimumCostPosition = oldMinimumCostPositionByOrigin.get(j);
                    if (movedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentCentersCount; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray.get(j).get(sites.get(k));
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentCostByOrigin.get(j) < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentCenterByOrigin.get(j);
                            minimumCostUnadjusted = minPermanentCostByOrigin.get(j);
                        }
                    } else {
                        double oldMinimumCost = graphArray.get(j).get(sites.get(oldMinimumCostPosition));
                        double newPositionCost = graphArray.get(j).get(newSite);
                        if (newPositionCost < oldMinimumCost) {
                            minimumCostPosition = movedPosition;
                            minimumCostUnadjusted = newPositionCost;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                            minimumCostUnadjusted = oldMinimumCost;
                        }
                    }
                    partitionMinimumCostPositionByOrigin.add(minimumCostPosition);
                    double currentCaseCount = caseCountByOrigin.get(j);
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount, centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
                }
                partitionedOutput.put(finalI, new PositionsAndMap(partitionMinimumCostPositionByOrigin, partitionMinimumCostMap));
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e){
            throw new AssertionError("Unexpected interruption", e);
        }
        PositionsAndMap combinedOutput = MultithreadingUtils.combinePartitionedOutput(partitionedOutput, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedOutput.getMap(), minimumCases), combinedOutput.getPositions());
    }

    //For higher levels
    public static ConfigurationCostAndPositions shiftSiteCost(int level, List<Integer> sites, Integer movedPosition, Integer newSite, List<Integer> oldMinimumCostPositionByOrigin,
                                             List<Integer> permanentHLCentersCount, List<List<Integer>> minPermanentHLPositionByOrigin, List<List<Double>> minPermanentHLCostByOrigin,
                                             double minimumCases, List<Double> caseCountByOrigin, List<List<Double>> graphArray,
                                             int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions(100000000.0, new ArrayList<>());
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentHashMap<Integer, PositionsAndMap> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                Map<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>();
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j=0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                List<Integer> partitionMinimumCostPositionByOrigin = new ArrayList<>();
                for (int j : partitionToOptimize) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted;
                    Integer oldMinimumCostPosition = oldMinimumCostPositionByOrigin.get(j);
                    if (movedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentHLCentersCount.get(level); k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray.get(j).get(sites.get(k));
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentHLCostByOrigin.get(level).get(j) < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentHLPositionByOrigin.get(level).get(j);
                            minimumCostUnadjusted = minPermanentHLCostByOrigin.get(level).get(j);
                        }
                    } else {
                        double oldMinimumCost = graphArray.get(j).get(sites.get(oldMinimumCostPosition));
                        double newPositionCost = graphArray.get(j).get(newSite);
                        if (newPositionCost < oldMinimumCost) {
                            minimumCostPosition = movedPosition;
                            minimumCostUnadjusted = newPositionCost;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                            minimumCostUnadjusted = oldMinimumCost;
                        }
                    }
                    partitionMinimumCostPositionByOrigin.add(minimumCostPosition);
                    double currentCaseCount = caseCountByOrigin.get(j);
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount, centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
                }
                partitionedOutput.put(finalI, new PositionsAndMap(partitionMinimumCostPositionByOrigin, partitionMinimumCostMap));
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e){
            throw new AssertionError("Unexpected interruption", e);
        }
        PositionsAndMap combinedOutput = MultithreadingUtils.combinePartitionedOutput(partitionedOutput, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedOutput.getMap(), minimumCases), combinedOutput.getPositions());
    }

    //Using initialCost from site configuration as originally no sites implies that there were no permanent centers
    public static ConfigurationCostAndPositions addSiteCost(List<Integer> sites, List<Integer> oldMinimumCostPositionByOrigin,
                                           double minimumCases, List<Double> caseCountByOrigin, List<List<Double>> graphArray,
                                           int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        Integer newPosition = siteCount - 1;
        Integer newSite = sites.get(newPosition);
        //If there were originally no sites
        if (siteCount == 1) {
            return SiteConfiguration.initialCost(sites, minimumCases, caseCountByOrigin, graphArray, taskCount, partitionedOrigins, executor);
        }
        //If there were some sites
        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentHashMap<Integer, PositionsAndMap> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                Map<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>();
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                List<Integer> partitionMinimumCostPositionByOrigin = new ArrayList<>();
                for (int j : partitionToOptimize) {
                    int minimumCostPosition;
                    double minimumCostUnadjusted; //Closest center travel cost, not adjusted for population or cancer center scaling
                    Integer oldMinimumCostPosition = oldMinimumCostPositionByOrigin.get(j);
                    double oldMinimumCost = graphArray.get(j).get(sites.get(oldMinimumCostPosition));
                    double newPositionCost = graphArray.get(j).get(newSite);
                    if (newPositionCost < oldMinimumCost) {
                        minimumCostPosition = newPosition;
                        minimumCostUnadjusted = newPositionCost;
                    } else {
                        minimumCostPosition = oldMinimumCostPosition;
                        minimumCostUnadjusted = oldMinimumCost;
                    }
                    partitionMinimumCostPositionByOrigin.add(minimumCostPosition);
                    double currentCaseCount = caseCountByOrigin.get(j);
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount, centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
                }
                partitionedOutput.put(finalI, new PositionsAndMap(partitionMinimumCostPositionByOrigin, partitionMinimumCostMap));
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e){
            throw new AssertionError("Unexpected interruption", e);
        }
        PositionsAndMap combinedOutput = MultithreadingUtils.combinePartitionedOutput(partitionedOutput, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedOutput.getMap(), minimumCases), combinedOutput.getPositions());
    }

    //For base level
    public static ConfigurationCostAndPositions removeSiteCost(List<Integer> sites, Integer removedPosition, List<Integer> oldMinimumCostPositionByOrigin,
                                              int permanentCentersCount, List<Integer> minPermanentCenterByOrigin, List<Double> minPermanentCostByOrigin,
                                              double minimumCases, List<Double> caseCountByOrigin, List<List<Double>> graphArray,
                                              int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions(100000000.0, new ArrayList<>());
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentHashMap<Integer, PositionsAndMap> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                Map<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>();
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                List<Integer> partitionMinimumCostPositionByOrigin = new ArrayList<>();
                for (int j : partitionToOptimize) {
                    int minimumCostPosition = -1;
                    double minimumCostUnadjusted;
                    Integer oldMinimumCostPosition = oldMinimumCostPositionByOrigin.get(j);
                    if (removedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = Double.POSITIVE_INFINITY; //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentCentersCount; k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray.get(j).get(sites.get(k));
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentCostByOrigin.get(j) < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentCenterByOrigin.get(j);
                            minimumCostUnadjusted = minPermanentCostByOrigin.get(j);
                        }
                    } else {
                        if (removedPosition < oldMinimumCostPosition) {
                            minimumCostPosition = oldMinimumCostPosition - 1;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                        }
                        minimumCostUnadjusted = graphArray.get(j).get(sites.get(minimumCostPosition));
                    }
                    partitionMinimumCostPositionByOrigin.add(minimumCostPosition);
                    double currentCaseCount = caseCountByOrigin.get(j);
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount,centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
                }
                partitionedOutput.put(finalI, new PositionsAndMap(partitionMinimumCostPositionByOrigin, partitionMinimumCostMap));
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e){
            throw new AssertionError("Unexpected interruption", e);
        }
        PositionsAndMap combinedOutput = MultithreadingUtils.combinePartitionedOutput(partitionedOutput, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedOutput.getMap(), minimumCases), combinedOutput.getPositions());
    }

    //For higher levels
    public static ConfigurationCostAndPositions removeSiteCost(int level, List<Integer> sites, Integer removedPosition, List<Integer> oldMinimumCostPositionByOrigin,
                                              List<Integer> permanentHLCentersCount, List<List<Integer>> minPermanentHLPositionByOrigin, List<List<Double>> minPermanentHLCostByOrigin,
                                              double minimumCases, List<Double> caseCountByOrigin, List<List<Double>> graphArray,
                                              int taskCount, Map<Integer, List<Integer>> partitionedOrigins, ExecutorService executor) {
        int siteCount = sites.size();
        if (siteCount == 0) {
            return new ConfigurationCostAndPositions(100000000.0, new ArrayList<>());
        }

        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentHashMap<Integer, PositionsAndMap> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                Map<Integer, CasesAndCost> partitionMinimumCostMap = new HashMap<>();
                CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
                for (int j = 0; j < siteCount; ++j) {
                    partitionMinimumCostMap.put(j, initialCasesCost);
                }
                List<Integer> partitionMinimumCostPositionByOrigin = new ArrayList<>();
                for (int j : partitionToOptimize) {
                    int minimumCostPosition = 0;
                    double minimumCostUnadjusted;
                    Integer oldMinimumCostPosition = oldMinimumCostPositionByOrigin.get(j);
                    if (removedPosition == oldMinimumCostPosition) {
                        minimumCostUnadjusted = graphArray.get(j).get(sites.get(0)); //Closest center travel cost, not adjusted for population or cancer center scaling
                        for (int k = permanentHLCentersCount.get(level); k < siteCount; ++k) {
                            double currentCostUnadjusted = graphArray.get(j).get(sites.get(k));
                            if (currentCostUnadjusted < minimumCostUnadjusted) {
                                minimumCostPosition = k;
                                minimumCostUnadjusted = currentCostUnadjusted;
                            }
                        }
                        if (minPermanentHLCostByOrigin.get(level).get(j) < minimumCostUnadjusted) {
                            minimumCostPosition = minPermanentHLPositionByOrigin.get(level).get(j);
                            minimumCostUnadjusted = minPermanentHLCostByOrigin.get(level).get(j);
                        }
                    } else {
                        if (removedPosition < oldMinimumCostPosition) {
                            minimumCostPosition = oldMinimumCostPosition - 1;
                        } else {
                            minimumCostPosition = oldMinimumCostPosition;
                        }
                        minimumCostUnadjusted = graphArray.get(j).get(sites.get(minimumCostPosition));
                    }
                    partitionMinimumCostPositionByOrigin.add(minimumCostPosition);
                    double currentCaseCount = caseCountByOrigin.get(j);
                    double centerCaseCount = partitionMinimumCostMap.get(minimumCostPosition).getCases() + currentCaseCount; //Add new case count to total case count at center
                    double centerCost = partitionMinimumCostMap.get(minimumCostPosition).getCost() + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
                    CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount,centerCost);
                    partitionMinimumCostMap.put(minimumCostPosition, minimumCasesCost);
                }
                partitionedOutput.put(finalI, new PositionsAndMap(partitionMinimumCostPositionByOrigin, partitionMinimumCostMap));
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e){
            throw new AssertionError("Unexpected interruption", e);
        }
        PositionsAndMap combinedOutput = MultithreadingUtils.combinePartitionedOutput(partitionedOutput, siteCount, taskCount);
        return new ConfigurationCostAndPositions(CostCalculator.computeCost(combinedOutput.getMap(), minimumCases), combinedOutput.getPositions());
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

    public List<Integer> getMinimumPositionsByOrigin() {
        return minimumPositionsByOrigin;
    }
}