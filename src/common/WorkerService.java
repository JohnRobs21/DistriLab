package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface WorkerService extends Remote{
    int getNodeId() throws RemoteException;

    int getJAC() throws RemoteException;

    void addNeighbor(WorkerService neighbor) throws RemoteException;
    List<WorkerService> getNeighbors() throws RemoteException;

    Object executeSubTask(String jobType, Object taskData) throws RemoteException;

    void receiveElection(String electionID, int candidateJAC, int candidateId, List<Integer> visitedNodes) throws RemoteException;
    void receiveCoordinator(int electedLeaderId, String leaderRmiUrl, int termId) throws RemoteException;

    Object submitJob(String jobType, Object jobData) throws RemoteException;

    int getRemainingTermJobs() throws RemoteException;
}
