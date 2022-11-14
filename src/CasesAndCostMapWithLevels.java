public class CasesAndCostMapWithLevels {
    private CasesAndCostMap[] casesAndCostMapWithTimeAndLevels;

    public CasesAndCostMapWithLevels(CasesAndCostMapWithLevels casesAndCostMapWithLevels) {
        this.casesAndCostMapWithTimeAndLevels = casesAndCostMapWithLevels.casesAndCostMapWithTimeAndLevels.clone();
    }

    public CasesAndCostMapWithLevels(int centerLevels) {
        this.casesAndCostMapWithTimeAndLevels = new CasesAndCostMap[centerLevels];
    }

    public void setLevel(int level, CasesAndCostMap casesAndCostMap) {
        casesAndCostMapWithTimeAndLevels[level] = casesAndCostMap;
    }

    public double getCases(int level, int timepoint, int position) {
        return casesAndCostMapWithTimeAndLevels[level].getCases(timepoint, position);
    }

    public double getCost(int level, int timepoint, int position) {
        return casesAndCostMapWithTimeAndLevels[level].getCost(timepoint, position);
    }

    public CasesAndCostMap getLevel(int level) {
        return casesAndCostMapWithTimeAndLevels[level];
    }
}