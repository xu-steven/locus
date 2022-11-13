import java.util.Arrays;

public class CasesAndCostMapWithTime extends CasesAndCostMap {
    private CasesAndCost[][] casesAndCostMapWithTime;
    private int timepointCount;
    private int siteCount;

    //Constructs a map from (timepoint, position) -> number of expected cases and total travel cost
    public CasesAndCostMapWithTime(int timepointCount, int siteCount) {
        CasesAndCost initialCasesCost = new CasesAndCost(0.0, 0.0);
        this.casesAndCostMapWithTime = new CasesAndCost[timepointCount][siteCount];
        for (int timepoint = 0; timepoint < timepointCount; timepoint++) {
            for (int position = 0; position < siteCount; ++position) {
                casesAndCostMapWithTime[timepoint][position] = initialCasesCost;
            }
        }
        this.timepointCount = timepointCount;
        this.siteCount = siteCount;
    }

    //Combine partitioned maps into a single map
    public CasesAndCostMapWithTime(CasesAndCostMapWithTime[] partitionedMap, int timepointCount, int siteCount, int taskCount) {
        this.casesAndCostMapWithTime = new CasesAndCost[timepointCount][siteCount]; //Map from centre to (cases, minimum travel cost)
        for (int timepoint = 0; timepoint < timepointCount; timepoint++) {
            for (int position = 0; position < siteCount; position++) {
                double positionCaseCount = partitionedMap[0].getCases(timepoint, position);
                double positionCost = partitionedMap[0].getCost(timepoint, position);
                for (int i = 1; i < taskCount; i++) {
                    positionCaseCount += partitionedMap[i].getCases(timepoint, position);
                    positionCost += partitionedMap[i].getCost(timepoint, position);
                }
                setCasesAndCost(timepoint, position, new CasesAndCost(positionCaseCount, positionCost));
            }
        }
    }

    //Constructs a map from (timepoint, position) -> number of expected cases and total travel cost
    public CasesAndCostMapWithTime() {
        this.casesAndCostMapWithTime = new CasesAndCost[timepointCount][0];
    }

    public void updateCasesAndCost(int minimumCostPosition, double minimumTravelCost, int origin, SearchSpace searchParameters) {
        for (int timepoint = 0; timepoint < timepointCount; timepoint++) {
            double currentCaseCount = searchParameters.getCaseCount(timepoint, origin);
            double centerCaseCount = getCases(timepoint, minimumCostPosition) + currentCaseCount; //Add new case count to total case count at center
            double centerCost = getCost(timepoint, minimumCostPosition) + (minimumTravelCost * currentCaseCount); //Add new travel cost multiplied by case count to total travel cost at center
            CasesAndCost minimumCasesCost = new CasesAndCost(centerCaseCount, centerCost);
            setCasesAndCost(timepoint, minimumCostPosition, minimumCasesCost);
        }
    }

    public double getCases(int timepoint, int position) {
        return casesAndCostMapWithTime[timepoint][position].getCases();
    }

    public double getCost(int timepoint, int position) {
        return casesAndCostMapWithTime[timepoint][position].getCost();
    }

    public void setCasesAndCost(int timepoint, int position, CasesAndCost casesAndCost) {
        casesAndCostMapWithTime[timepoint][position] = casesAndCost;
    }

    public int getTimepointCount() {
        return timepointCount;
    }

    public int getSiteCount() {
        return siteCount;
    }

    public CasesAndCost[] getTimeSpecificMap(int timepoint) {
        return casesAndCostMapWithTime[timepoint];
    }
}