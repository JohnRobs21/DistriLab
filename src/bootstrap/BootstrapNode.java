package bootstrap;

import common.BootstrapService;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class BootstrapNode {

    public static final int BOOTSTRAP_PORT = 1099;
    public static final String SERVICE_NAME = "BootstrapService";

    public static void main(String[] args) {
        try {
            System.out.println("Starting DistriLab Bootstrap Node...");

            // Create RMI Registry on port 1099
            Registry registry = LocateRegistry.createRegistry(BOOTSTRAP_PORT);

            // Instantiate and bind the Bootstrap service
            BootstrapService bootstrapService = new BootstrapImpl();
            registry.rebind(SERVICE_NAME, bootstrapService);

            System.out.println("==================================================");
            System.out.println("Bootstrap Node running on port " + BOOTSTRAP_PORT);
            System.out.println("Service bound as: " + SERVICE_NAME);
            System.out.println("Ready to accept worker registrations.");
            System.out.println("==================================================");

        } catch (RemoteException e) {
            System.err.println("Fatal error starting Bootstrap Node:");
        }
    }
}