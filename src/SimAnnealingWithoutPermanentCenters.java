import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

//Shrinking neighborhood optimization
public class SimAnnealingWithoutPermanentCenters extends SimAnnealingSearch{

    public SimAnnealingWithoutPermanentCenters() {
        //Simulated annealing configuration
        this.initialTemp = 1000000;
        this.finalTemp = 1;
        this.coolingRate = 0.9995;
        this.finalNeighborhoodSize = -1; //Determining finalNeighborhoodSize based on number of centers to optimize if -1
        this.finalNeighborhoodSizeIteration = 20000;

        //Multithreading configuration
        int threadCount = 6;
        executor = Executors.newFixedThreadPool(threadCount);

        //File locations
        String censusFileLocation = "M:\\Optimization Project\\alberta2016_origins.csv";
        String graphLocation = censusFileLocation.replace("_origins.csv", "_graph.csv");
        String azimuthLocation = censusFileLocation.replace("_origins.csv", "_azimuth.csv");
        String haversineLocation = censusFileLocation.replace("_origins.csv", "_haversine.csv");

        //Search space parameters
        double[] minimumCasesByLevel = {(double) 10000, (double) 1000000, (double) 2000000};
        double[] servicedProportionByLevel = {1.0, 0.0, 0.0};
        int[] minimumCenterCountByLevel = {2, 0, 1};
        int[] maximumCenterCountByLevel = {6, 6, 6};
        List<List<Integer>> levelSequences = new ArrayList<>();
        levelSequences.add(Arrays.asList(0, 1));
        levelSequences.add(Arrays.asList(0, 2));
        //levelSequences.add(Arrays.asList());
        searchParameters = new SearchSpace(minimumCenterCountByLevel, maximumCenterCountByLevel, minimumCasesByLevel, servicedProportionByLevel, levelSequences, 6,
                censusFileLocation, graphLocation, azimuthLocation, haversineLocation, 6, executor);
    }

    public static void main(String[] args) throws InterruptedException {
        new SimAnnealingWithoutPermanentCenters();
        System.out.println("Sublevels by level are " + Arrays.deepToString(searchParameters.getSublevelsByLevel()) + " and superlevels by level are " + Arrays.deepToString(searchParameters.getSuperlevelsByLevel()));
        System.out.println("Starting optimization algorithm"); //development only
        //This can be multithreaded with each thread working on a different number n.
        double minimumCost = Double.POSITIVE_INFINITY;
        List<List<Integer>> minimumSites = new ArrayList<>();
        SiteConfiguration solutionWithOneLevel = optimizeNCenters(6, 6);
        //development start
        long runtime = 0;
        for (int i = 0; i < 20; i++) {//dev
            long startTime = System.currentTimeMillis();
            LeveledSiteConfiguration solutionWithNCenters = leveledOptimizeCenters(searchParameters.getMinNewCentersByLevel(), searchParameters.getMaxNewCentersByLevel(), 6);
            System.out.println("Final cost is " + solutionWithNCenters.getCost() + " on centers " + solutionWithNCenters.getSitesByLevel());
            long endTime = System.currentTimeMillis();
            runtime += (endTime - startTime);
        }
        System.out.println("Run time on 20 iterations was " + (runtime / 1000) + "s");
        //development end
        LeveledSiteConfiguration solutionWithNCenters = leveledOptimizeCenters(searchParameters.getMinNewCentersByLevel(), searchParameters.getMaxNewCentersByLevel(), 6);
        minimumCost = solutionWithNCenters.getCost();
        minimumSites = solutionWithNCenters.getSitesByLevel();
        executor.shutdown();
        return;
    }

    //Optimize with shrinking
    //Multithreading variant of OptimizeNCenters
    public static SiteConfiguration optimizeNCenters(int centerCount, int taskCount) throws InterruptedException {
        long timer = System.currentTimeMillis(); // development only

        //Overriding finalNeighborhoodSize locally for multithreading based on number of centers to optimize if -1 chosen
        int localFinalNeighborhoodSize;
        if (finalNeighborhoodSize == -1) {
            localFinalNeighborhoodSize = (int) Math.min(Math.ceil(1.5 * searchParameters.getPotentialSitesCount() / centerCount), searchParameters.getPotentialSitesCount() - centerCount); //to account for 1 center
            //localFinalNeighborhoodSize = Math.max(localFinalNeighborhoodSize, 1200); //minimum empirical neighborhood size to not get trapped locally
        } else {
            localFinalNeighborhoodSize = finalNeighborhoodSize;
        }

        //Create initial configuration+
        List<Integer> potentialSites = IntStream.range(0, searchParameters.getPotentialSitesCount()).boxed().collect(Collectors.toList());
        SiteConfiguration currentSiteConfiguration = new SiteConfiguration(centerCount, centerCount, potentialSites, searchParameters, taskCount, executor);
        int currentCenterCount = currentSiteConfiguration.getSites().size();

        System.out.println("Initial cost " + currentSiteConfiguration.getCost() + " at " + currentSiteConfiguration.getSites()); //Initial cost from random placement.

        //Main simulated annealing algorithm
        double temp = initialTemp;
        int simAnnealingIteration = 0;
        while (temp > finalTemp) {
            simAnnealingIteration += 1;
            int neighborhoodSize;
            if (simAnnealingIteration >= finalNeighborhoodSizeIteration) {
                neighborhoodSize = localFinalNeighborhoodSize;
            } else {
                neighborhoodSize = SimAnnealingNeighbor.getNeighborhoodSize(centerCount, searchParameters.getPotentialSitesCount(), localFinalNeighborhoodSize, simAnnealingIteration, finalNeighborhoodSizeIteration);
            }
            //Try moving each cancer center once for every cycle
            for (int i = 0; i < currentCenterCount; ++i ) {
                currentSiteConfiguration.tryShiftToNeighbor(i, neighborhoodSize, searchParameters, temp, taskCount, executor);
            }

            temp *= coolingRate;

            long elapsedTime = System.currentTimeMillis()  - timer; //development only
            if (elapsedTime > updateFrequency) { //development only
                System.out.println("Temperature is now " + temp +" on optimization for " + centerCount + " center(s). Neighborhood size was " + neighborhoodSize + " for iteration " + simAnnealingIteration); // development only
                System.out.println("The current cost is " + currentSiteConfiguration.getCost() + " at positions " + currentSiteConfiguration.getSites()); // development only
                timer = System.currentTimeMillis(); // development only
            } // development only

            //For multithreading to allow interruption after sufficient search is completed.
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        return currentSiteConfiguration; //contains two elements: double minimum cost and List<Integer> minimum positions.
    }

    //Optimize with shrinking
    //Multithreading variant of leveledOptimizeCenters
    public static LeveledSiteConfiguration leveledOptimizeCenters(int[] minimumCenterCountByLevel, int[] maximumCenterCountByLevel, int taskCount) throws InterruptedException {
        long timer = System.currentTimeMillis(); // development only
        Random random = new Random();

        //Overriding finalNeighborhoodSize locally for multithreading based on number of centers to optimize if -1 chosen
        List<Integer> allPotentialSites = IntStream.range(0, searchParameters.getPotentialSitesCount()).boxed().collect(Collectors.toList());
        int[] localFinalNeighborhoodSizeByLevel = new int[searchParameters.getCenterLevels()];
        for (int i = 0; i < searchParameters.getCenterLevels(); i++) {
            localFinalNeighborhoodSizeByLevel[i] = SimAnnealingNeighbor.getFinalNeighborhoodSize(searchParameters.getPotentialSitesCount(), maximumCenterCountByLevel[i], finalNeighborhoodSize);
        }

        //Acceptance probability when comparing new target level cost to total cost to compute other level costs for new total to total cost comparison
        double targetLevelThresholdProbability = targetLevelThresholdProbability();

        //Create initial configuration+
        LeveledSiteConfiguration currentSiteConfiguration = new LeveledSiteConfiguration(minimumCenterCountByLevel, maximumCenterCountByLevel, allPotentialSites, searchParameters, taskCount, executor);
        double currentCost = currentSiteConfiguration.getCost();

        System.out.println("Initial cost " + currentCost + " at sites " + currentSiteConfiguration.getSitesByLevel()); //Initial cost from random placement.

        //Main simulated annealing algorithm
        double temp = initialTemp;
        int simAnnealingIteration = 0;
        while (temp > finalTemp) {
            simAnnealingIteration += 1;

            //For each level, try moving each cancer center once for every cycle
            for (int level = 0; level < searchParameters.getCenterLevels(); level++) {
                int currentCenterCount = currentSiteConfiguration.getSitesCount(level);
                int neighborhoodSize = 0;
                for (int position = 0; position < currentCenterCount; ++position) {
                    if (Math.random() < 0.5 || searchParameters.getSuperlevelsByLevel()[level].length == 0) { //try to shift to a neighbor
                        if (neighborhoodSize == 0) {
                            neighborhoodSize = SimAnnealingNeighbor.getNeighborhoodSize(currentCenterCount, searchParameters.getPotentialSitesCount(), localFinalNeighborhoodSizeByLevel[level], simAnnealingIteration, finalNeighborhoodSizeIteration);
                        }
                        if (searchParameters.getSuperlevelsByLevel()[level].length == 0 && searchParameters.getSublevelsByLevel()[level].length == 0) {
                            currentSiteConfiguration.tryShiftToNeighborWithoutLevelRelations(level, position, neighborhoodSize, searchParameters, temp, taskCount, executor);
                        } else {
                            currentSiteConfiguration.tryShiftToNeighbor(level, position, neighborhoodSize, searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                        }
                    } else { //try shift site to an unused superlevel site
                        List<Integer> unusedSuperlevelSites = currentSiteConfiguration.getRandomSuperlevelUnusedSites(level, searchParameters.getSuperlevelsByLevel()[level]);
                        if (unusedSuperlevelSites.size() > 0) {
                            currentSiteConfiguration.tryShiftSite(level, position, pickRandomSite(unusedSuperlevelSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                        } else { //no unused superlevel sites
                            boolean allSuperlevelsSubmaximal = true;
                            for (int superlevel : searchParameters.getSuperlevelsByLevel()[level]) {
                                if (currentSiteConfiguration.getSites(superlevel).size() == searchParameters.getMaxNewCentersByLevel()[superlevel]) {
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

            //Try adding or removing one of current sites for each level
            for (int level = 0; level < searchParameters.getCenterLevels(); level++) {
                int currentCenterCount = currentSiteConfiguration.getSitesCount(level);
                double adjustmentType = Math.random();
                if (adjustmentType < 0.5 && currentCenterCount < maximumCenterCountByLevel[level]) {
                    if (searchParameters.getSuperlevelsByLevel()[level].length == 0) {
                        currentSiteConfiguration.tryAddSiteWithoutSuperlevels(level, pickRandomAddableSite(currentSiteConfiguration.getSites(level), allPotentialSites, random), searchParameters, temp, taskCount, executor);
                    } else if (adjustmentType < 0.3) { //attempt to add unrestricted site unless number of sites is already maximal
                        List<Integer> restrictedAddableSuperlevelSites = currentSiteConfiguration.getRestrictedAddableSuperlevelSites(level, searchParameters.getSuperlevelsByLevel()[level], searchParameters.getMaxNewCentersByLevel());
                        if (restrictedAddableSuperlevelSites == null) { //not maximal, will add unrestricted site
                            currentSiteConfiguration.tryAddSite(level, pickRandomAddableSite(currentSiteConfiguration.getSites(level), allPotentialSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                        } else if (restrictedAddableSuperlevelSites.size() > 0) { //there are restrictions on adding sites to some superlevel but an available site exists
                            currentSiteConfiguration.tryAddSite(level, pickRandomSite(restrictedAddableSuperlevelSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                        }
                    } else { //attempt to add existing superlevel site first
                        List<Integer> restrictedAddableSuperlevelSites = currentSiteConfiguration.getRestrictedAddableSuperlevelSites(level, searchParameters.getSuperlevelsByLevel()[level], searchParameters.getMaxNewCentersByLevel());
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
                } else if (currentCenterCount > minimumCenterCountByLevel[level]) { //try to remove site
                    if (searchParameters.getSublevelsByLevel().length == 0) {
                        currentSiteConfiguration.tryRemovePositionWithoutSublevels(level, random.nextInt(currentSiteConfiguration.getSitesCount(level)), searchParameters, temp, taskCount, executor);
                    } else {
                        List<Integer> candidateRemovalSites = currentSiteConfiguration.getCandidateRemovalSites(level, searchParameters.getSublevelsByLevel()[level], minimumCenterCountByLevel);
                        if (candidateRemovalSites.size() > 0) {
                            currentSiteConfiguration.tryRemoveSite(level, pickRandomSite(candidateRemovalSites, random), searchParameters, temp, targetLevelThresholdProbability, taskCount, executor);
                        }
                    }
                }
            }

            temp *= coolingRate;

            long elapsedTime = System.currentTimeMillis()  - timer; //development only
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