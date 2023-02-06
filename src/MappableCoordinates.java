import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import static java.lang.Math.min;

public class MappableCoordinates {
    static String censusFileLocation = "M:\\Optimization Project Alpha\\alberta2021_mappable.csv";
    static List<List<String>> censusArray = parseCSV(censusFileLocation);
    public static List<String> censusHeadings = censusArray.get(0);

    public static List<List<String>> newArray = new ArrayList<>();
    public static List<String> nextRow = new ArrayList<>();

    public static void main(String[] args) {
        nextRow.add("Mappable latitude");
        nextRow.add("Mappable longitude");
        newArray.add(nextRow);
        int censusLatIndex = -1;
        int censusLongIndex = -1;
        try {
            censusLatIndex = findColumnIndex(censusHeadings, "Latitude");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            censusLongIndex = findColumnIndex(censusHeadings, "Longitude");
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<Double> knownMappableCoordinates = findMappableCoordinates(censusArray, censusLatIndex, censusLongIndex);
        System.out.println("Known mappable coordinates: " + knownMappableCoordinates);
        List<String> unmappableDauids = findUnmappableDauids(censusArray, knownMappableCoordinates, censusLatIndex, censusLongIndex);
        System.out.println("Unmappable DAuids in list: " + unmappableDauids); //Prints a list of unmappable Dauids
    }

    //Generates list of unmappable DAuids
    private static List<String> findUnmappableDauids(List<List<String>> censusArray, List<Double> knownMappableCoordinates, Integer censusLatIndex, Integer censusLongIndex) {
        List<String> unmappableDAuids = new ArrayList<>();
        for (int i = 1; i < censusArray.size(); i++) {
            List<Double> testRoute = getOrsRouteStatistics(Double.parseDouble(censusArray.get(i).get(censusLatIndex)), Double.parseDouble(censusArray.get(i).get(censusLongIndex)), knownMappableCoordinates.get(0), knownMappableCoordinates.get(1));
            if (testRoute.size() == 0) {
                unmappableDAuids.add(censusArray.get(i).get(0));
            }
            if (i % 100 == 0) {
                System.out.println("Done checking " + String.valueOf(i));
            }
        }
        return unmappableDAuids;
    }

    //Test pairwise coordinates until mappable pair is found. Output is the second of the two mappable coordinates.
    private static List<Double> findMappableCoordinates(List<List<String>> censusArray, int censusLatIndex, int censusLongIndex) {
        List<Double> mappableCoordinates = new ArrayList<>();
        Integer pairCount = (int) ((censusArray.size() - 1) / 2);
        for (int i = 1; i < pairCount; i++) {
            List<Double> testRoute = getOrsRouteStatistics(Double.parseDouble(censusArray.get(2*i - 1).get(censusLatIndex)), Double.parseDouble(censusArray.get(2*i - 1).get(censusLongIndex)), Double.parseDouble(censusArray.get(2*i).get(censusLatIndex)), Double.parseDouble(censusArray.get(2*i).get(censusLongIndex)));
            if (testRoute.size() != 0) {
                mappableCoordinates.add(Double.parseDouble(censusArray.get(2*i).get(censusLatIndex)));
                mappableCoordinates.add(Double.parseDouble(censusArray.get(2*i).get(censusLongIndex)));
                break;
            }
        }
        return mappableCoordinates;
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
