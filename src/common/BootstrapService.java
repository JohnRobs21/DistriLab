package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface BootstrapService extends Remote {
    /**
     * Registers a new worker node with the Bootstrap server.
     * 
     * @param workerId Unique integer ID of the worker.
     * @param workerRmiUrl RMI address string (e.g., "rmi://localhost:1099/Worker_101").
     * @return RMI URL of a randomly selected active worker node to connect to as a neighbor,
     *         or null if this is the first registered worker in the network.
     */
    String registerWorker(int workerId, String workerRmiUrl) throws RemoteException;

    /**
     * Unregisters a worker node when it shuts down cleanly.
     */
    void unregisterWorker(int workerId) throws RemoteException;

    /**
     * Returns a list of all currently active worker RMI URLs.
     */
    List<String> getActiveWorkers() throws RemoteException;
}