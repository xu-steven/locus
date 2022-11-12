import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FileUtils {
    private FileUtils(){}

    //Find number of origins based on graph location
    public static int getOriginCount(String graphLocation) {
        InputStream inputStream;
        int originCount = 0;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(graphLocation));
            byte[] chars = new byte[1024];
            int readChars = inputStream.read(chars);
            if (readChars == -1) {
                return 0;
            }
            while (readChars == 1024) {
                for (int i = 0; i < 1024; i++) {
                    if (chars[i] == '\n') {
                        ++originCount;
                    }
                }
                readChars = inputStream.read(chars);
            }
            while (readChars != -1) {
                for (int i = 0; i < readChars; ++i) {
                    if (chars[i] == '\n') {
                        ++originCount;
                    }
                }
                readChars = inputStream.read(chars);
            }
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Math.max(1, originCount) - 1;
    }

    //Find number of origins based on graph location
    public static int getSitesCount(String graphLocation) {
        InputStream inputStream;
        int potentialSitesCount = 0;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(graphLocation));
            byte[] chars = new byte[1024];
            int readChars = inputStream.read(chars);
            if (readChars == -1) {
                return 0;
            }
            boolean reachedNextLine = false;
            while (readChars == 1024) {
                for (int i = 0; i < 1024; i++) {
                    if (chars[i] == ',') {
                        ++potentialSitesCount;
                    } else if (chars[i] == '\n') {
                        reachedNextLine = true;
                        break;
                    }
                }
                if (reachedNextLine) break;
                readChars = inputStream.read(chars);
            }
            if (!reachedNextLine) {
                while (readChars != -1) {
                    for (int i = 0; i < readChars; ++i) {
                        if (chars[i] == ',') {
                            ++potentialSitesCount;
                        } else if (chars[i] == '\n') {
                            reachedNextLine = true;
                            break;
                        }
                    }
                    if (reachedNextLine) break;
                    readChars = inputStream.read(chars);
                }
            }
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return potentialSitesCount;
    }

    //Delete first row and column (identifiers) of census CSV to form census array
    public static double[][] getInnerDoubleArrayFromCSV(String fileLocation, int originCount, int siteCount) {
        System.out.println("Importing " + fileLocation + " into memory.");
        BufferedReader reader;
        String currentLine;
        double[][] innerDoubleArray = new double[originCount][siteCount];
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            int originCounter = 0;
            reader.readLine(); //skip first line
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                for (int i = 1; i < values.length; i++) {
                    innerDoubleArray[originCounter][i - 1] = Double.valueOf(values[i]);
                }
                originCounter += 1;
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Done importing " + fileLocation);
        return innerDoubleArray;
    }

    //Variant of getInnerDoubleArrayFromCSV that reclassifies N/A as -1 to denote identical origin and site
    public static double[][] getInnerAzimuthArrayFromCSV(String fileLocation, int originCount, int siteCount) {
        System.out.println("Importing " + fileLocation + " into memory.");
        BufferedReader reader;
        String currentLine;
        double[][] innerDoubleArray = new double[originCount][siteCount];
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            int originCounter = 0;
            reader.readLine(); //skip first line
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                for (int i = 1; i < values.length; i++) {
                    if (values[i].equals("N/A")) {
                        innerDoubleArray[originCounter][i - 1] = -1.0;
                    } else {
                        innerDoubleArray[originCounter][i - 1] = Double.valueOf(values[i]);
                    }
                }
                originCounter += 1;
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Done importing " + fileLocation);
        return innerDoubleArray;
    }

    //Extract case counts from CSV with column heading
    public static double[][] getCaseCountsFromCSV(String fileLocation, String caseCountColumnHeading, int originCount) {
        System.out.println("Importing case counts into memory.");
        BufferedReader reader;
        String currentLine;
        List<Integer> caseCountHeadingIndices;
        double[][] caseCounts = new double[0][];
        try {
            //Get case count indices
            reader = new BufferedReader(new FileReader(fileLocation));
            currentLine = reader.readLine();
            String[] values = currentLine.split(",");
            caseCountHeadingIndices = findColumnIndices(Arrays.asList(values), caseCountColumnHeading);

            //Fill out array with case counts
            int counter = 0;
            caseCounts = new double[caseCountHeadingIndices.size()][originCount];
            while ((currentLine = reader.readLine()) != null) {
                values = currentLine.split(",");
                for (int timepoint = 0; timepoint < caseCountHeadingIndices.size(); timepoint++) {
                    caseCounts[timepoint][counter] = Double.valueOf(values[caseCountHeadingIndices.get(timepoint)]);
                }
                counter += 1;
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

    //Finds index position of heading of interest from list of headings
    public static List<Integer> findColumnIndices(List<String> l, String s) throws Exception {
        List<Integer> indices = new ArrayList<>();
        for (int counter = 0; counter < l.size(); ++counter) {
            if (s.contains(l.get(counter))) {
                indices.add(counter);
                break;
            }
        }
        if (indices.size() == 0) {
            throw new Exception(s+" was not found.");
        }
        return indices;
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