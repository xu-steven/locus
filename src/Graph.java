public class Graph {
    public static double[] graphArray;// = parseCSV(graphLocation); Does not have to be static
    public static int totalSitesCount; //Does not have to be static

    //Stores 2D array as 1D array for speed. This does impose a size limit of originCount * totalSiteCount <= 2.147 billion. If exceeded, revert to 2D array.
    public Graph(double[][] twoDimensionalGraphArray) {
        int originCount = twoDimensionalGraphArray.length;
        this.totalSitesCount = twoDimensionalGraphArray[0].length;
        this.graphArray = new double[originCount * totalSitesCount];
        for (int origin = 0; origin < originCount; origin++) {
            for (int site = 0; site < totalSitesCount; site++) {
                graphArray[origin * totalSitesCount + site] = twoDimensionalGraphArray[origin][site];
            }
        }
    }

    //Compute travel distance from origin to destination (site)
    public static double getEdgeLength(int origin, int destination) {
        return graphArray[origin * totalSitesCount + destination];
    }
}