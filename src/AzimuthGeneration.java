import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AzimuthGeneration extends InputGenerator{
    static String outputFileLocation;
    public static double c = Math.PI / 180;

    public AzimuthGeneration(String potentialCenterFileLocation) throws Exception {
        super(potentialCenterFileLocation);
        outputFileLocation = potentialCenterFileLocation.replace("_potential_sites.csv", "_potential_azimuth.csv");
    }

    public static void main(String[] args) throws Exception {
        new AzimuthGeneration("M:\\Optimization Project Alpha\\alberta2021_potential_sites.csv");
        System.out.println("Computing haversine between candidate to candidate sites.");
        generateAzimuthArray(potentialCenterArray, potentialCenterLatIndex, potentialCenterLongIndex);
    }

    //Computes and writes azimuth array to outputFileLocation
    public static void generateAzimuthArray(List<List<String>> sitesArray, int latIndex, int longIndex) throws IOException {
        List<List<String>> azimuthArray = new ArrayList<>();
        List<String> nextRow = new ArrayList<>();

        nextRow.add("Origins (column) and destinations (row)");
        nextRow.addAll(getDestinationNames(sitesArray));
        azimuthArray.add(nextRow);

        for (int i = 1; i < sitesArray.size(); ++i) {
            nextRow = new ArrayList<>();
            nextRow.add(sitesArray.get(i).get(0));
            for (int j = 1; j < sitesArray.size(); ++j) {
                if (i == j) {
                    nextRow.add("N/A");
                    continue;
                }
                //Technically this produces the opposite (i.e. forward azimuth from column DAuid to row DAuid but offset by Pi does not change classification; maintaining for consistency of matrices)
                nextRow.add(String.valueOf(forwardAzimuth(Double.valueOf(sitesArray.get(i).get(latIndex)), Double.valueOf(sitesArray.get(i).get(longIndex)), Double.valueOf(sitesArray.get(j).get(latIndex)), Double.valueOf(sitesArray.get(j).get(longIndex)))));
            }
            azimuthArray.add(nextRow);
            if (i % 1 == 0) {
                System.out.println("Done row " + String.valueOf(i) + " of " + (sitesArray.size() - 1));
                FileUtils.writeCSV(outputFileLocation, azimuthArray);
                azimuthArray = new ArrayList<>();
            }
        }
    }

    //Finds initial bearing from (lat1,long1) to (lat2,long2). North is 0.
    public static double forwardAzimuth(double lat1, double long1, double lat2, double long2) {
        double degreeLat1 = lat1 * c;
        double degreeLong1 = long1 * c;
        double degreeLat2 = lat2 * c;
        double degreeLong2 = long2 * c;
        double forwardAzimuth = Math.atan2(Math.sin(degreeLong2-degreeLong1)*Math.cos(degreeLat2), Math.cos(degreeLat1)*Math.sin(degreeLat2)-Math.sin(degreeLat1)*Math.cos(degreeLat2)*Math.cos(degreeLong2-degreeLong1)) / c;
        if (forwardAzimuth < 0) {
            forwardAzimuth += 360;
        }
        return forwardAzimuth;
    }

}