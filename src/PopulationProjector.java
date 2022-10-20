import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class PopulationProjector {
    //Population projection parameters
    public PopulationParameters projectionParameters;

    //Multithreading configuration calculator
    public PopulationCalculator populationCalculator;

    public PopulationProjector(String mortalityLocation, String infantMortalityLocation, String fertilityLocation, String migrationLocation, String migrationFormat, int oldestPyramidCohortAge, int threadCount, int taskCount) {
        projectionParameters = new PopulationParameters(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, migrationFormat, oldestPyramidCohortAge);
        populationCalculator = new PopulationCalculator(threadCount, taskCount);
    }

    public static double projectDeaths(int age, int year, double atRiskPopulation, Map<Integer, double[]> sexSpecificMortality) {
        return atRiskPopulation * sexSpecificMortality.get(year)[age];
    }

    public static double projectMigration(int age, int year, double atRiskPopulation, Map<Integer, double[]> sexSpecificMigration) {
        return atRiskPopulation * sexSpecificMigration.get(year)[age];
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