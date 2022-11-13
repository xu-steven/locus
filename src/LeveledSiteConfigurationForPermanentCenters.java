import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class LeveledSiteConfigurationForPermanentCenters extends SiteConfigurationForPermanentCenters {
    //Developmental leveled configuration
    protected List<List<Integer>> sitesByLevel;
    protected double[] costByLevel; //development only
    protected CasesAndCost[][][] costMapByLevel; //Costs are not adjusted by serviced proportion, adjustment occurs when cost calculator used
    protected int[][] minimumPositionsByLevelAndOrigin;

    public LeveledSiteConfigurationForPermanentCenters(List<List<Integer>> sitesByLevel, double totalCost, CasesAndCost[][][] costByLevel, int[][] minimumPositionsByLevelAndOrigin) {
        this.sitesByLevel = sitesByLevel;
        this.cost = totalCost;
        this.costMapByLevel = costByLevel;
        this.minimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin;
    }

    public LeveledSiteConfigurationForPermanentCenters(int[] minimumNewCenterCountByLevel, int[] maximumNewCenterCountByLevel, List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Generate initial site configuration
        Random random = new Random();
        List<Integer> candidateInitialSites = new ArrayList<>(pickNRandomFromList(potentialSites, Arrays.stream(maximumNewCenterCountByLevel).max().getAsInt(), random));

        //Create site configuration for each level
        sitesByLevel = new ArrayList<>();
        for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
            //Initialize level
            int initialSiteCount = random.nextInt(maximumNewCenterCountByLevel[i] - minimumNewCenterCountByLevel[i] + 1) + minimumNewCenterCountByLevel[i];
            List<Integer> initialSites = candidateInitialSites.subList(0, initialSiteCount);
            sitesByLevel.add(initialSites);
        }

        //Update site configurations to respect level relations
        for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
            for (int superlevel : searchParameters.getSuperlevelsByLevel()[i]) {
                for (Integer site : sitesByLevel.get(i)) {
                    if (!sitesByLevel.get(superlevel).contains(site)) {
                        sitesByLevel.get(superlevel).add(site);
                    }
                }
                //Check to ensure superlevel site count is permissible, i.e. did not overload by adding sublevel
                if (sitesByLevel.get(superlevel).size() > maximumNewCenterCountByLevel[superlevel]) {
                    System.out.println("Overloaded superlevel " + superlevel + " of new level " + i);
                }
            }
        }

        //Add permanent sites
        sitesByLevel = ArrayOperations.mergeIntegerLists(searchParameters.getPermanentCentersByLevel(), sitesByLevel);

        //Compute initial cost
        costByLevel = new double[searchParameters.getCenterLevels()];
        costMapByLevel = new CasesAndCost[searchParameters.getCenterLevels()][searchParameters.getTimepointCount()][];
        minimumPositionsByLevelAndOrigin = new int[searchParameters.getCenterLevels()][searchParameters.getOriginCount()];
        for (int i = 0; i < searchParameters.getCenterLevels(); ++i) {
            CostMapAndPositions initialResult = initialCost(i, sitesByLevel.get(i), searchParameters.getPermanentCentersCountByLevel(),
                    searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                    searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
            double initialLevelCost = CostCalculator.computeLevelBaseCost(initialResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[i], searchParameters.getServicedProportionByLevel()[i], searchParameters.getTimepointWeights());
            cost += initialLevelCost;
            costByLevel[i] = initialLevelCost;
            //minimumPositionsByLevelAndOrigin[i] = initialLevelCostAndPositions.getPositions();
            costMapByLevel[i] = initialResult.getMinimumCostMap();
            minimumPositionsByLevelAndOrigin[i] = initialResult.getPositions();
        }
        cost = CostCalculator.computeCost(costMapByLevel, sitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
    }

    //Multithreaded variant
    public void tryShiftToNeighbor(int level, int positionToShift, int neighborhoodSize, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Shift current level sites
        List<Integer> currentTargetLevelSites = sitesByLevel.get(level);
        Integer siteToShift = currentTargetLevelSites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(currentTargetLevelSites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newTargetLevelSites = new ArrayList<>(currentTargetLevelSites);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        CostMapAndPositions updatedResult = shiftSiteCost(level, newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin,
                searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Update other level sites
            List<List<Integer>> newLeveledSitesArray;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCost[][][] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getMinimumCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
            if (searchParameters.getSublevelsByLevel()[level].length > 0 || searchParameters.getSuperlevelsByLevel()[level].length > 0) {
                SitesAndUpdateHistory updatedArrayAndHistory = shiftSitesArray(sitesByLevel, level, searchParameters.getCenterLevels(), searchParameters.getSublevelsByLevel(), searchParameters.getSuperlevelsByLevel(), searchParameters.getPermanentCentersCountByLevel(), siteToShift, newSite);
                newLeveledSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
                newLeveledSitesArray.set(level, newTargetLevelSites);
                boolean[] updateHistory = updatedArrayAndHistory.getUpdateHistory();
                int[] updatedPositions = updatedArrayAndHistory.getUpdatedPositions();
                for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
                    if (updateHistory[i]) {
                        if (updatedPositions[i] == -1) {
                            //when update history is true and positions is unchanged at -1, then a site was added
                            updatedResult = addSiteCost(i, newLeveledSitesArray.get(i), minimumPositionsByLevelAndOrigin,
                                    searchParameters.getMinimumCasesByLevel(), searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
                        } else {
                            updatedResult = shiftSiteCost(i, newLeveledSitesArray.get(i), updatedPositions[i], newSite, minimumPositionsByLevelAndOrigin,
                                    searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                                    searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
                        }
                        double levelCost = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[i], searchParameters.getServicedProportionByLevel()[i], searchParameters.getTimepointWeights());
                        newCostByLevel[i] = levelCost;
                        newCostMapByLevel[i] = updatedResult.getMinimumCostMap();
                        newMinimumPositionsByLevelAndOrigin[i] = updatedResult.getPositions();
                    }
                }
                newCost = CostCalculator.computeCost(newCostMapByLevel, newLeveledSitesArray, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
            } else {
                newLeveledSitesArray = new ArrayList<>(sitesByLevel);
                newLeveledSitesArray.set(level, newTargetLevelSites);
                newCost = CostCalculator.computeCost(newCostMapByLevel, newLeveledSitesArray, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
            }

            //Decide if cost change is acceptable
            if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
                sitesByLevel = newLeveledSitesArray;
                cost = newCost;
                costByLevel = newCostByLevel;
                costMapByLevel = newCostMapByLevel;
                minimumPositionsByLevelAndOrigin = newMinimumPositionsByLevelAndOrigin;
            }
        }
    }

    //Multithreaded variant
    public void tryShiftToNeighborWithoutLevelRelations(int level, int positionToShift, int neighborhoodSize, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Shift target level sites
        List<Integer> currentTargetLevelSites = sitesByLevel.get(level);
        Integer siteToShift = currentTargetLevelSites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(currentTargetLevelSites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newTargetLevelSites = new ArrayList<>(currentTargetLevelSites);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        CostMapAndPositions updatedResult = shiftSiteCost(level, newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin,
                searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        List<List<Integer>> newSitesByLevel  = new ArrayList<>(sitesByLevel);
        newSitesByLevel.set(level, newTargetLevelSites);
        CasesAndCost[][][] newCostMapByLevel = costMapByLevel.clone();
        newCostMapByLevel[level] = updatedResult.getMinimumCostMap();
        double newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());

        //Decide if cost change is acceptable
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sitesByLevel.set(level, newTargetLevelSites);
            cost = newCost;
            costByLevel[level] = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());
            costMapByLevel = newCostMapByLevel;
            minimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
        }
    }

    //Multithreaded variant
    public void tryShiftSite(int level, int positionToShift, Integer newSite, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Shift current level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        Integer siteToShift = newTargetLevelSites.get(positionToShift);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        CostMapAndPositions updatedResult = shiftSiteCost(level, newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin,
                searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Update other level sites
            List<List<Integer>> newLeveledSitesArray;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCost[][][] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getMinimumCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
            if (searchParameters.getSublevelsByLevel()[level].length > 0 || searchParameters.getSuperlevelsByLevel()[level].length > 0) {
                SitesAndUpdateHistory updatedArrayAndHistory = shiftSitesArray(sitesByLevel, level, searchParameters.getCenterLevels(), searchParameters.getSublevelsByLevel(), searchParameters.getSuperlevelsByLevel(), searchParameters.getPermanentCentersCountByLevel(), siteToShift, newSite);
                newLeveledSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
                newLeveledSitesArray.set(level, newTargetLevelSites);
                boolean[] updateHistory = updatedArrayAndHistory.getUpdateHistory();
                int[] updatedPositions = updatedArrayAndHistory.getUpdatedPositions();
                for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
                    if (updateHistory[i]) {
                        if (updatedPositions[i] == -1) {
                            //when update history is true and positions is unchanged at -1, then a site was added
                            updatedResult = addSiteCost(i, newLeveledSitesArray.get(i), minimumPositionsByLevelAndOrigin,
                                    searchParameters.getMinimumCasesByLevel(), searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
                        } else {
                            updatedResult = shiftSiteCost(i, newLeveledSitesArray.get(i), updatedPositions[i], newSite, minimumPositionsByLevelAndOrigin,
                                    searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                                    searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
                        }
                        double levelCost = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[i], searchParameters.getServicedProportionByLevel()[i], searchParameters.getTimepointWeights());
                        newCostByLevel[i] = levelCost;
                        newCostMapByLevel[i] = updatedResult.getMinimumCostMap();
                        newMinimumPositionsByLevelAndOrigin[i] = updatedResult.getPositions();
                    }
                }
                newCost = CostCalculator.computeCost(newCostMapByLevel, newLeveledSitesArray, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
            } else {
                newLeveledSitesArray = new ArrayList<>(sitesByLevel);
                newLeveledSitesArray.set(level, newTargetLevelSites);
                newCost = CostCalculator.computeCost(newCostMapByLevel, newLeveledSitesArray, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
            }

            //Decide if cost change is acceptable
            if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
                sitesByLevel = newLeveledSitesArray;
                cost = newCost;
                costByLevel = newCostByLevel;
                costMapByLevel = newCostMapByLevel;
                minimumPositionsByLevelAndOrigin = newMinimumPositionsByLevelAndOrigin;
            }
        }
    }

    //Unchanged from without permanent centers (only one of three)
    public void tryAddSite(int level, Integer newSite, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Add lowest level site
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        newTargetLevelSites.add(newSite);

        //Compute new parameters
        CostMapAndPositions updatedResult = addSiteCost(level, newTargetLevelSites, minimumPositionsByLevelAndOrigin,
                searchParameters.getMinimumCasesByLevel(), searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Update arrays and adjust for superlevel sites
            List<List<Integer>> newLeveledSitesArray;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCost[][][] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getMinimumCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
            if (searchParameters.getSuperlevelsByLevel()[level].length > 0) {
                int[] superlevels = searchParameters.getSuperlevelsByLevel()[level];
                SitesAndUpdateHistory updatedArrayAndHistory = addToSitesArray(sitesByLevel, superlevels, newSite);
                newLeveledSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
                newLeveledSitesArray.set(level, newTargetLevelSites);
                boolean[] superlevelUpdateHistory = updatedArrayAndHistory.getUpdateHistory();
                for (int i = 0; i < superlevels.length; i++) {
                    if (superlevelUpdateHistory[i]) {
                        updatedResult = addSiteCost(superlevels[i], newLeveledSitesArray.get(superlevels[i]), minimumPositionsByLevelAndOrigin,
                                searchParameters.getMinimumCasesByLevel(), searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
                        double levelCost = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[superlevels[i]], searchParameters.getServicedProportionByLevel()[superlevels[i]], searchParameters.getTimepointWeights());
                        newCostByLevel[superlevels[i]] = levelCost;
                        newCostMapByLevel[superlevels[i]] = updatedResult.getMinimumCostMap();
                        newMinimumPositionsByLevelAndOrigin[superlevels[i]] = updatedResult.getPositions();
                    }
                }
                newCost = CostCalculator.computeCost(newCostMapByLevel, newLeveledSitesArray, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
            } else {
                newLeveledSitesArray = new ArrayList<>(sitesByLevel);
                newLeveledSitesArray.set(level, newTargetLevelSites);
                newCost = CostCalculator.computeCost(newCostMapByLevel, newLeveledSitesArray, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
            }

            //Decide if cost change is acceptable
            if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
                sitesByLevel = newLeveledSitesArray;
                cost = newCost;
                costByLevel = newCostByLevel;
                costMapByLevel = newCostMapByLevel;
                minimumPositionsByLevelAndOrigin = newMinimumPositionsByLevelAndOrigin;
            }
        }
    }

    //Unchanged from without permanent centers (only one of three)
    public void tryAddSiteWithoutSuperlevels(int level, Integer newSite, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Add lowest level site
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        newTargetLevelSites.add(newSite);

        //Compute new parameters
        CostMapAndPositions updatedResult = addSiteCost(level, newTargetLevelSites, minimumPositionsByLevelAndOrigin,
                searchParameters.getMinimumCasesByLevel(), searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        List<List<Integer>> newSitesByLevel  = new ArrayList<>(sitesByLevel);
        newSitesByLevel.set(level, newTargetLevelSites);
        CasesAndCost[][][] newCostMapByLevel = costMapByLevel.clone();
        newCostMapByLevel[level] = updatedResult.getMinimumCostMap();
        double newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());

        //Decide if cost change is acceptable
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sitesByLevel.set(level, newTargetLevelSites);
            cost = newCost;
            costByLevel[level] = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());
            costMapByLevel = newCostMapByLevel;
            minimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
        }
    }

    //Variant with multithreading of previous removeLowestLevelSite
    public void tryRemoveSite(int level, Integer removalSite, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        int removalPosition = newTargetLevelSites.indexOf(removalSite);
        newTargetLevelSites.remove(removalSite);

        //Compute new parameters
        CostMapAndPositions updatedResult = removeSiteCost(level, newTargetLevelSites, removalPosition, minimumPositionsByLevelAndOrigin,
                searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Update arrays and adjust for sublevel sites
            List<List<Integer>> newLeveledSitesArray;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCost[][][] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getMinimumCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
            if (searchParameters.getSublevelsByLevel()[level].length > 0) {
                int[] sublevels = searchParameters.getSublevelsByLevel()[level];
                SitesAndUpdateHistory updatedArrayAndHistory = removeFromSitesArray(sitesByLevel, sublevels, removalSite);
                newLeveledSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
                newLeveledSitesArray.set(level, newTargetLevelSites);
                boolean[] sublevelUpdateHistory = updatedArrayAndHistory.getUpdateHistory();
                int[] sublevelUpdatedPositions = updatedArrayAndHistory.getUpdatedPositions();
                for (int i = 0; i < sublevels.length; i++) {
                    if (sublevelUpdateHistory[i]) {
                        updatedResult = removeSiteCost(sublevels[i], newLeveledSitesArray.get(sublevels[i]), sublevelUpdatedPositions[i], minimumPositionsByLevelAndOrigin,
                                searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
                        double levelCost = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[sublevels[i]], searchParameters.getServicedProportionByLevel()[sublevels[i]], searchParameters.getTimepointWeights());
                        newCostByLevel[sublevels[i]] = levelCost;
                        newCostMapByLevel[sublevels[i]] = updatedResult.getMinimumCostMap();
                        newMinimumPositionsByLevelAndOrigin[sublevels[i]] = updatedResult.getPositions();
                    }
                }
                newCost = CostCalculator.computeCost(newCostMapByLevel, newLeveledSitesArray, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
            } else {
                newLeveledSitesArray = new ArrayList<>(sitesByLevel);
                newLeveledSitesArray.set(level, newTargetLevelSites);
                newCost = CostCalculator.computeCost(newCostMapByLevel, newLeveledSitesArray, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
            }

            //Decide if cost change is acceptable
            if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
                sitesByLevel = newLeveledSitesArray;
                cost = newCost;
                costByLevel = newCostByLevel;
                costMapByLevel = newCostMapByLevel;
                minimumPositionsByLevelAndOrigin = newMinimumPositionsByLevelAndOrigin;
            }
        }
    }

    //Variant with multithreading of previous removeLowestLevelSite
    public void tryRemovePositionWithoutSublevels(int level, int removalPosition, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        newTargetLevelSites.remove(removalPosition);

        //Compute new parameters
        CostMapAndPositions updatedResult = removeSiteCost(level, newTargetLevelSites, removalPosition, minimumPositionsByLevelAndOrigin,
                searchParameters.getPermanentCentersCountByLevel(), searchParameters.getMinPermanentPositionByLevelAndOrigin(), searchParameters.getMinPermanentCostByLevelAndOrigin(),
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters, taskCount, searchParameters.getPartitionedOrigins(), executor);
        List<List<Integer>> newSitesByLevel  = new ArrayList<>(sitesByLevel);
        newSitesByLevel.set(level, newTargetLevelSites);
        CasesAndCost[][][] newCostMapByLevel = costMapByLevel.clone();
        newCostMapByLevel[level] = updatedResult.getMinimumCostMap();
        double newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());

        //Decide if cost change is acceptable
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sitesByLevel.set(level, newTargetLevelSites);
            cost = newCost;
            costByLevel[level] = CostCalculator.computeLevelBaseCost(updatedResult.getMinimumCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());
            costMapByLevel = newCostMapByLevel;
            minimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
        }
    }

    //Update sites array by replacing removedSite with newSite for all sites in the array. Output is updated sites array, updated positions, and history of updates, true for each level that was changed and false if not. Must account for permanent centers not being movable in superlevels.
    public static SitesAndUpdateHistory shiftSitesArray(List<List<Integer>> sitesArray, int level, int totalLevels, int[][] sublevelsByLevel, int[][] superlevelsByLevel, int[] permanentCenterCountByLevel, Integer removedSite, Integer newSite) {
        List<List<Integer>> updatedSitesArray = new ArrayList<>(sitesArray);

        boolean[] updateHistory = new boolean[totalLevels];
        int[] updatedPositions = new int[totalLevels]; //-2 if added site
        Arrays.fill(updatedPositions, -1);

        //Target sublevels and superlevels
        int[] sublevels = sublevelsByLevel[level];
        int[] superlevels = superlevelsByLevel[level];

        //Additional sublevels and superlevels to cycle
        boolean[] sublevelProcessedHistory = new boolean[totalLevels];
        sublevelProcessedHistory[level] = true;
        boolean[] superlevelProcessedHistory = new boolean[totalLevels];
        superlevelProcessedHistory[level] = true;
        List<Integer> higherOrderSublevelsToProcess = new ArrayList<>();
        List<Integer> higherOrderSuperlevelsToProcess = new ArrayList<>();

        //Update target level sublevels
        //Any permanent center in sublevel is also permanent center in target level
        for (int sublevel : sublevels) {
            List<Integer> currentSites = updatedSitesArray.get(sublevel);
            for (int position = 0; position < currentSites.size(); position++) {
                if (currentSites.get(position).equals(removedSite)) {
                    List<Integer> updatedSites = new ArrayList<>(currentSites);
                    updatedSites.set(position, newSite);
                    updatedSitesArray.set(sublevel, updatedSites);
                    updateHistory[sublevel] = true;
                    updatedPositions[sublevel] = position;
                    for (int superlevel : superlevelsByLevel[sublevel]) {
                        if (!superlevelProcessedHistory[superlevel]) {
                            higherOrderSuperlevelsToProcess.add(superlevel);
                        }
                    }
                    break;
                }
            }
            sublevelProcessedHistory[sublevel] = true;
        }

        //Update target level superlevels
        //Non-permanent center in target level may be permanent center in superlevel
        updateSuperlevels:
        for (int superlevel : superlevels) {
            List<Integer> updatedSites = new ArrayList<>(updatedSitesArray.get(superlevel));
            boolean removedSiteIsPermanent = false;
            for (int position = 0; position < permanentCenterCountByLevel[level]; position++) { //First sites are permanent sites
                if (updatedSitesArray.get(superlevel).get(position).equals(removedSite)) {
                    removedSiteIsPermanent = true;
                } else if (updatedSitesArray.get(superlevel).get(position).equals(newSite)) {
                    superlevelProcessedHistory[superlevel] = true;
                    continue updateSuperlevels;
                }
            }
            if (removedSiteIsPermanent) {
                for (int position = permanentCenterCountByLevel[level]; position < updatedSitesArray.get(superlevel).size(); position++) {
                    if (updatedSitesArray.get(superlevel).get(position).equals(newSite)) { //if already containing site
                        break;
                    }
                    if (position == updatedSitesArray.get(superlevel).size() - 1) { //cycle to end before updating in case site is already contained
                        updatedSites.add(newSite);
                        updatedSitesArray.set(superlevel, updatedSites);
                        updateHistory[superlevel] = true;
                        //No need to update sublevels as the original site is permanent and remains
                    }
                }
            } else {
                int updatedPosition = -1;
                for (int position = permanentCenterCountByLevel[level]; position < updatedSites.size(); position++) {
                    if (updatedSites.get(position).equals(newSite)) { //if already containing site
                        break;
                    }
                    if (updatedSites.get(position).equals(removedSite)) { //move removed site to site
                        updatedSites.set(position, newSite);
                        updatedPosition = position;
                    }
                    if (position == updatedSites.size() - 1) { //cycle to end before updating in case site is already contained
                        updatedSitesArray.set(superlevel, updatedSites);
                        updateHistory[superlevel] = true;
                        updatedPositions[superlevel] = updatedPosition;
                        for (int sublevel : sublevelsByLevel[superlevel]) {
                            if (!sublevelProcessedHistory[sublevel]) {
                                higherOrderSublevelsToProcess.add(sublevel); //original level does not contain newSite, therefore sublevel cannot
                            }
                        }
                    }
                }
            }
            superlevelProcessedHistory[superlevel] = true;
        }

        while (higherOrderSublevelsToProcess.size() > 0 || higherOrderSuperlevelsToProcess.size() > 0) {
            //Update secondary sublevels
            for (int sublevel : higherOrderSublevelsToProcess) {
                List<Integer> currentSites = updatedSitesArray.get(sublevel);
                for (int position = 0; position < currentSites.size(); position++) {
                    if (currentSites.get(position).equals(removedSite)) {
                        List<Integer> updatedSites = new ArrayList<>(currentSites);
                        updatedSites.set(position, newSite);
                        updatedSitesArray.set(sublevel, updatedSites);
                        updateHistory[sublevel] = true;
                        updatedPositions[sublevel] = position;
                        for (int superlevel : superlevelsByLevel[sublevel]) {
                            if (!superlevelProcessedHistory[superlevel]) {
                                higherOrderSuperlevelsToProcess.add(superlevel);
                            }
                        }
                        break;
                    }
                }
                sublevelProcessedHistory[sublevel] = true;
            }
            higherOrderSublevelsToProcess = new ArrayList<>();

            //Update secondary superlevels
            updateSuperlevels:
            for (int superlevel : higherOrderSuperlevelsToProcess) {
                List<Integer> updatedSites = new ArrayList<>(updatedSitesArray.get(superlevel));
                boolean removedSiteIsPermanent = false;
                for (int position = 0; position < permanentCenterCountByLevel[level]; position++) { //First sites are permanent sites
                    if (updatedSitesArray.get(superlevel).get(position).equals(removedSite)) {
                        removedSiteIsPermanent = true;
                    } else if (updatedSitesArray.get(superlevel).get(position).equals(newSite)) {
                        superlevelProcessedHistory[superlevel] = true;
                        continue updateSuperlevels;
                    }
                }
                if (removedSiteIsPermanent) {
                    for (int position = 0; position < updatedSitesArray.get(superlevel).size(); position++) {
                        if (updatedSitesArray.get(superlevel).get(position).equals(newSite)) { //check to see if new site already
                            break;
                        }
                        if (position == updatedSitesArray.get(superlevel).size() - 1) { //cycle to end before updating in case site is already contained
                            updatedSites.add(newSite);
                            updatedSitesArray.set(superlevel, updatedSites);
                            updateHistory[superlevel] = true;
                            //No need to update sublevels as the original site is permanent and remains
                        }
                    }
                } else {
                    int updatedPosition = -1;
                    for (int i = permanentCenterCountByLevel[level]; i < updatedSites.size(); i++) {
                        if (updatedSites.get(i).equals(newSite)) { //if already containing site
                            break;
                        }
                        if (updatedSites.get(i).equals(removedSite)) { //move removed site to site
                            updatedSites.set(i, newSite);
                            updatedPosition = i;
                        }
                        if (i == updatedSites.size() - 1) { //cycle to end before updating in case site is already contained
                            updatedSitesArray.set(superlevel, updatedSites);
                            updateHistory[superlevel] = true;
                            updatedPositions[superlevel] = updatedPosition;
                            for (int sublevel : sublevelsByLevel[superlevel]) {
                                if (!sublevelProcessedHistory[sublevel]) {
                                    higherOrderSublevelsToProcess.add(sublevel);
                                }
                            }
                        }
                    }
                }
                superlevelProcessedHistory[superlevel] = true;
            }
            higherOrderSuperlevelsToProcess = new ArrayList<>();
        }

        return new SitesAndUpdateHistory(updatedSitesArray, updateHistory, updatedPositions);
    }

    //Update sites array by adding newSite to all superlevels if not already present.
    public static SitesAndUpdateHistory addToSitesArray(List<List<Integer>> sitesArray, int[] superlevels, Integer newSite) {
        List<List<Integer>> updatedSitesArray = new ArrayList<>(sitesArray);

        //Update superlevels
        boolean[] superlevelUpdateHistory = new boolean[superlevels.length];
        for (int j = 0; j < superlevels.length; j++) {
            List<Integer> currentSites = updatedSitesArray.get(superlevels[j]);
            if (!currentSites.contains(newSite)) {
                List<Integer> updatedSites = new ArrayList<>(currentSites);
                updatedSites.add(newSite);
                updatedSitesArray.set(superlevels[j], updatedSites);
                superlevelUpdateHistory[j] = true;
            }
        }

        return new SitesAndUpdateHistory(updatedSitesArray, superlevelUpdateHistory, null); //default add at end of list
    }

    //Update sites array by removing removedSite from all sublevels if present.
    public static SitesAndUpdateHistory removeFromSitesArray(List<List<Integer>> sitesArray, int[] sublevels, Integer removedSite) {
        List<List<Integer>> updatedSitesArray = new ArrayList<>(sitesArray);

        //Update sublevels
        boolean[] sublevelUpdateHistory = new boolean[sublevels.length];
        int[] sublevelUpdatedPositions = new int[sublevels.length];
        Arrays.fill(sublevelUpdatedPositions, -1);
        for (int j = 0; j < sublevels.length; j++) {
            List<Integer> currentSites = updatedSitesArray.get(sublevels[j]);
            for (int i = 0; i < currentSites.size(); i++) {
                if (currentSites.get(i).equals(removedSite)) {
                    List<Integer> updatedSites = new ArrayList<>(currentSites);
                    updatedSites.remove(removedSite);
                    updatedSitesArray.set(sublevels[j], updatedSites);
                    sublevelUpdateHistory[j] = true;
                    sublevelUpdatedPositions[j] = i;
                    break;
                }
            }
        }

        return new SitesAndUpdateHistory(updatedSitesArray, sublevelUpdateHistory,sublevelUpdatedPositions);
    }

    //Equal chance to try any level, performance is generally better if high likelihood that there is an available site. Shuffles SearchSpace superlevels by level.
    public List<Integer> getRandomSuperlevelUnusedSites(int level, int[] superlevels) {
        List<Integer> unusedSuperlevelSites = null;

        //Process target superlevels
        ArrayOperations.shuffleIntegerArray(superlevels);
        for (int superlevel : superlevels) {
            unusedSuperlevelSites = new ArrayList<>(sitesByLevel.get(superlevel));
            unusedSuperlevelSites.removeAll(sitesByLevel.get(level));
            if (unusedSuperlevelSites.size() > 0) {
                break;
            }
        }

        return unusedSuperlevelSites;
    }

    //Return possible superlevel sites to add and null if there are no restrictions, i.e. any superlevel site can be added
    public List<Integer> getRestrictedAddableSuperlevelSites(int level, int[] superlevels, int[] maximumNewCenterCountByLevel, int[] permanentCentersCountByLevel) {
        List<Integer> restrictedAddableSites = null;
        for (int superlevel: superlevels) {
            if (sitesByLevel.get(superlevel).size() == maximumNewCenterCountByLevel[superlevel] + permanentCentersCountByLevel[superlevel]) {
                if (restrictedAddableSites == null) {
                    restrictedAddableSites = new ArrayList<>(sitesByLevel.get(superlevel));
                } else {
                    restrictedAddableSites.retainAll(sitesByLevel.get(superlevel));
                }
            }
        }
        if (restrictedAddableSites != null) {
            if (restrictedAddableSites.size() == sitesByLevel.get(level).size()) {
                //candidate superlevel sites must be equal to already existing sites
                restrictedAddableSites = new ArrayList<>();
            } else {
                //remove existing sites
                restrictedAddableSites.removeAll(sitesByLevel.get(level));
            }
        }
        return restrictedAddableSites;
    }

    public List<Integer> getCandidateRemovalSites(int level, int[] sublevels, int[] minimumNewCenterCountByLevel, int[] permanentCentersCountByLevel) {
        List<Integer> candidateRemovalSites = new ArrayList<>(sitesByLevel.get(level).subList(permanentCentersCountByLevel[level], sitesByLevel.get(level).size()));
        for (int sublevel : sublevels) {
            if (sitesByLevel.get(sublevel).size() == minimumNewCenterCountByLevel[sublevel] + permanentCentersCountByLevel[sublevel]) {
                candidateRemovalSites.removeAll(sitesByLevel.get(sublevel));
            }
        }
        return candidateRemovalSites;
    }

    //Restrictions exist for superlevels where site count is already maximal and site to shift is a permanent site. All shifted superlevels include those superlevels of sublevels that are shifted.
    public List<Integer> getRestrictedShiftableSuperlevelSites(int level, int totalLevels, int positionToShift, List<List<Integer>> permanentSitesByLevel, int[][] superlevelsByLevel, int[][] sublevelsByLevel, int[] maximumNewCenterCountByLevel, int[] permanentCentersCountByLevel) {
        Integer siteToShift = sitesByLevel.get(level).get(positionToShift);

        //Additional sublevels and superlevels to cycle
        boolean[] sublevelProcessedHistory = new boolean[totalLevels];
        sublevelProcessedHistory[level] = true;
        boolean[] superlevelProcessedHistory = new boolean[totalLevels];
        superlevelProcessedHistory[level] = true;
        List<Integer> higherOrderSublevelsToProcess = new ArrayList<>();
        List<Integer> higherOrderSuperlevelsToProcess = new ArrayList<>();

        //Update target level sublevels
        //Any permanent center in sublevel is also permanent center in target level
        for (int sublevel : sublevelsByLevel[level]) {
            if (sitesByLevel.get(sublevel).contains(siteToShift)) {
                //Site will be shifted in sublevel, therefore process all superlevels
                for (int superlevel : superlevelsByLevel[sublevel]) {
                    if (!superlevelProcessedHistory[superlevel]) {
                        higherOrderSuperlevelsToProcess.add(superlevel);
                    }
                }
            }
            sublevelProcessedHistory[sublevel] = true;
        }

        //Process superlevels
        List<Integer> restrictedShiftSites = null;
        for (int superlevel : superlevelsByLevel[level]) {
            if (!permanentSitesByLevel.get(superlevel).contains(siteToShift)) {
                //superlevel loses site to shift, process sublevels
                for (int sublevel : sublevelsByLevel[superlevel]) {
                    if (!sublevelProcessedHistory[sublevel]) {
                        higherOrderSublevelsToProcess.add(sublevel);
                    }
                }
            } else if (sitesByLevel.get(superlevel).size() == maximumNewCenterCountByLevel[superlevel] + permanentCentersCountByLevel[superlevel]) {
                //site to shift is permanent and superlevel is already maximal
                if (restrictedShiftSites == null) {
                    restrictedShiftSites = new ArrayList<>(sitesByLevel.get(superlevel));
                } else {
                    restrictedShiftSites.retainAll(sitesByLevel.get(superlevel));
                }
            }
            superlevelProcessedHistory[superlevel] = true;
        }

        //Process secondary levels
        while (higherOrderSublevelsToProcess.size() > 0 || higherOrderSuperlevelsToProcess.size() > 0) {
            //Process higher order sublevels
            for (int sublevel : higherOrderSublevelsToProcess) {
                if (sitesByLevel.get(sublevel).contains(siteToShift)) {
                    //Site will be shifted in sublevel, therefore process all superlevels
                    for (int superlevel : superlevelsByLevel[sublevel]) {
                        if (!superlevelProcessedHistory[superlevel]) {
                            higherOrderSuperlevelsToProcess.add(superlevel);
                        }
                    }
                }
                sublevelProcessedHistory[sublevel] = true;
            }
            higherOrderSublevelsToProcess = new ArrayList<>();

            //Process higher order superlevels
            for (int superlevel : higherOrderSuperlevelsToProcess) {
                if (!permanentSitesByLevel.get(superlevel).contains(siteToShift)) {
                    //superlevel loses site to shift, process sublevels
                    for (int sublevel : sublevelsByLevel[superlevel]) {
                        if (!sublevelProcessedHistory[sublevel]) {
                            higherOrderSublevelsToProcess.add(sublevel);
                        }
                    }
                } else if (sitesByLevel.get(superlevel).size() == maximumNewCenterCountByLevel[superlevel] + permanentCentersCountByLevel[superlevel]) {
                    //site to shift is permanent and superlevel is already maximal
                    if (restrictedShiftSites == null) {
                        restrictedShiftSites = new ArrayList<>(sitesByLevel.get(superlevel));
                    } else {
                        restrictedShiftSites.retainAll(sitesByLevel.get(superlevel));
                    }
                }
                superlevelProcessedHistory[superlevel] = true;
            }
            higherOrderSuperlevelsToProcess = new ArrayList<>();
        }

        //Remove existing sites
        if (restrictedShiftSites != null) {
            if (restrictedShiftSites.size() == sitesByLevel.get(level).size()) {
                //candidate superlevel sites must gbe equal to already existing sites
                restrictedShiftSites = new ArrayList<>();
            } else {
                //remove existing to obtain final sites
                restrictedShiftSites.removeAll(sitesByLevel.get(level));
            }
        }

        return restrictedShiftSites;
    }

    public List<Integer> getSites(int level) {
        return sitesByLevel.get(level);
    }

    public List<List<Integer>> getSitesByLevel() {
        return sitesByLevel;
    }

    public int getSitesCount(int level) {
        return sitesByLevel.get(level).size();
    }

    public double[] getCostByLevel() {
        return costByLevel;
    }

    public int[][] getMinimumPositionsByLevelAndOrigin() {
        return minimumPositionsByLevelAndOrigin;
    }

    //Separate into permanent centers (pre-specified), expanded permanent centers (based on site number over potentialSitesCount), and new centers (potential sites).
    public DecomposedLeveledSites decomposeSites(int[] permanentCentersCountByLevel, int potentialSitesCount) {
        List<List<Integer>> permanentSitesByLevel = new ArrayList<>();
        List<List<Integer>> expandedPermanentSitesByLevel = new ArrayList<>();
        List<List<Integer>> newSitesByLevel = new ArrayList<>();

        for (int level = 0; level < sitesByLevel.size(); level++) {
            permanentSitesByLevel.add(sitesByLevel.get(level).stream().limit(permanentCentersCountByLevel[level]).map(x -> x - potentialSitesCount).collect(Collectors.toList()));
            List<Integer> expandedPermanentSites = new ArrayList<>();
            List<Integer> newSites = new ArrayList<>();
            for (int position = permanentCentersCountByLevel[level]; position < sitesByLevel.get(level).size(); position++) {
                if (sitesByLevel.get(level).get(position) >= potentialSitesCount) {
                    expandedPermanentSites.add(sitesByLevel.get(level).get(position) - potentialSitesCount);
                } else {
                    newSites.add(sitesByLevel.get(level).get(position));
                }
            }
            expandedPermanentSitesByLevel.add(expandedPermanentSites);
            newSitesByLevel.add(newSites);
        }

        return new DecomposedLeveledSites(permanentSitesByLevel, expandedPermanentSitesByLevel, newSitesByLevel);
    }

    //Output format for decomposeSites in the form of permanent, expanded permanent, and new sites.
    public record DecomposedLeveledSites(List<List<Integer>> permanentSitesByLevel, List<List<Integer>> expandedPermanentSitesByLevel, List<List<Integer>> newSitesByLevel) {
        public List<List<Integer>> getPermanentSitesByLevel() {
            return permanentSitesByLevel;
        }
        public List<List<Integer>> getExpandedSitesByLevel() {
            return expandedPermanentSitesByLevel;
        }
        public List<List<Integer>> getNewSitesByLevel() {
            return newSitesByLevel;
        }
    }
}