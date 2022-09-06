import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Visualizer {
    private static List<List<String>> graphArray = FileUtils.parseCSV("M:\\Optimization Project\\alberta2016_graph.csv");
    private static List<List<String>> censusArray = FileUtils.parseCSV("M:\\Optimization Project\\alberta2016_origins.csv");
    private static double minimumCases = 10000;

    //Manually create overlay
    public static void main(String[] args) {
        List<Integer> sites = Arrays.asList(643, 3173, 2643, 3127, 5318, 3174);
        sites = sites.stream().map(x -> x + 1).collect(Collectors.toList());
        System.out.println("Cost " + totalCost(sites, graphArray, censusArray));
        //System.out.println("Cost " + (totalCost(sites, graphArray, censusArray) * 0.7 + totalCost(Arrays.asList(2640, 3062, 3253, 5294, 201, 1236), graphArray, censusArray) * 0.2 + totalCost(Arrays.asList(2640, 3062, 3253, 5294, 201, 1236), graphArray, censusArray) * 0.1));
        List<List<String>> candidateSites = FileUtils.parseCSV("M:\\Optimization Project\\alberta2016_origins.csv");
        String outputDirectory = "C:\\Projects\\Optimization Project\\output\\";
        String outputName = "alberta2016_optimized_sites.csv";
        createMapOverlay(sites, candidateSites, outputDirectory, outputName);
    }

    //Creates CSV file that can be fed to Google maps API to visualize sites of interest
    public static void createMapOverlay(List<Integer> sites, List<List<String>> candidateSites, String outputDirectory, String outputName) {
        List<List<String>> output = new ArrayList<>();
        String outputFileLocation = outputDirectory + outputName;
        Path outputFilePath = Paths.get(outputFileLocation);
        Boolean outputFilePathExists = Files.exists(outputFilePath);
        Integer counter = 1;
        while (outputFilePathExists) {
            counter++;
            outputFileLocation = outputDirectory + outputName.replace(".csv", "_" + String.valueOf(counter) + ".csv");
            outputFilePath = Paths.get(outputFileLocation);
            outputFilePathExists = Files.exists(outputFilePath);
        }
        output.add(candidateSites.get(0));
        for (int i = 0; i < sites.size(); i++) {
            output.add(candidateSites.get(sites.get(i)));
        }
        try {
            FileUtils.writeCSV(outputFileLocation, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Below is unchanged from ExhaustiveSearch.
    //Cost function of configuration with given cancer center positions, graph, expected case count. Technically does not optimize for case where one permits travel to further cancer center to lower cost.
    public static Double totalCost(List<Integer> positions, List<List<String>> graphArray, List<List<String>> censusArray) {
        Map<Integer, List<Double>> minimumCostMap = new HashMap<>(); //Map from centre to (cases, minimum travel cost)
        List<Double> initialCasesCost = new ArrayList<>(Arrays.asList((double) 0, (double) 0));
        for (int i=0; i < positions.size(); ++i) {
            minimumCostMap.put(i, initialCasesCost);
        }
        int censusCaseIndex = -1;
        try {
            censusCaseIndex = FileUtils.findColumnIndex(censusArray.get(0), "Population");
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
            double currentCaseCount = Double.valueOf(censusArray.get(i).get(censusCaseIndex));
            double centerCaseCount = minimumCostMap.get(minimumCostPosition).get(0) + currentCaseCount; //Add new case count to total case count at center
            double centerCost = minimumCostMap.get(minimumCostPosition).get(1) + (minimumCostUnadjusted * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
            List<Double> minimumCasesCost = new ArrayList<>(Arrays.asList(centerCaseCount,centerCost));
            minimumCostMap.put(minimumCostPosition, minimumCasesCost);
        }
        System.out.println(minimumCostMap); //development only
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
    private static double caseAdjustedCost(double cases, double cost) {
        if (cases == 0) {
            //System.out.println("A center had no cases in its catchment.");
            return 0;
        } else if (minimumCases == 0) {
            return cost;
        } else if (cases <= minimumCases) {
            //System.out.println("A center had less than " + minimumCases + " cases in its catchment.");
            return cost + 100 * (minimumCases - cases);
        } else {
            return cost;
        }
    }
}