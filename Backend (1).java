// BACKEND - exceptions, models, fare strategies, validation, services

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
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
 *                           BACKEND : EXCEPTIONS
 * ====================================================================== */

class BusReservationException extends Exception {
    private static final long serialVersionUID = 1L;

    public BusReservationException(String message) {
        super(message);
    }
}

class InvalidInputException extends BusReservationException {
    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }
}

class SeatUnavailableException extends BusReservationException {
    private static final long serialVersionUID = 1L;

    public SeatUnavailableException(String message) {
        super(message);
    }
}

class NotFoundException extends BusReservationException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}

/* ======================================================================
 *                           BACKEND : MODELS
 * ====================================================================== */

/** Abstract base class: Encapsulation + Abstraction + Inheritance. */
abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String fullName;
    private final String passwordHash;

    protected User(String username, String fullName, String rawPassword) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = hash(rawPassword);
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean checkPassword(String rawPassword) {
        return rawPassword != null && passwordHash.equals(hash(rawPassword));
    }

    /** Each subclass defines its own role (Polymorphism). */
    public abstract String getRole();

    private static String hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

class Admin extends User {
    private static final long serialVersionUID = 1L;

    public Admin(String username, String fullName, String rawPassword) {
        super(username, fullName, rawPassword);
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }
}

class Passenger extends User {
    private static final long serialVersionUID = 1L;

    private final int age;

    public Passenger(String username, String fullName, String rawPassword, int age) {
        super(username, fullName, rawPassword);
        this.age = age;
    }

    public int getAge() {
        return age;
    }

    public boolean isSenior() {
        return age >= 60;
    }

    @Override
    public String getRole() {
        return "PASSENGER";
    }
}

enum BusType {
    ORDINARY, AC, SLEEPER
}

enum TicketStatus {
    CONFIRMED, CANCELLED
}

class Route implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String source;
    private final String destination;
    private final double baseFare;

    public Route(String id, String source, String destination, double baseFare) {
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.baseFare = baseFare;
    }

    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public double getBaseFare() {
        return baseFare;
    }

    @Override
    public String toString() {
        return id + ": " + source + " -> " + destination;
    }
}

/** Seat state is private; it changes only via reserveSeat/releaseSeat (prevents double booking). */
class Bus implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final BusType type;
    private final int totalSeats;
    private final String routeId;
    private final LocalDate date;
    private final LocalTime time;
    private final Map<Integer, String> bookedSeats = new HashMap<>();

    public Bus(String id, String name, BusType type, int totalSeats,
               String routeId, LocalDate date, LocalTime time) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.totalSeats = totalSeats;
        this.routeId = routeId;
        this.date = date;
        this.time = time;
    }

    public synchronized void reserveSeat(int seatNo, String ticketId) throws BusReservationException {
        if (seatNo < 1 || seatNo > totalSeats) {
            throw new InvalidInputException("Seat number must be between 1 and " + totalSeats + ".");
        }
        if (bookedSeats.containsKey(seatNo)) {
            throw new SeatUnavailableException("Seat " + seatNo + " is already booked on bus " + id + ".");
        }
        bookedSeats.put(seatNo, ticketId);
    }

    public synchronized void releaseSeat(int seatNo) {
        bookedSeats.remove(seatNo);
    }

    public synchronized boolean isSeatBooked(int seatNo) {
        return bookedSeats.containsKey(seatNo);
    }

    public synchronized int getAvailableSeats() {
        return totalSeats - bookedSeats.size();
    }

    public LocalDateTime getDepartureDateTime() {
        return LocalDateTime.of(date, time);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BusType getType() {
        return type;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public String getRouteId() {
        return routeId;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }
}

class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String passengerUsername;
    private final String busId;
    private final int seatNo;
    private final double fare;
    private final LocalDateTime bookedOn;
    private TicketStatus status;

    public Ticket(String id, String passengerUsername, String busId, int seatNo, double fare) {
        this.id = id;
        this.passengerUsername = passengerUsername;
        this.busId = busId;
        this.seatNo = seatNo;
        this.fare = fare;
        this.bookedOn = LocalDateTime.now();
        this.status = TicketStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = TicketStatus.CANCELLED;
    }

    public String getId() {
        return id;
    }

    public String getPassengerUsername() {
        return passengerUsername;
    }

    public String getBusId() {
        return busId;
    }

    public int getSeatNo() {
        return seatNo;
    }

    public double getFare() {
        return fare;
    }

    public LocalDateTime getBookedOn() {
        return bookedOn;
    }

    public TicketStatus getStatus() {
        return status;
    }
}

/* ---------------- Fare strategies (Abstraction + Polymorphism) ---------------- */

interface FareStrategy {
    double calculate(double baseFare);

    String getName();
}

class OrdinaryFare implements FareStrategy {
    @Override
    public double calculate(double baseFare) {
        return baseFare;
    }

    @Override
    public String getName() {
        return "Ordinary";
    }
}

class AcFare implements FareStrategy {
    @Override
    public double calculate(double baseFare) {
        return baseFare * 1.5;
    }

    @Override
    public String getName() {
        return "AC";
    }
}

class SleeperFare implements FareStrategy {
    @Override
    public double calculate(double baseFare) {
        return baseFare * 1.8;
    }

    @Override
    public String getName() {
        return "Sleeper";
    }
}

/** Wraps another strategy and applies a 20% senior discount. */
class SeniorDiscountFare implements FareStrategy {
    private final FareStrategy inner;

    public SeniorDiscountFare(FareStrategy inner) {
        this.inner = inner;
    }

    @Override
    public double calculate(double baseFare) {
        return inner.calculate(baseFare) * 0.8;
    }

    @Override
    public String getName() {
        return inner.getName() + " + Senior discount";
    }
}

/* ======================================================================
 *                         BACKEND : SERVICES
 * ====================================================================== */

final class Validator {
    private Validator() {
    }

    public static String required(String value, String label) throws InvalidInputException {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidInputException(label + " is required.");
        }
        return value.trim();
    }

    public static int parseInt(String value, String label, int min, int max) throws InvalidInputException {
        String s = required(value, label);
        int n;
        try {
            n = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new InvalidInputException(label + " must be a whole number.");
        }
        if (n < min || n > max) {
            throw new InvalidInputException(label + " must be between " + min + " and " + max + ".");
        }
        return n;
    }

    public static double parsePositiveDouble(String value, String label) throws InvalidInputException {
        String s = required(value, label);
        double d;
        try {
            d = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new InvalidInputException(label + " must be a number.");
        }
        if (d <= 0) {
            throw new InvalidInputException(label + " must be greater than 0.");
        }
        return d;
    }

    public static LocalDate parseDate(String value, String label) throws InvalidInputException {
        String s = required(value, label);
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(label + " must be in format yyyy-MM-dd (e.g. 2026-12-31).");
        }
    }

    public static LocalTime parseTime(String value, String label) throws InvalidInputException {
        String s = required(value, label);
        try {
            return LocalTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(label + " must be in format HH:mm (e.g. 09:30).");
        }
    }
}

class AuthService {
    private final DataStore store;
    private final Database db;

    public AuthService(DataStore store, Database db) {
        this.store = store;
        this.db = db;
    }

    public synchronized User login(String username, String password) throws BusReservationException {
        String uname = Validator.required(username, "Username").toLowerCase();
        if (password == null || password.isEmpty()) {
            throw new InvalidInputException("Password is required.");
        }
        User user = store.getUsers().get(uname);
        if (user == null || !user.checkPassword(password)) {
            throw new InvalidInputException("Invalid username or password.");
        }
        return user;
    }

    public synchronized Passenger registerPassenger(String fullName, String username,
                                                    String password, String ageText)
            throws BusReservationException {
        String name = Validator.required(fullName, "Full name");
        String uname = Validator.required(username, "Username").toLowerCase();
        if (!uname.matches("[a-z0-9_]{3,20}")) {
            throw new InvalidInputException("Username must be 3-20 letters, digits or underscores.");
        }
        if (password == null || password.length() < 6) {
            throw new InvalidInputException("Password must be at least 6 characters.");
        }
        int age = Validator.parseInt(ageText, "Age", 1, 120);
        if (store.getUsers().containsKey(uname)) {
            throw new InvalidInputException("Username '" + uname + "' is already taken.");
        }
        Passenger passenger = new Passenger(uname, name, password, age);
        store.getUsers().put(uname, passenger);
        try {
            db.save(store);
        } catch (BusReservationException e) {
            store.getUsers().remove(uname);
            throw e;
        }
        return passenger;
    }
}

class BusService {
    private final DataStore store;
    private final Database db;

    public BusService(DataStore store, Database db) {
        this.store = store;
        this.db = db;
    }

    public synchronized Route addRoute(String id, String source, String destination, String fareText)
            throws BusReservationException {
        String rid = Validator.required(id, "Route ID").toUpperCase();
        String src = Validator.required(source, "Source");
        String dst = Validator.required(destination, "Destination");
        if (src.equalsIgnoreCase(dst)) {
            throw new InvalidInputException("Source and destination must be different.");
        }
        double fare = Validator.parsePositiveDouble(fareText, "Base fare");
        if (store.getRoutes().containsKey(rid)) {
            throw new InvalidInputException("Route ID '" + rid + "' already exists.");
        }
        Route route = new Route(rid, src, dst, fare);
        store.getRoutes().put(rid, route);
        try {
            db.save(store);
        } catch (BusReservationException e) {
            store.getRoutes().remove(rid);
            throw e;
        }
        return route;
    }

    public synchronized Bus addBus(String id, String name, BusType type, String seatsText,
                                   String routeId, String dateText, String timeText)
            throws BusReservationException {
        String bid = Validator.required(id, "Bus ID").toUpperCase();
        String bname = Validator.required(name, "Bus name");
        if (type == null) {
            throw new InvalidInputException("Bus type is required.");
        }
        int seats = Validator.parseInt(seatsText, "Total seats", 1, 60);
        String rid = Validator.required(routeId, "Route ID").toUpperCase();
        if (!store.getRoutes().containsKey(rid)) {
            throw new NotFoundException("Route '" + rid + "' does not exist.");
        }
        if (store.getBuses().containsKey(bid)) {
            throw new InvalidInputException("Bus ID '" + bid + "' already exists.");
        }
        LocalDate date = Validator.parseDate(dateText, "Date");
        LocalTime time = Validator.parseTime(timeText, "Time");
        if (!LocalDateTime.of(date, time).isAfter(LocalDateTime.now())) {
            throw new InvalidInputException("Departure date/time must be in the future.");
        }
        Bus bus = new Bus(bid, bname, type, seats, rid, date, time);
        store.getBuses().put(bid, bus);
        try {
            db.save(store);
        } catch (BusReservationException e) {
            store.getBuses().remove(bid);
            throw e;
        }
        return bus;
    }

    public synchronized List<Route> getRoutes() {
        return new ArrayList<>(store.getRoutes().values());
    }

    public synchronized List<Bus> getBuses() {
        List<Bus> list = new ArrayList<>(store.getBuses().values());
        list.sort(Comparator.comparing(Bus::getDepartureDateTime));
        return list;
    }

    public synchronized Bus getBus(String busId) throws BusReservationException {
        Bus bus = store.getBuses().get(busId);
        if (bus == null) {
            throw new NotFoundException("Bus '" + busId + "' does not exist.");
        }
        return bus;
    }

    public synchronized String getRouteLabel(String routeId) {
        Route r = store.getRoutes().get(routeId);
        return r == null ? routeId : r.toString();
    }

    /** Upcoming buses only. routeId and dateText are optional filters. */
    public synchronized List<Bus> searchBuses(String routeId, String dateText) throws BusReservationException {
        LocalDate date = null;
        if (dateText != null && !dateText.trim().isEmpty()) {
            date = Validator.parseDate(dateText, "Date");
        }
        List<Bus> result = new ArrayList<>();
        for (Bus b : store.getBuses().values()) {
            if (!b.getDepartureDateTime().isAfter(LocalDateTime.now())) {
                continue;
            }
            if (routeId != null && !b.getRouteId().equals(routeId)) {
                continue;
            }
            if (date != null && !b.getDate().equals(date)) {
                continue;
            }
            result.add(b);
        }
        result.sort(Comparator.comparing(Bus::getDepartureDateTime));
        return result;
    }
}

class BookingService {
    private final DataStore store;
    private final Database db;

    public BookingService(DataStore store, Database db) {
        this.store = store;
        this.db = db;
    }

    public synchronized double calculateFare(Passenger passenger, Bus bus) throws BusReservationException {
        Route route = store.getRoutes().get(bus.getRouteId());
        if (route == null) {
            throw new NotFoundException("Route for bus " + bus.getId() + " not found.");
        }
        FareStrategy strategy;
        switch (bus.getType()) {
            case AC:
                strategy = new AcFare();
                break;
            case SLEEPER:
                strategy = new SleeperFare();
                break;
            default:
                strategy = new OrdinaryFare();
                break;
        }
        if (passenger.isSenior()) {
            strategy = new SeniorDiscountFare(strategy);
        }
        double fare = strategy.calculate(route.getBaseFare());
        return Math.round(fare * 100.0) / 100.0;
    }

    public synchronized Ticket book(Passenger passenger, String busId, int seatNo)
            throws BusReservationException {
        Bus bus = store.getBuses().get(busId);
        if (bus == null) {
            throw new NotFoundException("Bus '" + busId + "' does not exist.");
        }
        if (!bus.getDepartureDateTime().isAfter(LocalDateTime.now())) {
            throw new InvalidInputException("This bus has already departed.");
        }
        double fare = calculateFare(passenger, bus);
        String ticketId = String.format("T%04d", store.nextTicketNumber());

        bus.reserveSeat(seatNo, ticketId); // throws if seat is taken or invalid
        Ticket ticket = new Ticket(ticketId, passenger.getUsername(), busId, seatNo, fare);
        store.getTickets().put(ticketId, ticket);
        try {
            db.save(store);
        } catch (BusReservationException e) {
            bus.releaseSeat(seatNo);
            store.getTickets().remove(ticketId);
            throw e;
        }
        return ticket;
    }

    public synchronized void cancel(Passenger passenger, String ticketId) throws BusReservationException {
        Ticket ticket = store.getTickets().get(ticketId);
        if (ticket == null) {
            throw new NotFoundException("Ticket '" + ticketId + "' does not exist.");
        }
        if (!ticket.getPassengerUsername().equals(passenger.getUsername())) {
            throw new InvalidInputException("This ticket belongs to another passenger.");
        }
        if (ticket.getStatus() != TicketStatus.CONFIRMED) {
            throw new InvalidInputException("Ticket is already cancelled.");
        }
        Bus bus = store.getBuses().get(ticket.getBusId());
        if (bus != null) {
            if (!bus.getDepartureDateTime().isAfter(LocalDateTime.now())) {
                throw new InvalidInputException("Cannot cancel after the bus has departed.");
            }
            bus.releaseSeat(ticket.getSeatNo());
        }
        ticket.cancel();
        db.save(store);
    }

    public synchronized List<Ticket> getTicketsFor(String username) {
        List<Ticket> list = new ArrayList<>();
        for (Ticket t : store.getTickets().values()) {
            if (t.getPassengerUsername().equals(username)) {
                list.add(t);
            }
        }
        return list;
    }

    public synchronized List<Ticket> getAllTickets() {
        return new ArrayList<>(store.getTickets().values());
    }
}

/** Single entry point the frontend uses to reach all backend services. */
class ServiceRegistry {
    private final AuthService authService;
    private final BusService busService;
    private final BookingService bookingService;

    public ServiceRegistry(Database db) throws BusReservationException {
        DataStore store = db.load();
        this.authService = new AuthService(store, db);
        this.busService = new BusService(store, db);
        this.bookingService = new BookingService(store, db);
    }

    public AuthService getAuthService() {
        return authService;
    }

    public BusService getBusService() {
        return busService;
    }

    public BookingService getBookingService() {
        return bookingService;
    }
}
