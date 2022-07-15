import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GraphGeneration extends InputGenerator{
    static String outputFileLocation;

    public GraphGeneration(String censusFileLocation, String potentialCenterFileLocation) throws Exception {
        super(censusFileLocation, potentialCenterFileLocation);
        outputFileLocation = censusFileLocation.replace(".csv", "_graph.csv");
    }

    public GraphGeneration(String censusFileLocation, String permanentCenterFileLocation, String potentialCenterFileLocation) throws Exception {
        super(censusFileLocation, permanentCenterFileLocation, potentialCenterFileLocation);
        outputFileLocation = censusFileLocation.replace(".csv", "_graph.csv");
    }

    public static void main(String[] args) throws Exception {
        new GraphGeneration("C:\\Projects\\Optimization Project\\alberta2016_origins.csv", "C:\\Projects\\Optimization Project\\alberta2016_permanent_sites.csv", "C:\\Projects\\Optimization Project\\alberta2016_potential_sites.csv");
        if (permanentCenterArray != null) {
            System.out.println("Generating graph from origins to permanent centers.");
            generateGraph(permanentCenterArray, permanentCenterLatIndex, permanentCenterLongIndex);
        } else {
            System.out.println("No permanent centers specified.");
        }
        System.out.println("Generating graph from origins to candidate sites.");
        generateGraph(potentialCenterArray, potentialCenterLatIndex, potentialCenterLongIndex);
    }

    //Creates graph based on pair origins and destinations
    public static void generateGraph(List<List<String>> destinationArray, int destinationLatIndex, int destinationLongIndex) throws IOException {
        List<List<String>> outputArray = new ArrayList<>();
        List<String> nextRow = new ArrayList<>();

        nextRow.add("Origins (column) and destinations (row)");
        nextRow.addAll(getDestinationNames(destinationArray));
        outputArray.add(nextRow);

        for (int i = 1; i < censusArray.size(); ++i) {
            nextRow = new ArrayList<>();
            nextRow.add(censusArray.get(i).get(0));
            for (int j = 1; j < destinationArray.size(); ++j) {
                if (i == j) {
                    nextRow.add("0");
                    continue;
                }
                List<Double> routeStatistics = getOrsRouteStatistics(Double.valueOf(censusArray.get(i).get(censusLatIndex)), Double.valueOf(censusArray.get(i).get(censusLongIndex)), Double.valueOf(destinationArray.get(j).get(destinationLatIndex)), Double.valueOf(destinationArray.get(j).get(destinationLongIndex)));
                if(routeStatistics.size() == 0) {
                    System.out.println("Failed to obtain driving distance from " + censusArray.get(i).get(0) + " to " + destinationArray.get(j).get(0));
                } else {
                    nextRow.add(String.valueOf(routeStatistics.get(1)));
                }
                if (j % 100 == 0) {
                    System.out.println("Done column " + String.valueOf(j) + " in row " + String.valueOf(i) + ". There are a total of " + censusArray.size() + " DAs.");
                }
            }
            outputArray.add(nextRow);
            if (i % 1 == 0) {
                System.out.println("Done row " + String.valueOf(i) + " of " + censusArray.size());
                FileUtils.writeCSV(outputFileLocation, outputArray);
                outputArray = new ArrayList<>();
            }
        }
    }

    //Takes latitude and longitude of origin and destination, produces list of driving distance (in km) and time (in hours). Requires ORS backend.
    public static List<Double> getOrsRouteStatistics(Double lat0, Double long0, Double lat1, Double long1) {
        List<Double> orsRouteStats = new ArrayList<>();
        for (int i=0; i<100; ++i) {
            try {
                URL url = new URL("http://localhost:8080/ors/v2/directions/driving-car?start=" + long0 + "," + lat0 + "&end=" + long1 + "," + lat1);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.connect();
                if (conn.getResponseCode() == 200) {
                    Scanner scan = new Scanner(url.openStream());
                    while (scan.hasNext()) {
                        String line = scan.nextLine();
                        Any jsonObject = JsonIterator.deserialize(line);
                        Any orsDrivingDistance = jsonObject.get("features").get(0).get("properties").get("segments").get(0).get("distance");
                        Any orsDrivingTime = jsonObject.get("features").get(0).get("properties").get("segments").get(0).get("duration");
                        orsRouteStats.add(Double.valueOf(String.valueOf(orsDrivingDistance)) / 1000);
                        orsRouteStats.add(Double.valueOf(String.valueOf(orsDrivingTime)) / 3600);
                    }
                }
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                    System.out.println("Failed to connect to ORS. Trying again.");
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
                continue;
            }
            break;
        }
        return orsRouteStats;
    }

}