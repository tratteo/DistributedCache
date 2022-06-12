package it.unitn.ds1.test;

public enum TestConfiguration {
    /**
     * A system that randomly evolves
     **/
    Random,
    /**
     * Critical write operation success
     **/
    CriticalWriteSuccess,
    /**
     * Critical write operation failure due to crashes in the REMOVE protocol
     **/
    CriticalWriteFailure,
    /**
     * Critical read operation
     **/
    CriticalRead,
    /**
     * Eventual consistency and evicting time
     **/
    EventualConsistency,
    /**
     * Simulate multiple concurrent crashes in the system
     **/
    MultipleCrashes,
    /**
     * Observe how the system dynamically updates the topology due to crashes and recovers
     **/
    TopologyUpdates,
    /**
     * Simulate a scenario that shows the causal consistency model in case of CRITWRITE and CRITREAD
     **/
    SequentialConsistency
}
