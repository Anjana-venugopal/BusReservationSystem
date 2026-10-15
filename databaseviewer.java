import java.io.*;
import java.util.*;

public class DatabaseViewer {
    private static final String STORAGE_FILE = "bharat_yatra_graphic_db.dat";

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) {
            // Also check alternate filename
            file = new File("bharat_yatra_database.dat");
        }

        if (!file.exists()) {
            System.out.println("No database file found in current directory.");
            return;
        }

        System.out.println("=================================================");
        System.out.println("  DATABASE FILE FOUND: " + file.getName());
        System.out.println("  SIZE: " + file.length() + " bytes");
        System.out.println("=================================================");

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object users = ois.readObject();
            Object buses = ois.readObject();
            Object routes = ois.readObject();
            Object tickets = ois.readObject();

            System.out.println("\n[TABLE 1: USERS]");
            if (users instanceof Map) {
                ((Map<?, ?>) users).forEach((k, v) -> System.out.println("  • " + k + " => " + v.getClass().getSimpleName()));
            }

            System.out.println("\n[TABLE 2: BUSES]");
            if (buses instanceof Map) {
                ((Map<?, ?>) buses).forEach((k, v) -> System.out.println("  • Bus ID: " + k));
            }

            System.out.println("\n[TABLE 3: ROUTES]");
            if (routes instanceof Map) {
                ((Map<?, ?>) routes).forEach((k, v) -> System.out.println("  • Route: " + k));
            }

            System.out.println("\n[TABLE 4: TICKETS / BOOKINGS]");
            if (tickets instanceof Map) {
                Map<?, ?> ticketMap = (Map<?, ?>) tickets;
                if (ticketMap.isEmpty()) {
                    System.out.println("  (No tickets booked yet)");
                } else {
                    ticketMap.forEach((k, v) -> System.out.println("  • PNR: " + k));
                }
            }
            System.out.println("\n=================================================");
        } catch (Exception e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }
}