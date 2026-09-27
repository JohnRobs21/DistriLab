package worker;

import common.WorkerService;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

//newly implemented imports
import common.JobPayload;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class WorkerImpl extends UnicastRemoteObject implements WorkerService {

    @SuppressWarnings("unused")
    private final String leaderman = "cs324";

    private final int nodeId;
    private int jobAllocationCounter; // JAC
    private final Set<WorkerService> neighbors;

    // Election & Leadership State
    private final Set<String> processedElections;
    private int currentTermId;
    private boolean isLeader;
    private WorkerService currentCoordinator;
    private String currentCoordinatorUrl;
    private int jobsInCurrentTerm;

    public WorkerImpl(int nodeId) throws RemoteException {
        super();
        this.nodeId = nodeId;
        this.jobAllocationCounter = 0;
        this.neighbors = ConcurrentHashMap.newKeySet();
        this.processedElections = ConcurrentHashMap.newKeySet();
        this.currentTermId = 0;
        this.isLeader = false;
        this.currentCoordinator = null;
        this.currentCoordinatorUrl = null;
        this.jobsInCurrentTerm = 0;
    }

    @Override
    public int getNodeId() throws RemoteException {
        return this.nodeId;
    }

    @Override
    public int getJAC() throws RemoteException {
        return this.jobAllocationCounter;
    }

    @Override
    public synchronized void addNeighbor(WorkerService neighbor) throws RemoteException {
        if (neighbor != null && !neighbor.equals(this)) {
            this.neighbors.add(neighbor);
            System.out.println("[Worker " + nodeId + "] Connected to neighbor: Worker " + neighbor.getNodeId());
        }
    }

    @Override
    public List<WorkerService> getNeighbors() throws RemoteException {
        return new ArrayList<>(this.neighbors);
    }

    // --- LEADER ELECTION ALGORITHM ---

    public synchronized void initiateElection() {
        String electionId = UUID.randomUUID().toString();
        int newTermId = this.currentTermId + 1;
        System.out.println("\n==================================================");
        System.out.println("[Worker " + nodeId + "] Initiating Leader Election for Term " + newTermId);
        System.out.println("==================================================");

        List<Integer> visitedNodes = new ArrayList<>();
        visitedNodes.add(this.nodeId);

        // Start election with this node as initial candidate
        propagateElection(electionId, this.jobAllocationCounter, this.nodeId, visitedNodes);
    }

    @Override
    public synchronized void receiveElection(String electionId, int candidateJAC, int candidateId, List<Integer> visitedNodes) throws RemoteException {
        if (processedElections.contains(electionId)) {
            return;
        }
        processedElections.add(electionId);

        System.out.println("[Worker " + nodeId + "] Received Election message. Current Candidate -> Worker " + candidateId + " (JAC: " + candidateJAC + ")");

        // Election rule: Lowest JAC wins. If tied, Highest ID wins.
        int bestJAC = candidateJAC;
        int bestId = candidateId;

        if (this.jobAllocationCounter < candidateJAC) {
            bestJAC = this.jobAllocationCounter;
            bestId = this.nodeId;
        } else if (this.jobAllocationCounter == candidateJAC) {
            if (this.nodeId > candidateId) {
                bestJAC = this.jobAllocationCounter;
                bestId = this.nodeId;
            }
        }

        List<Integer> updatedVisited = new ArrayList<>(visitedNodes != null ? visitedNodes : new ArrayList<>());
        if (!updatedVisited.contains(this.nodeId)) {
            updatedVisited.add(this.nodeId);
        }

        // Forward message to neighbors
        propagateElection(electionId, bestJAC, bestId, updatedVisited);

        // Announce win if this node is best candidate
        if (bestId == this.nodeId && !this.isLeader) {
            announceCoordinator(this.nodeId, "rmi://localhost:1099/Worker_" + this.nodeId, this.currentTermId + 1);
        }
    }

    private void propagateElection(String electionId, int candidateJAC, int candidateId, List<Integer> visitedNodes) {
        if (neighbors == null) return;

        for (WorkerService neighbor : neighbors) {
            try {
                if (neighbor != null && !visitedNodes.contains(neighbor.getNodeId())) {
                    neighbor.receiveElection(electionId, candidateJAC, candidateId, visitedNodes);
                }
            } catch (RemoteException e) {
                System.err.println("[Worker " + nodeId + "] Unreachable neighbor during election propagation.");
            }
        }
    }

    private void announceCoordinator(int winnerId, String winnerRmiUrl, int termId) {
        System.out.println("\n[Worker " + nodeId + "] ELECTED AS LEADER FOR TERM " + termId + "!");

        for (WorkerService neighbor : neighbors) {
            try {
                if (neighbor != null) {
                    neighbor.receiveCoordinator(winnerId, winnerRmiUrl, termId);
                }
            } catch (RemoteException e) {
                System.err.println("[Worker " + nodeId + "] Error broadcasting coordinator status.");
            }
        }
    }

    @Override
    public synchronized void receiveCoordinator(int electedLeaderId, String leaderRmiUrl, int termId) throws RemoteException {
        if (this.currentTermId >= termId) {
            return;
        }

        this.currentTermId = termId;
        this.jobsInCurrentTerm = 0;

        if (electedLeaderId == this.nodeId) {
            this.isLeader = true;
            this.currentCoordinator = this;
            this.currentCoordinatorUrl = leaderRmiUrl;
            System.out.println("[Worker " + nodeId + "] Confirmed as active Leader for Term " + termId);
        } else {
            this.isLeader = false;
            this.currentCoordinatorUrl = leaderRmiUrl;
            try {
                this.currentCoordinator = (WorkerService) Naming.lookup(leaderRmiUrl);
            } catch (Exception e) {
                this.currentCoordinator = null;
            }
            System.out.println("[Worker " + nodeId + "] Acknowledged Worker " + electedLeaderId + " as Leader for Term " + termId);
        }

        for (WorkerService neighbor : neighbors) {
            try {
                if (neighbor != null) {
                    neighbor.receiveCoordinator(electedLeaderId, leaderRmiUrl, termId);
                }
            } catch (RemoteException e) {
                // Neighbor already notified
            }
        }
    }
// Distributed Job Processing
    @Override
    public synchronized Object submitJob(String jobType, Object jobData) throws RemoteException {
        if (!isLeader) {
            if (currentCoordinator != null) {
                return currentCoordinator.submitJob(jobType, jobData);
            } else {
                initiateElection();
                if (currentCoordinator != null) {
                    return currentCoordinator.submitJob(jobType, jobData);
                }
                throw new RemoteException("No active coordinator available.");
            }
        }

        this.jobAllocationCounter++;
        this.jobsInCurrentTerm++;

        List<WorkerService> targetWorkers = getNeighbors();
        targetWorkers.add(this);

        JobPayload payload = (JobPayload) jobData;

        
        if (jobType.equalsIgnoreCase("MAX") || jobType.equalsIgnoreCase("PRIMECOUNT")){
            if(payload.getNumberArray().length < targetWorkers.size()){
                targetWorkers = targetWorkers.subList(0, payload.getNumberArray().length);
            }
        }
        int workerCount = targetWorkers.size();
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<Object>> futures = new ArrayList<>();

        

        for (int i = 0; i < workerCount; i++) {
            final WorkerService worker = targetWorkers.get(i);
            final int workerIndex = i;
        Callable<Object> task = () -> {
            JobPayload subPayload = createSubJobPayload(jobType, payload, workerIndex, workerCount);
            return worker.executeSubTask(jobType, subPayload);
            };
            futures.add(executor.submit(task));
        }

        Object finalResult = aggregateResults(jobType, futures);
        executor.shutdown();

        if (jobsInCurrentTerm >= 5){
            System.out.println("==================================================");
            System.out.println("[Leader Worker " + nodeId + "] Reached 5-job limit for Term " + currentTermId + ". Term ending!");
            System.out.println("==================================================");
            this.isLeader = false;
            this.jobsInCurrentTerm = 0;
            new Thread(this::initiateElection).start();
        }
        return finalResult;

    }

    @Override
    public int getRemainingTermJobs() throws RemoteException {
        return 5 - jobsInCurrentTerm;
    }

    

    private JobPayload createSubJobPayload(String jobType, JobPayload totalPayload, int index, int totalWorkers){
        if (jobType.equalsIgnoreCase("MAX") || jobType.equalsIgnoreCase("PRIMECOUNT")) {
            int[] arr = totalPayload.getNumberArray();
            int chunkSize = (int) Math.ceil((double) arr.length/totalWorkers);
            int start = index * chunkSize;
            int end = Math.min(start + chunkSize, arr.length);
            return new JobPayload(Arrays.copyOfRange(arr, start, end));

        }else{
            int start = totalPayload.getStartRange();
            int end = totalPayload.getEndRange();
            int rangeSize = (end - start + 1);
            int chunkSize = rangeSize/totalWorkers;

            int subStart = start + (index*chunkSize);
            int subEnd = (index == totalWorkers - 1) ? end: subStart + chunkSize - 1;
            return new JobPayload(subStart, subEnd);
        }
    } 

    private Object aggregateResults(String jobType, List<Future<Object>> futures) {
        try {
            if (jobType.equalsIgnoreCase("MAX")) {
                int globalMax = Integer.MIN_VALUE;
                for (Future<Object> f : futures) {
                    globalMax = Math.max(globalMax, (Integer) f.get());
                }
                return globalMax;
            } else if (jobType.equalsIgnoreCase("PRIMESUM")) {
                long totalSum = 0;
                for (Future<Object> f : futures) {
                    totalSum += (Long) f.get();
                }
                return totalSum;
            } else if (jobType.equalsIgnoreCase("PRIMECOUNT")) {
                int totalCount = 0;
                for (Future<Object> f : futures) {
                    totalCount += (Integer) f.get();
                }
                return totalCount;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //Local Sub-Task Computation worker side
    @Override
    public Object executeSubTask(String jobType, Object taskData) throws RemoteException{
        JobPayload payload = (JobPayload) taskData;

        if(jobType.equalsIgnoreCase("MAX")){
            int[] arr = payload.getNumberArray();
            int max = Integer.MIN_VALUE;
            for(int num: arr){
                if (num>max) max = num;
            }
            System.out.println("[Worker " + nodeId + "] Executed sub-task MAX -> Chunk Max:" + max);
            return max;
        }else if (jobType.equalsIgnoreCase("PRIMESUM")){
        long sum = 0;
        for(int i = payload.getStartRange(); i <= payload.getEndRange(); i++){
            if (isPrime(i)) sum += i;
        }
        System.out.println("[Worker " + nodeId + "] Executed sub-task PRIMESUM (" + payload.getStartRange() + " to " + payload.getEndRange() + ") -> Sub-Sum: " + sum);
        return sum;

        
        }else if (jobType.equalsIgnoreCase("PRIMECOUNT")) {
            int[] arr = payload.getNumberArray();
            int count = 0;
            for (int i: arr) {
                if (isPrime(i)) count++;
            }
            System.out.println("[Worker " + nodeId + "] Executed sub-task PRIMECOUNT -> sub-Count:" + count);
            return count;
        }

        return null;
        }
        private boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }
}

    

   
