package it.unitn.ds1.test.check;


import akka.japi.Pair;
import it.unitn.ds1.enums.Operation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SequentialConsistencyCheck {

    public static void main(String[] args) {
        String fileName = args[0];
        HashMap<Integer, HashMap<String, List<OperationContainer>>> operations = build2DHashMapFromLog(fileName);
        printTimeline(operations);

        // Check consistency for every key timeline
        boolean consistent = true;
        for (Map.Entry<Integer, HashMap<String, List<OperationContainer>>> entry : operations.entrySet()) {
            boolean res = isSequentiallyConsistent(entry.getValue());
            System.out.println("Key " + entry.getKey() + " operations sequentially consistent: " + res);
            if (!res) {
                consistent = false;
                break;
            }
        }

        System.out.println("\nSequentially consistent: " + consistent);
        System.out.flush();
    }

    private static void printTimeline(HashMap<Integer, HashMap<String, List<OperationContainer>>> operations) {
        // Print the operation timelines grouped by keys
        for (Map.Entry<Integer, HashMap<String, List<OperationContainer>>> entry : operations.entrySet()) {
            System.out.println("\nOperations on key: " + entry.getKey());
            System.out.flush();
            for (Map.Entry<String, List<OperationContainer>> operationEntry : entry.getValue().entrySet()) {
                System.out.print(operationEntry.getKey() + " -> [ ");
                System.out.flush();
                List<OperationContainer> value = operationEntry.getValue();
                for (int i = 0; i < value.size(); i++) {
                    OperationContainer pair = value.get(i);
                    System.out.print(pair.operation.toString().charAt(0) + "(" + pair.key + "):" + pair.value);
                    if (i < value.size() - 1) {
                        System.out.print(" - ");
                    }
                    System.out.flush();
                }
                System.out.println(" ]");
                System.out.flush();
            }
        }
        System.out.println();
        System.out.flush();
    }

    private static HashMap<Integer, HashMap<String, List<OperationContainer>>> build2DHashMapFromLog(String fileName) {
        String line;
        HashMap<Integer, HashMap<String, List<OperationContainer>>> operations = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileName));
            int count = 0;
            // Build the 2D HashMap
            while ((line = reader.readLine()) != null) {
                if (isLineNotValid(line)) continue;
                String[] l = line.split(" ");
                int value = Integer.parseInt(l[l.length - 1]);
                int key = Integer.parseInt(l[l.length - 2]);
                Operation operation = Operation.valueOf(l[l.length - 5]);
                if (operations.containsKey(key)) {
                    HashMap<String, List<OperationContainer>> keyTable = operations.get(key);
                    if (keyTable.containsKey(l[0])) {
                        List<OperationContainer> sequence = keyTable.get(l[0]);
                        sequence.add(new OperationContainer(operation, key, value));
                    }
                    else {
                        ArrayList<OperationContainer> list = new ArrayList<>();
                        list.add(new OperationContainer(operation, key, value));
                        keyTable.put(l[0], list);
                    }
                    count++;
                }
                else {
                    HashMap<String, List<OperationContainer>> newMap = new HashMap<>();
                    ArrayList<OperationContainer> list = new ArrayList<>();
                    list.add(new OperationContainer(operation, key, value));
                    newMap.put(l[0], list);
                    operations.put(key, newMap);
                    count++;
                }
            }
            reader.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return operations;
    }

    private static boolean isSequentiallyConsistent(HashMap<String, List<OperationContainer>> operations) {
        boolean consistent = true;
        List<List<Integer>> readsOperations = new ArrayList<>();
        // Extract only the read operations results
        for (Map.Entry<String, List<OperationContainer>> entry : operations.entrySet()) {
            List<Integer> test = entry.getValue().stream().filter(p -> p.operation.equals(Operation.Read)).map(c -> c.value).collect(Collectors.toList());
            readsOperations.add(test);
        }

        // For each read operation
        for (List<Integer> reads : readsOperations) {
            // For each value of the current read
            for (int i = 0; i < reads.size(); i++) {
                // Build the Pair containing the value and all the subsequent values in the read operation
                Pair<Integer, List<Integer>> reduceMap = new Pair<>(reads.get(i), reads.stream().skip(i + 1).collect(Collectors.toList()));

                // For every other read, check that the value comes before all the values in list
                for (List<Integer> otherReads : readsOperations) {
                    // Skip if checking same read operations
                    if (otherReads.equals(reads)) continue;
                    //System.out.println("Checking ReduceMap: " + reduceMap + " on " + otherReads);
                    int coreElementIndex = otherReads.indexOf(reduceMap.first());
                    for (Integer k : reduceMap.second()) {
                        if (reduceMap.first().equals(k)) continue;
                        int index = otherReads.indexOf(k);
                        if (index < 0) continue;
                        // In other reads, there is an element that comes before the inspected one, thus the sequence is not the same for everyone
                        if (index < coreElementIndex) {
                            //System.out.println("Comparing " + reads + " with " + otherReads);
                            //System.out.println("Reduce: " + reduceMap + "False on core: " + reduceMap.first() + ", " + coreElementIndex + ", current: " + k + ", " + otherReads.indexOf(k));
                            consistent = false;
                        }
                    }
                }
            }
        }
        return consistent;
    }

    private static boolean isLineNotValid(String line) {
        String[] l = line.split(" ");
        if (l.length <= 5) return true;
        Pattern pattern = Pattern.compile("Client_");
        Pattern errorPattern = Pattern.compile("ERROR");
        if (errorPattern.matcher(line).find()) return true;
        return !pattern.matcher(line).find();
    }

    private static class OperationContainer {
        public final Operation operation;
        public final int key;
        public final int value;

        public OperationContainer(Operation operation, int key, int value) {
            this.operation = operation;
            this.key = key;
            this.value = value;
        }
    }
}