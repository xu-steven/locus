import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.Random;

public abstract class SimAnnealingSearch {
    //Threadpool
    static ExecutorService executor;

    //Search space parameters
    static SearchSpace searchParameters;

    //Simulated annealing configuration
    static double initialTemp;// = 1000000;
    static double finalTemp;// = 1;
    static double coolingRate;// = 0.997;
    static int finalNeighborhoodSize;// = 60; Currently overriding in OptimizeNCenters method based on n if set at -1
    static int finalNeighborhoodSizeIteration; // = 3200;

    //Development only
    public static int updateFrequency = 1000; //frequency of updates in ms // development only
    
    //Acceptance probability. Energy is cost function.
    public static double acceptanceProbability(double currentEnergy, double newEnergy, double temperature) {
        //Accept superior solution
        if (newEnergy < currentEnergy) return 1;
        //Acceptance probability of an accepting inferior solution
        return Math.exp((currentEnergy - newEnergy) / temperature);
    }
}