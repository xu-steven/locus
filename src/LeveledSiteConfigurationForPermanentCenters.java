import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LeveledSiteConfigurationForPermanentCenters extends SiteConfigurationForPermanentCenters {
    //Developmental leveled configuration
    protected List<List<Integer>> sitesByLevel;
    protected double[] costByLevel; //development only
    protected CasesAndCostMap[] costMapByLevel; //Costs are not adjusted by serviced proportion, adjustment occurs when cost calculator used
    protected int[][] minimumPositionsByLevelAndOrigin;

    public LeveledSiteConfigurationForPermanentCenters(List<List<Integer>> sitesByLevel, double totalCost, CasesAndCostMap[] costByLevel, int[][] minimumPositionsByLevelAndOrigin) {
        this.sitesByLevel = sitesByLevel;
        this.cost = totalCost;
        this.costMapByLevel = costByLevel;
        this.minimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin;
    }

    public LeveledSiteConfigurationForPermanentCenters(List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Generate initial site configuration
        Random random = new Random();
        List<Integer> candidateInitialSites = new ArrayList<>(pickNRandomFromList(potentialSites, Arrays.stream(searchParameters.getMaxNewCentersByLevel()).max().getAsInt(), random));

        //Start with permanent sites
        sitesByLevel = new ArrayList<>(searchParameters.getCenterLevels());
        for (List<Integer> levelSites : searchParameters.getPermanentCentersByLevel()) {
            sitesByLevel.add(new ArrayList<>(levelSites));
        }

        //Add to minimum number of sites
        boolean sufficientSites = false;
        while (sufficientSites == false) {
            for (int level = 0; level < searchParameters.getCenterLevels(); level++) {
                if (sitesByLevel.get(level).size() < searchParameters.getMinNewCentersByLevel()[level] + searchParameters.getPermanentCentersCountByLevel()[level]) {
                    if (searchParameters.getSuperlevelsByLevel()[level].length == 0) {
                        List<Integer> candidateSitesToAdd = new ArrayList<>(candidateInitialSites);
                        candidateSitesToAdd.removeAll(sitesByLevel.get(level));
                        Integer site = candidateSitesToAdd.get(random.nextInt(candidateSitesToAdd.size()));
                        sitesByLevel.get(level).add(site);
                    } else {
                        List<Integer> restrictedAddableSites = getRestrictedAddableSuperlevelSites(level, searchParameters.getSuperlevelsByLevel()[level], searchParameters.getMaxNewCentersByLevel(), searchParameters.getPermanentCentersCountByLevel());
                        Integer site;
                        if (restrictedAddableSites == null || restrictedAddableSites.size() == 0) {
                            List<Integer> unusedSuperlevelSites = getRandomSuperlevelUnusedSites(level, searchParameters.getSuperlevelsByLevel()[level]);
                            if (unusedSuperlevelSites.size() > 0) {
                                site = unusedSuperlevelSites.get(random.nextInt(unusedSuperlevelSites.size()));

                            } else {
                                List<Integer> candidateSitesToAdd = new ArrayList<>(candidateInitialSites);
                                candidateSitesToAdd.removeAll(sitesByLevel.get(level));
                                site = candidateSitesToAdd.get(random.nextInt(candidateSitesToAdd.size()));
                            }
                        } else {
                             site = restrictedAddableSites.get(random.nextInt(restrictedAddableSites.size()));
                        }
                        sitesByLevel.get(level).add(site);
                        sitesByLevel = addToSitesArray(sitesByLevel, searchParameters.getSuperlevelsByLevel()[level], site).getUpdatedSitesArray();
                    }
                } else if (sitesByLevel.get(level).size() > searchParameters.getMaxNewCentersByLevel()[level] + searchParameters.getPermanentCentersCountByLevel()[level]) {
                    int removableSiteCount = sitesByLevel.get(level).size() - searchParameters.getPermanentCentersCountByLevel()[level];
                    int positionToRemove = searchParameters.getPermanentCentersCountByLevel()[level] + random.nextInt(removableSiteCount);
                    Integer siteToRemove = sitesByLevel.get(level).get(positionToRemove);
                    sitesByLevel.get(level).remove(positionToRemove);
                    sitesByLevel = removeFromSitesArray(sitesByLevel, searchParameters.getSublevelsByLevel()[level], siteToRemove).getUpdatedSitesArray();
                    //50% chance remove another site
                    if (Math.random() < 0.5 && removableSiteCount > 1) {
                        positionToRemove = searchParameters.getPermanentCentersCountByLevel()[level] + random.nextInt(removableSiteCount);
                        siteToRemove = sitesByLevel.get(level).get(positionToRemove);
                        sitesByLevel.get(level).remove(positionToRemove);
                        sitesByLevel = removeFromSitesArray(sitesByLevel, searchParameters.getSublevelsByLevel()[level], siteToRemove).getUpdatedSitesArray();
                    }
                }
            }
            //Check if site counts meet constraints
            sufficientSites = true;
            for (int level = 0; level < searchParameters.getCenterLevels(); level++) {
                if (sitesByLevel.get(level).size() < searchParameters.getMinNewCentersByLevel()[level] + searchParameters.getPermanentCentersCountByLevel()[level]
                        || sitesByLevel.get(level).size() > searchParameters.getMaxNewCentersByLevel()[level] + searchParameters.getPermanentCentersCountByLevel()[level]) {
                    sufficientSites = false;
                }
            }
        }

        //Try to add additional sites
        List<Integer> randomSequence = IntStream.range(0, searchParameters.getCenterLevels()).boxed().collect(Collectors.toList());
        for (int i = 0; i < randomSequence.size(); i++) {
            int level = randomSequence.get(i);
            int targetAdditionalSiteCount = random.nextInt(searchParameters.getMaxNewCentersByLevel()[level] - searchParameters.getMinNewCentersByLevel()[level] + 1) + searchParameters.getMinNewCentersByLevel()[level] - sitesByLevel.get(level).size() + searchParameters.getPermanentCentersCountByLevel()[level];
            while (targetAdditionalSiteCount > 0) {
                if (searchParameters.getSuperlevelsByLevel()[level].length == 0) {
                    List<Integer> candidateSitesToAdd = new ArrayList<>(candidateInitialSites);
                    candidateSitesToAdd.removeAll(sitesByLevel.get(level));
                    Integer site = candidateSitesToAdd.get(random.nextInt(candidateSitesToAdd.size()));
                    sitesByLevel.get(level).add(site);
                    sitesByLevel = addToSitesArray(sitesByLevel, searchParameters.getSuperlevelsByLevel()[level], site).getUpdatedSitesArray();
                } else {
                    List<Integer> restrictedAddableSites = getRestrictedAddableSuperlevelSites(level, searchParameters.getSuperlevelsByLevel()[level], searchParameters.getMaxNewCentersByLevel(), searchParameters.getPermanentCentersCountByLevel());
                    if (restrictedAddableSites == null) {
                        List<Integer> unusedSuperlevelSites = getRandomSuperlevelUnusedSites(level, searchParameters.getSuperlevelsByLevel()[level]);
                        Integer site;
                        if (unusedSuperlevelSites.size() > 0) {
                            site = unusedSuperlevelSites.get(random.nextInt(unusedSuperlevelSites.size()));
                        } else {
                            List<Integer> candidateSitesToAdd = new ArrayList<>(candidateInitialSites);
                            candidateSitesToAdd.removeAll(sitesByLevel.get(level));
                            site = candidateSitesToAdd.get(random.nextInt(candidateSitesToAdd.size()));
                        }
                        sitesByLevel.get(level).add(site);
                        sitesByLevel = addToSitesArray(sitesByLevel, searchParameters.getSuperlevelsByLevel()[level], site).getUpdatedSitesArray();
                    } else if (restrictedAddableSites.size() > 0) {
                        Integer site = restrictedAddableSites.get(random.nextInt(restrictedAddableSites.size()));
                        sitesByLevel.get(level).add(site);
                        sitesByLevel = addToSitesArray(sitesByLevel, searchParameters.getSuperlevelsByLevel()[level], site).getUpdatedSitesArray();
                    } else { //no possible sites to add for this level
                        break;
                    }
                }
                targetAdditionalSiteCount -= 1;
            }
        }

        //Compute initial cost
        costByLevel = new double[searchParameters.getCenterLevels()];
        costMapByLevel = new CasesAndCostMap[searchParameters.getCenterLevels()];
        minimumPositionsByLevelAndOrigin = new int[searchParameters.getCenterLevels()][searchParameters.getOriginCount()];
        for (int level = 0; level < searchParameters.getCenterLevels(); ++level) {
            CostMapAndPositions initialResult = initialCost(sitesByLevel.get(level), searchParameters.getPermanentCentersCountByLevel()[level],
                    searchParameters.getMinPermanentPositionByLevelAndOrigin()[level], searchParameters.getMinPermanentCostByLevelAndOrigin()[level],
                    searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                    taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
            double initialLevelCost = CostCalculator.computeLevelSpecificBaseCost(initialResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());
            cost += initialLevelCost;
            costByLevel[level] = initialLevelCost;
            //minimumPositionsByLevelAndOrigin[i] = initialLevelCostAndPositions.getPositions();
            costMapByLevel[level] = initialResult.getCasesAndCostMap();
            minimumPositionsByLevelAndOrigin[level] = initialResult.getPositions();
        }
        //cost = CostCalculator.computeCost(costMapByLevel, sitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointWeights());
        cost = CostCalculator.computeCost(costMapByLevel, sitesByLevel, searchParameters.getMinimumCasesByLevel(), searchParameters.getServicedProportionByLevel(), searchParameters.getTimepointCount(), searchParameters.getTimepointWeights(), executor);
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
        CostMapAndPositions updatedResult = shiftSiteCost(newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getPermanentCentersCountByLevel()[level], searchParameters.getMinPermanentPositionByLevelAndOrigin()[level], searchParameters.getMinPermanentCostByLevelAndOrigin()[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Update other level sites
            List<List<Integer>> newSitesByLevel;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCostMap[] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getCasesAndCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
            if (searchParameters.getSublevelsByLevel()[level].length > 0 || searchParameters.getSuperlevelsByLevel()[level].length > 0) {
                SitesAndUpdateHistory updatedArrayAndHistory = shiftSitesArray(sitesByLevel, level, searchParameters.getCenterLevels(), searchParameters.getSublevelsByLevel(), searchParameters.getSuperlevelsByLevel(), searchParameters.getPermanentCentersCountByLevel(), siteToShift, newSite);
                newSitesByLevel = updatedArrayAndHistory.getUpdatedSitesArray();
                newSitesByLevel.set(level, newTargetLevelSites);
                boolean[] updateHistory = updatedArrayAndHistory.getUpdateHistory();
                int[] updatedPositions = updatedArrayAndHistory.getUpdatedPositions();
                for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
                    if (updateHistory[i]) {
                        if (updatedPositions[i] == -1) {
                            //when update history is true and positions is unchanged at -1, then a site was added
                            updatedResult = addSiteCost(newSitesByLevel.get(i), minimumPositionsByLevelAndOrigin[i],
                                    searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[i], searchParameters.getGraphArray(),
                                    taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
                        } else {
                            updatedResult = shiftSiteCost(newSitesByLevel.get(i), updatedPositions[i], newSite, minimumPositionsByLevelAndOrigin[i],
                                    searchParameters.getPermanentCentersCountByLevel()[i], searchParameters.getMinPermanentPositionByLevelAndOrigin()[i], searchParameters.getMinPermanentCostByLevelAndOrigin()[i],
                                    searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[i], searchParameters.getGraphArray(),
                                    taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
                        }
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

    //Multithreaded variant
    public void tryShiftToNeighborWithoutLevelRelations(int level, int positionToShift, int neighborhoodSize, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Shift target level sites
        List<Integer> currentTargetLevelSites = sitesByLevel.get(level);
        Integer siteToShift = currentTargetLevelSites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(currentTargetLevelSites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newTargetLevelSites = new ArrayList<>(currentTargetLevelSites);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        CostMapAndPositions updatedResult = shiftSiteCost(newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getPermanentCentersCountByLevel()[level], searchParameters.getMinPermanentPositionByLevelAndOrigin()[level], searchParameters.getMinPermanentCostByLevelAndOrigin()[level],
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

    //Multithreaded variant
    public void tryShiftSite(int level, int positionToShift, Integer newSite, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Shift current level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        Integer siteToShift = newTargetLevelSites.get(positionToShift);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        CostMapAndPositions updatedResult = shiftSiteCost(newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getPermanentCentersCountByLevel()[level], searchParameters.getMinPermanentPositionByLevelAndOrigin()[level], searchParameters.getMinPermanentCostByLevelAndOrigin()[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

        //Ensure that new target level cost is not excessive compared to total configuration cost
        if (SimAnnealingSearch.acceptanceProbability(cost, newTargetLevelBaseCost, temp) > targetLevelThresholdProbability) {
            //Update other level sites
            List<List<Integer>> newSitesByLevel;
            double newCost;
            double[] newCostByLevel = costByLevel.clone();
            newCostByLevel[level] = newTargetLevelBaseCost;
            CasesAndCostMap[] newCostMapByLevel = costMapByLevel.clone();
            newCostMapByLevel[level] = updatedResult.getCasesAndCostMap();
            int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
            if (searchParameters.getSublevelsByLevel()[level].length > 0 || searchParameters.getSuperlevelsByLevel()[level].length > 0) {
                SitesAndUpdateHistory updatedArrayAndHistory = shiftSitesArray(sitesByLevel, level, searchParameters.getCenterLevels(), searchParameters.getSublevelsByLevel(), searchParameters.getSuperlevelsByLevel(), searchParameters.getPermanentCentersCountByLevel(), siteToShift, newSite);
                newSitesByLevel = updatedArrayAndHistory.getUpdatedSitesArray();
                newSitesByLevel.set(level, newTargetLevelSites);
                boolean[] updateHistory = updatedArrayAndHistory.getUpdateHistory();
                int[] updatedPositions = updatedArrayAndHistory.getUpdatedPositions();
                for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
                    if (updateHistory[i]) {
                        if (updatedPositions[i] == -1) {
                            //when update history is true and positions is unchanged at -1, then a site was added
                            updatedResult = addSiteCost(newSitesByLevel.get(i), minimumPositionsByLevelAndOrigin[i],
                                    searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[i], searchParameters.getGraphArray(),
                                    taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
                        } else {
                            updatedResult = shiftSiteCost(newSitesByLevel.get(i), updatedPositions[i], newSite, minimumPositionsByLevelAndOrigin[i],
                                    searchParameters.getPermanentCentersCountByLevel()[i], searchParameters.getMinPermanentPositionByLevelAndOrigin()[i], searchParameters.getMinPermanentCostByLevelAndOrigin()[i],
                                    searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[i], searchParameters.getGraphArray(),
                                    taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
                        }
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

    //Unchanged from without permanent centers (only one of three)
    public void tryAddSite(int level, Integer newSite, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Add lowest level site
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

    //Unchanged from without permanent centers (only one of three)
    public void tryAddSiteWithoutSuperlevels(int level, Integer newSite, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Add lowest level site
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        newTargetLevelSites.add(newSite);

        //Compute new parameters
        CostMapAndPositions updatedResult = addSiteCost(newTargetLevelSites, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(),  executor);
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

    //Variant with multithreading of previous removeLowestLevelSite
    public void tryRemoveSite(int level, Integer removalSite, SearchSpace searchParameters, double temp, double targetLevelThresholdProbability, int taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        int removalPosition = newTargetLevelSites.indexOf(removalSite);
        newTargetLevelSites.remove(removalSite);

        //Compute new parameters
        CostMapAndPositions updatedResult = removeSiteCost(newTargetLevelSites, removalPosition, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getPermanentCentersCountByLevel()[level], searchParameters.getMinPermanentPositionByLevelAndOrigin()[level], searchParameters.getMinPermanentCostByLevelAndOrigin()[level],
                searchParameters.getTimepointCount(), searchParameters.getOriginCount(), searchParameters.getCaseCountsByLevel()[level], searchParameters.getGraphArray(),
                taskCount, searchParameters.getStartingOrigins(), searchParameters.getEndingOrigins(), executor);
        double newTargetLevelBaseCost = CostCalculator.computeLevelSpecificBaseCost(updatedResult.getCasesAndCostMap(), searchParameters.getMinimumCasesByLevel()[level], searchParameters.getServicedProportionByLevel()[level], searchParameters.getTimepointWeights());

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
            newMinimumPositionsByLevelAndOrigin[level] = updatedResult.getPositions();
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
                                searchParameters.getPermanentCentersCountByLevel()[sublevels[i]], searchParameters.getMinPermanentPositionByLevelAndOrigin()[sublevels[i]], searchParameters.getMinPermanentCostByLevelAndOrigin()[sublevels[i]],
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

    //Variant with multithreading of previous removeLowestLevelSite
    public void tryRemovePositionWithoutSublevels(int level, int removalPosition, SearchSpace searchParameters, double temp, int taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        newTargetLevelSites.remove(removalPosition);

        //Compute new parameters
        CostMapAndPositions updatedResult = removeSiteCost(newTargetLevelSites, removalPosition, minimumPositionsByLevelAndOrigin[level],
                searchParameters.getPermanentCentersCountByLevel()[level], searchParameters.getMinPermanentPositionByLevelAndOrigin()[level], searchParameters.getMinPermanentCostByLevelAndOrigin()[level],
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

    //Restrictions exist for superlevels where site count is already maximal and site to shift is a permanent site. All shifted superlevels include those superlevels of sublevels that are shifted. Returns null if there are no restrictions. Returns empty list if there are no permissible moves. Otherwise, returns list of permissible sites to which one can shift.
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
                //candidate superlevel sites must be equal to already existing sites
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