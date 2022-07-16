import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LeveledSiteConfigurationForPermanentCenters extends SiteConfigurationForPermanentCenters {
    protected List<List<Integer>> higherLevelSitesArray; //array containing lists of higher level sites
    protected Set<Integer> allHigherLevelSites;
    protected List<Double> higherLevelCosts; //cost for each higher level
    protected List<List<Integer>> higherLevelMinimumPositionsByOrigin; //analogue of minimumPositionsByOrigin for each higher level

    public LeveledSiteConfigurationForPermanentCenters(List<Integer> sites, double cost, List<Integer> minimumPositionsByOrigin, List<List<Integer>> higherLevelSitesArray, Set<Integer> allHigherLevelSites, List<Double> higherLevelCosts, List<List<Integer>> higherLevelMinimumPositionsByOrigin) {
        super(sites, cost, minimumPositionsByOrigin);
        this.higherLevelSitesArray = higherLevelSitesArray;
        this.allHigherLevelSites = allHigherLevelSites;
        this.higherLevelCosts = higherLevelCosts;
        this.higherLevelMinimumPositionsByOrigin = higherLevelMinimumPositionsByOrigin;
    }

    public LeveledSiteConfigurationForPermanentCenters(Integer minimumCenterCount, Integer maximumCenterCount, List<Integer> potentialSites, SearchSpace searchParameters, Integer taskCount, ExecutorService executor) {
        //Create random list of current cancer center positions and list of remaining potential positions.
        Random random = new Random();
        sites = new ArrayList<>(pickNRandomFromList(potentialSites, random.nextInt(maximumCenterCount - minimumCenterCount + 1) + minimumCenterCount, random));

        //Find subset of currentSites for higher level services
        higherLevelSitesArray = new ArrayList<>();
        allHigherLevelSites = new HashSet<>();
        for (int i = 0; i < searchParameters.getHigherCenterLevels(); i++) {
            List<Integer> initialHigherLevelSites = sites.stream().limit(1 + random.nextInt(sites.size())).collect(Collectors.toList());
            higherLevelSitesArray.add(initialHigherLevelSites);
            allHigherLevelSites.addAll(initialHigherLevelSites);
            Collections.shuffle(sites);
        }

        //Add permanent sites
        sites = Stream.concat(IntStream.range(searchParameters.getPotentialSitesCount(), searchParameters.getPotentialSitesCount() + searchParameters.getPermanentCentersCount()).boxed(), sites.stream()).toList();
        List<List<Integer>> incrementedPermanentHLCenters = ArrayOperations.incrementArray(searchParameters.getPermanentHLCenters(), searchParameters.getPotentialSitesCount());
        for (int i = 0; i < searchParameters.getHigherCenterLevels(); i++) {
            higherLevelSitesArray.set(i, Stream.concat(incrementedPermanentHLCenters.get(i).stream(), higherLevelSitesArray.get(i).stream()).toList());
            allHigherLevelSites.addAll(incrementedPermanentHLCenters.get(i));
        }

        //Compute initial cost and list of the closest of current positions for each originating population center
        List<Object> initialCostAndPositions = initialCost(sites, searchParameters.getPermanentCentersCount(), searchParameters.getMinPermanentPositionByOrigin(), searchParameters.getMinPermanentCostByOrigin(),
                searchParameters.getMinimumCasesByLevel().get(0), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(),
                taskCount, searchParameters.getPartitionedOrigins(), executor);
        cost = (double) initialCostAndPositions.get(0) * searchParameters.getServicedProportionByLevel().get(0);
        minimumPositionsByOrigin = (List<Integer>) initialCostAndPositions.get(1);

        //Adjust cost for higher level positions
        higherLevelCosts = new ArrayList<>();
        higherLevelMinimumPositionsByOrigin = new ArrayList<>();
        for (int i = 0; i < searchParameters.getHigherCenterLevels(); ++i) {
            List<Object> initialHigherLevelCostAndPositions = initialCost(i, higherLevelSitesArray.get(i), searchParameters.getPermanentHLCentersCount(), searchParameters.getMinPermanentHLPositionByOrigin(),
                    searchParameters.getMinPermanentHLCostByOrigin(), searchParameters.getMinimumCasesByLevel().get(i + 1), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
            double initialHigherLevelCost = (double) initialHigherLevelCostAndPositions.get(0) * searchParameters.getServicedProportionByLevel().get(i + 1);
            cost += initialHigherLevelCost;
            higherLevelCosts.add(initialHigherLevelCost);
            higherLevelMinimumPositionsByOrigin.add((List<Integer>) initialHigherLevelCostAndPositions.get(1));
        }
    }

    //Multithreaded variant
    public LeveledSiteConfigurationForPermanentCenters shiftLowestLevelSite(Integer positionToShift, Integer neighborhoodSize, SearchSpace searchParameters, Integer taskCount, ExecutorService executor) {
        //Get shifted sites
        Integer siteToShift = sites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(sites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newSites = new ArrayList<>(sites);
        newSites.set(positionToShift, newSite);
        //Compute cost of new positions and update list of the closest of current positions for each population center
        List<Object> updatedResult = shiftSiteCost(newSites, positionToShift, newSite, minimumPositionsByOrigin,
                searchParameters.getPermanentCentersCount(), searchParameters.getMinPermanentPositionByOrigin(), searchParameters.getMinPermanentCostByOrigin(),
                searchParameters.getMinimumCases(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = (double) updatedResult.get(0) * searchParameters.getServicedProportionByLevel().get(0);
        List<Integer> newMinimumPositionsByOrigin = (List<Integer>) updatedResult.get(1);
        //Update higher level sites array
        List<List<Integer>> newHigherLevelSitesArray = new ArrayList<>(higherLevelSitesArray);
        Set<Integer> newAllHigherLevelSites = new HashSet<>(allHigherLevelSites);
        Boolean higherLevelSitesChanged = allHigherLevelSites.contains(siteToShift);
        List<Double> newHigherLevelCosts = new ArrayList<>(higherLevelCosts);
        List<List<Integer>> newHigherLevelMinimumPositionsByOrigin = new ArrayList<>(higherLevelMinimumPositionsByOrigin);
        if (higherLevelSitesChanged) {
            List<Object> updatedArrayAndHistory = updateSitesArray(higherLevelSitesArray, siteToShift, newSite);
            newHigherLevelSitesArray = (List<List<Integer>>) updatedArrayAndHistory.get(0);
            newAllHigherLevelSites.remove(siteToShift);
            newAllHigherLevelSites.add(newSite);
            List<Boolean> updateHistory = (List<Boolean>) updatedArrayAndHistory.get(1);
            List<Integer> updatedPositions = (List<Integer>) updatedArrayAndHistory.get(2);
            for (int j = 0; j < searchParameters.getHigherCenterLevels(); j++) {
                if (updateHistory.get(j)) {
                    updatedResult = SiteConfigurationForPermanentCenters.shiftSiteCost(j, newHigherLevelSitesArray.get(j), updatedPositions.get(j), newSite, higherLevelMinimumPositionsByOrigin.get(j),
                            searchParameters.getPermanentHLCentersCount(), searchParameters.getMinPermanentHLPositionByOrigin(), searchParameters.getMinPermanentHLCostByOrigin(),
                            searchParameters.getMinimumCasesByLevel().get(j + 1), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
                    double levelCost = (double) updatedResult.get(0) * searchParameters.getServicedProportionByLevel().get(j + 1);
                    newCost += levelCost;
                    newHigherLevelCosts.set(j, levelCost);
                    newHigherLevelMinimumPositionsByOrigin.set(j, (List<Integer>) updatedResult.get(1));
                } else {
                    newCost += higherLevelCosts.get(j);
                }
            }
        } else {
            for (double levelCost : higherLevelCosts) {
                newCost += levelCost;
            }
        }
        return new LeveledSiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin, newHigherLevelSitesArray, newAllHigherLevelSites, newHigherLevelCosts, newHigherLevelMinimumPositionsByOrigin);
    }

    //Unchanged from without permanent centers (only one of three)
    public LeveledSiteConfigurationForPermanentCenters addLowestLevelSite(List<Integer> potentialSites, SearchSpace searchParameters, Integer taskCount, ExecutorService executor) {
        //Add lowest level site
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        newSites.add(unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute new parameters
        List<Object> updatedResult = addSiteCost(newSites, minimumPositionsByOrigin, searchParameters.getMinimumCases(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = (double) updatedResult.get(0) * searchParameters.getServicedProportionByLevel().get(0);
        for (double levelCost : higherLevelCosts) { //higher level costs do not change with addition of a lowest level center
            newCost += levelCost;
        }
        List<Integer> newMinimumPositionsByOrigin = (List<Integer>) updatedResult.get(1);

        return new LeveledSiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin, higherLevelSitesArray, allHigherLevelSites, higherLevelCosts, higherLevelMinimumPositionsByOrigin);
    }

    //Variant with multithreading of previous removeLowestLevelSite
    public LeveledSiteConfigurationForPermanentCenters removeLowestLevelSite(SearchSpace searchParameters, Integer taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> candidateRemovalSites = new ArrayList<>(newSites);
        candidateRemovalSites.removeAll(allHigherLevelSites);
        candidateRemovalSites.removeIf(i -> i >= searchParameters.getPotentialSitesCount());
        Random random = new Random();
        Integer removalSite = candidateRemovalSites.get(random.nextInt(candidateRemovalSites.size()));
        Integer removalPosition = newSites.indexOf(removalSite);
        newSites.remove(removalSite);

        //Compute new parameters
        List<Object> updatedResult = SiteConfigurationForPermanentCenters.removeSiteCost(newSites, removalPosition, minimumPositionsByOrigin,
                searchParameters.getPermanentCentersCount(), searchParameters.getMinPermanentPositionByOrigin(), searchParameters.getMinPermanentCostByOrigin(),
                searchParameters.getMinimumCases(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = (double) updatedResult.get(0) * searchParameters.getServicedProportionByLevel().get(0);
        for (double levelCost : higherLevelCosts) { //higher level costs do not change by requirement
            newCost += levelCost;
        }
        List<Integer> newMinimumPositionsByOrigin = (List<Integer>) updatedResult.get(1);

        return new LeveledSiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin, higherLevelSitesArray, allHigherLevelSites, higherLevelCosts, higherLevelMinimumPositionsByOrigin);
    }

    //Update LeveledSiteConfiguration at a specified level with new configuration and costs
    public void updateHigherLevelConfiguration(Integer level, SiteConfigurationForPermanentCenters newThisLevelSiteConfiguration, double currentThisLevelCost, double newThisLevelCost) {
        cost = cost + newThisLevelCost - currentThisLevelCost;
        higherLevelSitesArray.set(level, newThisLevelSiteConfiguration.getSites());
        higherLevelCosts.set(level, newThisLevelCost);
        higherLevelMinimumPositionsByOrigin.set(level, newThisLevelSiteConfiguration.getMinimumPositionsByOrigin());
        allHigherLevelSites = null;
    }

    //Updates allHigherLevelSites in siteConfiguration using higher level sites array (requires updated higher level sites array but outdated set allHigherLevelSites)
    public void updateAllHigherLevelSites() {
        allHigherLevelSites = new HashSet<>();
        for (List<Integer> higherLevelSites : higherLevelSitesArray) {
            allHigherLevelSites.addAll(higherLevelSites);
        }
    }

    //Update sites array by replacing removedSite with newSite for all sites in the array. Output is updated sites array, updated positions, and history of updates, true for each level that was changed and false if not.
    public static List<Object> updateSitesArray(List<List<Integer>> sitesArray, Integer removedSite, Integer newSite) {
        List<List<Integer>> updatedSitesArray = new ArrayList<>();
        List<Boolean> updateHistory = new ArrayList<>(Collections.nCopies(sitesArray.size(), false));
        List<Integer> updatedPositions = new ArrayList<>(Collections.nCopies(sitesArray.size(), -1));
        for (int j = 0; j < sitesArray.size(); j++) {
            List<Integer> updatedSites = new ArrayList<>(sitesArray.get(j));
            for (int i = 0; i < updatedSites.size(); i++) {
                if (updatedSites.get(i) == removedSite) {
                    updatedSites.set(i, newSite);
                    updateHistory.set(j, true);
                    updatedPositions.set(j, i);
                    break;
                }
            }
            updatedSitesArray.add(updatedSites);
        }
        return Arrays.asList(updatedSitesArray, updateHistory, updatedPositions);
    }

    public List<List<Integer>> getHigherLevelSitesArray() {
        return higherLevelSitesArray;
    }

    public Set<Integer> getAllHigherLevelSites() {
        return allHigherLevelSites;
    }

    public List<Double> getHigherLevelCosts() {
        return higherLevelCosts;
    }

    public List<List<Integer>> getHigherLevelMinimumPositionsByOrigin() {
        return higherLevelMinimumPositionsByOrigin;
    }
}