import java.util.Collections;
import java.util.Map;

public class CaseProjector {
    //Map year -> (age -> incidence rate)
    public final Map<Integer, double[]> maleCaseIncidenceRate;
    public final Map<Integer, double[]> femaleCaseIncidenceRate;

    public CaseProjector(String caseIncidenceRateLocation) {
        PopulationParameters.ParsedEventRates caseIncidenceRate = PopulationParameters.parseYearHeadingCSV(caseIncidenceRateLocation, 1.0);
        maleCaseIncidenceRate = caseIncidenceRate.getMaleRate();
        femaleCaseIncidenceRate = caseIncidenceRate.getFemaleRate();
    }

    public double projectCases(Population population) {
        int year = population.getYear();
        double[] malePyramid = population.getMalePyramid();
        double[] femalePyramid = population.getFemalePyramid();
        double[] yearSpecificMaleIncidenceRate = maleCaseIncidenceRate.get(year);
        double[] yearSpecificFemaleIncidenceRate = femaleCaseIncidenceRate.get(year);

        double projectedCases = 0;
        for (int age = 0; age <= population.getOldestPyramidCohortAge(); age++) {
            projectedCases += malePyramid[age] * getAgeCappedEventRate(yearSpecificMaleIncidenceRate, age);
            projectedCases += femalePyramid[age] * getAgeCappedEventRate(yearSpecificFemaleIncidenceRate, age);
        }
        return projectedCases;
    }

    private double getAgeCappedEventRate(double[] ageToEvents, int age) {
        return ageToEvents[Math.min(age, ageToEvents.length -1)];
    }
}