import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SimAnnealingNeighbor {

    //Sort neighbors by ID -> insert in random alternating order by azimuth classification from shortest to longest haversine distance
    public static List<List<Integer>> sortNeighbors(List<List<Double>> azimuthArray, List<List<Double>> haversineArray, Integer taskCount, ExecutorService executor) {
        System.out.println("Generating sorted neighbors.");
        Map<Integer, List<Integer>> partitionedOrigins = MultithreadingUtils.orderedPartitionList(IntStream.range(0, azimuthArray.size()).boxed().collect(Collectors.toList()), taskCount);
        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentHashMap<Integer, List<List<Integer>>> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.submit(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                List<List<Integer>> currentOutput = new ArrayList<>();
                for (int j : partitionToOptimize) {
                    //Create a local hashmap from siteID -> haversineDist for each azimuth class
                    Map<Integer, Double> azimuthClassZeroMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassOneMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassTwoMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassThreeMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassFourMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassFiveMap = new HashMap<>();

                    //For every alternate site location, put it in a corresponding hashmap
                    for (int k = 0; k < haversineArray.get(0).size(); k++) {
                        if (azimuthArray.get(j).get(k) == -1.0) continue; //use -1.0 to identify same position, see FileUtils.getInnerAzimuthArrayFromCSV
                        Integer azimuthClass = classifyAzimuth(azimuthArray.get(j).get(k));
                        Double haversineDistance = haversineArray.get(j).get(k);
                        if (azimuthClass == 0) {
                            azimuthClassZeroMap.put(k, haversineDistance);
                        } else if (azimuthClass == 1) {
                            azimuthClassOneMap.put(k, haversineDistance);
                        } else if (azimuthClass == 2) {
                            azimuthClassTwoMap.put(k, haversineDistance);
                        } else if (azimuthClass == 3) {
                            azimuthClassThreeMap.put(k, haversineDistance);
                        } else if (azimuthClass == 4) {
                            azimuthClassFourMap.put(k, haversineDistance);
                        } else if (azimuthClass == 5) {
                            azimuthClassFiveMap.put(k, haversineDistance);
                        }
                    }

                    //Sort by cost and convert to list by eliminating cost to reduce memory needed to store and improve access
                    List<Integer> azimuthClassZeroList = azimuthClassZeroMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassOneList = azimuthClassOneMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassTwoList = azimuthClassTwoMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassThreeList = azimuthClassThreeMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassFourList = azimuthClassFourMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassFiveList = azimuthClassFiveMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());

                    //Merge wedges by index
                    List<List<Integer>> unmergedWedges = Arrays.asList(azimuthClassZeroList, azimuthClassOneList, azimuthClassTwoList, azimuthClassThreeList, azimuthClassFourList, azimuthClassFiveList);
                    List<Integer> mergedWedges = mergeSortedWedges(unmergedWedges);

                    //Add sortedAlternativeSites to final output
                    currentOutput.add(mergedWedges);
                }
                partitionedOutput.put(finalI, currentOutput);
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new AssertionError("Unexpected interruption", e);
        }

        //Merge output in order
        List<List<Integer>> output = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            output.addAll(partitionedOutput.get(i));
        }
        System.out.println("Done sorting neighbors.");
        return output;
    }

    //Only use if number of origins is equal to number of potential sites.
    public static List<List<Integer>> sortNeighbors(List<List<Double>> azimuthArray, List<List<Double>> haversineArray, Integer taskCount, ExecutorService executor, Map<Integer, List<Integer>> partitionedOrigins) {
        System.out.println("Generating sorted neighbors.");
        CountDownLatch latch = new CountDownLatch(taskCount);
        ConcurrentHashMap<Integer, List<List<Integer>>> partitionedOutput = new ConcurrentHashMap<>();
        for (int i = 0; i < taskCount; i++) {
            int finalI = i;
            executor.submit(() -> {
                List<Integer> partitionToOptimize = partitionedOrigins.get(finalI);
                List<List<Integer>> currentOutput = new ArrayList<>();
                for (int j : partitionToOptimize) {
                    //Create a local hashmap from siteID -> haversineDist for each azimuth class
                    Map<Integer, Double> azimuthClassZeroMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassOneMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassTwoMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassThreeMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassFourMap = new HashMap<>();
                    Map<Integer, Double> azimuthClassFiveMap = new HashMap<>();

                    //For every alternate site location, put it in a corresponding hashmap
                    for (int k = 0; k < haversineArray.get(0).size(); k++) {
                        if (azimuthArray.get(j).get(k) == -1.0) continue; //use -1.0 to identify same position, see FileUtils.getInnerAzimuthArrayFromCSV
                        Integer azimuthClass = classifyAzimuth(azimuthArray.get(j).get(k));
                        Double haversineDistance = haversineArray.get(j).get(k);
                        if (azimuthClass == 0) {
                            azimuthClassZeroMap.put(k, haversineDistance);
                        } else if (azimuthClass == 1) {
                            azimuthClassOneMap.put(k, haversineDistance);
                        } else if (azimuthClass == 2) {
                            azimuthClassTwoMap.put(k, haversineDistance);
                        } else if (azimuthClass == 3) {
                            azimuthClassThreeMap.put(k, haversineDistance);
                        } else if (azimuthClass == 4) {
                            azimuthClassFourMap.put(k, haversineDistance);
                        } else if (azimuthClass == 5) {
                            azimuthClassFiveMap.put(k, haversineDistance);
                        }
                    }

                    //Sort by cost and convert to list by eliminating cost to reduce memory needed to store and improve access
                    List<Integer> azimuthClassZeroList = azimuthClassZeroMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassOneList = azimuthClassOneMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassTwoList = azimuthClassTwoMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassThreeList = azimuthClassThreeMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassFourList = azimuthClassFourMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    List<Integer> azimuthClassFiveList = azimuthClassFiveMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());

                    //Merge wedges by index
                    List<List<Integer>> unmergedWedges = Arrays.asList(azimuthClassZeroList, azimuthClassOneList, azimuthClassTwoList, azimuthClassThreeList, azimuthClassFourList, azimuthClassFiveList);
                    List<Integer> mergedWedges = mergeSortedWedges(unmergedWedges);

                    //Add sortedAlternativeSites to final output
                    currentOutput.add(mergedWedges);
                }
                partitionedOutput.put(finalI, currentOutput);
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new AssertionError("Unexpected interruption", e);
        }

        //Merge output in order
        List<List<Integer>> output = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            output.addAll(partitionedOutput.get(i));
        }
        System.out.println("Done sorting neighbors.");
        return output;
    }

    //Classifies into 6 directional wedges from 0 to 5
    public static Integer classifyAzimuth (Double forwardAzimuth) {
        Integer wedge = -1;
        for (int i = 0; i <= 5; i++ ) {
            if((forwardAzimuth >= i * 60) && (forwardAzimuth < (i + 1) * 60)) {
                wedge = i;
                break;
            }
        }
        return wedge;
    }

    //For each current site, merge 6 wedges into sorted list with nth element of each wedge randomly but in sequence by index
    public static List<Integer> mergeSortedWedges (List<List<Integer>> unmergedWedges) {
        List<Integer> mergedWedges = new ArrayList<>();
        List<Integer> insertionOrder = IntStream.rangeClosed(0, 5).boxed().collect(Collectors.toList());
        //Insert by index in wedges
        for (int i = 0; i < getMaxWedgeSize(unmergedWedges); i++) {
            //Randomize insertion order of azimuth (wedge) classes for each index
            Collections.shuffle(insertionOrder);
            for (int j = 0; j <= 5; j++) {
                List<Integer> currentWedge = unmergedWedges.get(insertionOrder.get(j));
                if (currentWedge.size() > i) {
                    mergedWedges.add(currentWedge.get(i));
                }
            }
        }
        return mergedWedges;
    }

    private static Integer getMaxWedgeSize (List<List<Integer>> wedges) {
        Integer maxWedgeSize = 0;
        for (List<Integer> wedge : wedges) {
            maxWedgeSize = Math.max(wedge.size(), maxWedgeSize);
        }
        return maxWedgeSize;
    }

    //Get new site
    public static Integer getUnusedNeighbor(List<Integer> currentSites, Integer siteToShift, Integer neighborhoodSize, List<List<Integer>> sortedNeighbors) {
        //Generate a list of potential next sites given particular site and remove all current sites from consideration.
        List<Integer> nextSiteCandidates = new ArrayList<>(sortedNeighbors.get(siteToShift));
        nextSiteCandidates.removeAll(currentSites);
        //Find new positions to test
        Random random = new Random();
        return nextSiteCandidates.get(random.nextInt(neighborhoodSize));
    }

    //Get final neighborhood size, adjusting for -1 case where algorithm automatically determines final size
    public static Integer getFinalNeighborhoodSize (Integer potentialSitesCount, Integer centerCount, Integer initialFinalNeighborhoodSize) {
        if (initialFinalNeighborhoodSize == -1) {
            Integer adjustedFinalNeighborhoodSize = (int) Math.min(Math.ceil(1.5 * potentialSitesCount / centerCount), potentialSitesCount - centerCount);
            adjustedFinalNeighborhoodSize = Math.max(adjustedFinalNeighborhoodSize, 670); //minimum empirical neighborhood size
            adjustedFinalNeighborhoodSize = Math.min(adjustedFinalNeighborhoodSize, 1336); //maximum empirical neighborhood size
            return adjustedFinalNeighborhoodSize;
        } else {
            return initialFinalNeighborhoodSize;
        }
    }

    //Shrinking neighborhood size, full at iteration 1
    public static Integer getNeighborhoodSize(Integer centerCount, Integer potentialSitesCount, Integer finalNeighborhoodSize, Integer simAnnealingIteration, Integer finalNeighborhoodSizeIteration) {
        if (simAnnealingIteration >= finalNeighborhoodSizeIteration) {
            return finalNeighborhoodSize;
        } else {
            return finalNeighborhoodSize + (int) Math.floor((potentialSitesCount - centerCount - finalNeighborhoodSize) * (finalNeighborhoodSizeIteration - simAnnealingIteration) / finalNeighborhoodSizeIteration);
        }
    }

    //Sketch why 6 directions are sufficient, i.e. wedges of less than 60 degrees
    //Sufficient to show that there exists a path from any point to another for finite points {x} (justify later).
    //In Euclidean space, i.e. R2, let y denote an arbitrary point. It is not isolated because there exists a point x for which d(x, y) is minimal (<= all other points x in {x}).
    //The point y must be in some 60 degree directional wedge from x. It must be strictly minimal within that wedge, i.e. no point x' in that wedge d(x, x') <= d(x, y).
    //Sketch: Assume to the contrary that x' exists. By definition of x being the closest point to y, d(x', y) >= d(x, y). But angle between lines x to y and x to x' must be less than 60 degrees, so d(x', y) < d(x, y) by law of cosines.
    //In spherical space, i.e. S2, embed the S2 into R3. Again, let y denote some point. Then there exists a point x for which d(x, y) is minimal (<= all other points x in {x}) (i.e. not isolated).
    //Sketch: Let 2 great circles (circles with maximum radius on the sphere) be separated by less than 60 degrees. We assume safely that x is at the top of the sphere. Let d denote haversine distance.
    //Let x' be another point in {x} within that wedge, i.e. on another great circle less than 60 degrees from the great circle containing x and y (by definition 6 wedges).
    //By definition, we again have d(x', y) >= d(x, y) as x is minimal. Assume to the contrary d(x, x') <= d(x, y). Then take the straight-line distance between the three points to form a triangle. The map from straight-line to haversine distance is monotonic.
    //Consider the point y* along the great circle containing x and y, such that d(x, y*) = d(x, x'). That is, it has traveled the same distance from x toward y as the distance from x to x'.
    //Then projected onto plane tangent to x (top of sphere), we have the R2 case with angle less than 60 so sl(x', y*) < sl(x, y*) and sl(x', y*) < sl(x, x'). It follows that d(x', y) <= d(x', y*) + d(y*, y) < d(x, y*) + d(y(y*, y) = d(x, y). Contradiction.
    //Justification for earlier:
    //Induction
    // 1. The closest point to y can access y.
    // 2. Suppose the nth closest point to y can access y. Let x be the (n+1)th closest point. If the n closer points didn't exist, then x will have a direct path to y being the closest point.
    // Now if the points did exist, then either this wedge will contain new points that lead to the elimination of y or will contain y itself (accessible).
    // If it does not contain y, then the new points x must access one of the n closer points which access y.
    // End induction.
}