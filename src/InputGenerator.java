import java.util.ArrayList;
import java.util.List;

public abstract class InputGenerator {
    static String censusFileLocation;
    static String permanentCenterFileLocation;
    static String potentialCenterFileLocation;

    static List<List<String>> censusArray;
    static List<List<String>> permanentCenterArray;
    static List<List<String>> potentialCenterArray;

    static int censusLatIndex;
    static int censusLongIndex;
    static int permanentCenterLatIndex;
    static int permanentCenterLongIndex;
    static int potentialCenterLatIndex;
    static int potentialCenterLongIndex;

    public InputGenerator(String potentialCenterFileLocation) throws Exception {
        this.potentialCenterFileLocation = potentialCenterFileLocation;
        this.potentialCenterArray = FileUtils.parseCSV(potentialCenterFileLocation);
        potentialCenterLatIndex = FileUtils.findColumnIndex(potentialCenterArray.get(0), "Latitude");
        potentialCenterLongIndex = FileUtils.findColumnIndex(potentialCenterArray.get(0), "Longitude");
    }

    public InputGenerator(String censusFileLocation, String potentialCenterFileLocation) throws Exception {
        this.censusFileLocation = censusFileLocation;
        this.censusArray = FileUtils.parseCSV(censusFileLocation);
        this.potentialCenterFileLocation = potentialCenterFileLocation;
        this.potentialCenterArray = FileUtils.parseCSV(potentialCenterFileLocation);
        censusLatIndex = FileUtils.findColumnIndex(censusArray.get(0), "Latitude");
        censusLongIndex = FileUtils.findColumnIndex(censusArray.get(0), "Longitude");
        potentialCenterLatIndex = FileUtils.findColumnIndex(potentialCenterArray.get(0), "Latitude");
        potentialCenterLongIndex = FileUtils.findColumnIndex(potentialCenterArray.get(0), "Longitude");
    }

    public InputGenerator(String censusFileLocation, String permanentCenterFileLocation, String potentialCenterFileLocation) throws Exception {
        this.censusFileLocation = censusFileLocation;
        this.censusArray = FileUtils.parseCSV(censusFileLocation);
        this.permanentCenterFileLocation = permanentCenterFileLocation;
        this.permanentCenterArray = FileUtils.parseCSV(permanentCenterFileLocation);
        this.potentialCenterFileLocation = potentialCenterFileLocation;
        this.potentialCenterArray = FileUtils.parseCSV(potentialCenterFileLocation);
        censusLatIndex = FileUtils.findColumnIndex(censusArray.get(0), "Latitude");
        censusLongIndex = FileUtils.findColumnIndex(censusArray.get(0), "Longitude");
        permanentCenterLatIndex = FileUtils.findColumnIndex(permanentCenterArray.get(0), "Latitude");
        permanentCenterLongIndex = FileUtils.findColumnIndex(permanentCenterArray.get(0), "Longitude");
        potentialCenterLatIndex = FileUtils.findColumnIndex(potentialCenterArray.get(0), "Latitude");
        potentialCenterLongIndex = FileUtils.findColumnIndex(potentialCenterArray.get(0), "Longitude");
    }

    //Generates list of DAuids
    public static List<String> getDestinationNames(List<List<String>> destinationCenterArray) {
        List<String> destinationNames = new ArrayList<>();
        for (int i = 1; i < destinationCenterArray.size(); ++i) destinationNames.add(destinationCenterArray.get(i).get(0));
        return destinationNames;
    }

}