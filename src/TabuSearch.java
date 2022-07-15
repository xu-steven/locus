import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TabuSearch {
    //Soft minimum cases per cancer center
    static double minimumCases;// = 10000;

    //Minimum number of moves that a facility remains open or closed, variable per cycle between min and max.
    static Integer minimumClosedMemory;
    static Integer maximumClosedMemory;
    static Integer minimumOpenMemory;
    static Integer maximumOpenMemory;

    //Stopping constants
    static double shortStoppingCoefficient;
    static double mediumStoppingCoefficient;
    static Integer longStoppingCoefficient;

    static List<List<String>> censusArray;// = parseCSV(censusFileLocation);
    public static List<String> censusHeadings;// = censusArray.get(0);
    static List<List<String>> graphArray;// = parseCSV(graphLocation);
    static int potentialSitesCount;// = graphArray.get(0).size() - 1;

    //Development only
    public static Integer updateFrequency = 1000; //frequency of updates in ms // development only
    static Integer minNewCenters = 6; //Usually 1.
    static Integer maxNewCenters = 6; //Maximum number of cancer centers to try

    //Constructs a simulated annealing search with no previously defined parameters. For development.
    public TabuSearch() {
        String censusFileLocation = "M:\\Optimization Project\\alberta2016.csv";
        String graphLocation = censusFileLocation.replace(".csv", "_graph.csv");
        this.minimumCases = 200000;
        this.minimumClosedMemory = 40;
        this.maximumClosedMemory = 60;
        this.minimumOpenMemory = 5;
        this.maximumOpenMemory = 40;
        this.shortStoppingCoefficient = 3;
        this.mediumStoppingCoefficient = 1;
        this.longStoppingCoefficient = 10;
        this.censusArray = parseCSV(censusFileLocation);
        this.censusHeadings = censusArray.get(0);
        this.graphArray = parseCSV(graphLocation);
        this.potentialSitesCount = graphArray.get(0).size() - 1;
    }

    public static void main(String[] args) {
        new TabuSearch();

        //Create random list of current cancer center positions and list of remaining potential positions.
        Random random = new Random();
        List<Integer> unusedPositions = IntStream.rangeClosed(1, potentialSitesCount).boxed().collect(Collectors.toList());
        List<Integer> currentPositions = new ArrayList<>(Arrays.asList(4020, 58, 166, 4732, 2641, 5188, 628, 3418, 3063, 5295, 4926, 2380));

        //Perform search
        List<Object> optimizationResult = optimizeWithMemory(currentPositions);
        double minimumCost = (double) optimizationResult.get(0);
        List<Integer> minimumPositions = (List<Integer>) optimizationResult.get(1);

        System.out.println("Minimum cost " + minimumCost + " at positions " + minimumPositions);
    }

    public static List<Object> optimizeWithMemory(List<Integer> startingPositions){
        //Stored best positions, eventually for output
        List<Integer> bestPositions = new ArrayList<>(startingPositions);
        double bestCost = (double) initialCost(bestPositions, graphArray, censusArray).get(0);
        System.out.println("Starting cost " + bestCost + " at " + bestPositions);

        //Cycle best information
        List<Integer> cycleBestPositions = new ArrayList<>(bestPositions);
        double cycleBestCost = bestCost;

        //Establish current cost positions to move around
        List<Integer> currentPositions = new ArrayList<>(bestPositions);
        double currentCost = bestCost;

        //Compute initial cost and list of the closest of current positions for each originating population center
        List<Integer> currentMinimumCostPositionsByOrigin = (List<Integer>) initialCost(currentPositions, graphArray, censusArray).get(1);

        //Iteration and cycle counters
        Integer cycleStart = 1;
        Integer currentMove = 1;
        Integer cyclesFinished = 0;

        //Short term memory structure. For each potential site (key), records iteration (value) on which status was last changed from open to closed.
        HashMap<Integer, Integer> lastStatusChangeMoves = new HashMap<>(); //Hashmap needed for sorting in long-term memory process
        for (int i = 1; i <= potentialSitesCount; i++){
            lastStatusChangeMoves.put(i, 0);
        }

        //Intermediate term memory. For each potential site, records number of times status was changed.
        List<Integer> statusChangeFrequency = new ArrayList<>();
        statusChangeFrequency.add(-1); //Enumerating sites from 1
        statusChangeFrequency.addAll(Collections.nCopies(potentialSitesCount, 0));

        Integer c = 1;

        while (c <= longStoppingCoefficient) {
            while (currentMove - cycleStart < shortStoppingCoefficient * potentialSitesCount) {
                List<Object> update = shortTermUpdatePositions(currentPositions, currentCost, currentMove, lastStatusChangeMoves, cycleBestCost, bestCost, currentMinimumCostPositionsByOrigin);
                currentPositions = (List<Integer>) update.get(1);
                currentCost = (double) update.get(0);
                currentMinimumCostPositionsByOrigin = (List<Integer>) update.get(3);
                if ((Boolean) update.get(5)) {
                    bestPositions = new ArrayList<>(currentPositions);
                    cycleBestPositions = new ArrayList<>(currentPositions);
                    bestCost = currentCost;
                    cycleBestCost = currentCost;
                } else if ((Boolean) update.get(4)) {
                    cycleBestPositions = new ArrayList<>(currentPositions);
                    cycleBestCost = currentCost;
                }
                lastStatusChangeMoves.put((Integer) update.get(2), currentMove);
                currentMove++;
                System.out.println("Completed short term search with best cost " + bestCost + " at positions " + bestPositions + " on move " + currentMove);
            }
            currentPositions = longTermUpdatePositions(currentPositions, lastStatusChangeMoves, c);
            List<Object> update = initialCost(currentPositions, graphArray, censusArray);
            currentCost = (double) update.get(0);
            currentMinimumCostPositionsByOrigin = (List<Integer>) update.get(1);
            c++;
            cycleStart = currentMove;
        }

        return Arrays.asList(bestCost, bestPositions);
    }

    public static List<Object> shortTermUpdatePositions (List<Integer> currentPositions, double currentCost, Integer currentMove, HashMap<Integer, Integer> lastStatusChangeMoves, Double cycleBestCost, Double bestCost, List<Integer> currentMinimumCostPositionsByOrigin) {
        Boolean improvedBestCost = false;
        Boolean improvedCycleBestCost = false;
        Integer updatedSite = -1;
        Integer closedMemory = minimumClosedMemory;
        Integer openMemory = minimumOpenMemory;
        List<Integer> bestNewPositions = new ArrayList<>();
        List<Integer> bestNewMinimumCostPositionsByOrigin = new ArrayList<>();
        double bestNewCost = Double.MAX_VALUE;
        for (int i = 1; i <= potentialSitesCount; i++) {

            Boolean addedPosition = false;
            List<Integer> testPositions = new ArrayList<>(currentPositions);
            int finalI = i;
            if (!testPositions.removeIf(p -> p.equals(finalI))) {
                testPositions.add(finalI);
                addedPosition = true;
            }

            List<Object> update;
            if (addedPosition) {
                update = addCost(testPositions, finalI, graphArray, censusArray, currentMinimumCostPositionsByOrigin);

            } else{
                update = removeCost(testPositions, currentPositions.indexOf(finalI), graphArray, censusArray, currentMinimumCostPositionsByOrigin);
            }
            double testCost = (double) update.get(0);
            List<Integer> testMinimumCostPositionsByOrigin = (List<Integer>) update.get(1);

            if (testCost < bestNewCost) {
                //If improving on best cycle result, then override Tabu
                if (improvedCycleBestCost) {
                    bestNewCost = testCost;
                    bestNewPositions = testPositions;
                    bestNewMinimumCostPositionsByOrigin = testMinimumCostPositionsByOrigin;
                    updatedSite = finalI;
                } else {
                    //If improving on best cycle result, then override Tabu and remember
                    if (testCost < cycleBestCost) {
                        bestNewCost = testCost;
                        bestNewPositions = testPositions;
                        bestNewMinimumCostPositionsByOrigin = testMinimumCostPositionsByOrigin;
                        updatedSite = finalI;
                        improvedCycleBestCost = true;
                    } else {
                        //Check if in Tabu depending on whether position added or lost
                        if (addedPosition) {
                            if (currentMove - lastStatusChangeMoves.get(finalI) > closedMemory) {
                                bestNewCost = testCost;
                                bestNewPositions = testPositions;
                                bestNewMinimumCostPositionsByOrigin = testMinimumCostPositionsByOrigin;
                                updatedSite = finalI;
                            }
                        } else {
                            if (currentMove - lastStatusChangeMoves.get(finalI) > openMemory) {
                                bestNewCost = testCost;
                                bestNewPositions = testPositions;
                                bestNewMinimumCostPositionsByOrigin = testMinimumCostPositionsByOrigin;
                                updatedSite = finalI;
                            }
                        }
                    }
                }
            }
        }
        if (improvedCycleBestCost && bestNewCost < bestCost) improvedBestCost = true;

        return Arrays.asList(bestNewCost, bestNewPositions, updatedSite, bestNewMinimumCostPositionsByOrigin, improvedCycleBestCost, improvedBestCost);
    }

    //Long term memory process. Input is current positions, short term memory, and number of moves that need to be made.
    public static List<Integer> longTermUpdatePositions(List<Integer> currentPositions, HashMap<Integer, Integer> lastStatusChangeIterations, Integer moveCount) {
        ArrayList<Integer> sortedSites = new ArrayList<>(); //sorted by last change iteration
        lastStatusChangeIterations.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEachOrdered(x -> sortedSites.add(x.getKey()));
        List<Integer> newPositions = new ArrayList<>(currentPositions);

        for (int i = 0; i < moveCount; i++) {
            Integer statusChangeSite = sortedSites.get(i);
            if (currentPositions.contains(statusChangeSite)) newPositions.remove(newPositions.indexOf(statusChangeSite));
            else newPositions.add(statusChangeSite);
        }

        /* For fixed number of sites only
        List<Integer> sitesToDelete = new ArrayList<>();
        List<Integer> sitesToAdd = new ArrayList<>();

        for (int i = 0; i < sortedSites.size(); i++) {
            Integer site = sortedSites.get(i);
            if (currentPositions.contains(site)) {
                sitesToDelete.add(site);
            } else {
                sitesToAdd.add(site);
            }
            if (sitesToAdd.size() >= moveCount && sitesToDelete.size() >= moveCount) {
                break;
            }
        }

        newPositions.removeAll(sitesToDelete.stream().limit(moveCount).collect(Collectors.toList()));
        newPositions.addAll(sitesToAdd.stream().limit(moveCount).collect(Collectors.toList()));
        */

        return newPositions;
    }

    //Updates short term memory after shifting a site from deletedSite to newSite
    public static List<Integer> updateShortTermMemory(List<Integer> previousLastChangeIterations, Integer deletedSite, Integer newSite) {
        previousLastChangeIterations.set(deletedSite, previousLastChangeIterations.get(deletedSite) + 1);
        return previousLastChangeIterations;
    }

    //Variation of totalCost to save compute resources. For initial sites.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static List<Object> initialCost(List<Integer> positions, List<List<String>> graphArray, List<List<String>> censusArray) {

        List<Integer> minimumCostPositionByOrigin = new ArrayList<>(Arrays.asList(-1)); //List of the closest positions (centers), in the order of origins/start population centers. We are IDing from 1.
        Map<Integer, List<Double>> minimumCostMap = new HashMap<>(); //Map from centre to (cases, minimum travel cost)
        List<Double> initialCasesCost = new ArrayList<>(Arrays.asList((double) 0, (double) 0));
        for (int i=0; i < positions.size(); ++i) {
            minimumCostMap.put(i, initialCasesCost);
        }
        int censusCaseIndex = -1;
        try {
            censusCaseIndex = findColumnIndex(censusHeadings, "Population");
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i=1; i < graphArray.size(); ++i) {
            int minimumCostPosition = 0;
            double minimumCostUnadjusted = Double.parseDouble(graphArray.get(i).get(positions.get(0))); //Closest center travel cost, not adjusted for population or cancer center scaling
            for (int j=1; j < positions.size(); ++j) {
                double currentCostUnadjusted = Double.parseDouble(graphArray.get(i).get(positions.get(j)));
                if (currentCostUnadjusted < minimumCostUnadjusted) {
                    minimumCostPosition = j;
                    minimumCostUnadjusted = currentCostUnadjusted;
                }
            }
            minimumCostPositionByOrigin.add(minimumCostPosition);
            double currentCaseCount = Double.valueOf(censusArray.get(i).get(censusCaseIndex));
            double centerCaseCount = minimumCostMap.get(minimumCostPosition).get(0) + currentCaseCount; //Add new case count to total case count at center
            double centerCost = minimumCostMap.get(minimumCostPosition).get(1) + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
            List<Double> minimumCostCases = new ArrayList<>(Arrays.asList(centerCaseCount,centerCost));
            minimumCostMap.put(minimumCostPosition, minimumCostCases);
        }
        return Arrays.asList(computeCost(minimumCostMap), minimumCostPositionByOrigin);
    }

    //Variation of totalCost to save compute resources. For subsequent sites.
    //Input movedPosition is index from [0, 1, 2, ..., n-1] for n centers that was shifted to a new site; newSite is actual indexed position of new site; oldMinimumCostPositionByOrigin is list of the lowest travel cost centers for each population center using previous iteration sites prior to shift.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static List<Object> addCost(List<Integer> positions, Integer newSite, List<List<String>> graphArray, List<List<String>> censusArray, List<Integer> oldMinimumCostPositionByOrigin) {
        List<Integer> minimumCostPositionByOrigin = new ArrayList<>(Arrays.asList(-1)); //List of the closest positions (centers), in the order of originating population centers. We are IDing from 1.
        Map<Integer, List<Double>> minimumCostMap = new HashMap<>(); //Map from centre to (cases, minimum travel cost)
        List<Double> initialCasesCost = new ArrayList<>(Arrays.asList((double) 0, (double) 0));
        for (int i=0; i < positions.size(); ++i) {
            minimumCostMap.put(i, initialCasesCost);
        }
        int censusCaseIndex = -1;
        try {
            censusCaseIndex = findColumnIndex(censusHeadings, "Population");
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i=1; i < graphArray.size(); ++i) {
            int minimumCostPosition = oldMinimumCostPositionByOrigin.get(i);
            double minimumCostUnadjusted = Double.parseDouble(graphArray.get(i).get(positions.get(minimumCostPosition)));
            double newPositionCost = Double.parseDouble(graphArray.get(i).get(newSite));
            if (newPositionCost < minimumCostUnadjusted) {
                minimumCostPosition = positions.size() - 1;
                minimumCostUnadjusted = newPositionCost;
            }
            minimumCostPositionByOrigin.add(minimumCostPosition);
            double currentCaseCount = Double.valueOf(censusArray.get(i).get(censusCaseIndex));
            double centerCaseCount = minimumCostMap.get(minimumCostPosition).get(0) + currentCaseCount; //Add new case count to total case count at center
            double centerCost = minimumCostMap.get(minimumCostPosition).get(1) + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
            List<Double> minimumCasesCost = new ArrayList<>(Arrays.asList(centerCaseCount,centerCost));
            minimumCostMap.put(minimumCostPosition, minimumCasesCost);
        }
        return Arrays.asList(computeCost(minimumCostMap), minimumCostPositionByOrigin);
    }

    //Variation of totalCost to save compute resources. For subsequent sites.
    //Input movedPosition is index from [0, 1, 2, ..., n-1] for n centers that was shifted to a new site; newSite is actual indexed position of new site; oldMinimumCostPositionByOrigin is list of the lowest travel cost centers for each population center using previous iteration sites prior to shift.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static List<Object> removeCost(List<Integer> positions, Integer removedPosition, List<List<String>> graphArray, List<List<String>> censusArray, List<Integer> oldMinimumCostPositionByOrigin) {
        List<Integer> minimumCostPositionByOrigin = new ArrayList<>(Arrays.asList(-1)); //List of the closest positions (centers), in the order of originating population centers. We are IDing from 1.
        Map<Integer, List<Double>> minimumCostMap = new HashMap<>(); //Map from centre to (cases, minimum travel cost)
        List<Double> initialCasesCost = new ArrayList<>(Arrays.asList((double) 0, (double) 0));
        for (int i=0; i < positions.size(); ++i) {
            minimumCostMap.put(i, initialCasesCost);
        }
        int censusCaseIndex = -1;
        try {
            censusCaseIndex = findColumnIndex(censusHeadings, "Population");
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i=1; i < graphArray.size(); ++i) {
            int minimumCostPosition = 0;
            double minimumCostUnadjusted;
            Integer oldMinimumCostPosition = oldMinimumCostPositionByOrigin.get(i);
            if (removedPosition == oldMinimumCostPosition) {
                minimumCostUnadjusted = Double.parseDouble(graphArray.get(i).get(positions.get(0))); //Closest center travel cost, not adjusted for population or cancer center scaling
                for (int j=1; j < positions.size(); ++j) {
                    double currentCostUnadjusted = Double.parseDouble(graphArray.get(i).get(positions.get(j)));
                    if (currentCostUnadjusted < minimumCostUnadjusted) {
                        minimumCostPosition = j;
                        minimumCostUnadjusted = currentCostUnadjusted;
                    }
                }
            } else {
                if (oldMinimumCostPosition > removedPosition) {
                    minimumCostPosition = oldMinimumCostPosition - 1;
                } else {
                    minimumCostPosition = oldMinimumCostPosition;
                }
                minimumCostUnadjusted = Double.parseDouble(graphArray.get(i).get(positions.get(minimumCostPosition)));
            }
            minimumCostPositionByOrigin.add(minimumCostPosition);
            double currentCaseCount = Double.valueOf(censusArray.get(i).get(censusCaseIndex));
            double centerCaseCount = minimumCostMap.get(minimumCostPosition).get(0) + currentCaseCount; //Add new case count to total case count at center
            double centerCost = minimumCostMap.get(minimumCostPosition).get(1) + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
            List<Double> minimumCasesCost = new ArrayList<>(Arrays.asList(centerCaseCount,centerCost));
            minimumCostMap.put(minimumCostPosition, minimumCasesCost);
        }
        return Arrays.asList(computeCost(minimumCostMap), minimumCostPositionByOrigin);
    }

    //Calculates cost from hashmap centre -> (cases, minimum travel cost)
    private static Double computeCost(Map<Integer, List<Double>> minimumCostMap) {
        double totalCost = 0;
        for (List<Double> centerCasesCost : minimumCostMap.values()) {
            double cases = centerCasesCost.get(0);
            double cost = centerCasesCost.get(1);
            totalCost += caseAdjustedCost(cases, cost);
        }
        return totalCost;
    }

    //Cost penalty function depending on amount of cases seen at cancer center. Examples include additive constant (extra cost to administer each center) or multiplicative piecewise to prefer bigger centers.
    private static  double caseAdjustedCost(double cases, double cost) {
        if (cases == 0) {
            System.out.println("A center had no cases in its catchment.");
            return 0;
        } else if (minimumCases ==0) {
            return cost;
        } else if (cases <= minimumCases) {
            //System.out.println("A center had less than " + minimumCases + " cases in its catchment.");
            return cost * Math.pow(10, 10 * (minimumCases - cases) / minimumCases );
        } else {
            return cost;
        }
    }

    //Pick random sublist
    public static <E> List<E> pickNRandomFromList(List<E> list, int n, Random r) {
        int length = list.size();
        for (int i = length - 1; i >= length - n; --i) {
            Collections.swap(list, i, r.nextInt(i + 1));
        }
        return list.subList(length - n, length);
    }

    //Parses CSV file into array
    public static List<List<String>> parseCSV(String fileLocation) {
        BufferedReader reader;
        String currentLine;
        List<List<String>> csvArray = new ArrayList<>();
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                csvArray.add(Arrays.asList(values));
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return csvArray;
    }

    //Finds index position of heading of interest from list of headings
    public static int findColumnIndex(List<String> l, String s) throws Exception {
        int finalCount = -1;
        for (int counter = 0; counter < l.size(); ++counter) {
            if (s.equals(l.get(counter))) {
                finalCount = counter;
                break;
            }
        }
        if (finalCount == -1) {
            throw new Exception(s+" was not found.");
        }
        return finalCount;
    }
}

