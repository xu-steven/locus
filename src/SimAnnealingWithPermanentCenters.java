import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SimAnnealingWithPermanentCenters extends SimAnnealingSearch{

    public SimAnnealingWithPermanentCenters() {
        //Simulated annealing configuration
        initialTemp = 10000;
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
        List<List<Integer>> levelSequences = new ArrayList<>();
        levelSequences.add(Arrays.asList(0, 1));
        levelSequences.add(Arrays.asList(0, 2));
        searchParameters = new SearchSpace(new int[]{12, 12, 12}, new int[]{12, 12, 12}, Arrays.asList(new ArrayList<>(), Arrays.asList()),
                minimumCasesByLevel, servicedProportionByLevel, levelSequences,
                censusFileLocation, permanentGraphLocation, potentialGraphLocation, azimuthLocation, haversineLocation, 6, executor);
    }

    public static void main(String[] args) throws InterruptedException {
        new SimAnnealingWithPermanentCenters();
        System.out.println("Starting optimization algorithm"); //development only
        //This can be multithreaded with each thread working on a different number n.
        double minimumCost = Double.POSITIVE_INFINITY;
        List<Integer> minimumSites = new ArrayList<>();
        List<List<Integer>> higherLevelMinimumSitesArray = new ArrayList<>();
        //development start
        for (int i = 0; i < 10000; i++) {//dev
            List<Object> solutionWithNCenters = leveledOptimizeCenters(2, 2, 6);
            minimumCost = (double) solutionWithNCenters.get(0);
            minimumSites = (List<Integer>) solutionWithNCenters.get(1);
            higherLevelMinimumSitesArray = (List<List<Integer>>) solutionWithNCenters.get(2);
        } //dev
        System.out.println("Minimum cost " + minimumCost + " at sites " + minimumSites + " and higher level sites " + higherLevelMinimumSitesArray + ".");
        executor.shutdown();
        return;
    }

    //Multithreading variant of leveledOptimizeCenters
    public static List<Object> leveledOptimizeCenters(int minimumNewCenterCount, int maximumNewCenterCount, int taskCount) throws InterruptedException {
        long timer = System.currentTimeMillis(); // development only

        //Overriding finalNeighborhoodSize locally for multithreading based on number of centers to optimize if -1 chosen
        List<Integer> potentialSites = IntStream.range(0, searchParameters.getPotentialSitesCount()).boxed().collect(Collectors.toList());
        int localFinalNeighborhoodSize = SimAnnealingNeighbor.getFinalNeighborhoodSize(searchParameters.getPotentialSitesCount(), minimumNewCenterCount, finalNeighborhoodSize);

        //Create initial configuration
        LeveledSiteConfigurationForPermanentCenters currentSiteConfiguration = new LeveledSiteConfigurationForPermanentCenters(minimumNewCenterCount, maximumNewCenterCount, potentialSites, searchParameters, taskCount, executor);
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
            for (int i = searchParameters.getPermanentCentersCount(); i < currentCenterCount; ++i ) {
                LeveledSiteConfigurationForPermanentCenters newSiteConfiguration = currentSiteConfiguration.shiftLowestLevelSite(i, neighborhoodSize, searchParameters, taskCount, executor);
                double newCost = newSiteConfiguration.getCost();
                //Decide whether to accept new positions
                if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                    currentSiteConfiguration = newSiteConfiguration;
                    currentCost = newCost;
                }
            }

            //Try adding or removing one of current sites
            if (Math.random() < 0.5 && currentCenterCount < maximumNewCenterCount + searchParameters.getPermanentCentersCount()) { //add site
                LeveledSiteConfigurationForPermanentCenters newSiteConfiguration = currentSiteConfiguration.addLowestLevelSite(potentialSites, searchParameters, taskCount, executor);
                double newCost = newSiteConfiguration.getCost();
                if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                    currentSiteConfiguration = newSiteConfiguration;
                    currentCost = newCost;
                    currentCenterCount += 1;
                }
            } else if (currentCenterCount - searchParameters.getPermanentCentersCount() > currentSiteConfiguration.getAllHigherLevelSites().size() - searchParameters.getPermanentAllHLCentersCount()) { //remove site
                LeveledSiteConfigurationForPermanentCenters newSiteConfiguration = currentSiteConfiguration.removeLowestLevelSite(searchParameters, taskCount, executor);
                double newCost = newSiteConfiguration.getCost();
                if (acceptanceProbability(currentCost, newCost, temp) > Math.random()) {
                     currentSiteConfiguration = newSiteConfiguration;
                     currentCost = newCost;
                     currentCenterCount -= 1;
                }
            }

            //Try adding, removing, or shifting one of the higher level positions for each level in array
            //Rebuilds all higher level sites from ground up at end.
            for (int i = 0; i < searchParameters.getCenterLevels(); ++i ) {
                //Create artificial level configuration
                List<Integer> currentThisLevelSites = currentSiteConfiguration.getHigherLevelSitesArray().get(i);
                int currentThisLevelSiteCount = currentThisLevelSites.size();
                double currentThisLevelCost = currentSiteConfiguration.getHigherLevelCosts()[i];
                int[] currentThisLevelMinimumPositionsByOrigin = currentSiteConfiguration.getHigherLevelMinimumPositionsByOrigin()[i];
                SiteConfigurationForPermanentCenters currentThisLevelSiteConfiguration = new SiteConfigurationForPermanentCenters(currentThisLevelSites, currentThisLevelCost, currentThisLevelMinimumPositionsByOrigin);
                //Create new site configuration and compute cost
                SiteConfigurationForPermanentCenters newThisLevelSiteConfiguration;
                double adjustmentType = Math.random();
                if ((adjustmentType < 0.1 || currentThisLevelSiteCount == searchParameters.getPermanentHLCentersCount()[i]) && currentThisLevelSiteCount != currentSiteConfiguration.getSites().size()) { //add higher level site to level
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration.addSite(currentSiteConfiguration.getSites(), searchParameters.getServicedProportionByLevel()[i + 1], searchParameters.getMinimumCasesByLevel()[i + 1], searchParameters, taskCount, executor);
                } else if ((adjustmentType < 0.2 || currentThisLevelSiteCount == currentSiteConfiguration.getSites().size()) && currentThisLevelSiteCount > searchParameters.getPermanentHLCentersCount()[i]) { //remove higher level site from level
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration.removeSite(i, searchParameters.getServicedProportionByLevel()[i + 1], searchParameters.getMinimumCasesByLevel()[i + 1], searchParameters, taskCount, executor);
                } else if (currentThisLevelSiteCount > searchParameters.getPermanentHLCentersCount()[i]){
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration.shiftSite(i, currentSiteConfiguration.getSites(), searchParameters.getServicedProportionByLevel()[i + 1], searchParameters.getMinimumCasesByLevel()[i + 1], searchParameters, taskCount, executor);
                } else { //shift one of the higher level sites
                    newThisLevelSiteConfiguration = currentThisLevelSiteConfiguration;
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

            long elapsedTime = System.currentTimeMillis() - timer; //development only
            if (elapsedTime > updateFrequency) { //development only
                System.out.println("Temperature is now " + temp +" on optimization for " + currentCenterCount + " new center(s). Neighborhood size was " + neighborhoodSize + " for iteration " + simAnnealingIteration); // development only
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