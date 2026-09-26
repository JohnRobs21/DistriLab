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

    @Override
    public Object submitJob(String jobType, Object jobData) throws RemoteException {
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

        Object result = "Job " + jobType + " executed by Leader Worker " + nodeId;

        if (jobsInCurrentTerm >= 5) {
            System.out.println("[Leader Worker " + nodeId + "] Reached 5-job limit for Term " + currentTermId + ". Term ending!");
            this.isLeader = false;
            this.jobsInCurrentTerm = 0;
            new Thread(this::initiateElection).start();
        }

        return result;
    }

    @Override
    public int getRemainingTermJobs() throws RemoteException {
        return 5 - jobsInCurrentTerm;
    }

    @Override
    public Object executeSubTask(String jobType, Object taskData) throws RemoteException {
        return null;
    }
}