import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class Population {
    //Current year
    public int year;

    //Map from age group (lower, upper bounds) to population. Final age includes all persons exceeding age as well.
    public Map<List<Integer>, Double> malePopulationPyramid;
    public Map<List<Integer>, Double> femalePopulationPyramid;
    int oldestPyramidCohortAge;

    //Adjusted map age -> population with 1 year age increments, with final year including all >= that age
    public Map<Integer, Double> maleAdjustedPyramid;
    public Map<Integer, Double> femaleAdjustedPyramid;

    //Mortality data; year -> (age -> mortality)
    public Map<Integer, Map<Integer, Double>> maleMortality;
    public Map<Integer, Map<Integer, Double>> femaleMortality;
    static int oldestMortalityCohortAge;

    //year -> male to female birth ratio
    public Map<Integer, Double> sexRatioAtBirth;

    //Fraction of population aged 0-6 months of total age 0 population
    public double currentMaleProportionUnderSixMonths;
    public double currentFemaleProportionUnderSixMonths;

    //Migration data
    public Map<Integer, Map<Integer, Double>> maleMigration;
    public Map<Integer, Map<Integer, Double>> femaleMigration;
    int oldestMigrationCohortAge;
    int migrationFormat = 0; //0 if absolute migration numbers, 1 if rates per capita

    public Population() {
        List<Object> mortalityInfo = parseMortalityCSV("M:\\Optimization Project\\demographic projections\\alberta_mortality.csv");
        maleMortality = (Map<Integer, Map<Integer, Double>>) mortalityInfo.get(0);
        femaleMortality = (Map<Integer, Map<Integer, Double>>) mortalityInfo.get(1);
        oldestMortalityCohortAge = (Integer) mortalityInfo.get(2);
    }

    public static void main(String[] args) {
        Population pop = new Population();
        List<String> partitionNames = FileUtils.getCSVHeadings("M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv");
        List<List<Double>> populationByPartition = FileUtils.getInnerDoubleArrayFromCSV("M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv");
        pop.createPopulationPyramid(partitionNames, populationByPartition.get(0));
        System.out.println(pop.getFemalePopulationPyramid());
        pop.createAdjustedPyramid(1991);
        System.out.println(pop.getFemaleAdjustedPyramid());
    }



    //Project population
    public void projectPopulation(int years) {

    }

    public void projectNextYearPyramid() {
        Map<Integer, Double> nextYearMalePyramid = new HashMap<>();
        for (Integer age : maleAdjustedPyramid.keySet()) {
            if (age < oldestPyramidCohortAge - 1) {
                nextYearMalePyramid.put(age + 1, projectNextYearPopulation("Male", age, year, maleAdjustedPyramid.get(age)));
            } else if (age == oldestPyramidCohortAge) { //maxPyramidAgeCohort and maxPyramid(Age-1)Cohort are merged so only computing once
                nextYearMalePyramid.put(oldestPyramidCohortAge, projectNextYearMaxAgePopulation("Male", year));
            }

        }
    }

    //Projects population of cohort in next year, i.e. Pop(t + 1, age + 1)
    public double projectNextYearPopulation(String sex, int age, int year, double currentYearPopulation) {
        double nextYearPopulation;
        if (sex.equals("Male")) {
            nextYearPopulation = (currentYearPopulation - 0.5 * projectDeaths(sex, age, year, currentYearPopulation) + 0.5 * projectMigration(sex, age, year, currentYearPopulation) + (1 - migrationFormat) * 0.5 * projectMigration(sex, age + 1, year + 1, -1.0))
                    / (1 + maleMortality.get(year + 1).get(age + 1) - migrationFormat * maleMigration.get(year + 1).get(age + 1));
        } else {
            nextYearPopulation = (currentYearPopulation - 0.5 * projectDeaths(sex, age, year, currentYearPopulation) + 0.5 * projectMigration(sex, age, year, currentYearPopulation) + (1 - migrationFormat) * 0.5 * projectMigration(sex, age + 1, year + 1, -1.0))
                    / (1 + femaleMortality.get(year + 1).get(age + 1) - migrationFormat * femaleMigration.get(year + 1).get(age + 1));
        }
        return nextYearPopulation;
    }

    //Projects population of the oldest cohort in next year
    public double projectNextYearMaxAgePopulation(String sex, int year) {
        double nextYearPopulation;
        int secondOldestPyramidCohortAge = oldestPyramidCohortAge - 1;
        double currentYearSecondOldestCohortPopulation;
        double currentYearOldestCohortPopulation;
        if (sex.equals("Male")) {
            currentYearSecondOldestCohortPopulation = maleAdjustedPyramid.get(secondOldestPyramidCohortAge);
            currentYearOldestCohortPopulation = maleAdjustedPyramid.get(oldestPyramidCohortAge);
            nextYearPopulation = (currentYearSecondOldestCohortPopulation - 0.5 * projectDeaths(sex, secondOldestPyramidCohortAge, year, currentYearSecondOldestCohortPopulation) + 0.5 * projectMigration(sex, secondOldestPyramidCohortAge, year, currentYearSecondOldestCohortPopulation)
                    + currentYearOldestCohortPopulation - 0.5 * projectDeaths(sex, oldestPyramidCohortAge, year, currentYearOldestCohortPopulation) + 0.5 * projectMigration(sex, oldestPyramidCohortAge, year, currentYearOldestCohortPopulation)
                    + (1 - migrationFormat) * 0.5 * projectMigration(sex, oldestPyramidCohortAge, year + 1, -1.0)) //not population dependent if format 0, i.e. absolute numbers
                    / (1 + maleMortality.get(year + 1).get(oldestPyramidCohortAge) - migrationFormat * maleMigration.get(year + 1).get(oldestPyramidCohortAge));
        } else {
            currentYearSecondOldestCohortPopulation = femaleAdjustedPyramid.get(secondOldestPyramidCohortAge);
            currentYearOldestCohortPopulation = femaleAdjustedPyramid.get(oldestPyramidCohortAge);
            nextYearPopulation = (currentYearSecondOldestCohortPopulation - 0.5 * projectDeaths(sex, secondOldestPyramidCohortAge, year, currentYearSecondOldestCohortPopulation) + 0.5 * projectMigration(sex, secondOldestPyramidCohortAge, year, currentYearSecondOldestCohortPopulation)
                    + currentYearOldestCohortPopulation - 0.5 * projectDeaths(sex, oldestPyramidCohortAge, year, currentYearOldestCohortPopulation) + 0.5 * projectMigration(sex, oldestPyramidCohortAge, year, currentYearOldestCohortPopulation)
                    + (1 - migrationFormat) * 0.5 * projectMigration(sex, oldestPyramidCohortAge, year + 1, -1.0)) //not population dependent if format 0, i.e. absolute numbers
                    / (1 + femaleMortality.get(year + 1).get(oldestPyramidCohortAge) - migrationFormat * femaleMigration.get(year + 1).get(oldestPyramidCohortAge));
        }
        return nextYearPopulation;
    }

    //Projects total male and female births next year, updates age zero separation
    public List<Double> projectNextYearAgeZeroPopulation(int year, Map<Integer, Double> nextYearFemalePyramid) {
        double survivingMaleBirths = 0.5 * projectBirths("Male", year, femaleAdjustedPyramid) * (0.33 * meanInfantSurvival("Male", year, 0.5, 1.0) + 0.67 * meanInfantSurvival("Male", year + 1, 0.5, 1.0)) //estimates as average of infant mortality between two years
                + 0.5 * projectBirths("Male", year + 1, nextYearFemalePyramid) * meanInfantSurvival("Male", year + 1, 0.0, 0.5);
        double survivingFemaleBirths = 0.5 * projectBirths("Female", year, femaleAdjustedPyramid) * (0.33 * meanInfantSurvival("Female", year, 0.5, 1.0) + 0.67 * meanInfantSurvival("Female", year + 1, 0.5, 1.0)) //estimates as average of infant mortality between two years
                + 0.5 * projectBirths("Female", year + 1, nextYearFemalePyramid) * meanInfantSurvival("Female", year + 1, 0.0, 0.5);
        double nextYearMaleAgeZeroPopulation = (survivingMaleBirths + (1 - migrationFormat) * 0.5 * projectMigration("Male", 0, year + 1, -1.0))
                / (1 - migrationFormat * maleMigration.get(year + 1).get(0)); //using RUP formulation, can also consider 0.125/0.375 split of migration according to probability year 0 and year 1 migrant remaining age 0
        double nextYearFemaleAgeZeroPopulation = (survivingFemaleBirths + (1 - migrationFormat) * 0.5 * projectMigration("Female", 0, year + 1, -1.0))
                / (1 - migrationFormat * femaleMigration.get(year + 1).get(0)); //using RUP formulation, can also consider 0.125/0.375 split of migration according to probability year 0 and year 1 migrant remaining age 0

        //Update age zero separation
        this.currentMaleProportionUnderSixMonths = (0.5 * projectBirths("Male", year + 1, nextYearFemalePyramid) * meanInfantSurvival("Male", year + 1, 0.0, 0.5)
                //+ 0.5 * (nextYearMaleAgeZeroPopulation - survivingMaleBirths)) //assumes relatively robust immigrant population
                + 0.5 * meanInfantSurvival("Male", year + 1, 0.0, 0.5) / meanInfantSurvival("Male", year + 1, 0.0, 1.0) * (nextYearMaleAgeZeroPopulation - survivingMaleBirths)) //estimates proportion of migrants being 0-6 months based on infant mortality
                / nextYearMaleAgeZeroPopulation;
        this.currentFemaleProportionUnderSixMonths = (0.5 * projectBirths("Female", year + 1, nextYearFemalePyramid) * meanInfantSurvival("Female", year + 1, 0.0, 0.5) //assumes relatively robust immigrant population
                //+ 0.5 * (nextYearFemaleAgeZeroPopulation - survivingFemaleBirths)) //estimates proportion of migrants being 0-6 months based on infant mortality
                + 0.5 * meanInfantSurvival("Female", year + 1, 0.0, 0.5) / meanInfantSurvival("Female", year + 1, 0.0, 1.0) * (nextYearFemaleAgeZeroPopulation - survivingFemaleBirths)) //estimates proportion of migrants being 0-6 months based on infant mortality
                / nextYearFemaleAgeZeroPopulation;

        return Arrays.asList(nextYearMaleAgeZeroPopulation, nextYearFemaleAgeZeroPopulation);
    }

    //Projects age 1 population
    public double projectNextYearAgeOnePopulation(String sex, int year, double currentYearAgeZeroPopulation) {
        double nextYearPopulation;
        if (sex.equals("Male")) {
            nextYearPopulation = (currentYearAgeZeroPopulation * currentMaleProportionUnderSixMonths * (0.67 * (1 - maleMortality.get(year).get(0)) / meanInfantSurvival(sex, year, 0.0, 0.5) + 0.33 * (1 - maleMortality.get(year + 1).get(0)) / meanInfantSurvival(sex, year + 1, 0.0, 0.5)) * (1 - 0.25 * maleMortality.get(year + 1).get(1)) //assumes constant rate of death in year 1, given low ; can use log estimates
                    + currentYearAgeZeroPopulation * (1 - currentMaleProportionUnderSixMonths) * (1 - maleMortality.get(year).get(0)) / meanInfantSurvival("Male", year, 0.5, 1.0) * (1 - 0.25 * maleMortality.get(year).get(1) - 0.5 * maleMortality.get(year + 1).get(1)) //mortality exposures varies from full year to half year in this group in age
                    + 0.5 * projectMigration(sex, 0, year, currentYearAgeZeroPopulation) + (1 - migrationFormat) * 0.5 * projectMigration(sex, 1, year + 1, -1.0))
                    / (1 - migrationFormat * maleMigration.get(year + 1).get(1));
        } else {
            nextYearPopulation = (currentYearAgeZeroPopulation * currentFemaleProportionUnderSixMonths * (0.67 * (1 - femaleMortality.get(year).get(0)) / meanInfantSurvival(sex, year, 0.0, 0.5) + 0.33 * (1 - femaleMortality.get(year + 1).get(0)) / meanInfantSurvival(sex, year + 1, 0.0, 0.5)) * (1 - 0.25 * femaleMortality.get(year + 1).get(1)) //assumes constant rate of death in year 1, given low ; can use log estimates
                    + currentYearAgeZeroPopulation * (1 - currentFemaleProportionUnderSixMonths) * (1 - femaleMortality.get(year).get(0)) / meanInfantSurvival(sex, year, 0.5, 1.0) * (1 - 0.25 * femaleMortality.get(year).get(1) - 0.5 * maleMortality.get(year + 1).get(1)) //mortality exposures varies from full year to half year in this group in age
                    + 0.5 * projectMigration(sex, 0, year, currentYearAgeZeroPopulation) + (1 - migrationFormat) * 0.5 * projectMigration(sex, 1, year + 1, -1.0))
                    / (1 - migrationFormat * femaleMigration.get(year + 1).get(1));
        }
        return nextYearPopulation;
    }

    public double projectDeaths(String sex, int age, int year, double currentYearPopulation) {
        return 0.0;
    }

    public double projectMigration(String sex, int age, int year, double currentYearPopulation) {
        return 0.0;
    }

    public double projectBirths(String sex, int year, Map<Integer, Double> femalePyramid) {
        return 0.0;
    }

    //Mean proportion of surviving live births from a to b years ago with constant birth rate; a,b <= 1
    public double meanInfantSurvival(String sex, int year, double a, double b) {
        return 0.0;
    }

    //Estimate population pyramid with 1 year age gaps
    public void createPopulationPyramid(List<String> partitionNames, List<Double> populationByPartition) {
        malePopulationPyramid = new HashMap<>();
        femalePopulationPyramid = new HashMap<>();
        oldestPyramidCohortAge = 0;
        for (int i = 0; i < partitionNames.size(); i++) {
            List<Integer> sexAgeInfo = parseSexAgeGroup(partitionNames.get(i));
            if (sexAgeInfo.get(2) > oldestPyramidCohortAge) oldestPyramidCohortAge = sexAgeInfo.get(2);
            if (sexAgeInfo.get(0) == 0) {
                malePopulationPyramid.put(Arrays.asList(sexAgeInfo.get(1), sexAgeInfo.get(2)), populationByPartition.get(i));
            } else {
                femalePopulationPyramid.put(Arrays.asList(sexAgeInfo.get(1), sexAgeInfo.get(2)), populationByPartition.get(i));
            }
        }
    }

    public void createAdjustedPyramid(Integer year) {
        maleAdjustedPyramid = new HashMap();
        femaleAdjustedPyramid = new HashMap();
        for (List<Integer> ageBounds : malePopulationPyramid.keySet()) {
            maleAdjustedPyramid.putAll(refineAgeGroups(ageBounds, malePopulationPyramid.get(ageBounds), maleMortality, year));
        }
        for (List<Integer> ageBounds : femalePopulationPyramid.keySet()) {
            femaleAdjustedPyramid.putAll(refineAgeGroups(ageBounds, femalePopulationPyramid.get(ageBounds), femaleMortality, year));
        }
    }

    //If population pyramid is not given in 1 year age groups, then estimates one year age groups by using mortality
    public Map<Integer, Double> refineAgeGroups(List<Integer> ageBounds, Double populationSize, Map<Integer, Map<Integer, Double>> sexMortality, Integer year) {
        //Determine relative population sizes
        List<Double> relativePopulationByAge = new ArrayList<>();
        Double nextRelativePopulation = 1.0;
        relativePopulationByAge.add(nextRelativePopulation);
        Double totalRelativePopulation = 1.0;
        for (int age = ageBounds.get(0); age < ageBounds.get(1); age++) {
            nextRelativePopulation = nextRelativePopulation * (1 - sexMortality.get(year).get(age));
            relativePopulationByAge.add(nextRelativePopulation);
            totalRelativePopulation += nextRelativePopulation;
        }

        //Create a refined pyramid
        Map<Integer, Double> refinedPyramid = new HashMap<>();
        for (int age = ageBounds.get(0); age <= ageBounds.get(1); age++) {
            refinedPyramid.put(age, populationSize * relativePopulationByAge.get(age - ageBounds.get(0)) / totalRelativePopulation);
        }
        return refinedPyramid;
    }

    //Collapses adjusted pyramid to same age cohorts of the original pyramid
    public void collapseAdjustedPyramid() {
        Map<List<Integer>, Double> remadeMalePyramid = new HashMap<>();
        Map<List<Integer>, Double> remadeFemalePyramid = new HashMap<>();
        for (List<Integer> ageBounds : malePopulationPyramid.keySet()) {
            Double cohortPopulation = 0.0;
            for (int age = ageBounds.get(0); age <= ageBounds.get(1); age++) {
                cohortPopulation += maleAdjustedPyramid.get(age);
            }
            remadeMalePyramid.put(ageBounds, cohortPopulation);
        }
        for (List<Integer> ageBounds : femalePopulationPyramid.keySet()) {
            Double cohortPopulation = 0.0;
            for (int age = ageBounds.get(0); age <= ageBounds.get(1); age++) {
                cohortPopulation += femaleAdjustedPyramid.get(age);
            }
            remadeFemalePyramid.put(ageBounds, cohortPopulation);
        }
        this.malePopulationPyramid = remadeMalePyramid;
        this.femalePopulationPyramid = remadeFemalePyramid;
    }

    public List<Object> parseMortalityCSV(String fileLocation) {
        BufferedReader reader;
        String currentLine;
        Map<Integer, Map<Integer, Double>> maleMortalityData = new HashMap<>();
        Map<Integer, Map<Integer, Double>> femaleMortalityData = new HashMap<>();
        Integer maxMortalityAgeCohort = 0;
        try {
            reader = new BufferedReader(new FileReader(fileLocation));
            List<String> headings = new ArrayList<>(Arrays.asList(reader.readLine().split(",")));
            headings.remove(0);
            int[] years = headings.stream().mapToInt(Integer::parseInt).toArray();
            for (int i = 0; i < years.length; i++) {
                maleMortalityData.put(years[i], new HashMap<>());
                femaleMortalityData.put(years[i], new HashMap<>());
            }
            while ((currentLine = reader.readLine()) != null) {
                String[] values = currentLine.split(",");
                List<Integer> sexAgeInfo = parseSexAgeGroup(values[0]);
                if (sexAgeInfo.get(2) > maxMortalityAgeCohort) maxMortalityAgeCohort = sexAgeInfo.get(2);
                if (sexAgeInfo.get(0) == 0) {
                    for (int i = 0; i < years.length; i++) {
                        Map<Integer, Double> currentYearMortalityData = maleMortalityData.get(years[i]);
                        for (int age = sexAgeInfo.get(1); age <= sexAgeInfo.get(2); age++) {
                            currentYearMortalityData.put(age, Double.valueOf(values[i + 1]));
                        }
                        maleMortalityData.put(years[i], currentYearMortalityData);
                    }
                } else {
                    for (int i = 0; i < years.length; i++) {
                        Map<Integer, Double> currentYearMortalityData = femaleMortalityData.get(years[i]);
                        for (int j = sexAgeInfo.get(1); j <= sexAgeInfo.get(2); j++) {
                            currentYearMortalityData.put(j, Double.valueOf(values[i + 1]));
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Arrays.asList(maleMortalityData, femaleMortalityData, maxMortalityAgeCohort);
    }

    //Reads population partition and outputs list of 3 integers: sex (0 male or 1 female), lower age bound, upper age bound
    public List<Integer> parseSexAgeGroup(String sexAgeGroup) {
        List<Integer> output = new ArrayList<>();
        if (sexAgeGroup.contains("M")) {
            output.add(0);
        } else {
            output.add(1);
        }
        List<Integer> ageBounds = new ArrayList<>(Arrays.stream(sexAgeGroup.replaceAll("[^0-9]+", " ").trim().split(" ")).mapToInt(Integer::parseInt).boxed().toList());
        if (ageBounds.size() == 1) {
            ageBounds.add(ageBounds.get(0));
        }
        output.addAll(ageBounds);
        return output;
    }

    public Map<List<Integer>, Double> getMalePopulationPyramid() {
        return malePopulationPyramid;
    }

    public Map<List<Integer>, Double> getFemalePopulationPyramid() {
        return femalePopulationPyramid;
    }

    public Map<Integer, Double> getMaleAdjustedPyramid() {
        return maleAdjustedPyramid;
    }

    public Map<Integer, Double> getFemaleAdjustedPyramid() {
        return femaleAdjustedPyramid;
    }

    public Map<Integer, Map<Integer, Double>> getMaleMortality() {
        return maleMortality;
    }

    public Map<Integer, Map<Integer, Double>> getFemaleMortality() {
        return femaleMortality;
    }
}