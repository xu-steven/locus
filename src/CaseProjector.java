import java.util.Collections;
import java.util.Map;

public class CaseProjector {
    //Map year -> (age -> incidence rate)
    public final Map<Integer, Map<Integer, Double>> maleCaseIncidenceRate;
    public final Map<Integer, Map<Integer, Double>> femaleCaseIncidenceRate;

    public CaseProjector(String caseIncidenceRateLocation) {
        PopulationParameters.ParsedEventRates caseIncidenceRate = PopulationParameters.parseYearHeadingCSV(caseIncidenceRateLocation, 1.0);
        maleCaseIncidenceRate = caseIncidenceRate.getMaleRate();
        femaleCaseIncidenceRate = caseIncidenceRate.getFemaleRate();
    }

    public double projectCases(Population population) {
        int year = population.getYear();
        Map<Integer, Double> malePyramid = population.getMalePyramid();
        Map<Integer, Double> femalePyramid = population.getFemalePyramid();
        Map<Integer, Double> yearSpecificMaleIncidenceRate = maleCaseIncidenceRate.get(year);
        Map<Integer, Double> yearSpecificFemaleIncidenceRate = femaleCaseIncidenceRate.get(year);

        double projectedCases = 0;
        for (int age = 0; age < population.getOldestPyramidCohortAge(); age++) {
            double maleCases;
            if (yearSpecificMaleIncidenceRate.keySet().contains(age)) {
                maleCases = malePyramid.get(age) * yearSpecificMaleIncidenceRate.get(age);
            } else {
                maleCases = malePyramid.get(age) * yearSpecificMaleIncidenceRate.get(Collections.max(yearSpecificMaleIncidenceRate.keySet()));
            }
            double femaleCases;
            if (yearSpecificFemaleIncidenceRate.keySet().contains(age)) {
                femaleCases = femalePyramid.get(age) * yearSpecificFemaleIncidenceRate.get(age);
            } else {
                femaleCases = femalePyramid.get(age) * yearSpecificFemaleIncidenceRate.get(Collections.max(yearSpecificFemaleIncidenceRate.keySet()));
            }
            projectedCases = projectedCases + maleCases + femaleCases;
        }

        return projectedCases;
    }
}