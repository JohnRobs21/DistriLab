package bootstrap;

import common.BootstrapService;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class BootstrapImpl extends UnicastRemoteObject implements BootstrapService {
    
    private final Map<Integer, String> registeredWorkers;
    private final Random random;

    public BootstrapImpl() throws RemoteException {
        super();
        this.registeredWorkers = new ConcurrentHashMap<>();
        this.random = new Random();
    }

    @Override 
    public synchronized String registerWorker(int workerId, String workerRmiUrl) throws RemoteException {
        System.out.println("[Bootstrap] Registration request -> ID: " + workerId + " | URL: " + workerRmiUrl);

        String randomNeighborUrl = null;

        // If active workers already exist, pick one randomly to serve as neighbor
        if (!registeredWorkers.isEmpty()) {
            List<String> activeUrls = new ArrayList<>(registeredWorkers.values());
            randomNeighborUrl = activeUrls.get(random.nextInt(activeUrls.size()));
        }

        // Add the new worker to active set
        registeredWorkers.put(workerId, workerRmiUrl);
        System.out.println("[Bootstrap] Worker " + workerId + " registered. Active workers: " + registeredWorkers.size());

        return randomNeighborUrl;
    }

    @Override
    public synchronized void unregisterWorker(int workerId) throws RemoteException {
        if (registeredWorkers.remove(workerId) != null) {
            System.out.println("[Bootstrap] Worker " + workerId + " unregistered. Active workers: " + registeredWorkers.size());
        }
    }

    @Override
    public synchronized List<String> getActiveWorkers() throws RemoteException {
        return new ArrayList<>(registeredWorkers.values());
    }
}