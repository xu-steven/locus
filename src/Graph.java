public class Graph {
    public static double[] graphArray;// = parseCSV(graphLocation);

    //Stores 2D array as 1D array for speed.
    //This does impose a size limit of originCount * totalSiteCount <= 2.147 billion. If exceeded, revert to 2D array.
    public Graph(double[][] twoDimensionalGraphArray) {
        int originCount = twoDimensionalGraphArray.length;
        int totalSiteCount = twoDimensionalGraphArray[0].length;
        this.graphArray = new double[originCount * totalSiteCount];
        for (int origin = 0; origin < originCount; origin++) {
            for (int site = 0; site < totalSiteCount; site++) {
                graphArray[origin * totalSiteCount + site] = twoDimensionalGraphArray[origin][site];
            }
        }
    }

    //Compute travel distance from origin to destination (site)
    public static double getEdgeLength(int origin, int destination, int totalSitesCount) {
        return graphArray[origin * totalSitesCount + destination];
    }
}