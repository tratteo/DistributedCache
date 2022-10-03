![GitHub](https://img.shields.io/github/license/tratteo/DistributedCache?color=orange&label=License)
![GitHub top language](https://img.shields.io/github/languages/top/tratteo/DistributedCache?color=FF0035&label=Java)
![GitHub last commit (branch)](https://img.shields.io/github/last-commit/tratteo/DistributedCache/main?label=Last%20commit&color=brightgreen&logo=github)
 
 ## Run
To run this code, there are two ways:
- Use an IDE that runs Akka projects written in Java. Open your IDE, load the project and then run it.
- Run the code from source terminal. In this case go to the root directory and use the command `gradle run`

## Parameters
The program gives the possibility to the user to change some configurable parameters, such as timing values and probability values.
All these parameters are found in the `Configuration` file.

## Testing environment
A series of test configurations have been made in order to verify the correctness of the implemented protocol. Each test configuration
simulates a scenario in which it is possible to see how the protocol performs. TestConfiguration is an immutable enum variable which can
assume one of the following values, based on what configuration the user wants to execute:

- *Random*: the system executes operation randomly
- *CriticalWriteSuccess*: an instance where critical write operation is performed successfully
- *CriticalWriteFailure*: an instance where critical write oepration fails due to crashes in the remove protocol
- *CriticalRead*: an instance where critical read operation is performed successfully
- *EventualConsistency*: an instance proving that eventual consistency is achieved by evicting time of the cached item
- *MultipleCrashes*: simulate multiple concurrent crashes in the system
- *TopologyUpdates*: observe how the system dynamically updates the topology due to crashes and recovers
- *SequentialConsistency*: simulate a scenario that shows the sequential consistency in case of CRITREAD and CRITWRITE


If the *Random* configuration is chosen, operations will be randomly chosen with the configured properties in the `Configuration` file and the system will evolve randomly. 
Otherwise, if another configuration is given, the program will create the environment adapted to test the configuration. 
