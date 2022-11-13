public record CostMapAndPositions(CasesAndCost[][] minimumCostMap, int[] minimumCostPositions) {
    public CasesAndCost[][] getMinimumCostMap() {
        return minimumCostMap;
    }
    public int[] getPositions() {
        return minimumCostPositions;
    }
}