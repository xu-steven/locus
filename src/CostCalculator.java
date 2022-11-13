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

    //Calculates cost from hashmap centre -> (cases, minimum travel cost). No levels.
    public static double computeCost(CasesAndCostMapWithTime minimumCostMap, double minimumCases, double[] timepointWeights) {
        double totalCost = 0;
        for (int timepoint = 0; timepoint < minimumCostMap.getTimepointCount(); timepoint++) {
            for (int position = 0; position < minimumCostMap.getSiteCount(); position++) {
                totalCost += levelSpecificCost(minimumCostMap.getCases(timepoint, position), minimumCostMap.getCost(timepoint, position), minimumCases) * timepointWeights[timepoint];
            }
        }
        return totalCost;
    }

    //Calculates cost from hashmap centre -> (cases, minimum travel cost) for multiple levels
    public static double computeCost(CasesAndCostMapWithTimeAndLevels minimumCostMapByLevel, List<List<Integer>> sitesByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel, double[] timepointWeights) {
        double totalCost = 0;
        for (int t = 0; t < timepointWeights.length; t++) {
            totalCost += computeTimeSpecificCost(minimumCostMapByLevel, sitesByLevel, minimumCasesByLevel, servicedProportionByLevel, t) * timepointWeights[t];
        }
        return totalCost;
    }

    //Compute cost for one specific timepoint
    public static double computeTimeSpecificCost(CasesAndCostMapWithTimeAndLevels minimumCostMapByLevel, List<List<Integer>> sitesByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel, int timepoint) {
        double totalCost = 0;

        //Compute costs specific to each timepoint and level
        for (int level = 0; level < minimumCasesByLevel.length; level++) {
            if (minimumCostMapByLevel.getLevel(level) == null) {
                totalCost += 100000000.0 * servicedProportionByLevel[level];
            } else {
                totalCost += computeTimeAndLevelSpecificBaseCost(minimumCostMapByLevel.getTimeAndLevelSpecificMap(level, timepoint), minimumCasesByLevel[level], servicedProportionByLevel[level]);
            }
        }

        //Adjust for volume seen at each center
        totalCost += computeTimeSpecificVolumePenalty(minimumCostMapByLevel, sitesByLevel, timepoint);

        return totalCost;
    }

    //Compute fixed costs for each level
    public static double computeLevelSpecificBaseCost(CasesAndCostMapWithTime minimumCostMap, double minimumCases, double servicedProportion, double[] timepointWeights) {
        if (minimumCostMap == null) { //no sites
            return 100000000.0 * servicedProportion;
        } else {
            double totalCost = 0;
            for (int timepoint = 0; timepoint < minimumCostMap.getTimepointCount(); timepoint++) {
                totalCost += computeTimeAndLevelSpecificBaseCost(minimumCostMap.getTimeSpecificMap(timepoint), minimumCases, servicedProportion) * timepointWeights[timepoint];
            }
            return totalCost;
        }
    }

    //Compute fixed costs for each level
    public static double computeTimeAndLevelSpecificBaseCost(CasesAndCost[] timeAndLevelSpecificMinimumCostMap, double minimumCases, double servicedProportion) {
        double totalCost = 0;
        for (int position = 0; position < timeAndLevelSpecificMinimumCostMap.length; position++) {
            totalCost += levelSpecificCost(timeAndLevelSpecificMinimumCostMap[position].getCases(), timeAndLevelSpecificMinimumCostMap[position].getCost(), minimumCases);
        }
        return totalCost * servicedProportion;
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
    public static double computeTimeSpecificVolumePenalty(CasesAndCostMapWithTimeAndLevels minimumCostMapByLevel, List<List<Integer>> sitesByLevel, int timepoint) {
        //Adjust cost by center accounting for all services
        Set<Integer> allSites = new HashSet<>();
        for (int level = 0; level < sitesByLevel.size(); level++) {
            allSites.addAll(sitesByLevel.get(level));
        }

        double volumePenalty = 0;
        //Compute total number of cases of all levels by site
        for (Integer site : allSites) {
            double allLevelCases = 0;
            for (int level = 0; level < sitesByLevel.size(); level++) {
                if (minimumCostMapByLevel.getLevel(level) == null) {
                    continue; //no sites in level
                } else {
                    for (int position = 0; position < minimumCostMapByLevel.getTimeAndLevelSpecificMap(level, timepoint).length; position++) {
                        //System.out.println("Sites part 2 " + sitesByLevel + " and map " + Arrays.toString(minimumCostMapByLevel.getLevel(level).getTimeSpecificMap(timepoint)) + " for level " + level);
                        if (site == sitesByLevel.get(level).get(position)) {
                            allLevelCases += minimumCostMapByLevel.getCases(level, timepoint, position);
                        }
                    }
                }
            }
            if (allLevelCases > 10000) {
                volumePenalty += 0; //should be non-negative
            }
        }
        return volumePenalty;
    }
}