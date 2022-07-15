import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FileUtils {
    private FileUtils(){}

    //Delete first row and column (identifiers) of census CSV to form census array
    public static List<List<Double>> getInnerDoubleArrayFromCSV(String fileLocation) {
        System.out.println("Importing " + fileLocation + " into memory.");
        BufferedReader reader;
        String currentLine;
        List<List<Double>> innerDoubleArray = new ArrayList<>();
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            int counter = 0;
            while ((currentLine = reader.readLine()) != null) {
                counter += 1;
                if (counter == 1) continue;
                String[] values = currentLine.split(",");
                List<Double> doubleValues = new ArrayList<>();
                for (int i = 1; i < values.length; i++) {
                    doubleValues.add(Double.valueOf(values[i]));
                }
                innerDoubleArray.add(doubleValues);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Done importing " + fileLocation);
        return innerDoubleArray;
    }

    //Variant of getInnerDoubleArrayFromCSV that reclassifies N/A as -1 to denote identical origin and site
    public static List<List<Double>> getInnerAzimuthArrayFromCSV(String fileLocation) {
        System.out.println("Importing " + fileLocation + " into memory.");
        BufferedReader reader;
        String currentLine;
        List<List<Double>> innerDoubleArray = new ArrayList<>();
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            int counter = 0;
            while ((currentLine = reader.readLine()) != null) {
                counter += 1;
                if (counter == 1) continue;
                String[] values = currentLine.split(",");
                List<Double> doubleValues = new ArrayList<>();
                for (int i = 1; i < values.length; i++) {
                    if (values[i].equals("N/A")) {
                        doubleValues.add(-1.0);
                    } else {
                        doubleValues.add(Double.valueOf(values[i]));
                    }
                }
                innerDoubleArray.add(doubleValues);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Done importing " + fileLocation);
        return innerDoubleArray;
    }

    //Extract case counts from CSV with column heading
    public static List<Double> getCaseCountsFromCSV(String fileLocation, String caseCountColumnHeading) {
        System.out.println("Importing case counts into memory.");
        BufferedReader reader;
        String currentLine;
        List<Double> caseCounts = new ArrayList<>();
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            int counter = 0;
            int caseCountHeadingIndex = -1;
            while ((currentLine = reader.readLine()) != null) {
                counter += 1;
                String[] values = currentLine.split(",");
                if (counter == 1) {
                    caseCountHeadingIndex = findColumnIndex(Arrays.asList(values), caseCountColumnHeading);
                } else {
                    caseCounts.add(Double.valueOf(values[caseCountHeadingIndex]));
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Done importing case counts to memory.");
        return caseCounts;
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

    //Gets first row less the first element of CSV
    public static List<String> getCSVHeadings(String fileLocation) {
        List<String> headings = null;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileLocation));
            headings = new ArrayList<>(Arrays.asList(reader.readLine().split(",")));
            headings.remove(0);
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return headings;
    }

    //Gets first column less the first element of CSV
    public static List<String> getCSVFirstColumn(String fileLocation) {
        String currentLine;
        List<String> firstColumn = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileLocation));
            reader.readLine();
            while ((currentLine = reader.readLine()) != null) {
                firstColumn.add(currentLine.split(",")[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return firstColumn;
    }

    //Writes array to CSV file
    public static void writeCSV(String outputFileLocation, List<List<String>> array) throws IOException {
        StringBuilder output;
        FileWriter writer = new FileWriter(outputFileLocation, true);
        try{
            int newArrayWidth = array.get(0).size();
            for (int i=0; i<array.size(); ++i) {
                String currentLine = "";
                output = new StringBuilder(currentLine);
                output.append(array.get(i).get(0));
                for (int j=1; j<newArrayWidth; ++j) {
                    output.append(",").append(array.get(i).get(j));
                }
                output.append("\n");
                writer.write(output.toString());
            }
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    //Convert census ID to row number in census
    public static int getRowFromCensus(Integer censusID, String censusFileLocation) {
        BufferedReader reader;
        String currentLine;
        int counter = 0;
        try {
            reader = new BufferedReader(new FileReader(censusFileLocation));
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                if (censusID.equals(Integer.valueOf(values[0]))) {
                    reader.close();
                    return counter;
                }
                counter += 1;
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Could not find census ID " + censusID);
        return -1;
    }
}
