import java.util.*;
import java.util.concurrent.ExecutorService;
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
        this.finalNeighborhoodSizeIteration = 2000;

        //Multithreading configuration
        int threadCount = 6;
        executor = Executors.newFixedThreadPool(threadCount);

        //File locations
        String censusFileLocation = "M:\\Optimization Project\\alberta2016_origins.csv";
        String graphLocation = censusFileLocation.replace("_origins.csv", "_graph.csv");
        String azimuthLocation = censusFileLocation.replace("_origins.csv", "_azimuth.csv");
        String haversineLocation = censusFileLocation.replace("_origins.csv", "_haversine.csv");

        //Search space parameters
        searchParameters = new SearchSpace(12, 12, Arrays.asList((double) 10000, (double) 1000000, (double) 2000000), Arrays.asList(1.0, 0.0, 0.0),
                censusFileLocation, graphLocation, azimuthLocation, haversineLocation, 6, 6, executor);
    }

    public static void main(String[] args) throws InterruptedException {
        new SimAnnealingWithoutPermanentCenters();
        System.out.println("Starting optimization algorithm"); //development only
        //This can be multithreaded with each thread working on a different number n.
        double minimumCost = Double.POSITIVE_INFINITY;
        List<Integer> minimumSites = new ArrayList<>();
        List<List<Integer>> higherLevelMinimumSitesArray = new ArrayList<>();
        //development start
        long runtime = 0;
        for (int i = 0; i < 50; i++) {//dev
            long startTime = System.currentTimeMillis();
            List<Object> solutionWithNCenters = leveledOptimizeCenters(6, 6, 6);
            long endTime = System.currentTimeMillis();
            runtime += (endTime - startTime);
        }
        System.out.println("Run time on 20 iterations was " + (runtime / 1000) + "s");
        //development end
        List<Object> solutionWithNCenters = leveledOptimizeCenters(6, 6, 6);
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
    //Multithreaded variant of leveledOptimizeNCenters
    public static List<Object> leveledOptimizeNCenters(int centerCount, int taskCount) throws InterruptedException {
        long timer = System.currentTimeMillis(); // development only

        //Overriding finalNeighborhoodSize locally for multithreading based on number of centers to optimize if -1 chosen
        int localFinalNeighborhoodSize;
        if (finalNeighborhoodSize == -1) {
            localFinalNeighborhoodSize = (int) Math.min(Math.ceil(1.5 * searchParameters.getPotentialSitesCount() / centerCount), searchParameters.getPotentialSitesCount() - centerCount); //to account for 1 center
            localFinalNeighborhoodSize = Math.max(localFinalNeighborhoodSize, 1200);
        } else {
            localFinalNeighborhoodSize = finalNeighborhoodSize;
        }

        //Create initial configuration+
        List<Integer> potentialSites = IntStream.range(0, searchParameters.getPotentialSitesCount()).boxed().collect(Collectors.toList());
        LeveledSiteConfiguration currentSiteConfiguration = new LeveledSiteConfiguration(centerCount, centerCount, potentialSites, searchParameters, taskCount, executor);
        double currentCost = currentSiteConfiguration.getCost();
        int currentCenterCount = currentSiteConfiguration.getSites().size();

        System.out.println("Initial cost " + currentCost + " at sites " + currentSiteConfiguration.getSites() + " and higher level sites " + currentSiteConfiguration.getHigherLevelSitesArray());

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
                LeveledSiteConfiguration newSiteConfiguration = currentSiteConfiguration.shiftLowestLevelSite(i, neighborhoodSize, searchParameters, taskCount, executor);
                double newCost = newSiteConfiguration.getCost();
                //Decide whether to accept new positions
                if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                    currentSiteConfiguration = newSiteConfiguration;
                    currentCost = newCost;
                }
            }

            //Try adding, removing, or shifting one of the higher level positions for each level in array
            //Rebuilds all higher level sites from ground up at end.
            for (int i = 0; i < searchParameters.getHigherCenterLevels(); ++i ) {
                //Create artificial level configuration
                List<Integer> currentThisLevelSites = currentSiteConfiguration.getHigherLevelSitesArray().get(i);
                int currentThisLevelSiteCount = currentThisLevelSites.size();
                double currentThisLevelCost = currentSiteConfiguration.getHigherLevelCosts().get(i);
                List<Integer> currentThisLevelMinimumPositionsByOrigin = currentSiteConfiguration.getHigherLevelMinimumPositionsByOrigin().get(i);
                SiteConfiguration currentThisLevelSiteConfiguration = new SiteConfiguration(currentThisLevelSites, currentThisLevelCost, currentThisLevelMinimumPositionsByOrigin);
                //Create new site configuration and compute cost
                SiteConfiguration newThisLevelSiteConfiguration;
                double adjustmentType = Math.random();
                if ((adjustmentType < 0.1 || currentThisLevelSiteCount == 0) && currentThisLevelSiteCount != currentCenterCount) { //add higher level site to level
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration.addSite(currentSiteConfiguration.getSites(), searchParameters.getServicedProportionByLevel().get(i + 1), searchParameters.getMinimumCasesByLevel().get(i + 1), searchParameters, taskCount, executor);
                } else if (adjustmentType < 0.2 || currentThisLevelSiteCount == currentCenterCount) { //remove higher level site from level
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration.removeSite(searchParameters.getServicedProportionByLevel().get(i + 1), searchParameters.getMinimumCasesByLevel().get(i + 1), searchParameters, taskCount, executor);
                } else { //shift one of the higher level sites
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration.shiftSite(currentSiteConfiguration.getSites(), searchParameters.getServicedProportionByLevel().get(i + 1), searchParameters.getMinimumCasesByLevel().get(i + 1), searchParameters, taskCount, executor);
                }
                double newThisLevelCost = newThisLevelSiteConfiguration.getCost();
                //Decide on accepting
                if (acceptanceProbability(currentThisLevelCost, newThisLevelCost, temp) > Math.random()) {
                    currentSiteConfiguration.updateHigherLevelConfiguration(i, newThisLevelSiteConfiguration, currentThisLevelCost, newThisLevelCost);
                }
            }
            if (currentSiteConfiguration.getAllHigherLevelSites() == null) { //if any changes were accepted
                currentSiteConfiguration.updateAllHigherLevelSites();
                currentCost = currentSiteConfiguration.getCost();
            }

            temp *= coolingRate;

            long elapsedTime = System.currentTimeMillis()  - timer; //development only
            if (elapsedTime > updateFrequency) { //development only
                System.out.println("Temperature is now " + temp +" on optimization for " + currentCenterCount + " center(s). Neighborhood size was " + neighborhoodSize + " for iteration " + simAnnealingIteration); // development only
                System.out.println("The current cost is " + currentCost + " at positions " + currentSiteConfiguration.getSites() + " and higher level positions " + currentSiteConfiguration.getHigherLevelSitesArray()); // development only
                System.out.println("Garbage collection count = " + DevelopmentUtils.getGarbageCollectionCycles() + " taking " + DevelopmentUtils.getGarbageCollectionTime() + "ms"); //garbage time
                timer = System.currentTimeMillis(); // development only
            } // development only

            //For multithreading to allow interruption after sufficient search is completed.
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        return Arrays.asList(currentCost, currentSiteConfiguration.getSites(), currentSiteConfiguration.getHigherLevelSitesArray()); //contains 3 elements: minimum cost, minimum positions, and higher level minimum positions.
    }

    //Optimize with shrinking
    //Multithreading variant of leveledOptimizeCenters
    public static List<Object> leveledOptimizeCenters(int minimumCenterCount, int maximumCenterCount, int taskCount) throws InterruptedException {
        long timer = System.currentTimeMillis(); // development only

        //Overriding finalNeighborhoodSize locally for multithreading based on number of centers to optimize if -1 chosen
        List<Integer> potentialSites = IntStream.range(0, searchParameters.getPotentialSitesCount()).boxed().collect(Collectors.toList());
        int localFinalNeighborhoodSize = SimAnnealingNeighbor.getFinalNeighborhoodSize(searchParameters.getPotentialSitesCount(), minimumCenterCount, finalNeighborhoodSize);

        //Create initial configuration+
        LeveledSiteConfiguration currentSiteConfiguration = new LeveledSiteConfiguration(minimumCenterCount, maximumCenterCount, potentialSites, searchParameters, taskCount, executor);
        double currentCost = currentSiteConfiguration.getCost();
        int currentCenterCount = currentSiteConfiguration.getSites().size();

        System.out.println("Initial cost " + currentCost + " at sites " + currentSiteConfiguration.getSites() + " and higher level sites " + currentSiteConfiguration.getHigherLevelSitesArray()); //Initial cost from random placement.

        //Main simulated annealing algorithm
        double temp = initialTemp;
        int simAnnealingIteration = 0;
        while (temp > finalTemp) {
            simAnnealingIteration += 1;
            int neighborhoodSize = SimAnnealingNeighbor.getNeighborhoodSize(currentCenterCount, searchParameters.getPotentialSitesCount(), localFinalNeighborhoodSize, simAnnealingIteration, finalNeighborhoodSizeIteration);
            //Try moving each cancer center once for every cycle
            for (int i = 0; i < currentCenterCount; ++i ) {
                LeveledSiteConfiguration newSiteConfiguration = currentSiteConfiguration.shiftLowestLevelSite(i, neighborhoodSize, searchParameters, taskCount, executor);
                double newCost = newSiteConfiguration.getCost();
                //Decide whether to accept new positions
                if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                    currentSiteConfiguration = newSiteConfiguration;
                    currentCost = newCost;
                }
            }
            //Try adding or removing one of current sites
            if (Math.random() < 0.5 && currentCenterCount < maximumCenterCount) { //add site
                LeveledSiteConfiguration newSiteConfiguration = currentSiteConfiguration.addLowestLevelSite(potentialSites, searchParameters, taskCount, executor);
                double newCost = newSiteConfiguration.getCost();
                if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                    currentSiteConfiguration = newSiteConfiguration;
                    currentCost = newCost;
                    currentCenterCount += 1;
                }
            } else if (currentCenterCount > currentSiteConfiguration.getAllHigherLevelSites().size()) { //remove site
                LeveledSiteConfiguration newSiteConfiguration = currentSiteConfiguration.removeLowestLevelSite(searchParameters, taskCount, executor);
                double newCost = newSiteConfiguration.getCost();
                if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                    currentSiteConfiguration = newSiteConfiguration;
                    currentCost = newCost;
                    currentCenterCount -= 1;
                }
            }

            //Try adding, removing, or shifting one of the higher level positions for each level in array
            //Rebuilds all higher level sites from ground up at end.
            for (int i = 0; i < searchParameters.getHigherCenterLevels(); ++i ) {
                //Create artificial level configuration
                List<Integer> currentThisLevelSites = currentSiteConfiguration.getHigherLevelSitesArray().get(i);
                int currentThisLevelSiteCount = currentThisLevelSites.size();
                double currentThisLevelCost = currentSiteConfiguration.getHigherLevelCosts().get(i);
                List<Integer> currentThisLevelMinimumPositionsByOrigin = currentSiteConfiguration.getHigherLevelMinimumPositionsByOrigin().get(i);
                SiteConfiguration currentThisLevelSiteConfiguration = new SiteConfiguration(currentThisLevelSites, currentThisLevelCost, currentThisLevelMinimumPositionsByOrigin);
                //Create new site configuration and compute cost
                SiteConfiguration newThisLevelSiteConfiguration;
                double adjustmentType = Math.random();
                if ((adjustmentType < 0.1 || currentThisLevelSiteCount == 0) && currentThisLevelSiteCount != currentCenterCount) { //add higher level site to level
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration.addSite(currentSiteConfiguration.getSites(), searchParameters.getServicedProportionByLevel().get(i + 1), searchParameters.getMinimumCasesByLevel().get(i + 1), searchParameters, taskCount, executor);
                } else if (adjustmentType < 0.2 || currentThisLevelSiteCount == currentCenterCount) { //remove higher level site from level
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration.removeSite(searchParameters.getServicedProportionByLevel().get(i + 1), searchParameters.getMinimumCasesByLevel().get(i + 1), searchParameters, taskCount, executor);
                } else { //shift one of the higher level sites
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration.shiftSite(currentSiteConfiguration.getSites(), searchParameters.getServicedProportionByLevel().get(i + 1), searchParameters.getMinimumCasesByLevel().get(i + 1), searchParameters, taskCount, executor);
                }
                double newThisLevelCost = newThisLevelSiteConfiguration.getCost();
                //Decide on accepting
                if (acceptanceProbability(currentThisLevelCost, newThisLevelCost, temp) > Math.random()) {
                    currentSiteConfiguration.updateHigherLevelConfiguration(i, newThisLevelSiteConfiguration, currentThisLevelCost, newThisLevelCost);
                }
            }
            if (currentSiteConfiguration.getAllHigherLevelSites() == null) { //if any changes were accepted
                currentSiteConfiguration.updateAllHigherLevelSites();
                currentCost = currentSiteConfiguration.getCost();
            }

            temp *= coolingRate;

            long elapsedTime = System.currentTimeMillis()  - timer; //development only
            if (elapsedTime > updateFrequency) { //development only
                System.out.println("Temperature is now " + temp +" on optimization for " + currentCenterCount + " center(s). Neighborhood size was " + neighborhoodSize + " for iteration " + simAnnealingIteration); // development only
                System.out.println("The current cost is " + currentCost + " at positions " + currentSiteConfiguration.getSites() + " and higher level positions " + currentSiteConfiguration.getHigherLevelSitesArray()); // development only
                System.out.println("Garbage collection count = " + DevelopmentUtils.getGarbageCollectionCycles() + " taking " + DevelopmentUtils.getGarbageCollectionTime() + "ms"); //garbage time
                timer = System.currentTimeMillis(); // development only
            } // development only

            //For multithreading to allow interruption after sufficient search is completed.
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        return Arrays.asList(currentCost, currentSiteConfiguration.getSites(), currentSiteConfiguration.getHigherLevelSitesArray()); //contains 3 elements: minimum cost, minimum positions, and higher level minimum positions.
    }
}