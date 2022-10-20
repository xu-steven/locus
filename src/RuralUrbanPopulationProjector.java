import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuralUrbanPopulationProjector extends PopulationProjector{
    public RuralUrbanPopulationProjector(String mortalityLocation, String infantMortalityLocation, String fertilityLocation, String migrationLocation, String migrationFormat, int oldestPyramidCohortAge) {
        super(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, migrationFormat, oldestPyramidCohortAge, 1, 1);
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
        RuralUrbanPopulationProjector projector = new RuralUrbanPopulationProjector(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, "Total migrants",
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
        Map<Integer, Double> currentMalePyramid = currentYearPopulation.getMalePyramid();
        Map<Integer, Double> currentFemalePyramid = currentYearPopulation.getFemalePyramid();
        int oldestPyramidCohortAge = currentYearPopulation.getOldestPyramidCohortAge();

        Map<Integer, Double> nextYearMalePyramid = new HashMap<>();
        Map<Integer, Double> nextYearFemalePyramid = new HashMap<>();

        //Compute male cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (Integer age : currentMalePyramid.keySet()) {
            if (age == 0) {
                continue;
            } else if (age < oldestPyramidCohortAge - 1) {
                nextYearMalePyramid.put(age + 1, projectOtherCohortPopulation(age, year, currentMalePyramid.get(age), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration()));
            }
        }
        //Add in remaining max age cohort that continues to survive
        nextYearMalePyramid.put(oldestPyramidCohortAge, projectOldestCohortPopulation(oldestPyramidCohortAge, year, currentMalePyramid.get(oldestPyramidCohortAge - 1), currentMalePyramid.get(oldestPyramidCohortAge), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration()));

        //Compute female cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (Integer age : currentFemalePyramid.keySet()) {
            if (age == 0) {
                continue;
            } else if (age < oldestPyramidCohortAge - 1) {
                nextYearFemalePyramid.put(age + 1, projectOtherCohortPopulation(age, year, currentFemalePyramid.get(age), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration()));
            }
        }
        //Add in remaining max age cohort that continues to survive
        nextYearFemalePyramid.put(oldestPyramidCohortAge, projectOldestCohortPopulation(oldestPyramidCohortAge, year, currentFemalePyramid.get(oldestPyramidCohortAge - 1), currentFemalePyramid.get(oldestPyramidCohortAge), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration()));

        //Project male and female births for remainder of current year and first half of next year
        double currentYearBirths = projectBirths(year, currentFemalePyramid);
        double nextYearBirths = projectBirths(year + 1, nextYearFemalePyramid);

        //Project age 0 and 1 male cohort
        double currentYearMaleBirths = currentYearBirths * projectionParameters.getHumanSexRatio(year) / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearMaleBirths = nextYearBirths * projectionParameters.getHumanSexRatio(year + 1) / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        nextYearMalePyramid.put(0, projectAgeZeroPopulation(year, currentMalePyramid.get(0), currentYearMaleBirths, nextYearMaleBirths, projectionParameters.getMaleInfantSeparationFactor(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration()));
        nextYearMalePyramid.put(1, projectAgeOnePopulation(year, currentMalePyramid.get(0), nextYearMalePyramid.get(0), projectionParameters.getMaleInfantSeparationFactor(), projectionParameters.getMaleMortality(), projectionParameters.getMaleMigration()));

        //Project age 0 and 1 female cohort
        double currentYearFemaleBirths = currentYearBirths / (projectionParameters.getHumanSexRatio(year)  + 1);
        double nextYearFemaleBirths = nextYearBirths / (projectionParameters.getHumanSexRatio(year + 1)  + 1);
        nextYearFemalePyramid.put(0, projectAgeZeroPopulation(year, currentFemalePyramid.get(0), currentYearFemaleBirths, nextYearFemaleBirths, projectionParameters.getFemaleInfantSeparationFactor(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration()));
        nextYearFemalePyramid.put(1, projectAgeOnePopulation(year, currentFemalePyramid.get(0), nextYearFemalePyramid.get(0), projectionParameters.getFemaleInfantSeparationFactor(), projectionParameters.getFemaleMortality(), projectionParameters.getFemaleMigration()));

        return new Population(year + 1, nextYearMalePyramid, nextYearFemalePyramid, oldestPyramidCohortAge);
    }

    //Projects population of cohort in next year, i.e. Pop(t + 1, age + 1)
    public double projectOtherCohortPopulation(int age, int year, double currentYearPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
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
    public double projectOldestCohortPopulation(int oldestCohortAge, int year, double currentYearSecondOldestCohortPopulation, double currentYearOldestCohortPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        double nextYearOldestCohortPopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearOldestCohortPopulation = (currentYearSecondOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge - 1, year, currentYearSecondOldestCohortPopulation, sexSpecificMortality)
                    + 0.5 * sexSpecificMigration.get(year).get(oldestCohortAge - 1) + 0.5 * sexSpecificMigration.get(year + 1).get(oldestCohortAge))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(oldestCohortAge));
            nextYearOldestCohortPopulation += (currentYearOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge, year, currentYearOldestCohortPopulation, sexSpecificMortality)
                    + 0.5 * sexSpecificMigration.get(year).get(oldestCohortAge))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(oldestCohortAge));
        } else { //migration rates
            nextYearOldestCohortPopulation = (currentYearSecondOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge - 1, year, currentYearSecondOldestCohortPopulation, sexSpecificMortality)
                    + 0.5 * projectMigration(oldestCohortAge - 1, year, currentYearSecondOldestCohortPopulation, sexSpecificMigration))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(oldestCohortAge) - 0.5 * sexSpecificMigration.get(year + 1).get(oldestCohortAge));
            nextYearOldestCohortPopulation += (currentYearOldestCohortPopulation - 0.5 * projectDeaths(oldestCohortAge, year, currentYearOldestCohortPopulation, sexSpecificMortality)
                    + 0.5 * projectMigration(oldestCohortAge, year, currentYearOldestCohortPopulation, sexSpecificMigration))
                    / (1 + 0.5 * sexSpecificMortality.get(year + 1).get(oldestCohortAge) - 0.5 * sexSpecificMigration.get(year + 1).get(oldestCohortAge));
        }
        return nextYearOldestCohortPopulation;
    }

    //RUP (rural urban projection) is made based on US Census Bureau methods
    public double projectAgeZeroPopulation(int year, double currentYearPopulation, double currentYearBirths, double nextYearBirths,
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

    //RUP (rural urban projection) is made based on US Census Bureau methods
    public double projectAgeOnePopulation(int year, double currentYearAgeZeroPopulation, double nextYearAgeZeroPopulation,
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
}