import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CostCalculator {
    //Calculates cost from hashmap centre -> (cases, minimum travel cost)
    public static double computeCost(Map<Integer, CasesAndCost> minimumCostMap, double minimumCases) {
        double totalCost = 0;
        for (CasesAndCost centerCasesCost : minimumCostMap.values()) {
            totalCost += levelSpecificCost(centerCasesCost.getCases(), centerCasesCost.getCost(), minimumCases);
        }
        return totalCost;
    }

    @Deprecated
    //Calculates cost from hashmap centre -> (cases, minimum travel cost)
    public static double computeCost(CasesAndCost[] minimumCostMap, double minimumCases) {
        double totalCost = 0;
        for (int i = 0; i < minimumCostMap.length; i++) {
            totalCost += levelSpecificCost(minimumCostMap[i].getCases(), minimumCostMap[i].getCost(), minimumCases);
        }
        return totalCost;
    }

    //Calculates cost from hashmap centre -> (cases, minimum travel cost)
    public static double computeCost(CasesAndCost[][] minimumCostMapByLevel, List<List<Integer>> sitesByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel) {
        double totalCost = 0;

        //Compute costs specific to each level
        for (int level = 0; level < minimumCasesByLevel.length; level++) {
            if (minimumCostMapByLevel[level].length == 0) { //no sites
                totalCost += 100000000.0 * servicedProportionByLevel[level];
            }
            totalCost += computeLevelBaseCost(minimumCostMapByLevel[level], minimumCasesByLevel[level], servicedProportionByLevel[level]);
        }

        //Adjust for volume seen at each center
        totalCost += lowCenterVolumeCost(minimumCostMapByLevel, sitesByLevel);

        return totalCost;
    }

    //Cost penalty function depending on amount of cases for the particular service seen at cancer center. Examples include additive constant (extra cost to administer each center) or multiplicative piecewise to prefer bigger centers.
    public static double levelSpecificCost(double cases, double cost, double minimumCases) {
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

    //Compute fixed costs for each level
    public static double computeLevelBaseCost(CasesAndCost[] minimumCostMap, double minimumCases, double servicedProportion) {
        double totalCost = 0;
        for (int i = 0; i < minimumCostMap.length; i++) {
            totalCost += levelSpecificCost(minimumCostMap[i].getCases(), minimumCostMap[i].getCost(), minimumCases);
        }
        return totalCost * servicedProportion;
    }

    //Takes into account all levels
    public static double lowCenterVolumeCost(CasesAndCost[][] minimumCostMapByLevel, List<List<Integer>> sitesByLevel) {
        //Adjust cost by center accounting for all services
        Set<Integer> allSites = new HashSet<>();
        for (int level = 0; level < minimumCostMapByLevel.length; level++) {
            allSites.addAll(sitesByLevel.get(level));
        }

        //Compute total number of cases of all levels by site
        double totalAdjustment = 0;
        for (Integer site : allSites) {
            double allLevelCases = 0;
            for (int level = 0; level < minimumCostMapByLevel.length; level++) {
                for (int position = 0; position < minimumCostMapByLevel[level].length; position++) {
                    if (site == sitesByLevel.get(level).get(position)) {
                        allLevelCases += minimumCostMapByLevel[level][position].getCases();
                    }
                }
            }
            if (allLevelCases > 10000) {
                totalAdjustment += 0; //should be negative with increasing cases
            }
        }

        return totalAdjustment;
    }
}