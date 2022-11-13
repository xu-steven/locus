public record CostMapAndPositions(CasesAndCostMapWithTime minimumCostMap, int[] minimumCostPositions) {
    public CasesAndCostMapWithTime getCasesAndCostMap() {
        return minimumCostMap;
    }
    public int[] getPositions() {
        return minimumCostPositions;
    }
}