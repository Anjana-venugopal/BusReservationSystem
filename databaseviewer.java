import java.io.*;
import java.util.*;

public class DatabaseViewer {
    private static final String STORAGE_FILE = "bharat_yatra_graphic_db.dat";

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) {
            System.out.println("❌ Database file not found: " + STORAGE_FILE);
            return;
        }

        System.out.println("==================================================================");
        System.out.println("          BHARAT YATRA - PERSISTED DATABASE VIEWER                ");
        System.out.println("          File: " + file.getAbsolutePath());
        System.out.println("          Size: " + file.length() + " bytes");
        System.out.println("==================================================================\n");

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, User> users = (Map<String, User>) ois.readObject();
            Map<String, Bus> buses = (Map<String, Bus>) ois.readObject();
            Map<String, Route> routes = (Map<String, Route>) ois.readObject();
            Map<String, Ticket> tickets = (Map<String, Ticket>) ois.readObject();

            // 1. Display Users
            System.out.println("--- [ TABLE: USERS (" + users.size() + " Records) ] ---");
            for (User u : users.values()) {
                System.out.printf("• Username: %-12s | Name: %-20s | Role: %s%n",
                        u.getUsername(), u.getFullName(), u.getRole());
            }

            // 2. Display Routes
            System.out.println("\n--- [ TABLE: ROUTES (" + routes.size() + " Records) ] ---");
            for (Route r : routes.values()) {
                System.out.printf("• Route ID: %-10s | %s -> %s (%.0f km) | Dept: %s%n",
                        r.getRouteId(), r.getOrigin(), r.getDestination(), r.getDistanceKm(), r.getDepartureTime());
            }

            // 3. Display Buses
            System.out.println("\n--- [ TABLE: BUS FLEET (" + buses.size() + " Records) ] ---");
            for (Bus b : buses.values()) {
                System.out.printf("• Bus ID: %-15s | Plate: %-14s | Type: %-32s | Avail Seats: %d/%d%n",
                        b.getBusId(), b.getRegistrationNumber(), b.getBusServiceType(),
                        b.getAvailableSeatCount(), b.getSeats().size());
            }

            // 4. Display Issued Tickets
            System.out.println("\n--- [ TABLE: TICKETS / PNR TRANSACTIONS (" + tickets.size() + " Records) ] ---");
            if (tickets.isEmpty()) {
                System.out.println("  (No tickets booked yet)");
            } else {
                for (Ticket t : tickets.values()) {
                    System.out.printf("• PNR: %-10s | Bus: %-14s | Seat: #%-2d | Passenger: %-18s | Fare: ₹%.0f | Status: %s%n",
                            t.getPnrNumber(), t.getBusId(), t.getSeatNumber(),
                            t.getPassengerName(), t.getFare(), t.isActive() ? "CONFIRMED" : "CANCELLED");
                }
            }
            System.out.println("\n==================================================================");

        } catch (Exception e) {
            System.err.println("Error reading binary database file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}