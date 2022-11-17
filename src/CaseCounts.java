public class CaseCounts {
    public static double[] caseCountByOrigin; //Does not have to be static
    public static int originCount; //Does not have to be static

    //Stores 2D array as 1D array for speed.
    //This does impose a size limit of timepointCount * originCount <= 2.147 billion. If exceeded, revert to 2D array.
    public CaseCounts(double[][] caseCountByTimeAndOrigin) {
        int timepointCount = caseCountByTimeAndOrigin.length;
        this.originCount = caseCountByTimeAndOrigin[0].length;
        this.caseCountByOrigin = new double[timepointCount * originCount];
        for (int timepoint = 0; timepoint < timepointCount; timepoint++) {
            for (int origin = 0; origin < originCount; origin++) {
                caseCountByOrigin[timepoint * originCount + origin] = caseCountByTimeAndOrigin[timepoint][origin];
            }
        }
    }

    public static double getCaseCount(int timepoint, int origin) {
        return caseCountByOrigin[timepoint * originCount + origin];
    }
}