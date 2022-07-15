import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class AppendHaversineDistance {
    static String censusFileLocation = "M:\\Census 2016\\csv\\census2016_mapping.csv";
    static String centreFileLocation = "M:\\Census 2016\\csv\\cancercenters.csv";
    static String outputFileLocation = censusFileLocation.replace(".csv","_hd.csv");

    static List<List<String>> censusArray = parseCSV(censusFileLocation);
    static List<String> censusHeadings = censusArray.get(0);
    static List<List<String>> centreArray = parseCSV(centreFileLocation);
    static List<String> centreHeadings = centreArray.get(0);
    static List<List<String>> newArray = new ArrayList<>();
    static List<String> nextRow = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        try {
            File outputFile = new File(outputFileLocation);
            outputFile.createNewFile();
        } catch (IOException ioException){
            ioException.printStackTrace();
        }
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
        int centreLatIndex = -1;
        int centreLongIndex = -1;
        try {
            centreLatIndex = findColumnIndex(centreHeadings, "Latitude");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            centreLongIndex = findColumnIndex(centreHeadings, "Longitude");
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i=1; i<centreArray.size(); ++i) {
            nextRow.add("hd_"+centreArray.get(i).get(0));
        }
        newArray.add(nextRow);
        for (int i=1; i<censusArray.size(); ++i) {
            nextRow = new ArrayList<>();
            double latitude = Double.parseDouble(censusArray.get(i).get(censusLatIndex));
            double longitude = Double.parseDouble(censusArray.get(i).get(censusLongIndex));
            for (int j=1; j<centreArray.size(); ++j) {
                double haversineDistance = haversineDist(latitude, longitude, Double.parseDouble(centreArray.get(j).get(centreLatIndex)), Double.parseDouble(centreArray.get(j).get(centreLongIndex)));
                nextRow.add(String.valueOf(haversineDistance));
            }
            newArray.add(nextRow);
        }
        addCsvColumn(outputFileLocation, newArray, censusFileLocation);
    }

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

    public static double haversineDist(Double originLat, Double originLong, Double targetLat, Double targetLong) {
        var c = 0.01745329252;
        var angle = 0.5-Math.cos((originLat-targetLat)*c)/2 + Math.cos(originLat*c)*Math.cos(targetLat*c)*(1-Math.cos((originLong-targetLong)*c))/2;
        return 12742 * Math.asin(Math.sqrt(angle));
    }

    public static void addCsvColumn(String outputFileLocation, List<List<String>> newArray, String originalFileLocation) throws IOException {
        StringBuilder output;
        BufferedReader reader;
        FileWriter writer = new FileWriter(outputFileLocation, false);
        String currentLine;
        try{
            reader = new BufferedReader(new FileReader(originalFileLocation));
            int i=0;
            int newArrayWidth = newArray.get(0).size();
            while ((currentLine = reader.readLine()) != null) {
                output = new StringBuilder(currentLine);
                for (int j=0; j<newArrayWidth; ++j) {
                    output.append(",").append(newArray.get(i).get(j));
                }
                output.append("\n");
                writer.write(output.toString());
                i++;
            }
            reader.close();
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
