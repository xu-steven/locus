import org.paukov.combinatorics3.Generator;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ExhaustiveSearch {
    static double minimumCases;
    static List<List<String>> censusArray;
    static List<String> censusHeadings;
    static List<List<String>> graphArray;
    static int potentialSitesCount;

    //Development specific
    static Integer updateFrequency = 1000; //frequency of updates in ms // development only
    static Integer maxNewCenters = 2; //Do not recommend possible sites choose maxNewCenters to exceed 500m (around 100 hours). Only used for developmental testing of class.

    //Constructs an exhaustive search with no previously defined parameters
    public ExhaustiveSearch() {
        String censusFileLocation = "M:\\Optimization Project\\test_alberta2016.csv";
        String graphLocation = censusFileLocation.replace(".csv", "_graph.csv");
        this.minimumCases = 10000;
        this.censusArray = FileUtils.parseCSV(censusFileLocation);
        this.censusHeadings = censusArray.get(0);
        this.graphArray = FileUtils.parseCSV(graphLocation);
        this.potentialSitesCount = graphArray.get(0).size() - 1;
    }

    //Constructs an exhaustive search given known parameters
    public ExhaustiveSearch(double minimumCases, List<List<String>> censusArray, List<List<String>> graphArray) {
        this.minimumCases = minimumCases;
        this.censusArray = censusArray;
        this.censusHeadings = censusArray.get(0);
        this.graphArray = graphArray;
        this.potentialSitesCount = graphArray.get(0).size() - 1;
    }

    public static void setUpdateFrequency(Integer updateFrequency) {
        ExhaustiveSearch.updateFrequency = updateFrequency;
    }

    public static void main(String[] args) {
        new ExhaustiveSearch();
        double minimumCost = Double.POSITIVE_INFINITY;
        List<Integer> minimumPositions = new ArrayList<>();
        for (int i=1; i <= maxNewCenters; ++i) {
            List<Object> sol = optimizeNCenters(i);
            double currentMinimumCost = (double) sol.get(0);
            if (currentMinimumCost < minimumCost) {
                minimumCost = currentMinimumCost;
                minimumPositions = (List<Integer>) sol.get(1);
            } else {
                break;
            }
        }
        System.out.println("Minimum cost " + minimumCost + " at positions " + minimumPositions);
    }

    //Optimize with n centers
    public static List<Object> optimizeNCenters (Integer n) {
        long timer = System.currentTimeMillis(); // development only
        List<Integer> minimumPositions = new ArrayList<>();
        double minimumCost = Double.POSITIVE_INFINITY;
        List<Integer> potentialSites = IntStream.rangeClosed(1, potentialSitesCount).boxed().collect(Collectors.toList());
        for (List positions : Generator.combination(potentialSites)
                .simple(n)) {
            double currentCost = totalCost(positions, graphArray, censusArray);
            if (currentCost < minimumCost) {
                minimumPositions = positions;
                minimumCost = currentCost;
            }
            long elapsedTime = System.currentTimeMillis()  - timer; //development only
            if (elapsedTime > updateFrequency) { //development only
                System.out.println(positions); // development only
                timer = System.currentTimeMillis(); // development only
            } // development only
        }
        List<Object> output = new ArrayList<>(Arrays.asList(minimumCost, minimumPositions)); //contains two elements: Double minimum cost and List<Integer> minimum positions.
        return output;
    }

    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static Double totalCost(List<Integer> positions, List<List<String>> graphArray, List<List<String>> censusArray) {
        Map <Integer, List<Double>> minimumCostMap = new HashMap<>(); //Map from centre to (cases, minimum travel cost)
        List<Double> initialCasesCost = new ArrayList<>(Arrays.asList((double) 0, (double) 0));
        for (int i=0; i < positions.size(); ++i) {
            minimumCostMap.put(i, initialCasesCost);
        }
        int censusCaseIndex = -1;
        try {
            censusCaseIndex = FileUtils.findColumnIndex(censusHeadings, "Population");
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i=1; i < graphArray.size(); ++i) {
            int minimumCostPosition = 0;
            double minimumCostUnadjusted = Double.parseDouble(graphArray.get(i).get(positions.get(0))); //Not adjusted for population or cancer center scaling
            for (int j=1; j < positions.size(); ++j) {
                double currentCostUnadjusted = Double.parseDouble(graphArray.get(i).get(positions.get(j)));
                if (currentCostUnadjusted < minimumCostUnadjusted) {
                    minimumCostPosition = j;
                    minimumCostUnadjusted = currentCostUnadjusted;
                }
            }
            double currentCaseCount = Double.valueOf(censusArray.get(i).get(censusCaseIndex));
            double centerCaseCount = minimumCostMap.get(minimumCostPosition).get(0) + currentCaseCount; //Add new case count to total case count at center
            double centerCost = minimumCostMap.get(minimumCostPosition).get(1) + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
            List<Double> minimumCasesCost = new ArrayList<>(Arrays.asList(centerCaseCount,centerCost));
            minimumCostMap.put(minimumCostPosition, minimumCasesCost);
        }
        return computeCost(minimumCostMap);
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


}
