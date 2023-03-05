import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HaversineGeneration extends InputGenerator{
    static String outputFileLocation;

    public HaversineGeneration(String potentialCenterFileLocation) throws Exception {
        super(potentialCenterFileLocation);
        outputFileLocation = potentialCenterFileLocation.replace("_potential_sites.csv", "_potential_haversine.csv");
    }

    public static void main(String[] args) throws Exception {
        new HaversineGeneration("M:\\Optimization Project Alpha\\alberta2021_potential_sites.csv");
        generateHaversineArray(potentialCenterArray, potentialCenterLatIndex, potentialCenterLongIndex);
    }

    //Creates haversine array based on origins and destinations
    public static void generateHaversineArray(List<List<String>> sitesArray, int latIndex, int longIndex) throws IOException {
        List<List<String>> haversineArray = new ArrayList<>();
        List<String> nextRow = new ArrayList<>();

        nextRow.add("Origins (column) and destinations (row)");
        nextRow.addAll(getDestinationNames(sitesArray));
        haversineArray.add(nextRow);

        for (int i = 1; i < sitesArray.size(); ++i) {
            nextRow = new ArrayList<>();
            nextRow.add(sitesArray.get(i).get(0));
            for (int j = 1; j < sitesArray.size(); ++j) {
                //Technically this produces the opposite (i.e. forward azimuth from column DAuid to row DAuid but offset by Pi does not change classification; maintaining for consistency of matrices)
                nextRow.add(String.valueOf(haversineDist(Double.valueOf(sitesArray.get(i).get(latIndex)), Double.valueOf(sitesArray.get(i).get(longIndex)), Double.valueOf(sitesArray.get(j).get(latIndex)), Double.valueOf(sitesArray.get(j).get(longIndex)))));
            }
            haversineArray.add(nextRow);
            if (i % 1 == 0) {
                System.out.println("Done row " + String.valueOf(i) + " of " + (sitesArray.size() - 1));
                FileUtils.writeCSV(outputFileLocation, haversineArray);
                haversineArray = new ArrayList<>();
            }
        }
    }

    //Finds haversine distance between (lat1,long1) to (lat2,long2).
    public static double haversineDist(Double lat1, Double long1, Double lat2, Double long2) {
        var c = 0.01745329252;
        var angle = 0.5-Math.cos((lat1-lat2)*c)/2 + Math.cos(lat1*c)*Math.cos(lat2*c)*(1-Math.cos((long1-long2)*c))/2;
        return 12742 * Math.asin(Math.sqrt(angle));
    }
}