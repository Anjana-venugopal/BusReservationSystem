// DATABASE - storage interface, in-memory data store, file persistence

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* ======================================================================
 *                              DATABASE
 * ====================================================================== */

/** Abstraction over storage so the backend does not depend on how data is stored. */
interface Database {
    DataStore load() throws BusReservationException;

    void save(DataStore store) throws BusReservationException;
}

/** Holds all system data in memory; FileDatabase persists it to disk. */
class DataStore implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Route> routes = new LinkedHashMap<>();
    private final Map<String, Bus> buses = new LinkedHashMap<>();
    private final Map<String, Ticket> tickets = new LinkedHashMap<>();
    private int ticketCounter = 0;

    public Map<String, User> getUsers() {
        return users;
    }

    public Map<String, Route> getRoutes() {
        return routes;
    }

    public Map<String, Bus> getBuses() {
        return buses;
    }

    public Map<String, Ticket> getTickets() {
        return tickets;
    }

    public int nextTicketNumber() {
        ticketCounter++;
        return ticketCounter;
    }
}

class FileDatabase implements Database {
    private final File file;

    public FileDatabase(String path) {
        this.file = new File(path);
    }

    @Override
    public synchronized DataStore load() throws BusReservationException {
        if (!file.exists()) {
            DataStore fresh = createDefault();
            save(fresh);
            return fresh;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (DataStore) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new BusReservationException("Could not read '" + file.getAbsolutePath()
                    + "'. Delete that file and run again. (" + e.getMessage() + ")");
        }
    }

    @Override
    public synchronized void save(DataStore store) throws BusReservationException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(store);
        } catch (IOException e) {
            throw new BusReservationException("Could not save data: " + e.getMessage());
        }
    }

    private DataStore createDefault() {
        DataStore s = new DataStore();
        s.getUsers().put("admin", new Admin("admin", "System Administrator", "admin123"));

        s.getRoutes().put("R1", new Route("R1", "Thiruvananthapuram", "Kochi", 250.0));
        s.getRoutes().put("R2", new Route("R2", "Thiruvananthapuram", "Kollam", 90.0));
        s.getRoutes().put("R3", new Route("R3", "Kochi", "Bengaluru", 700.0));

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        s.getBuses().put("B101", new Bus("B101", "Kerala Express", BusType.AC, 40,
                "R1", tomorrow, LocalTime.of(8, 0)));
        s.getBuses().put("B102", new Bus("B102", "Coastal Rider", BusType.ORDINARY, 40,
                "R1", tomorrow, LocalTime.of(14, 30)));
        s.getBuses().put("B201", new Bus("B201", "Kollam Fast", BusType.ORDINARY, 30,
                "R2", tomorrow, LocalTime.of(7, 15)));
        s.getBuses().put("B301", new Bus("B301", "Night Sleeper", BusType.SLEEPER, 24,
                "R3", LocalDate.now().plusDays(2), LocalTime.of(21, 0)));
        return s;
    }
}
