import java.util.List;

public record ConfigurationCostAndPositions(double cost, List<Integer> minimumCostPositions) {
    public double getCost() {
        return cost;
    }

    public List<Integer> getPositions() {
        return minimumCostPositions;
    }
}