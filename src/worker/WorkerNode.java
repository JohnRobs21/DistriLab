package worker;

import common.BootstrapService;
import common.WorkerService;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class WorkerNode {
    public static void main(String[] args) {
        int nodeId = (args.length > 0) ? Integer.parseInt(args[0]) : (int) (Math.random() * 9000 + 1000);
        int rmiPort = (args.length > 1) ? Integer.parseInt(args[1]) : 1099;

        String workerRmiUrl = "rmi://localhost:" + rmiPort + "/Worker_" + nodeId;

        try {
                System.out.println("[Worker Node " + nodeId + "] Starting process...");

                // 1. Create or get RMI registry
                Registry registry;
                try {
                    registry = LocateRegistry.createRegistry(rmiPort);
                } catch (Exception e) {
                    registry = LocateRegistry.getRegistry(rmiPort);
                }

                // 2. Instantiate and bind Worker remote object
                WorkerImpl workerInstance = new WorkerImpl(nodeId);
                registry.rebind("Worker_" + nodeId, workerInstance);

                System.out.println("[Worker " + nodeId + "] Bound in RMI registry as Worker_" + nodeId);

                // 3. Connect to Bootstrap Node
                BootstrapService bootstrap = (BootstrapService) Naming.lookup("rmi://localhost:1099/BootstrapService");
                
                // 4. Register with Bootstrap and retrieve a random active neighbor URL
                String neighborUrl = bootstrap.registerWorker(nodeId, workerRmiUrl);

                // 5. Connect to randomly assigned neighbor (if one exists)
                if (neighborUrl != null) {
                    System.out.println("[Worker " + nodeId + "] Connecting to neighbor: " + neighborUrl);
                    WorkerService neighborStub = (WorkerService) Naming.lookup(neighborUrl);
                    
                    // Establish bidirectional link in the unstructured graph
                    workerInstance.addNeighbor(neighborStub);
                    neighborStub.addNeighbor(workerInstance);
                } else {
                    System.out.println("[Worker " + nodeId + "] First node in network. Waiting for other workers to join...");
                }

                System.out.println("==================================================");
                System.out.println("Worker Node " + nodeId + " is live and ready!");
                System.out.println("==================================================");

            } catch (Exception e) {
                System.err.println("Error initializing Worker Node " + nodeId + ":");
                e.printStackTrace();
            }
    }
}