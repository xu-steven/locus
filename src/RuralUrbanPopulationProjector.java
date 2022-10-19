import java.util.Map;

public class RuralUrbanPopulationProjector extends PopulationProjector{
    public RuralUrbanPopulationProjector(String mortalityLocation, String infantMortalityLocation, String fertilityLocation, String migrationLocation, String migrationFormat, int oldestPyramidCohortAge) {
        super(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, migrationFormat, oldestPyramidCohortAge, 1, 1);
    }

    //Projects population of the oldest cohort in next year
    public double ruralUrbanProjectOldestCohortPopulation(int oldestCohortAge, int year, double currentYearSecondOldestCohortPopulation, double currentYearOldestCohortPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
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

    //RUP (rural urban projection) is made based on US Census Bureau methods for Pop(t + 1, age 1)
    public double ruralUrbanProjectAgeZeroPopulation(int year, double currentYearPopulation, double currentYearBirths, double nextYearBirths,
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
    public double ruralUrbanProjectAgeOnePopulation(int year, double currentYearAgeZeroPopulation, double nextYearAgeZeroPopulation,
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