import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ArrayOperations {
    //Shuffles an array of integers
    public static void shuffleIntegerArray(int[] array)
    {
        Random random = ThreadLocalRandom.current();
        for (int i = array.length - 1; i > 0; i--)
        {
            int index = random.nextInt(i + 1);
            int a = array[index];
            array[index] = array[i];
            array[i] = a;
        }
    }

    //Merge two double arrays with same number of rows, [][].
    public static double[][] mergeDoubleArrays(double[][] firstArray, double[][] secondArray) {
        double[][] mergedArray = new double[firstArray.length][firstArray[0].length + secondArray[0].length];
        for (int i = 0; i < firstArray.length; i++) {
            for (int j = 0; j < firstArray[i].length; j++) {
                mergedArray[i][j] = firstArray[i][j];
            }
            for (int j = firstArray[i].length; j < firstArray[i].length + secondArray[i].length; j++) {
                mergedArray[i][j] = secondArray[i][j - firstArray[i].length];
            }
        }
        return mergedArray;
    }

    //Increment every value in array by integer
    public static List<List<Integer>> incrementList(List<List<Integer>> array, Integer increment) {
        List<List<Integer>> output = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            List<Integer> outputRow = new ArrayList<>();
            for (int j = 0; j < array.get(i).size(); j++) {
                outputRow.add(array.get(i).get(j) + increment);
            }
            output.add(outputRow);
        }
        return output;
    }

    //Merge two double arrays with same number of rows, [][].
    public static List<List<Integer>> mergeIntegerLists(List<List<Integer>> firstArray, List<List<Integer>> secondArray) {
        List<List<Integer>> mergedIntegerList = new ArrayList<>();
        for (int i = 0; i < firstArray.size(); i++) {
            List<Integer> nextList = new ArrayList<>();
            for (int j = 0; j < firstArray.get(i).size(); j++) {
                nextList.add(firstArray.get(i).get(j));
            }
            for (int j = 0; j < secondArray.get(i).size(); j++) {
                nextList.add(secondArray.get(i).get(j));
            }
            mergedIntegerList.add(nextList);
        }
        return mergedIntegerList;
    }

    //Sums every double in array of doubles
    public static double sumDoubleArray(double[] doubleArray) {
        double sum = doubleArray[0];
        for (int i = 1; i < doubleArray.length; i++)
            sum += doubleArray[i];
        return sum;
    }
}