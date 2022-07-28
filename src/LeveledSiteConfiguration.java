import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class LeveledSiteConfiguration extends SiteConfiguration {
    protected List<List<Integer>> higherLevelSitesArray; //array containing lists of higher level sites
    protected Set<Integer> allHigherLevelSites;
    protected List<Double> higherLevelCosts; //cost for each higher level
    protected int[][] higherLevelMinimumPositionsByOrigin; //analogue of minimumPositionsByOrigin for each higher level

    public LeveledSiteConfiguration(List<Integer> sites, double cost, int[] minimumPositionsByOrigin, List<List<Integer>> higherLevelSitesArray, Set<Integer> allHigherLevelSites, List<Double> higherLevelCosts, int[][] higherLevelMinimumPositionsByOrigin) {
        super(sites, cost, minimumPositionsByOrigin);
        this.higherLevelSitesArray = higherLevelSitesArray;
        this.allHigherLevelSites = allHigherLevelSites;
        this.higherLevelCosts = higherLevelCosts;
        this.higherLevelMinimumPositionsByOrigin = higherLevelMinimumPositionsByOrigin;
    }

    //Generates initial configuration
    public LeveledSiteConfiguration(int minimumCenterCount, int maximumCenterCount, List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
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

        //Compute initial cost and list of the closest of current positions for each originating population center
        ConfigurationCostAndPositions initialCostAndPositions = initialCost(sites, searchParameters.getMinimumCasesByLevel().get(0), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        cost = initialCostAndPositions.getCost() * searchParameters.getServicedProportionByLevel().get(0);
        minimumPositionsByOrigin = initialCostAndPositions.getPositions();

        //Adjust cost for higher level positions
        higherLevelCosts = new ArrayList<>();
        higherLevelMinimumPositionsByOrigin = new int[searchParameters.getHigherCenterLevels()][searchParameters.getOriginCount()];
        for (int i = 0; i < searchParameters.getHigherCenterLevels(); ++i) {
            ConfigurationCostAndPositions initialHigherLevelCostAndPositions = initialCost(higherLevelSitesArray.get(i), searchParameters.getMinimumCasesByLevel().get(i + 1), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
            double initialHigherLevelCost = initialHigherLevelCostAndPositions.getCost() * searchParameters.getServicedProportionByLevel().get(i + 1);
            cost += initialHigherLevelCost;
            higherLevelCosts.add(initialHigherLevelCost);
            higherLevelMinimumPositionsByOrigin[i] = initialHigherLevelCostAndPositions.getPositions();
        }
    }

    //Get new leveled site configuration by shifting one of the lowest level sites
    //Multithreaded variant
    public LeveledSiteConfiguration shiftLowestLevelSite(int positionToShift, int neighborhoodSize, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Get shifted sites
        Integer siteToShift = sites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(sites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newSites = new ArrayList<>(sites);
        newSites.set(positionToShift, newSite);
        //Compute cost of new positions and update list of the closest of current positions for each population center
        ConfigurationCostAndPositions updatedResult = shiftSiteCost(newSites, positionToShift, newSite, minimumPositionsByOrigin, searchParameters.getMinimumCases(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel().get(0);
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();
        //Update higher level sites array
        List<List<Integer>> newHigherLevelSitesArray = new ArrayList<>(higherLevelSitesArray);
        Set<Integer> newAllHigherLevelSites = new HashSet<>(allHigherLevelSites);
        Boolean higherLevelSitesChanged = allHigherLevelSites.contains(siteToShift);
        List<Double> newHigherLevelCosts = new ArrayList<>(higherLevelCosts);
        int[][] newHigherLevelMinimumPositionsByOrigin = higherLevelMinimumPositionsByOrigin.clone();
        if (higherLevelSitesChanged) {
            List<Object> updatedArrayAndHistory = updateSitesArray(higherLevelSitesArray, siteToShift, newSite);
            newHigherLevelSitesArray = (List<List<Integer>>) updatedArrayAndHistory.get(0);
            newAllHigherLevelSites.remove(siteToShift);
            newAllHigherLevelSites.add(newSite);
            List<Boolean> updateHistory = (List<Boolean>) updatedArrayAndHistory.get(1);
            List<Integer> updatedPositions = (List<Integer>) updatedArrayAndHistory.get(2);
            for (int j = 0; j < searchParameters.getHigherCenterLevels(); j++) {
                if (updateHistory.get(j)) {
                    updatedResult = shiftSiteCost(newHigherLevelSitesArray.get(j), updatedPositions.get(j), newSite, higherLevelMinimumPositionsByOrigin[j], searchParameters.getMinimumCasesByLevel().get(j + 1), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
                    double levelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel().get(j + 1);
                    newCost += levelCost;
                    newHigherLevelCosts.set(j, levelCost);
                    newHigherLevelMinimumPositionsByOrigin[j] = updatedResult.getPositions();
                } else {
                    newCost += higherLevelCosts.get(j);
                }
            }
        } else {
            for (double levelCost : higherLevelCosts) {
                newCost += levelCost;
            }
        }
        return new LeveledSiteConfiguration(newSites, newCost, newMinimumPositionsByOrigin, newHigherLevelSitesArray, newAllHigherLevelSites, newHigherLevelCosts, newHigherLevelMinimumPositionsByOrigin);
    }

    //Add one of the base level site
    public LeveledSiteConfiguration addLowestLevelSite(List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Add lowest level site
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        newSites.add(unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = addSiteCost(newSites, minimumPositionsByOrigin, searchParameters.getMinimumCases(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel().get(0);
        for (double levelCost : higherLevelCosts) { //higher level costs do not change with addition of a lowest level center
            newCost += levelCost;
        }
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new LeveledSiteConfiguration(newSites, newCost, newMinimumPositionsByOrigin, higherLevelSitesArray, allHigherLevelSites, higherLevelCosts, higherLevelMinimumPositionsByOrigin);
    }

    //Remove lowest level site that is not used by higher level site
    public LeveledSiteConfiguration removeLowestLevelSite(SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> candidateRemovalSites = new ArrayList<>(newSites);
        candidateRemovalSites.removeAll(allHigherLevelSites);
        Random random = new Random();
        Integer removalSite = candidateRemovalSites.get(random.nextInt(candidateRemovalSites.size()));
        int removalPosition = newSites.indexOf(removalSite);
        newSites.remove(removalSite);

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = removeSiteCost(newSites, removalPosition, minimumPositionsByOrigin, searchParameters.getMinimumCases(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = (double) updatedResult.getCost() * searchParameters.getServicedProportionByLevel().get(0);
        for (double levelCost : higherLevelCosts) { //higher level costs do not change by requirement
            newCost += levelCost;
        }
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new LeveledSiteConfiguration(newSites, newCost, newMinimumPositionsByOrigin, higherLevelSitesArray, allHigherLevelSites, higherLevelCosts, higherLevelMinimumPositionsByOrigin);
    }


    //Update LeveledSiteConfiguration at a specified level with new configuration and costs
    public void updateHigherLevelConfiguration(int level, SiteConfiguration newThisLevelSiteConfiguration, double currentThisLevelCost, double newThisLevelCost) {
        cost = cost + newThisLevelCost - currentThisLevelCost;
        higherLevelSitesArray.set(level, newThisLevelSiteConfiguration.getSites());
        higherLevelCosts.set(level, newThisLevelCost);
        higherLevelMinimumPositionsByOrigin[level] = newThisLevelSiteConfiguration.getMinimumPositionsByOrigin();
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

    public List<Integer> getSites() {
        return sites;
    }

    public double getCost() {
        return cost;
    }

    public int[] getMinimumPositionsByOrigin() {
        return minimumPositionsByOrigin;
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

    public int[][] getHigherLevelMinimumPositionsByOrigin() {
        return higherLevelMinimumPositionsByOrigin;
    }

}