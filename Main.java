// ENTRY POINT - connects Database -> Backend -> Web frontend and starts the server.

import java.io.File;

public class Main {
    public static void main(String[] args) {
        try {
            // Data is stored in the user's home folder, so it survives restarts.
            File dir = new File(System.getProperty("user.home"), ".busreservation");
            dir.mkdirs();
            Database db = new FileDatabase(new File(dir, "busdb.dat").getPath());
            ServiceRegistry services = new ServiceRegistry(db);

            WebServer server = new WebServer(services);
            int port = server.start(8080);
            String url = "http://localhost:" + port + "/";

            System.out.println("Bus Reservation System is running.");
            System.out.println("Open this address in your browser:  " + url);
            System.out.println("Admin login: admin / admin123      (press Ctrl+C to stop)");
            openBrowser(url);
        } catch (Exception e) {
            System.err.println("Startup error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void openBrowser(String url) {
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless()
                    && java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Throwable ignored) {
            // Not fatal: the user can open the address manually.
        }
    }
}
