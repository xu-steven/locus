import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuralUrbanPopulationProjector extends PopulationProjector{
    public RuralUrbanPopulationProjector(String mortalityLocation, double mortalityPerPopulation,
                                         String infantMortalityLocation, double infantMortalityPerPopulation,
                                         String fertilityLocation, double fertilityPerPopulation,
                                         String migrationLocation, double migrationPerPopulation,
                                         String migrationFormat, int oldestPyramidCohortAge) {
        super(mortalityLocation, mortalityPerPopulation,
                infantMortalityLocation, infantMortalityPerPopulation,
                fertilityLocation, fertilityPerPopulation,
                migrationLocation, migrationPerPopulation,
                migrationFormat, oldestPyramidCohortAge, 1, 1);
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
        RuralUrbanPopulationProjector projector = new RuralUrbanPopulationProjector(mortalityLocation, 1000, infantMortalityLocation, 1000, fertilityLocation, 1000, migrationLocation, 1000,"Total migrants",
                Population.determineOldestPyramidCohortAge(ageAndSexGroups));

        //In final program, this will be input into projector
        Population initialPopulation = new Population(2000, ageAndSexGroups, populationByAgeAndSexGroup,
                projector.getProjectionParameters().getMaleMortality(), projector.getProjectionParameters().getFemaleMortality(),
                projector.getProjectionParameters().getMaleMigration(), projector.getProjectionParameters().getFemaleMigration(), projector.getProjectionParameters().getMigrationFormat(),
                projector.getProjectionParameters().getMaleInfantSeparationFactor(), projector.getProjectionParameters().getFemaleInfantSeparationFactor());
        //System.out.println("Male pyramid " + initialPopulation.getMalePyramid());
        //System.out.println("Female pyramid " + initialPopulation.getFemalePyramid());

        System.out.println("Start projection.");
        Population yearOnePopulation = projector.projectNextYearPopulation(initialPopulation);
        Population yearTwoPopulation = projector.projectNextYearPopulation(yearOnePopulation);
        Map<Integer, Population> projectedPopulation = projector.projectPopulation(initialPopulation, 2000, 2002);
        //System.out.println("Year zero male pyramid " + initialPopulation.getMalePyramid());
        //System.out.println("Year one male pyramid " + yearOnePopulation.getMalePyramid());
        //System.out.println("Year two male pyramid " + yearTwoPopulation.getMalePyramid());
        System.out.println("Year zero female pyramid " + initialPopulation.getFemalePyramid());
        System.out.println("Year one female pyramid " + yearOnePopulation.getFemalePyramid());
        System.out.println("Year two female pyramid " + yearTwoPopulation.getFemalePyramid());
        //System.out.println("Projected year one pyramid " + projectedPopulation.get(2001).getFemalePyramid());
        //System.out.println("Projected year two pyramid " + projectedPopulation.get(2002).getFemalePyramid());

        projector.getPopulationCalculator().getExecutor().shutdown();
    }

    //Project population storing population of every year from initialYear to finalYear
    public Map<Integer, Population> projectPopulation(Population initialPopulation, int initialYear, int finalYear) {
        Map<Integer, Population> populationProjection = new HashMap<>();
        populationProjection.put(initialYear, initialPopulation);
        for (int year = initialYear; year < finalYear; year++) {
            populationProjection.put(year + 1, projectNextYearPopulation(populationProjection.get(year)));
        }
        return populationProjection;
    }

    //When previous year population is not known
    public Population projectNextYearPopulation(Population currentYearPopulation) {
        int year = currentYearPopulation.getYear();
        double[] currentMalePyramid = currentYearPopulation.getMalePyramid();
        double[] currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        double[] nextYearMalePyramid = new double[currentMalePyramid.length];
        double[] nextYearFemalePyramid = new double[currentFemalePyramid.length];

        //Compute male cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (int age = 0; age < oldestPyramidCohortAge - 1; age++) {
            if (age == 0) {
                continue;
            } else {
                nextYearMalePyramid[age + 1] = projectOtherCohortPopulation(age, year, currentMalePyramid[age], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
            }
        }
        //Add in remaining max age cohort that continues to survive
        nextYearMalePyramid[oldestPyramidCohortAge] = projectOldestCohortPopulation(oldestPyramidCohortAge, year, currentMalePyramid[oldestPyramidCohortAge - 1], currentMalePyramid[oldestPyramidCohortAge], projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (int age = 0; age < oldestPyramidCohortAge - 1; age++) {
            if (age == 0) {
                continue;
            } else {
                nextYearFemalePyramid[age + 1] = projectOtherCohortPopulation(age, year, currentFemalePyramid[age], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
            }
        }
        //Add in remaining max age cohort that continues to survive
        nextYearFemalePyramid[oldestPyramidCohortAge] = projectOldestCohortPopulation(oldestPyramidCohortAge, year, currentFemalePyramid[oldestPyramidCohortAge - 1], currentFemalePyramid[oldestPyramidCohortAge], projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());

        //Project male and female births for remainder of current year and first half of next year
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirths = projectBirths(year + 1, nextYearFemalePyramid);

        //Project age 0 and 1 male cohort
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearMaleBirths = nextYearBirths * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        nextYearMalePyramid[0] = projectAgeZeroPopulation(year, currentMalePyramid[0], currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantSeparationFactor(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());
        nextYearMalePyramid[1] = projectAgeOnePopulation(year, currentMalePyramid[0], nextYearMalePyramid[0], projectionParameters.getMaleInfantSeparationFactor(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration());

        //Project age 0 and 1 female cohort
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearFemaleBirths = nextYearBirths / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        nextYearFemalePyramid[0] = projectAgeZeroPopulation(year, currentFemalePyramid[0], currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantSeparationFactor(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());
        nextYearFemalePyramid[1] = projectAgeOnePopulation(year, currentFemalePyramid[0], nextYearFemalePyramid[0], projectionParameters.getFemaleInfantSeparationFactor(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration());

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //Projects population of cohort in next year, i.e. Pop(t + 1, age + 1)
    public double projectOtherCohortPopulation(int age, int year, double currentYearPopulation, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        double nextYearPopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearPopulation = (currentYearPopulation - 0.5 * projectDeaths(age, currentYearPopulation, sexSpecificMortality.get(year))
                    + 0.5 * sexSpecificMigration.get(year)[age] + 0.5 * sexSpecificMigration.get(year + 1)[age + 1])
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[age + 1]);
        } else { //migration rates
            nextYearPopulation = (currentYearPopulation - 0.5 * projectDeaths(age, currentYearPopulation, sexSpecificMortality.get(year))
                    + 0.5 * projectMigration(age, currentYearPopulation, sexSpecificMigration.get(year)))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[age + 1] - 0.5 * sexSpecificMigration.get(year + 1)[age + 1]);
        }
        return nextYearPopulation;
    }

    //Projects population of the oldest cohort in next year
    public double projectOldestCohortPopulation(int oldestCohortAge, int year, double currentYearSecondOldestCohortPopulation, double currentYearOldestCohortPopulation, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        double nextYearOldestCohortPopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearOldestCohortPopulation = (currentYearSecondOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge - 1, currentYearSecondOldestCohortPopulation, sexSpecificMortality.get(year))
                    + 0.5 * sexSpecificMigration.get(year)[oldestCohortAge - 1] + 0.5 * sexSpecificMigration.get(year + 1)[oldestCohortAge])
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[oldestCohortAge]);
            nextYearOldestCohortPopulation += (currentYearOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge, currentYearOldestCohortPopulation, sexSpecificMortality.get(year))
                    + 0.5 * sexSpecificMigration.get(year)[oldestCohortAge])
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[oldestCohortAge]);
        } else { //migration rates
            nextYearOldestCohortPopulation = (currentYearSecondOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge - 1, currentYearSecondOldestCohortPopulation, sexSpecificMortality.get(year))
                    + 0.5 * projectMigration(oldestCohortAge - 1, currentYearSecondOldestCohortPopulation, sexSpecificMigration.get(year)))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[oldestCohortAge] - 0.5 * sexSpecificMigration.get(year + 1)[oldestCohortAge]);
            nextYearOldestCohortPopulation += (currentYearOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge, currentYearOldestCohortPopulation, sexSpecificMortality.get(year))
                    + 0.5 * projectMigration(oldestCohortAge, currentYearOldestCohortPopulation, sexSpecificMigration.get(year)))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[oldestCohortAge] - 0.5 * sexSpecificMigration.get(year + 1)[oldestCohortAge]);
        }
        return nextYearOldestCohortPopulation;
    }

    //RUP (rural urban projection) is made based on US Census Bureau methods
    public double projectAgeZeroPopulation(int year, double currentYearPopulation, double currentYearBirths, double nextYearBirths,
                                                     Map<Integer, Double> sexSpecificInfantSeparationFactor, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        double nextYearAgeOnePopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearAgeOnePopulation = 0.5 * (currentYearBirths + nextYearBirths
                    - projectDeaths(0, currentYearPopulation, sexSpecificMortality.get(year)) * (1 - sexSpecificInfantSeparationFactor.get(year))
                    + sexSpecificMigration.get(year + 1)[0])
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[0] * (1 - sexSpecificInfantSeparationFactor.get(year + 1)));
        } else { //migration rates
            nextYearAgeOnePopulation = 0.5 * (currentYearBirths + nextYearBirths
                    - projectDeaths(0, currentYearPopulation, sexSpecificMortality.get(year)) * (1 - sexSpecificInfantSeparationFactor.get(year)))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[0] * (1 - sexSpecificInfantSeparationFactor.get(year + 1)) - 0.5 * sexSpecificMigration.get(year + 1)[0]);
        }
        return nextYearAgeOnePopulation;
    }

    //RUP (rural urban projection) is made based on US Census Bureau methods
    public double projectAgeOnePopulation(int year, double currentYearAgeZeroPopulation, double nextYearAgeZeroPopulation,
                                          Map<Integer, Double> sexSpecificInfantSeparationFactor, Map<Integer, double[]> sexSpecificMortality, Map<Integer, double[]> sexSpecificMigration) {
        double nextYearAgeOnePopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearAgeOnePopulation = (currentYearAgeZeroPopulation
                    - 0.5 * projectDeaths(0, currentYearAgeZeroPopulation, sexSpecificMortality.get(year)) * sexSpecificInfantSeparationFactor.get(year)
                    - 0.5 * projectDeaths(0, nextYearAgeZeroPopulation, sexSpecificMortality.get(year + 1)) * sexSpecificInfantSeparationFactor.get(year + 1)
                    + 0.5 * sexSpecificMigration.get(year)[0]
                    + 0.5 * sexSpecificMigration.get(year + 1)[1])
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[1]);
        } else { //migration rates
            nextYearAgeOnePopulation = (currentYearAgeZeroPopulation
                    - 0.5 * projectDeaths(0, currentYearAgeZeroPopulation, sexSpecificMortality.get(year)) * sexSpecificInfantSeparationFactor.get(year)
                    - 0.5 * projectDeaths(0, nextYearAgeZeroPopulation, sexSpecificMortality.get(year + 1)) * sexSpecificInfantSeparationFactor.get(year + 1)
                    + 0.5 * projectMigration(0, currentYearAgeZeroPopulation, sexSpecificMigration.get(year)))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1)[1] - 0.5 * sexSpecificMigration.get(year + 1)[1]);
        }
        return nextYearAgeOnePopulation;
    }
}