import java.util.*;

public class DirectPopulationProjector extends PopulationProjector{
    //Integral configuration
    static int singleXCount;
    static int xCount;
    static int yCount;

    public DirectPopulationProjector(String mortalityLocation, String infantMortalityLocation, String fertilityLocation, String migrationLocation, String migrationFormat,
                                     int oldestPyramidCohortAge, int singleXCount, int xCount, int yCount, int threadCount, int taskCount) {
        super(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, migrationFormat, oldestPyramidCohortAge, threadCount, taskCount);
        this.singleXCount = singleXCount;
        this.xCount = xCount;
        this.yCount = yCount;
    }

    public static void main(String[] args) {
        //Demographics file
        String demographicsLocation = "M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv";
        List<String> ageAndSexGroups = FileUtils.getCSVHeadings(demographicsLocation);
        double[] populationByAgeAndSexGroup = FileUtils.getInnerDoubleArrayFromCSV(demographicsLocation, FileUtils.getOriginCount(demographicsLocation), FileUtils.getSitesCount(demographicsLocation))[0];

        //File locations
        String mortalityLocation = "M:\\Optimization Project\\demographic projections\\alberta_mortality.csv";
        String infantMortalityLocation = "M:\\Optimization Project\\demographic projections\\test_alberta_infant_mortality.csv";
        String fertilityLocation = "M:\\Optimization Project\\demographic projections\\alberta_fertility.csv";
        String migrationLocation = "M:\\Optimization Project\\demographic projections\\alberta_migration.csv";
        DirectPopulationProjector projector = new DirectPopulationProjector(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, "Total migrants",
                Population.determineOldestPyramidCohortAge(ageAndSexGroups), 200000, 2000, 3000, 6, 6);

        //In final program, this will be input into projector
        Population initialPopulation = new Population(2000, ageAndSexGroups, populationByAgeAndSexGroup,
                projector.getProjectionParameters().getMaleMortality(), projector.getProjectionParameters().getFemaleMortality(),
                projector.getProjectionParameters().getMaleMigration(), projector.getProjectionParameters().getFemaleMigration(), projector.getProjectionParameters().getMigrationFormat(),
                projector.getProjectionParameters().getMaleInfantSeparationFactor(), projector.getProjectionParameters().getFemaleInfantSeparationFactor());
        //System.out.println("Male pyramid " + initialPopulation.getMalePyramid());
        //System.out.println("Female pyramid " + initialPopulation.getFemalePyramid());

        //Testing functions in calculator
        String compositeFunction = "2y + x";
        String lowerBoundX = "0.2y";
        String upperBoundX = "0.5y + 1";
        double lowerBoundY = 0;
        double upperBoundY = 1;
        double[][] infantMortality = projector.getPopulationCalculator().mapToTwoDimensionalArray(projector.getProjectionParameters().getMaleInfantCumulativeMortality().get(2000), compositeFunction, lowerBoundX, upperBoundX, projector.xCount, lowerBoundY, upperBoundY, projector.yCount);
        //System.out.println(infantMortality[999][999]);
        double[] singleMortalityArray = projector.getPopulationCalculator().mapToArray(projector.getProjectionParameters().getMaleInfantCumulativeMortality().get(2000), "3 - 2x", 0, 1, projector.singleXCount);
        System.out.println(projector.getPopulationCalculator().simpsonIntegral(singleMortalityArray, 0, 1, projector.singleXCount));
        System.out.println(projector.getPopulationCalculator().doubleSimpsonIntegral(infantMortality, lowerBoundX, upperBoundX, projector.xCount, lowerBoundY, upperBoundY, projector.yCount));

        System.out.println("Year zero female pyramid " + Arrays.toString(initialPopulation.getMalePyramid()));
        System.out.println("Start projection.");
        Population yearOnePopulation = projector.projectNextYearPopulationWithMigrationCount(initialPopulation);
        Population yearTwoPopulation = projector.projectNextYearPopulationWithMigrationCount(initialPopulation, yearOnePopulation);
        //System.out.println("Year zero male pyramid " + initialPopulation.getMalePyramid());
        //System.out.println("Year one male pyramid " + yearOnePopulation.getMalePyramid());
        //System.out.println("Year two male pyramid " + yearTwoPopulation.getMalePyramid());
        System.out.println("Year one female pyramid " + Arrays.toString(yearOnePopulation.getMalePyramid()));
        System.out.println("Year two female pyramid " + Arrays.toString(yearTwoPopulation.getMalePyramid()));

        projector.getPopulationCalculator().getExecutor().shutdown();
    }

    //Project population storing population of every year from initialYear to finalYear
    public Map<Integer, Population> projectPopulation(Population initialPopulation, int initialYear, int finalYear) {
        Map<Integer, Population> populationProjection = new HashMap<>();
        populationProjection.put(initialYear, initialPopulation);
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            populationProjection.put(initialYear + 1, projectNextYearPopulationWithMigrationCount(initialPopulation));
            for (int year = initialYear + 1; year < finalYear; year++) {
                populationProjection.put(year + 1, projectNextYearPopulationWithMigrationCount(populationProjection.get(year - 1), populationProjection.get(year)));
            }
        } else { //migration rates
            populationProjection.put(initialYear + 1, projectNextYearPopulationWithMigrationRates(initialPopulation));
            for (int year = initialYear + 1; year < finalYear; year++) {
                populationProjection.put(year + 1, projectNextYearPopulationWithMigrationRates(populationProjection.get(year - 1), populationProjection.get(year)));
            }
        }
        return populationProjection;
    }

    //When previous year population is not known
    public Population projectNextYearPopulationWithMigrationCount(Population currentYearPopulation) {
        int year = currentYearPopulation.getYear();
        double[] currentMalePyramid = currentYearPopulation.getMalePyramid();
        double[] currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        double[] nextYearMalePyramid = new double[currentMalePyramid.length];
        double[] nextYearFemalePyramid = new double[currentFemalePyramid.length];

        //Compute male cohorts aged 2 through maxCohortAge - 1
        for (int age = 1; age < oldestPyramidCohortAge - 1; age ++) {
            nextYearMalePyramid[age + 1] = projectOtherCohortPopulationWithMigrationCount(age, year, currentMalePyramid[age], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        }
        //Add in remaining max age cohort that continues to survive
        nextYearMalePyramid[oldestPyramidCohortAge] = projectOldestCohortPopulationWithMigrationCount(oldestPyramidCohortAge, year, currentMalePyramid[oldestPyramidCohortAge - 1], currentMalePyramid[oldestPyramidCohortAge], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (int age = 1; age < oldestPyramidCohortAge - 1; age ++) {
                nextYearFemalePyramid[age + 1] = projectOtherCohortPopulationWithMigrationCount(age, year, currentFemalePyramid[age], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        }
        //Add in remaining max age cohort that continues to survive
        nextYearFemalePyramid[oldestPyramidCohortAge] = projectOldestCohortPopulationWithMigrationCount(oldestPyramidCohortAge, year, currentFemalePyramid[oldestPyramidCohortAge - 1], currentFemalePyramid[oldestPyramidCohortAge], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());

        //Project male and female births for remainder of current year and first half of next year
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirths = projectBirths(year + 1, nextYearFemalePyramid);

        //Project age 0 and 1 male cohort
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year) + 1);
        double nextYearMaleBirths = nextYearBirths * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        nextYearMalePyramid[0] = projectAgeZeroPopulationWithMigrationCount(year, currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramid[1] = ruralUrbanProjectAgeOnePopulationWithMigrationCount(year, currentMalePyramid[0], nextYearMalePyramid[0], projectionParameters.getMaleInfantSeparationFactor(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());

        //Project age 0 and 1 female cohort
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year) + 1);
        double nextYearFemaleBirths = nextYearBirths / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        nextYearFemalePyramid[0] = projectAgeZeroPopulationWithMigrationCount(year, currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramid[1] = ruralUrbanProjectAgeOnePopulationWithMigrationCount(year, currentFemalePyramid[0], nextYearFemalePyramid[0], projectionParameters.getFemaleInfantSeparationFactor(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //When previous year population is known
    public Population projectNextYearPopulationWithMigrationCount(Population lastYearPopulation, Population currentYearPopulation) {
        int year = currentYearPopulation.getYear();
        double[] currentMalePyramid = currentYearPopulation.getMalePyramid();
        double[] currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        double[] nextYearMalePyramid = new double[currentMalePyramid.length];
        double[] nextYearFemalePyramid = new double[currentFemalePyramid.length];

        //Compute male cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (int age = 1; age < oldestPyramidCohortAge - 1; age ++) {
            nextYearMalePyramid[age + 1] = projectOtherCohortPopulationWithMigrationCount(age, year, currentMalePyramid[age], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        }
        //Add in remaining max age cohort that continues to survive
        nextYearMalePyramid[oldestPyramidCohortAge] = projectOldestCohortPopulationWithMigrationCount(oldestPyramidCohortAge, year, currentMalePyramid[oldestPyramidCohortAge - 1], currentMalePyramid[oldestPyramidCohortAge], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (int age = 1; age < oldestPyramidCohortAge - 1; age ++) {
                nextYearFemalePyramid[age + 1] = projectOtherCohortPopulationWithMigrationCount(age, year, currentFemalePyramid[age], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        }
        //Add in remaining max age cohort that continues to survive
        nextYearFemalePyramid[oldestPyramidCohortAge] = projectOldestCohortPopulationWithMigrationCount(oldestPyramidCohortAge, year, currentFemalePyramid[oldestPyramidCohortAge - 1], currentFemalePyramid[oldestPyramidCohortAge], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());

        //Project male and female births for remainder of current year and first half of next year
        double lastYearBirths = projectBirths(year - 1, lastYearPopulation.getFemalePyramid());
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirths = projectBirths(year + 1, nextYearFemalePyramid);

        //Project age 0 and 1 male cohort
        double lastYearMaleBirths = lastYearBirths * projectionParameters.getHumanSexRatio(year - 1) / (projectionParameters.getHumanSexRatio(year - 1) + 1);
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearMaleBirths = nextYearBirths * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        nextYearMalePyramid[0] = projectAgeZeroPopulationWithMigrationCount(year, currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramid[1] = projectAgeOnePopulationWithMigrationCount(year, lastYearMaleBirths, currentYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());

        //Project age 0 and 1 female cohort
        double lastYearFemaleBirths = lastYearBirths / (projectionParameters.getHumanSexRatio(year - 1) + 1);
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year) + 1);
        double nextYearFemaleBirths = nextYearBirths / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        nextYearFemalePyramid[0] = projectAgeZeroPopulationWithMigrationCount(year, currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramid[1] = projectAgeOnePopulationWithMigrationCount(year, lastYearFemaleBirths, currentYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //When previous year population is not known
    public Population projectNextYearPopulationWithMigrationRates(Population currentYearPopulation) {
        int year = currentYearPopulation.getYear();
        double[] currentMalePyramid = currentYearPopulation.getMalePyramid();
        double[] currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        double[] variableAgeOnePopulation = {1, 0}; //next year age one population
        double[] nextYearMalePyramidVariable = new double[currentMalePyramid.length];
        double[] nextYearMalePyramidConstant = new double[currentMalePyramid.length];
        double[] nextYearFemalePyramidVariable = new double[currentFemalePyramid.length];
        double[] nextYearFemalePyramidConstant = new double[currentFemalePyramid.length];

        //Compute male cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        double[] variableLastAgePopulation = variableAgeOnePopulation.clone();
        for (int age = 1; age < oldestPyramidCohortAge - 1; age++) {
            variableLastAgePopulation = projectOtherCohortPopulationWithMigrationRates(age, year, variableLastAgePopulation, currentMalePyramid[age], currentMalePyramid[age + 1], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
            nextYearMalePyramidVariable[age + 1] = variableLastAgePopulation[0];
            nextYearMalePyramidConstant[age + 1] = variableLastAgePopulation[1];
        }
        //Add in remaining max age cohort that continues to survive
        variableLastAgePopulation = projectOldestCohortPopulationWithMigrationRates(oldestPyramidCohortAge, year, variableLastAgePopulation, currentMalePyramid[oldestPyramidCohortAge - 1], currentMalePyramid[oldestPyramidCohortAge], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable[oldestPyramidCohortAge] = variableLastAgePopulation[0];
        nextYearMalePyramidConstant[oldestPyramidCohortAge] = variableLastAgePopulation[1];

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        variableLastAgePopulation = variableAgeOnePopulation.clone();
        for (int age = 1; age < oldestPyramidCohortAge - 1; age++) {
            variableLastAgePopulation = projectOtherCohortPopulationWithMigrationRates(age, year, variableLastAgePopulation, currentFemalePyramid[age], currentFemalePyramid[age + 1], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
            nextYearFemalePyramidVariable[age + 1] = variableLastAgePopulation[0];
            nextYearFemalePyramidConstant[age + 1] = variableLastAgePopulation[1];
        }
        //Add in remaining max age cohort that continues to survive
        variableLastAgePopulation = projectOldestCohortPopulationWithMigrationRates(oldestPyramidCohortAge, year, variableLastAgePopulation, currentFemalePyramid[oldestPyramidCohortAge - 1], currentFemalePyramid[oldestPyramidCohortAge], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable[oldestPyramidCohortAge] = variableLastAgePopulation[0];
        nextYearFemalePyramidConstant[oldestPyramidCohortAge] = variableLastAgePopulation[1];

        //Project male and female births for remainder of current year and first half of next year
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirthsVariable = projectBirths(year + 1, nextYearFemalePyramidVariable);
        double nextYearBirthsConstant = projectBirths(year + 1, nextYearFemalePyramidConstant);

        //Project age 0 male cohort
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year) + 1);
        double nextYearMaleBirthsVariable = nextYearBirthsVariable * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        double nextYearMaleBirthsConstant = nextYearBirthsConstant * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        double[] nextYearMaleBirths = {nextYearMaleBirthsVariable, nextYearMaleBirthsConstant};
        variableLastAgePopulation = projectAgeZeroPopulationWithMigrationRates(year, currentMalePyramid[0], currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable[0] = variableLastAgePopulation[0];
        nextYearMalePyramidConstant[0] = variableLastAgePopulation[1];

        //Project age 1 male cohort using other cohort method for first cycle
        variableLastAgePopulation = ruralUrbanProjectAgeOnePopulationWithMigrationRates(year, currentMalePyramid[0], variableLastAgePopulation, projectionParameters.getMaleInfantSeparationFactor(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable[1] = variableLastAgePopulation[0];
        nextYearMalePyramidConstant[1] = variableLastAgePopulation[1];

        //Project age 0 female cohort
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year) + 1);
        double nextYearFemaleBirthsVariable = nextYearBirthsVariable / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        double nextYearFemaleBirthsConstant = nextYearBirthsConstant / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        double[] nextYearFemaleBirths = {nextYearFemaleBirthsVariable, nextYearFemaleBirthsConstant};
        variableLastAgePopulation = projectAgeZeroPopulationWithMigrationRates(year, currentFemalePyramid[0], currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable[0] = variableLastAgePopulation[0];
        nextYearFemalePyramidConstant[0] = variableLastAgePopulation[1];

        //Project age 1 female cohort using other cohort method for first cycle
        variableLastAgePopulation = ruralUrbanProjectAgeOnePopulationWithMigrationRates(year, currentFemalePyramid[0], variableLastAgePopulation, projectionParameters.getFemaleInfantSeparationFactor(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable[1] = variableLastAgePopulation[0];
        nextYearFemalePyramidConstant[1] = variableLastAgePopulation[1];

        double[] nextYearMalePyramid = solveVariablePyramid(nextYearMalePyramidVariable, nextYearMalePyramidConstant, variableAgeOnePopulation);
        double[] nextYearFemalePyramid = solveVariablePyramid(nextYearFemalePyramidVariable, nextYearFemalePyramidConstant, variableAgeOnePopulation);

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //When previous year population is known
    public Population projectNextYearPopulationWithMigrationRates(Population lastYearPopulation, Population currentYearPopulation) {
        int year = currentYearPopulation.getYear();
        double[] currentMalePyramid = currentYearPopulation.getMalePyramid();
        double[] currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        double[] variableAgeOnePopulation = {1, 0}; //next year age one population
        double[] nextYearMalePyramidVariable = new double[currentMalePyramid.length];
        double[] nextYearMalePyramidConstant = new double[currentMalePyramid.length];
        double[] nextYearFemalePyramidVariable = new double[currentFemalePyramid.length];
        double[] nextYearFemalePyramidConstant = new double[currentFemalePyramid.length];

        //Compute male cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        double[] variableLastAgePopulation = variableAgeOnePopulation.clone();
        for (int age = 1; age < oldestPyramidCohortAge - 1; age++) {
            variableLastAgePopulation = projectOtherCohortPopulationWithMigrationRates(age, year, variableLastAgePopulation, currentMalePyramid[age], currentMalePyramid[age + 1], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
            nextYearMalePyramidVariable[age + 1] = variableLastAgePopulation[0];
            nextYearMalePyramidConstant[age + 1] = variableLastAgePopulation[1];
        }
        //Add in remaining max age cohort that continues to survive
        variableLastAgePopulation = projectOldestCohortPopulationWithMigrationRates(oldestPyramidCohortAge, year, variableLastAgePopulation, currentMalePyramid[oldestPyramidCohortAge - 1], currentMalePyramid[oldestPyramidCohortAge], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable[oldestPyramidCohortAge] = variableLastAgePopulation[0];
        nextYearMalePyramidConstant[oldestPyramidCohortAge] = variableLastAgePopulation[1];

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        variableLastAgePopulation = variableAgeOnePopulation.clone();
        for (int age = 1; age < oldestPyramidCohortAge - 1; age++) {
            variableLastAgePopulation = projectOtherCohortPopulationWithMigrationRates(age, year, variableLastAgePopulation, currentFemalePyramid[age], currentFemalePyramid[age + 1], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
            nextYearFemalePyramidVariable[age + 1] = variableLastAgePopulation[0];
            nextYearFemalePyramidConstant[age + 1] = variableLastAgePopulation[1];
        }
        //Add in remaining max age cohort that continues to survive
        variableLastAgePopulation = projectOldestCohortPopulationWithMigrationRates(oldestPyramidCohortAge, year, variableLastAgePopulation, currentFemalePyramid[oldestPyramidCohortAge - 1], currentFemalePyramid[oldestPyramidCohortAge], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable[oldestPyramidCohortAge] = variableLastAgePopulation[0];
        nextYearFemalePyramidConstant[oldestPyramidCohortAge] = variableLastAgePopulation[1];

        //Project male and female births for remainder of current year and first half of next year
        double lastYearBirths = projectBirths(year - 1, lastYearPopulation.getFemalePyramid());
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirthsVariable = projectBirths(year + 1, nextYearFemalePyramidVariable);
        double nextYearBirthsConstant = projectBirths(year + 1, nextYearFemalePyramidConstant);

        //Project age 0 male cohort
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearMaleBirthsVariable = nextYearBirthsVariable * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        double nextYearMaleBirthsConstant = nextYearBirthsConstant * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        double[] nextYearMaleBirths = {nextYearMaleBirthsVariable, nextYearMaleBirthsConstant};
        variableLastAgePopulation = projectAgeZeroPopulationWithMigrationRates(year, currentMalePyramid[0], currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable[0] = variableLastAgePopulation[0];
        nextYearMalePyramidConstant[0] = variableLastAgePopulation[1];

        //Project age 1 males using method specifically for age 1
        double lastYearMaleBirths = lastYearBirths * projectionParameters.getHumanSexRatio(year - 1) / (projectionParameters.getHumanSexRatio(year - 1) + 1);
        variableLastAgePopulation = projectAgeOnePopulationWithMigrationRates(year, variableLastAgePopulation, lastYearPopulation.getMalePyramid()[0], currentMalePyramid[0], currentMalePyramid[1], lastYearMaleBirths, currentYearMaleBirths, projectionParameters.getMaleInfantCumulativeMortality(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramidVariable[1] = variableLastAgePopulation[0];
        nextYearMalePyramidConstant[1] = variableLastAgePopulation[1];

        //Project age 0 female cohort
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year) + 1);
        double nextYearFemaleBirthsVariable = nextYearBirthsVariable / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        double nextYearFemaleBirthsConstant = nextYearBirthsConstant / (projectionParameters.getHumanSexRatio(year + 1) + 1);
        double[] nextYearFemaleBirths = {nextYearFemaleBirthsVariable, nextYearFemaleBirthsConstant};
        variableLastAgePopulation = projectAgeZeroPopulationWithMigrationRates(year, currentFemalePyramid[0], currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable[0] = variableLastAgePopulation[0];
        nextYearFemalePyramidConstant[0] = variableLastAgePopulation[1];

        //Project age 1 female using method specifically for age 1
        double lastYearFemaleBirths = lastYearBirths / (projectionParameters.getHumanSexRatio(year - 1) + 1);
        variableLastAgePopulation = projectAgeOnePopulationWithMigrationRates(year, variableLastAgePopulation, lastYearPopulation.getFemalePyramid()[0], currentFemalePyramid[0], currentFemalePyramid[1], lastYearFemaleBirths, currentYearFemaleBirths, projectionParameters.getFemaleInfantCumulativeMortality(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramidVariable[1] = variableLastAgePopulation[0];
        nextYearFemalePyramidConstant[1] = variableLastAgePopulation[1];

        double[] nextYearMalePyramid = solveVariablePyramid(nextYearMalePyramidVariable, nextYearMalePyramidConstant, variableAgeOnePopulation);
        double[] nextYearFemalePyramid = solveVariablePyramid(nextYearFemalePyramidVariable, nextYearFemalePyramidConstant, variableAgeOnePopulation);

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //Project next year age + 1 population using migration counts for cohorts excluding ages 0, 1, and maximal age
    public double projectOtherCohortPopulationWithMigrationCount(int age, int year, double currentYearAgePopulation, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        //Get projection parameters
        double[] sexSpecificCurrentYearMortality = sexSpecificMortality.get(year);
        double[] sexSpecificNextYearMortality = sexSpecificMortality.get(year + 1);
        double[] sexSpecificCurrentYearMigration = sexSpecificMigration.get(year);
        double[] sexSpecificNextYearMigration = sexSpecificMigration.get(year + 1);

        //Surviving age + 0 to 0.5
        double lowerBound = 0;
        double upperBound = 0.5;
        double survivingPopulation = populationCalculator.simpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.estimatePopulationAgeWeights(age, sexSpecificCurrentYearMortality, 0, 0.5, singleXCount),
                        populationCalculator.createConstantArray(Math.pow(1 - sexSpecificCurrentYearMortality[age], 0.5), singleXCount), //can be taken out of integral for performance
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age], "0.5 - x", 0, 0.5, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "x", 0, 0.5, singleXCount)
                ),
                0, 0.5, singleXCount);
        //Surviving age + 0.5 to 1
        lowerBound = 0.5;
        upperBound = 1;
        survivingPopulation += populationCalculator.simpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.estimatePopulationAgeWeights(age, sexSpecificCurrentYearMortality, 0.5, 1, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age], "1 - x", 0.5, 1, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age + 1], "x - 0.5", 0.5, 1, singleXCount),
                        populationCalculator.createConstantArray(Math.pow(1 - sexSpecificNextYearMortality[age + 1], 0.5), singleXCount)
                ), //can be taken out of integral for performance
                0.5, 1, singleXCount);
        //Adjust for weight
        survivingPopulation *= currentYearAgePopulation;
        survivingPopulation /= populationCalculator.simpsonIntegral(populationCalculator.estimatePopulationAgeWeights(age, sexSpecificCurrentYearMortality, 0, 1, singleXCount), 0, 1, singleXCount);

        //Age migrants who arrive between current year months 6 and 12 and will be age + 1 by next census
        //Those who will be age + 1 next year
        String lowerBoundX = "y - 0.5";
        String upperBoundX = "y";
        double lowerBoundY = 0.5;
        double upperBoundY = 1;
        double[][] migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgeMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age], "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age], "-x + y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "x + 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //dev
        if (age == 1 && year == 2000) {
            System.out.println("Average averageCurrentSecondHalfYearAgeMigrantSurvival " + averageCurrentSecondHalfYearAgeMigrantSurvival);
        }
        //end dev
        //Those who will be age + 1 this year
        lowerBoundX = "y";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentSecondHalfYearAgeMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age], "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age + 1], "x - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgeMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age migrants who arrive next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "y + 0.5";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgeMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age], "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "x - 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgeMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the current year between months 6 and 12 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "y - 0.5";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age + 1, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgePlusOneMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age + 1], "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age + 1, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgePlusOneMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "y + 0.5";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age + 1, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgePlusOneMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age + 1, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgePlusOneMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        double currentYearAgeMigrants = sexSpecificCurrentYearMigration[age];
        double nextYearAgeMigrants = sexSpecificNextYearMigration[age];
        double currentYearAgePlusOneMigrants = sexSpecificCurrentYearMigration[age + 1];
        double nextYearAgePlusOneMigrants = sexSpecificNextYearMigration[age + 1];
        double nextYearAgePlusOnePopulation = survivingPopulation +
                    currentYearAgeMigrants * averageCurrentSecondHalfYearAgeMigrantSurvival +
                    nextYearAgeMigrants * averageNextFirstHalfYearAgeMigrantSurvival +
                    currentYearAgePlusOneMigrants * averageCurrentSecondHalfYearAgePlusOneMigrantSurvival +
                    nextYearAgePlusOneMigrants * averageNextFirstHalfYearAgePlusOneMigrantSurvival;
        return nextYearAgePlusOnePopulation;
    }

    //Project next year age + 1 population using migration rates for cohorts excluding 0, 1, and maximal age.
    public double[] projectOtherCohortPopulationWithMigrationRates(int age, int year, double[] nextYearAgePopulation, double currentYearAgePopulation, double currentYearAgePlusOnePopulation, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        //Get projection parameters
        double[] sexSpecificCurrentYearMortality = sexSpecificMortality.get(year);
        double[] sexSpecificNextYearMortality = sexSpecificMortality.get(year + 1);
        double[] sexSpecificCurrentYearMigration = sexSpecificMigration.get(year);
        double[] sexSpecificNextYearMigration = sexSpecificMigration.get(year + 1);

        //Surviving age + 0 to 0.5
        double lowerBound = 0;
        double upperBound = 0.5;
        double survivingPopulation = populationCalculator.simpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.estimatePopulationAgeWeights(age, sexSpecificCurrentYearMortality, lowerBound, upperBound, singleXCount),
                        populationCalculator.createConstantArray(Math.pow(1 - sexSpecificCurrentYearMortality[age], 0.5), singleXCount), //can be taken out of integral for performance
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age], "0.5 - x", lowerBound, upperBound, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "x", lowerBound, upperBound, singleXCount)
                ),
                lowerBound, upperBound, singleXCount);
        //Surviving age + 0.5 to 1
        lowerBound = 0.5;
        upperBound = 1;
        survivingPopulation += populationCalculator.simpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.estimatePopulationAgeWeights(age, sexSpecificCurrentYearMortality, lowerBound, upperBound, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age], "1 - x", lowerBound, upperBound, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age + 1], "x - 0.5", lowerBound, upperBound, singleXCount),
                        populationCalculator.createConstantArray(Math.pow(1 - sexSpecificNextYearMortality[age + 1], 0.5), singleXCount)
                ), //can be taken out of integral for performance
                lowerBound, upperBound, singleXCount);
        //Adjust for weight
        survivingPopulation *= currentYearAgePopulation;
        survivingPopulation /= populationCalculator.simpsonIntegral(populationCalculator.estimatePopulationAgeWeights(age, sexSpecificCurrentYearMortality, 0, 1, singleXCount), 0, 1, singleXCount);

        //Age migrants who arrive between current year months 6 and 12 and will be age + 1 by next census
        //Those who will be age + 1 next year
        String lowerBoundX = "y - 0.5";
        String upperBoundX = "y";
        double lowerBoundY = 0.5;
        double upperBoundY = 1;
        double[][] migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgeMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age], "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age], "-x + y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "x + 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age + 1 this year
        lowerBoundX = "y";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentSecondHalfYearAgeMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age], "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age + 1], "x - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgeMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age migrants who arrive next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "y + 0.5";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgeMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age], "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "x - 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgeMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the current year between months 6 and 12 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "y - 0.5";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights( age + 1, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgePlusOneMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[age + 1], "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age + 1, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgePlusOneMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "y + 0.5";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age + 1, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgePlusOneMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[age + 1], "0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(age + 1, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgePlusOneMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        //Compute total migrant population by age and year
        double currentYearAgeMigrants = projectMigration(age, currentYearAgePopulation, sexSpecificCurrentYearMigration);
        double currentYearAgePlusOneMigrants = projectMigration(age + 1, currentYearAgePlusOnePopulation, sexSpecificCurrentYearMigration);
        double nextYearAgeMigrantsVariable = projectMigration(age,  nextYearAgePopulation[0], sexSpecificNextYearMigration); //variable portion
        double nextYearAgeMigrantsConstant = projectMigration(age, nextYearAgePopulation[1], sexSpecificNextYearMigration); //constant portion
        //Compute final population, variable and constant portions
        double nextYearAgePlusOnePopulationVariable = nextYearAgeMigrantsVariable * averageNextFirstHalfYearAgeMigrantSurvival /
                (1 - sexSpecificNextYearMigration[age + 1] * averageNextFirstHalfYearAgePlusOneMigrantSurvival);
        double nextYearAgePlusOnePopulationConstant = (survivingPopulation +
                currentYearAgeMigrants * averageCurrentSecondHalfYearAgeMigrantSurvival +
                currentYearAgePlusOneMigrants * averageCurrentSecondHalfYearAgePlusOneMigrantSurvival +
                nextYearAgeMigrantsConstant * averageNextFirstHalfYearAgeMigrantSurvival) /
                (1 - sexSpecificNextYearMigration[age + 1] * averageNextFirstHalfYearAgePlusOneMigrantSurvival);
        //Create next year age + 1 population
        double[] nextYearAgePlusOnePopulation = new double[2];
        nextYearAgePlusOnePopulation[0] = nextYearAgePlusOnePopulationVariable;
        nextYearAgePlusOnePopulation[1] = nextYearAgePlusOnePopulationConstant;
        return nextYearAgePlusOnePopulation;
    }

    //Project next year's oldest cohort population using migration counts
    public double projectOldestCohortPopulationWithMigrationCount(int oldestCohortAge, int year, double currentYearSecondOldestCohortPopulation, double currentYearOldestCohortPopulation, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        //Get projection parameters
        double[] sexSpecificCurrentYearMortality = sexSpecificMortality.get(year);
        double[] sexSpecificNextYearMortality = sexSpecificMortality.get(year + 1);
        double[] sexSpecificCurrentYearMigration = sexSpecificMigration.get(year);
        double[] sexSpecificNextYearMigration = sexSpecificMigration.get(year + 1);

        //Surviving (oldestCohortAge - 1) + 0 to 0.5
        double lowerBound = 0;
        double upperBound = 0.5;
        double survivingPopulation = populationCalculator.simpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.estimatePopulationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, lowerBound, upperBound, singleXCount),
                        populationCalculator.createConstantArray(Math.pow(1 - sexSpecificCurrentYearMortality[oldestCohortAge - 1], 0.5), singleXCount), //can be taken out of integral for performance
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge - 1], "0.5 - x", lowerBound, upperBound, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "x", lowerBound, upperBound, singleXCount)
                ),
                lowerBound, upperBound, singleXCount);
        //Surviving (oldestCohortAge - 1) + 0.5 to 1
        lowerBound = 0.5;
        upperBound = 1;
        survivingPopulation += populationCalculator.simpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.estimatePopulationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, lowerBound, upperBound, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge - 1], "1 - x", lowerBound, upperBound, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge], "x - 0.5", lowerBound, upperBound, singleXCount),
                        populationCalculator.createConstantArray(Math.pow(1 - sexSpecificNextYearMortality[oldestCohortAge], 0.5), singleXCount)
                ), //can be taken out of integral for performance
                lowerBound, upperBound, singleXCount);
        //Adjust for weight
        survivingPopulation *= currentYearSecondOldestCohortPopulation;
        survivingPopulation /= populationCalculator.simpsonIntegral(populationCalculator.estimatePopulationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, 0, 1, singleXCount), 0, 1, singleXCount);

        //Surviving oldestCohortAge population
        survivingPopulation += currentYearOldestCohortPopulation * Math.pow(1 - sexSpecificCurrentYearMortality[oldestCohortAge], 0.5) * Math.pow(1 - sexSpecificNextYearMortality[oldestCohortAge], 0.5);

        //Age migrants who arrive between current year months 6 and 12 and will be age + 1 by next census
        //Those who will be age + 1 next year
        String lowerBoundX = "y - 0.5";
        String upperBoundX = "y";
        double lowerBoundY = 0.5;
        double upperBoundY = 1;
        double[][] migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearSecondOldestMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge - 1], "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge - 1], "-x + y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "x + 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age + 1 this year
        lowerBoundX = "y";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentSecondHalfYearSecondOldestMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge - 1], "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge], "x - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearSecondOldestMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age migrants who arrive next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "y + 0.5";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearSecondOldestMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge - 1], "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "x - 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearSecondOldestMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the current year between months 6 and 12 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearOldestMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge], "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearOldestMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearOldestMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearOldestMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        double currentYearSecondOldestMigrants = sexSpecificCurrentYearMigration[oldestCohortAge - 1];
        double nextYearSecondOldestMigrants = sexSpecificNextYearMigration[oldestCohortAge - 1];
        double currentYearOldestMigrants = sexSpecificCurrentYearMigration[oldestCohortAge];
        double nextYearOldestMigrants = sexSpecificNextYearMigration[oldestCohortAge];
        double nextYearOldestCohortPopulation = survivingPopulation +
                    currentYearSecondOldestMigrants * averageCurrentSecondHalfYearSecondOldestMigrantSurvival +
                    nextYearSecondOldestMigrants * averageNextFirstHalfYearSecondOldestMigrantSurvival +
                    currentYearOldestMigrants * averageCurrentSecondHalfYearOldestMigrantSurvival +
                    nextYearOldestMigrants * averageNextFirstHalfYearOldestMigrantSurvival;
        return nextYearOldestCohortPopulation;
    }

    //Project next year's oldest cohort population using migration rates
    public double[] projectOldestCohortPopulationWithMigrationRates(int oldestCohortAge, int year, double[] nextYearSecondOldestCohortPopulation, double currentYearSecondOldestCohortPopulation, double currentYearOldestCohortPopulation, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        //Get projection parameters
        double[] sexSpecificCurrentYearMortality = sexSpecificMortality.get(year);
        double[] sexSpecificNextYearMortality = sexSpecificMortality.get(year + 1);
        double[] sexSpecificCurrentYearMigration = sexSpecificMigration.get(year);
        double[] sexSpecificNextYearMigration = sexSpecificMigration.get(year + 1);

        //Surviving (oldestCohortAge - 1) + 0 to 0.5
        double lowerBound = 0;
        double upperBound = 0.5;
        double survivingPopulation = populationCalculator.simpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.estimatePopulationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, lowerBound, upperBound, singleXCount),
                        populationCalculator.createConstantArray(Math.pow(1 - sexSpecificCurrentYearMortality[oldestCohortAge - 1], 0.5), singleXCount), //can be taken out of integral for performance
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge - 1], "0.5 - x", lowerBound, upperBound, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "x", lowerBound, upperBound, singleXCount)
                ),
                lowerBound, upperBound, singleXCount);
        //Surviving (oldestCohortAge - 1) + 0.5 to 1
        lowerBound = 0.5;
        upperBound = 1;
        survivingPopulation += populationCalculator.simpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.estimatePopulationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, lowerBound, upperBound, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge - 1], "1 - x", lowerBound, upperBound, singleXCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge], "x - 0.5", lowerBound, upperBound, singleXCount),
                        populationCalculator.createConstantArray(Math.pow(1 - sexSpecificNextYearMortality[oldestCohortAge], 0.5), singleXCount)
                ), //can be taken out of integral for performance
                lowerBound, upperBound, singleXCount);
        //Adjust for weight
        survivingPopulation *= currentYearSecondOldestCohortPopulation;
        survivingPopulation /= populationCalculator.simpsonIntegral(populationCalculator.estimatePopulationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, 0, 1, singleXCount), 0, 1, singleXCount);

        //Surviving oldestCohortAge population
        survivingPopulation += currentYearOldestCohortPopulation * Math.pow(1 - sexSpecificCurrentYearMortality[oldestCohortAge], 0.5) * Math.pow(1 - sexSpecificNextYearMortality[oldestCohortAge], 0.5);

        //Age migrants who arrive between current year months 6 and 12 and will be age + 1 by next census
        //Those who will be age + 1 next year
        String lowerBoundX = "y - 0.5";
        String upperBoundX = "y";
        double lowerBoundY = 0.5;
        double upperBoundY = 1;
        double[][] migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearSecondOldestMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge - 1], "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge - 1], "-x + y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "x + 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age + 1 this year
        lowerBoundX = "y";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentSecondHalfYearSecondOldestMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge - 1], "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge], "x - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearSecondOldestMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age migrants who arrive next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "y + 0.5";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearSecondOldestMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge - 1], "1 - x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "x - 0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge - 1, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearSecondOldestMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the current year between months 6 and 12 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "1";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearOldestMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[oldestCohortAge], "1 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearOldestMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age + 1 migrants who arrive in the next year between months 0 and 6 and will be age + 1 by census
        lowerBoundX = "0";
        upperBoundX = "1";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearOldestMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        migrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[oldestCohortAge], "0.5 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        migrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(oldestCohortAge, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearOldestMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(migrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        //Compute total migrant population by age and year
        double currentYearSecondOldestMigrants = projectMigration(oldestCohortAge - 1, currentYearSecondOldestCohortPopulation, sexSpecificCurrentYearMigration);
        double currentYearOldestMigrants = projectMigration(oldestCohortAge, currentYearOldestCohortPopulation, sexSpecificCurrentYearMigration);
        double nextYearSecondOldestMigrantsVariable = projectMigration(oldestCohortAge - 1, nextYearSecondOldestCohortPopulation[0], sexSpecificNextYearMigration);
        double nextYearSecondOldestMigrantsConstant = projectMigration(oldestCohortAge - 1, nextYearSecondOldestCohortPopulation[1], sexSpecificNextYearMigration);
        //Compute final population, variable and constant portions
        double nextYearOldestCohortPopulationVariable = nextYearSecondOldestMigrantsVariable * averageNextFirstHalfYearSecondOldestMigrantSurvival /
                (1 - sexSpecificNextYearMigration[oldestCohortAge] * averageNextFirstHalfYearOldestMigrantSurvival);
        double nextYearOldestCohortPopulationConstant = (survivingPopulation +
                currentYearSecondOldestMigrants * averageCurrentSecondHalfYearSecondOldestMigrantSurvival +
                currentYearOldestMigrants * averageCurrentSecondHalfYearOldestMigrantSurvival +
                nextYearSecondOldestMigrantsConstant * averageNextFirstHalfYearSecondOldestMigrantSurvival) /
                (1 - sexSpecificNextYearMigration[oldestCohortAge] * averageNextFirstHalfYearOldestMigrantSurvival);
        //Combine into double[] population
        double[] nextYearOldestCohortPopulation = new double[2];
        nextYearOldestCohortPopulation[0] = nextYearOldestCohortPopulationVariable;
        nextYearOldestCohortPopulation[1] = nextYearOldestCohortPopulationConstant;
        return nextYearOldestCohortPopulation;
    }

    //Project age zero population using migration counts, infant CM curve.
    public double projectAgeZeroPopulationWithMigrationCount(int year, double currentYearBirths, double nextYearBirths, Map<Integer, double[][]> sexSpecificInfantCumulativeMortality, Map<Integer, double[]> sexSpecificMigration) {
        //Get projection parameters
        double[][] sexSpecificCurrentYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year);
        double[][] sexSpecificNextYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year + 1);
        double[] sexSpecificCurrentYearMigration = sexSpecificMigration.get(year);
        double[] sexSpecificNextYearMigration = sexSpecificMigration.get(year + 1);

        //Project surviving births
        //Surviving current year births
        double lowerBound = 0;
        double upperBound = 6;
        double survivingCurrentYearBirths = 0.5 * currentYearBirths
                * populationCalculator.simpsonIntegral(
                        populationCalculator.multiplyArrays(
                            populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount)),
                            populationCalculator.divideArrays(
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificNextYearInfantCumulativeMortality, "x + 6", lowerBound, upperBound, singleXCount)),
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificNextYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount))
                            )
                        ),
                lowerBound, upperBound, singleXCount) / 6;
        //Surviving next year births
        double survivingNextYearBirths = 0.5 * nextYearBirths
                * (1 - populationCalculator.integrateCubicSpline(sexSpecificNextYearInfantCumulativeMortality, 0,6) / 6);

        //Project migrant death rates
        //Current year
        String lowerBoundX = "1";
        String upperBoundX = "y - 6";
        double lowerBoundY = 7;
        double upperBoundY = 12;
        double areaOfDomain = 12.5;
        double averageCurrentYearMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 18 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        )
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentYearMigrantSurvival /= 132;
        //Next year
        lowerBoundX = "1";
        upperBoundX = "y + 6";
        lowerBoundY = 0;
        upperBoundY = 6;
        areaOfDomain = 48;
        double averageNextYearMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.divideArrays(
                        populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 6 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                        populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageNextYearMigrantSurvival /= 132;

        //Compute next year population
        double currentYearMigrants = sexSpecificCurrentYearMigration[0]; //proportion of current year age 0 migrants that are age 0 on the next census
        double nextYearMigrants = sexSpecificNextYearMigration[0];
        double nextYearAgeZeroPopulation = survivingCurrentYearBirths + survivingNextYearBirths +
                    currentYearMigrants * averageCurrentYearMigrantSurvival +
                    nextYearMigrants * averageNextYearMigrantSurvival;
        return nextYearAgeZeroPopulation;
    }

    //Project age zero population using migration rates. Requires infant CM curve.
    public double[] projectAgeZeroPopulationWithMigrationRates(int year, double currentYearAgeZeroPopulation, double currentYearBirths, double[] nextYearBirths, Map<Integer, double[][]> sexSpecificInfantCumulativeMortality, Map<Integer, double[]> sexSpecificMigration) {
        //Get projection parameters
        double[][] sexSpecificCurrentYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year);
        double[][] sexSpecificNextYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year + 1);
        double[] sexSpecificCurrentYearMigration = sexSpecificMigration.get(year);
        double[] sexSpecificNextYearMigration = sexSpecificMigration.get(year + 1);

        //Project surviving births
        //Surviving current year births
        double lowerBound = 0;
        double upperBound = 6;
        double survivingCurrentYearBirths = 0.5 * currentYearBirths
                * populationCalculator.simpsonIntegral(
                        populationCalculator.multiplyArrays(
                            populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount)),
                            populationCalculator.divideArrays(
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificNextYearInfantCumulativeMortality, "x + 6", lowerBound, upperBound, singleXCount)),
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificNextYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount))
                            )
                        ),
                lowerBound, upperBound, singleXCount) / 6;
        //Surviving next year births
        double averageNextYearBirthsSurvival = (1 - populationCalculator.integrateCubicSpline(sexSpecificNextYearInfantCumulativeMortality, 0,6) / 6);
        double survivingNextYearBirthsVariable = 0.5 * nextYearBirths[0] * averageNextYearBirthsSurvival;
        double survivingNextYearBirthsConstant = 0.5 * nextYearBirths[1] * averageNextYearBirthsSurvival;

        //Project next year migrant death rates
        //Current year
        String lowerBoundX = "1";
        String upperBoundX = "y - 6";
        double lowerBoundY = 7;
        double upperBoundY = 12;
        double areaOfDomain = 12.5;
        double averageCurrentYearMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 18 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        )
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageCurrentYearMigrantSurvival /= 132;
        //Next year
        lowerBoundX = "1";
        upperBoundX = "y + 6";
        lowerBoundY = 0;
        upperBoundY = 6;
        areaOfDomain = 48;
        double averageNextYearMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.divideArrays(
                        populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 6 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                        populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        averageNextYearMigrantSurvival /= 132;

        //Compute next year population
        //Compute migrants for this year
        double currentYearMigrants = projectMigration(0, currentYearAgeZeroPopulation, sexSpecificCurrentYearMigration);
        //Compute variable and constant portions of next year population
        double nextYearAgeZeroPopulationVariable = survivingNextYearBirthsVariable / (1 - sexSpecificNextYearMigration[0] * averageNextYearMigrantSurvival);
        double nextYearAgeZeroPopulationConstant = (survivingCurrentYearBirths + survivingNextYearBirthsConstant + currentYearMigrants * averageCurrentYearMigrantSurvival) / (1 - sexSpecificNextYearMigration[0] * averageNextYearMigrantSurvival);
        //Combine variable and constant portions of next year population
        double[] nextYearAgeZeroPopulation = new double[2];
        nextYearAgeZeroPopulation[0] = nextYearAgeZeroPopulationVariable;
        nextYearAgeZeroPopulation[1] = nextYearAgeZeroPopulationConstant;
        return nextYearAgeZeroPopulation;
    }

    //Project age one population using migration counts, infant CM.
    public double projectAgeOnePopulationWithMigrationCount(int year, double lastYearBirths, double currentYearBirths,
                                                            Map<Integer, double[][]> sexSpecificInfantCumulativeMortality, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        //Get projection parameters
        double[][] sexSpecificLastYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year - 1);
        double[][] sexSpecificCurrentYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year);
        double[][] sexSpecificNextYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year + 1);
        double[] sexSpecificCurrentYearMortality = sexSpecificMortality.get(year);
        double[] sexSpecificNextYearMortality = sexSpecificMortality.get(year + 1);
        double[] sexSpecificLastYearMigration = sexSpecificMigration.get(year - 1);
        double[] sexSpecificCurrentYearMigration = sexSpecificMigration.get(year);
        double[] sexSpecificNextYearMigration = sexSpecificMigration.get(year + 1);

        //Surviving last year births until age 1 next year
        double lowerBound = 0;
        double upperBound = 6;
        double survivingLastYearBirths = 0.5 * lastYearBirths
                * populationCalculator.simpsonIntegral(
                    populationCalculator.multiplyArrays(
                            populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificLastYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount)),
                            populationCalculator.divideArrays(
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBound, upperBound, singleXCount)),
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount))
                            ),
                            populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x", lowerBound, upperBound, singleXCount),
                            populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBound, upperBound, singleXCount)
                    ),
                    lowerBound, upperBound, singleXCount) / 6;

        //Surviving current year births until age 1 next year
        lowerBound = 6;
        upperBound = 12;
        double survivingCurrentYearBirths = 0.5 * currentYearBirths
                * populationCalculator.simpsonIntegral(
                    populationCalculator.multiplyArrays(
                            populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount)),
                            populationCalculator.divideArrays(
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBound, upperBound, singleXCount)),
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificNextYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount))
                            ),
                            populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.5", lowerBound, upperBound, singleXCount)
                    ),
                    lowerBound, upperBound, singleXCount) / 6;

        //Project next year migrant death rates
        //Previous year age 0 migrants
        String lowerBoundX = "1";
        String upperBoundX = "y - 6";
        double lowerBoundY = 7;
        double upperBoundY = 12;
        double areaOfDomain = 12.5;
        double averageLastYearAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificLastYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificLastYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y + 1", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
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
        double averageCurrentFirstHalfYearAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Next 5 months, reach age 1 this year
        lowerBoundX = "y";
        upperBoundX = "y + 6";
        lowerBoundY = 1;
        upperBoundY = 6;
        areaOfDomain = 48;
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Next 5 months, reach age 1 next year
        lowerBoundX = "1";
        upperBoundX = "y";
        lowerBoundY = 1;
        upperBoundY = 6;
        areaOfDomain = 48;
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
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
        double averageCurrentMonthSixSevenAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age 1 this year
        lowerBoundX = "y";
        upperBoundX = "12";
        lowerBoundY = 6;
        upperBoundY = 7;
        areaOfDomain = 11;
        averageCurrentMonthSixSevenAgeZeroMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
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
        double averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age 1 this year
        lowerBoundX = "y";
        upperBoundX = "12";
        lowerBoundY = 7;
        upperBoundY = 12;
        areaOfDomain = 42.5;
        averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival /= 132;

        //Migrants who arrive next year at age 0
        lowerBoundX = "y + 6";
        upperBoundX = "12";
        lowerBoundY = 0;
        upperBoundY = 6;
        areaOfDomain = 18;
        double averageNextYearAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.833333333333y - 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageNextYearAgeZeroMigrantSurvival /= 132;

        //Age 1 migrants who arrive between current year months 6 and 12
        lowerBoundX = "0";
        upperBoundX = "y - 0.5";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        double[][] sexSpecificMigrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(1, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgeOneMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        sexSpecificMigrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "-y + 1", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        sexSpecificMigrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(1, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgeOneMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(sexSpecificMigrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age 1 migrants who arrive between next year months 0 and 6
        lowerBoundX = "0";
        upperBoundX = "y + 0.5";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        sexSpecificMigrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(1, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgeOneMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        sexSpecificMigrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "-y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        sexSpecificMigrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(1, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgeOneMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(sexSpecificMigrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        double lastYearAgeZeroMigrants = sexSpecificLastYearMigration[0]; //proportion of current year age 0 migrants that are age 0 on the next census
        double currentYearAgeZeroMigrants = sexSpecificCurrentYearMigration[0];
        double nextYearAgeZeroMigrants = sexSpecificNextYearMigration[0];
        double currentYearAgeOneMigrants = sexSpecificCurrentYearMigration[1];
        double nextYearAgeOneMigrants = sexSpecificNextYearMigration[1];
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
    public double[] projectAgeOnePopulationWithMigrationRates(int year, double[] nextYearAgeZeroPopulation, double lastYearAgeZeroPopulation, double currentYearAgeZeroPopulation, double currentYearAgeOnePopulation, double lastYearBirths, double currentYearBirths,
                                                              Map<Integer, double[][]> sexSpecificInfantCumulativeMortality, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        //Get projection parameters
        double[][] sexSpecificLastYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year - 1);
        double[][] sexSpecificCurrentYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year);
        double[][] sexSpecificNextYearInfantCumulativeMortality = sexSpecificInfantCumulativeMortality.get(year + 1);
        double[] sexSpecificCurrentYearMortality = sexSpecificMortality.get(year);
        double[] sexSpecificNextYearMortality = sexSpecificMortality.get(year + 1);
        double[] sexSpecificLastYearMigration = sexSpecificMigration.get(year - 1);
        double[] sexSpecificCurrentYearMigration = sexSpecificMigration.get(year);
        double[] sexSpecificNextYearMigration = sexSpecificMigration.get(year + 1);

        //Surviving last year births until age 1 next year
        double lowerBound = 0;
        double upperBound = 6;
        double survivingLastYearBirths = 0.5 * lastYearBirths
                * populationCalculator.simpsonIntegral(
                    populationCalculator.multiplyArrays(
                            populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificLastYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount)),
                            populationCalculator.divideArrays(
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBound, upperBound, singleXCount)),
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount))
                            ),
                            populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x", lowerBound, upperBound, singleXCount),
                            populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBound, upperBound, singleXCount)
                    ),
                    lowerBound, upperBound, singleXCount) / 6;

        //Surviving current year births until age 1 next year
        lowerBound = 6;
        upperBound = 12;
        double survivingCurrentYearBirths = 0.5 * currentYearBirths
                * populationCalculator.simpsonIntegral(
                    populationCalculator.multiplyArrays(
                            populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount)),
                            populationCalculator.divideArrays(
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBound, upperBound, singleXCount)),
                                    populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, singleXCount), populationCalculator.mapToArray(sexSpecificNextYearInfantCumulativeMortality, "x", lowerBound, upperBound, singleXCount))
                            ),
                            populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.5", lowerBound, upperBound, singleXCount)
                    ),
                    lowerBound, upperBound, singleXCount) / 6;

        //Project next year migrant death rates
        //Previous year age 0 migrants
        String lowerBoundX = "1";
        String upperBoundX = "y - 6";
        double lowerBoundY = 7;
        double upperBoundY = 12;
        double areaOfDomain = 12.5;
        double averageLastYearAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificLastYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificLastYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y + 1", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
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
        double averageCurrentFirstHalfYearAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Next 5 months, reach age 1 this year
        lowerBoundX = "y";
        upperBoundX = "y + 6";
        lowerBoundY = 1;
        upperBoundY = 6;
        areaOfDomain = 48;
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Next 5 months, reach age 1 next year
        lowerBoundX = "1";
        upperBoundX = "y";
        lowerBoundY = 1;
        upperBoundY = 6;
        areaOfDomain = 48;
        averageCurrentFirstHalfYearAgeZeroMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
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
        double averageCurrentMonthSixSevenAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age 1 this year
        lowerBoundX = "y";
        upperBoundX = "12";
        lowerBoundY = 6;
        upperBoundY = 7;
        areaOfDomain = 11;
        averageCurrentMonthSixSevenAgeZeroMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
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
        double averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x + 12 - y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.833333333333y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Those who will be age 1 this year
        lowerBoundX = "y";
        upperBoundX = "12";
        lowerBoundY = 7;
        upperBoundY = 12;
        areaOfDomain = 42.5;
        averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival += populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificCurrentYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "0.0833333333333x - 0.833333333333y", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival /= 132;

        //Migrants who arrive next year at age 0
        lowerBoundX = "y + 6";
        upperBoundX = "12";
        lowerBoundY = 0;
        upperBoundY = 6;
        areaOfDomain = 18;
        double averageNextYearAgeZeroMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        populationCalculator.divideArrays(
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "12", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                                populationCalculator.subtractArrays(populationCalculator.createConstantArray(1, xCount, yCount), populationCalculator.mapToTwoDimensionalArray(sexSpecificNextYearInfantCumulativeMortality, "x", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount))
                        ),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.0833333333333x - 0.833333333333y - 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)
                ),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Weight
        averageNextYearAgeZeroMigrantSurvival /= 132;

        //Age 1 migrants who arrive between current year months 6 and 12
        lowerBoundX = "0";
        upperBoundX = "y - 0.5";
        lowerBoundY = 0.5;
        upperBoundY = 1;
        double[][] sexSpecificMigrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(1, sexSpecificCurrentYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageCurrentSecondHalfYearAgeOneMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        sexSpecificMigrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificCurrentYearMortality[1], "-y + 1", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount),
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        sexSpecificMigrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(1, sexSpecificCurrentYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageCurrentSecondHalfYearAgeOneMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(sexSpecificMigrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Age 1 migrants who arrive between next year months 0 and 6
        lowerBoundX = "0";
        upperBoundX = "y + 0.5";
        lowerBoundY = 0;
        upperBoundY = 0.5;
        sexSpecificMigrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(1, sexSpecificNextYearMortality, lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        double averageNextFirstHalfYearAgeOneMigrantSurvival = populationCalculator.doubleSimpsonIntegral(
                populationCalculator.multiplyArrays(
                        sexSpecificMigrantAgeWeights,
                        populationCalculator.exponentiateArray(1 - sexSpecificNextYearMortality[1], "-y + 0.5", lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount)),
                lowerBoundX, upperBoundX, xCount, lowerBoundY, upperBoundY, yCount);
        //Adjust for weight
        sexSpecificMigrantAgeWeights = populationCalculator.estimateMigrationAgeWeights(1, sexSpecificNextYearMortality, "0", "1", xCount, 0, 1, yCount);
        averageNextFirstHalfYearAgeOneMigrantSurvival /= populationCalculator.doubleSimpsonIntegral(sexSpecificMigrantAgeWeights, "0", "1", xCount, 0, 1, yCount);

        //Compute next year population
        //Compute annual migrant populations
        double lastYearAgeZeroMigrants = projectMigration(0, lastYearAgeZeroPopulation, sexSpecificLastYearMigration);
        double currentYearAgeZeroMigrants = projectMigration(0, currentYearAgeZeroPopulation, sexSpecificCurrentYearMigration);
        double currentYearAgeOneMigrants = projectMigration(1, currentYearAgeOnePopulation, sexSpecificCurrentYearMigration);
        double nextYearAgeZeroMigrantsVariable = projectMigration(0, nextYearAgeZeroPopulation[0], sexSpecificNextYearMigration);
        double nextYearAgeZeroMigrantsConstant = projectMigration(0, nextYearAgeZeroPopulation[1], sexSpecificNextYearMigration);
        //Compute variable and constant portions of next year age 1 population
        double nextYearAgeOnePopulationVariable = nextYearAgeZeroMigrantsVariable * averageNextYearAgeZeroMigrantSurvival /
                (1 - sexSpecificNextYearMigration[1] * averageNextFirstHalfYearAgeOneMigrantSurvival);
        double nextYearAgeOnePopulationConstant = (survivingLastYearBirths + survivingCurrentYearBirths +
                    lastYearAgeZeroMigrants * averageLastYearAgeZeroMigrantSurvival +
                    currentYearAgeZeroMigrants * averageCurrentFirstHalfYearAgeZeroMigrantSurvival +
                    currentYearAgeZeroMigrants * averageCurrentMonthSixSevenAgeZeroMigrantSurvival +
                    currentYearAgeZeroMigrants * averageCurrentMonthSevenTwelveAgeZeroMigrantSurvival +
                    currentYearAgeOneMigrants * averageCurrentSecondHalfYearAgeOneMigrantSurvival +
                    nextYearAgeZeroMigrantsConstant * averageNextYearAgeZeroMigrantSurvival) /
                (1 - sexSpecificNextYearMigration[1] * averageNextFirstHalfYearAgeOneMigrantSurvival);
        //Combine variable and constant portions of next year age 1
        double[] nextYearAgeOnePopulation = new double[2];
        nextYearAgeOnePopulation[0] = nextYearAgeOnePopulationVariable;
        nextYearAgeOnePopulation[1] = nextYearAgeOnePopulationConstant;
        return nextYearAgeOnePopulation;
    }

    //Converts variable and constant population pyramids into single final population pyramid
    public double[] solveVariablePyramid(double[] pyramidVariable, double[] pyramidConstant, double[] originalAgeOnePopulation) {
        //Solve age one population
        double ageOnePopulation = computeAgeOnePopulation(new double[]{pyramidVariable[1], pyramidConstant[1]}, originalAgeOnePopulation);

        //Generate new pyramid
        double[] pyramid = new double[pyramidVariable.length];
        for(int age = 0; age < pyramidVariable.length; age++) {
            pyramid[age] = pyramidVariable[age] * ageOnePopulation + pyramidConstant[age];
        }

        return pyramid;
    }

    //Solves ax + b = cx + d to obtain age 1 population from variable and constant portions. Normally c = 1, d = 0.
    public double computeAgeOnePopulation(double[] ageOnePopulation, double[] originalAgeOnePopulation) {
        return (ageOnePopulation[1] - originalAgeOnePopulation[1]) / (originalAgeOnePopulation[0] - ageOnePopulation[0]);
    }

    //RUP (rural urban projection) is made based on US Census Bureau methods for Pop(t + 1, age 1). Necessary as direct projection requires knowledge of previous year births.
    public double ruralUrbanProjectAgeOnePopulationWithMigrationCount(int year, double currentYearAgeZeroPopulation, double nextYearAgeZeroPopulation,
                                                                      Map<Integer, Double> sexSpecificInfantSeparationFactor, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        double nextYearAgeOnePopulation = (currentYearAgeZeroPopulation
                - 0.5 * projectDeaths(0, currentYearAgeZeroPopulation, sexSpecificMortality.get(year)) * sexSpecificInfantSeparationFactor.get(year)
                - 0.5 * projectDeaths(0, nextYearAgeZeroPopulation, sexSpecificMortality.get(year + 1)) * sexSpecificInfantSeparationFactor.get(year + 1)
                + 0.5 * sexSpecificMigration.get(year)[0]
                + 0.5 * sexSpecificMigration.get(year + 1)[1])
                / (1 + 0.5 * sexSpecificMortality.get(year + 1)[1]);
        return nextYearAgeOnePopulation;
    }

    //RUP (rural urban projection) is made based on US Census Bureau methods for Pop(t + 1, age 1). Necessary as direct projection requires knowledge of previous year births.
    public double[] ruralUrbanProjectAgeOnePopulationWithMigrationRates(int year, double currentYearAgeZeroPopulation, double[] nextYearAgeZeroPopulation,
                                                                      Map<Integer, Double> sexSpecificInfantSeparationFactor, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        //Get projection parameters
        double[] sexSpecificCurrentYearMortality = sexSpecificMortality.get(year);
        double[] sexSpecificNextYearMortality = sexSpecificMortality.get(year + 1);
        double[] sexSpecificCurrentYearMigration = sexSpecificMigration.get(year);
        double[] sexSpecificNextYearMigration = sexSpecificMigration.get(year + 1);

        double nextYearAgeOnePopulationVariable = (currentYearAgeZeroPopulation
                - 0.5 * projectDeaths(0, currentYearAgeZeroPopulation, sexSpecificCurrentYearMortality) * sexSpecificInfantSeparationFactor.get(year)
                - 0.5 * projectDeaths(0, nextYearAgeZeroPopulation[0], sexSpecificNextYearMortality) * sexSpecificInfantSeparationFactor.get(year + 1)
                + 0.5 * projectMigration(0, currentYearAgeZeroPopulation, sexSpecificCurrentYearMigration))
                / (1 + 0.5 * sexSpecificNextYearMortality[1] - 0.5 * sexSpecificNextYearMigration[1]);
        double nextYearAgeOnePopulationConstant = (currentYearAgeZeroPopulation
                - 0.5 * projectDeaths(0, currentYearAgeZeroPopulation, sexSpecificCurrentYearMortality) * sexSpecificInfantSeparationFactor.get(year)
                - 0.5 * projectDeaths(0, nextYearAgeZeroPopulation[1], sexSpecificNextYearMortality) * sexSpecificInfantSeparationFactor.get(year + 1)
                + 0.5 * projectMigration(0, currentYearAgeZeroPopulation, sexSpecificCurrentYearMigration))
                / (1 + 0.5 * sexSpecificNextYearMortality[1] - 0.5 * sexSpecificNextYearMigration[1]);
        //Combine variable and constant components of age one population
        double[] nextYearAgeOnePopulation = new double[2];
        nextYearAgeOnePopulation[0] = nextYearAgeOnePopulationVariable;
        nextYearAgeOnePopulation[1] = nextYearAgeOnePopulationConstant;
        return nextYearAgeOnePopulation;
    }
}