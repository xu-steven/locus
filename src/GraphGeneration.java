import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class GraphGeneration extends InputGenerator{
    //Final file location
    static String outputFileLocation;

    //Starting origin (inclusive) and ending origin (not inclusive)
    private static int startOrigin = 2;
    private static int endOrigin = 18; //put 0 for censusArray.size();
    private static int startPort = 8080;

    //Thread count
    private static int threadCount = 5;
    private static ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    public GraphGeneration(String censusFileLocation, String potentialCenterFileLocation) throws Exception {
        super(censusFileLocation, potentialCenterFileLocation);
        outputFileLocation = censusFileLocation.replace(".csv", "_graph.csv");
    }

    public GraphGeneration(String censusFileLocation, String permanentCenterFileLocation, String potentialCenterFileLocation) throws Exception {
        super(censusFileLocation, permanentCenterFileLocation, potentialCenterFileLocation);
        outputFileLocation = censusFileLocation.replace(".csv", "_graph.csv");
    }

    public static void main(String[] args) throws Exception {
        new GraphGeneration("M:\\Optimization Project Alpha\\alberta2021_origins.csv", "M:\\Optimization Project Alpha\\alberta2021_potential_sites.csv");
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
        //Create a partitioned origin array
        if (endOrigin == 0) endOrigin = censusArray.size();
        int[] originsToGraph = IntStream.range(startOrigin, endOrigin).toArray();
        int[][] partitionedOrigins = MultithreadingUtils.orderedPartitionArray(originsToGraph, threadCount);

        //Generate graph partitions
        String[] partitionOutputLocations = new String[threadCount];
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                List<List<String>> outputArray = new ArrayList<>();
                List<String> nextRow = new ArrayList<>();

                //Partition output file location
                String partitionOutputLocation = outputFileLocation.replace(".csv", "_" + finalI + ".csv");
                partitionOutputLocations[finalI] = partitionOutputLocation;

                //Insert destination headings
                nextRow.add("Origins (column) and destinations (row)");
                nextRow.addAll(getDestinationNames(destinationArray));
                outputArray.add(nextRow);

                //Generate graph for each origin
                for (int j : partitionedOrigins[finalI]) {
                    nextRow = new ArrayList<>();
                    nextRow.add(censusArray.get(j).get(0));
                    for (int k = 1; k < destinationArray.size(); ++k) {
                        if (j == k) {
                            nextRow.add("0");
                            continue;
                        }
                        List<Double> routeStatistics = getOrsRouteStatistics(Double.valueOf(censusArray.get(j).get(censusLatIndex)), Double.valueOf(censusArray.get(j).get(censusLongIndex)), Double.valueOf(destinationArray.get(k).get(destinationLatIndex)), Double.valueOf(destinationArray.get(k).get(destinationLongIndex)), startPort + finalI);
                        if(routeStatistics.size() == 0) {
                            System.out.println("Failed to obtain driving distance from " + censusArray.get(j).get(0) + " to " + destinationArray.get(k).get(0));
                        } else {
                            nextRow.add(String.valueOf(routeStatistics.get(1)));
                        }
                        if (k % 100 == 0) {
                            System.out.println("Done column " + String.valueOf(k) + " in row " + String.valueOf(j) + ". There are a total of " + (censusArray.size() - 1) + " DAs.");
                        }
                    }
                    outputArray.add(nextRow);
                    if (j % 1 == 0) {
                        System.out.println("Done row " + String.valueOf(j) + " of " + (censusArray.size() - 1));
                        try {
                            FileUtils.writeCSV(partitionOutputLocation, outputArray);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        outputArray = new ArrayList<>();
                    }
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (Exception e) {
            throw new AssertionError("Latch await exception");
        }

        //Merge graph partitions to outputFileLocation
        FileUtils.mergeCSV(outputFileLocation, partitionOutputLocations);
    }

    //Takes latitude and longitude of origin and destination, produces list of driving distance (in km) and time (in hours). Requires ORS backend.
    public static List<Double> getOrsRouteStatistics(Double lat0, Double long0, Double lat1, Double long1, int port) {
        List<Double> orsRouteStats = new ArrayList<>();
        for (int i=0; i<100; ++i) {
            try {
                URL url = new URL("http://localhost:" + String.valueOf(port) + "/ors/v2/directions/driving-car?start=" + long0 + "," + lat0 + "&end=" + long1 + "," + lat1);
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