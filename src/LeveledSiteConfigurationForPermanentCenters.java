import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LeveledSiteConfigurationForPermanentCenters extends SiteConfigurationForPermanentCenters {
    protected List<List<Integer>> higherLevelSitesArray; //array containing lists of higher level sites
    protected Set<Integer> allHigherLevelSites;
    protected double[] higherLevelCosts; //cost for each higher level
    protected int[][] higherLevelMinimumPositionsByOrigin; //analogue of minimumPositionsByOrigin for each higher level

    //Developmental leveled configuration
    protected List<List<Integer>> sitesByLevel;
    protected double[] costByLevel;
    protected int[][] minimumPositionsByLevelAndOrigin;

    public LeveledSiteConfigurationForPermanentCenters(List<Integer> sites, double cost, int[] minimumPositionsByOrigin, List<List<Integer>> higherLevelSitesArray, Set<Integer> allHigherLevelSites, double[] higherLevelCosts, int[][] higherLevelMinimumPositionsByOrigin) {
        super(sites, cost, minimumPositionsByOrigin);
        this.higherLevelSitesArray = higherLevelSitesArray;
        this.allHigherLevelSites = allHigherLevelSites;
        this.higherLevelCosts = higherLevelCosts;
        this.higherLevelMinimumPositionsByOrigin = higherLevelMinimumPositionsByOrigin;
    }

    public LeveledSiteConfigurationForPermanentCenters(int minimumCenterCount, int maximumCenterCount, List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Create random list of current cancer center positions and list of remaining potential positions.
        Random random = new Random();
        sites = new ArrayList<>(pickNRandomFromList(potentialSites, random.nextInt(maximumCenterCount - minimumCenterCount + 1) + minimumCenterCount, random));

        //Find subset of currentSites for higher level services
        higherLevelSitesArray = new ArrayList<>();
        allHigherLevelSites = new HashSet<>();
        for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
            List<Integer> initialHigherLevelSites = sites.stream().limit(1 + random.nextInt(sites.size())).collect(Collectors.toList());
            higherLevelSitesArray.add(initialHigherLevelSites);
            allHigherLevelSites.addAll(initialHigherLevelSites);
            Collections.shuffle(sites);
        }

        //Add permanent sites
        sites = Stream.concat(IntStream.range(searchParameters.getPotentialSitesCount(), searchParameters.getPotentialSitesCount() + searchParameters.getPermanentCentersCount()).boxed(), sites.stream()).toList();
        List<List<Integer>> incrementedPermanentHLCenters = ArrayOperations.incrementArray(searchParameters.getPermanentHLCenters(), searchParameters.getPotentialSitesCount());
        for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
            higherLevelSitesArray.set(i, Stream.concat(incrementedPermanentHLCenters.get(i).stream(), higherLevelSitesArray.get(i).stream()).toList());
            allHigherLevelSites.addAll(incrementedPermanentHLCenters.get(i));
        }

        //Compute initial cost and list of the closest of current positions for each originating population center
        ConfigurationCostAndPositions initialCostAndPositions = initialCost(sites, searchParameters.getPermanentCentersCount(), searchParameters.getMinPermanentPositionByOrigin(), searchParameters.getMinPermanentCostByOrigin(),
                searchParameters.getMinimumCasesByLevel()[0], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(),
                taskCount, searchParameters.getPartitionedOrigins(), executor);
        cost = initialCostAndPositions.getCost() * searchParameters.getServicedProportionByLevel()[0];
        minimumPositionsByOrigin = initialCostAndPositions.getPositions();

        //Adjust cost for higher level positions
        higherLevelCosts = new double[searchParameters.getCenterLevels()];
        higherLevelMinimumPositionsByOrigin = new int[searchParameters.getCenterLevels()][searchParameters.getOriginCount()];
        for (int i = 0; i < searchParameters.getCenterLevels(); ++i) {
            ConfigurationCostAndPositions initialHigherLevelCostAndPositions = initialCost(i, higherLevelSitesArray.get(i), searchParameters.getPermanentHLCentersCount(), searchParameters.getMinPermanentHLPositionByOrigin(),
                    searchParameters.getMinPermanentHLCostByOrigin(), searchParameters.getMinimumCasesByLevel()[i + 1], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
            double initialHigherLevelCost = initialHigherLevelCostAndPositions.getCost() * searchParameters.getServicedProportionByLevel()[i + 1];
            cost += initialHigherLevelCost;
            higherLevelCosts[i] = initialHigherLevelCost;
            higherLevelMinimumPositionsByOrigin[i] = initialHigherLevelCostAndPositions.getPositions();
        }
    }

    //Multithreaded variant
    public LeveledSiteConfigurationForPermanentCenters shiftLowestLevelSite(Integer positionToShift, int neighborhoodSize, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Get shifted sites
        Integer siteToShift = sites.get(positionToShift);
        Integer newSite = SimAnnealingNeighbor.getUnusedNeighbor(sites, siteToShift, neighborhoodSize, searchParameters.getSortedNeighbors());
        List<Integer> newSites = new ArrayList<>(sites);
        newSites.set(positionToShift, newSite);
        //Compute cost of new positions and update list of the closest of current positions for each population center
        ConfigurationCostAndPositions updatedResult = shiftSiteCost(newSites, positionToShift, newSite, minimumPositionsByOrigin,
                searchParameters.getPermanentCentersCount(), searchParameters.getMinPermanentPositionByOrigin(), searchParameters.getMinPermanentCostByOrigin(),
                searchParameters.getMinimumCases(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[0];
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();
        //Update higher level sites array
        List<List<Integer>> newHigherLevelSitesArray = new ArrayList<>(higherLevelSitesArray);
        Set<Integer> newAllHigherLevelSites = new HashSet<>(allHigherLevelSites);
        boolean higherLevelSitesChanged = allHigherLevelSites.contains(siteToShift);
        double[] newHigherLevelCosts = higherLevelCosts.clone();
        int[][] newHigherLevelMinimumPositionsByOrigin = higherLevelMinimumPositionsByOrigin.clone();
        if (higherLevelSitesChanged) {
            SitesAndUpdateHistory updatedArrayAndHistory = updateSitesArray(higherLevelSitesArray, siteToShift, newSite);
            newHigherLevelSitesArray = updatedArrayAndHistory.getUpdatedSitesArray();
            newAllHigherLevelSites.remove(siteToShift);
            newAllHigherLevelSites.add(newSite);
            boolean[] updateHistory = updatedArrayAndHistory.getUpdateHistory();
            int[] updatedPositions = updatedArrayAndHistory.getUpdatedPositions();
            for (int j = 0; j < searchParameters.getCenterLevels(); j++) {
                if (updateHistory[j]) {
                    updatedResult = SiteConfigurationForPermanentCenters.shiftSiteCost(j, newHigherLevelSitesArray.get(j), updatedPositions[j], newSite, higherLevelMinimumPositionsByOrigin[j],
                            searchParameters.getPermanentHLCentersCount(), searchParameters.getMinPermanentHLPositionByOrigin(), searchParameters.getMinPermanentHLCostByOrigin(),
                            searchParameters.getMinimumCasesByLevel()[j + 1], searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
                    double levelCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[j + 1];
                    newCost += levelCost;
                    newHigherLevelCosts[j] = levelCost;
                    newHigherLevelMinimumPositionsByOrigin[j] = updatedResult.getPositions();
                } else {
                    newCost += higherLevelCosts[j];
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
    public LeveledSiteConfigurationForPermanentCenters addLowestLevelSite(List<Integer> potentialSites, SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Add lowest level site
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> unusedSites = new ArrayList<>(potentialSites);
        unusedSites.removeAll(sites);
        Random random = new Random();
        newSites.add(unusedSites.get(random.nextInt(unusedSites.size())));

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = addSiteCost(newSites, minimumPositionsByOrigin, searchParameters.getMinimumCases(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[0];
        for (double levelCost : higherLevelCosts) { //higher level costs do not change with addition of a lowest level center
            newCost += levelCost;
        }
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new LeveledSiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin, higherLevelSitesArray, allHigherLevelSites, higherLevelCosts, higherLevelMinimumPositionsByOrigin);
    }

    //Variant with multithreading of previous removeLowestLevelSite
    public LeveledSiteConfigurationForPermanentCenters removeLowestLevelSite(SearchSpace searchParameters, int taskCount, ExecutorService executor) {
        //Remove one of the lowest level sites
        List<Integer> newSites = new ArrayList<>(sites);
        List<Integer> candidateRemovalSites = new ArrayList<>(newSites);
        candidateRemovalSites.removeAll(allHigherLevelSites);
        candidateRemovalSites.removeIf(i -> i >= searchParameters.getPotentialSitesCount());
        Random random = new Random();
        Integer removalSite = candidateRemovalSites.get(random.nextInt(candidateRemovalSites.size()));
        int removalPosition = newSites.indexOf(removalSite);
        newSites.remove(removalSite);

        //Compute new parameters
        ConfigurationCostAndPositions updatedResult = SiteConfigurationForPermanentCenters.removeSiteCost(newSites, removalPosition, minimumPositionsByOrigin,
                searchParameters.getPermanentCentersCount(), searchParameters.getMinPermanentPositionByOrigin(), searchParameters.getMinPermanentCostByOrigin(),
                searchParameters.getMinimumCases(), searchParameters.getOriginCount(), searchParameters.getCaseCountByOrigin(), searchParameters.getGraphArray(), taskCount, searchParameters.getPartitionedOrigins(), executor);
        double newCost = updatedResult.getCost() * searchParameters.getServicedProportionByLevel()[0];
        for (double levelCost : higherLevelCosts) { //higher level costs do not change by requirement
            newCost += levelCost;
        }
        int[] newMinimumPositionsByOrigin = updatedResult.getPositions();

        return new LeveledSiteConfigurationForPermanentCenters(newSites, newCost, newMinimumPositionsByOrigin, higherLevelSitesArray, allHigherLevelSites, higherLevelCosts, higherLevelMinimumPositionsByOrigin);
    }

    //Update LeveledSiteConfiguration at a specified level with new configuration and costs
    public void updateHigherLevelConfiguration(int level, SiteConfigurationForPermanentCenters newThisLevelSiteConfiguration, double currentThisLevelCost, double newThisLevelCost) {
        cost = cost + newThisLevelCost - currentThisLevelCost;
        higherLevelSitesArray.set(level, newThisLevelSiteConfiguration.getSites());
        higherLevelCosts[level] = newThisLevelCost;
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
    public static SitesAndUpdateHistory updateSitesArray(List<List<Integer>> sitesArray, Integer removedSite, Integer newSite) {
        List<List<Integer>> updatedSitesArray = new ArrayList<>();
        boolean[] updateHistory = new boolean[sitesArray.size()];
        int[] updatedPositions = new int[sitesArray.size()];
        Arrays.fill(updatedPositions, -1);
        for (int j = 0; j < sitesArray.size(); j++) {
            List<Integer> updatedSites = new ArrayList<>(sitesArray.get(j));
            for (int i = 0; i < updatedSites.size(); i++) {
                if (updatedSites.get(i) == removedSite) {
                    updatedSites.set(i, newSite);
                    updateHistory[j] = true;
                    updatedPositions[j] = i;
                    break;
                }
            }
            updatedSitesArray.add(updatedSites);
        }
        return new SitesAndUpdateHistory(updatedSitesArray, updateHistory, updatedPositions);
    }

    public List<List<Integer>> getHigherLevelSitesArray() {
        return higherLevelSitesArray;
    }

    public Set<Integer> getAllHigherLevelSites() {
        return allHigherLevelSites;
    }

    public double[] getHigherLevelCosts() {
        return higherLevelCosts;
    }

    public int[][] getHigherLevelMinimumPositionsByOrigin() {
        return higherLevelMinimumPositionsByOrigin;
    }
}