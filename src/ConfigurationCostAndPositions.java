import java.util.List;

public record ConfigurationCostAndPositions(double cost, int[] minimumCostPositions) {
    public double getCost() {
        return cost;
    }

    public int[] getPositions() {
        return minimumCostPositions;
    }
}