import java.util.ArrayList;
import java.util.List;

public class ArrayOperations {
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
    public static List<List<Integer>> incrementArray(List<List<Integer>> array, Integer increment) {
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
}