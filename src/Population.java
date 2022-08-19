import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Population {
    //Multithreading configuration
    static int threadCount = 6;
    static int taskCount = 6;
    static ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    //Current year
    public static int year;

    //Map from age group (lower, upper bounds) to population. Final age includes all persons exceeding age as well.
    public static Map<List<Integer>, Double> malePopulationPyramid;
    public static Map<List<Integer>, Double> femalePopulationPyramid;
    static int oldestPyramidCohortAge;

    //Adjusted map age -> population with 1 year age increments, with final year including all >= that age
    public static Map<Integer, Double> maleAdjustedPyramid;
    public static Map<Integer, Double> femaleAdjustedPyramid;

    //Fertility data; year -> (age -> fertility)
    public static Map<Integer, Map<Integer, Double>> fertility;
    static int oldestFertilityCohortAge;
    static double humanSexRatio = 1.05;

    //Mortality data; year -> (age -> mortality)
    public static Map<Integer, Map<Integer, Double>> maleMortality;
    public static Map<Integer, Map<Integer, Double>> femaleMortality;
    static int oldestMortalityCohortAge;

    //Infant mortality data; year -> (age in months -> cumulative mortality by that age)
    public static Map<Integer, Map<Double, Double>> maleInfantMortality;
    public static Map<Integer, Map<Double, Double>> femaleInfantMortality;

    //year -> male to female birth ratio
    public static Map<Integer, Double> sexRatioAtBirth;

    //Fraction of population aged 0-6 months of total age 0 population
    public static double currentMaleProportionUnderSixMonths;
    public static double currentFemaleProportionUnderSixMonths;

    //Migration data; year -> (age -> migration)
    public static Map<Integer, Map<Integer, Double>> maleMigration;
    public static Map<Integer, Map<Integer, Double>> femaleMigration;
    static int oldestMigrationCohortAge;
    static int migrationFormat = 0; //0 if absolute migration numbers, 1 if rates per capita

    public Population() {
        List<Object> mortalityInfo = parseYearHeadingCSV("M:\\Optimization Project\\demographic projections\\alberta_mortality.csv", 1.0);
        maleMortality = (Map<Integer, Map<Integer, Double>>) mortalityInfo.get(0);
        femaleMortality = (Map<Integer, Map<Integer, Double>>) mortalityInfo.get(1);
        oldestMortalityCohortAge = (int) mortalityInfo.get(2);
        List<Object> infantMortalityInfo = parseCumulativeInfantMortalityCSV("M:\\Optimization Project\\demographic projections\\test_alberta_infant_mortality.csv", 1);
        maleInfantMortality = (Map<Integer, Map<Double, Double>>) infantMortalityInfo.get(0);
        femaleInfantMortality = (Map<Integer, Map<Double, Double>>) infantMortalityInfo.get(1);
        List<Object> fertilityInfo = parseAgeSexHeadingCSV("M:\\Optimization Project\\demographic projections\\alberta_fertility.csv", 1000);
        fertility = (Map<Integer, Map<Integer, Double>>) fertilityInfo.get(1);
        oldestFertilityCohortAge = (int) fertilityInfo.get(2);
        List<Object> migrationInfo = parseAgeSexHeadingCSV("M:\\Optimization Project\\demographic projections\\alberta_migration.csv", 1.0);
        maleMigration = (Map<Integer, Map<Integer, Double>>) migrationInfo.get(0);
        femaleMigration = (Map<Integer, Map<Integer, Double>>) migrationInfo.get(1);
        oldestMigrationCohortAge = (int) migrationInfo.get(2);
    }

    public static void main(String[] args) {
        Population pop = new Population();
        List<String> partitionNames = FileUtils.getCSVHeadings("M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv");
        double[][] populationByPartition = FileUtils.getInnerDoubleArrayFromCSV("M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv", FileUtils.getOriginCount("M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv"), FileUtils.getSitesCount("M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv"));
        pop.createPopulationPyramid(partitionNames, populationByPartition[0]);
        System.out.println(pop.getFemalePopulationPyramid());
        year = 2000;
        pop.createAdjustedPyramid(year);
        System.out.println("Female adjusted period year 2000 is " + femaleAdjustedPyramid);
        pop.projectNextYearPyramid();
        System.out.println(maleInfantMortality);
        double[][] infantMortality = mapToDoubleArray(maleInfantMortality.get(2000), "2y - 3x", "0", "0.33y", 1000, 0, 1, 1000);
        //System.out.println(Arrays.deepToString(infantMortality));
        System.out.println(doubleIntegral(infantMortality, "0", "0.33y", 1000, 0, 1, 1000));
        executor.shutdown();
    }

    //Project population
    public void projectPopulation(int years) {

    }

    public void projectNextYearPyramid() {
        Map<Integer, Double> nextYearMalePyramid = new HashMap<>();
        for (Integer age : maleAdjustedPyramid.keySet()) {
            if (age == 0) {
                nextYearMalePyramid.put(1, 0.0);//projectNextYearAgeOnePopulation("Male", year, maleAdjustedPyramid.get(0)));
            } else if (age == oldestPyramidCohortAge) {
                nextYearMalePyramid.put(oldestPyramidCohortAge, projectNextYearMaxAgePopulation("Male", year));
            } else {
                nextYearMalePyramid.put(age + 1, projectNextYearPopulation("Male", age, year, maleAdjustedPyramid.get(age)));
            }
        }
        maleAdjustedPyramid = nextYearMalePyramid;
        Map<Integer, Double> nextYearFemalePyramid = new HashMap<>();
        for (Integer age : femaleAdjustedPyramid.keySet()) {
            if (age == 0 ) {
                nextYearFemalePyramid.put(1, 0.0);//projectNextYearAgeOnePopulation("Female", year, femaleAdjustedPyramid.get(0)));
            } else if (age == oldestPyramidCohortAge) { //maxPyramidAgeCohort and maxPyramid(Age-1)Cohort are merged so only computing once
                nextYearFemalePyramid.put(oldestPyramidCohortAge, projectNextYearMaxAgePopulation("Female", year));
            } else {
                 nextYearFemalePyramid.put(age + 1, projectNextYearPopulation("Female", age, year, femaleAdjustedPyramid.get(age)));
            }
        }
        femaleAdjustedPyramid = nextYearFemalePyramid;
    }

    //Projects population of cohort in next year, i.e. Pop(t + 1, age + 1)
    public static double projectNextYearPopulation(String sex, int age, int year, double currentYearPopulation) {
        double nextYearPopulation;
        if (sex.equals("Male")) {
            nextYearPopulation = (currentYearPopulation - 0.5 * projectDeaths(sex, age, year, currentYearPopulation) + 0.5 * projectMigration(sex, age, year, currentYearPopulation) + (1 - migrationFormat) * 0.5 * projectMigration(sex, age + 1, year + 1, -1.0))
                    / (1 + 0.5 * getAgeSpecificIncidence(age + 1, oldestMortalityCohortAge, maleMortality.get(year + 1)) - 0.5 * migrationFormat * getAgeSpecificIncidence(age + 1, oldestMigrationCohortAge, maleMigration.get(year + 1)));
        } else {
            nextYearPopulation = (currentYearPopulation - 0.5 * projectDeaths(sex, age, year, currentYearPopulation) + 0.5 * projectMigration(sex, age, year, currentYearPopulation) + (1 - migrationFormat) * 0.5 * projectMigration(sex, age + 1, year + 1, -1.0))
                    / (1 + 0.5 * getAgeSpecificIncidence(age + 1, oldestMortalityCohortAge, femaleMortality.get(year + 1)) - 0.5 * migrationFormat * getAgeSpecificIncidence(age + 1, oldestMigrationCohortAge, femaleMigration.get(year + 1)));
        }
        return nextYearPopulation;
    }

    //Projects population of the oldest cohort in next year
    public static double projectNextYearMaxAgePopulation(String sex, int year) {
        double nextYearPopulation;
        int secondOldestPyramidCohortAge = oldestPyramidCohortAge - 1;
        double currentYearSecondOldestCohortPopulation;
        double currentYearOldestCohortPopulation;
        if (sex.equals("Male")) {
            currentYearSecondOldestCohortPopulation = maleAdjustedPyramid.get(secondOldestPyramidCohortAge); //guaranteed <= oldest pyramid cohort age
            currentYearOldestCohortPopulation = maleAdjustedPyramid.get(oldestPyramidCohortAge); //guaranteed <= oldest pyramid cohort age
            nextYearPopulation = (currentYearSecondOldestCohortPopulation - 0.5 * projectDeaths(sex, secondOldestPyramidCohortAge, year, currentYearSecondOldestCohortPopulation) + 0.5 * projectMigration(sex, secondOldestPyramidCohortAge, year, currentYearSecondOldestCohortPopulation)
                    + currentYearOldestCohortPopulation - 0.5 * projectDeaths(sex, oldestPyramidCohortAge, year, currentYearOldestCohortPopulation) + 0.5 * projectMigration(sex, oldestPyramidCohortAge, year, currentYearOldestCohortPopulation)
                    + (1 - migrationFormat) * 0.5 * projectMigration(sex, oldestPyramidCohortAge, year + 1, -1.0)) //not population dependent if format 0, i.e. absolute numbers
                    / (1 + 0.5 * getAgeSpecificIncidence(oldestPyramidCohortAge, oldestMortalityCohortAge, maleMortality.get(year + 1)) - 0.5 * migrationFormat * getAgeSpecificIncidence(oldestPyramidCohortAge, oldestMigrationCohortAge, maleMigration.get(year + 1)));
        } else {
            currentYearSecondOldestCohortPopulation = femaleAdjustedPyramid.get(secondOldestPyramidCohortAge);
            currentYearOldestCohortPopulation = femaleAdjustedPyramid.get(oldestPyramidCohortAge);
            nextYearPopulation = (currentYearSecondOldestCohortPopulation - 0.5 * projectDeaths(sex, secondOldestPyramidCohortAge, year, currentYearSecondOldestCohortPopulation) + 0.5 * projectMigration(sex, secondOldestPyramidCohortAge, year, currentYearSecondOldestCohortPopulation)
                    + currentYearOldestCohortPopulation - 0.5 * projectDeaths(sex, oldestPyramidCohortAge, year, currentYearOldestCohortPopulation) + 0.5 * projectMigration(sex, oldestPyramidCohortAge, year, currentYearOldestCohortPopulation)
                    + (1 - migrationFormat) * 0.5 * projectMigration(sex, oldestPyramidCohortAge, year + 1, -1.0)) //not population dependent if format 0, i.e. absolute numbers
                    / (1 + 0.5 * getAgeSpecificIncidence(oldestPyramidCohortAge, oldestMortalityCohortAge, femaleMortality.get(year + 1)) - 0.5 * migrationFormat * getAgeSpecificIncidence(oldestPyramidCohortAge, oldestMigrationCohortAge, femaleMigration.get(year + 1)));
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
                / (1 - 0.5 * migrationFormat * maleMigration.get(year + 1).get(0)); //using RUP formulation, can also consider 0.125/0.375 split of migration according to probability year 0 and year 1 migrant remaining age 0
        double nextYearFemaleAgeZeroPopulation = (survivingFemaleBirths + (1 - migrationFormat) * 0.5 * projectMigration("Female", 0, year + 1, -1.0))
                / (1 - 0.5 * migrationFormat * femaleMigration.get(year + 1).get(0)); //using RUP formulation, can also consider 0.125/0.375 split of migration according to probability year 0 and year 1 migrant remaining age 0

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
                    / (1 - migrationFormat * getAgeSpecificIncidence(1, oldestMigrationCohortAge, maleMigration.get(year + 1)));
        } else {
            nextYearPopulation = (currentYearAgeZeroPopulation * currentFemaleProportionUnderSixMonths * (0.67 * (1 - femaleMortality.get(year).get(0)) / meanInfantSurvival(sex, year, 0.0, 0.5) + 0.33 * (1 - femaleMortality.get(year + 1).get(0)) / meanInfantSurvival(sex, year + 1, 0.0, 0.5)) * (1 - 0.25 * femaleMortality.get(year + 1).get(1)) //assumes constant rate of death in year 1, given low ; can use log estimates
                    + currentYearAgeZeroPopulation * (1 - currentFemaleProportionUnderSixMonths) * (1 - femaleMortality.get(year).get(0)) / meanInfantSurvival(sex, year, 0.5, 1.0) * (1 - 0.25 * femaleMortality.get(year).get(1) - 0.5 * maleMortality.get(year + 1).get(1)) //mortality exposures varies from full year to half year in this group in age
                    + 0.5 * projectMigration(sex, 0, year, currentYearAgeZeroPopulation) + (1 - migrationFormat) * 0.5 * projectMigration(sex, 1, year + 1, -1.0))
                    / (1 - migrationFormat * getAgeSpecificIncidence(1, oldestMigrationCohortAge, femaleMigration.get(year + 1)));
        }
        return nextYearPopulation;
    }

    public static double projectDeaths(String sex, int age, int year, double currentYearPopulation) {
        if (sex.equals("Male")) {
            return currentYearPopulation * getAgeSpecificIncidence(age, oldestMortalityCohortAge, maleMortality.get(year));
        } else {
            return currentYearPopulation * getAgeSpecificIncidence(age, oldestMortalityCohortAge, femaleMortality.get(year));
        }
    }

    public static double projectMigration(String sex, int age, int year, double currentYearPopulation) {
        if (sex.equals("Male")) {
            return currentYearPopulation * getAgeSpecificIncidence(age, oldestMigrationCohortAge, maleMigration.get(year));
        } else {
            return currentYearPopulation * getAgeSpecificIncidence(age, oldestMigrationCohortAge, femaleMigration.get(year));
        }
    }

    public static double projectBirths(String sex, int year, Map<Integer, Double> femalePyramid) {
        double totalBirths = 0;
        for (int age = 1; age <= oldestFertilityCohortAge; age++) {
            totalBirths += femalePyramid.get(age) * fertility.get(year).get(age);
        }
        if (sex.equals("Male")) {
            return humanSexRatio * totalBirths/(humanSexRatio + 1);
        } else {
            return totalBirths / (humanSexRatio + 1);
        }
    }

    //Mean proportion of surviving live births from surviving through a to b months ago with constant birth rate; a < b <= 11
    //Using https://www150.statcan.gc.ca/t1/tbl1/en/tv.action?pid=1310071301 or https://www150.statcan.gc.ca/t1/tbl1/en/tv.action?pid=1310007801&pickMembers%5B0%5D=2.1&pickMembers%5B1%5D=4.2&cubeTimeFrame.startYear=2000&cubeTimeFrame.endYear=2015&referencePeriods=20000101%2C20150101
    public static double meanInfantSurvival(String sex, int year, double a, double b) {

        return 0.0;
    }

    //calculate area under curve of infant cumulative mortality between startAge and endAge
    public static double areaUnderInfantCMCurve(String sex, int year, double startAge, double endAge) {
        double areaUnderInfantCMCurve = 0;
        int bestLowerBoundStartAgeIndex = 0;
        int bestLowerBoundEndAgeIndex = 0;
        if (sex.equals("Male")) {
            if (startAge == 12) return maleInfantMortality.get(year).get(12);
            List<Double> orderedAges = new ArrayList<>(maleInfantMortality.get(year).keySet());
            Collections.sort(orderedAges);
            for (int i = 0; i < orderedAges.size(); i++) {
                if (orderedAges.get(i) <= startAge) {
                    bestLowerBoundStartAgeIndex = i;
                }
                if (orderedAges.get(i) <= endAge) {
                    bestLowerBoundEndAgeIndex = i;
                }
            }
            double estimatedStartingCM = maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex)) +
                    (maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex + 1)) - maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex))) *
                            ((startAge - orderedAges.get(bestLowerBoundStartAgeIndex)) / (orderedAges.get(bestLowerBoundStartAgeIndex + 1) - orderedAges.get(bestLowerBoundStartAgeIndex)));
            areaUnderInfantCMCurve += (estimatedStartingCM + maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex + 1))) / 2 * (orderedAges.get(bestLowerBoundStartAgeIndex + 1) - startAge);
            for (int i = bestLowerBoundStartAgeIndex + 1; i < bestLowerBoundEndAgeIndex; i++) {
                areaUnderInfantCMCurve += (maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex)) + maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex + 1))) / 2 * (orderedAges.get(bestLowerBoundStartAgeIndex + 1) - orderedAges.get(bestLowerBoundStartAgeIndex));
            }
            if (endAge < 12) {
                double estimatedEndingCM = maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundEndAgeIndex)) +
                        (maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundEndAgeIndex + 1)) - maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundEndAgeIndex))) *
                                ((endAge - orderedAges.get(bestLowerBoundEndAgeIndex)) / (orderedAges.get(bestLowerBoundEndAgeIndex + 1) - orderedAges.get(bestLowerBoundEndAgeIndex)));
                areaUnderInfantCMCurve += (maleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex)) + estimatedEndingCM) / 2 * (endAge - orderedAges.get(bestLowerBoundStartAgeIndex));
            }
            return areaUnderInfantCMCurve;
        } else {
            if (startAge == 12) return femaleInfantMortality.get(year).get(12);
            List<Double> orderedAges = new ArrayList<>(femaleInfantMortality.get(year).keySet());
            Collections.sort(orderedAges);
            for (int i = 0; i < orderedAges.size(); i++) {
                if (orderedAges.get(i) <= startAge) {
                    bestLowerBoundStartAgeIndex = i;
                }
                if (orderedAges.get(i) <= endAge) {
                    bestLowerBoundEndAgeIndex = i;
                }
            }
            double estimatedStartingCM = femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex)) +
                    (femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex + 1)) - femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex))) *
                            ((startAge - orderedAges.get(bestLowerBoundStartAgeIndex)) / (orderedAges.get(bestLowerBoundStartAgeIndex + 1) - orderedAges.get(bestLowerBoundStartAgeIndex)));
            areaUnderInfantCMCurve += (estimatedStartingCM + femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex + 1))) / 2 * (orderedAges.get(bestLowerBoundStartAgeIndex + 1) - startAge);
            for (int i = bestLowerBoundStartAgeIndex + 1; i < bestLowerBoundEndAgeIndex; i++) {
                areaUnderInfantCMCurve += (femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex)) + femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex + 1))) / 2 * (orderedAges.get(bestLowerBoundStartAgeIndex + 1) - orderedAges.get(bestLowerBoundStartAgeIndex));
            }
            if (endAge < 12) {
                double estimatedEndingCM = femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundEndAgeIndex)) +
                        (femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundEndAgeIndex + 1)) - femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundEndAgeIndex))) *
                                ((endAge - orderedAges.get(bestLowerBoundEndAgeIndex)) / (orderedAges.get(bestLowerBoundEndAgeIndex + 1) - orderedAges.get(bestLowerBoundEndAgeIndex)));
                areaUnderInfantCMCurve += (femaleInfantMortality.get(year).get(orderedAges.get(bestLowerBoundStartAgeIndex)) + estimatedEndingCM) / 2 * (endAge - orderedAges.get(bestLowerBoundStartAgeIndex));
            }
            return areaUnderInfantCMCurve;
        }
    }

    //Estimate population pyramid with 1 year age gaps
    public void createPopulationPyramid(List<String> partitionNames, double[] populationByPartition) {
        malePopulationPyramid = new HashMap<>();
        femalePopulationPyramid = new HashMap<>();
        oldestPyramidCohortAge = 0;
        for (int i = 0; i < partitionNames.size(); i++) {
            List<Integer> sexAgeInfo = parseSexAgeGroup(partitionNames.get(i));
            if (sexAgeInfo.get(2) > oldestPyramidCohortAge) oldestPyramidCohortAge = sexAgeInfo.get(2);
            if (sexAgeInfo.get(0) == 0) {
                malePopulationPyramid.put(Arrays.asList(sexAgeInfo.get(1), sexAgeInfo.get(2)), populationByPartition[i]);
            } else {
                femalePopulationPyramid.put(Arrays.asList(sexAgeInfo.get(1), sexAgeInfo.get(2)), populationByPartition[i]);
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

    public static double getAgeSpecificIncidence(int age, int oldestCohortAge, Map<Integer, Double> currentYearData) {
        return currentYearData.get(Math.min(age, oldestCohortAge));
    }

    public double getAgeSpecificFertility(int age, int oldestCohortAge, Map<Integer, Double> currentYearData) {
        if (age > oldestCohortAge) {
            return 0.0;
        } else {
            return currentYearData.get(Math.min(age, oldestCohortAge));
        }
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

    //Computes integral using a function represented by map: (age in months -> cumulative mortality by that age)
    public static double doubleIntegral(double[][] function, String lowerBoundX, String upperBoundX, int xCount, double lowerBoundY, double upperBoundY, int yCount) {
        CountDownLatch latch = new CountDownLatch(taskCount);
        Map<Integer, List<Integer>> partitionedYCount = MultithreadingUtils.orderedPartitionList(IntStream.range(0, yCount).boxed().collect(Collectors.toList()), taskCount);
        //Integrate with respect to x
        double[] numericalIntegralWrtX = new double[yCount];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                for (int j : partitionedYCount.get(finalI)){
                    numericalIntegralWrtX[j] = 0;
                    double lowerX = parseBound(lowerBoundX)[0] * (lowerBoundY + j / ((double) yCount - 1) * (upperBoundY - lowerBoundY)) + parseBound(lowerBoundX)[1];
                    double upperX = parseBound(upperBoundX)[0] * (lowerBoundY + j / ((double) yCount - 1) * (upperBoundY - lowerBoundY)) + parseBound(upperBoundX)[1];
                    for (int k = 0; k < xCount; k++) {
                        if (k == 0 || k == xCount - 1) {
                            numericalIntegralWrtX[j] += function[k][j];
                        } else if (k % 2 == 1) {
                            numericalIntegralWrtX[j] += 4 * function[k][j];
                        } else {
                            numericalIntegralWrtX[j] += 2 * function[k][j];
                        }
                    }
                    numericalIntegralWrtX[j] *= (upperX - lowerX) / (xCount - 1) / 3;
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (Exception e) {
            throw new AssertionError("Latch await exception");
        }
        //Integrate with respect to y
        double numericalIntegral = 0;
        for (int j = 0; j < yCount; j++) {
            if (j == 0 || j == yCount - 1) {
                numericalIntegral += numericalIntegralWrtX[j];
            } else if (j % 2 == 1) {
                numericalIntegral += 4 * numericalIntegralWrtX[j];
            } else {
                numericalIntegral += 2 * numericalIntegralWrtX[j];
            }
        }
        return numericalIntegral * (upperBoundY - lowerBoundY) / (yCount - 1) / 3;
    }

    //Transform (map: age -> cumulative mortality by that age) to double[][] for computation of integral
    //compositeFunction g: R^2 -> R of the form (x,y) -> ax + by + c is composed with map: R -> R to make map(g): R^2 -> R
    public static double[][] mapToDoubleArray(Map<Double, Double> map, String compositeFunction, String lowerBoundX, String upperBoundX, int xCount, double lowerBoundY, double upperBoundY, int yCount) {
        CountDownLatch latch = new CountDownLatch(taskCount);
        Map<Integer, List<Integer>> partitionedYCount = MultithreadingUtils.orderedPartitionList(IntStream.range(0, yCount).boxed().collect(Collectors.toList()), taskCount);
        double[][] outputArray = new double[xCount][yCount];
        double[] lowerBoundXCoefficients = parseBound(lowerBoundX);
        double[] upperBoundXCoefficients = parseBound(upperBoundX);
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                for (int j : partitionedYCount.get(finalI)) {
                    double lowerX = lowerBoundXCoefficients[0] * (lowerBoundY + j / ((double) yCount - 1) * (upperBoundY - lowerBoundY)) + lowerBoundXCoefficients[1];
                    double upperX = upperBoundXCoefficients[0] * (lowerBoundY + j / ((double) yCount - 1) * (upperBoundY - lowerBoundY)) + upperBoundXCoefficients[1];
                    double y = lowerBoundY + (upperBoundY - lowerBoundY) * j / ((double) yCount - 1);
                    for (int k = 0; k < xCount; k++) {
                        double x = lowerX + (upperX - lowerX) * k / ((double) xCount - 1);
                        outputArray[k][j] = computeComposition(map, compositeFunction, x, y);
                    }
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (Exception e) {
            throw new AssertionError("Latch await exception");
        }
        return outputArray;
    }

    //Computes map(g(x, y)) for compositeFunction g: R2 -> R given by g(x,y) = ax + by + c
    private static double computeComposition(Map<Double, Double> map, String compositeFunction, double x, double y) {
        double[] compositeFunctionCoefficients = parseCoefficients(compositeFunction);
        return evaluateInterpolatedMap(map, compositeFunctionCoefficients[0] * x + compositeFunctionCoefficients[1] * y + compositeFunctionCoefficients[2]);
    }

    //Evaluates map(x) by interpolation
    private static double evaluateInterpolatedMap(Map<Double, Double> map, double x) {
        List<Double> orderedAges = new ArrayList<>(map.keySet());
        Collections.sort(orderedAges);
        int bestLowerBoundIndex = -1;
        for (int i = 0; i < orderedAges.size(); i++) {
            if (orderedAges.get(i) <= x) {
                bestLowerBoundIndex = i;
            }
        }
        if (bestLowerBoundIndex == orderedAges.size() - 1) { //x is at least oldest age with known cumulative mortality
            return map.get(orderedAges.get(bestLowerBoundIndex));
        } else {
            return map.get(orderedAges.get(bestLowerBoundIndex)) + (map.get(orderedAges.get(bestLowerBoundIndex + 1)) - map.get(orderedAges.get(bestLowerBoundIndex))) *
                    ((x - orderedAges.get(bestLowerBoundIndex)) / (orderedAges.get(bestLowerBoundIndex + 1) - orderedAges.get(bestLowerBoundIndex)));
        }
    }

    //Computes firstArray_(i,j) + secondArray_(i,j) for all (i,j)
    private static double[][] addArrays(double[][] firstArray, double[][] secondArray){
        CountDownLatch latch = new CountDownLatch(taskCount);
        Map<Integer, List<Integer>> partitionedArrayIndex = MultithreadingUtils.orderedPartitionList(IntStream.range(0, firstArray.length).boxed().collect(Collectors.toList()), taskCount);
        double[][] output = new double[firstArray.length][firstArray[0].length];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                for (int j : partitionedArrayIndex.get(finalI)) {
                    for (int k = 0; k < firstArray[0].length; k++) {
                        output[j][k] = firstArray[j][k] + secondArray[j][k];
                    }
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (Exception e) {
            throw new AssertionError("Latch await exception");
        }
        return output;
    }

    //Computes firstArray_(i,j) - secondArray_(i,j) for all (i,j)
    private static double[][] subtractArrays(double[][] firstArray, double[][] secondArray){
        CountDownLatch latch = new CountDownLatch(taskCount);
        Map<Integer, List<Integer>> partitionedArrayIndex = MultithreadingUtils.orderedPartitionList(IntStream.range(0, firstArray.length).boxed().collect(Collectors.toList()), taskCount);
        double[][] output = new double[firstArray.length][firstArray[0].length];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                for (int j : partitionedArrayIndex.get(finalI)) {
                    for (int k = 0; k < firstArray[0].length; k++) {
                        output[j][k] = firstArray[j][k] - secondArray[j][k];
                    }
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (Exception e) {
            throw new AssertionError("Latch await exception");
        }
        return output;
    }

    //Computes firstArray_(i,j) * secondArray_(i,j) for all (i,j)
    private static double[][] multiplyArrays(double[][] firstArray, double[][] secondArray){
        CountDownLatch latch = new CountDownLatch(taskCount);
        Map<Integer, List<Integer>> partitionedArrayIndex = MultithreadingUtils.orderedPartitionList(IntStream.range(0, firstArray.length).boxed().collect(Collectors.toList()), taskCount);
        double[][] output = new double[firstArray.length][firstArray[0].length];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                for (int j : partitionedArrayIndex.get(finalI)) {
                    for (int k = 0; k < firstArray[0].length; k++) {
                        output[j][k] = firstArray[j][k] * secondArray[j][k];
                    }
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (Exception e) {
            throw new AssertionError("Latch await exception");
        }
        return output;
    }

    //Computes firstArray_(i,j) / secondArray_(i,j) for all (i,j)
    private static double[][] divideArrays(double[][] firstArray, double[][] secondArray){
        CountDownLatch latch = new CountDownLatch(taskCount);
        Map<Integer, List<Integer>> partitionedArrayIndex = MultithreadingUtils.orderedPartitionList(IntStream.range(0, firstArray.length).boxed().collect(Collectors.toList()), taskCount);
        double[][] output = new double[firstArray.length][firstArray[0].length];
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                for (int j : partitionedArrayIndex.get(finalI)) {
                    for (int k = 0; k < firstArray[0].length; k++) {
                        output[j][k] = firstArray[j][k] / secondArray[j][k];
                    }
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (Exception e) {
            throw new AssertionError("Latch await exception");
        }
        return output;
    }

    //Creates 2d array of c^(ax+by+k)
    private static double[][] exponentiateArray(double c, String compositeFunction, String lowerBoundX, String upperBoundX, int xCount, double lowerBoundY, double upperBoundY, int yCount){
        CountDownLatch latch = new CountDownLatch(taskCount);
        Map<Integer, List<Integer>> partitionedYCount = MultithreadingUtils.orderedPartitionList(IntStream.range(0, yCount).boxed().collect(Collectors.toList()), taskCount);
        double[][] outputArray = new double[xCount][yCount];
        double[] compositeFunctionCoefficients = parseCoefficients(compositeFunction); //ax + by + c to [a, b, c]
        double[] lowerBoundXCoefficients = parseBound(lowerBoundX);
        double[] upperBoundXCoefficients = parseBound(upperBoundX);
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.execute(() -> {
                for (int j : partitionedYCount.get(finalI)) {
                    double lowerX = lowerBoundXCoefficients[0] * (lowerBoundY + j / ((double) yCount - 1) * (upperBoundY - lowerBoundY)) + lowerBoundXCoefficients[1];
                    double upperX = upperBoundXCoefficients[0] * (lowerBoundY + j / ((double) yCount - 1) * (upperBoundY - lowerBoundY)) + upperBoundXCoefficients[1];
                    double y = lowerBoundY + (upperBoundY - lowerBoundY) * j / ((double) yCount - 1);
                    for (int k = 0; k < xCount; k++) {
                        double x = lowerX + (upperX - lowerX) * k / ((double) xCount - 1);
                        outputArray[k][j] = Math.pow(c, compositeFunctionCoefficients[0] * x + compositeFunctionCoefficients[1] * y + compositeFunctionCoefficients[2]);
                    }
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (Exception e) {
            throw new AssertionError("Latch await exception");
        }
        return outputArray;
    }


    //Parses x bound as function of y of the form ay + b
    public static double[] parseBound(String bound) {
        double[] parsedBounds = new double[2]; //parsedBounds[0] = a, parsedBounds[1] = b
        bound.replaceAll("\\s", "");

        //Account for leading negative coefficient
        double operationMultiplier;
        if (bound.indexOf("-") == 0) {
            operationMultiplier = -1;
            bound = bound.substring(1);
        } else {
            operationMultiplier = 1;
        }

        //Iterate through remainder of function except for final term
        while (Math.max(bound.indexOf("+"), bound.indexOf("-")) != -1) {
            int addPosition = bound.indexOf("+");
            int subtractPosition = bound.indexOf("-");
            int nextAddOrSubtractPosition;
            if (addPosition == -1) {
                nextAddOrSubtractPosition = subtractPosition;
            } else if (subtractPosition == -1) {
                nextAddOrSubtractPosition = addPosition;
            } else {
                nextAddOrSubtractPosition = Math.min(addPosition, subtractPosition);
            }
            int yPosition = bound.indexOf("y");
            if (yPosition >= 0 && yPosition < nextAddOrSubtractPosition) {
                if (yPosition == 0) {
                    parsedBounds[0] = 1 * operationMultiplier;
                } else {
                    parsedBounds[0] = Double.parseDouble(bound.substring(0, yPosition)) * operationMultiplier;
                }
            } else {
                parsedBounds[1] = Double.parseDouble(bound.substring(0, nextAddOrSubtractPosition)) * operationMultiplier;
            }
            bound = bound.substring(nextAddOrSubtractPosition + 1);
            if (addPosition == nextAddOrSubtractPosition) {
                operationMultiplier = 1;
            } else {
                operationMultiplier = -1;
            }
        }

        //Process final term
        int yPosition = bound.indexOf("y");
        if (yPosition >= 0) {
            if (yPosition == 0) {
                parsedBounds[0] = 1 * operationMultiplier;
            } else {
                parsedBounds[0] = Double.parseDouble(bound.substring(0, yPosition)) * operationMultiplier;
            }
        } else {
            parsedBounds[1] = Double.parseDouble(bound) * operationMultiplier;
        }

        return parsedBounds;
    }

    //Parse coefficients [a, b, c] from ax+by+c (can be in any order)
    private static double[] parseCoefficients(String compositeFunction) {
        double[] compositeFunctionCoefficients = new double[3]; //g(x,y) = ax + by + c
        compositeFunction = compositeFunction.replaceAll("\\s", "");
        double operationMultiplier;
        if (compositeFunction.indexOf("-") == 0) {
            operationMultiplier = -1;
            compositeFunction = compositeFunction.substring(1);
        } else {
            operationMultiplier = 1;
        }
        while (Math.max(compositeFunction.indexOf("+"), compositeFunction.indexOf("-")) != -1) {
            int addPosition = compositeFunction.indexOf("+");
            int subtractPosition = compositeFunction.indexOf("-");
            int nextAddOrSubtractPosition;
            if (addPosition == -1) {
                nextAddOrSubtractPosition = subtractPosition;
            } else if (subtractPosition == -1) {
                nextAddOrSubtractPosition = addPosition;
            } else {
                nextAddOrSubtractPosition = Math.min(addPosition, subtractPosition);
            }
            int xPosition = compositeFunction.indexOf("x");
            int yPosition = compositeFunction.indexOf("y");
            if (xPosition >= 0 && xPosition < nextAddOrSubtractPosition) {
                if (xPosition == 0) {
                    compositeFunctionCoefficients[0] += 1 * operationMultiplier;
                } else {
                    compositeFunctionCoefficients[0] += Double.parseDouble(compositeFunction.substring(0, xPosition)) * operationMultiplier;
                }
            } else if (yPosition >= 0 && yPosition < nextAddOrSubtractPosition) {
                if (yPosition == 0) {
                    compositeFunctionCoefficients[1] += 1 * operationMultiplier;
                } else {
                    compositeFunctionCoefficients[1] += Double.parseDouble(compositeFunction.substring(0, yPosition)) * operationMultiplier;
                }
            } else {
                compositeFunctionCoefficients[2] += Double.parseDouble(compositeFunction.substring(0, nextAddOrSubtractPosition)) * operationMultiplier;
            }
            compositeFunction = compositeFunction.substring(nextAddOrSubtractPosition + 1);
            if (addPosition == nextAddOrSubtractPosition) {
                operationMultiplier = 1;
            } else {
                operationMultiplier = -1;
            }
        }
        int xPosition = compositeFunction.indexOf("x");
        int yPosition = compositeFunction.indexOf("y");
        if (xPosition >= 0) {
            if (xPosition == 0) {
                compositeFunctionCoefficients[0] += 1 * operationMultiplier;
            } else {
                compositeFunctionCoefficients[0] += Double.parseDouble(compositeFunction.substring(0, xPosition)) * operationMultiplier;
            }
        } else if (yPosition >= 0) {
            if (yPosition == 0) {
                compositeFunctionCoefficients[1] += 1 * operationMultiplier;
            } else {
                compositeFunctionCoefficients[1] += Double.parseDouble(compositeFunction.substring(0, yPosition)) * operationMultiplier;
            }
        } else {
            compositeFunctionCoefficients[2] += Double.parseDouble(compositeFunction) * operationMultiplier;
        }
        return compositeFunctionCoefficients;
    }

    public List<Object> parseYearHeadingCSV(String fileLocation, double perPopulation) {
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
                List<Integer> sexAgeInfo = parseSexAgeGroup(values[0]);
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
        return Arrays.asList(maleData, femaleData, maxAgeCohort);
    }

    //Cumulative infant mortality CSV with year headings
    public List<Object> parseCumulativeInfantMortalityCSV(String fileLocation, double perPopulation) {
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
                List<Double> sexAgeInfo = parseInfantSexAge(values[0]);
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
        return Arrays.asList(maleData, femaleData);
    }

    public List<Object> parseAgeSexHeadingCSV(String fileLocation, double perPopulation) {
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
            List<List<Integer>> sexAgeHeadings = headings.stream().map(x -> parseSexAgeGroup(x)).collect(Collectors.toList());
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
        return Arrays.asList(maleData, femaleData, maxAgeCohort);
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

    //Reads population partition and outputs list of 3 integers: sex (0 male or 1 female), lower age bound, upper age bound
    public List<Double> parseInfantSexAge(String sexAge) {
        List<Double> output = new ArrayList<>();
        if (sexAge.contains("M")) {
            output.add(0.0);
        } else {
            output.add(1.0);
        }
        Double age = Double.valueOf(sexAge.replaceAll("[^\\d.]", ""));
        output.add(age);
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