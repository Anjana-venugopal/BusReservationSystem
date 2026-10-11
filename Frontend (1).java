// FRONTEND - Swing GUI (login, admin, passenger, seat selection)

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
 *                         FRONTEND : SWING GUI
 * ====================================================================== */

final class UiUtil {
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private UiUtil() {
    }

    public static DefaultTableModel newModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static JTable newTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }
}

class LoginFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final ServiceRegistry services;
    private final JTextField userField = new JTextField(15);
    private final JPasswordField passField = new JPasswordField(15);

    public LoginFrame(ServiceRegistry services) {
        super("Bus Reservation System - Login");
        this.services = services;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel title = new JLabel("Bus Reservation System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setBorder(BorderFactory.createEmptyBorder(15, 10, 0, 10));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        form.add(new JLabel("Username:"));
        form.add(userField);
        form.add(new JLabel("Password:"));
        form.add(passField);

        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register (Passenger)");
        loginBtn.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e -> doRegister());

        JPanel buttons = new JPanel();
        buttons.add(loginBtn);
        buttons.add(registerBtn);

        JLabel hint = new JLabel("Default admin -> username: admin, password: admin123",
                SwingConstants.CENTER);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JPanel south = new JPanel(new GridLayout(2, 1));
        south.add(buttons);
        south.add(hint);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(title, BorderLayout.NORTH);
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(loginBtn);

        pack();
        setLocationRelativeTo(null);
    }

    private void doLogin() {
        try {
            User user = services.getAuthService().login(userField.getText(),
                    new String(passField.getPassword()));
            JFrame next;
            if (user instanceof Admin) {
                next = new AdminFrame(services, (Admin) user);
            } else {
                next = new PassengerFrame(services, (Passenger) user);
            }
            next.setVisible(true);
            dispose();
        } catch (BusReservationException ex) {
            UiUtil.error(this, ex.getMessage());
        }
    }

    private void doRegister() {
        JTextField name = new JTextField();
        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();
        JTextField age = new JTextField();
        Object[] fields = {
                "Full name:", name,
                "Username:", user,
                "Password (min 6 chars):", pass,
                "Age:", age
        };
        int result = JOptionPane.showConfirmDialog(this, fields, "Register Passenger",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            try {
                services.getAuthService().registerPassenger(name.getText(), user.getText(),
                        new String(pass.getPassword()), age.getText());
                UiUtil.info(this, "Registration successful. You can now log in.");
            } catch (BusReservationException ex) {
                UiUtil.error(this, ex.getMessage());
            }
        }
    }
}

class AdminFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final ServiceRegistry services;
    private final DefaultTableModel routeModel =
            UiUtil.newModel("Route ID", "Source", "Destination", "Base Fare (Rs)");
    private final DefaultTableModel busModel =
            UiUtil.newModel("Bus ID", "Name", "Type", "Route", "Date", "Time", "Total Seats", "Available");
    private final DefaultTableModel ticketModel =
            UiUtil.newModel("Ticket ID", "Passenger", "Bus", "Seat", "Fare (Rs)", "Booked On", "Status");

    public AdminFrame(ServiceRegistry services, Admin admin) {
        super("Admin Dashboard - " + admin.getFullName());
        this.services = services;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 560);
        setLocationRelativeTo(null);

        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("  Welcome, " + admin.getFullName() + " (Admin)"), BorderLayout.CENTER);
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> {
            new LoginFrame(services).setVisible(true);
            dispose();
        });
        top.add(logout, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Routes", buildRoutePanel());
        tabs.addTab("Buses & Seat Availability", buildBusPanel());
        tabs.addTab("All Bookings", buildTicketPanel());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(tabs, BorderLayout.CENTER);
        refreshAll();
    }

    private JPanel buildRoutePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JTable table = UiUtil.newTable(routeModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JTextField id = new JTextField(6);
        JTextField src = new JTextField(10);
        JTextField dst = new JTextField(10);
        JTextField fare = new JTextField(6);
        JButton addBtn = new JButton("Add Route");
        addBtn.addActionListener(e -> {
            try {
                services.getBusService().addRoute(id.getText(), src.getText(), dst.getText(), fare.getText());
                id.setText("");
                src.setText("");
                dst.setText("");
                fare.setText("");
                refreshAll();
            } catch (BusReservationException ex) {
                UiUtil.error(this, ex.getMessage());
            }
        });

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("Route ID:"));
        form.add(id);
        form.add(new JLabel("Source:"));
        form.add(src);
        form.add(new JLabel("Destination:"));
        form.add(dst);
        form.add(new JLabel("Base fare:"));
        form.add(fare);
        form.add(addBtn);
        panel.add(form, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBusPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JTable table = UiUtil.newTable(busModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JTextField id = new JTextField(5);
        JTextField name = new JTextField(9);
        JComboBox<BusType> typeBox = new JComboBox<>(BusType.values());
        JTextField seats = new JTextField("40", 3);
        JTextField routeId = new JTextField(4);
        JTextField date = new JTextField(LocalDate.now().plusDays(1).toString(), 8);
        JTextField time = new JTextField("09:00", 4);
        JButton addBtn = new JButton("Add Bus");
        JButton refreshBtn = new JButton("Refresh");

        addBtn.addActionListener(e -> {
            try {
                services.getBusService().addBus(id.getText(), name.getText(),
                        (BusType) typeBox.getSelectedItem(), seats.getText(),
                        routeId.getText(), date.getText(), time.getText());
                id.setText("");
                name.setText("");
                refreshAll();
            } catch (BusReservationException ex) {
                UiUtil.error(this, ex.getMessage());
            }
        });
        refreshBtn.addActionListener(e -> refreshAll());

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("Bus ID:"));
        form.add(id);
        form.add(new JLabel("Name:"));
        form.add(name);
        form.add(new JLabel("Type:"));
        form.add(typeBox);
        form.add(new JLabel("Seats:"));
        form.add(seats);
        form.add(new JLabel("Route ID:"));
        form.add(routeId);
        form.add(new JLabel("Date:"));
        form.add(date);
        form.add(new JLabel("Time:"));
        form.add(time);
        form.add(addBtn);
        form.add(refreshBtn);
        panel.add(form, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildTicketPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JTable table = UiUtil.newTable(ticketModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshAll());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(refreshBtn);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshAll() {
        routeModel.setRowCount(0);
        for (Route r : services.getBusService().getRoutes()) {
            routeModel.addRow(new Object[]{r.getId(), r.getSource(), r.getDestination(), r.getBaseFare()});
        }
        busModel.setRowCount(0);
        for (Bus b : services.getBusService().getBuses()) {
            busModel.addRow(new Object[]{b.getId(), b.getName(), b.getType(), b.getRouteId(),
                    b.getDate(), b.getTime(), b.getTotalSeats(), b.getAvailableSeats()});
        }
        ticketModel.setRowCount(0);
        for (Ticket t : services.getBookingService().getAllTickets()) {
            ticketModel.addRow(new Object[]{t.getId(), t.getPassengerUsername(), t.getBusId(),
                    t.getSeatNo(), t.getFare(), t.getBookedOn().format(UiUtil.DATE_TIME), t.getStatus()});
        }
    }
}

class PassengerFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final ServiceRegistry services;
    private final Passenger passenger;
    private final JComboBox<String> routeBox = new JComboBox<>();
    private final JTextField dateField = new JTextField(9);
    private List<Route> routes = new ArrayList<>();

    private final DefaultTableModel busModel = UiUtil.newModel(
            "Bus ID", "Name", "Type", "Route", "Date", "Time", "Seats Left", "Fare (Rs)");
    private final DefaultTableModel ticketModel = UiUtil.newModel(
            "Ticket ID", "Bus", "Seat", "Fare (Rs)", "Booked On", "Status");
    private final JTable busTable = UiUtil.newTable(busModel);
    private final JTable ticketTable = UiUtil.newTable(ticketModel);

    public PassengerFrame(ServiceRegistry services, Passenger passenger) {
        super("Passenger - " + passenger.getFullName());
        this.services = services;
        this.passenger = passenger;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 540);
        setLocationRelativeTo(null);

        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("  Welcome, " + passenger.getFullName()), BorderLayout.CENTER);
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> {
            new LoginFrame(services).setVisible(true);
            dispose();
        });
        top.add(logout, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Search & Book", buildSearchPanel());
        tabs.addTab("My Tickets", buildTicketPanel());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(tabs, BorderLayout.CENTER);

        loadRoutes();
        search();
        loadTickets();
    }

    private JPanel buildSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Route:"));
        filters.add(routeBox);
        filters.add(new JLabel("Date (yyyy-MM-dd, optional):"));
        filters.add(dateField);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> search());
        filters.add(searchBtn);
        panel.add(filters, BorderLayout.NORTH);

        panel.add(new JScrollPane(busTable), BorderLayout.CENTER);

        JButton bookBtn = new JButton("Select Seat & Book");
        bookBtn.addActionListener(e -> openSeatSelection());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(bookBtn);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildTicketPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JScrollPane(ticketTable), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadTickets());
        JButton cancelBtn = new JButton("Cancel Selected Ticket");
        cancelBtn.addActionListener(e -> cancelTicket());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(refreshBtn);
        south.add(cancelBtn);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void loadRoutes() {
        routes = services.getBusService().getRoutes();
        routeBox.removeAllItems();
        routeBox.addItem("All routes");
        for (Route r : routes) {
            routeBox.addItem(r.toString());
        }
    }

    private void search() {
        String routeId = null;
        int index = routeBox.getSelectedIndex();
        if (index > 0) {
            routeId = routes.get(index - 1).getId();
        }
        try {
            List<Bus> result = services.getBusService().searchBuses(routeId, dateField.getText());
            busModel.setRowCount(0);
            for (Bus b : result) {
                double fare = services.getBookingService().calculateFare(passenger, b);
                busModel.addRow(new Object[]{b.getId(), b.getName(), b.getType(),
                        services.getBusService().getRouteLabel(b.getRouteId()),
                        b.getDate(), b.getTime(), b.getAvailableSeats(), fare});
            }
        } catch (BusReservationException ex) {
            UiUtil.error(this, ex.getMessage());
        }
    }

    private void openSeatSelection() {
        int row = busTable.getSelectedRow();
        if (row < 0) {
            UiUtil.info(this, "Please select a bus from the table first.");
            return;
        }
        String busId = (String) busModel.getValueAt(row, 0);
        try {
            Bus bus = services.getBusService().getBus(busId);
            new SeatDialog(this, services, passenger, bus, () -> {
                search();
                loadTickets();
            }).setVisible(true);
        } catch (BusReservationException ex) {
            UiUtil.error(this, ex.getMessage());
        }
    }

    private void loadTickets() {
        ticketModel.setRowCount(0);
        for (Ticket t : services.getBookingService().getTicketsFor(passenger.getUsername())) {
            ticketModel.addRow(new Object[]{t.getId(), t.getBusId(), t.getSeatNo(), t.getFare(),
                    t.getBookedOn().format(UiUtil.DATE_TIME), t.getStatus()});
        }
    }

    private void cancelTicket() {
        int row = ticketTable.getSelectedRow();
        if (row < 0) {
            UiUtil.info(this, "Please select a ticket first.");
            return;
        }
        String ticketId = (String) ticketModel.getValueAt(row, 0);
        Object status = ticketModel.getValueAt(row, 5);
        if (status != TicketStatus.CONFIRMED) {
            UiUtil.error(this, "Only confirmed tickets can be cancelled.");
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Cancel ticket " + ticketId + "?",
                "Confirm cancellation", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            services.getBookingService().cancel(passenger, ticketId);
            UiUtil.info(this, "Ticket " + ticketId + " cancelled.");
            search();
            loadTickets();
        } catch (BusReservationException ex) {
            UiUtil.error(this, ex.getMessage());
        }
    }
}

class SeatDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final ServiceRegistry services;
    private final Passenger passenger;
    private final Bus bus;
    private final Runnable onBooked;

    public SeatDialog(Frame owner, ServiceRegistry services, Passenger passenger,
                      Bus bus, Runnable onBooked) {
        super(owner, "Select a seat - " + bus.getName(), true);
        this.services = services;
        this.passenger = passenger;
        this.bus = bus;
        this.onBooked = onBooked;

        JPanel grid = new JPanel(new GridLayout(0, 4, 6, 6));
        grid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (int seat = 1; seat <= bus.getTotalSeats(); seat++) {
            final int seatNo = seat;
            JButton btn = new JButton(String.valueOf(seat));
            btn.setOpaque(true);
            if (bus.isSeatBooked(seat)) {
                btn.setText(seat + " (X)");
                btn.setEnabled(false);
                btn.setBackground(new Color(220, 90, 90));
            } else {
                btn.setBackground(new Color(120, 200, 120));
                btn.addActionListener(e -> bookSeat(seatNo));
            }
            grid.add(btn);
        }

        JLabel legend = new JLabel("Green = available, Red (X) = booked", SwingConstants.CENTER);
        legend.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(new JScrollPane(grid), BorderLayout.CENTER);
        getContentPane().add(legend, BorderLayout.SOUTH);
        setSize(380, 440);
        setLocationRelativeTo(owner);
    }

    private void bookSeat(int seatNo) {
        try {
            double fare = services.getBookingService().calculateFare(passenger, bus);
            int ok = JOptionPane.showConfirmDialog(this,
                    "Book seat " + seatNo + " on " + bus.getName() + "?\nFare: Rs "
                            + String.format("%.2f", fare),
                    "Confirm booking", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) {
                return;
            }
            Ticket ticket = services.getBookingService().book(passenger, bus.getId(), seatNo);
            UiUtil.info(this, "Booking confirmed!\n\nTicket ID: " + ticket.getId()
                    + "\nBus: " + bus.getName() + " (" + bus.getId() + ")"
                    + "\nSeat: " + ticket.getSeatNo()
                    + "\nDeparture: " + bus.getDepartureDateTime().format(UiUtil.DATE_TIME)
                    + "\nFare: Rs " + String.format("%.2f", ticket.getFare()));
            onBooked.run();
            dispose();
        } catch (BusReservationException ex) {
            UiUtil.error(this, ex.getMessage());
        }
    }
}
