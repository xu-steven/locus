import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;

public class Simulator {
    public static int iterations = 50;

    public static void main(String[] args) {
        //Demographics file
        String demographicsLocation = "M:\\Optimization Project Alpha\\demographic projections\\alberta2021_demographics.csv";

        //Case incidence rate file
        String caseIncidenceRateLocation = "M:\\Optimization Project Alpha\\cancer projection\\alberta_cancer_incidence.csv";

        CaseSimulator caseSimulator = new CaseSimulator(demographicsLocation, caseIncidenceRateLocation);
        CaseCounts simulatedCaseCounts = caseSimulator.simulateCases();

        //Test against expected cases but computing mean simulated cases by origin, for total of testIterations simulations
        int testIterations = 0; //use 0 to eliminate comparisons
        double[] sumOfSimulations = simulatedCaseCounts.caseCountByOrigin;
        for (int i = 1; i < testIterations; i++) {
            simulatedCaseCounts = caseSimulator.simulateCases();
            sumOfSimulations = addArrays(sumOfSimulations, simulatedCaseCounts.caseCountByOrigin);
        }
        if (testIterations > 0) {
            double[] averageSimulatedCaseCounts = DoubleStream.of(sumOfSimulations).map(d -> d / testIterations).toArray();
            //Compute expected case counts
            CaseCounts expectedCaseCounts = caseSimulator.expectedCases();
            //Compare difference of average simulated and expected case counts
            double[] differenceInCaseCounts = subtractArrays(averageSimulatedCaseCounts, expectedCaseCounts.caseCountByOrigin);
            System.out.println("Difference in cases counts by origin " + Arrays.toString(differenceInCaseCounts));
            //Compare average of differences
            System.out.println("Average difference of " + averageArray(differenceInCaseCounts));
        }

        //List<List<Double>> costDifference = simulatedCostComparison(caseSimulator, iterations); //can use array but no practical speed difference here

        //System.out.println("Costs " + costDifference.get(0) + " for optimized locations versus " + costDifference.get(1) + " for actual locations.");
    }

    public static List<Double> simulatedCosts(CaseSimulator caseSimulator, int iterations) {
        return null;
    }

    public static List<List<Double>> simulatedCostComparison(CaseSimulator caseSimulator, int iterations){
        return null;
    }

    //Add arrays together
    public static double[] addArrays(double[]... arrays) {
        double[] sumOfArrays = new double[arrays[0].length];
        for (int i = 0; i < sumOfArrays.length; i++) {
            double value = 0;
            for (double[] array : arrays) {
                value += array[i];
            }
            sumOfArrays[i] = value;
        }
        return sumOfArrays;
    }

    //Subtract two arrays
    public static double[] subtractArrays(double[] firstArray, double[] secondArray) {
        double[] firstMinusSecondArray = new double[firstArray.length];
        for (int i = 0; i < firstMinusSecondArray.length; i++) {
            firstMinusSecondArray[i] = firstArray[i] - secondArray[i];
        }
        return firstMinusSecondArray;
    }

    //Find average of double array
    public static double averageArray(double[] array) {
        double sum = 0;
        for (double d : array) {
            sum += d;
        }
        return sum / array.length;
    }
}