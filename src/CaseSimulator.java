import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.floor;

public class CaseSimulator {
    //Origin count and population by origin
    int originCount;
    Population[] populationByOrigin;

    //Incidence rates
    double[] maleCaseIncidenceRate;
    double[] femaleCaseIncidenceRate;

    public CaseSimulator(String demographicsLocation, String caseIncidenceRateLocation) {
        //Read demographics file
        List<String> ageAndSexGroups = FileUtils.getCSVHeadings(demographicsLocation);
        List<String> origins = FileUtils.getCSVFirstColumn(demographicsLocation);
        originCount = origins.size();
        double[][] populationByOriginAndDemographics = FileUtils.getInnerDoubleArrayFromCSV(demographicsLocation, FileUtils.getOriginCount(demographicsLocation), FileUtils.getSitesCount(demographicsLocation));
        int oldestPyramidCohortAge = Population.determineOldestPyramidCohortAge(ageAndSexGroups);

        //No mortality, migration
        double[] zeroArray = new double[oldestPyramidCohortAge + 1];
        Map<Integer, double[]> zeroMap = new HashMap<>();
        zeroMap.put(0, zeroArray);

        //No ISF
        Map<Integer, Double> zeroISF = new HashMap<>();
        zeroISF.put(0, 0.0);

        //Demographics
        populationByOrigin = new Population[origins.size()];
        for (int origin = 0; origin < originCount; origin++) {
            double[] populationByDemographics = populationByOriginAndDemographics[origin];
            Population population = new Population(0, ageAndSexGroups, populationByDemographics, zeroMap, zeroMap, zeroMap, zeroMap, 0, zeroISF, zeroISF);
            populationByOrigin[origin] = population;
        }

        //Read incidence file
        PopulationParameters.ParsedEventRates caseIncidenceRate = PopulationParameters.parseYearHeadingCSV(caseIncidenceRateLocation, 100000, oldestPyramidCohortAge);
        Map<Integer, double[]> maleCaseIncidenceRateByYear = caseIncidenceRate.getMaleRate();
        Map<Integer, double[]> femaleCaseIncidenceRateByYear = caseIncidenceRate.getFemaleRate();
        Integer year = maleCaseIncidenceRateByYear.keySet().iterator().next();

        //Incidence
        maleCaseIncidenceRate = maleCaseIncidenceRateByYear.get(year);
        femaleCaseIncidenceRate = femaleCaseIncidenceRateByYear.get(year);
    }

    //Simulate case counts by origin
    public CaseCounts simulateCases() throws IllegalArgumentException {
        //Expected cases, assumes same incidence for each origin (can be modified)
        double[] simulatedCaseCountByOrigin = new double[originCount];
        for (int origin = 0; origin < originCount; origin++) {
            double[] malePyramid = populationByOrigin[origin].getMalePyramid();
            double[] femalePyramid = populationByOrigin[origin].getFemalePyramid();
            double projectedCases = 0;
            for (int age = 0; age <= populationByOrigin[origin].getOldestPyramidCohortAge(); age++) {
                projectedCases += getBinomial(randomlyRound(malePyramid[age]), maleCaseIncidenceRate[age]);
                projectedCases += getBinomial(randomlyRound(femalePyramid[age]), femaleCaseIncidenceRate[age]);
            }
            simulatedCaseCountByOrigin[origin] = projectedCases;
        }

        //Generate a CaseCount object. Only one timepoint used.
        double[][] singleTimepointCaseCountByTimeAndOrigin = new double[1][originCount];
        singleTimepointCaseCountByTimeAndOrigin[0] = simulatedCaseCountByOrigin;
        return new CaseCounts(singleTimepointCaseCountByTimeAndOrigin);
    }

    //Expected case counts by origin, no randomness
    public CaseCounts expectedCases() {
        double[] expectedCaseCountByOrigin = new double[originCount];
        for (int origin = 0; origin < originCount; origin++) {
            double expectedCases = 0;
            for (int age = 0; age <= populationByOrigin[origin].getOldestPyramidCohortAge(); age++) {
                double[] malePyramid = populationByOrigin[origin].getMalePyramid();
                double[] femalePyramid = populationByOrigin[origin].getFemalePyramid();
                expectedCases += malePyramid[age] * maleCaseIncidenceRate[age];
                expectedCases += femalePyramid[age] * femaleCaseIncidenceRate[age];
            }
            expectedCaseCountByOrigin[origin] = expectedCases;
        }

        //Generate a CaseCount object. Only one timepoint used.
        double[][] singleTimepointCaseCountByTimeAndOrigin = new double[1][originCount];
        singleTimepointCaseCountByTimeAndOrigin[0] = expectedCaseCountByOrigin;
        return new CaseCounts(singleTimepointCaseCountByTimeAndOrigin);
    }

    public static int randomlyRound(double d) {
        int randomlyRounded = (int) floor(d);
        double residual = d - floor(d);
        if (residual > Math.random()) {
            randomlyRounded += 1;
        }
        return randomlyRounded;
    }


    public static int getBinomial(int n, double p) throws IllegalArgumentException {
        if (p<0 || p>1) {
            throw new IllegalArgumentException("Binomial probability " + p + " was not between 0 and 1.");
        }
        double log_q = Math.log(1.0 - p);
        int x = 0;
        double sum = 0;
        for(;;) {
            sum += Math.log(Math.random()) / (n - x);
            if(sum < log_q) {
                return x;
            }
            x++;
        }
    }
}