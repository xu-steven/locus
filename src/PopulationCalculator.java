import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PopulationCalculator {
    //Transform (map: age -> cumulative mortality by that age) to double[] for computation of integrals
    public static double[] mapToArray(Map<Double, Double> map, String compositeFunction, double lowerBound, double upperBound, int xCount) {
        CountDownLatch latch = new CountDownLatch(PopulationProjector.taskCount);
        Map<Integer, List<Integer>> partitionedXCount = MultithreadingUtils.orderedPartitionList(IntStream.range(0, xCount).boxed().collect(Collectors.toList()), PopulationProjector.taskCount);
        double[] outputArray = new double[xCount];
        PolynomialSplineFunction interpolatedCubicSpline = mapToCubicSpline(map);
        double[] compositeFunctionCoefficients = parseCoefficients(compositeFunction);
        for (int i = 0; i < PopulationProjector.taskCount; i++) {
            int finalI = i;
            PopulationProjector.executor.execute(() -> {
                for (int j : partitionedXCount.get(finalI)) {
                    double x = lowerBound + (upperBound - lowerBound) * j / ((double) xCount - 1);
                    outputArray[j] = interpolatedCubicSpline.value(compositeFunctionCoefficients[0] * x + compositeFunctionCoefficients[2]); //computeComposition(map, compositeFunction, x, 0);
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

    //Transform (map: age -> cumulative mortality by that age) to double[][] for computation of integral
    //compositeFunction g: R^2 -> R of the form (x,y) -> ax + by + c is composed with map: R -> R to make map(g): R^2 -> R
    public static double[][] mapToTwoDimensionalArray(Map<Double, Double> map, String compositeFunction, String lowerBoundX, String upperBoundX, int xCount, double lowerBoundY, double upperBoundY, int yCount) {
        CountDownLatch latch = new CountDownLatch(PopulationProjector.taskCount);
        Map<Integer, List<Integer>> partitionedYCount = MultithreadingUtils.orderedPartitionList(IntStream.range(0, yCount).boxed().collect(Collectors.toList()), PopulationProjector.taskCount);
        double[][] outputArray = new double[xCount][yCount];
        double[] lowerBoundXCoefficients = parseBound(lowerBoundX);
        double[] upperBoundXCoefficients = parseBound(upperBoundX);
        PolynomialSplineFunction interpolatedCubicSpline = mapToCubicSpline(map);
        double[] compositeFunctionCoefficients = parseCoefficients(compositeFunction);
        for (int i = 0; i < PopulationProjector.taskCount; i++) {
            int finalI = i;
            PopulationProjector.executor.execute(() -> {
                for (int j : partitionedYCount.get(finalI)) {
                    double y = lowerBoundY + (upperBoundY - lowerBoundY) * j / ((double) yCount - 1);
                    double lowerX = lowerBoundXCoefficients[0] * y + lowerBoundXCoefficients[1];
                    double upperX = upperBoundXCoefficients[0] * y + upperBoundXCoefficients[1];
                    for (int k = 0; k < xCount; k++) {
                        double x = lowerX + (upperX - lowerX) * k / ((double) xCount - 1);
                        outputArray[k][j] = interpolatedCubicSpline.value(compositeFunctionCoefficients[0] * x + compositeFunctionCoefficients[1] * y + compositeFunctionCoefficients[2]); //computeComposition(map, compositeFunction, x, y);
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

    //Exact integral of cubic spline approximation based on Map of points in R2. Simpson's rule is exact on cubic with xCount 3.
    public static double integrateCubicSpline(Map<Double, Double> map, double lowerBound, double upperBound) {
        PolynomialSplineFunction interpolatedCubicSpline = mapToCubicSpline(map);
        double[] knotPoints = interpolatedCubicSpline.getKnots();

        //Get left, midpoint, and right values between each pair of knot points. These are representative values for each cubic function between knot points for Simpson's rule.
        double[][] cubicPointValues = new double[knotPoints.length - 1][3];
        for (int i = 0; i < knotPoints.length - 1; i++) {
            //ith cubic polynomial
            cubicPointValues[i][0] = interpolatedCubicSpline.value(knotPoints[i]);
            cubicPointValues[i][1] = interpolatedCubicSpline.value((knotPoints[i] + knotPoints[i + 1]) / 2);
            cubicPointValues[i][2] = interpolatedCubicSpline.value(knotPoints[i + 1]);
        }

        //Apply Simpson's rule
        double numericalIntegral = 0;
        for (int i = 0; i < cubicPointValues.length; i++) {
            numericalIntegral += simpsonIntegral(cubicPointValues[i], knotPoints[i], knotPoints[i + 1], 3);
        }
        return numericalIntegral;
    }

    //Computes integral using a function represented by array[] with xCount estimates of points in domain [lowerBound, upperBound]
    public static double simpsonIntegral(double[] function, double lowerBound, double upperBound, int xCount) {
        double numericalIntegral = 0;
        for (int i = 0; i < xCount; i++) {
            if (i == 0 || i == xCount - 1) {
                numericalIntegral += function[i];
            } else if (i % 2 == 1) {
                numericalIntegral += 4 * function[i];
            } else {
                numericalIntegral += 2 * function[i];
            }
        }
        return numericalIntegral * (upperBound - lowerBound) / (xCount - 1) / 3;
    }

    //Computes integral using a function represented by map: (age in months -> cumulative mortality by that age)
    public static double doubleSimpsonIntegral(double[][] function, String lowerBoundX, String upperBoundX, int xCount, double lowerBoundY, double upperBoundY, int yCount) {
        CountDownLatch latch = new CountDownLatch(PopulationProjector.taskCount);
        Map<Integer, List<Integer>> partitionedYCount = MultithreadingUtils.orderedPartitionList(IntStream.range(0, yCount).boxed().collect(Collectors.toList()), PopulationProjector.taskCount);
        //Integrate with respect to x
        double[] numericalIntegralWrtX = new double[yCount];
        for (int i = 0; i < PopulationProjector.taskCount; i++) {
            int finalI = i;
            PopulationProjector.executor.execute(() -> {
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

    //Computes map(g(x, y)) for compositeFunction g: R2 -> R given by g(x,y) = ax + by + c
    private static double computeComposition(Map<Double, Double> map, String compositeFunction, double x, double y) {
        double[] compositeFunctionCoefficients = parseCoefficients(compositeFunction);
        //linear alternative: return evaluateLinearlyInterpolatedMap(map, compositeFunctionCoefficients[0] * x + compositeFunctionCoefficients[1] * y + compositeFunctionCoefficients[2]);
        return evaluateCubicSplineInterpolatedMap(map, compositeFunctionCoefficients[0] * x + compositeFunctionCoefficients[1] * y + compositeFunctionCoefficients[2]);
    }

    //Evaluates map(x) by linear interpolation
    private static double evaluateLinearlyInterpolatedMap(Map<Double, Double> map, double x) {
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

    //Evaluates map(x) by cubic spline interpolation
    public static double evaluateCubicSplineInterpolatedMap(Map<Double, Double> map, double x) {
        PolynomialSplineFunction interpolatedSpline = mapToCubicSpline(map);
        return interpolatedSpline.value(x);
    }

    public static PolynomialSplineFunction mapToCubicSpline(Map<Double, Double> map) {
        double[] xValues = new double[map.keySet().size()];
        double[] yValues = new double[map.keySet().size()];
        List<Double> sortedKeys = new ArrayList<>(map.keySet());
        Collections.sort(sortedKeys);
        for (int i = 0; i < map.keySet().size(); i++) {
            xValues[i] = sortedKeys.get(i);
            yValues[i] = map.get(sortedKeys.get(i));
        }
        SplineInterpolator cubicSplineInterpolator = new SplineInterpolator();
        return cubicSplineInterpolator.interpolate(xValues, yValues);
    }

    //Fill array with same value
    public static double[] createConstantArray(double c, int xSize) {
        double[] constantArray = new double[xSize];
        Arrays.fill(constantArray, c);
        return constantArray;
    }

    //Fill 2D array with same value
    public static double[][] createConstantArray(double c, int xSize, int ySize) {
        double [][] constantArray = new double[xSize][ySize];
        for (int i = 0; i < constantArray.length; i++) {
            Arrays.fill(constantArray[i], c);
        }
        return constantArray;
    }

    //Computes sum firstArray_(i) for all i
    public static double[] addArrays(double[]... arrays){
        double[] output = new double[arrays[0].length];
        for (int i = 0; i < arrays[0].length; i++) {
            double positionSum = 0;
            for (double[] array : arrays) {
                positionSum += array[i];
            }
            output[i] = positionSum;
        }
        return output;
    }

    //Computes firstArray_(i,j) + secondArray_(i,j) for all (i,j)
    public static double[][] addArrays(double[][]... arrays){
        CountDownLatch latch = new CountDownLatch(PopulationProjector.taskCount);
        Map<Integer, List<Integer>> partitionedArrayIndex = MultithreadingUtils.orderedPartitionList(IntStream.range(0, arrays[0].length).boxed().collect(Collectors.toList()), PopulationProjector.taskCount);
        double[][] output = new double[arrays[0].length][arrays[0][0].length];
        for (int i = 0; i < PopulationProjector.taskCount; i++) {
            int finalI = i;
            PopulationProjector.executor.execute(() -> {
                for (int j : partitionedArrayIndex.get(finalI)) {
                    for (int k = 0; k < arrays[0][0].length; k++) {
                        double positionSum = 0; //additive identity
                        for (double[][] array : arrays) {
                            positionSum += array[j][k];
                        }
                        output[j][k] = positionSum;
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
    public static double[] subtractArrays(double[] firstArray, double[] secondArray){
        double[] output = new double[firstArray.length];
        for (int i = 0; i < firstArray.length; i++) {
            output[i] = firstArray[i] - secondArray[i];
        }
        return output;
    }

    //Computes firstArray_(i,j) - secondArray_(i,j) for all (i,j)
    public static double[][] subtractArrays(double[][] firstArray, double[][] secondArray){
        CountDownLatch latch = new CountDownLatch(PopulationProjector.taskCount);
        Map<Integer, List<Integer>> partitionedArrayIndex = MultithreadingUtils.orderedPartitionList(IntStream.range(0, firstArray.length).boxed().collect(Collectors.toList()), PopulationProjector.taskCount);
        double[][] output = new double[firstArray.length][firstArray[0].length];
        for (int i = 0; i < PopulationProjector.taskCount; i++) {
            int finalI = i;
            PopulationProjector.executor.execute(() -> {
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
    static double[] multiplyArrays(double[]... arrays){
        double[] output = new double[arrays[0].length];
        for (int i = 0; i < arrays[0].length; i++) {
            double positionProduct = 1; //multiplicative identity
            for (double[] array : arrays) {
                positionProduct *= array[i];
            }
            output[i] = positionProduct;
        }
        return output;
    }

    //Computes product firstArray_(i) for all i
    public static double[][] multiplyArrays(double[][]... arrays){
        CountDownLatch latch = new CountDownLatch(PopulationProjector.taskCount);
        Map<Integer, List<Integer>> partitionedArrayIndex = MultithreadingUtils.orderedPartitionList(IntStream.range(0, arrays[0].length).boxed().collect(Collectors.toList()), PopulationProjector.taskCount);
        double[][] output = new double[arrays[0].length][arrays[0][0].length];
        for (int i = 0; i < PopulationProjector.taskCount; i++) {
            int finalI = i;
            PopulationProjector.executor.execute(() -> {
                for (int j : partitionedArrayIndex.get(finalI)) {
                    for (int k = 0; k < arrays[0][0].length; k++) {
                        double positionProduct = 1; //multiplicative identity
                        for (double[][] array : arrays) {
                            positionProduct *= array[j][k];
                        }
                        output[j][k] = positionProduct;
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
    public static double[] divideArrays(double[] firstArray, double[] secondArray){
        double[] output = new double[firstArray.length];
        for (int i = 0; i < firstArray.length; i++) {
            output[i] = firstArray[i] / secondArray[i];
        }
        return output;
    }

    //Computes firstArray_(i,j) / secondArray_(i,j) for all (i,j)
    public static double[][] divideArrays(double[][] firstArray, double[][] secondArray){
        CountDownLatch latch = new CountDownLatch(PopulationProjector.taskCount);
        Map<Integer, List<Integer>> partitionedArrayIndex = MultithreadingUtils.orderedPartitionList(IntStream.range(0, firstArray.length).boxed().collect(Collectors.toList()), PopulationProjector.taskCount);
        double[][] output = new double[firstArray.length][firstArray[0].length];
        for (int i = 0; i < PopulationProjector.taskCount; i++) {
            int finalI = i;
            PopulationProjector.executor.execute(() -> {
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

    //Creates 1d array of c^(ax+by+k), by = 0 as 1d
    public static double[] exponentiateArray(double c, String compositeFunction, double lowerBound, double upperBound, int xCount){
        double[] outputArray = new double[xCount];
        double[] compositeFunctionCoefficients = parseCoefficients(compositeFunction); //ax + by + c to [a, b, c]
        for (int i = 0; i < xCount; i++) {
            double x = lowerBound + (upperBound - lowerBound) * i / ((double) xCount - 1);
            outputArray[i] = Math.pow(c, compositeFunctionCoefficients[0] * x + compositeFunctionCoefficients[2]);
        }
        return outputArray;
    }

    //Creates 2d array of c^(ax+by+k)
    public static double[][] exponentiateArray(double c, String compositeFunction, String lowerBoundX, String upperBoundX, int xCount, double lowerBoundY, double upperBoundY, int yCount){
        CountDownLatch latch = new CountDownLatch(PopulationProjector.taskCount);
        Map<Integer, List<Integer>> partitionedYCount = MultithreadingUtils.orderedPartitionList(IntStream.range(0, yCount).boxed().collect(Collectors.toList()), PopulationProjector.taskCount);
        double[][] outputArray = new double[xCount][yCount];
        double[] compositeFunctionCoefficients = parseCoefficients(compositeFunction); //ax + by + c to [a, b, c]
        double[] lowerBoundXCoefficients = parseBound(lowerBoundX);
        double[] upperBoundXCoefficients = parseBound(upperBoundX);
        for (int i = 0; i < PopulationProjector.taskCount; i++) {
            int finalI = i;
            PopulationProjector.executor.execute(() -> {
                for (int j : partitionedYCount.get(finalI)) {
                    double y = lowerBoundY + (upperBoundY - lowerBoundY) * j / ((double) yCount - 1);
                    double lowerX = lowerBoundXCoefficients[0] * y + lowerBoundXCoefficients[1];
                    double upperX = upperBoundXCoefficients[0] * y + upperBoundXCoefficients[1];
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

    //Computes age weights using sex-specific mortality in format year -> (age -> ageWeights based on xCount)
    public static double[] estimatePopulationAgeWeights(int year, int age, Map<Integer, Map<Integer, Double>> sexSpecificMortality, double lowerBound, double upperBound, int xCount) {
        double[] ageWeights = new double[xCount];
        for (int i = 0; i < xCount; i++) {
            ageWeights[i] = Math.pow(sexSpecificMortality.get(year).get(age), lowerBound + (upperBound - lowerBound) * i / ((double) xCount - 1));
        }
        return ageWeights;
    }

    //Computes age weights using sex-specific mortality in format year -> (age -> ageWeights based on xCount and yCount)
    public static double[][] estimateMigrationAgeWeights(int year, int age, Map<Integer, Map<Integer, Double>> sexSpecificMortality, String lowerBoundX, String upperBoundX, int xCount, double lowerBoundY, double upperBoundY, int yCount) {
        CountDownLatch latch = new CountDownLatch(PopulationProjector.taskCount);
        Map<Integer, List<Integer>> partitionedYCount = MultithreadingUtils.orderedPartitionList(IntStream.range(0, yCount).boxed().collect(Collectors.toList()), PopulationProjector.taskCount);
        double[][] ageWeights = new double[xCount][yCount];
        double[] lowerBoundXCoefficients = parseBound(lowerBoundX);
        double[] upperBoundXCoefficients = parseBound(upperBoundX);
        for (int i = 0; i < PopulationProjector.taskCount; i++) {
            int finalI = i;
            PopulationProjector.executor.execute(() -> {
                for (int j : partitionedYCount.get(finalI)) {
                    double y = lowerBoundY + (upperBoundY - lowerBoundY) * j / ((double) yCount - 1);
                    double lowerX = lowerBoundXCoefficients[0] * y + lowerBoundXCoefficients[1];
                    double upperX = upperBoundXCoefficients[0] * y + upperBoundXCoefficients[1];
                    for (int k = 0; k < xCount; k++) {
                        double x = lowerX + (upperX - lowerX) * k / ((double) xCount - 1);
                        ageWeights[k][j] = Math.pow(sexSpecificMortality.get(year).get(age), x);
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
        return ageWeights;
    }

    //Parses x bound as function of y of the form ay + b
    public static double[] parseBound(String bound) {
        double[] parsedBounds = new double[2]; //parsedBounds[0] = a, parsedBounds[1] = b
        bound = bound.replaceAll("\\s", "");
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

        //Iterate through remainder of function except for final term
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

        //Process final term
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