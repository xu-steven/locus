import groovy.transform.Undefined;

import java.util.*;

public final class CostCalculator {
    //Calculates cost from hashmap centre -> (cases, minimum travel cost)
    @Deprecated
    public static double computeCost(Map<Integer, CasesAndCost> minimumCostMap, double minimumCases) {
        double totalCost = 0;
        for (CasesAndCost centerCasesCost : minimumCostMap.values()) {
            totalCost += levelSpecificCost(centerCasesCost.getCases(), centerCasesCost.getCost(), minimumCases);
        }
        return totalCost;
    }

    //Calculates cost from hashmap centre -> (cases, minimum travel cost)
    public static double computeCost(CasesAndCost[][] minimumCostMap, double minimumCases, double[] timepointWeights) {
        double totalCost = 0;
        for (int timepoint = 0; timepoint < minimumCostMap.length; timepoint++) {
            for (int position = 0; position < minimumCostMap[timepoint].length; position++) {
                totalCost += levelSpecificCost(minimumCostMap[timepoint][position].getCases(), minimumCostMap[timepoint][position].getCost(), minimumCases) * timepointWeights[timepoint];
            }
        }
        return totalCost;
    }

    //Calculates cost from hashmap centre -> (cases, minimum travel cost) for multiple levels
    public static double computeCost(CasesAndCost[][][] minimumCostMapByLevel, List<List<Integer>> sitesByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel, double[] timepointWeights) {
        double totalCost = 0;

        //Compute costs specific to each timepoint and level
        for (int level = 0; level < minimumCasesByLevel.length; level++) {
            totalCost += computeLevelBaseCost(minimumCostMapByLevel[level], minimumCasesByLevel[level], servicedProportionByLevel[level], timepointWeights);
        }

        //Adjust for volume seen at each center
        totalCost += centerVolumePenalty(minimumCostMapByLevel, sitesByLevel, timepointWeights);

        return totalCost;
    }

    //Compute fixed costs for each level
    public static double computeLevelBaseCost(CasesAndCost[][] minimumCostMap, double minimumCases, double servicedProportion, double[] timepointWeights) {
        if (minimumCostMap[0].length == 0) { //no sites
            return 100000000.0 * servicedProportion;
        }
        double totalCost = 0;
        double[] costByTimepoint = new double[minimumCostMap.length];
        for (int timepoint = 0; timepoint < minimumCostMap.length; timepoint++) {
            for (int position = 0; position < minimumCostMap[0].length; position++) {
                costByTimepoint[timepoint] += levelSpecificCost(minimumCostMap[timepoint][position].getCases(), minimumCostMap[timepoint][position].getCost(), minimumCases);
            }
            totalCost += costByTimepoint[timepoint] * servicedProportion * timepointWeights[timepoint];
        }
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

    //Takes into account all levels
    public static double centerVolumePenalty(CasesAndCost[][][] minimumCostMapByLevel, List<List<Integer>> sitesByLevel, double[] timepointWeights) {
        //Adjust cost by center accounting for all services
        Set<Integer> allSites = new HashSet<>();
        for (int level = 0; level < minimumCostMapByLevel.length; level++) {
            allSites.addAll(sitesByLevel.get(level));
        }

        double totalAdjustment = 0;
        double[] adjustmentByTimepoint = new double[timepointWeights.length];
        for (int timepoint = 0; timepoint < timepointWeights.length; timepoint++) {
            //Compute total number of cases of all levels by site
            for (Integer site : allSites) {
                double allLevelCases = 0;
                for (int level = 0; level < minimumCostMapByLevel.length; level++) {
                    if (minimumCostMapByLevel[level].length == 0) { //no sites
                        continue;
                    }
                    for (int position = 0; position < minimumCostMapByLevel[level][timepoint].length; position++) {
                        if (site == sitesByLevel.get(level).get(position)) {
                            allLevelCases += minimumCostMapByLevel[level][timepoint][position].getCases();
                        }
                    }
                }
                if (allLevelCases > 10000) {
                    adjustmentByTimepoint[timepoint] += 0; //should be negative with increasing cases}
                }
            }
            totalAdjustment += adjustmentByTimepoint[timepoint] * timepointWeights[timepoint];
        }

        return totalAdjustment;
    }
}