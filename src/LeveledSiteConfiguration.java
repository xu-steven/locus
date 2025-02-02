import java.util.*;
import java.util.concurrent.ExecutorService;

public class LeveledSiteConfiguration extends SiteConfiguration {
    //Developmental leveled configuration
    protected List<List<Integer>> sitesByLevel;
    protected double[] costByLevel; //dev only
    protected CasesAndCostMap[] costMapByLevel; //Costs are not adjusted by serviced proportion, adjustment occurs when cost calculator used
    protected int[][] minimumPositionsByLevelAndOrigin;

    public LeveledSiteConfiguration(List<List<Integer>> sitesByLevel, double totalCost, double[] costByLevel, int[][] minimumPositionsByLevelAndOrigin) {
        this.sitesByLevel = sitesByLevel;
        this.cost = totalCost;
        this.costByLevel = costByLevel;
        this.minimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin;
    }

    //Generates initial configuration
    public LeveledSiteConfiguration(List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Generate initial site configuration
        Random random = new Random();
        List<Integer> candidateInitialSites = new ArrayList<>(pickNRandomFromList(potentialSites, Arrays.stream(searchParameters.getMaxNewCentersByLevel()).max().getAsInt(), random));

        //Create site configuration for each level
        sitesByLevel = new ArrayList<>();
        for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
            //Initialize level
            int initialSiteCount = random.nextInt(searchParameters.getMaxNewCentersByLevel()[i] - searchParameters.getMinNewCentersByLevel()[i] + 1) + searchParameters.getMinNewCentersByLevel()[i];
            List<Integer> initialSites = new ArrayList<>(candidateInitialSites.subList(0, initialSiteCount));
            sitesByLevel.add(initialSites);
        }

        //Update site configurations to respect level relations
        for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
            //Update all superlevels
            for (int superlevel : searchParameters.getSuperlevelsByLevel()[i]) {
                for (Integer site : sitesByLevel.get(i)) {
                    if(!sitesByLevel.get(superlevel).contains(site)) {
                        sitesByLevel.get(superlevel).add(site);
                    }
                }
                //Check to ensure superlevel site count is permissible, i.e. did not overload by adding sublevel
                if (sitesByLevel.get(superlevel).size() > searchParameters.getMaxNewCentersByLevel()[superlevel]) {
                    System.out.println("Overloaded superlevel " + superlevel + " of new level " + i);
                }
            }
        }

        //Compute initial cost
        costByLevel = new double[searchParameters.getCenterLevels()];
        costMapByLevel = new CasesAndCostMap[searchParameters.getCenterLevels()];
        minimumPositionsByLevelAndOrigin = new int[searchParameters.getCenterLevels()][searchParameters.getOriginCount()];
        for (int level = 0; level < searchParameters.getCenterLevels(); ++level) {
            CostMapAndPositions initialResult = initialCost(sitesByLevel.get(level), searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                    taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
            //dev
            double initialLevelCost = CostCalculator.computeLevelSpecificBaseCost(initialResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());
            cost += initialLevelCost;
            costByLevel[level] = initialLevelCost;
            //end dev
            costMapByLevel[level] = initialResult.getCasesAndCostMap();
            minimumPositionsByLevelAndOrigin[level] = initialResult.getPositions();
        }
        //cost = CostCalculator.computeCost(costMapByLevel, sitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
        cost = CostCalculator.computeCost(costMapByLevel, sitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
    }

    //Get new leveled site configuration by shifting one of the lowest level sites
    //Multithreaded variant
    public void tryShiftToNeighbor(int level, int positionToShift, int neighborhoodSize, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Shift target level sites
        List<Integer> currentTargetLevelSites = sitesByLevel.get(level);
        Integer siteToShift = currentTargetLevelSites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(currentTargetLevelSites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newTargetLevelSites = new ArrayList<>(currentTargetLevelSites);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        CostMapAndPositions updatedResult = shiftSiteCost(newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Create new leveled sites array
            List<List<Integer>> newSitesByLevel;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCostMap[] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getCasesAndCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
            if (searchParameters.getSublevelsByLevel()[level].length > 0 || searchParameters.getSuperlevelsByLevel()[level].length > 0) {
                SitesAndUpdateHistory updatedArrayAndHistory = shiftSitesArray(sitesByLevel, level, searchParameters.getCenterLevels(), searchParameters.getSublevelsByLevel(), searchParameters.getSuperlevelsByLevel(), siteToShift, newSite);
                newSitesByLevel = updatedArrayAndHistory.getUpdatedSitesArray();
                newSitesByLevel.set(level, newTargetLevelSites);
                boolean[] updateHistory = updatedArrayAndHistory.getUpdateHistory();
                int[] updatedPositions = updatedArrayAndHistory.getUpdatedPositions();
                for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
                    if (updateHistory[i]) {
                        updatedResult = shiftSiteCost(newSitesByLevel.get(i), updatedPositions[i], newSite, minimumPositionsByLevelAndOrigin[i],
                                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[i], searchParameters.getGraphArray(),
                                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
                        double levelCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[i], searchParameters.getServicedProportionByLevel()[i], searchParameters.getTimepointWeights());
                        newCostByLevel[i] = levelCost;
                        newCostMapByLevel[i] = updatedResult.getCasesAndCostMap();
                        newMinimumPositionsByLevelAndOrigin[i] = updatedResult.getPositions();
                    }
                }
                //newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
                newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
            } else {
                newSitesByLevel = new ArrayList<>(sitesByLevel);
                newSitesByLevel.set(level, newTargetLevelSites);
                //newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
                newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
            }

            //Decide if cost change is acceptable
            if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
                sitesByLevel = newSitesByLevel;
                cost = newCost;
                costByLevel = newCostByLevel;
                costMapByLevel = newCostMapByLevel;
                minimumPositionsByLevelAndOrigin = newMinimumPositionsByLevelAndOrigin;
            }
        }
    }

    //Try shift site on a target level without superlevels or sublevels
    public void tryShiftToNeighborWithoutLevelRelations(int level, int positionToShift, int neighborhoodSize, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Shift target level sites
        List<Integer> currentTargetLevelSites = sitesByLevel.get(level);
        Integer siteToShift = currentTargetLevelSites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(currentTargetLevelSites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newTargetLevelSites = new ArrayList<>(currentTargetLevelSites);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        CostMapAndPositions updatedResult = shiftSiteCost(newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        List<List<Integer>> newSitesByLevel  = new ArrayList<>(sitesByLevel);
        newSitesByLevel.set(level, newTargetLevelSites);
        CasesAndCostMap[] newCostMapByLevel = costMapByLevel.clone();
        newCostMapByLevel[level] = updatedResult.getCasesAndCostMap();
        //double newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
        double newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);

        //Decide if cost change is acceptable
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sitesByLevel.set(level, newTargetLevelSites);
            cost = newCost;
            costByLevel[level] = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());
            costMapByLevel = newCostMapByLevel;
            minimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
        }
    }

    //Get new leveled site configuration by shifting one of the lowest level sites
    public void tryShiftSite(int level, int positionToShift, Integer newSite, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Shift target level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        Integer siteToShift = newTargetLevelSites.get(positionToShift);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        CostMapAndPositions updatedResult = shiftSiteCost(newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Create new leveled sites array
            List<List<Integer>> newSitesByLevel;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCostMap[] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getCasesAndCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
            if (searchParameters.getSublevelsByLevel()[level].length > 0 || searchParameters.getSuperlevelsByLevel()[level].length > 0) {
                SitesAndUpdateHistory updatedArrayAndHistory = shiftSitesArray(sitesByLevel, level, searchParameters.getCenterLevels(), searchParameters.getSublevelsByLevel(), searchParameters.getSuperlevelsByLevel(), siteToShift, newSite);
                newSitesByLevel = updatedArrayAndHistory.getUpdatedSitesArray();
                newSitesByLevel.set(level, newTargetLevelSites);
                boolean[] updateHistory = updatedArrayAndHistory.getUpdateHistory();
                int[] updatedPositions = updatedArrayAndHistory.getUpdatedPositions();
                for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
                    if (updateHistory[i]) {
                        updatedResult = shiftSiteCost(newSitesByLevel.get(i), updatedPositions[i], newSite, minimumPositionsByLevelAndOrigin[i],
                                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[i], searchParameters.getGraphArray(),
                                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
                        double levelCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[i], searchParameters.getServicedProportionByLevel()[i], searchParameters.getTimepointWeights());
                        newCostByLevel[i] = levelCost;
                        newCostMapByLevel[i] = updatedResult.getCasesAndCostMap();
                        newMinimumPositionsByLevelAndOrigin[i] = updatedResult.getPositions();
                    }
                }
                //newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
                newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
            } else {
                newSitesByLevel = new ArrayList<>(sitesByLevel);
                newSitesByLevel.set(level, newTargetLevelSites);
                //newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
                newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
            }

            //Decide if cost change is acceptable
            if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
                sitesByLevel = newSitesByLevel;
                cost = newCost;
                costByLevel = newCostByLevel;
                costMapByLevel = newCostMapByLevel;
                minimumPositionsByLevelAndOrigin = newMinimumPositionsByLevelAndOrigin;
            }
        }
    }

    //Add one site to target level and superlevels
    public void tryAddSite(int level, Integer newSite, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Add target level site
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        newTargetLevelSites.add(newSite);

        //Compute new parameters
        CostMapAndPositions updatedResult = addSiteCost(newTargetLevelSites, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Update arrays and adjust for superlevel sites
            List<List<Integer>> newSitesByLevel;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCostMap[] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getCasesAndCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
            if (searchParameters.getSuperlevelsByLevel()[level].length > 0) {
                int[] superlevels = searchParameters.getSuperlevelsByLevel()[level];
                SitesAndUpdateHistory updatedArrayAndHistory = addToSitesArray(sitesByLevel, superlevels, newSite);
                newSitesByLevel = updatedArrayAndHistory.getUpdatedSitesArray();
                newSitesByLevel.set(level, newTargetLevelSites);
                boolean[] superlevelUpdateHistory = updatedArrayAndHistory.getUpdateHistory();
                for (int i = 0; i < superlevels.length; i++) {
                    if (superlevelUpdateHistory[i]) {
                        updatedResult = addSiteCost(newSitesByLevel.get(superlevels[i]), minimumPositionsByLevelAndOrigin[superlevels[i]],
                                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[superlevels[i]], searchParameters.getGraphArray(),
                                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
                        double levelCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[superlevels[i]], searchParameters.getServicedProportionByLevel()[superlevels[i]], searchParameters.getTimepointWeights());
                        newCostByLevel[superlevels[i]] = levelCost;
                        newCostMapByLevel[superlevels[i]] = updatedResult.getCasesAndCostMap();
                        newMinimumPositionsByLevelAndOrigin[superlevels[i]] = updatedResult.getPositions();
                    }
                }
                //newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
                newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
            } else {
                newSitesByLevel = new ArrayList<>(sitesByLevel);
                newSitesByLevel.set(level, newTargetLevelSites);
                //newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
                newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
            }

            //Decide if cost change is acceptable
            if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
                sitesByLevel = newSitesByLevel;
                cost = newCost;
                costByLevel = newCostByLevel;
                costMapByLevel = newCostMapByLevel;
                minimumPositionsByLevelAndOrigin = newMinimumPositionsByLevelAndOrigin;
            }
        }
    }

    //Try add site to level without superlevels
    public void tryAddSiteWithoutSuperlevels(int level, Integer newSite, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Add target level site
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        newTargetLevelSites.add(newSite);

        //Compute new parameters
        CostMapAndPositions updatedResult = addSiteCost(newTargetLevelSites, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        List<List<Integer>> newSitesByLevel  = new ArrayList<>(sitesByLevel);
        newSitesByLevel.set(level, newTargetLevelSites);
        CasesAndCostMap[] newCostMapByLevel = costMapByLevel.clone();
        newCostMapByLevel[level] = updatedResult.getCasesAndCostMap();
        //double newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
        double newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);

        //Decide if cost change is acceptable
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sitesByLevel.set(level, newTargetLevelSites);
            cost = newCost;
            costByLevel[level] = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());
            costMapByLevel = newCostMapByLevel;
            minimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
        }
    }

    //Remove lowest level site that is not used by higher level site
    public void tryRemoveSite(int level, Integer removalSite, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        int removalPosition = newTargetLevelSites.indexOf(removalSite);
        newTargetLevelSites.remove(removalSite);

        //Compute new parameters
        CostMapAndPositions updatedResult = removeSiteCost(newTargetLevelSites, removalPosition, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());
        int[] newTargetLevelMinimumPositionsByOrigin = updatedResult.getPositions();

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Update arrays and adjust for sublevel sites
            List<List<Integer>> newSitesByLevel;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCostMap[] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getCasesAndCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = newTargetLevelMinimumPositionsByOrigin;
            if (searchParameters.getSublevelsByLevel()[level].length > 0) {
                int[] sublevels = searchParameters.getSublevelsByLevel()[level];
                SitesAndUpdateHistory updatedArrayAndHistory = removeFromSitesArray(sitesByLevel, sublevels, removalSite);
                newSitesByLevel = updatedArrayAndHistory.getUpdatedSitesArray();
                newSitesByLevel.set(level, newTargetLevelSites);
                boolean[] sublevelUpdateHistory = updatedArrayAndHistory.getUpdateHistory();
                int[] sublevelUpdatedPositions = updatedArrayAndHistory.getUpdatedPositions();
                for (int i = 0; i < sublevels.length; i++) {
                    if (sublevelUpdateHistory[i]) {
                        updatedResult = removeSiteCost(newSitesByLevel.get(sublevels[i]), sublevelUpdatedPositions[i], minimumPositionsByLevelAndOrigin[sublevels[i]],
                                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[sublevels[i]], searchParameters.getGraphArray(),
                                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
                        double levelCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[sublevels[i]], searchParameters.getServicedProportionByLevel()[sublevels[i]], searchParameters.getTimepointWeights());
                        newCostByLevel[sublevels[i]] = levelCost;
                        newCostMapByLevel[sublevels[i]] = updatedResult.getCasesAndCostMap();
                        newMinimumPositionsByLevelAndOrigin[sublevels[i]] = updatedResult.getPositions();
                    }
                }
                //newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
                newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
            } else {
                newSitesByLevel = new ArrayList<>(sitesByLevel);
                newSitesByLevel.set(level, newTargetLevelSites);
                //newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
                newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
            }

            //Decide if cost change is acceptable
            if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
                sitesByLevel = newSitesByLevel;
                cost = newCost;
                costByLevel = newCostByLevel;
                costMapByLevel = newCostMapByLevel;
                minimumPositionsByLevelAndOrigin = newMinimumPositionsByLevelAndOrigin;
            }
        }
    }

    //Remove a position from target level without sublevels
    public void tryRemovePositionWithoutSublevels(int level, int removalPosition, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        newTargetLevelSites.remove(removalPosition);

        //Compute new parameters
        CostMapAndPositions updatedResult = removeSiteCost(newTargetLevelSites, removalPosition, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        List<List<Integer>> newSitesByLevel  = new ArrayList<>(sitesByLevel);
        newSitesByLevel.set(level, newTargetLevelSites);
        CasesAndCostMap[] newCostMapByLevel = costMapByLevel.clone();
        newCostMapByLevel[level] = updatedResult.getCasesAndCostMap();
        //double newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
        double newCost = CostCalculator.computeCost(newCostMapByLevel, newSitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);

        //Decide if cost change is acceptable
        if (SimAnnealingSearch.acceptanceProbability(cost, newCost, temp) > Math.random()) {
            sitesByLevel.set(level, newTargetLevelSites);
            cost = newCost;
            costByLevel[level] = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());
            costMapByLevel = newCostMapByLevel;
            minimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
        }
    }

    //Update sites array by replacing removedSite with newSite for all sites in the array. Output is updated sites array, updated positions, and history of updates, true for each level that was changed and false if not.
    public static SitesAndUpdateHistory shiftSitesArray(List<List<Integer>> sitesArray, int level, int totalLevels, int[][] sublevelsByLevel, int[][] superlevelsByLevel, Integer removedSite, Integer newSite) {
        List<List<Integer>> updatedSitesArray = new ArrayList<>(sitesArray);

        boolean[] updateHistory = new boolean[totalLevels];
        int[] updatedPositions = new int[totalLevels];
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
        for (int superlevel : superlevels) {
            List<Integer> updatedSites = new ArrayList<>(updatedSitesArray.get(superlevel));
            int updatedPosition = -1;
            for (int position = 0; position < updatedSites.size(); position++) {
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
                            higherOrderSublevelsToProcess.add(sublevel);
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
            for (int superlevel : higherOrderSuperlevelsToProcess) {
                List<Integer> updatedSites = new ArrayList<>(updatedSitesArray.get(superlevel));
                int updatedPosition = -1;
                for (int position = 0; position < updatedSites.size(); position++) {
                    if (updatedSites.get(position).equals(newSite)) { //if already containing site
                        break;
                    }
                    if (updatedSites.get(position).equals(removedSite)) { //move removed site to site
                        updatedSites.set(position, newSite);
                    }
                    if (position == updatedSites.size() - 1) { //cycle to end before updating in case site is already contained
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

        return new SitesAndUpdateHistory(updatedSitesArray, superlevelUpdateHistory, null);
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

    public List<Integer> getUnusedSuperlevelSites(int level, int[] superlevels) {
        Set<Integer> unusedSuperlevelSites = new HashSet<>();
        for (int superlevel : superlevels) {
            unusedSuperlevelSites.addAll(sitesByLevel.get(superlevel));
        }
        unusedSuperlevelSites.removeAll(sitesByLevel.get(level));
        return new ArrayList<>(unusedSuperlevelSites);
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
    public List<Integer> getRestrictedAddableSuperlevelSites(int level, int[] superlevels, int[] maximumCenterCountByLevel) {
        List<Integer> restrictedAddableSites = null;

        //Process target superlevels
        for (int superlevel: superlevels) {
            if (sitesByLevel.get(superlevel).size() == maximumCenterCountByLevel[superlevel]) {
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


    public List<Integer> getCandidateRemovalSites(int level, int[] sublevels, int[] minimumCenterCountByLevel) {
        List<Integer> candidateRemovalSites = new ArrayList<>(sitesByLevel.get(level));
        for (int sublevel : sublevels) {
            if (sitesByLevel.get(sublevel).size() == minimumCenterCountByLevel[sublevel]) {
                candidateRemovalSites.removeAll(sitesByLevel.get(sublevel));
            }
        }
        return candidateRemovalSites;
    }

    public List<Integer> getSites(int level) {
        return sitesByLevel.get(level);
    }

    public double getCost() {
        return cost;
    }

    public int[] getMinimumPositionsByOrigin() {
        return minimumPositionsByOrigin;
    }

    public List<List<Integer>> getSitesByLevel() {
        return sitesByLevel;
    }

    public double[] getCostByLevel() {
        return costByLevel;
    }

    public int[][] getMinimumPositionsByLevelAndOrigin() {
        return minimumPositionsByLevelAndOrigin;
    }

    public int getSitesCount(int level) {
        return sitesByLevel.get(level).size();
    }

    public int getTotalSitesCount() {
        Set<Integer> allSites = new HashSet<>(sitesByLevel.get(0));
        for (int i = 1; i < costByLevel.length; i++) {
            allSites.addAll(sitesByLevel.get(i));
        }
        return allSites.size();
    }
}