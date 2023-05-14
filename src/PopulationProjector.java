import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class PopulationProjector {
    //Population projection parameters
    public PopulationParameters projectionParameters;

    //Multithreading configuration calculator
    public PopulationCalculator populationCalculator;

    public PopulationProjector(String mortalityLocation, double mortalityPerPopulation,
                               String infantMortalityLocation, double infantMortalityPerPopulation,
                               String fertilityLocation, double fertilityPerPopulation,
                               String migrationLocation, double migrationPerPopulation,
                               String migrationFormat, int oldestPyramidCohortAge, int threadCount, int taskCount) {
        projectionParameters = new PopulationParameters(mortalityLocation, mortalityPerPopulation,
                infantMortalityLocation, infantMortalityPerPopulation,
                fertilityLocation, fertilityPerPopulation,
                migrationLocation, migrationPerPopulation,
                migrationFormat, oldestPyramidCohortAge);
        populationCalculator = new PopulationCalculator(threadCount, taskCount);
    }

    public static double projectDeaths(int age, double atRiskPopulation, double[] sexSpecificMortality) {
        return atRiskPopulation * sexSpecificMortality[age];
    }

    public static double projectMigration(int age, double atRiskPopulation, double[] sexSpecificMigration) {
        return atRiskPopulation * sexSpecificMigration[age];
    }

    public double projectBirths(int year, double[] femalePyramid) {
        double totalBirths = 0;
        for (int age = 2; age <= projectionParameters.getOldestFertilityCohortAge(); age++) { //no records of births before age 5
            totalBirths += femalePyramid[age] * projectionParameters.getSpecificFertility(year, age);
        }
        return totalBirths;
    }

    public PopulationParameters getProjectionParameters() {
        return projectionParameters;
    }

    public PopulationCalculator getPopulationCalculator() {
        return populationCalculator;
    }
}