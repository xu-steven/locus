import java.util.List;
import java.util.Map;

public final class CostCalculator {
    //Calculates cost from hashmap centre -> (cases, minimum travel cost)
    public static Double computeCost(Map<Integer, List<Double>> minimumCostMap, double minimumCases) {
        double totalCost = 0;
        for (List<Double> centerCasesCost : minimumCostMap.values()) {
            double cases = centerCasesCost.get(0);
            double cost = centerCasesCost.get(1);
            totalCost += caseAdjustedCost(cases, cost, minimumCases);
        }
        return totalCost;
    }

    //Cost penalty function depending on amount of cases seen at cancer center. Examples include additive constant (extra cost to administer each center) or multiplicative piecewise to prefer bigger centers.
    public static double caseAdjustedCost(double cases, double cost, double minimumCases) {
        if (cases == 0) {
            //System.out.println("A center had no cases in its catchment.");
            return 0;
        } else if (minimumCases == 0) {
            return cost;
        } else if (cases <= minimumCases) {
            //System.out.println("A center had less than " + minimumCases + " cases in its catchment.");
            return cost + 100 * (minimumCases - cases);
        } else {
            return cost;
        }
    }
}
