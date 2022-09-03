import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PopulationParameters {
    //Fertility data; year -> (age -> fertility)
    private final Map<Integer, Map<Integer, Double>> fertility;
    private final int oldestFertilityCohortAge;

    //year -> male to female birth ratio
    private final Map<Integer, Double> sexRatioAtBirth;

    //Mortality data; year -> (age -> mortality)
    private final Map<Integer, Map<Integer, Double>> maleMortality;
    private final Map<Integer, Map<Integer, Double>> femaleMortality;

    //Infant mortality data; year -> (age in months -> cumulative mortality by that age)
    private final Map<Integer, Map<Double, Double>> maleInfantCumulativeMortality;
    private final Map<Integer, Map<Double, Double>> femaleInfantCumulativeMortality;

    //Separation factor; year -> infant separation factor (mean age of death in years <= 1)
    private final Map<Integer, Double> maleInfantSeparationFactor;
    private final Map<Integer, Double> femaleInfantSeparationFactor;

    //Migration data; year -> (age -> migration)
    private final Map<Integer, Map<Integer, Double>> maleMigration;
    private final Map<Integer, Map<Integer, Double>> femaleMigration;
    private int migrationFormat; //0 if absolute migration numbers, 1 if rates per capita

    public PopulationParameters(String mortalityLocation, String infantMortalityLocation, String fertilityLocation, String migrationLocation, String migrationFormat, int oldestPyramidCohortAge) {
        ParsedEventRates fertilityRates = parseAgeSexHeadingCSV(fertilityLocation, 1.0);
        fertility = fertilityRates.getFemaleRate();
        oldestFertilityCohortAge = fertilityRates.getMaxCohortAge();

        sexRatioAtBirth = new HashMap<>();
        for (int year = 2000; year < 3000; year++) {
            sexRatioAtBirth.put(year, 1.05);
        }
        ParsedCumulativeMortality infantCumulativeMortality = parseCumulativeInfantMortalityCSV(infantMortalityLocation, 1);
        maleInfantCumulativeMortality = infantCumulativeMortality.getMaleCumulativeMortality();
        femaleInfantCumulativeMortality = infantCumulativeMortality.getFemaleCumulativeMortality();
        maleInfantSeparationFactor = new HashMap<>();

        for (int year = 2000; year < 3000; year++) {
            maleInfantSeparationFactor.put(year, 0.235);
        }
        femaleInfantSeparationFactor = new HashMap<>();
        for (int year = 2000; year < 3000; year++) {
            femaleInfantSeparationFactor.put(year, 0.235);
        }
        ParsedEventRates mortalityRates = parseYearHeadingCSV(mortalityLocation, 1.0);
        maleMortality = mortalityRates.getMaleRate();
        femaleMortality = mortalityRates.getFemaleRate();
        int oldestMortalityCohortAge = mortalityRates.getMaxCohortAge();
        if (oldestMortalityCohortAge < oldestPyramidCohortAge) {
            for (int i = oldestMortalityCohortAge + 1; i <= oldestPyramidCohortAge; i++) {
                maleMortality.put(i, maleMortality.get(oldestMortalityCohortAge));
                femaleMortality.put(i, femaleMortality.get(oldestMortalityCohortAge));
            }
        }
        ParsedEventRates migrationRates = parseAgeSexHeadingCSV(migrationLocation, 1.0);
        maleMigration = migrationRates.getMaleRate();
        femaleMigration = migrationRates.getFemaleRate();
        int oldestMigrationCohortAge = migrationRates.getMaxCohortAge();
        if (oldestMigrationCohortAge < oldestPyramidCohortAge) {
            for (int i = oldestMigrationCohortAge + 1; i <= oldestPyramidCohortAge; i++) {
                maleMigration.put(i, maleMigration.get(oldestMigrationCohortAge));
                femaleMigration.put(i, femaleMigration.get(oldestMigrationCohortAge));
            }
        }
        if (migrationFormat.contains("Rate")) {
            this.migrationFormat = 1;
        } else {
            this.migrationFormat = 0;
        }
    }

    public static ParsedEventRates parseYearHeadingCSV(String fileLocation, double perPopulation) {
        BufferedReader reader;
        String currentLine;
        Map<Integer, Map<Integer, Double>> maleData = new HashMap<>();
        Map<Integer, Map<Integer, Double>> femaleData = new HashMap<>();
        Integer minAgeCohort = Integer.MAX_VALUE;
        Integer maxAgeCohort = 0;
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            List<String> headings = new ArrayList<>(Arrays.asList(reader.readLine().split(",")));
            headings.remove(0);
            int[] years = headings.stream().mapToInt(Integer::parseInt).toArray();
            for (int i = 0; i < years.length; i++) {
                maleData.put(years[i], new HashMap<>());
                femaleData.put(years[i], new HashMap<>());
            }
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                List<Integer> sexAgeInfo = parseAgeSexGroup(values[0]);
                if (sexAgeInfo.get(1) < minAgeCohort) minAgeCohort = sexAgeInfo.get(1);
                if (sexAgeInfo.get(2) > maxAgeCohort) maxAgeCohort = sexAgeInfo.get(2);
                if (sexAgeInfo.get(0) == 0) {
                    for (int i = 0; i < years.length; i++) {
                        Map<Integer, Double> currentYearData = maleData.get(years[i]);
                        for (int age = sexAgeInfo.get(1); age <= sexAgeInfo.get(2); age++) {
                            currentYearData.put(age, Double.valueOf(values[i + 1]) / perPopulation);
                        }
                        maleData.put(years[i], currentYearData);
                    }
                } else {
                    for (int i = 0; i < years.length; i++) {
                        Map<Integer, Double> currentYearData = femaleData.get(years[i]);
                        for (int j = sexAgeInfo.get(1); j <= sexAgeInfo.get(2); j++) {
                            currentYearData.put(j, Double.valueOf(values[i + 1]) / perPopulation);
                        }
                        femaleData.put(years[i], currentYearData);
                    }
                }
            }
            reader.close();
            for (int i = 0; i < years.length; i++) {
                Map<Integer, Double> currentYearMaleData = maleData.get(years[i]);
                Map<Integer, Double> currentYearFemaleData = femaleData.get(years[i]);
                for (int j = 0; j < minAgeCohort; j++){
                    currentYearMaleData.put(j, 0.0);
                    currentYearFemaleData.put(j, 0.0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ParsedEventRates(maleData, femaleData, maxAgeCohort);
    }

    public static ParsedEventRates parseAgeSexHeadingCSV(String fileLocation, double perPopulation) {
        BufferedReader reader;
        String currentLine;
        Map<Integer, Map<Integer, Double>> maleData = new HashMap<>();
        Map<Integer, Map<Integer, Double>> femaleData = new HashMap<>();
        Integer minAgeCohort = 1000;
        Integer maxAgeCohort = 0;
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            List<String> headings = new ArrayList<>(Arrays.asList(reader.readLine().split(",")));
            headings.remove(0);
            List<List<Integer>> sexAgeHeadings = headings.stream().map(x -> parseAgeSexGroup(x)).collect(Collectors.toList());
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                Map<Integer, Double> currentYearMaleData = new HashMap<>();
                Map<Integer, Double> currentYearFemaleData = new HashMap<>();
                for (int i = 0; i < sexAgeHeadings.size(); i++) {
                    if (sexAgeHeadings.get(i).get(1) < minAgeCohort) minAgeCohort = sexAgeHeadings.get(i).get(1);
                    if (sexAgeHeadings.get(i).get(2) > maxAgeCohort) maxAgeCohort = sexAgeHeadings.get(i).get(2);
                    for (int age = sexAgeHeadings.get(i).get(1); age <= sexAgeHeadings.get(i).get(2); age++) {
                        if (sexAgeHeadings.get(i).get(0) == 0) {
                            currentYearMaleData.put(age, Double.valueOf(values[i + 1]) / perPopulation);
                        } else {
                            currentYearFemaleData.put(age, Double.valueOf(values[i + 1]) / perPopulation);
                        }
                    }
                }
                for (int i = 0; i < minAgeCohort; i++) {
                    currentYearMaleData.put(i, 0.0);
                    currentYearFemaleData.put(i, 0.0);
                }
                maleData.put(Integer.valueOf(values[0]), currentYearMaleData);
                femaleData.put(Integer.valueOf(values[0]), currentYearFemaleData);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ParsedEventRates(maleData, femaleData, maxAgeCohort);
    }

    //Cumulative infant mortality CSV with year headings
    public static ParsedCumulativeMortality parseCumulativeInfantMortalityCSV(String fileLocation, double perPopulation) {
        BufferedReader reader;
        String currentLine;
        Map<Integer, Map<Double, Double>> maleData = new HashMap<>();
        Map<Integer, Map<Double, Double>> femaleData = new HashMap<>();
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            List<String> headings = new ArrayList<>(Arrays.asList(reader.readLine().split(",")));
            headings.remove(0);
            int[] years = headings.stream().mapToInt(Integer::parseInt).toArray();
            for (int i = 0; i < years.length; i++) {
                maleData.put(years[i], new HashMap<>(Map.of(0.0, 0.0)));
                femaleData.put(years[i], new HashMap<>(Map.of(0.0, 0.0)));
            }
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                List<Double> sexAgeInfo = parseInfantAgeSex(values[0]);
                if (sexAgeInfo.get(0) == 0) {
                    for (int i = 0; i < years.length; i++) {
                        Map<Double, Double> currentYearData = maleData.get(years[i]);
                        currentYearData.put(sexAgeInfo.get(1), Double.valueOf(values[i + 1]) / perPopulation);
                        maleData.put(years[i], currentYearData);
                    }
                } else {
                    for (int i = 0; i < years.length; i++) {
                        Map<Double, Double> currentYearData = femaleData.get(years[i]);
                        currentYearData.put(sexAgeInfo.get(1), Double.valueOf(values[i + 1]) / perPopulation);
                        femaleData.put(years[i], currentYearData);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ParsedCumulativeMortality(maleData, femaleData);
    }

    //Reads population partition and outputs list of 3 integers: sex (0 male or 1 female), lower age bound, upper age bound
    public static List<Integer> parseAgeSexGroup(String ageSexGroup) {
        List<Integer> output = new ArrayList<>();
        if (ageSexGroup.contains("M")) {
            output.add(0);
        } else {
            output.add(1);
        }
        List<Integer> ageBounds = new ArrayList<>(Arrays.stream(ageSexGroup.replaceAll("[^0-9]+", " ").trim().split(" ")).mapToInt(Integer::parseInt).boxed().toList());
        if (ageBounds.size() == 1) {
            ageBounds.add(ageBounds.get(0));
        }
        output.addAll(ageBounds);
        return output;
    }

    //Reads population partition and outputs list of 3 integers: sex (0 male or 1 female), lower age bound, upper age bound
    public static List<Double> parseInfantAgeSex(String ageSex) {
        List<Double> output = new ArrayList<>();
        if (ageSex.contains("M")) {
            output.add(0.0);
        } else {
            output.add(1.0);
        }
        Double age = Double.valueOf(ageSex.replaceAll("[^\\d.]", ""));
        output.add(age);
        return output;
    }

    //Returns true if there are missing years in data
    public boolean checkProjectionYears(int startingYear, int endingYear) {
        boolean anyMissingYears = false;
        Set<Integer> projectionYears = IntStream.range(startingYear, endingYear).boxed().collect(Collectors.toSet());
        if (!this.fertility.keySet().containsAll(projectionYears)) {
            anyMissingYears = true;
            Set<Integer> missingYears = new HashSet<>(projectionYears);
            missingYears.removeAll(this.fertility.keySet());
            System.out.println("Missing fertility data for years " + missingYears);
        }
        if (!this.sexRatioAtBirth.keySet().containsAll(projectionYears)) {
            anyMissingYears = true;
            Set<Integer> missingYears = new HashSet<>(projectionYears);
            missingYears.removeAll(this.sexRatioAtBirth.keySet());
            System.out.println("Missing human sex ratio data for years " + missingYears);
        }
        if (!this.maleMortality.keySet().containsAll(projectionYears)) {
            anyMissingYears = true;
            Set<Integer> missingYears = new HashSet<>(projectionYears);
            missingYears.removeAll(this.maleMortality.keySet());
            System.out.println("Missing male mortality data for years " + missingYears);
        }
        if (!this.femaleMortality.keySet().containsAll(projectionYears)) {
            anyMissingYears = true;
            Set<Integer> missingYears = new HashSet<>(projectionYears);
            missingYears.removeAll(this.femaleMortality.keySet());
            System.out.println("Missing female mortality data for years " + missingYears);
        }
        if (!this.maleInfantCumulativeMortality.keySet().containsAll(projectionYears)) {
            anyMissingYears = true;
            Set<Integer> missingYears = new HashSet<>(projectionYears);
            missingYears.removeAll(this.maleInfantCumulativeMortality.keySet());
            System.out.println("Missing male infant mortality data for years " + missingYears);
        }
        if (!this.femaleInfantCumulativeMortality.keySet().containsAll(projectionYears)) {
            anyMissingYears = true;
            Set<Integer> missingYears = new HashSet<>(projectionYears);
            missingYears.removeAll(this.femaleInfantCumulativeMortality.keySet());
            System.out.println("Missing female infant mortality data for years " + missingYears);
        }
        if (!this.maleMigration.keySet().containsAll(projectionYears)) {
            anyMissingYears = true;
            Set<Integer> missingYears = new HashSet<>(projectionYears);
            missingYears.removeAll(this.maleMigration.keySet());
            System.out.println("Missing male migration data for years " + missingYears);
        }
        if (!this.femaleMigration.keySet().containsAll(projectionYears)) {
            anyMissingYears = true;
            Set<Integer> missingYears = new HashSet<>(projectionYears);
            missingYears.removeAll(this.femaleMigration.keySet());
            System.out.println("Missing female migration data for years " + missingYears);
        }
        return anyMissingYears;
    }

    record ParsedEventRates(Map<Integer, Map<Integer, Double>> maleRate, Map<Integer, Map<Integer, Double>> femaleRate, int maxCohortAge) {
        public Map<Integer, Map<Integer, Double>> getMaleRate() {
            return maleRate;
        }
        public Map<Integer, Map<Integer, Double>> getFemaleRate() {
            return femaleRate;
        }
        public int getMaxCohortAge() {
            return maxCohortAge;
        }
    }

    record ParsedCumulativeMortality(Map<Integer, Map<Double, Double>> maleCumulativeMortality, Map<Integer, Map<Double, Double>> femaleCumulativeMortality) {
        public Map<Integer, Map<Double, Double>> getMaleCumulativeMortality() {
            return maleCumulativeMortality;
        }
        public Map<Integer, Map<Double, Double>> getFemaleCumulativeMortality() {
            return femaleCumulativeMortality;
        }
    }

    public double getSpecificFertility(int year, int age) {
        if (age > oldestFertilityCohortAge) {
            return 0.0;
        } else {
            return fertility.get(year).get(Math.min(age, oldestFertilityCohortAge));
        }
    }

    public int getOldestFertilityCohortAge() {
        return oldestFertilityCohortAge;
    }

    public double getHumanSexRatio(int year) {
        return sexRatioAtBirth.get(year);
    }

    public Map<Integer, Map<Integer, Double>> getMaleMortality() {
        return maleMortality;
    }

    public Map<Integer, Map<Integer, Double>> getFemaleMortality() {
        return femaleMortality;
    }

    public Map<Integer, Map<Double, Double>> getMaleInfantCumulativeMortality() {
        return maleInfantCumulativeMortality;
    }

    public Map<Integer, Map<Double, Double>> getFemaleInfantCumulativeMortality() {
        return femaleInfantCumulativeMortality;
    }

    public Map<Integer, Double> getMaleInfantSeparationFactor() {
        return maleInfantSeparationFactor;
    }

    public Map<Integer, Double> getFemaleInfantSeparationFactor() {
        return femaleInfantSeparationFactor;
    }

    public Map<Integer, Map<Integer, Double>> getMaleMigration() {
        return maleMigration;
    }

    public Map<Integer, Map<Integer, Double>> getFemaleMigration() {
        return femaleMigration;
    }

    public int getMigrationFormat() {
        return migrationFormat;
    }

}