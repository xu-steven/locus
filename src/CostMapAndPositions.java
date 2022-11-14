public record CostMapAndPositions(CasesAndCostMap minimumCostMap, int[] minimumCostPositions) {
    public CasesAndCostMap getCasesAndCostMap() {
        return minimumCostMap;
    }
    public int[] getPositions() {
        return minimumCostPositions;
    }
}