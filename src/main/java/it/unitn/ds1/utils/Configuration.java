package it.unitn.ds1.utils;

import it.unitn.ds1.utils.enums.CacheProtocolStage;

import java.util.EnumSet;

public class Configuration {

    // region Timings
    public static final int CLIENT_REQUEST_MIN_TIME = 500;
    public static final int CLIENT_REQUEST_MAX_TIME = 1000;
    public static final int TIMEOUT = 1000;
    public static final int DB_TIMEOUT = TIMEOUT / 2;
    public static final int CLIENT_TIMEOUT = TIMEOUT * 2;
    public static final int RECOVERY_MIN_TIME = 3000;
    public static final int RECOVERY_MAX_TIME = 10000;
    public static final int EVICT_TIME = 5000;
    public static final int EVICT_GRANULARITY = 500;
    //endregion

    //region Probabilities
    public static final double P_WRITE = 0.35;
    public static final double P_CRITICAL = 0.25;
    public static final double P_CRASH = 0.05;
    // endregion

    //region Crashes
    public static final int MAX_CONCURRENT_CRASHES = 1;

    //EnumSet.allOf(ProtocolStage.class);
    //EnumSet.of(ProtocolStage.Read, ProtocolStage.Write, ProtocolStage.Result);
    public static final EnumSet<CacheProtocolStage> STAGES_CRASH = EnumSet.allOf(CacheProtocolStage.class);


    //endregion

}
