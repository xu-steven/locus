import java.util.*;
import java.util.concurrent.ExecutorService;

public class LeveledSiteConfiguration extends SiteConfiguration {
    //Developmental leveled configuration
    protected List<List<Integer>> sitesByLevel;
    protected double[] costByLevel;
    protected int[][] minimumPositionsByLevelAndOrigin;

    public LeveledSiteConfiguration(List<List<Integer>> sitesByLevel, double totalCost, double[] costByLevel, int[][] minimumPositionsByLevelAndOrigin) {
        this.sitesByLevel = sitesByLevel;
        this.cost = totalCost;
        this.costByLevel = costByLevel;
        this.minimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin;
    }

    //Generates initial configuration
    public LeveledSiteConfiguration(int[] minimumCenterCountByLevel, int[] maximumCenterCountByLevel, List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Generate initial site configuration
        Random random = new Random();
        List<Integer> candidateInitialSites = new ArrayList<>(pickNRandomFromList(potentialSites, Arrays.stream(maximumCenterCountByLevel).max().getAsInt(), random));

        //Create site configuration for each level
        sitesByLevel = new ArrayList<>();
        for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
            //Initialize level
            int initialSiteCount = random.nextInt(maximumCenterCountByLevel[i] - minimumCenterCountByLevel[i] + 1) + minimumCenterCountByLevel[i];
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
                if (sitesByLevel.get(superlevel).size() > maximumCenterCountByLevel[superlevel]) {
                    System.out.println("Overloaded superlevel " + superlevel + " of new level " + i);
                }
            }
        }

        //Compute initial cost
        costByLevel = new double[searchParameters.getCenterLevels()];
        minimumPositionsByLevelAndOrigin = new int[searchParameters.getCenterLevels()][searchParameters.getOriginCount()];
        for (int i = 0; i < searchParameters.getCenterLevels(); ++i) {
            ConfigurationCostAndPositions initialLevelCostAndPositions = initialCost(sitesByLevel.get(i), searchParameters.getMinimumCasesByLevel()[i], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
            double initialLevelCost = initialLevelCostAndPositions.getCost() * searchParameters.getServicedProportionByLevel()[i];
            cost += initialLevelCost;
            costByLevel[i] = initialLevelCost;
            minimumPositionsByLevelAndOrigin[i] = initialLevelCostAndPositions.getPositions();
        }
    }

    //Get new leveled site configuration by shifting one of the lowest level sites
    //Multithreaded variant
    public LeveledSiteConfiguration leveledShiftToNeighborSite(int level, int positionToShift, int neighborhoodSize, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Shift target level sites
        List<Integer> currentTargetLevelSites = sitesByLevel.get(level);
        Integer siteToShift = currentTargetLevelSites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(currentTargetLevelSites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newTargetLevelSites = new ArrayList<>(currentTargetLevelSites);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        ConfigurationCostAndPositions updatedResult = shiftSiteCost(newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin[level], searchParameters.getMinimumCasesByLevel()[level], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newTargetLevelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[level];
        int[] newTargetLevelMinimumPositionsByOrigin = updatedResult.getPositions();

        //Create new leveled sites array
        List<List<Integer>> newLeveledSitesArray;
        double totalCost;
        double[] newCostByLevel = costByLevel.clone();
        newCostByLevel[level] = newTargetLevelCost;
        int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
        newMinimumPositionsByLevelAndOrigin[level] = newTargetLevelMinimumPositionsByOrigin;
        if (searchParameters.getSublevelsByLevel()[level].length > 0 || searchParameters.getSuperlevelsByLevel()[level].length > 0) {
            SitesAndUpdateHistory updatedArrayAndHistory = shiftSitesArray(sitesByLevel, level, searchParameters.getCenterLevels(), searchParameters.getSublevelsByLevel(), searchParameters.getSuperlevelsByLevel(), siteToShift, newSite);
            newLeveledSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
            newLeveledSitesArray.set(level, newTargetLevelSites);
            boolean[] updateHistory = updatedArrayAndHistory.getUpdateHistory();
            int[] updatedPositions = updatedArrayAndHistory.getUpdatedPositions();
            for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
                if (updateHistory[i]) {
                    updatedResult = shiftSiteCost(newLeveledSitesArray.get(i), updatedPositions[i], newSite, minimumPositionsByLevelAndOrigin[i], searchParameters.getMinimumCasesByLevel()[i], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
                    double levelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[i];
                    newCostByLevel[i] = levelCost;
                    newMinimumPositionsByLevelAndOrigin[i] = updatedResult.getPositions();
                }
            }
            totalCost = ArrayOperations.sumDoubleArray(newCostByLevel);
        } else {
            newLeveledSitesArray = new ArrayList<>(sitesByLevel);
            newLeveledSitesArray.set(level, newTargetLevelSites);
            totalCost = cost + newTargetLevelCost - costByLevel[level];
        }
        return new LeveledSiteConfiguration(newLeveledSitesArray, totalCost, newCostByLevel, newMinimumPositionsByLevelAndOrigin);
    }

    //Get new leveled site configuration by shifting one of the lowest level sites
    //Multithreaded variant
    public LeveledSiteConfiguration leveledShiftToPotentialSite(int level, int positionToShift, List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Shift target level sites
        List<Integer> currentTargetLevelSites = sitesByLevel.get(level);
        Integer siteToShift = currentTargetLevelSites.get(positionToShift);
        Random random = new Random();
        Integer newSite = potentialSites.get(random.nextInt(potentialSites.size()));
        List<Integer> newTargetLevelSites = new ArrayList<>(currentTargetLevelSites);
        newTargetLevelSites.set(positionToShift, newSite);

        //Compute cost of new positions and update list of the closest of current positions for each population center
        ConfigurationCostAndPositions updatedResult = shiftSiteCost(newTargetLevelSites, positionToShift, newSite, minimumPositionsByLevelAndOrigin[level], searchParameters.getMinimumCasesByLevel()[level], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newTargetLevelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[level];
        int[] newTargetLevelMinimumPositionsByOrigin = updatedResult.getPositions();

        //Create new leveled sites array
        List<List<Integer>> newLeveledSitesArray;
        double totalCost;
        double[] newCostByLevel = costByLevel.clone();
        newCostByLevel[level] = newTargetLevelCost;
        int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
        newMinimumPositionsByLevelAndOrigin[level] = newTargetLevelMinimumPositionsByOrigin;
        if (searchParameters.getSublevelsByLevel()[level].length > 0 || searchParameters.getSuperlevelsByLevel()[level].length > 0) {
            SitesAndUpdateHistory updatedArrayAndHistory = shiftSitesArray(sitesByLevel, level, searchParameters.getCenterLevels(), searchParameters.getSublevelsByLevel(), searchParameters.getSuperlevelsByLevel(), siteToShift, newSite);
            newLeveledSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
            newLeveledSitesArray.set(level, newTargetLevelSites);
            boolean[] updateHistory = updatedArrayAndHistory.getUpdateHistory();
            int[] updatedPositions = updatedArrayAndHistory.getUpdatedPositions();
            for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
                if (updateHistory[i]) {
                    updatedResult = shiftSiteCost(newLeveledSitesArray.get(i), updatedPositions[i], newSite, minimumPositionsByLevelAndOrigin[i], searchParameters.getMinimumCasesByLevel()[i], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
                    double levelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[i];
                    newCostByLevel[i] = levelCost;
                    newMinimumPositionsByLevelAndOrigin[i] = updatedResult.getPositions();
                }
            }
            totalCost = ArrayOperations.sumDoubleArray(newCostByLevel);
        } else {
            newLeveledSitesArray = new ArrayList<>(sitesByLevel);
            newLeveledSitesArray.set(level, newTargetLevelSites);
            totalCost = cost + newTargetLevelCost - costByLevel[level];
        }
        return new LeveledSiteConfiguration(newLeveledSitesArray, totalCost, newCostByLevel, newMinimumPositionsByLevelAndOrigin);
    }

    //Add one site to target level and superlevels
    public LeveledSiteConfiguration leveledAddSite(int level, List<Integer> allPotentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Add target level site
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        List<Integer> unusedSites = new ArrayList<>(allPotentialSites);
        unusedSites.removeAll(newTargetLevelSites);
        Random random = new Random();
        Integer newSite = unusedSites.get(random.nextInt(unusedSites.size()));
        newTargetLevelSites.add(newSite);

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = addSiteCost(newTargetLevelSites, minimumPositionsByLevelAndOrigin[level], searchParameters.getMinimumCasesByLevel()[level], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newTargetLevelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[level];
        int[] newTargetLevelMinimumPositionsByOrigin = updatedResult.getPositions();

        //Update arrays and adjust for superlevel sites
        List<List<Integer>> newLeveledSitesArray;
        double totalCost;
        double[] newCostByLevel = costByLevel.clone();
        newCostByLevel[level] = newTargetLevelCost;
        int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
        newMinimumPositionsByLevelAndOrigin[level] = newTargetLevelMinimumPositionsByOrigin;
        if (searchParameters.getSuperlevelsByLevel()[level].length > 0) {
            int[] superlevels = searchParameters.getSuperlevelsByLevel()[level];
            SitesAndUpdateHistory updatedArrayAndHistory = addToSitesArray(sitesByLevel, superlevels, newSite);
            newLeveledSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
            newLeveledSitesArray.set(level, newTargetLevelSites);
            boolean[] superlevelUpdateHistory = updatedArrayAndHistory.getUpdateHistory();
            for (int i = 0; i < superlevels.length; i++) {
                if (superlevelUpdateHistory[i]) {
                    updatedResult = addSiteCost(newLeveledSitesArray.get(superlevels[i]), minimumPositionsByLevelAndOrigin[superlevels[i]], searchParameters.getMinimumCasesByLevel()[superlevels[i]], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
                    double levelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[superlevels[i]];
                    newCostByLevel[superlevels[i]] = levelCost;
                    newMinimumPositionsByLevelAndOrigin[superlevels[i]] = updatedResult.getPositions();
                }
            }
            totalCost = ArrayOperations.sumDoubleArray(newCostByLevel);
        } else {
            newLeveledSitesArray = new ArrayList<>(sitesByLevel);
            newLeveledSitesArray.set(level, newTargetLevelSites);
            totalCost = cost + newTargetLevelCost - costByLevel[level];
        }

        return new LeveledSiteConfiguration(newLeveledSitesArray, totalCost, newCostByLevel, newMinimumPositionsByLevelAndOrigin);
    }

    //Remove lowest level site that is not used by higher level site
    public LeveledSiteConfiguration leveledRemoveSite(int level, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Remove one of the target level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        Random random = new Random();
        Integer removalSite = newTargetLevelSites.get(random.nextInt(newTargetLevelSites.size()));
        int removalPosition = newTargetLevelSites.indexOf(removalSite);
        newTargetLevelSites.remove(removalSite);

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = removeSiteCost(newTargetLevelSites, removalPosition, minimumPositionsByLevelAndOrigin[level], searchParameters.getMinimumCasesByLevel()[level], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newTargetLevelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[level];
        int[] newTargetLevelMinimumPositionsByOrigin = updatedResult.getPositions();

        //Update arrays and adjust for sublevel sites
        List<List<Integer>> newLeveledSitesArray;
        double totalCost;
        double[] newCostByLevel = costByLevel.clone();
        newCostByLevel[level] = newTargetLevelCost;
        int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
        newMinimumPositionsByLevelAndOrigin[level] = newTargetLevelMinimumPositionsByOrigin;
        if (searchParameters.getSublevelsByLevel()[level].length > 0) {
            int[] sublevels = searchParameters.getSublevelsByLevel()[level];
            SitesAndUpdateHistory updatedArrayAndHistory = removeFromSitesArray(sitesByLevel, sublevels, removalSite);
            newLeveledSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
            newLeveledSitesArray.set(level, newTargetLevelSites);
            boolean[] sublevelUpdateHistory = updatedArrayAndHistory.getUpdateHistory();
            int[] sublevelUpdatedPositions = updatedArrayAndHistory.getUpdatedPositions();
            for (int i = 0; i < sublevels.length; i++) {
                if (sublevelUpdateHistory[i]) {
                    updatedResult = removeSiteCost(newLeveledSitesArray.get(sublevels[i]), sublevelUpdatedPositions[i], minimumPositionsByLevelAndOrigin[sublevels[i]], searchParameters.getMinimumCasesByLevel()[sublevels[i]], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
                    double levelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[sublevels[i]];
                    newCostByLevel[sublevels[i]] = levelCost;
                    newMinimumPositionsByLevelAndOrigin[sublevels[i]] = updatedResult.getPositions();
                }
            }
            totalCost = ArrayOperations.sumDoubleArray(newCostByLevel);
        } else {
            newLeveledSitesArray = new ArrayList<>(sitesByLevel);
            newLeveledSitesArray.set(level, newTargetLevelSites);
            totalCost = cost + newTargetLevelCost - costByLevel[level];
        }

        return new LeveledSiteConfiguration(newLeveledSitesArray, totalCost, newCostByLevel, newMinimumPositionsByLevelAndOrigin);
    }

    //Remove lowest level site that is not used by higher level site
    public LeveledSiteConfiguration removePotentialSite(int level, List<Integer> potentialRemovalSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newTargetLevelSites = new ArrayList<>(sitesByLevel.get(level));
        Random random = new Random();
        Integer removalSite = potentialRemovalSites.get(random.nextInt(potentialRemovalSites.size()));
        int removalPosition = newTargetLevelSites.indexOf(removalSite);
        newTargetLevelSites.remove(removalSite);

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = removeSiteCost(newTargetLevelSites, removalPosition, minimumPositionsByLevelAndOrigin[level], searchParameters.getMinimumCasesByLevel()[level], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newTargetLevelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[level];
        int[] newTargetLevelMinimumPositionsByOrigin = updatedResult.getPositions();

        //Update arrays and adjust for sublevel sites
        List<List<Integer>> newLeveledSitesArray;
        double totalCost;
        double[] newCostByLevel = costByLevel.clone();
        newCostByLevel[level] = newTargetLevelCost;
        int[][] newMinimumPositionsByLevelAndOrigin = minimumPositionsByLevelAndOrigin.clone();
        newMinimumPositionsByLevelAndOrigin[level] = newTargetLevelMinimumPositionsByOrigin;
        if (searchParameters.getSublevelsByLevel()[level].length > 0) {
            int[] sublevels = searchParameters.getSublevelsByLevel()[level];
            SitesAndUpdateHistory updatedArrayAndHistory = removeFromSitesArray(sitesByLevel, sublevels, removalSite);
            newLeveledSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
            newLeveledSitesArray.set(level, newTargetLevelSites);
            boolean[] sublevelUpdateHistory = updatedArrayAndHistory.getUpdateHistory();
            int[] sublevelUpdatedPositions = updatedArrayAndHistory.getUpdatedPositions();
            for (int i = 0; i < sublevels.length; i++) {
                if (sublevelUpdateHistory[i]) {
                    updatedResult = removeSiteCost(newLeveledSitesArray.get(sublevels[i]), sublevelUpdatedPositions[i], minimumPositionsByLevelAndOrigin[sublevels[i]], searchParameters.getMinimumCasesByLevel()[sublevels[i]], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
                    double levelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[sublevels[i]];
                    newCostByLevel[sublevels[i]] = levelCost;
                    newMinimumPositionsByLevelAndOrigin[sublevels[i]] = updatedResult.getPositions();
                }
            }
            totalCost = ArrayOperations.sumDoubleArray(newCostByLevel);
        } else {
            newLeveledSitesArray = new ArrayList<>(sitesByLevel);
            newLeveledSitesArray.set(level, newTargetLevelSites);
            totalCost = cost + newTargetLevelCost - costByLevel[level];
        }

        return new LeveledSiteConfiguration(newLeveledSitesArray, totalCost, newCostByLevel, newMinimumPositionsByLevelAndOrigin);
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

    public List<Integer> getSites() {
        return sites;
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

    public int getLevelSitesCount(int level) {
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