import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PopulationProjector {
    //Multithreading configuration
    static int threadCount = 6;
    static int taskCount = 6;
    static ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    //Population projection parameters
    public static PopulationParameters projectionParameters;

    //Projection parameters
    static int singleXCount = 100;
    static int xCount = 100;
    static int yCount = 100;

    public PopulationProjector(int oldestPyramidCohortAge) {
        //File locations
        String mortalityLocation = "M:\\Optimization Project\\demographic projections\\alberta_mortality.csv";
        String infantMortalityLocation = "M:\\Optimization Project\\demographic projections\\test_alberta_infant_mortality.csv";
        String fertilityLocation = "M:\\Optimization Project\\demographic projections\\alberta_fertility.csv";
        String migrationLocation = "M:\\Optimization Project\\demographic projections\\alberta_migration.csv";

        projectionParameters = new PopulationParameters(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, "Total migrants", oldestPyramidCohortAge);
    }

    public static void main(String[] args) {
        //Demographics file
        String demographicsLocation = "M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv";
        List<String> ageAndSexGroups = FileUtils.getCSVHeadings(demographicsLocation);
        double[] populationByAgeAndSexGroup = FileUtils.getInnerDoubleArrayFromCSV(demographicsLocation, FileUtils.getOriginCount(demographicsLocation), FileUtils.getSitesCount(demographicsLocation))[0];
        new PopulationProjector(Population.determineOldestPyramidCohortAge(ageAndSexGroups));
        //In final program, this will be input into projector
        Population initialPopulation = new Population(2000, ageAndSexGroups, populationByAgeAndSexGroup,
                projectionParameters.getMaleMortality(), projectionParameters.getFemaleMortality(),
                projectionParameters.getMaleMigration(), projectionParameters.getFemaleMigration(), projectionParameters.getMigrationFormat(),
                projectionParameters.getMaleInfantSeparationFactor(), projectionParameters.getFemaleInfantSeparationFactor());
        System.out.println("Male pyramid " + initialPopulation.getMalePyramid());
        System.out.println("Female pyramid " + initialPopulation.getFemalePyramid());

        String compositeFunction = "2y + 3x";
        String lowerBoundX = "0.2y";
        String upperBoundX = "0.5y + 1";
        double lowerBoundY = 0;
        double upperBoundY = 1;
        double[][] infantMortality = PopulationCalculator.mapToTwoDimensionalArray(projectionParameters.getMaleInfantCumulativeMortality().get(2000), compositeFunction, lowerBoundX, upperBoundX, 1000, lowerBoundY, upperBoundY, 1000);
        System.out.println(infantMortality[999][999]); //should be 2*1 + 3*(0.5+1) = 6.5 squared
        System.out.println(PopulationCalculator.doubleIntegral(infantMortality, lowerBoundX, upperBoundX, 1000, lowerBoundY, upperBoundY, 1000));
        executor.shutdown();
    }

    //Project population
    public Map<Integer, Population> projectPopulation(Population initialPopulation, int initialYear, int finalYear) {
        return null;
    }

    //When previous year population is not known
    public Population projectNextYearPopulation(Population currentYearPopulation) {
        int year = currentYearPopulation.getYear();
        Map<Integer, Double> currentMalePyramid = currentYearPopulation.getMalePyramid();
        Map<Integer, Double> currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        Map<Integer, Double> nextYearMalePyramid = new HashMap<>();
        Map<Integer, Double> nextYearFemalePyramid = new HashMap<>();

        //Compute male cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (Integer age : currentMalePyramid.keySet()) {
            if (age == 0) {
                continue;
            } else if (age < oldestPyramidCohortAge) {
                nextYearMalePyramid.put(age + 1, ruralUrbanProjectOtherCohortPopulation(age, year, currentMalePyramid.get(age), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration()));
            }
        }
        //Add in remaining max age cohort that continues to survive
        double oldestMalePyramidCohortPopulation = nextYearMalePyramid.get(oldestPyramidCohortAge);
        oldestMalePyramidCohortPopulation += ruralUrbanProjectRemainingOldestCohortPopulation(oldestPyramidCohortAge, year, currentMalePyramid.get(oldestPyramidCohortAge), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramid.put(oldestPyramidCohortAge, oldestMalePyramidCohortPopulation);

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (Integer age : currentFemalePyramid.keySet()) {
            if (age == 0) {
                continue;
            } else if (age < oldestPyramidCohortAge) {
                currentFemalePyramid.put(age + 1, ruralUrbanProjectOtherCohortPopulation(age, year, currentFemalePyramid.get(age), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration()));
            }
        }
        //Add in remaining max age cohort that continues to survive
        double oldestFemalePyramidCohortPopulation = nextYearFemalePyramid.get(oldestPyramidCohortAge);
        oldestFemalePyramidCohortPopulation += ruralUrbanProjectRemainingOldestCohortPopulation(oldestPyramidCohortAge, year, nextYearFemalePyramid.get(oldestPyramidCohortAge), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramid.put(oldestPyramidCohortAge, oldestFemalePyramidCohortPopulation);

        //Project male and female births for remainder of current year and first half of next year
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirths = projectBirths(year + 1, nextYearFemalePyramid);

        //Project age 0 and 1 male cohort
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearMaleBirths = nextYearBirths * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        nextYearMalePyramid.put(0, projectAgeZeroPopulation(year, currentMalePyramid.get(0), currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMigration()));
        nextYearMalePyramid.put(1, ruralUrbanProjectAgeOnePopulation(year, currentMalePyramid.get(0), nextYearMalePyramid.get(0), projectionParameters.getMaleInfantSeparationFactor(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration()));

        //Project age 0 and 1 female cohort
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearFemaleBirths = nextYearBirths / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        nextYearFemalePyramid.put(0, projectAgeZeroPopulation(year, currentFemalePyramid.get(0), currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMigration()));
        nextYearFemalePyramid.put(1, ruralUrbanProjectAgeOnePopulation(year, currentFemalePyramid.get(0), nextYearFemalePyramid.get(0), projectionParameters.getFemaleInfantSeparationFactor(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration()));

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //When previous year population is known
    public Population projectNextYearPopulation(Population currentYearPopulation, Population lastYearPopulation) {
        int year = currentYearPopulation.getYear();
        Map<Integer, Double> currentMalePyramid = currentYearPopulation.getMalePyramid();
        Map<Integer, Double> currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        Map<Integer, Double> nextYearMalePyramid = new HashMap<>();
        Map<Integer, Double> nextYearFemalePyramid = new HashMap<>();

        //Compute male cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (Integer age : currentMalePyramid.keySet()) {
            if (age == 0) {
                continue;
            } else if (age < oldestPyramidCohortAge) {
                nextYearMalePyramid.put(age + 1, ruralUrbanProjectOtherCohortPopulation(age, year, currentMalePyramid.get(age), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration()));
            }
        }
        //Add in remaining max age cohort that continues to survive
        double oldestMalePyramidCohortPopulation = nextYearMalePyramid.get(oldestPyramidCohortAge);
        oldestMalePyramidCohortPopulation += ruralUrbanProjectRemainingOldestCohortPopulation(oldestPyramidCohortAge, year, currentMalePyramid.get(oldestPyramidCohortAge), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramid.put(oldestPyramidCohortAge, oldestMalePyramidCohortPopulation);

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (Integer age : currentFemalePyramid.keySet()) {
            if (age == 0) {
                continue;
            } else if (age < oldestPyramidCohortAge) {
                currentFemalePyramid.put(age + 1, ruralUrbanProjectOtherCohortPopulation(age, year, currentFemalePyramid.get(age), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration()));
            }
        }
        //Add in remaining max age cohort that continues to survive
        double oldestFemalePyramidCohortPopulation = nextYearFemalePyramid.get(oldestPyramidCohortAge);
        oldestFemalePyramidCohortPopulation += ruralUrbanProjectRemainingOldestCohortPopulation(oldestPyramidCohortAge, year, nextYearFemalePyramid.get(oldestPyramidCohortAge), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramid.put(oldestPyramidCohortAge, oldestFemalePyramidCohortPopulation);

        //Project male and female births for remainder of current year and first half of next year
        double lastYearBirths = projectBirths(year - 1, lastYearPopulation.getFemalePyramid());
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirths = projectBirths(year + 1, nextYearFemalePyramid);

        //Project age 0 and 1 male cohort
        double lastYearMaleBirths = lastYearBirths * projectionParameters.getHumanSexRatio(year - 1) / (projectionParameters.getHumanSexRatio(year - 1)  + 1);
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearMaleBirths = nextYearBirths * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        nextYearMalePyramid.put(0, projectAgeZeroPopulation(year, currentMalePyramid.get(0), currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMigration()));
        nextYearMalePyramid.put(1, projectAgeOnePopulation(year, lastYearPopulation.getMalePyramid().get(1), currentMalePyramid.get(1), lastYearMaleBirths, currentYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration()));

        //Project age 0 and 1 female cohort
        double lastYearFemaleBirths = lastYearBirths / (projectionParameters.getHumanSexRatio(year - 1)  + 1);
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearFemaleBirths = nextYearBirths / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        nextYearFemalePyramid.put(0, projectAgeZeroPopulation(year, currentFemalePyramid.get(0), currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMigration()));
        nextYearFemalePyramid.put(1, projectAgeOnePopulation(year, lastYearPopulation.getFemalePyramid().get(1), currentFemalePyramid.get(1), lastYearFemaleBirths, currentYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration()));

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //When previous year population is not known
    public Population projectNextYearPopulationWithMigrationRates(Population currentYearPopulation) {
        int year = currentYearPopulation.getYear();
        Map<Integer, Double> currentMalePyramid = currentYearPopulation.getMalePyramid();
        Map<Integer, Double> currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        double[] variableAgeOnePopulation = {1, 0}; //next year age one population
        Map<Integer, Double> nextYearMalePyramidVariable = new HashMap<>();
        Map<Integer, Double> nextYearMalePyramidConstant = new HashMap<>();
        Map<Integer, Double> nextYearFemalePyramidVariable = new HashMap<>();
        Map<Integer, Double> nextYearFemalePyramidConstant = new HashMap<>();

        //Compute male cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        double[] variableLastAgePopulation = variableAgeOnePopulation.clone();
        for (int age = 1; age < oldestPyramidCohortAge - 1; age++) {
            variableLastAgePopulation = projectOtherCohortPopulation(age, year, variableLastAgePopulation, currentMalePyramid.get(age), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
            nextYearMalePyramidVariable.put(age + 1, variableLastAgePopulation[0]);
            nextYearMalePyramidConstant.put(age + 1, variableLastAgePopulation[1]);
        }
        //Add in remaining max age cohort that continues to survive
        variableLastAgePopulation = projectOldestCohortPopulation(oldestPyramidCohortAge, year, variableLastAgePopulation, currentMalePyramid.get(oldestPyramidCohortAge - 1), currentMalePyramid.get(oldestPyramidCohortAge), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable.put(oldestPyramidCohortAge, variableLastAgePopulation[0]);
        nextYearMalePyramidConstant.put(oldestPyramidCohortAge, variableLastAgePopulation[1]);

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        variableLastAgePopulation = variableAgeOnePopulation.clone();
        for (int age = 1; age < oldestPyramidCohortAge - 1; age++) {
            variableLastAgePopulation = projectOtherCohortPopulation(age, year, variableLastAgePopulation, currentFemalePyramid.get(age), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
            nextYearFemalePyramidVariable.put(age + 1, variableLastAgePopulation[0]);
            nextYearFemalePyramidConstant.put(age + 1, variableLastAgePopulation[1]);
        }
        //Add in remaining max age cohort that continues to survive
        variableLastAgePopulation = projectOldestCohortPopulation(oldestPyramidCohortAge, year, variableLastAgePopulation, currentFemalePyramid.get(oldestPyramidCohortAge - 1), currentFemalePyramid.get(oldestPyramidCohortAge), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable.put(oldestPyramidCohortAge, variableLastAgePopulation[0]);
        nextYearFemalePyramidConstant.put(oldestPyramidCohortAge, variableLastAgePopulation[1]);

        //Project male and female births for remainder of current year and first half of next year
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirthsVariable = projectBirths(year + 1, nextYearFemalePyramidVariable);
        double nextYearBirthsConstant = projectBirths(year + 1, nextYearFemalePyramidConstant);

        //Project age 0 male cohort
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearMaleBirthsVariable = nextYearBirthsVariable * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        double nextYearMaleBirthsConstant = nextYearBirthsConstant * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        double[] nextYearMaleBirths = {nextYearMaleBirthsVariable, nextYearMaleBirthsConstant};
        variableLastAgePopulation = projectAgeZeroPopulation(year, currentMalePyramid.get(0), currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable.put(0, variableLastAgePopulation[0]);
        nextYearMalePyramidConstant.put(0, variableLastAgePopulation[1]);

        //Project age 1 male cohort using other cohort method for first cycle
        variableLastAgePopulation = projectOtherCohortPopulation(0, year, variableLastAgePopulation, currentMalePyramid.get(0), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable.put(1, variableLastAgePopulation[0]);
        nextYearMalePyramidConstant.put(1, variableLastAgePopulation[1]);

        //Project age 0 female cohort
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearFemaleBirthsVariable = nextYearBirthsVariable / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        double nextYearFemaleBirthsConstant = nextYearBirthsConstant / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        double[] nextYearFemaleBirths = {nextYearFemaleBirthsVariable, nextYearFemaleBirthsConstant};
        variableLastAgePopulation = projectAgeZeroPopulation(year, currentFemalePyramid.get(0), currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable.put(0, variableLastAgePopulation[0]);
        nextYearFemalePyramidConstant.put(0, variableLastAgePopulation[1]);

        //Project age 1 female cohort using other cohort method for first cycle
        variableLastAgePopulation = projectOtherCohortPopulation(0, year, variableLastAgePopulation, currentFemalePyramid.get(0), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable.put(1, variableLastAgePopulation[0]);
        nextYearFemalePyramidConstant.put(1, variableLastAgePopulation[1]);

        Map<Integer, Double> nextYearMalePyramid = solveVariablePyramid(nextYearMalePyramidVariable, nextYearMalePyramidConstant, variableAgeOnePopulation);
        Map<Integer, Double> nextYearFemalePyramid = solveVariablePyramid(nextYearFemalePyramidVariable, nextYearFemalePyramidConstant, variableAgeOnePopulation);

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //When previous year population is known
    public Population projectNextYearPopulationWithMigrationRates(Population currentYearPopulation, Population lastYearPopulation) {
        int year = currentYearPopulation.getYear();
        Map<Integer, Double> currentMalePyramid = currentYearPopulation.getMalePyramid();
        Map<Integer, Double> currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        double[] variableAgeOnePopulation = {1, 0}; //next year age one population
        Map<Integer, Double> nextYearMalePyramidVariable = new HashMap<>();
        Map<Integer, Double> nextYearMalePyramidConstant = new HashMap<>();
        Map<Integer, Double> nextYearFemalePyramidVariable = new HashMap<>();
        Map<Integer, Double> nextYearFemalePyramidConstant = new HashMap<>();

        //Compute male cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        double[] variableLastAgePopulation = variableAgeOnePopulation.clone();
        for (int age = 1; age < oldestPyramidCohortAge - 1; age++) {
            variableLastAgePopulation = projectOtherCohortPopulation(age, year, variableLastAgePopulation, currentMalePyramid.get(age), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
            nextYearMalePyramidVariable.put(age + 1, variableLastAgePopulation[0]);
            nextYearMalePyramidConstant.put(age + 1, variableLastAgePopulation[1]);
        }
        //Add in remaining max age cohort that continues to survive
        variableLastAgePopulation = projectOldestCohortPopulation(oldestPyramidCohortAge, year, variableLastAgePopulation, currentMalePyramid.get(oldestPyramidCohortAge - 1), currentMalePyramid.get(oldestPyramidCohortAge), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable.put(oldestPyramidCohortAge, variableLastAgePopulation[0]);
        nextYearMalePyramidConstant.put(oldestPyramidCohortAge, variableLastAgePopulation[1]);

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        variableLastAgePopulation = variableAgeOnePopulation.clone();
        for (int age = 1; age < oldestPyramidCohortAge - 1; age++) {
            variableLastAgePopulation = projectOtherCohortPopulation(age, year, variableLastAgePopulation, currentFemalePyramid.get(age), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
            nextYearFemalePyramidVariable.put(age + 1, variableLastAgePopulation[0]);
            nextYearFemalePyramidConstant.put(age + 1, variableLastAgePopulation[1]);
        }
        //Add in remaining max age cohort that continues to survive
        variableLastAgePopulation = projectOldestCohortPopulation(oldestPyramidCohortAge, year, variableLastAgePopulation, currentFemalePyramid.get(oldestPyramidCohortAge - 1), currentFemalePyramid.get(oldestPyramidCohortAge), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable.put(oldestPyramidCohortAge, variableLastAgePopulation[0]);
        nextYearFemalePyramidConstant.put(oldestPyramidCohortAge, variableLastAgePopulation[1]);

        //Project male and female births for remainder of current year and first half of next year
        double lastYearBirths = projectBirths(year - 1, lastYearPopulation.getFemalePyramid());
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirthsVariable = projectBirths(year + 1, nextYearFemalePyramidVariable);
        double nextYearBirthsConstant = projectBirths(year + 1, nextYearFemalePyramidConstant);

        //Project age 0 male cohort
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearMaleBirthsVariable = nextYearBirthsVariable * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        double nextYearMaleBirthsConstant = nextYearBirthsConstant * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        double[] nextYearMaleBirths = {nextYearMaleBirthsVariable, nextYearMaleBirthsConstant};
        variableLastAgePopulation = projectAgeZeroPopulation(year, currentMalePyramid.get(0), currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable.put(0, variableLastAgePopulation[0]);
        nextYearMalePyramidConstant.put(0, variableLastAgePopulation[1]);

        //Project age 1 males using method specifically for age 1
        double lastYearMaleBirths = lastYearBirths * projectionParameters.getHumanSexRatio(year - 1) / (projectionParameters.getHumanSexRatio(year - 1)  + 1);
        variableLastAgePopulation = projectAgeOnePopulation(year, variableLastAgePopulation, lastYearPopulation.getMalePyramid().get(0), currentMalePyramid.get(0), lastYearMaleBirths, currentYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable.put(1, variableLastAgePopulation[0]);
        nextYearMalePyramidConstant.put(1, variableLastAgePopulation[1]);

        //Project age 0 female cohort
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearFemaleBirthsVariable = nextYearBirthsVariable / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        double nextYearFemaleBirthsConstant = nextYearBirthsConstant / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        double[] nextYearFemaleBirths = {nextYearFemaleBirthsVariable, nextYearFemaleBirthsConstant};
        variableLastAgePopulation = projectAgeZeroPopulation(year, currentFemalePyramid.get(0), currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable.put(0, variableLastAgePopulation[0]);
        nextYearFemalePyramidConstant.put(0, variableLastAgePopulation[1]);

        //Project age 1 female using method specifically for age 1
        double lastYearFemaleBirths = lastYearBirths / (projectionParameters.getHumanSexRatio(year - 1)  + 1);
        variableLastAgePopulation = projectAgeOnePopulation(year, variableLastAgePopulation, lastYearPopulation.getFemalePyramid().get(0), currentFemalePyramid.get(0), lastYearFemaleBirths, currentYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable.put(1, variableLastAgePopulation[0]);
        nextYearFemalePyramidConstant.put(1, variableLastAgePopulation[1]);

        Map<Integer, Double> nextYearMalePyramid = solveVariablePyramid(nextYearMalePyramidVariable, nextYearMalePyramidConstant, variableAgeOnePopulation);
        Map<Integer, Double> nextYearFemalePyramid = solveVariablePyramid(nextYearFemalePyramidVariable, nextYearFemalePyramidConstant, variableAgeOnePopulation);

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //Project next year age + 1 population using migration counts for cohorts excluding ages 0, 1, and maximal age
    public static double projectOtherCohortPopulation(int age, int year, double currentYearPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        //Surviving age + 0 to 0.5
        double survivingPopulation = PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.estimatePopulationAgeWeights(year, age, sexSpecificMortality, 0, 0.5, singleXCount),
                        PopulationCalculator.createConstantArray(Math.pow(1 - sexSpecificMortality.get(year).get(age), 0.5), singleXCount), //can be taken out of integral for performance
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age), "0.5 - x", 0, 0.5, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "x", 0, 0.5, singleXCount)),
                0, 0.5, singleXCount);
        //Surviving age + 0.5 to 1
        survivingPopulation += PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.estimatePopulationAgeWeights(year, age, sexSpecificMortality, 0.5, 1, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age), "1 - x", 0.5, 1, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age + 1), "x - 0.5", 0.5, 1, singleXCount),
                        PopulationCalculator.createConstantArray(Math.pow(1 - sexSpecificMortality.get(year + 1).get(age + 1), 0.5), singleXCount)), //can be taken out of integral for performance
                0.5, 1, singleXCount);
        //Adjust for weight
        survivingPopulation *= currentYearPopulation;
        survivingPopulation /= PopulationCalculator.integral(PopulationCalculator.estimatePopulationAgeWeights(year, age, sexSpecificMortality, 0, 1, singleXCount), 0, 1, xCount);

        //Age migrants who arrive between current year months 6 and 12 and will be age + 1 by next census
        //Those who will be age + 1 next year
        String lowerBoundX = "y - 0.5";
        String upperBoundX = "y";
        double lowerBoundY = 0.5;
        double upperBoundY = 1;
        double[][] migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgeMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age), "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age), "-x + y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "x + 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age + 1 this year
        lowerBoundX = "y";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentSecondHalfYearAgeMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age), "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age + 1), "x - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgeMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age migrants who arrive next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "y + 0.5";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, age, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgeMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age), "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "x - 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, age, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgeMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the current year between months 6 and 12 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "y - 0.5";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age + 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgePlusOneMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age + 1), "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age + 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgePlusOneMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "y + 0.5";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, age + 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgePlusOneMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, age + 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgePlusOneMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        double currentYearAgeMigrants = sexSpecificMigration.get(year).get(age);
        double nextYearAgeMigrants = sexSpecificMigration.get(year + 1).get(age);
        double currentYearAgePlusOneMigrants = sexSpecificMigration.get(year).get(age + 1);
        double nextYearAgePlusOneMigrants = sexSpecificMigration.get(year + 1).get(age + 1);
        double nextYearAgePlusOnePopulation = survivingPopulation +
                    currentYearAgeMigrants * averageCurrentSecondHalfYearAgeMigrantSurvival +
                    nextYearAgeMigrants * averageNextFirstHalfYearAgeMigrantSurvival +
                    currentYearAgePlusOneMigrants * averageCurrentSecondHalfYearAgePlusOneMigrantSurvival +
                    nextYearAgePlusOneMigrants * averageNextFirstHalfYearAgePlusOneMigrantSurvival;
        return nextYearAgePlusOnePopulation;
    }

    //Project next year age + 1 population using migration rates for cohorts excluding 0, 1, and maximal age.
    public static double[] projectOtherCohortPopulation(int age, int year, double[] nextYearAgePopulation, double currentYearPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        //Surviving age + 0 to 0.5
        double survivingPopulation = PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.estimatePopulationAgeWeights(year, age, sexSpecificMortality, 0, 0.5, singleXCount),
                        PopulationCalculator.createConstantArray(Math.pow(1 - sexSpecificMortality.get(year).get(age), 0.5), singleXCount), //can be taken out of integral for performance
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age), "0.5 - x", 0, 0.5, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "x", 0, 0.5, singleXCount)),
                0, 0.5, singleXCount);
        //Surviving age + 0.5 to 1
        survivingPopulation += PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.estimatePopulationAgeWeights(year, age, sexSpecificMortality, 0.5, 1, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age), "1 - x", 0.5, 1, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age + 1), "x - 0.5", 0.5, 1, singleXCount),
                        PopulationCalculator.createConstantArray(Math.pow(1 - sexSpecificMortality.get(year + 1).get(age + 1), 0.5), singleXCount)), //can be taken out of integral for performance
                0.5, 1, singleXCount);
        //Adjust for weight
        survivingPopulation *= currentYearPopulation;
        survivingPopulation /= PopulationCalculator.integral(PopulationCalculator.estimatePopulationAgeWeights(year, age, sexSpecificMortality, 0, 1, singleXCount), 0, 1, xCount);

        //Age migrants who arrive between current year months 6 and 12 and will be age + 1 by next census
        //Those who will be age + 1 next year
        String lowerBoundX = "y - 0.5";
        String upperBoundX = "y";
        double lowerBoundY = 0.5;
        double upperBoundY = 1;
        double[][] migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgeMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age), "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age), "-x + y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "x + 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age + 1 this year
        lowerBoundX = "y";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentSecondHalfYearAgeMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age), "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age + 1), "x - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgeMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age migrants who arrive next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "y + 0.5";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, age, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgeMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age), "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "x - 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, age, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgeMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the current year between months 6 and 12 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "y - 0.5";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age + 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgePlusOneMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(age + 1), "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, age + 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgePlusOneMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "y + 0.5";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, age + 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgePlusOneMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(age + 1), "0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, age + 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgePlusOneMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        //Compute total migrant population by age and year
        double currentYearAgeMigrants = projectMigration(age, year, currentYearPopulation, sexSpecificMigration);
        double currentYearAgePlusOneMigrants = projectMigration(age + 1, year, currentYearPopulation, sexSpecificMigration);
        double nextYearAgeMigrantsVariable = projectMigration(age, year + 1, nextYearAgePopulation[0], sexSpecificMigration); //variable portion
        double nextYearAgeMigrantsConstant = projectMigration(age, year + 1, nextYearAgePopulation[1], sexSpecificMigration); //constant portion
        //Compute final population, variable and constant portions
        double nextYearAgePlusOnePopulationVariable = nextYearAgeMigrantsVariable * averageNextFirstHalfYearAgeMigrantSurvival /
                (1 - sexSpecificMigration.get(year + 1).get(age + 1) * averageNextFirstHalfYearAgePlusOneMigrantSurvival);
        double nextYearAgePlusOnePopulationConstant = (survivingPopulation +
                currentYearAgeMigrants * averageCurrentSecondHalfYearAgeMigrantSurvival +
                currentYearAgePlusOneMigrants * averageCurrentSecondHalfYearAgePlusOneMigrantSurvival +
                nextYearAgeMigrantsConstant * averageNextFirstHalfYearAgeMigrantSurvival) /
                (1 - sexSpecificMigration.get(year + 1).get(age + 1) * averageNextFirstHalfYearAgePlusOneMigrantSurvival);
        //Create next year age + 1 population
        double[] nextYearAgePlusOnePopulation = new double[2];
        nextYearAgePlusOnePopulation[0] = nextYearAgePlusOnePopulationVariable;
        nextYearAgePlusOnePopulation[1] = nextYearAgePlusOnePopulationConstant;
        return nextYearAgePlusOnePopulation;
    }

    //Project next year's oldest cohort population using migration counts
    public static double projectOldestCohortPopulation(int oldestCohortAge, int year, double currentYearSecondOldestCohortPopulation, double currentYearOldestCohortPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        //Surviving (oldestCohortAge - 1) + 0 to 0.5
        double survivingPopulation = PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.estimatePopulationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, 0, 0.5, singleXCount),
                        PopulationCalculator.createConstantArray(Math.pow(1 - sexSpecificMortality.get(year).get(oldestCohortAge - 1), 0.5), singleXCount), //can be taken out of integral for performance
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge - 1), "0.5 - x", 0, 0.5, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "x", 0, 0.5, singleXCount)),
                0, 0.5, singleXCount);
        //Surviving (oldestCohortAge - 1) + 0.5 to 1
        survivingPopulation += PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.estimatePopulationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, 0.5, 1, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge), "1 - x", 0.5, 1, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge), "x - 0.5", 0.5, 1, singleXCount),
                        PopulationCalculator.createConstantArray(Math.pow(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), 0.5), singleXCount)), //can be taken out of integral for performance
                0.5, 1, singleXCount);
        //Adjust for weight
        survivingPopulation *= currentYearSecondOldestCohortPopulation;
        survivingPopulation /= PopulationCalculator.integral(PopulationCalculator.estimatePopulationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, 0, 1, singleXCount), 0, 1, xCount);

        //Surviving oldestCohortAge population
        survivingPopulation += currentYearOldestCohortPopulation * Math.pow(1 - sexSpecificMortality.get(year).get(oldestCohortAge), 0.5) * Math.pow(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), 0.5);

        //Age migrants who arrive between current year months 6 and 12 and will be age + 1 by next census
        //Those who will be age + 1 next year
        String lowerBoundX = "y - 0.5";
        String upperBoundX = "y";
        double lowerBoundY = 0.5;
        double upperBoundY = 1;
        double[][] migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearSecondOldestMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge - 1), "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge - 1), "-x + y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "x + 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age + 1 this year
        lowerBoundX = "y";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentSecondHalfYearSecondOldestMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge - 1), "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge), "x - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearSecondOldestMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age migrants who arrive next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "y + 0.5";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, oldestCohortAge - 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearSecondOldestMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge - 1), "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "x - 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, oldestCohortAge - 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearSecondOldestMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the current year between months 6 and 12 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearOldestMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge), "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearOldestMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, oldestCohortAge, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearOldestMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, oldestCohortAge, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearOldestMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        double currentYearSecondOldestMigrants = sexSpecificMigration.get(year).get(oldestCohortAge - 1);
        double nextYearSecondOldestMigrants = sexSpecificMigration.get(year + 1).get(oldestCohortAge - 1);
        double currentYearOldestMigrants = sexSpecificMigration.get(year).get(oldestCohortAge);
        double nextYearOldestMigrants = sexSpecificMigration.get(year + 1).get(oldestCohortAge);
        double nextYearOldestCohortPopulation = survivingPopulation +
                    currentYearSecondOldestMigrants * averageCurrentSecondHalfYearSecondOldestMigrantSurvival +
                    nextYearSecondOldestMigrants * averageNextFirstHalfYearSecondOldestMigrantSurvival +
                    currentYearOldestMigrants * averageCurrentSecondHalfYearOldestMigrantSurvival +
                    nextYearOldestMigrants * averageNextFirstHalfYearOldestMigrantSurvival;
        return nextYearOldestCohortPopulation;
    }

    //Project next year's oldest cohort population using migration rates
    public static double[] projectOldestCohortPopulation(int oldestCohortAge, int year, double[] nextYearSecondOldestCohortPopulation, double currentYearSecondOldestCohortPopulation, double currentYearOldestCohortPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        //Surviving (oldestCohortAge - 1) + 0 to 0.5
        double survivingPopulation = PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.estimatePopulationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, 0, 0.5, singleXCount),
                        PopulationCalculator.createConstantArray(Math.pow(1 - sexSpecificMortality.get(year).get(oldestCohortAge - 1), 0.5), singleXCount), //can be taken out of integral for performance
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge - 1), "0.5 - x", 0, 0.5, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "x", 0, 0.5, singleXCount)),
                0, 0.5, singleXCount);
        //Surviving (oldestCohortAge - 1) + 0.5 to 1
        survivingPopulation += PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.estimatePopulationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, 0.5, 1, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge), "1 - x", 0.5, 1, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge), "x - 0.5", 0.5, 1, singleXCount),
                        PopulationCalculator.createConstantArray(Math.pow(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), 0.5), singleXCount)), //can be taken out of integral for performance
                0.5, 1, singleXCount);
        //Adjust for weight
        survivingPopulation *= currentYearSecondOldestCohortPopulation;
        survivingPopulation /= PopulationCalculator.integral(PopulationCalculator.estimatePopulationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, 0, 1, singleXCount), 0, 1, xCount);

        //Surviving oldestCohortAge population
        survivingPopulation += currentYearOldestCohortPopulation * Math.pow(1 - sexSpecificMortality.get(year).get(oldestCohortAge), 0.5) * Math.pow(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), 0.5);

        //Age migrants who arrive between current year months 6 and 12 and will be age + 1 by next census
        //Those who will be age + 1 next year
        String lowerBoundX = "y - 0.5";
        String upperBoundX = "y";
        double lowerBoundY = 0.5;
        double upperBoundY = 1;
        double[][] migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearSecondOldestMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge - 1), "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge - 1), "-x + y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "x + 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age + 1 this year
        lowerBoundX = "y";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentSecondHalfYearSecondOldestMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge - 1), "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge), "x - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge - 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearSecondOldestMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age migrants who arrive next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "y + 0.5";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, oldestCohortAge - 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearSecondOldestMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge - 1), "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "x - 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, oldestCohortAge - 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearSecondOldestMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the current year between months 6 and 12 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearOldestMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(oldestCohortAge), "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, oldestCohortAge, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearOldestMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, oldestCohortAge, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearOldestMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(migrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(oldestCohortAge), "0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, oldestCohortAge, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearOldestMigrantSurvival /= PopulationCalculator.doubleIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        //Compute total migrant population by age and year
        double currentYearSecondOldestMigrants = projectMigration(oldestCohortAge - 1, year, currentYearSecondOldestCohortPopulation, sexSpecificMigration);
        double currentYearOldestMigrants = projectMigration(oldestCohortAge, year, currentYearOldestCohortPopulation, sexSpecificMigration);
        double nextYearSecondOldestMigrantsVariable = projectMigration(oldestCohortAge - 1, year + 1, nextYearSecondOldestCohortPopulation[0], sexSpecificMigration);
        double nextYearSecondOldestMigrantsConstant = projectMigration(oldestCohortAge - 1, year + 1, nextYearSecondOldestCohortPopulation[1], sexSpecificMigration);
        //Compute final population, variable and constant portions
        double nextYearOldestCohortPopulationVariable = nextYearSecondOldestMigrantsVariable * averageNextFirstHalfYearSecondOldestMigrantSurvival /
                (1 - sexSpecificMigration.get(year + 1).get(oldestCohortAge) * averageNextFirstHalfYearOldestMigrantSurvival);
        double nextYearOldestCohortPopulationConstant = (survivingPopulation +
                currentYearSecondOldestMigrants * averageCurrentSecondHalfYearSecondOldestMigrantSurvival +
                currentYearOldestMigrants * averageCurrentSecondHalfYearOldestMigrantSurvival +
                nextYearSecondOldestMigrantsConstant * averageNextFirstHalfYearSecondOldestMigrantSurvival) /
                (1 - sexSpecificMigration.get(year + 1).get(oldestCohortAge) * averageNextFirstHalfYearOldestMigrantSurvival);
        //Combine into double[] population
        double[] nextYearOldestCohortPopulation = new double[2];
        nextYearOldestCohortPopulation[0] = nextYearOldestCohortPopulationVariable;
        nextYearOldestCohortPopulation[1] = nextYearOldestCohortPopulationConstant;
        return nextYearOldestCohortPopulation;
    }

    //Project age zero population using migration counts, infant CM curve.
    public static double projectAgeZeroPopulation(int year, double currentYearPopulation, double currentYearBirths, double nextYearBirths, Map<Integer, Map<Double, Double>> sexSpecificInfantCumulativeMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        //Project surviving births
        double survivingCurrentYearBirths = currentYearBirths * (PopulationCalculator.integralOfCumulativeMortality(0, 6, sexSpecificInfantCumulativeMortality.get(year))
                + PopulationCalculator.integralOfCumulativeMortality(6, 12, sexSpecificInfantCumulativeMortality.get(year + 1)) - PopulationCalculator.integralOfCumulativeMortality(0, 6, sexSpecificInfantCumulativeMortality.get(year + 1))) / 6;
        double survivingNextYearBirths = nextYearBirths * PopulationCalculator.integralOfCumulativeMortality(0,6, sexSpecificInfantCumulativeMortality.get(year + 1));

        //Project next year migrant death rates
        //Current year
        String lowerBoundX = "1";
        String upperBoundX = "y - 6";
        double lowerBoundY = 7;
        double upperBoundY = 12;
        double areaOfDomain = 12.5;
        double averageCurrentYearMigrantSurvival = PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                - PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                + PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 18 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                - PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentYearMigrantSurvival = 1 - (averageCurrentYearMigrantSurvival / areaOfDomain);
        //Next year
        lowerBoundX = "1";
        upperBoundX = "y + 6";
        lowerBoundY = 0;
        upperBoundY = 6;
        areaOfDomain = 48;
        double averageNextYearMigrantSurvival = PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 6 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                - PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageNextYearMigrantSurvival = 1 - (averageNextYearMigrantSurvival / areaOfDomain);

        //Compute next year population
        double currentYearMigrants = sexSpecificMigration.get(year).get(0) * 12.5/132; //proportion of current year age 0 migrants that are age 0 on the next census
        double nextYearMigrants = sexSpecificMigration.get(year + 1).get(0) * 48/132;
        double nextYearAgeZeroPopulation = survivingCurrentYearBirths + survivingNextYearBirths +
                    currentYearMigrants * averageCurrentYearMigrantSurvival +
                    nextYearMigrants * averageNextYearMigrantSurvival;
        return nextYearAgeZeroPopulation;
    }

    //Project age zero population using migration rates. Requires infant CM curve.
    public static double[] projectAgeZeroPopulation(int year, double currentYearPopulation, double currentYearBirths, double[] nextYearBirths, Map<Integer, Map<Double, Double>> sexSpecificInfantCumulativeMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        //Project surviving births
        double survivingCurrentYearBirths = currentYearBirths * (PopulationCalculator.integralOfCumulativeMortality(0, 6, sexSpecificInfantCumulativeMortality.get(year))
                + PopulationCalculator.integralOfCumulativeMortality(6, 12, sexSpecificInfantCumulativeMortality.get(year + 1)) - PopulationCalculator.integralOfCumulativeMortality(0, 6, sexSpecificInfantCumulativeMortality.get(year + 1))) / 6;
        double survivingNextYearBirthsVariable = nextYearBirths[0] * PopulationCalculator.integralOfCumulativeMortality(0,6, sexSpecificInfantCumulativeMortality.get(year + 1));
        double survivingNextYearBirthsConstant = nextYearBirths[1] * PopulationCalculator.integralOfCumulativeMortality(0,6, sexSpecificInfantCumulativeMortality.get(year + 1));

        //Project next year migrant death rates
        //Current year
        String lowerBoundX = "1";
        String upperBoundX = "y - 6";
        double lowerBoundY = 7;
        double upperBoundY = 12;
        double areaOfDomain = 12.5;
        double averageCurrentYearMigrantSurvival = PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                - PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                + PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 18 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                - PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentYearMigrantSurvival = 1 - (averageCurrentYearMigrantSurvival / areaOfDomain);
        //Next year
        lowerBoundX = "1";
        upperBoundX = "y + 6";
        lowerBoundY = 0;
        upperBoundY = 6;
        areaOfDomain = 48;
        double averageNextYearMigrantSurvival = PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 6 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                - PopulationCalculator.doubleIntegral(PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount), lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageNextYearMigrantSurvival = 1 - (averageNextYearMigrantSurvival / areaOfDomain);

        //Compute next year population
        //Compute migrants for this year
        double currentYearMigrants = projectMigration(0, year, currentYearPopulation, sexSpecificMigration) * 12.5/132;
        //Compute variable and constant portions of next year population
        double nextYearAgeZeroPopulationVariable = survivingNextYearBirthsVariable / (1 - sexSpecificMigration.get(year + 1).get(0) * 48/132 * averageNextYearMigrantSurvival);
        double nextYearAgeZeroPopulationConstant = (survivingCurrentYearBirths + survivingNextYearBirthsConstant + currentYearMigrants * averageCurrentYearMigrantSurvival) / (1 - sexSpecificMigration.get(year + 1).get(0) * 48/132 * averageNextYearMigrantSurvival);
        //Combine variable and constant portions of next year population
        double[] nextYearAgeZeroPopulation = new double[2];
        nextYearAgeZeroPopulation[0] = nextYearAgeZeroPopulationVariable;
        nextYearAgeZeroPopulation[1] = nextYearAgeZeroPopulationConstant;
        return nextYearAgeZeroPopulation;
    }

    //Project age one population using migration counts, infant CM.
    public static double projectAgeOnePopulation(int year, double lastYearPopulation, double currentYearPopulation, double lastYearBirths, double currentYearBirths,
                                                 Map<Integer, Map<Double, Double>> sexSpecificInfantCumulativeMortality, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        double survivingLastYearBirths = lastYearBirths * PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year - 1), "x", 0, 6, singleXCount)),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year), "12", 0, 6, singleXCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year), "x", 0, 6, singleXCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x", 0, 6, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", 0, 6, singleXCount)),
                0, 6, singleXCount) / 6;
        double survivingCurrentYearBirths = currentYearBirths * PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year), "x", 6, 12, singleXCount)),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", 6, 12, singleXCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x", 6, 12, singleXCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y + 0.5", 6, 12, singleXCount)),
                6, 12, singleXCount) / 6;

        //Project next year migrant death rates
        //Previous year age 0 migrants
        String lowerBoundX = "1";
        String upperBoundX = "y - 6";
        double lowerBoundY = 7;
        double upperBoundY = 12;
        double areaOfDomain = 12.5;
        double averageLastYearAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year - 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year - 1), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y + 1", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageLastYearAgeZeroMigrantSurvival /= 132;

        //Current first 6 months age 0 migrants
        //First month
        lowerBoundX = "1";
        upperBoundX = "y + 6";
        lowerBoundY = 0;
        upperBoundY = 1;
        areaOfDomain = 48;
        double averageCurrentFirstHalfYearAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Next 5 months, reach age 1 this year
        lowerBoundX = "y";
        upperBoundX = "y + 6";
        lowerBoundY = 1;
        upperBoundY = 6;
        areaOfDomain = 48;
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Next 5 months, reach age 1 next year
        lowerBoundX = "1";
        upperBoundX = "y";
        lowerBoundY = 1;
        upperBoundY = 6;
        areaOfDomain = 48;
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival /= 132;

        //Migrants arriving between months 6 and 7 at age 0
        //Those who will be age 1 next year
        lowerBoundX = "1";
        upperBoundX = "y";
        lowerBoundY = 6;
        upperBoundY = 7;
        areaOfDomain = 11;
        double averageCurrentMonthSixSevenAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age 1 this year
        lowerBoundX = "y";
        upperBoundX = "12";
        lowerBoundY = 6;
        upperBoundY = 7;
        areaOfDomain = 11;
        averageCurrentMonthSixSevenAgeZeroMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageCurrentMonthSixSevenAgeZeroMigrantSurvival /= 132;

        //Migrants arriving between months 7 and 12
        //Those who will be age 1 next year
        lowerBoundX = "y - 6";
        upperBoundX = "y";
        lowerBoundY = 7;
        upperBoundY = 12;
        areaOfDomain = 42.5;
        double averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age 1 this year
        lowerBoundX = "y";
        upperBoundX = "12";
        lowerBoundY = 7;
        upperBoundY = 12;
        areaOfDomain = 42.5;
        averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival /= 132;

        //Migrants who arrive next year at age 0
        lowerBoundX = "y + 6";
        upperBoundX = "12";
        lowerBoundY = 0;
        upperBoundY = 6;
        areaOfDomain = 18;
        double averageNextYearAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y - 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageNextYearAgeZeroMigrantSurvival /= 132;

        //Age 1 migrants who arrive between current year months 6 and 12
        lowerBoundX = "0";
        upperBoundX = "y - 0.5";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        double[][] sexSpecificMigrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgeOneMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(sexSpecificMigrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "-y + 1", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        sexSpecificMigrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgeOneMigrantSurvival /= PopulationCalculator.doubleIntegral(sexSpecificMigrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age 1 migrants who arrive between next year months 0 and 6
        lowerBoundX = "0";
        upperBoundX = "y + 0.5";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        sexSpecificMigrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgeOneMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(sexSpecificMigrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "-y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        sexSpecificMigrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgeOneMigrantSurvival /= PopulationCalculator.doubleIntegral(sexSpecificMigrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        double lastYearAgeZeroMigrants = sexSpecificMigration.get(year - 1).get(0); //proportion of current year age 0 migrants that are age 0 on the next census
        double currentYearAgeZeroMigrants = sexSpecificMigration.get(year).get(0);
        double nextYearAgeZeroMigrants = sexSpecificMigration.get(year + 1).get(0);
        double currentYearAgeOneMigrants = sexSpecificMigration.get(year).get(1);
        double nextYearAgeOneMigrants = sexSpecificMigration.get(year + 1).get(1);
        double nextYearAgeOnePopulation = survivingLastYearBirths + survivingCurrentYearBirths +
                    lastYearAgeZeroMigrants * averageLastYearAgeZeroMigrantSurvival +
                    currentYearAgeZeroMigrants * averageCurrentFirstHalfYearAgeZeroMigrantSurvival +
                    currentYearAgeZeroMigrants * averageCurrentMonthSixSevenAgeZeroMigrantSurvival +
                    currentYearAgeZeroMigrants * averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival +
                    nextYearAgeZeroMigrants * averageNextYearAgeZeroMigrantSurvival +
                    currentYearAgeOneMigrants * averageCurrentSecondHalfYearAgeOneMigrantSurvival +
                    nextYearAgeOneMigrants * averageNextFirstHalfYearAgeOneMigrantSurvival;
        return nextYearAgeOnePopulation;
    }

    //Project age one population using migration counts, infant CM.
    public static double[] projectAgeOnePopulation(int year, double[] nextYearAgeZeroPopulation, double lastYearAgeZeroPopulation, double currentYearPopulation, double lastYearBirths, double currentYearBirths,
                                                 Map<Integer, Map<Double, Double>> sexSpecificInfantCumulativeMortality, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        double survivingLastYearBirths = lastYearBirths * PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year - 1), "x", 0, 6, singleXCount)),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year), "12", 0, 6, singleXCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year), "x", 0, 6, singleXCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x", 0, 6, singleXCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", 0, 6, singleXCount)),
                0, 6, singleXCount) / 6;
        double survivingCurrentYearBirths = currentYearBirths * PopulationCalculator.integral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year), "x", 6, 12, singleXCount)),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", 6, 12, singleXCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, singleXCount), PopulationCalculator.mapToArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x", 6, 12, singleXCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y + 0.5", 6, 12, singleXCount)),
                6, 12, singleXCount) / 6;

        //Project next year migrant death rates
        //Previous year age 0 migrants
        String lowerBoundX = "1";
        String upperBoundX = "y - 6";
        double lowerBoundY = 7;
        double upperBoundY = 12;
        double areaOfDomain = 12.5;
        double averageLastYearAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year - 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year - 1), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y + 1", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageLastYearAgeZeroMigrantSurvival /= 132;

        //Current first 6 months age 0 migrants
        //First month
        lowerBoundX = "1";
        upperBoundX = "y + 6";
        lowerBoundY = 0;
        upperBoundY = 1;
        areaOfDomain = 48;
        double averageCurrentFirstHalfYearAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Next 5 months, reach age 1 this year
        lowerBoundX = "y";
        upperBoundX = "y + 6";
        lowerBoundY = 1;
        upperBoundY = 6;
        areaOfDomain = 48;
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Next 5 months, reach age 1 next year
        lowerBoundX = "1";
        upperBoundX = "y";
        lowerBoundY = 1;
        upperBoundY = 6;
        areaOfDomain = 48;
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival /= 132;

        //Migrants arriving between months 6 and 7 at age 0
        //Those who will be age 1 next year
        lowerBoundX = "1";
        upperBoundX = "y";
        lowerBoundY = 6;
        upperBoundY = 7;
        areaOfDomain = 11;
        double averageCurrentMonthSixSevenAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age 1 this year
        lowerBoundX = "y";
        upperBoundX = "12";
        lowerBoundY = 6;
        upperBoundY = 7;
        areaOfDomain = 11;
        averageCurrentMonthSixSevenAgeZeroMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageCurrentMonthSixSevenAgeZeroMigrantSurvival /= 132;

        //Migrants arriving between months 7 and 12
        //Those who will be age 1 next year
        lowerBoundX = "y - 6";
        upperBoundX = "y";
        lowerBoundY = 7;
        upperBoundY = 12;
        areaOfDomain = 42.5;
        double averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age 1 this year
        lowerBoundX = "y";
        upperBoundX = "12";
        lowerBoundY = 7;
        upperBoundY = 12;
        areaOfDomain = 42.5;
        averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival += PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival /= 132;

        //Migrants who arrive next year at age 0
        lowerBoundX = "y + 6";
        upperBoundX = "12";
        lowerBoundY = 0;
        upperBoundY = 6;
        areaOfDomain = 18;
        double averageNextYearAgeZeroMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(PopulationCalculator.divideArrays(PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                PopulationCalculator.subtractArrays(PopulationCalculator.createConstantArray(1, xCount, yCount), PopulationCalculator.mapToTwoDimensionalArray(sexSpecificInfantCumulativeMortality.get(year + 1), "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.0833333333333x - 0.833333333333y - 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageNextYearAgeZeroMigrantSurvival /= 132;

        //Age 1 migrants who arrive between current year months 6 and 12
        lowerBoundX = "0";
        upperBoundX = "y - 0.5";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        double[][] sexSpecificMigrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgeOneMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(sexSpecificMigrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year).get(1), "-y + 1", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        sexSpecificMigrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year, 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgeOneMigrantSurvival /= PopulationCalculator.doubleIntegral(sexSpecificMigrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age 1 migrants who arrive between next year months 0 and 6
        lowerBoundX = "0";
        upperBoundX = "y + 0.5";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        sexSpecificMigrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, 1, sexSpecificMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgeOneMigrantSurvival = PopulationCalculator.doubleIntegral(
                PopulationCalculator.multiplyArrays(sexSpecificMigrantAgeWeights,
                        PopulationCalculator.exponentiateArray(1 - sexSpecificMortality.get(year + 1).get(1), "-y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        sexSpecificMigrantAgeWeights = PopulationCalculator.estimateMigrationAgeWeights(year + 1, 1, sexSpecificMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgeOneMigrantSurvival /= PopulationCalculator.doubleIntegral(sexSpecificMigrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        //Compute annual migrant populations
        double lastYearAgeZeroMigrants = projectMigration(0, year - 1, lastYearAgeZeroPopulation, sexSpecificMigration);
        double currentYearAgeZeroMigrants = projectMigration(0, year, currentYearPopulation, sexSpecificMigration);
        double currentYearAgeOneMigrants = projectMigration(1, year, currentYearPopulation, sexSpecificMigration);
        double nextYearAgeZeroMigrantsVariable = projectMigration(0, year + 1, nextYearAgeZeroPopulation[0], sexSpecificMigration);
        double nextYearAgeZeroMigrantsConstant = projectMigration(0, year + 1, nextYearAgeZeroPopulation[1], sexSpecificMigration);
        //Compute variable and constant portions of next year age 1 population
        double nextYearAgeOnePopulationVariable = nextYearAgeZeroMigrantsVariable * averageNextYearAgeZeroMigrantSurvival /
                (1 - sexSpecificMigration.get(year + 1).get(1) * averageNextFirstHalfYearAgeOneMigrantSurvival);
        double nextYearAgeOnePopulationConstant = (survivingLastYearBirths + survivingCurrentYearBirths +
                    lastYearAgeZeroMigrants * averageLastYearAgeZeroMigrantSurvival +
                    currentYearAgeZeroMigrants * averageCurrentFirstHalfYearAgeZeroMigrantSurvival +
                    currentYearAgeZeroMigrants * averageCurrentMonthSixSevenAgeZeroMigrantSurvival +
                    currentYearAgeZeroMigrants * averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival +
                    currentYearAgeOneMigrants * averageCurrentSecondHalfYearAgeOneMigrantSurvival +
                    nextYearAgeZeroMigrantsConstant * averageNextYearAgeZeroMigrantSurvival) /
                (1 - sexSpecificMigration.get(year + 1).get(1) * averageNextFirstHalfYearAgeOneMigrantSurvival);
        //Combine variable and constant portions of next year age 1
        double[] nextYearAgeOnePopulation = new double[2];
        nextYearAgeOnePopulation[0] = nextYearAgeOnePopulationVariable;
        nextYearAgeOnePopulation[1] = nextYearAgeOnePopulationConstant;
        return nextYearAgeOnePopulation;
    }

    //Converts variable and constant population pyramids into single final population pyramid
    public HashMap<Integer, Double> solveVariablePyramid(Map<Integer, Double> pyramidVariable, Map<Integer, Double> pyramidConstant, double[] originalAgeOnePopulation) {
        //Solve age one population
        double ageOnePopulation = computeAgeOnePopulation(new double[]{pyramidVariable.get(1), pyramidConstant.get(1)}, originalAgeOnePopulation);

        //Generate new pyramid
        HashMap<Integer, Double> pyramid = new HashMap();
        for(int age : pyramidVariable.keySet()) {
            pyramid.put(age, pyramidVariable.get(age) * ageOnePopulation + pyramidConstant.get(age));
        }

        return pyramid;
    }

    //Solves ax + b = cx + d to obtain age 1 population from variable and constant portions. Normally c = 1, d = 0.
    public double computeAgeOnePopulation(double[] ageOnePopulation, double[] originalAgeOnePopulation) {
        return (ageOnePopulation[1] - originalAgeOnePopulation[1]) / (originalAgeOnePopulation[0] - ageOnePopulation[0]);
    }

    //Projects population of cohort in next year, i.e. Pop(t + 1, age + 1)
    public static double ruralUrbanProjectOtherCohortPopulation(int age, int year, double currentYearPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        double nextYearPopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearPopulation = (currentYearPopulation - 0.5 * projectDeaths(age, year, currentYearPopulation, sexSpecificMortality)
                    + 0.5 * sexSpecificMigration.get(year).get(age) + 0.5 * sexSpecificMigration.get(year + 1).get(age + 1))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(age + 1));
        } else { //migration rates
            nextYearPopulation = (currentYearPopulation - 0.5 * projectDeaths(age, year, currentYearPopulation, sexSpecificMortality)
                    + 0.5 * projectMigration(age, year, currentYearPopulation, sexSpecificMigration))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(age + 1) - 0.5 * sexSpecificMigration.get(year + 1).get(age + 1));
        }
        return nextYearPopulation;
    }

    //Projects population of the oldest cohort in next year
    public static double ruralUrbanProjectRemainingOldestCohortPopulation(int oldestCohortAge, int year, double currentYearOldestCohortPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        double nextYearPersistentPopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearPersistentPopulation = (currentYearOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge, year, currentYearOldestCohortPopulation, sexSpecificMortality)
                    + 0.5 * sexSpecificMigration.get(year).get(oldestCohortAge))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(oldestCohortAge));
        } else { //migration rates
            nextYearPersistentPopulation = (currentYearOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge, year, currentYearOldestCohortPopulation, sexSpecificMortality)
                    + 0.5 * projectMigration(oldestCohortAge, year, currentYearOldestCohortPopulation, sexSpecificMigration))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(oldestCohortAge) - 0.5 * sexSpecificMigration.get(year + 1).get(oldestCohortAge));
        }
        return nextYearPersistentPopulation;
    }

    //RUP (rural urban projection) is made based on US Census Bureau methods for Pop(t + 1, age 1)
    public static double ruralUrbanProjectAgeZeroPopulation(int year, double currentYearPopulation, double currentYearBirths, double nextYearBirths,
                                                           Map<Integer, Double> sexSpecificInfantSeparationFactor, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        double nextYearAgeOnePopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearAgeOnePopulation = 0.5 * (currentYearBirths + nextYearBirths
                    - projectDeaths(0, year, currentYearPopulation, sexSpecificMortality) * (1 - sexSpecificInfantSeparationFactor.get(year))
                    + sexSpecificMigration.get(year + 1).get(0))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(0) * (1 - sexSpecificInfantSeparationFactor.get(year + 1)));
        } else { //migration rates
            nextYearAgeOnePopulation = 0.5 * (currentYearBirths + nextYearBirths
                    - projectDeaths(0, year, currentYearPopulation, sexSpecificMortality) * (1 - sexSpecificInfantSeparationFactor.get(year)))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(0) * (1 - sexSpecificInfantSeparationFactor.get(year + 1)) - 0.5 * sexSpecificMigration.get(year + 1).get(0));
        }
        return nextYearAgeOnePopulation;
    }

    //RUP (rural urban projection) is made based on US Census Bureau methods for Pop(t + 1, age 1)
    public static double ruralUrbanProjectAgeOnePopulation(int year, double currentYearAgeZeroPopulation, double nextYearAgeZeroPopulation,
                                                           Map<Integer, Double> sexSpecificInfantSeparationFactor, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        double nextYearAgeOnePopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearAgeOnePopulation = (currentYearAgeZeroPopulation
                    - 0.5 * projectDeaths(0, year, currentYearAgeZeroPopulation, sexSpecificMortality) * sexSpecificInfantSeparationFactor.get(year)
                    - 0.5 * projectDeaths(0, year + 1, nextYearAgeZeroPopulation, sexSpecificMortality) * sexSpecificInfantSeparationFactor.get(year + 1)
                    + 0.5 * sexSpecificMigration.get(year).get(0)
                    + 0.5 * sexSpecificMigration.get(year + 1).get(1))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(1));
        } else { //migration rates
            nextYearAgeOnePopulation = (currentYearAgeZeroPopulation
                    - 0.5 * projectDeaths(0, year, currentYearAgeZeroPopulation, sexSpecificMortality) * sexSpecificInfantSeparationFactor.get(year)
                    - 0.5 * projectDeaths(0, year + 1, nextYearAgeZeroPopulation, sexSpecificMortality) * sexSpecificInfantSeparationFactor.get(year + 1)
                    + 0.5 * projectMigration(0, year, currentYearAgeZeroPopulation, sexSpecificMigration))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(1) - 0.5 * sexSpecificMigration.get(year + 1).get(1));
        }
        return nextYearAgeOnePopulation;
    }

    public static double projectDeaths(int age, int year, double atRiskPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality) {
        return atRiskPopulation * sexSpecificMortality.get(year).get(age);
    }

    public static double projectMigration(int age, int year, double atRiskPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        return atRiskPopulation * sexSpecificMigration.get(year).get(age);
    }

    public static double projectBirths(int year, Map<Integer, Double> femalePyramid) {
        double totalBirths = 0;
        for (int age = 2; age <= projectionParameters.getOldestFertilityCohortAge(); age++) { //no records of births before age 5
            totalBirths += femalePyramid.get(age) * projectionParameters.getSpecificFertility(year, age);
        }
        return totalBirths;
    }

    //Mean proportion of surviving live births from surviving through a to b months ago with constant birth rate; a < b <= 11
    //Using https://www150.statcan.gc.ca/t1/tbl1/en/tv.action?pid=1310071301 or https://www150.statcan.gc.ca/t1/tbl1/en/tv.action?pid=1310007801&pickMembers%5B0%5D=2.1&pickMembers%5B1%5D=4.2&cubeTimeFrame.startYear=2000&cubeTimeFrame.endYear=2015&referencePeriods=20000101%2C20150101
    public static double meanInfantSurvival(String sex, int year, double a, double b) {

        return 0.0;
    }
}