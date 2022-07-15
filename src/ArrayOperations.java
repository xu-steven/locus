import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ArrayOperations {
    //Merge two double arrays with same number of rows, [][].
    public static <E> List<List<E>> mergeArrays(List<List<E>> firstArray, List<List<E>> secondArray) {
        List<List<E>> mergedArray = new ArrayList<>();
        for (int i = 0; i < firstArray.size(); i++) {
            mergedArray.add(Stream.concat(firstArray.get(i).stream(), secondArray.get(i).stream()).toList());
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
