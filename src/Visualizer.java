import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Visualizer {
    private static List<List<String>> graphArray = FileUtils.parseCSV("M:\\Optimization Project Alpha\\alberta2021_graph.csv");
    private static List<List<String>> censusArray = FileUtils.parseCSV("M:\\Optimization Project Alpha\\alberta2021_origins.csv");
    private static double minimumCases = 10000;

    //Manually create overlay
    public static void main(String[] args) {
        List<Integer> sites = replacePermanents(Arrays.asList(206, 1222, 5181));
        List<Integer> level2 = replacePermanents(Arrays.asList(5346, 1985));
        List<Integer> level3 = replacePermanents(Arrays.asList(1985, 5346));
        sites = sites.stream().map(x -> x + 1).collect(Collectors.toList());
        level2 = level2.stream().map(x -> x + 1).collect(Collectors.toList());
        level3 = level3.stream().map(x -> x + 1).collect(Collectors.toList());
        //System.out.println("Cost " + totalCost(sites, graphArray, censusArray));
        double costSites = totalCost(sites, graphArray, censusArray, 10000) * 1.0;
        double costSites2 = totalCost(level2, graphArray, censusArray, 1000000) * 0.2;
        double costSites3 = totalCost(level3, graphArray, censusArray, 2000000) * 0.1;
        System.out.println("Cost " + (costSites + costSites2 + costSites3) + " with " + Arrays.asList(costSites, costSites2, costSites3));
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
    public static Double totalCost(List<Integer> positions, List<List<String>> graphArray, List<List<String>> censusArray, double minimumCases) {
        Map<Integer, List<Double>> minimumCostMap = new HashMap<>(); //Map from centre to (cases, minimum travel cost)
        List<Double> initialCasesCost = new ArrayList<>(Arrays.asList((double) 0, (double) 0));
        for (int i=0; i < positions.size(); ++i) {
            minimumCostMap.put(i, initialCasesCost);
        }
        int censusCaseIndex = -1;
        try {
            censusCaseIndex = FileUtils.findColumnIndex(censusArray.get(0), "Cases 2000");
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
        return computeCost(minimumCostMap, minimumCases);
    }

    //Calculates cost from hashmap centre -> (cases, minimum travel cost)
    private static Double computeCost(Map<Integer, List<Double>> minimumCostMap, double minimumCases) {
        double totalCost = 0;
        for (List<Double> centerCasesCost : minimumCostMap.values()) {
            double cases = centerCasesCost.get(0);
            double cost = centerCasesCost.get(1);
            totalCost += caseAdjustedCost(cases, cost, minimumCases);
        }
        return totalCost;
    }

    //Cost penalty function depending on amount of cases seen at cancer center. Examples include additive constant (extra cost to administer each center) or multiplicative piecewise to prefer bigger centers.
    private static double caseAdjustedCost(double cases, double cost, double minimumCases) {
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

    //Replace permanent centers with corresponding center, developmental use
    private static List<Integer> replacePermanents(List<Integer> originalSites) {
        for (int i = 0; i < originalSites.size(); i++) {
            if (originalSites.get(i) == 5344) {
                originalSites.set(i, 2640);
            } else if (originalSites.get(i) == 5345) {
                originalSites.set(i, 3062);
            } else if (originalSites.get(i) == 5346) {
                originalSites.set(i, 3253);
            } else if (originalSites.get(i) == 5347) {
                originalSites.set(i, 5294);
            } else if (originalSites.get(i) >= 5291) {
                Integer newSiteNumber = originalSites.get(i) + 4;
                originalSites.set(i, newSiteNumber);
            } else if (originalSites.get(i) >= 3251) {
                Integer newSiteNumber = originalSites.get(i) + 3;
                originalSites.set(i, newSiteNumber);
            } else if (originalSites.get(i) >= 3061) {
                Integer newSiteNumber = originalSites.get(i) + 2;
                originalSites.set(i, newSiteNumber);
            } else if (originalSites.get(i) >= 2640) {
                Integer newSiteNumber = originalSites.get(i) + 1;
                originalSites.set(i, newSiteNumber);
            }
        }
        return originalSites;
    }
}