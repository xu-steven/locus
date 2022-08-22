import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PopulationProjector {
    //Multithreading configuration
    static int threadCount = 6;
    static int taskCount = 6;
    static ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    //Population projection parameters
    public static PopulationParameters projectionParameters;

    public PopulationProjector() {
        //File locations
        String mortalityLocation = "M:\\Optimization Project\\demographic projections\\alberta_mortality.csv";
        String infantMortalityLocation = "M:\\Optimization Project\\demographic projections\\test_alberta_infant_mortality.csv";
        String fertilityLocation = "M:\\Optimization Project\\demographic projections\\alberta_fertility.csv";
        String migrationLocation = "M:\\Optimization Project\\demographic projections\\alberta_migration.csv";

        projectionParameters = new PopulationParameters(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, "Total migrants");
    }

    public static void main(String[] args) {
        new PopulationProjector();
        //Demographics file
        String demographicsLocation = "M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv";
        List<String> ageAndSexGroups = FileUtils.getCSVHeadings(demographicsLocation);
        double[] populationByAgeAndSexGroup = FileUtils.getInnerDoubleArrayFromCSV(demographicsLocation, FileUtils.getOriginCount(demographicsLocation), FileUtils.getSitesCount(demographicsLocation))[0];
        //In final program, this will be input into projector
        Population initialPopulation = new Population(2000, ageAndSexGroups, populationByAgeAndSexGroup,
                projectionParameters.getMaleMortality(), projectionParameters.getFemaleMortality(),
                projectionParameters.getMaleMigration(), projectionParameters.getFemaleMigration(), projectionParameters.getMigrationFormat(),
                projectionParameters.getMaleInfantSeparationFactor(), projectionParameters.getFemaleInfantSeparationFactor());
        System.out.println("Male pyramid " + initialPopulation.getMalePyramid());
        System.out.println("Female pyramid " + initialPopulation.getFemalePyramid());


        double[][] infantMortality = mapToDoubleArray(projectionParameters.getMaleInfantCumulativeMortality().get(2000), "2y - 3x", "0", "0.33y", 1000, 0, 1, 1000);
        //System.out.println(Arrays.deepToString(infantMortality));
        System.out.println(doubleIntegral(infantMortality, "0", "0.33y", 1000, 0, 1, 1000));
        executor.shutdown();
    }

    //Project population
    public Map<Integer, Population> projectPopulation(Population initialPopulation, int initialYear, int finalYear) {
        return null;
    }

    public Map<Integer, Double> projectNextYearPyramid(int year, Map<Integer, Double> currentPyramid, int oldestPyramidCohortAge, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        Map<Integer, Double> nextYearPyramid = new HashMap<>();
        //Compute cohorts aged 2 through maxCohortAge - 1 as well as preliminary maxCohortAge
        for (Integer age : currentPyramid.keySet()) {
            if (age == 0) {
                continue;
            } else if (age < oldestPyramidCohortAge) {
                nextYearPyramid.put(age + 1, projectNextYearCohortPopulation(age, year, currentPyramid.get(age), sexSpecificMortality, sexSpecificMigration));
            }
        }
        //Add in remaining max age cohort that continues to survive
        double oldestPyramidCohortPopulation = nextYearPyramid.get(oldestPyramidCohortAge);
        oldestPyramidCohortPopulation += projectRemainingOldestCohortPopulation(oldestPyramidCohortAge, year, currentPyramid.get(oldestPyramidCohortAge), sexSpecificMortality, sexSpecificMigration);
        nextYearPyramid.put(oldestPyramidCohortAge, oldestPyramidCohortPopulation);
        return nextYearPyramid;
    }

    //RUP (rural urban projection) is made based on US Census Bureau methods for Pop(t + 1, age 1)
    public static double ruralUrbanProjectionNextYearAgeOnePopulation(int year, double currentYearAgeZeroPopulation, double nextYearAgeZeroPopulation,
                                                                      Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration, Map<Integer, Double> sexSpecificInfantSeparationFactor) {
        double nextYearAgeOnePopulation;
        if (projectionParameters.getMigrationFormat() == 0) { //absolute migration
            nextYearAgeOnePopulation = (currentYearAgeZeroPopulation
                    - 0.5 * projectDeaths(0, year, currentYearAgeZeroPopulation, sexSpecificMortality) * sexSpecificInfantSeparationFactor.get(year)
                    - 0.5 * projectDeaths(0, year + 1, nextYearAgeZeroPopulation, sexSpecificMortality) * sexSpecificInfantSeparationFactor.get(year + 1)
                    + 0.5 * sexSpecificMigration.get(year).get(0) + 0.5 * sexSpecificMigration.get(year + 1).get(1))
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

    //Projects population of cohort in next year, i.e. Pop(t + 1, age + 1)
    public static double projectNextYearCohortPopulation(int age, int year, double currentYearPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
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
    public static double projectRemainingOldestCohortPopulation(int oldestCohortAge, int year, double currentYearOldestCohortPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
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

    //Projects total male and female births next year, updates age zero separation
    public List<Double> projectNextYearAgeZeroPopulation(int year, Map<Integer, Double> nextYearFemalePyramid) {
        return null;
    }

    //Projects age 1 population
    public double projectNextYearAgeOnePopulation(String sex, int year, double currentYearAgeZeroPopulation) {
        return 0;
    }

    public static double projectDeaths(int age, int year, double atRiskPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMortality) {
        return atRiskPopulation * sexSpecificMortality.get(year).get(age);
    }

    public static double projectMigration(int age, int year, double atRiskPopulation, Map<Integer, Map<Integer, Double>> sexSpecificMigration) {
        return atRiskPopulation * sexSpecificMigration.get(year).get(age);
    }

    public static double projectBirths(int year, Map<Integer, Double> femalePyramid) {
        double totalBirths = 0;
        for (int age = 1; age <= projectionParameters.getOldestFertilityCohortAge(); age++) {
            totalBirths += femalePyramid.get(age) * projectionParameters.getSpecificFertility(year, age);
        }
        return totalBirths;
    }

    //Mean proportion of surviving live births from surviving through a to b months ago with constant birth rate; a < b <= 11
    //Using https://www150.statcan.gc.ca/t1/tbl1/en/tv.action?pid=1310071301 or https://www150.statcan.gc.ca/t1/tbl1/en/tv.action?pid=1310007801&pickMembers%5B0%5D=2.1&pickMembers%5B1%5D=4.2&cubeTimeFrame.startYear=2000&cubeTimeFrame.endYear=2015&referencePeriods=20000101%2C20150101
    public static double meanInfantSurvival(String sex, int year, double a, double b) {

        return 0.0;
    }

    //calculate area under curve of infant cumulative mortality between startAge and endAge
    public static double areaUnderInfantCMCurve(int year, double startAge, double endAge, Map<Double, Double> infantCM) {
        //Start age = end age = 12.
        if (startAge == 12) {
            return infantCM.get(12);
        }
        //Otherwise
        int bestLowerBoundStartAgeIndex = 0;
        int bestLowerBoundEndAgeIndex = 0;
        List<Double> orderedAges = new ArrayList<>(infantCM.keySet());
        Collections.sort(orderedAges);
        for (int i = 0; i < orderedAges.size(); i++) {
            if (orderedAges.get(i) <= startAge) {
                bestLowerBoundStartAgeIndex = i;
            }
            if (orderedAges.get(i) <= endAge) {
                bestLowerBoundEndAgeIndex = i;
            }
        }
        double estimatedStartingCM = infantCM.get(orderedAges.get(bestLowerBoundStartAgeIndex)) +
                (infantCM.get(orderedAges.get(bestLowerBoundStartAgeIndex + 1)) - infantCM.get(orderedAges.get(bestLowerBoundStartAgeIndex))) *
                        ((startAge - orderedAges.get(bestLowerBoundStartAgeIndex)) / (orderedAges.get(bestLowerBoundStartAgeIndex + 1) - orderedAges.get(bestLowerBoundStartAgeIndex)));
        double areaUnderInfantCMCurve = (estimatedStartingCM + infantCM.get(orderedAges.get(bestLowerBoundStartAgeIndex + 1))) / 2 * (orderedAges.get(bestLowerBoundStartAgeIndex + 1) - startAge);
        for (int i = bestLowerBoundStartAgeIndex + 1; i < bestLowerBoundEndAgeIndex; i++) {
            areaUnderInfantCMCurve += (infantCM.get(orderedAges.get(bestLowerBoundStartAgeIndex)) + infantCM.get(orderedAges.get(bestLowerBoundStartAgeIndex + 1))) / 2 * (orderedAges.get(bestLowerBoundStartAgeIndex + 1) - orderedAges.get(bestLowerBoundStartAgeIndex));
        }
        if (endAge < 12) {
            double estimatedEndingCM = infantCM.get(orderedAges.get(bestLowerBoundEndAgeIndex)) +
                    (infantCM.get(orderedAges.get(bestLowerBoundEndAgeIndex + 1)) - infantCM.get(orderedAges.get(bestLowerBoundEndAgeIndex))) *
                            ((endAge - orderedAges.get(bestLowerBoundEndAgeIndex)) / (orderedAges.get(bestLowerBoundEndAgeIndex + 1) - orderedAges.get(bestLowerBoundEndAgeIndex)));
            areaUnderInfantCMCurve += (infantCM.get(orderedAges.get(bestLowerBoundStartAgeIndex)) + estimatedEndingCM) / 2 * (endAge - orderedAges.get(bestLowerBoundStartAgeIndex));
        }
        return areaUnderInfantCMCurve;
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
}