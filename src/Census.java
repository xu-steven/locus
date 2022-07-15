import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Census {
    public static List<String> orderedSubsetNames; //same order as population by subset
    public static List<List<Double>> populationBySubset;

    public Census(String censusFileLocation) {
        this.orderedSubsetNames = FileUtils.getCSVHeadings(censusFileLocation);
        this.populationBySubset = FileUtils.getInnerDoubleArrayFromCSV(censusFileLocation);
    }

    public static void main(String[] args) throws IOException {
        String censusFileLocation = "M:\\Optimization Project\\demographic projections\\test_alberta2016_census.csv";
        String incidenceFileLocation = "M:\\Optimization Project\\demographic projections\\test_alberta2016_incidence_density_rate.csv";
        String outputFileLocation = censusFileLocation.replace(".csv", "_cases_by_origin.csv");

        new Census(censusFileLocation);
        List<String> originNames = FileUtils.getCSVFirstColumn(censusFileLocation);
        List<String> incidenceDensityRateSubsetNames = FileUtils.getCSVFirstColumn(incidenceFileLocation);
        if (!equalNonemptyLists(incidenceDensityRateSubsetNames, orderedSubsetNames)) {
            System.out.println("Subset name lists were empty or unequal");
            return;
        }
        List<Double> projectedCasesByOrigin = projectCases(incidenceFileLocation);

        List<List<String>> outputArray = new ArrayList<>();
        outputArray.add(Arrays.asList("DAuid", "Cases"));
        for (int i = 0; i < originNames.size(); i++){
            outputArray.add(Arrays.asList(originNames.get(i), String.valueOf(projectedCasesByOrigin.get(i))));
        }

        FileUtils.writeCSV(outputFileLocation, outputArray);
    }

    /*
    public static List<List<Double>> populationProjection(String fertilityFileLocation, String mortalityFileLocation, String migrationFileLocation) {

    }

     */

    //Projected cases by origin based on population subsets (e.g. pyramid) at each origin and incidence density rate for each subset
    public static List<Double> projectCases(String incidenceFileLocation) {
        List<Double> orderedIncidenceDensityRate = getIncidenceDensityRate(incidenceFileLocation);
        List<Double> projectedCasesByOrigin = new ArrayList<>();
        for (int i = 0; i < populationBySubset.size(); i++) {
            Double currentOriginCases = 0.0;
            for (int j = 0; j < orderedSubsetNames.size(); j++) {
                currentOriginCases += populationBySubset.get(i).get(j) * orderedIncidenceDensityRate.get(j);
            }
            projectedCasesByOrigin.add(currentOriginCases);
        }
        return projectedCasesByOrigin;
    }

    //Reads incidence file which should contain incidence density rate for each population subset
    public static List<Double> getIncidenceDensityRate(String incidenceFileLocation) {
        BufferedReader reader;
        String currentLine;
        List<Double> orderedIncidenceDensityRate = Arrays.asList(new Double[orderedSubsetNames.size()]);
        try {
            reader = new BufferedReader(new FileReader(incidenceFileLocation));
            String[] headings = reader.readLine().split(",");
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                int orderedSubsetPosition = orderedSubsetNames.indexOf(values[0]);
                orderedIncidenceDensityRate.set(orderedSubsetPosition, Double.valueOf(values[1]));
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return orderedIncidenceDensityRate;
    }

    //Compares two lists and ensures that they are the same (order irrelevant) and nonempty
    public static boolean equalNonemptyLists(List<String> l1, List<String> l2){
        if (l1 == null || l1.size() == 0 || l2 == null || l2.size() == 0){
            System.out.println("Unable to identify subset names");
            return false;
        }

        //Duplicates to not affect original lists
        l1 = new ArrayList<>(l1);
        l2 = new ArrayList<>(l2);

        Collections.sort(l1);
        Collections.sort(l2);
        return l1.equals(l2);
    }
}