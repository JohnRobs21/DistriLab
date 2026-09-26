package worker;

import common.BootstrapService;
import common.WorkerService;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class WorkerNode {

    public static void main(String[] args) {
        int nodeId = (args.length > 0) ? Integer.parseInt(args[0]) : (int) (Math.random() * 9000 + 1000);
        int rmiPort = (args.length > 1) ? Integer.parseInt(args[1]) : 1099;

        String workerRmiUrl = "rmi://localhost:" + rmiPort + "/Worker_" + nodeId;

        try {
            System.out.println("[Worker Node " + nodeId + "] Starting process...");

            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(rmiPort);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(rmiPort);
            }

            final WorkerImpl workerInstance = new WorkerImpl(nodeId);
            registry.rebind("Worker_" + nodeId, workerInstance);

            System.out.println("[Worker " + nodeId + "] Bound in RMI registry as Worker_" + nodeId);

            BootstrapService bootstrap = (BootstrapService) Naming.lookup("rmi://localhost:1099/BootstrapService");
            
            String neighborUrl = bootstrap.registerWorker(nodeId, workerRmiUrl);

            if (neighborUrl != null) {
                System.out.println("[Worker " + nodeId + "] Connecting to neighbor: " + neighborUrl);
                WorkerService neighborStub = (WorkerService) Naming.lookup(neighborUrl);
                
                workerInstance.addNeighbor(neighborStub);
                neighborStub.addNeighbor(workerInstance);
            } else {
                System.out.println("[Worker " + nodeId + "] First node in network. Waiting for other workers to join...");
            }

            System.out.println("==================================================");
            System.out.println("Worker Node " + nodeId + " is live!");
            System.out.println("Type 'elect' and press Enter to trigger a Leader Election.");
            System.out.println("==================================================");

            Thread inputThread = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while (scanner.hasNextLine()) {
                    String input = scanner.nextLine().trim();
                    if (input.equalsIgnoreCase("elect")) {
                        workerInstance.initiateElection();
                    }
                }
            });
            inputThread.setDaemon(true);
            inputThread.start();

        } catch (Exception e) {
            System.err.println("Error initializing Worker Node " + nodeId + ":");
            e.printStackTrace();
        }
    }
}