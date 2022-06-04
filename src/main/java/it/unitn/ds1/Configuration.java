package it.unitn.ds1;

public class Configuration {
    public static final int DATABASE_KEYS = 10;
    public static final int N_CLIENTS = 1;
    public static final int N_L1 = 2;
    public static final int N_L2_L1 = 2;
    public static final int TIMEOUT = 2000;
    public static final int RECOVERY_TIME = 6000;
    public static final double P_WRITE = 0.25;
    public static final double P_CRITICAL = 0;
    public static final double P_READ = 1 - P_WRITE;
    public static final int MAX_CONCURRENT_CRASHES = 1;
    public static final double P_CRASH = 0.1;
    public static int currentCrashes = 0;

}
