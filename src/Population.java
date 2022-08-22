import java.util.*;

public class Population {
    //Current year
    public static int year;

    //Adjusted map age -> population with 1 year age increments, with final year including all >= that age
    public static Map<Integer, Double> malePyramid;
    public static Map<Integer, Double> femalePyramid;

    static int oldestPyramidCohortAge;

    public Population(int year, List<String> ageAndSexGroups, double[] populationByAgeAndSexGroup,
                      Map<Integer, Map<Integer, Double>> maleMortality, Map<Integer, Map<Integer, Double>> femaleMortality,
                      Map<Integer, Map<Integer, Double>> maleMigration, Map<Integer, Map<Integer, Double>> femaleMigration, int migrationFormat,
                      Map<Integer, Double> maleInfantSeparationFactor, Map<Integer, Double> femaleInfantSeparationFactor) {
        this.year = year;

        //Create raw pyramids containing same information as in demographics file
        Map<List<Integer>, Double> maleRawPyramid = new HashMap<>();
        Map<List<Integer>, Double> femaleRawPyramid = new HashMap<>();
        oldestPyramidCohortAge = 0;
        for (int i = 0; i < ageAndSexGroups.size(); i++) {
            List<Integer> sexAgeInfo = PopulationParameters.parseSexAgeGroup(ageAndSexGroups.get(i));
            if (sexAgeInfo.get(2) > oldestPyramidCohortAge) oldestPyramidCohortAge = sexAgeInfo.get(2);
            if (sexAgeInfo.get(0) == 0) {
                maleRawPyramid.put(Arrays.asList(sexAgeInfo.get(1), sexAgeInfo.get(2)), populationByAgeAndSexGroup[i]);
            } else {
                femaleRawPyramid.put(Arrays.asList(sexAgeInfo.get(1), sexAgeInfo.get(2)), populationByAgeAndSexGroup[i]);
            }
        }
        //Convert to 1 year increments of age with final year including all >= that age
        this.malePyramid = adjustRawPyramid(maleRawPyramid, maleMortality.get(year), maleMigration.get(year), migrationFormat, maleInfantSeparationFactor.get(year));
        this.femalePyramid = adjustRawPyramid(femaleRawPyramid, femaleMortality.get(year), femaleMigration.get(year), migrationFormat, femaleInfantSeparationFactor.get(year));
    }

    public Map<Integer, Double> adjustRawPyramid(Map<List<Integer>, Double> rawPyramid, Map<Integer, Double> mortality, Map<Integer, Double> migration, int migrationFormat, Double infantSeparationFactor) {
        Map<Integer, Double> adjustedPyramid = new HashMap<>();
        for (List<Integer> ageBounds : rawPyramid.keySet()) {
            if (ageBounds.get(0) == 0) {
                adjustedPyramid.putAll(refineAgeZeroGroups(ageBounds, rawPyramid.get(ageBounds), mortality, migration, migrationFormat, infantSeparationFactor));
            } else {
                adjustedPyramid.putAll(refineAgeGroups(ageBounds, rawPyramid.get(ageBounds), mortality, migration, migrationFormat));
            }
        }
        return adjustedPyramid;
    }

    //If population pyramid is not given in 1 year age groups, then estimates one year age groups by using mortality. Assumes steady state population.
    public Map<Integer, Double> refineAgeGroups(List<Integer> ageBounds, Double populationSize, Map<Integer, Double> mortality, Map<Integer, Double> migration, int migrationFormat) {
        Map<Integer, Double> refinedPyramid = new HashMap<>();
        if (migrationFormat == 0) { //total migration
            //Coefficient of multiplicative term in front of initial population when all summed together
            List<Double> relativePopulationByAge = new ArrayList<>();
            Double nextRelativePopulation = 1.0;
            relativePopulationByAge.add(nextRelativePopulation);
            Double totalRelativePopulation = 1.0;
            for (int age = ageBounds.get(0); age < ageBounds.get(1); age++) {
                nextRelativePopulation = nextRelativePopulation * (1 - 0.5 * mortality.get(age)) / (1 + 0.5 * mortality.get(age + 1));
                relativePopulationByAge.add(nextRelativePopulation);
                totalRelativePopulation += nextRelativePopulation;
            }

            //Additive term of populations summed together (represents migrants)
            List<Double> migrantsByAge = new ArrayList<>();
            Double nextMigrants = 0.0;
            migrantsByAge.add(nextRelativePopulation);
            Double totalMigrants = 0.0;
            for (int age = ageBounds.get(0); age < ageBounds.get(1); age++) {
                nextMigrants = (nextMigrants * (1 - 0.5 * mortality.get(age)) + 0.5 * migration.get(age) + 0.5 * migration.get(age + 1)) / (1 + 0.5 * mortality.get(age + 1));
                migrantsByAge.add(nextMigrants);
                totalMigrants += nextMigrants;
            }
            Double firstCohortPopulation = (populationSize - totalMigrants) / totalRelativePopulation;

            //Create a refined pyramid
            refinedPyramid.put(ageBounds.get(0), firstCohortPopulation);
            for (int age = ageBounds.get(0) + 1; age <= ageBounds.get(1); age++) {
                refinedPyramid.put(age, firstCohortPopulation * relativePopulationByAge.get(age - ageBounds.get(0)) + migrantsByAge.get(age - ageBounds.get(0)));
            }
        } else { //migration rate
            //Compute populations relative to 1.0 for the first cohort
            List<Double> relativePopulationByAge = new ArrayList<>();
            Double nextRelativePopulation = 1.0;
            relativePopulationByAge.add(nextRelativePopulation);
            Double totalRelativePopulation = 1.0;
            for (int age = ageBounds.get(0); age < ageBounds.get(1); age++) {
                nextRelativePopulation = nextRelativePopulation * (1 - 0.5 * mortality.get(age) + 0.5 * migration.get(age))
                        / (1 + 0.5 * mortality.get(age + 1) - 0.5 * migration.get(age + 1));
                relativePopulationByAge.add(nextRelativePopulation);
                totalRelativePopulation += nextRelativePopulation;
            }

            //Create a refined pyramid
            for (int age = ageBounds.get(0); age <= ageBounds.get(1); age++) {
                refinedPyramid.put(age, populationSize * relativePopulationByAge.get(age - ageBounds.get(0)) / totalRelativePopulation);
            }
        }
        return refinedPyramid;
    }

    //If population pyramid is not given in 1 year age groups, then estimates one year age groups by using mortality. Assumes steady state population.
    public Map<Integer, Double> refineAgeZeroGroups(List<Integer> ageBounds, Double populationSize, Map<Integer, Double> mortality, Map<Integer, Double> migration, int migrationFormat, Double infantSeparationFactor) {
        Map<Integer, Double> refinedPyramid = new HashMap<>();
        if (migrationFormat == 0) { //total migration
            //Coefficient of multiplicative term in front of initial population when all summed together
            List<Double> relativePopulationByAge = new ArrayList<>();
            Double nextRelativePopulation = 1.0;
            relativePopulationByAge.add(nextRelativePopulation);
            Double totalRelativePopulation = 1.0;
            //Compute next relative population for age 0 (i.e. relative age 1 population)
            nextRelativePopulation = nextRelativePopulation * (1 - infantSeparationFactor * mortality.get(0)) / (1 + 0.5 * mortality.get(1));
            relativePopulationByAge.add(nextRelativePopulation);
            totalRelativePopulation += nextRelativePopulation;
            //Compute next relative population beginning age 1
            for (int age = 1; age < ageBounds.get(1); age++) {
                nextRelativePopulation = nextRelativePopulation * (1 - 0.5 * mortality.get(age)) / (1 + 0.5 * mortality.get(age + 1));
                relativePopulationByAge.add(nextRelativePopulation);
                totalRelativePopulation += nextRelativePopulation;
            }

            //Additive term of populations summed together (represents migrants)
            List<Double> migrantsByAge = new ArrayList<>();
            Double nextMigrants = 0.0;
            migrantsByAge.add(nextRelativePopulation);
            Double totalMigrants = 0.0;
            //Compute next relative population for age 0 (i.e. relative age 1 population)
            nextMigrants = (nextMigrants * (1 - infantSeparationFactor * mortality.get(0)) + 0.5 * migration.get(0) + 0.5 * migration.get(1)) / (1 + 0.5 * mortality.get(1));
            migrantsByAge.add(nextMigrants);
            totalMigrants += nextMigrants;
            //Compute next relative population beginning age 1
            for (int age = 1; age < ageBounds.get(1); age++) {
                nextMigrants = (nextMigrants * (1 - 0.5 * mortality.get(age)) + 0.5 * migration.get(age) + 0.5 * migration.get(age + 1)) / (1 + 0.5 * mortality.get(age + 1));
                migrantsByAge.add(nextMigrants);
                totalMigrants += nextMigrants;
            }
            Double firstCohortPopulation = (populationSize - totalMigrants) / totalRelativePopulation;

            //Create a refined pyramid
            refinedPyramid.put(ageBounds.get(0), firstCohortPopulation);
            for (int age = ageBounds.get(0) + 1; age <= ageBounds.get(1); age++) {
                refinedPyramid.put(age, firstCohortPopulation * relativePopulationByAge.get(age - ageBounds.get(0)) + migrantsByAge.get(age - ageBounds.get(0)));
            }
        } else { //migration rate
            //Compute populations relative to 1.0 for the first cohort
            List<Double> relativePopulationByAge = new ArrayList<>();
            Double nextRelativePopulation = 1.0;
            relativePopulationByAge.add(nextRelativePopulation);
            Double totalRelativePopulation = 1.0;
            //Compute next relative population for age 0 (relative age 1 population)
            nextRelativePopulation = nextRelativePopulation * (1 - infantSeparationFactor * mortality.get(0) + 0.5 * migration.get(0))
                    / (1 + 0.5 * mortality.get(1) - 0.5 * migration.get(1));
            relativePopulationByAge.add(nextRelativePopulation);
            totalRelativePopulation += nextRelativePopulation;
            //Compute next relative population beginning at age 1
            for (int age = 1; age < ageBounds.get(1); age++) {
                nextRelativePopulation = nextRelativePopulation * (1 - 0.5 * mortality.get(age) + 0.5 * migration.get(age))
                        / (1 + 0.5 * mortality.get(age + 1) - 0.5 * migration.get(age + 1));
                relativePopulationByAge.add(nextRelativePopulation);
                totalRelativePopulation += nextRelativePopulation;
            }

            //Create a refined pyramid
            for (int age = ageBounds.get(0); age <= ageBounds.get(1); age++) {
                refinedPyramid.put(age, populationSize * relativePopulationByAge.get(age - ageBounds.get(0)) / totalRelativePopulation);
            }
        }
        return refinedPyramid;
    }

    //Collapses adjusted pyramid to same age cohorts of the original pyramid
    public Map<List<Integer>, Double> collapsePyramid(Map<Integer, Double> pyramid, String referenceLocation) {
        //Build reference age groups
        List<String> referenceAgeAndSexGroups = FileUtils.getCSVHeadings(referenceLocation);
        Set<List<Integer>> allReferenceAgeBounds = new HashSet<>();
        for (int i = 0; i < referenceAgeAndSexGroups.size(); i++) {
            List<Integer> sexAgeInfo = PopulationParameters.parseSexAgeGroup(referenceAgeAndSexGroups.get(i));
            allReferenceAgeBounds.add(Arrays.asList(sexAgeInfo.get(1), sexAgeInfo.get(2)));
        }
        //Compute new pyramid in same format as reference
        Map<List<Integer>, Double> remadePyramid = new HashMap<>();
        for (List<Integer> ageBounds : allReferenceAgeBounds) {
            Double cohortPopulation = 0.0;
            for (int age = ageBounds.get(0); age <= ageBounds.get(1); age++) {
                cohortPopulation += pyramid.get(age);
            }
            remadePyramid.put(ageBounds, cohortPopulation);
        }
        return remadePyramid;
    }


    public Map<Integer, Double> getMalePyramid() {
        return malePyramid;
    }

    public Map<Integer, Double> getFemalePyramid() {
        return femalePyramid;
    }

    public static int getOldestPyramidCohortAge() {
        return oldestPyramidCohortAge;
    }
}