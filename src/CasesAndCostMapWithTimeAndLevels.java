public class CasesAndCostMapWithTimeAndLevels {
    private CasesAndCostMapWithTime[] casesAndCostMapWithTimeAndLevels;

    public CasesAndCostMapWithTimeAndLevels(CasesAndCostMapWithTimeAndLevels casesAndCostMapWithTimeAndLevels) {
        this.casesAndCostMapWithTimeAndLevels = casesAndCostMapWithTimeAndLevels.casesAndCostMapWithTimeAndLevels.clone();
    }

    public CasesAndCostMapWithTimeAndLevels(int centerLevels) {
        this.casesAndCostMapWithTimeAndLevels = new CasesAndCostMapWithTime[centerLevels];
    }

    public void updateLevel(int level, CasesAndCostMapWithTime casesAndCostMapWithTime) {
        casesAndCostMapWithTimeAndLevels[level] = casesAndCostMapWithTime;
    }

    public CasesAndCost[] getTimeAndLevelSpecificMap(int level, int timepoint) {
        return casesAndCostMapWithTimeAndLevels[level].getTimeSpecificMap(timepoint);
    }

    public double getCases(int level, int timepoint, int position) {
        return casesAndCostMapWithTimeAndLevels[level].getCases(timepoint, position);
    }

    public double getCost(int level, int timepoint, int position) {
        return casesAndCostMapWithTimeAndLevels[level].getCost(timepoint, position);
    }

    public CasesAndCostMapWithTime getLevel(int level) {
        return casesAndCostMapWithTimeAndLevels[level];
    }
}