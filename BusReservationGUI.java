import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;

/* ============================================================================
   TIER 1: DATABASE PERSISTENCE LAYER (Storage & Repository)
   ============================================================================ */

interface IDatabaseRepository {
    void saveUser(User user);
    User findUserByUsername(String username);
    List<User> getAllUsers();

    void saveBus(Bus bus);
    Bus findBusById(String busId);
    List<Bus> getAllBuses();

    void saveRoute(Route route);
    Route findRouteById(String routeId);
    List<Route> getAllRoutes();

    void saveTicket(Ticket ticket);
    Ticket findTicketById(String ticketId);
    List<Ticket> getTicketsByPassenger(String passengerUsername);
    List<Ticket> getAllTickets();

    void syncToDisk();
    void loadFromDisk();
}

class FileDatabase implements IDatabaseRepository, Serializable {
    private static final long serialVersionUID = 1L;
    private static FileDatabase instance;
    private static final String STORAGE_FILE = "bharat_yatra_database.dat";

    private final Map<String, User> userTable = new ConcurrentHashMap<>();
    private final Map<String, Bus> busTable = new ConcurrentHashMap<>();
    private final Map<String, Route> routeTable = new ConcurrentHashMap<>();
    private final Map<String, Ticket> ticketTable = new ConcurrentHashMap<>();

    private FileDatabase() {}

    public static synchronized FileDatabase getInstance() {
        if (instance == null) {
            instance = new FileDatabase();
            instance.loadFromDisk();
        }
        return instance;
    }

    @Override
    public void saveUser(User user) {
        userTable.put(user.getUsername(), user);
        syncToDisk();
    }

    @Override
    public User findUserByUsername(String username) {
        return userTable.get(username);
    }

    @Override
    public List<User> getAllUsers() {
        return new ArrayList<>(userTable.values());
    }

    @Override
    public void saveBus(Bus bus) {
        busTable.put(bus.getBusId(), bus);
        syncToDisk();
    }

    @Override
    public Bus findBusById(String busId) {
        return busTable.get(busId);
    }

    @Override
    public List<Bus> getAllBuses() {
        return new ArrayList<>(busTable.values());
    }

    @Override
    public void saveRoute(Route route) {
        routeTable.put(route.getRouteId(), route);
        syncToDisk();
    }

    @Override
    public Route findRouteById(String routeId) {
        return routeTable.get(routeId);
    }

    @Override
    public List<Route> getAllRoutes() {
        return new ArrayList<>(routeTable.values());
    }

    @Override
    public void saveTicket(Ticket ticket) {
        ticketTable.put(ticket.getPnrNumber(), ticket);
        syncToDisk();
    }

    @Override
    public Ticket findTicketById(String ticketId) {
        return ticketTable.get(ticketId);
    }

    @Override
    public List<Ticket> getTicketsByPassenger(String passengerUsername) {
        List<Ticket> list = new ArrayList<>();
        for (Ticket t : ticketTable.values()) {
            if (t.getPassengerUsername().equalsIgnoreCase(passengerUsername)) {
                list.add(t);
            }
        }
        return list;
    }

    @Override
    public List<Ticket> getAllTickets() {
        return new ArrayList<>(ticketTable.values());
    }

    @Override
    public synchronized void syncToDisk() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(STORAGE_FILE))) {
            oos.writeObject(userTable);
            oos.writeObject(busTable);
            oos.writeObject(routeTable);
            oos.writeObject(ticketTable);
        } catch (IOException ignored) {}
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void loadFromDisk() {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<String, User> u = (Map<String, User>) ois.readObject();
            Map<String, Bus> b = (Map<String, Bus>) ois.readObject();
            Map<String, Route> r = (Map<String, Route>) ois.readObject();
            Map<String, Ticket> t = (Map<String, Ticket>) ois.readObject();

            userTable.putAll(u);
            busTable.putAll(b);
            routeTable.putAll(r);
            ticketTable.putAll(t);
        } catch (Exception ignored) {}
    }
}

/* ============================================================================
   TIER 2: BACKEND DOMAIN MODELS & OOP HIERARCHY
   ============================================================================ */

abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String userId;
    private final String username;
    private final String password;
    private final String fullName;

    public User(String userId, String username, String password, String fullName) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    public String getUsername() { return username; }
    public boolean authenticate(String pwd) { return this.password.equals(pwd); }
    public String getFullName() { return fullName; }
    public abstract String getRole();
}

class Passenger extends User {
    private final String mobileNumber;

    public Passenger(String userId, String username, String password, String fullName, String mobileNumber) {
        super(userId, username, password, fullName);
        this.mobileNumber = mobileNumber;
    }

    public String getMobileNumber() { return mobileNumber; }
    @Override public String getRole() { return "PASSENGER"; }
}

class Admin extends User {
    private final String designation;

    public Admin(String userId, String username, String password, String fullName, String designation) {
        super(userId, username, password, fullName);
        this.designation = designation;
    }

    public String getDesignation() { return designation; }
    @Override public String getRole() { return "DEPOT CONTROLLER"; }
}

class Seat implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int seatNumber;
    private boolean isBooked;
    private String passengerUsername;
    private final transient ReentrantLock lock = new ReentrantLock();

    public Seat(int seatNumber) {
        this.seatNumber = seatNumber;
        this.isBooked = false;
        this.passengerUsername = null;
    }

    public int getSeatNumber() { return seatNumber; }
    public synchronized boolean isBooked() { return isBooked; }

    public boolean reserve(String username) {
        ReentrantLock activeLock = (lock != null) ? lock : new ReentrantLock();
        activeLock.lock();
        try {
            if (isBooked) return false;
            this.isBooked = true;
            this.passengerUsername = username;
            return true;
        } finally {
            activeLock.unlock();
        }
    }

    public synchronized void cancel() {
        this.isBooked = false;
        this.passengerUsername = null;
    }
}

class Route implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String routeId;
    private final String origin;
    private final String destination;
    private final double distanceKm;
    private final String departureTime;
    private final String arrivalTime;
    private final String boardingPlatform;

    public Route(String routeId, String origin, String destination, double distanceKm, 
                 String departureTime, String arrivalTime, String boardingPlatform) {
        this.routeId = routeId;
        this.origin = origin;
        this.destination = destination;
        this.distanceKm = distanceKm;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.boardingPlatform = boardingPlatform;
    }

    public String getRouteId() { return routeId; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public double getDistanceKm() { return distanceKm; }
    public String getDepartureTime() { return departureTime; }
    public String getArrivalTime() { return arrivalTime; }
    public String getBoardingPlatform() { return boardingPlatform; }
}

abstract class Bus implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String busId;
    private final String registrationNumber;
    private final Route route;
    private final Map<Integer, Seat> seats = new HashMap<>();

    public Bus(String busId, String registrationNumber, Route route, int totalSeats) {
        this.busId = busId;
        this.registrationNumber = registrationNumber;
        this.route = route;
        for (int i = 1; i <= totalSeats; i++) {
            seats.put(i, new Seat(i));
        }
    }

    public String getBusId() { return busId; }
    public String getRegistrationNumber() { return registrationNumber; }
    public Route getRoute() { return route; }
    public Map<Integer, Seat> getSeats() { return seats; }
    public abstract String getBusServiceType();
    public abstract double calculateBaseFare();

    public double calculateTotalFareWithGst() {
        double base = calculateBaseFare();
        return Math.round(base * 1.05);
    }

    public synchronized int getAvailableSeatCount() {
        int count = 0;
        for (Seat s : seats.values()) {
            if (!s.isBooked()) count++;
        }
        return count;
    }
}

class SuperFastBus extends Bus {
    public SuperFastBus(String busId, String registrationNumber, Route route, int totalSeats) {
        super(busId, registrationNumber, route, totalSeats);
    }
    @Override public String getBusServiceType() { return "KSRTC Super Fast Non-AC"; }
    @Override public double calculateBaseFare() { return Math.round(getRoute().getDistanceKm() * 1.25 + 25.0); }
}

class GarudaAiravatAcBus extends Bus {
    public GarudaAiravatAcBus(String busId, String registrationNumber, Route route, int totalSeats) {
        super(busId, registrationNumber, route, totalSeats);
    }
    @Override public String getBusServiceType() { return "Airavat Club Class Multi-Axle AC"; }
    @Override public double calculateBaseFare() { return Math.round((getRoute().getDistanceKm() * 2.15) + 120.0); }
}

class SwiftSleeperBus extends Bus {
    public SwiftSleeperBus(String busId, String registrationNumber, Route route, int totalSeats) {
        super(busId, registrationNumber, route, totalSeats);
    }
    @Override public String getBusServiceType() { return "KSRTC-SWIFT AC Gajaraj Sleeper"; }
    @Override public double calculateBaseFare() { return Math.round((getRoute().getDistanceKm() * 2.65) + 200.0); }
}

class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String pnrNumber;
    private final String busId;
    private final String passengerUsername;
    private final String passengerName;
    private final String passengerMobile;
    private final int seatNumber;
    private final double fare;
    private final String bookingDate;
    private boolean active;

    public Ticket(String pnrNumber, String busId, String passengerUsername, 
                  String passengerName, String passengerMobile, int seatNumber, double fare) {
        this.pnrNumber = pnrNumber;
        this.busId = busId;
        this.passengerUsername = passengerUsername;
        this.passengerName = passengerName;
        this.passengerMobile = passengerMobile;
        this.seatNumber = seatNumber;
        this.fare = fare;
        this.bookingDate = new SimpleDateFormat("dd-MMM-yyyy hh:mm a").format(new Date());
        this.active = true;
    }

    public String getPnrNumber() { return pnrNumber; }
    public String getBusId() { return busId; }
    public String getPassengerUsername() { return passengerUsername; }
    public String getPassengerName() { return passengerName; }
    public String getPassengerMobile() { return passengerMobile; }
    public int getSeatNumber() { return seatNumber; }
    public double getFare() { return fare; }
    public String getBookingDate() { return bookingDate; }
    public boolean isActive() { return active; }
    public void cancel() { this.active = false; }
}

/* ============================================================================
   TIER 2 (CONT): BACKEND BUSINESS SERVICE LOGIC
   ============================================================================ */

interface IReservationService {
    Ticket bookSeat(String busId, int seatNumber, User user) throws Exception;
    boolean cancelTicket(String pnrNumber) throws Exception;
}

class ReservationServiceImpl implements IReservationService {
    private final IDatabaseRepository db = FileDatabase.getInstance();

    @Override
    public synchronized Ticket bookSeat(String busId, int seatNumber, User user) throws Exception {
        Bus bus = db.findBusById(busId);
        if (bus == null) throw new IllegalArgumentException("Bus schedule not found.");

        Seat seat = bus.getSeats().get(seatNumber);
        if (seat == null) throw new IllegalArgumentException("Invalid Seat Number specified.");

        if (!seat.reserve(user.getUsername())) {
            throw new IllegalStateException("Seat #" + seatNumber + " is already reserved by another passenger!");
        }

        String pnr = "KL" + (1000000 + new Random().nextInt(9000000));
        double totalFare = bus.calculateTotalFareWithGst();
        String mobile = (user instanceof Passenger) ? ((Passenger) user).getMobileNumber() : "Official Counter";

        Ticket ticket = new Ticket(pnr, busId, user.getUsername(), user.getFullName(), mobile, seatNumber, totalFare);

        db.saveTicket(ticket);
        db.saveBus(bus);
        return ticket;
    }

    @Override
    public synchronized boolean cancelTicket(String pnrNumber) throws Exception {
        Ticket ticket = db.findTicketById(pnrNumber);
        if (ticket == null || !ticket.isActive()) return false;

        Bus bus = db.findBusById(ticket.getBusId());
        if (bus != null) {
            Seat seat = bus.getSeats().get(ticket.getSeatNumber());
            if (seat != null) seat.cancel();
            db.saveBus(bus);
        }

        ticket.cancel();
        db.saveTicket(ticket);
        return true;
    }
}

/* ============================================================================
   TIER 3: PRESENTATION LAYER (GUI)
   ============================================================================ */

public class BusReservationGUI extends JFrame {
    private final IDatabaseRepository db = FileDatabase.getInstance();
    private final IReservationService service = new ReservationServiceImpl();
    private User currentUser = null;

    private JComboBox<String> busSelector;
    private JPanel seatGridPanel;
    private JLabel statusLabel;
    private JLabel fareBreakdownLabel;
    private DefaultTableModel ticketTableModel;
    private int selectedSeatNumber = -1;

    public BusReservationGUI() {
        seedRealisticFleetData();
        initUI();
    }

    private void seedRealisticFleetData() {
        if (db.getAllUsers().isEmpty()) {
            db.saveUser(new Admin("ADM-01", "officer", "kerala@123", "P. S. Ramanathan", "Chief Depot Controller"));
            db.saveUser(new Passenger("PAS-01", "anjana", "anjana@123", "Anjana V. Nair", "+91 94470 12345"));
            db.saveUser(new Passenger("PAS-02", "rahul", "rahul@123", "Rahul Krishnan", "+91 98460 98765"));
        }

        if (db.getAllRoutes().isEmpty()) {
            Route r1 = new Route("RT-101", "Thiruvananthapuram Central (TVC)", "Ernakulam South (KSRTC Stand)", 
                                 218.0, "06:00 AM", "11:15 AM", "Platform 3");
            Route r2 = new Route("RT-204", "Kozhikode New Bus Stand", "Bengaluru (Shantinagar Bus Station)", 
                                 354.0, "09:30 PM", "05:45 AM", "Platform 7");
            Route r3 = new Route("RT-308", "Ernakulam Vyttila Hub", "Chennai (Koyambedu / CMBT)", 
                                 684.0, "04:45 PM", "07:30 AM", "Platform 1");

            db.saveRoute(r1);
            db.saveRoute(r2);
            db.saveRoute(r3);

            db.saveBus(new SuperFastBus("KL-15-A-1102", "KL-15-A-1102", r1, 28));
            db.saveBus(new GarudaAiravatAcBus("KA-57-F-9090", "KA-57-F-9090", r2, 24));
            db.saveBus(new SwiftSleeperBus("SWIFT-KL-01-999", "KL-01-CZ-8888", r3, 20));
        }
    }

    private void initUI() {
        setTitle("BHARAT YATRA - State Road Transport & Intercity Seat Reservation Portal");
        setSize(1100, 760);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        if (!authenticateUserWithDialog()) {
            System.exit(0);
        }

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab("Coach Selection & Live Seating", createBookingPanel());
        tabbedPane.addTab("Passenger Bookings & Cancellation", createMyBookingsPanel());

        if (currentUser instanceof Admin) {
            tabbedPane.addTab("Depot Master Fleet Dispatch", createAdminPanel());
        }

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(24, 43, 73));
        headerPanel.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel titleLbl = new JLabel("BHARAT EXPRESS RESERVATION SYSTEM (KSRTC & INTERSTATE CO-OP)");
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));

        JLabel userBadge = new JLabel("Logged in: " + currentUser.getFullName() + " [" + currentUser.getRole() + "]");
        userBadge.setForeground(new Color(255, 215, 0));
        userBadge.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        headerPanel.add(titleLbl, BorderLayout.WEST);
        headerPanel.add(userBadge, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        setVisible(true);
    }

    private boolean authenticateUserWithDialog() {
        JDialog authDialog = new JDialog(this, "Portal Security Authentication", true);
        authDialog.setSize(520, 360);
        authDialog.setLocationRelativeTo(null);
        authDialog.setLayout(new BorderLayout());

        JTabbedPane authTabs = new JTabbedPane();

        JPanel passengerLoginPanel = new JPanel(new GridLayout(3, 2, 10, 12));
        passengerLoginPanel.setBorder(new EmptyBorder(25, 30, 25, 30));
        JTextField pUser = new JTextField();
        JPasswordField pPass = new JPasswordField();
        passengerLoginPanel.add(new JLabel("Passenger Username:"));
        passengerLoginPanel.add(pUser);
        passengerLoginPanel.add(new JLabel("Password:"));
        passengerLoginPanel.add(pPass);

        JButton pLoginBtn = new JButton("Passenger Sign In");
        pLoginBtn.setBackground(new Color(30, 130, 76));
        pLoginBtn.setForeground(Color.WHITE);
        passengerLoginPanel.add(new JLabel(""));
        passengerLoginPanel.add(pLoginBtn);

        JPanel registerPanel = new JPanel(new GridLayout(5, 2, 8, 8));
        registerPanel.setBorder(new EmptyBorder(15, 25, 15, 25));
        JTextField regName = new JTextField();
        JTextField regPhone = new JTextField();
        JTextField regUser = new JTextField();
        JPasswordField regPass = new JPasswordField();
        registerPanel.add(new JLabel("Full Legal Name:"));
        registerPanel.add(regName);
        registerPanel.add(new JLabel("10-Digit Mobile (+91):"));
        registerPanel.add(regPhone);
        registerPanel.add(new JLabel("Desired Username:"));
        registerPanel.add(regUser);
        registerPanel.add(new JLabel("Password:"));
        registerPanel.add(regPass);

        JButton regBtn = new JButton("Create Passenger Account");
        regBtn.setBackground(new Color(41, 128, 185));
        regBtn.setForeground(Color.WHITE);
        registerPanel.add(new JLabel(""));
        registerPanel.add(regBtn);

        JPanel adminLoginPanel = new JPanel(new GridLayout(3, 2, 10, 12));
        adminLoginPanel.setBorder(new EmptyBorder(25, 30, 25, 30));
        JTextField admUser = new JTextField();
        JPasswordField admPass = new JPasswordField();
        adminLoginPanel.add(new JLabel("Officer Employee ID:"));
        adminLoginPanel.add(admUser);
        adminLoginPanel.add(new JLabel("Security Password:"));
        adminLoginPanel.add(admPass);

        JButton admLoginBtn = new JButton("Officer Authorization");
        admLoginBtn.setBackground(new Color(192, 57, 43));
        admLoginBtn.setForeground(Color.WHITE);
        adminLoginPanel.add(new JLabel(""));
        adminLoginPanel.add(admLoginBtn);

        authTabs.addTab("Passenger Login", passengerLoginPanel);
        authTabs.addTab("Register New Passenger", registerPanel);
        authTabs.addTab("Depot Controller Login", adminLoginPanel);

        final boolean[] success = {false};

        pLoginBtn.addActionListener(e -> {
            User u = db.findUserByUsername(pUser.getText().trim());
            if (u != null && u.authenticate(new String(pPass.getPassword()))) {
                currentUser = u;
                success[0] = true;
                authDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(authDialog, "Invalid passenger credentials.", "Auth Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        regBtn.addActionListener(e -> {
            String name = regName.getText().trim();
            String mobile = regPhone.getText().trim();
            String usr = regUser.getText().trim();
            String pwd = new String(regPass.getPassword()).trim();

            if (name.isEmpty() || mobile.length() < 10 || usr.isEmpty() || pwd.isEmpty()) {
                JOptionPane.showMessageDialog(authDialog, "Please enter valid credentials with 10-digit mobile number.", "Input Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (db.findUserByUsername(usr) != null) {
                JOptionPane.showMessageDialog(authDialog, "Username already exists in system.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Passenger newPas = new Passenger("PAS-" + UUID.randomUUID().toString().substring(0, 5), usr, pwd, name, "+91 " + mobile);
            db.saveUser(newPas);
            currentUser = newPas;
            success[0] = true;
            JOptionPane.showMessageDialog(authDialog, "Account registered successfully for " + name + "!");
            authDialog.dispose();
        });

        admLoginBtn.addActionListener(e -> {
            User u = db.findUserByUsername(admUser.getText().trim());
            if (u instanceof Admin && u.authenticate(new String(admPass.getPassword()))) {
                currentUser = u;
                success[0] = true;
                authDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(authDialog, "Invalid Depot Controller credentials.", "Access Denied", JOptionPane.ERROR_MESSAGE);
            }
        });

        authDialog.add(authTabs, BorderLayout.CENTER);
        authDialog.setVisible(true);
        return success[0];
    }

    private JPanel createBookingPanel() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBackground(new Color(240, 243, 246));
        top.setBorder(new CompoundBorder(new LineBorder(new Color(210, 215, 220)), new EmptyBorder(10, 12, 10, 12)));

        JLabel selectLbl = new JLabel("Select Service / Route: ");
        selectLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));

        busSelector = new JComboBox<>();
        refreshBusSelector();
        busSelector.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        busSelector.addActionListener(e -> renderSeatGrid());

        top.add(selectLbl, BorderLayout.WEST);
        top.add(busSelector, BorderLayout.CENTER);

        seatGridPanel = new JPanel();
        seatGridPanel.setBackground(new Color(250, 250, 252));
        seatGridPanel.setBorder(new TitledBorder(new LineBorder(new Color(180, 190, 200)), "Intercity Coach Layout [2x2 Standard & Sleeper Berths]"));
        JScrollPane scroll = new JScrollPane(seatGridPanel);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(new Color(245, 245, 245));
        bottom.setBorder(new CompoundBorder(new LineBorder(new Color(210, 210, 210)), new EmptyBorder(12, 15, 12, 15)));

        JPanel textSide = new JPanel(new GridLayout(2, 1));
        textSide.setOpaque(false);
        statusLabel = new JLabel("Selected Seat: None");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        fareBreakdownLabel = new JLabel("Distance Tariff + Surcharges + 5% GST Included");
        fareBreakdownLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        fareBreakdownLabel.setForeground(Color.DARK_GRAY);
        textSide.add(statusLabel);
        textSide.add(fareBreakdownLabel);

        JButton bookBtn = new JButton("Confirm Booking & Issue Boarding Pass");
        bookBtn.setBackground(new Color(30, 130, 76));
        bookBtn.setForeground(Color.WHITE);
        bookBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        bookBtn.addActionListener(e -> handleBooking());

        bottom.add(textSide, BorderLayout.CENTER);
        bottom.add(bookBtn, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        renderSeatGrid();
        return root;
    }

    private void refreshBusSelector() {
        busSelector.removeAllItems();
        for (Bus b : db.getAllBuses()) {
            busSelector.addItem(b.getBusId() + " | " + b.getBusServiceType() + " | " + 
                b.getRoute().getOrigin() + " -> " + b.getRoute().getDestination() + 
                " | Dept: " + b.getRoute().getDepartureTime() + " | Fare: INR " + (int) b.calculateTotalFareWithGst());
        }
    }

    private void renderSeatGrid() {
        seatGridPanel.removeAll();
        selectedSeatNumber = -1;
        statusLabel.setText("Selected Seat: None");

        String selectedItem = (String) busSelector.getSelectedItem();
        if (selectedItem == null) return;
        String busId = selectedItem.split(" \\| ")[0];
        Bus bus = db.findBusById(busId);
        if (bus == null) return;

        Map<Integer, Seat> seats = bus.getSeats();
        int total = seats.size();
        seatGridPanel.setLayout(new GridLayout(0, 4, 16, 12));

        for (int i = 1; i <= total; i++) {
            Seat s = seats.get(i);
            JButton btn = new JButton();
            btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btn.setPreferredSize(new Dimension(100, 48));

            if (s.isBooked()) {
                btn.setText("Seat " + i + " [Booked]");
                btn.setBackground(new Color(231, 76, 60));
                btn.setForeground(Color.WHITE);
                btn.setEnabled(false);
            } else {
                btn.setText("Seat " + i + " (Avail)");
                btn.setBackground(new Color(235, 247, 238));
                btn.setForeground(new Color(20, 90, 50));
                final int seatNum = i;
                btn.addActionListener(e -> {
                    selectedSeatNumber = seatNum;
                    statusLabel.setText("Selected Seat: #" + seatNum + " on " + bus.getRegistrationNumber());
                    fareBreakdownLabel.setText("Base: INR " + (int)bus.calculateBaseFare() + " | Total Payable (w/ GST): INR " + (int)bus.calculateTotalFareWithGst());
                });
            }
            seatGridPanel.add(btn);
        }

        seatGridPanel.revalidate();
        seatGridPanel.repaint();
    }

    private void handleBooking() {
        if (selectedSeatNumber == -1) {
            JOptionPane.showMessageDialog(this, "Please select an available seat from the bus coach first.", "No Seat Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String busId = ((String) busSelector.getSelectedItem()).split(" \\| ")[0];
        Bus bus = db.findBusById(busId);
        if (bus == null) return;

        try {
            Ticket t = service.bookSeat(busId, selectedSeatNumber, currentUser);
            showDigitalBoardingPass(t, bus);
            renderSeatGrid();
            refreshMyBookings();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Reservation Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDigitalBoardingPass(Ticket t, Bus bus) {
        JDialog passDialog = new JDialog(this, "Official Boarding Pass", true);
        passDialog.setSize(560, 440);
        passDialog.setLocationRelativeTo(this);
        passDialog.setLayout(new BorderLayout());

        JPanel pass = new JPanel();
        pass.setBackground(Color.WHITE);
        pass.setBorder(new CompoundBorder(new LineBorder(new Color(24, 43, 73), 2), new EmptyBorder(20, 25, 20, 25)));
        pass.setLayout(new BoxLayout(pass, BoxLayout.Y_AXIS));

        JLabel header = new JLabel("BHARAT ROAD TRANSPORT CORPORATION (BRTC)");
        header.setFont(new Font("Segoe UI", Font.BOLD, 15));
        header.setForeground(new Color(24, 43, 73));
        header.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subHeader = new JLabel("CONFIRMED E-RESERVATION SLIP / BOARDING CARD");
        subHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        subHeader.setForeground(new Color(30, 130, 76));
        subHeader.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(500, 10));

        JLabel pnrLbl = new JLabel("PNR NUMBER: " + t.getPnrNumber() + "      |      DATE: " + t.getBookingDate());
        pnrLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JLabel routeLbl = new JLabel("ROUTE: " + bus.getRoute().getOrigin() + " -> " + bus.getRoute().getDestination());
        routeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel busLbl = new JLabel("SERVICE: " + bus.getBusServiceType() + " (" + bus.getRegistrationNumber() + ")");
        busLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel platLbl = new JLabel("DEPARTURE: " + bus.getRoute().getDepartureTime() + " | PLATFORM: " + bus.getRoute().getBoardingPlatform());
        platLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel seatLbl = new JLabel("ASSIGNED SEAT: #" + t.getSeatNumber() + "   |   STATUS: CONFIRMED");
        seatLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        seatLbl.setForeground(new Color(180, 40, 40));

        JLabel passLbl = new JLabel("PASSENGER: " + t.getPassengerName() + " (" + t.getPassengerMobile() + ")");
        passLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel fareLbl = new JLabel("TOTAL FARE PAID: INR " + (int) t.getFare() + " (INCL. APPLICABLE TAXES)");
        fareLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        fareLbl.setForeground(new Color(24, 43, 73));

        pass.add(header);
        pass.add(Box.createVerticalStrut(4));
        pass.add(subHeader);
        pass.add(Box.createVerticalStrut(10));
        pass.add(sep);
        pass.add(Box.createVerticalStrut(10));
        pass.add(pnrLbl);
        pass.add(Box.createVerticalStrut(6));
        pass.add(routeLbl);
        pass.add(Box.createVerticalStrut(6));
        pass.add(busLbl);
        pass.add(Box.createVerticalStrut(6));
        pass.add(platLbl);
        pass.add(Box.createVerticalStrut(6));
        pass.add(passLbl);
        pass.add(Box.createVerticalStrut(10));
        pass.add(seatLbl);
        pass.add(Box.createVerticalStrut(8));
        pass.add(fareLbl);

        JButton closeBtn = new JButton("Close / Save Boarding Pass");
        closeBtn.addActionListener(e -> passDialog.dispose());

        passDialog.add(pass, BorderLayout.CENTER);
        passDialog.add(closeBtn, BorderLayout.SOUTH);
        passDialog.setVisible(true);
    }

    private JPanel createMyBookingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        String[] cols = {"PNR Number", "Bus Number", "Seat #", "Passenger", "Total Fare", "Booking Date", "Status"};
        ticketTableModel = new DefaultTableModel(cols, 0);
        JTable table = new JTable(ticketTableModel);
        table.setRowHeight(24);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel Selected Ticket");
        cancelBtn.setBackground(new Color(192, 57, 43));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));

        cancelBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Select a booking row from the table first.", "Selection Needed", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String pnr = (String) ticketTableModel.getValueAt(row, 0);
            int confirm = JOptionPane.showConfirmDialog(this, "Confirm cancellation of Ticket " + pnr + "?", "Verify Cancellation", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            try {
                if (service.cancelTicket(pnr)) {
                    JOptionPane.showMessageDialog(this, "Ticket " + pnr + " is cancelled. The seat has been made available for booking.");
                    refreshMyBookings();
                    renderSeatGrid();
                } else {
                    JOptionPane.showMessageDialog(this, "Could not cancel ticket.");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        });

        bottom.add(cancelBtn);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        refreshMyBookings();
        return panel;
    }

    private void refreshMyBookings() {
        if (ticketTableModel == null) return;
        ticketTableModel.setRowCount(0);
        List<Ticket> list = (currentUser instanceof Admin) ? db.getAllTickets() : db.getTicketsByPassenger(currentUser.getUsername());

        for (Ticket t : list) {
            ticketTableModel.addRow(new Object[]{
                t.getPnrNumber(), t.getBusId(), t.getSeatNumber(), t.getPassengerName(),
                "INR " + (int) t.getFare(), t.getBookingDate(), t.isActive() ? "CONFIRMED" : "CANCELLED"
            });
        }
    }

    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel form = new JPanel(new GridLayout(6, 2, 10, 10));
        form.setBorder(new TitledBorder("Register & Dispatch New Bus to Depot"));

        JTextField busIdField = new JTextField("KL-15-TRV-890");
        JTextField plateField = new JTextField("KL-15-TRV-890");
        JComboBox<String> typeBox = new JComboBox<>(new String[]{"KSRTC Super Fast Non-AC", "Airavat Club Class Multi-Axle AC", "KSRTC-SWIFT AC Gajaraj Sleeper"});
        JTextField seatsField = new JTextField("24");

        form.add(new JLabel("Service Identifier:"));
        form.add(busIdField);
        form.add(new JLabel("Vehicle Plate (RTO Approved):"));
        form.add(plateField);
        form.add(new JLabel("Bus Coach Category:"));
        form.add(typeBox);
        form.add(new JLabel("Total Certified Seats:"));
        form.add(seatsField);

        JButton addBtn = new JButton("Authorize & Add Bus to Active Fleet");
        addBtn.setBackground(new Color(41, 128, 185));
        addBtn.setForeground(Color.WHITE);
        addBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));

        addBtn.addActionListener(e -> {
            try {
                String id = busIdField.getText().trim();
                String plate = plateField.getText().trim();
                int seats = Integer.parseInt(seatsField.getText().trim());
                Route defaultRoute = db.getAllRoutes().get(0);

                Bus newBus;
                String type = (String) typeBox.getSelectedItem();
                if ("Airavat Club Class Multi-Axle AC".equals(type)) {
                    newBus = new GarudaAiravatAcBus(id, plate, defaultRoute, seats);
                } else if ("KSRTC-SWIFT AC Gajaraj Sleeper".equals(type)) {
                    newBus = new SwiftSleeperBus(id, plate, defaultRoute, seats);
                } else {
                    newBus = new SuperFastBus(id, plate, defaultRoute, seats);
                }

                db.saveBus(newBus);
                refreshBusSelector();
                JOptionPane.showMessageDialog(this, "Bus service " + plate + " added to official schedule!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Input Error: " + ex.getMessage());
            }
        });

        panel.add(form, BorderLayout.NORTH);
        return panel;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(BusReservationGUI::new);
    }
}