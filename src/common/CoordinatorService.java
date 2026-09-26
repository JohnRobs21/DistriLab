package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface CoordinatorService extends Remote {
    /**
     * Submits a job to the current coordinator node.
     * 
     * @param jobType "MAX", "PRIMESUM", or "PRIMECOUNT"
     * @param jobData Payload data required for computation
     * @return Aggregated result from all participating workers
     */
    Object submitJob(String jobType, Object jobData) throws RemoteException;

    /**
     * Returns the remaining number of job allocations for the current leader's 5-job term.
     */
    int getRemainingTermJobs() throws RemoteException;
}