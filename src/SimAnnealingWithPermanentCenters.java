import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SimAnnealingWithPermanentCenters extends SimAnnealingSearch{

    public SimAnnealingWithPermanentCenters() {
        //Simulated annealing configuration
        initialTemp = 1000000;
        finalTemp = 10;
        coolingRate = 0.9995;
        finalNeighborhoodSize = -1; //Determining finalNeighborhoodSize based on number of centers to optimize if -1
        finalNeighborhoodSizeIteration = 2000;

        //Multithreading configuration
        int threadCount = 6;
        executor = Executors.newFixedThreadPool(threadCount);

        //File locations
        String censusFileLocation = "M:\\Optimization Project\\alberta2016_origins.csv";
        String permanentGraphLocation = censusFileLocation.replace("_origins.csv", "_permanent_graph.csv");
        String potentialGraphLocation = censusFileLocation.replace("_origins.csv", "_potential_graph.csv");
        String azimuthLocation = censusFileLocation.replace("_origins.csv", "_potential_azimuth.csv");
        String haversineLocation = censusFileLocation.replace("_origins.csv", "_potential_haversine.csv");

        //Search space parameters
        double[] minimumCasesByLevel = {(double) 10000, (double) 1000000, (double) 2000000};
        double[] servicedProportionByLevel = {0.7, 0.2, 0.1};
        int[] minimumNewCenterCountByLevel = {2, 1, 1};
        int[] maximumNewCenterCountByLevel = {2, 5, 5};
        List<List<Integer>> permanentCentersByLevel = new ArrayList<>();
        permanentCentersByLevel.add(Arrays.asList(0, 1, 2, 3)); //level 0
        permanentCentersByLevel.add(Arrays.asList()); //level 1
        permanentCentersByLevel.add(Arrays.asList()); //level 2
        List<List<Integer>> levelSequences = new ArrayList<>();
        levelSequences.add(Arrays.asList(0, 1));
        levelSequences.add(Arrays.asList(0, 2));
        searchParameters = new SearchSpace(minimumNewCenterCountByLevel, maximumNewCenterCountByLevel, permanentCentersByLevel,
                minimumCasesByLevel, servicedProportionByLevel, levelSequences, 6,
                censusFileLocation, permanentGraphLocation, potentialGraphLocation, azimuthLocation, haversineLocation, 6, executor);
    }

    public static void main(String[] args) throws InterruptedException {
        new SimAnnealingWithPermanentCenters();
        System.out.println("Starting optimization algorithm"); //development only
        //This can be multithreaded with each thread working on a different number n.
        LeveledSiteConfigurationForPermanentCenters minimumSolution = null;
        //development start
        long runtime = 0;
        for (int i = 0; i < 20; i++) {//dev
            long startTime = System.currentTimeMillis();
            LeveledSiteConfigurationForPermanentCenters solution = leveledOptimizeCenters(searchParameters.getMinNewCentersByLevel(), searchParameters.getMaxNewCentersByLevel(), 6);
            System.out.println("Iteration cost is " + solution.getCost() + " on centers " + solution.getSitesByLevel());
            if (minimumSolution == null) {
                minimumSolution = solution;
            } else {
                if (solution.getCost() < minimumSolution.getCost()) {
                    minimumSolution = solution;
                }
            }
            long endTime = System.currentTimeMillis();
            runtime += (endTime - startTime);
        }
        LeveledSiteConfigurationForPermanentCenters.DecomposedLeveledSites decomposedSites = minimumSolution.decomposeSites(searchParameters.getPermanentCentersCountByLevel(), searchParameters.getPotentialSitesCount());
        System.out.println("Minimum cost " + minimumSolution.getCost() + " at permanent sites " + decomposedSites.getPermanentSitesByLevel() + " and expanded permanent sites " + decomposedSites.getExpandedSitesByLevel() + " and new sites " + decomposedSites.getNewSitesByLevel() + ".");
        executor.shutdown();
        System.out.println("Total runtime " + (runtime / (long) 1000) + " seconds");
        return;
    }

    //Multithreading variant of leveledOptimizeCenters
    public static LeveledSiteConfigurationForPermanentCenters leveledOptimizeCenters(int[] minimumNewCenterCountByLevel, int[] maximumNewCenterCountByLevel, int taskCount) throws InterruptedException {
        long timer = System.currentTimeMillis(); // development only
        Random random = new Random();

        //Overriding finalNeighborhoodSize locally for multithreading based on number of centers to optimize if -1 chosen
        List<Integer> allPotentialSites = IntStream.range(0, searchParameters.getPotentialSitesCount()).boxed().collect(Collectors.toList());
        int[] localFinalNeighborhoodSizeByLevel = new int[searchParameters.getCenterLevels()];
        for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
            localFinalNeighborhoodSizeByLevel[i] = SimAnnealingNeighbor.getFinalNeighborhoodSize(searchParameters.getPotentialSitesCount(), maximumNewCenterCountByLevel[i] + searchParameters.getPermanentCentersCountByLevel()[i], finalNeighborhoodSize);
        }

        //Acceptance probability when comparing new target level cost to total cost to compute other level costs for new total to total cost comparison
        double targetLevelThresholdProbability = targetLevelThresholdProbability();

        //Create initial configuration
        LeveledSiteConfigurationForPermanentCenters currentSiteConfiguration = new LeveledSiteConfigurationForPermanentCenters(allPotentialSites, searchParameters, taskCount, executor);
        double currentCost = currentSiteConfiguration.getCost();

        System.out.println("Initial cost " + currentCost + " with details " + Arrays.toString(currentSiteConfiguration.getCostByLevel()) + " at sites " + currentSiteConfiguration.getSitesByLevel()); //Initial cost from random placement.

        //Main simulated annealing algorithm
        double temp = initialTemp;
        int simAnnealingIteration = 0;
        simAnnealing: while (temp > finalTemp) {
            simAnnealingIteration += 1;

            //Try moving each cancer center once for every cycle
            for (int level = 0; level < searchParameters.getCenterLevels(); level++) {
                int currentCenterCount = currentSiteConfiguration.getSitesCount(level);
                int neighborhoodSize = 0;
                for (int position = searchParameters.getPermanentCentersCountByLevel()[level]; position < currentCenterCount; ++position) {
                    List<Integer> restrictedShiftSites = currentSiteConfiguration.getRestrictedShiftableSuperlevelSites(level, searchParameters.getCenterLevels(), position,
                            searchParameters.getPermanentCentersByLevel(), searchParameters.getSuperlevelsByLevel(), searchParameters.getSublevelsByLevel(), searchParameters.getMaxNewCentersByLevel(), searchParameters.getPermanentCentersCountByLevel()); //restrictedShiftSites are null if there are no restrictions
                    if ((Math.random() < 0.5 || searchParameters.getSuperlevelsByLevel()[level].length == 0 ) && restrictedShiftSites == null) { //try to shift to a neighbor, cannot have restriction while shifting (OK to add new site to superlevels)
                        if (neighborhoodSize == 0) {
                            neighborhoodSize = SimAnnealingNeighbor.getNeighborhoodSize(currentCenterCount, searchParameters.getPotentialSitesCount(), localFinalNeighborhoodSizeByLevel[level], simAnnealingIteration, finalNeighborhoodSizeIteration);
                        }
                        if (searchParameters.getSuperlevelsByLevel()[level].length == 0 && searchParameters.getSublevelsByLevel()[level].length == 0) {
                            currentSiteConfiguration.tryShiftToNeighborWithoutLevelRelations(level, position, neighborhoodSize, searchParameters, temp, taskCount, executor);
                        } else {
                            currentSiteConfiguration.tryShiftToNeighbor(level, position, neighborhoodSize, searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                        }
                    } else if (restrictedShiftSites != null && restrictedShiftSites.size() > 0) { //try shift site to an unused superlevel site, some superlevel site configuration is restricting shift (shifting from permanent site)
                        currentSiteConfiguration.tryShiftSite(level, position, pickRandomSite(restrictedShiftSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                    } else if (restrictedShiftSites == null) { //no restrictions in shifting to superlevel site
                        List<Integer> unusedSuperlevelSites = currentSiteConfiguration.getRandomSuperlevelUnusedSites(level, searchParameters.getSuperlevelsByLevel()[level]);
                        if (unusedSuperlevelSites.size() > 0) { //available superlevel sites to which to shift
                            currentSiteConfiguration.tryShiftSite(level, position, pickRandomSite(unusedSuperlevelSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                        } else { //no available superlevel sites to which to shift
                            boolean allSuperlevelsSubmaximal = true;
                            for (int superlevel : searchParameters.getSuperlevelsByLevel()[level]) {
                                if (currentSiteConfiguration.getSites(superlevel).size() == searchParameters.getMaxNewCentersByLevel()[superlevel] + searchParameters.getPermanentCentersCountByLevel()[superlevel]) {
                                    allSuperlevelsSubmaximal = false;
                                }
                            }
                            if (allSuperlevelsSubmaximal) {
                                if (neighborhoodSize == 0) {
                                    neighborhoodSize = SimAnnealingNeighbor.getNeighborhoodSize(currentCenterCount, searchParameters.getPotentialSitesCount(), localFinalNeighborhoodSizeByLevel[level], simAnnealingIteration, finalNeighborhoodSizeIteration);
                                }
                                currentSiteConfiguration.tryShiftToNeighbor(level, position, neighborhoodSize, searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                            }
                        }
                    }
                }
            }

            if (Math.random() < 1) {
                //Try adding or removing one of current sites for each level
                for (int level = 0; level < searchParameters.getCenterLevels(); level++) {
                    int currentCenterCount = currentSiteConfiguration.getSitesCount(level);
                    double adjustmentType = Math.random();
                    if ((adjustmentType < 0.5 || currentCenterCount == minimumNewCenterCountByLevel[level] + searchParameters.getPermanentCentersCountByLevel()[level]) && currentCenterCount < maximumNewCenterCountByLevel[level] + searchParameters.getPermanentCentersCountByLevel()[level]) { //attempt to add unrestricted site unless number of sites is already maximal
                        if (searchParameters.getSuperlevelsByLevel()[level].length == 0) {
                            currentSiteConfiguration.tryAddSiteWithoutSuperlevels(level, pickRandomAddableSite(currentSiteConfiguration.getSites(level), allPotentialSites, random), searchParameters, temp, taskCount, executor);
                        } else if (adjustmentType < 0.3) {
                            List<Integer> restrictedAddableSuperlevelSites = currentSiteConfiguration.getRestrictedAddableSuperlevelSites(level, searchParameters.getSuperlevelsByLevel()[level], searchParameters.getMaxNewCentersByLevel(), searchParameters.getPermanentCentersCountByLevel());
                            if (restrictedAddableSuperlevelSites == null) { //not maximal, will add random site
                                currentSiteConfiguration.tryAddSite(level, pickRandomAddableSite(currentSiteConfiguration.getSites(level), allPotentialSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                            } else if (restrictedAddableSuperlevelSites.size() > 0) { //there are restrictions on adding sites to some superlevel but an available site exists
                                currentSiteConfiguration.tryAddSite(level, pickRandomSite(restrictedAddableSuperlevelSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                            }
                        } else {
                            List<Integer> restrictedAddableSuperlevelSites = currentSiteConfiguration.getRestrictedAddableSuperlevelSites(level, searchParameters.getSuperlevelsByLevel()[level], searchParameters.getMaxNewCentersByLevel(), searchParameters.getPermanentCentersCountByLevel());
                            if (restrictedAddableSuperlevelSites == null) { //if there are no restrictions on adding sites
                                List<Integer> unusedSuperlevelSites = currentSiteConfiguration.getRandomSuperlevelUnusedSites(level, searchParameters.getSuperlevelsByLevel()[level]);
                                if (unusedSuperlevelSites.size() > 0) { //there is a superlevel site not in current level
                                    currentSiteConfiguration.tryAddSite(level, pickRandomSite(unusedSuperlevelSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                                } else { //all superlevels equal to current level, to add random site
                                    currentSiteConfiguration.tryAddSite(level, pickRandomAddableSite(currentSiteConfiguration.getSites(level), allPotentialSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                                }
                            } else if (restrictedAddableSuperlevelSites.size() > 0) { //there are restrictions on adding sites but an available site exists
                                currentSiteConfiguration.tryAddSite(level, pickRandomSite(restrictedAddableSuperlevelSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                            } //size == 0 implies that there are restricting superlevels and that their intersection is equal to the original sites, i.e. adding a site is not permissible
                        }
                    } else if (currentCenterCount > minimumNewCenterCountByLevel[level] + searchParameters.getPermanentCentersCountByLevel()[level]) { //try to remove site
                        if (searchParameters.getSublevelsByLevel().length == 0) {
                            currentSiteConfiguration.tryRemovePositionWithoutSublevels(level, random.nextInt(currentSiteConfiguration.getSitesCount(level)), searchParameters, temp, taskCount, executor);
                        } else {
                            List<Integer> candidateRemovalSites = currentSiteConfiguration.getCandidateRemovalSites(level, searchParameters.getSublevelsByLevel()[level], minimumNewCenterCountByLevel, searchParameters.getPermanentCentersCountByLevel());
                            if (candidateRemovalSites.size() > 0) {
                                currentSiteConfiguration.tryRemoveSite(level, pickRandomSite(candidateRemovalSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                            }
                        }
                    }
                }
            }

            temp *= coolingRate;

            long elapsedTime = System.currentTimeMillis() - timer; //development only
            if (elapsedTime > updateFrequency) { //development only
                System.out.println("Temperature is now " + temp + " for iteration " + simAnnealingIteration); // development only
                System.out.println("The current cost is " + currentSiteConfiguration.getCost() + " with cost by level " + Arrays.toString(currentSiteConfiguration.getCostByLevel()) + " at positions " + currentSiteConfiguration.getSitesByLevel()); // development only
                System.out.println("Garbage collection count = " + DevelopmentUtils.getGarbageCollectionCycles() + " taking " + DevelopmentUtils.getGarbageCollectionTime() + "ms"); //garbage time
                timer = System.currentTimeMillis(); // development only
            } // development only

            //For multithreading to allow interruption after sufficient search is completed.
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        return currentSiteConfiguration;
    }

    public static Integer pickRandomSite(List<Integer> potentialSites, Random random) {
        return potentialSites.get(random.nextInt(potentialSites.size()));
    }

    //Checks to ensure site does not already exist on current level
    public static Integer pickRandomAddableSite(List<Integer> currentSites, List<Integer> potentialSites, Random random) {
        int attemptCount = 0;
        while (true) {
            Integer potentialSite = potentialSites.get(random.nextInt(potentialSites.size()));
            if (currentSites.contains(potentialSite)) {
                attemptCount += 1;
                if (attemptCount > 10) {
                    List<Integer> shortenedPotentialSites = new ArrayList<>(potentialSites);
                    shortenedPotentialSites.removeAll(currentSites);
                    return shortenedPotentialSites.get(random.nextInt(shortenedPotentialSites.size()));
                }
            } else {
                return potentialSite;
            }
        }
    }
}