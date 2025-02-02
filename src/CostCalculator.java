import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

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
    public static double computeCost(CasesAndCostMap minimumCostMap, double minimumCases, double[] timepointWeights) {
        double totalCost = 0;
        for (int timepoint = 0; timepoint < minimumCostMap.getTimepointCount(); timepoint++) {
            for (int position = 0; position < minimumCostMap.getSiteCount(); position++) {
                totalCost += levelSpecificCost(minimumCostMap.getCases(timepoint, position), minimumCostMap.getCost(timepoint, position), minimumCases) * timepointWeights[timepoint];
            }
        }
        return totalCost;
    }

    //Calculates cost from hashmap centre -> (cases, minimum travel cost) for multiple levels
    public static double computeCost(CasesAndCostMap[] minimumCostMapByLevel, List<List<Integer>> sitesByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel, double[] timepointWeights) {
        double totalCost = 0;
        for (int t = 0; t < timepointWeights.length; t++) {
            totalCost += computeTimeSpecificCost(minimumCostMapByLevel, sitesByLevel, minimumCasesByLevel, servicedProportionByLevel, t) * timepointWeights[t];
        }
        return totalCost;
    }

    //Multithreaded compute cost when there are numerous time points
    public static double computeCost(CasesAndCostMap[] minimumCostMapByLevel, List<List<Integer>> sitesByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel, int timepointCount, double[] timepointWeights, ExecutorService executor) {
        if (timepointWeights.length == 1) {
            return computeTimeSpecificCost(minimumCostMapByLevel, sitesByLevel, minimumCasesByLevel, servicedProportionByLevel, 0);
        } else if (timepointWeights.length < 6) {
            double totalCost = 0;
            for (int t = 0; t < timepointWeights.length; t++) {
                totalCost += computeTimeSpecificCost(minimumCostMapByLevel, sitesByLevel, minimumCasesByLevel, servicedProportionByLevel, t) * timepointWeights[t];
            }
            return totalCost;
        } else {
            CountDownLatch latch = new CountDownLatch(timepointWeights.length);
            double[] costByTimepoint = new double[timepointWeights.length];
            for (int t = 0; t < timepointWeights.length; t++) {
                int finalT = t;
                executor.execute(() -> {
                    costByTimepoint[finalT] = computeTimeSpecificCost(minimumCostMapByLevel, sitesByLevel, minimumCasesByLevel, servicedProportionByLevel, finalT) * timepointWeights[finalT];
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new AssertionError("Unexpected interruption", e);
            }
            return ArrayOperations.sumDoubleArray(costByTimepoint);
        }
    }

    //Compute cost for one specific timepoint
    public static double computeTimeSpecificCost(CasesAndCostMap[] minimumCostMapByLevel, List<List<Integer>> sitesByLevel, double[] minimumCasesByLevel, double[] servicedProportionByLevel, int timepoint) {
        double totalCost = 0;

        //Compute costs specific to each timepoint and level
        for (int level = 0; level < minimumCasesByLevel.length; level++) {
            if (minimumCostMapByLevel[level] == null) {
                totalCost += 100000000.0 * servicedProportionByLevel[level];
            } else {
                totalCost += computeTimeAndLevelSpecificBaseCost(minimumCostMapByLevel[level], timepoint, minimumCasesByLevel[level], servicedProportionByLevel[level]);
            }
        }

        //Adjust for volume seen at each center
        totalCost += computeTimeSpecificVolumePenalty(minimumCostMapByLevel, sitesByLevel, timepoint);

        return totalCost;
    }

    //Compute fixed costs for each level
    public static double computeLevelSpecificBaseCost(CasesAndCostMap minimumCostMap, double minimumCases, double servicedProportion, double[] timepointWeights) {
        if (minimumCostMap == null) { //no sites
            return 100000000.0 * servicedProportion;
        } else {
            double totalCost = 0;
            for (int timepoint = 0; timepoint < minimumCostMap.getTimepointCount(); timepoint++) {
                totalCost += computeTimeAndLevelSpecificBaseCost(minimumCostMap, timepoint, minimumCases, servicedProportion) * timepointWeights[timepoint];
            }
            return totalCost;
        }
    }

    //Compute fixed costs for each level
    public static double computeTimeAndLevelSpecificBaseCost(CasesAndCostMap minimumCostMap, int timepoint, double minimumCases, double servicedProportion) {
        double totalCost = 0;
        for (int position = 0; position < minimumCostMap.getSiteCount(); position++) {
            totalCost += levelSpecificCost(minimumCostMap.getCases(timepoint, position), minimumCostMap.getCost(timepoint, position), minimumCases);
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
    public static double computeTimeSpecificVolumePenalty(CasesAndCostMap[] minimumCostMapByLevel, List<List<Integer>> sitesByLevel, int timepoint) {
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
                if (minimumCostMapByLevel[level] == null) {
                    continue; //no sites in level
                } else {
                    for (int position = 0; position < minimumCostMapByLevel[level].getSiteCount(); position++) {
                        //System.out.println("Sites part 2 " + sitesByLevel + " and map " + Arrays.toString(minimumCostMapByLevel.getLevel(level).getTimeSpecificMap(timepoint)) + " for level " + level);
                        if (site == sitesByLevel.get(level).get(position)) {
                            allLevelCases += minimumCostMapByLevel[level].getCases(timepoint, position);
                        }
                    }
                }
            }
            if (allLevelCases < 10000) {
                volumePenalty += 0; //should be non-negative
            }
        }
        return volumePenalty;
    }
}