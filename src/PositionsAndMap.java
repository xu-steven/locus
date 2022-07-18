import java.util.List;
import java.util.Map;

public record PositionsAndMap(List<Integer> partitionMinimumCostPositionByOrigin, Map<Integer, CasesAndCost> partitionMinimumCostMap) {
    public List<Integer> getPositions() {
        return partitionMinimumCostPositionByOrigin;
    }
    public Map<Integer, CasesAndCost> getMap() {
        return partitionMinimumCostMap;
    }
}