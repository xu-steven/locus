import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class IterativePopulationProjector extends PopulationProjector {
    //Number of subgroups per age less than max age
    static int ageSubgroups;

    public IterativePopulationProjector(String mortalityLocation, String infantMortalityLocation, String fertilityLocation, String migrationLocation, String migrationFormat, int oldestPyramidCohortAge, int ageSubgroups) {
        super(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, migrationFormat, oldestPyramidCohortAge, 1, 1);
        this.ageSubgroups = ageSubgroups;
    }

    public static void main(String[] args) {
        //Demographics file
        String demographicsLocation = "M:\\Optimization Project\\demographic projections\\test_alberta2016_demographics.csv";
        List<String> ageAndSexGroups = FileUtils.getCSVHeadings(demographicsLocation);
        double[] populationByAgeAndSexGroup = FileUtils.getInnerDoubleArrayFromCSV(demographicsLocation, FileUtils.getOriginCount(demographicsLocation), FileUtils.getSitesCount(demographicsLocation))[0];

        //File locations
        String mortalityLocation = "M:\\Optimization Project\\demographic projections\\alberta_mortality.csv";
        String infantMortalityLocation = "M:\\Optimization Project\\demographic projections\\test_alberta_infant_mortality.csv";
        String fertilityLocation = "M:\\Optimization Project\\demographic projections\\alberta_fertility.csv";
        String migrationLocation = "M:\\Optimization Project\\demographic projections\\alberta_migration.csv";
        IterativePopulationProjector projector = new IterativePopulationProjector(mortalityLocation, infantMortalityLocation, fertilityLocation, migrationLocation, "Total migrants",
                Population.determineOldestPyramidCohortAge(ageAndSexGroups), 5);

        //In final program, this will be input into projector
        Population initialPopulation = new Population(2000, ageAndSexGroups, populationByAgeAndSexGroup,
                projector.getProjectionParameters().getMaleMortality(), projector.getProjectionParameters().getFemaleMortality(),
                projector.getProjectionParameters().getMaleMigration(), projector.getProjectionParameters().getFemaleMigration(), projector.getProjectionParameters().getMigrationFormat(),
                projector.getProjectionParameters().getMaleInfantSeparationFactor(), projector.getProjectionParameters().getFemaleInfantSeparationFactor());
        System.out.println("Male pyramid " + initialPopulation.getMalePyramid());
        System.out.println("Female pyramid " + initialPopulation.getFemalePyramid());
        double[][] maleSubpyramid = createSubpyramidWithMigrationCount(initialPopulation.getMalePyramid(), projector.getProjectionParameters().getMaleMortality().get(2000), projector.getProjectionParameters().getMaleInfantCumulativeMortality().get(2000), projector.getProjectionParameters().getMaleMigration().get(2000));
        //double[][] femaleSubpyramid = createSubpyramidWithMigrationCount(initialPopulation.getFemalePyramid(), projector.getProjectionParameters().getFemaleMortality().get(2000), projector.getProjectionParameters().getFemaleInfantCumulativeMortality().get(2000), projector.getProjectionParameters().getFemaleMigration().get(2000));
        System.out.println(Arrays.deepToString(maleSubpyramid));
        //System.out.println(Arrays.deepToString(femaleSubpyramid));
        //System.out.println(projector.getProjectionParameters().getMaleInfantCumulativeMortality().get(2000));

        System.out.println("Start projection.");
        //Population yearOnePopulation = projector.projectNextYearPopulationWithMigrationCount(initialPopulation);
        //Population yearTwoPopulation = projector.projectNextYearPopulationWithMigrationCount(yearOnePopulation, initialPopulation);
        //System.out.println("Year zero male pyramid " + initialPopulation.getMalePyramid());
        //System.out.println("Year one male pyramid " + yearOnePopulation.getMalePyramid());
        //System.out.println("Year two male pyramid " + yearTwoPopulation.getMalePyramid());
        //System.out.println("Year zero female pyramid " + initialPopulation.getFemalePyramid());
        //System.out.println("Year one female pyramid " + yearOnePopulation.getFemalePyramid());
        //System.out.println("Year two female pyramid " + yearTwoPopulation.getFemalePyramid());
        //System.out.println("Year two population " + yearTwoPopulation.getTotalPopulation() + " with " + yearTwoPopulation.getMalePopulation() + " males and " + yearTwoPopulation.getFemalePopulation() + " females.");
        projector.getPopulationCalculator().getExecutor().shutdown();
    }

    //Create subpyramid based on assumption age subgroups distributed according to mortality
    public static double[][] createSubpyramidWithMigrationCount(Map<Integer, Double> pyramid, Map<Integer, Double> mortality, Map<Double, Double> infantMortality, Map<Integer, Double> migration) {
        double[][] subpyramid = new double[pyramid.size()][ageSubgroups];

        //For age 0
        //Coefficient of multiplicative term in front of initial population when all summed together
        List<Double> relativePopulationBySubgroup = new ArrayList<>();
        double nextRelativePopulation;
        double totalRelativePopulation = 0;
        for (int subgroup = 0; subgroup < ageSubgroups; subgroup++) {
            nextRelativePopulation = 1 - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, (1 + 2 * subgroup) / (2 * (double) ageSubgroups)); //cubic spline approximation
            relativePopulationBySubgroup.add(nextRelativePopulation);
            totalRelativePopulation += nextRelativePopulation;
        }

        //Additive term of populations summed together (represents migrants), assume migrants only arrive after first month of life
        List<Double> migrantsBySubgroup = new ArrayList<>();
        double nextMigrants = 0;
        double totalMigrants = 0;
        double migrantGroups = 0;
        for (int subgroup = 0; subgroup < ageSubgroups; subgroup++) {
            if ((1 + 2 * subgroup) / (2 * (double) ageSubgroups) < 1 / (double) 12) {
                migrantsBySubgroup.add(0.0);
            } else {
                if (nextMigrants == 0) {
                    migrantGroups = ageSubgroups - subgroup;
                    nextMigrants = migration.get(0) / migrantGroups
                            * (1 - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, (3 + 4 * subgroup) / (4 * (double) ageSubgroups))) / (1 - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, (1 + 4 * subgroup) / (4 * (double) ageSubgroups)));
                } else {
                    nextMigrants = nextMigrants
                            * (1 - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, (1 + 2 * subgroup) / (2 * (double) ageSubgroups))) / (1 - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, (-1 + 2 * subgroup) / (2 * (double) ageSubgroups)))
                            + migration.get(0) / migrantGroups
                            * (1 - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, (3 + 4 * subgroup) / (4 * (double) ageSubgroups))) / (1 - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, (1 + 4 * subgroup) / (4 * (double) ageSubgroups)));
                }
                migrantsBySubgroup.add(nextMigrants);
                totalMigrants += nextMigrants;
            }
        }

        //Update subpyramid
        double basePopulation = (pyramid.get(0) - totalMigrants) / totalRelativePopulation;
        for (int subgroup = 0; subgroup < ageSubgroups; subgroup++) {
            subpyramid[0][subgroup] = basePopulation * relativePopulationBySubgroup.get(subgroup) + migrantsBySubgroup.get(subgroup);
        }

        //For other ages
        for (int age = 1; age < pyramid.keySet().size() - 1; age++) {
            //Coefficient of multiplicative term in front of initial population when all summed together
            relativePopulationBySubgroup = new ArrayList<>();
            nextRelativePopulation = 1.0;
            relativePopulationBySubgroup.add(nextRelativePopulation);
            totalRelativePopulation = 1.0;
            for (int subgroup = 1; subgroup < ageSubgroups; subgroup++) {
                nextRelativePopulation = nextRelativePopulation * Math.pow(1 - mortality.get(age), 1 / (double) ageSubgroups); //linear approximation
                relativePopulationBySubgroup.add(nextRelativePopulation);
                totalRelativePopulation += nextRelativePopulation;
            }

            //Additive term of populations summed together (represents migrants)
            migrantsBySubgroup = new ArrayList<>();
            nextMigrants = migration.get(age) / ageSubgroups * (1 - 0.5 * mortality.get(age) / (double) ageSubgroups);
            migrantsBySubgroup.add(nextMigrants);
            totalMigrants = nextMigrants;
            for (int subgroup = 1; subgroup < ageSubgroups; subgroup++) {
                nextMigrants = nextMigrants * Math.pow(1 - mortality.get(age), 1 / (double) ageSubgroups) + migration.get(age) / ageSubgroups * Math.pow(1 - mortality.get(age), 0.5 / (double) ageSubgroups);
                migrantsBySubgroup.add(nextMigrants);
                totalMigrants += nextMigrants;
            }

            //Create a refined pyramid
            basePopulation = (pyramid.get(age) - totalMigrants) / totalRelativePopulation;
            for (int subgroup = 0; subgroup < ageSubgroups; subgroup++) {
                subpyramid[age][subgroup] = basePopulation * relativePopulationBySubgroup.get(subgroup) + migrantsBySubgroup.get(subgroup);
            }
        }

        //For final age
        subpyramid[pyramid.size() - 1][0] = pyramid.get(pyramid.size() - 1);

        return subpyramid;
    }

    //Create subpyramid based on assumption age subgroups distributed according to mortality
    public static double[][] createSubpyramidWithMigrationRates(Map<Integer, Double> pyramid, Map<Integer, Double> mortality, Map<Double, Double> infantMortality, Map<Integer, Double> migration) {
        double[][] subpyramid = new double[pyramid.size()][ageSubgroups];

        //For age 0
        //Coefficient of multiplicative term in front of initial population when all summed together
        List<Double> relativePopulationBySubgroup = new ArrayList<>();
        double nextRelativePopulation = 1 - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, 1 / (2 * (double) ageSubgroups));
        relativePopulationBySubgroup.add(nextRelativePopulation);
        double totalRelativePopulation = nextRelativePopulation;
        for (int subgroup = 1; subgroup < ageSubgroups; subgroup++) {
            nextRelativePopulation = nextRelativePopulation
                    * (Math.pow(1 + migration.get(0), 1 / (double) ageSubgroups) - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, (1 + 2 * subgroup) / (2 * (double) ageSubgroups))) / (1 - PopulationCalculator.evaluateCubicSplineInterpolatedMap(infantMortality, (-1 + 2 * subgroup) / (2 * (double) ageSubgroups))); //cubic spline approximation
            relativePopulationBySubgroup.add(nextRelativePopulation);
            totalRelativePopulation += nextRelativePopulation;
        }

        //Update subpyramid
        double firstCohortPopulation = pyramid.get(0) / totalRelativePopulation;
        for (int subgroup = 0; subgroup < ageSubgroups; subgroup++) {
            subpyramid[0][subgroup] = firstCohortPopulation * relativePopulationBySubgroup.get(subgroup);
        }

        //For other ages
        for (int age = 1; age < pyramid.keySet().size() - 1; age++) {
            //Coefficient of multiplicative term in front of initial population when all summed together
            relativePopulationBySubgroup = new ArrayList<>();
            nextRelativePopulation = 1.0;
            relativePopulationBySubgroup.add(nextRelativePopulation);
            totalRelativePopulation = 1.0;
            for (int subgroup = 1; subgroup < ageSubgroups; subgroup++) {
                nextRelativePopulation = nextRelativePopulation * Math.pow(1 - mortality.get(age) + migration.get(age), 1 / (double) ageSubgroups); //linear approximation
                relativePopulationBySubgroup.add(nextRelativePopulation);
                totalRelativePopulation += nextRelativePopulation;
            }

            //Create a refined pyramid
            firstCohortPopulation = pyramid.get(age) / totalRelativePopulation;
            for (int subgroup = 0; subgroup < ageSubgroups; subgroup++) {
                subpyramid[age][subgroup] = firstCohortPopulation * relativePopulationBySubgroup.get(subgroup);
            }
        }

        //For final age
        subpyramid[pyramid.size() - 1][0] = pyramid.get(pyramid.size() - 1);

        return subpyramid;
    }
}