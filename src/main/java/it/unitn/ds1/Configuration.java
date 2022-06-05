package it.unitn.ds1;

public class Configuration {
    public static final int DATABASE_KEYS = 10;

    // region Topology
    public static final int N_CLIENTS = 1;
    public static final int N_L1 = 2;
    public static final int N_L2_L1 = 2;
    //endregion

    // region Timings
    public static final int CLIENT_REQUEST_MIN_TIME = 1000;
    public static final int CLIENT_REQUEST_MAX_TIME = 2000;
    public static final int TIMEOUT = 500;
    public static final int CLIENT_TIMEOUT = TIMEOUT * 2;

    public static final int RECOVERY_MIN_TIME = 1000;
    public static final int RECOVERY_MAX_TIME = 5000;
    //endregion

    public static final int EVICT_TIME = 5000;
    public static final int EVICT_GRANULARITY = 500;

    //region Probabilities
    public static final double P_WRITE = 0;

    public static final double P_READ = 1 - P_WRITE;
    public static final double P_CRITICAL = 0;
    //endregion

    //region Crashes
    public static final int MAX_CONCURRENT_CRASHES = 2;
    public static final double P_CRASH = 0.01;
    public static int currentCrashes = 0;
    //endregion

}
