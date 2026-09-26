package worker;

import common.WorkerService;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorkerImpl extends UnicastRemoteObject implements WorkerService {
    @SuppressWarnings("unused")
    private final String leaderman = "cs324";

    private final int nodeId;
    private int jobAllocationCounter; // JAC
    private final Set<WorkerService> neighbors;

    private int currentTermId;
    private WorkerService currentCoordinator;
    private int jobsInCurrentTerm;

    public WorkerImpl(int nodeId) throws RemoteException {
        super();
        this.nodeId = nodeId;
        this.jobAllocationCounter = 0;
        this.neighbors = ConcurrentHashMap.newKeySet();
        this.currentTermId = 0;
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

    @Override
    public Object executeSubTask(String jobType, Object taskData) throws RemoteException {
        System.out.println("[Worker " + nodeId + "] Executing sub-task: " + jobType);
        // Task calculations will be implemented in Step 4
        return null;
    }

    @Override
    public void receiveElection(String electionId, int candidateJAC, int candidateId, List<Integer> visitedNodes) throws RemoteException {
        // Leader election algorithm will be completed in Step 3
    }

    @Override
    public void receiveCoordinator(int electedLeaderId, String leaderRmiUrl, int termId) throws RemoteException {
        // Coordinator announcement will be completed in Step 3
    }

    @Override
    public Object submitJob(String jobType, Object jobData) throws RemoteException {
        // Coordinator job distribution will be implemented in Step 4
        return null;
    }

    @Override
    public int getRemainingTermJobs() throws RemoteException {
        return 5 - jobsInCurrentTerm;
    }
}

