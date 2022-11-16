public class CaseCounts {
    public static double[] caseCountByOrigin;

    public CaseCounts(double[][] caseCountByTimeAndOrigin) {
        int timepointCount = caseCountByTimeAndOrigin.length;
        int originCount = caseCountByTimeAndOrigin[0].length;
        this.caseCountByOrigin = new double[timepointCount * originCount];
        for (int timepoint = 0; timepoint < timepointCount; timepoint++) {
            for (int origin = 0; origin < originCount; origin++) {
                caseCountByOrigin[timepoint * originCount + origin] = caseCountByTimeAndOrigin[timepoint][origin];
            }
        }
    }

    public static double getCaseCount(int timepoint, int origin, int originCount) {
        return caseCountByOrigin[timepoint * originCount + origin];
    }
}