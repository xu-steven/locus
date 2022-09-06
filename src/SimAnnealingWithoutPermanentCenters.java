import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

//Shrinking neighborhood optimization
public class SimAnnealingWithoutPermanentCenters extends SimAnnealingSearch{

    public SimAnnealingWithoutPermanentCenters() {
        //Simulated annealing configuration
        this.initialTemp = 10000;
        this.finalTemp = 10;
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
        double[] minimumCasesByLevel = {(double) 10000, (double) 1000000, (double) 2000000, (double) 10000, (double) 10};
        double[] servicedProportionByLevel = {1.0, 0.0, 0.0, 0.0, 0.0};
        int[] minimumCenterCountByLevel = {6, 0, 0, 1, 4};
        int[] maximumCenterCountByLevel = {6, 6, 6, 5, 5};
        List<List<Integer>> levelSequences = new ArrayList<>();
        levelSequences.add(Arrays.asList(0, 1));
        //levelSequences.add(Arrays.asList(0, 2));
        levelSequences.add(Arrays.asList(1, 4));
        searchParameters = new SearchSpace(minimumCenterCountByLevel, maximumCenterCountByLevel, minimumCasesByLevel, servicedProportionByLevel, levelSequences,
                censusFileLocation, graphLocation, azimuthLocation, haversineLocation, 6, executor);
    }

    public static void main(String[] args) throws InterruptedException {
        new SimAnnealingWithoutPermanentCenters();
        System.out.println("Sublevels by level are " + Arrays.deepToString(searchParameters.getSublevelsByLevel()) + " and superlevels by level are " + Arrays.deepToString(searchParameters.getSuperlevelsByLevel()));
        System.out.println("Starting optimization algorithm"); //development only
        //This can be multithreaded with each thread working on a different number n.
        double minimumCost = Double.POSITIVE_INFINITY;
        List<Integer> minimumSites = new ArrayList<>();
        List<List<Integer>> higherLevelMinimumSitesArray = new ArrayList<>();
        //development start
        long runtime = 0;
        for (int i = 0; i < 200; i++) {//dev
            long startTime = System.currentTimeMillis();
            List<Object> solutionWithNCenters = leveledOptimizeCenters(searchParameters.getMinNewCentersByLevel(), searchParameters.getMaxNewCentersByLevel(), 6);
            long endTime = System.currentTimeMillis();
            runtime += (endTime - startTime);
        }
        System.out.println("Run time on 20 iterations was " + (runtime / 1000) + "s");
        //development end
        List<Object> solutionWithNCenters = leveledOptimizeCenters(searchParameters.getMinNewCentersByLevel(), searchParameters.getMaxNewCentersByLevel(), 6);
        minimumCost = (double) solutionWithNCenters.get(0);
        minimumSites = (List<Integer>) solutionWithNCenters.get(1);
        try {
            higherLevelMinimumSitesArray = (List<List<Integer>>) solutionWithNCenters.get(2);
            System.out.println("Minimum cost " + minimumCost + " at sites " + minimumSites + " and higher level sites " + higherLevelMinimumSitesArray + ".");
        } catch (Exception e) {
            System.out.println("Minimum cost " + minimumCost + " at sites " + minimumSites);
        }
        executor.shutdown();
        return;
    }

    //Optimize with shrinking
    //Multithreading variant of OptimizeNCenters
    public static List<Object> optimizeNCenters(int centerCount, int taskCount) throws InterruptedException {
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
        double currentCost = currentSiteConfiguration.getCost();
        int currentCenterCount = currentSiteConfiguration.getSites().size();

        System.out.println("Initial cost " + currentCost + " at " + currentSiteConfiguration.getSites()); //Initial cost from random placement.

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
                SiteConfiguration newSiteConfiguration = currentSiteConfiguration.shiftSiteWithoutLevels(i, neighborhoodSize, searchParameters, taskCount, executor);
                double newCost = newSiteConfiguration.getCost();
                //Decide whether to accept new positions
                if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                    currentSiteConfiguration = newSiteConfiguration;
                    currentCost = newCost;
                }
            }

            temp *= coolingRate;

            long elapsedTime = System.currentTimeMillis()  - timer; //development only
            if (elapsedTime > updateFrequency) { //development only
                System.out.println("Temperature is now " + temp +" on optimization for " + centerCount + " center(s). Neighborhood size was " + neighborhoodSize + " for iteration " + simAnnealingIteration); // development only
                System.out.println("The current cost is " + currentCost + " at positions " + currentSiteConfiguration.getSites()); // development only
                timer = System.currentTimeMillis(); // development only
            } // development only

            //For multithreading to allow interruption after sufficient search is completed.
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        return Arrays.asList(currentCost, currentSiteConfiguration.getSites()); //contains two elements: double minimum cost and List<Integer> minimum positions.
    }

    //Optimize with shrinking
    //Multithreading variant of leveledOptimizeCenters
    public static List<Object> leveledOptimizeCenters(int[] minimumCenterCountByLevel, int[] maximumCenterCountByLevel, int taskCount) throws InterruptedException {
        long timer = System.currentTimeMillis(); // development only

        //Overriding finalNeighborhoodSize locally for multithreading based on number of centers to optimize if -1 chosen
        List<Integer> allPotentialSites = IntStream.range(0, searchParameters.getPotentialSitesCount()).boxed().collect(Collectors.toList());
        int localFinalNeighborhoodSize = SimAnnealingNeighbor.getFinalNeighborhoodSize(searchParameters.getPotentialSitesCount(), Arrays.stream(maximumCenterCountByLevel).max().getAsInt(), finalNeighborhoodSize);

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
                int currentCenterCount = currentSiteConfiguration.getLevelSitesCount(level);
                for (int position = 0; position < currentCenterCount; ++position) {
                    LeveledSiteConfiguration newSiteConfiguration;
                    if (Math.random() < 0.5 || searchParameters.getSuperlevelsByLevel()[level].length == 0) { //try to shift site random new site
                        int neighborhoodSize = SimAnnealingNeighbor.getNeighborhoodSize(currentCenterCount, searchParameters.getPotentialSitesCount(), localFinalNeighborhoodSize, simAnnealingIteration, finalNeighborhoodSizeIteration);
                        newSiteConfiguration = currentSiteConfiguration.leveledShiftSite(level, position, neighborhoodSize, searchParameters, taskCount, executor);
                    } else { //try shift site to an unused superlevel site
                        List<Integer> unusedSuperlevelSites = currentSiteConfiguration.getUnusedSuperlevelSites(level, searchParameters.getSuperlevelsByLevel()[level]);
                        if (unusedSuperlevelSites.size() > 0) {
                            newSiteConfiguration = currentSiteConfiguration.shiftToPotentialSite(level, position, unusedSuperlevelSites, searchParameters, taskCount, executor);
                        } else { //no unused superlevel sites
                            int neighborhoodSize = SimAnnealingNeighbor.getNeighborhoodSize(currentCenterCount, searchParameters.getPotentialSitesCount(), localFinalNeighborhoodSize, simAnnealingIteration, finalNeighborhoodSizeIteration);
                            newSiteConfiguration = currentSiteConfiguration.leveledShiftSite(level, position, neighborhoodSize, searchParameters, taskCount, executor);
                        }
                    }
                    double newCost = newSiteConfiguration.getCost();
                    //Decide whether to accept new positions
                    if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                        currentSiteConfiguration = newSiteConfiguration;
                        currentCost = newCost;
                    }
                }
            }

            //Try adding or removing one of current sites for each level
            for (int level = 0; level < searchParameters.getCenterLevels(); level++) {
                int currentCenterCount = currentSiteConfiguration.getLevelSitesCount(level);
                double adjustmentType = Math.random();
                if (adjustmentType < 0.3 && currentCenterCount < maximumCenterCountByLevel[level]) { //attempt to add unrestricted site unless number of sites is already maximal
                    boolean maximalSuperlevelSites = false;
                    for (int superlevel : searchParameters.getSuperlevelsByLevel()[level]) {
                        if (currentSiteConfiguration.getLevelSitesCount(superlevel) == maximumCenterCountByLevel[superlevel]) {
                            maximalSuperlevelSites = true;
                        }
                    }
                    if (maximalSuperlevelSites) { //maximal, will attempt to add unused superlevel site
                        List<Integer> unusedSuperlevelSites = currentSiteConfiguration.getUnusedSuperlevelSites(level, searchParameters.getSuperlevelsByLevel()[level]);
                        if (unusedSuperlevelSites.size() > 0) {
                            LeveledSiteConfiguration newSiteConfiguration = currentSiteConfiguration.leveledAddSite(level, unusedSuperlevelSites, searchParameters, taskCount, executor);
                            double newCost = newSiteConfiguration.getCost();
                            if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                                currentSiteConfiguration = newSiteConfiguration;
                                currentCost = newCost;
                            }
                        }
                    } else { //not maximal, will add unrestricted site
                        LeveledSiteConfiguration newSiteConfiguration = currentSiteConfiguration.leveledAddSite(level, allPotentialSites, searchParameters, taskCount, executor);
                        double newCost = newSiteConfiguration.getCost();
                        if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                            currentSiteConfiguration = newSiteConfiguration;
                            currentCost = newCost;
                        }
                    }
                } else if (adjustmentType < 0.5 && currentCenterCount < maximumCenterCountByLevel[level]) { //attempt to add existing superlevel site first
                    List<Integer> unusedSuperlevelSites = currentSiteConfiguration.getUnusedSuperlevelSites(level, searchParameters.getSuperlevelsByLevel()[level]);
                    if (unusedSuperlevelSites.size() > 0) {
                        LeveledSiteConfiguration newSiteConfiguration = currentSiteConfiguration.leveledAddSite(level, unusedSuperlevelSites, searchParameters, taskCount, executor);
                        double newCost = newSiteConfiguration.getCost();
                        if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                            currentSiteConfiguration = newSiteConfiguration;
                            currentCost = newCost;
                        }
                    } else {
                        boolean maximalSuperlevelSites = false;
                        for (int superlevel : searchParameters.getSuperlevelsByLevel()[level]) {
                            if (currentSiteConfiguration.getLevelSitesCount(superlevel) == maximumCenterCountByLevel[superlevel]) {
                                maximalSuperlevelSites = true;
                            }
                        }
                        if (!maximalSuperlevelSites) {
                            LeveledSiteConfiguration newSiteConfiguration = currentSiteConfiguration.leveledAddSite(level, allPotentialSites, searchParameters, taskCount, executor);
                            double newCost = newSiteConfiguration.getCost();
                            if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                                currentSiteConfiguration = newSiteConfiguration;
                                currentCost = newCost;
                            }
                        }
                    }
                } else if (currentCenterCount > minimumCenterCountByLevel[level]) { //try to remove site
                    List<Integer> candidateRemovalSites = currentSiteConfiguration.getCandidateRemovalSites(level, searchParameters.getSublevelsByLevel()[level], minimumCenterCountByLevel);
                    if (candidateRemovalSites.size() > 0) {
                        LeveledSiteConfiguration newSiteConfiguration = currentSiteConfiguration.removePotentialSite(level, candidateRemovalSites, searchParameters, taskCount, executor);
                        double newCost = newSiteConfiguration.getCost();
                        if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                            currentSiteConfiguration = newSiteConfiguration;
                            currentCost = newCost;
                        }
                    }
                }
            }

            temp *= coolingRate;

            long elapsedTime = System.currentTimeMillis()  - timer; //development only
            if (elapsedTime > updateFrequency) { //development only
                System.out.println("Temperature is now " + temp + " for iteration " + simAnnealingIteration); // development only
                System.out.println("The current cost is " + currentCost + " with cost by level " + Arrays.toString(currentSiteConfiguration.getCostByLevel()) + " at positions " + currentSiteConfiguration.getSitesByLevel()); // development only
                System.out.println("Garbage collection count = " + DevelopmentUtils.getGarbageCollectionCycles() + " taking " + DevelopmentUtils.getGarbageCollectionTime() + "ms"); //garbage time
                timer = System.currentTimeMillis(); // development only
            } // development only

            //For multithreading to allow interruption after sufficient search is completed.
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        //development only
        System.out.println("Final cost " + currentSiteConfiguration.getCost() + " and minimum positions by origin " + Arrays.toString(currentSiteConfiguration.getMinimumPositionsByLevelAndOrigin()[0]));
        return Arrays.asList(currentCost, currentSiteConfiguration.getSitesByLevel()); //contains 2 elements: minimum cost, minimum positions array.
    }
}