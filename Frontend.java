// FRONTEND (web) - HTTP server, controllers and HTML pages, all written in Java.

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/* ======================================================================
 *                     HTTP PLUMBING (request / response / session)
 * ====================================================================== */

/** Keeps logged-in users. A random session id is stored in the browser cookie. */
class SessionManager {
    private final Map<String, User> sessions = new ConcurrentHashMap<>();

    public String create(User user) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, user);
        return id;
    }

    public User get(String id) {
        return id == null ? null : sessions.get(id);
    }

    public void remove(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }
}

/** Wraps an incoming HTTP request: query/form values and the logged-in user. */
class WebRequest {
    private final HttpExchange exchange;
    private final SessionManager sessions;
    private final Map<String, String> params = new HashMap<>();

    public WebRequest(HttpExchange exchange, SessionManager sessions) throws IOException {
        this.exchange = exchange;
        this.sessions = sessions;
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null) {
            parseInto(query);
        }
        if (isPost()) {
            parseInto(readBody(exchange.getRequestBody()));
        }
    }

    public boolean isPost() {
        return "POST".equalsIgnoreCase(exchange.getRequestMethod());
    }

    /** Returns the value for a field, or an empty string when it is missing. */
    public String get(String key) {
        String v = params.get(key);
        return v == null ? "" : v;
    }

    public String getSessionId() {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null) {
            return null;
        }
        for (String part : header.split(";")) {
            String p = part.trim();
            if (p.startsWith("SID=")) {
                return p.substring(4);
            }
        }
        return null;
    }

    public User getUser() {
        return sessions.get(getSessionId());
    }

    private void parseInto(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        for (String pair : data.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
    }

    private static String readBody(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            if (out.size() > 100_000) {
                throw new IOException("Request too large.");
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}

/** Wraps the HTTP response: send a page, redirect, set a cookie. */
class WebResponse {
    private final HttpExchange exchange;

    public WebResponse(HttpExchange exchange) {
        this.exchange = exchange;
    }

    public void html(int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    public void html(String body) throws IOException {
        html(200, body);
    }

    public void redirect(String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    public void setCookie(String cookie) {
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }
}

/* ======================================================================
 *                              HTML VIEW HELPERS
 * ====================================================================== */

final class Html {
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String CSS =
            "body{font-family:Segoe UI,Arial,sans-serif;margin:0;background:#f3f5f9;color:#1f2937}"
            + "nav{background:#1e3a8a;color:#fff;padding:12px 24px;display:flex;gap:18px;align-items:center}"
            + "nav .brand{font-weight:bold;font-size:18px;margin-right:auto}"
            + "nav a{color:#fff;text-decoration:none}nav a:hover{text-decoration:underline}"
            + "main{max-width:1000px;margin:24px auto;padding:0 16px}"
            + ".card{background:#fff;border-radius:8px;padding:20px;margin-bottom:20px;box-shadow:0 1px 3px rgba(0,0,0,.12)}"
            + "table{border-collapse:collapse;width:100%;margin-bottom:14px}"
            + "th,td{text-align:left;padding:8px 10px;border-bottom:1px solid #e5e7eb;font-size:14px}"
            + "th{background:#eef2ff}"
            + "input,select{padding:7px 9px;border:1px solid #cbd5e1;border-radius:5px;font-size:14px}"
            + "button,.btn{background:#2563eb;color:#fff;border:0;border-radius:5px;padding:8px 14px;"
            + "font-size:14px;cursor:pointer;text-decoration:none;display:inline-block}"
            + "button:hover,.btn:hover{background:#1d4ed8}"
            + "button.danger{background:#dc2626}button.danger:hover{background:#b91c1c}"
            + "form.row{display:flex;flex-wrap:wrap;gap:8px;align-items:center}"
            + "form.stack{display:flex;flex-direction:column;gap:10px;max-width:340px}"
            + ".msg{background:#dcfce7;color:#166534;padding:10px 14px;border-radius:6px;margin-bottom:16px}"
            + ".err{background:#fee2e2;color:#991b1b;padding:10px 14px;border-radius:6px;margin-bottom:16px}"
            + ".muted{color:#6b7280}"
            + ".seats{display:grid;grid-template-columns:repeat(4,70px);gap:8px;margin:14px 0}"
            + ".seat{padding:12px 0;font-weight:bold}"
            + ".seat.free{background:#16a34a}.seat.free:hover{background:#15803d}"
            + ".seat.booked{background:#dc2626;cursor:not-allowed;opacity:.8}"
            + ".tag{padding:2px 8px;border-radius:10px;font-size:12px;background:#e0e7ff}"
            + ".CONFIRMED{background:#dcfce7;color:#166534}.CANCELLED{background:#fee2e2;color:#991b1b}";

    private Html() {
    }

    public static String esc(Object value) {
        String s = String.valueOf(value);
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String url(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /** Cells must already be escaped HTML. */
    public static String table(String[] headers, List<List<String>> rows) {
        if (rows.isEmpty()) {
            return "<p class=\"muted\">Nothing to show yet.</p>";
        }
        StringBuilder sb = new StringBuilder("<div style=\"overflow-x:auto\"><table><tr>");
        for (String h : headers) {
            sb.append("<th>").append(esc(h)).append("</th>");
        }
        sb.append("</tr>");
        for (List<String> row : rows) {
            sb.append("<tr>");
            for (String cell : row) {
                sb.append("<td>").append(cell).append("</td>");
            }
            sb.append("</tr>");
        }
        return sb.append("</table></div>").toString();
    }

    public static String page(String title, User user, String msg, String err, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(esc(title)).append(" - Bus Reservation System</title>")
                .append("<style>").append(CSS).append("</style></head><body>");
        sb.append("<nav><span class=\"brand\">Bus Reservation System</span>");
        if (user instanceof Admin) {
            sb.append("<a href=\"/admin\">Dashboard</a>");
        } else if (user != null) {
            sb.append("<a href=\"/passenger\">Find buses</a><a href=\"/tickets\">My tickets</a>");
        }
        if (user != null) {
            sb.append("<span>").append(esc(user.getFullName())).append(" (")
                    .append(esc(user.getRole())).append(")</span><a href=\"/logout\">Logout</a>");
        }
        sb.append("</nav><main>");
        if (msg != null && !msg.isEmpty()) {
            sb.append("<div class=\"msg\">").append(esc(msg)).append("</div>");
        }
        if (err != null && !err.isEmpty()) {
            sb.append("<div class=\"err\">").append(esc(err)).append("</div>");
        }
        sb.append(body).append("</main></body></html>");
        return sb.toString();
    }
}

/* ======================================================================
 *                          CONTROLLERS (one class per page)
 * ====================================================================== */

/** Abstraction: every page is a controller that handles one request. */
interface Controller {
    void handle(WebRequest req, WebResponse res) throws Exception;
}

/** Base class for pages that need a logged-in user of a specific role (Inheritance). */
abstract class SecureController implements Controller {
    protected final ServiceRegistry services;
    private final boolean adminOnly;

    protected SecureController(ServiceRegistry services, boolean adminOnly) {
        this.services = services;
        this.adminOnly = adminOnly;
    }

    @Override
    public final void handle(WebRequest req, WebResponse res) throws Exception {
        User user = req.getUser();
        if (user == null) {
            res.redirect("/login?err=" + Html.url("Please log in first."));
            return;
        }
        if ((user instanceof Admin) != adminOnly) {
            res.redirect("/");
            return;
        }
        process(req, res, user);
    }

    /** Each page supplies its own behaviour (Polymorphism). */
    protected abstract void process(WebRequest req, WebResponse res, User user) throws Exception;

    protected void success(WebResponse res, String path, String message) throws IOException {
        res.redirect(path + "?msg=" + Html.url(message));
    }

    protected void failure(WebResponse res, String path, String message) throws IOException {
        res.redirect(path + "?err=" + Html.url(message));
    }
}

class HomeController implements Controller {
    @Override
    public void handle(WebRequest req, WebResponse res) throws Exception {
        User user = req.getUser();
        if (user == null) {
            res.redirect("/login");
        } else if (user instanceof Admin) {
            res.redirect("/admin");
        } else {
            res.redirect("/passenger");
        }
    }
}

class LoginController implements Controller {
    private final ServiceRegistry services;
    private final SessionManager sessions;

    public LoginController(ServiceRegistry services, SessionManager sessions) {
        this.services = services;
        this.sessions = sessions;
    }

    @Override
    public void handle(WebRequest req, WebResponse res) throws Exception {
        if (req.isPost()) {
            try {
                User user = services.getAuthService().login(req.get("username"), req.get("password"));
                res.setCookie("SID=" + sessions.create(user) + "; Path=/; HttpOnly; SameSite=Lax");
                res.redirect("/");
            } catch (BusReservationException e) {
                res.html(Html.page("Login", null, null, e.getMessage(), form(req.get("username"))));
            }
            return;
        }
        res.html(Html.page("Login", null, req.get("msg"), req.get("err"), form("")));
    }

    private String form(String username) {
        return "<div class=\"card\"><h2>Login</h2>"
                + "<form method=\"post\" action=\"/login\" class=\"stack\">"
                + "<input name=\"username\" placeholder=\"Username\" value=\"" + Html.esc(username) + "\" required>"
                + "<input name=\"password\" type=\"password\" placeholder=\"Password\" required>"
                + "<button type=\"submit\">Login</button></form>"
                + "<p>New passenger? <a href=\"/register\">Create an account</a></p>"
                + "<p class=\"muted\">Default admin: username <b>admin</b>, password <b>admin123</b></p></div>";
    }
}

class RegisterController implements Controller {
    private final ServiceRegistry services;

    public RegisterController(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public void handle(WebRequest req, WebResponse res) throws Exception {
        if (req.isPost()) {
            try {
                services.getAuthService().registerPassenger(req.get("fullName"), req.get("username"),
                        req.get("password"), req.get("age"));
                res.redirect("/login?msg=" + Html.url("Registration successful. You can now log in."));
            } catch (BusReservationException e) {
                res.html(Html.page("Register", null, null, e.getMessage(), form(req)));
            }
            return;
        }
        res.html(Html.page("Register", null, null, null, form(req)));
    }

    private String form(WebRequest req) {
        return "<div class=\"card\"><h2>Register as passenger</h2>"
                + "<form method=\"post\" action=\"/register\" class=\"stack\">"
                + "<input name=\"fullName\" placeholder=\"Full name\" value=\"" + Html.esc(req.get("fullName")) + "\" required>"
                + "<input name=\"username\" placeholder=\"Username (3-20 letters/digits/_)\" value=\"" + Html.esc(req.get("username")) + "\" required>"
                + "<input name=\"password\" type=\"password\" placeholder=\"Password (min 6 characters)\" required>"
                + "<input name=\"age\" placeholder=\"Age\" value=\"" + Html.esc(req.get("age")) + "\" required>"
                + "<button type=\"submit\">Register</button></form>"
                + "<p><a href=\"/login\">Back to login</a></p></div>";
    }
}

class LogoutController implements Controller {
    private final SessionManager sessions;

    public LogoutController(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void handle(WebRequest req, WebResponse res) throws Exception {
        sessions.remove(req.getSessionId());
        res.setCookie("SID=; Path=/; Max-Age=0");
        res.redirect("/login?msg=" + Html.url("You have been logged out."));
    }
}

/* ------------------------------ Admin pages ------------------------------ */

class AdminController extends SecureController {
    public AdminController(ServiceRegistry services) {
        super(services, true);
    }

    @Override
    protected void process(WebRequest req, WebResponse res, User user) throws Exception {
        StringBuilder b = new StringBuilder();

        // Routes
        List<List<String>> routeRows = new ArrayList<>();
        for (Route r : services.getBusService().getRoutes()) {
            routeRows.add(Arrays.asList(Html.esc(r.getId()), Html.esc(r.getSource()),
                    Html.esc(r.getDestination()), String.format("%.2f", r.getBaseFare())));
        }
        b.append("<div class=\"card\"><h2>Routes</h2>")
                .append(Html.table(new String[]{"Route ID", "Source", "Destination", "Base fare (Rs)"}, routeRows))
                .append("<form method=\"post\" action=\"/admin/route\" class=\"row\">")
                .append("<input name=\"id\" placeholder=\"Route ID\" size=\"8\" required>")
                .append("<input name=\"source\" placeholder=\"Source\" required>")
                .append("<input name=\"destination\" placeholder=\"Destination\" required>")
                .append("<input name=\"fare\" placeholder=\"Base fare\" size=\"8\" required>")
                .append("<button type=\"submit\">Add route</button></form></div>");

        // Buses
        List<List<String>> busRows = new ArrayList<>();
        for (Bus bus : services.getBusService().getBuses()) {
            busRows.add(Arrays.asList(Html.esc(bus.getId()), Html.esc(bus.getName()), Html.esc(bus.getType()),
                    Html.esc(services.getBusService().getRouteLabel(bus.getRouteId())),
                    Html.esc(bus.getDate()), Html.esc(bus.getTime()),
                    String.valueOf(bus.getTotalSeats()), String.valueOf(bus.getAvailableSeats())));
        }
        b.append("<div class=\"card\"><h2>Buses &amp; seat availability</h2>")
                .append(Html.table(new String[]{"Bus ID", "Name", "Type", "Route", "Date", "Time", "Seats", "Available"}, busRows))
                .append("<form method=\"post\" action=\"/admin/bus\" class=\"row\">")
                .append("<input name=\"id\" placeholder=\"Bus ID\" size=\"7\" required>")
                .append("<input name=\"name\" placeholder=\"Bus name\" required>")
                .append("<select name=\"type\">");
        for (BusType t : BusType.values()) {
            b.append("<option>").append(t).append("</option>");
        }
        b.append("</select>")
                .append("<input name=\"seats\" value=\"40\" size=\"4\" title=\"Total seats\" required>")
                .append("<input name=\"routeId\" placeholder=\"Route ID\" size=\"8\" required>")
                .append("<input name=\"date\" type=\"date\" value=\"").append(LocalDate.now().plusDays(1)).append("\" required>")
                .append("<input name=\"time\" type=\"time\" value=\"09:00\" required>")
                .append("<button type=\"submit\">Add bus</button></form></div>");

        // Tickets
        List<List<String>> ticketRows = new ArrayList<>();
        for (Ticket t : services.getBookingService().getAllTickets()) {
            ticketRows.add(Arrays.asList(Html.esc(t.getId()), Html.esc(t.getPassengerUsername()),
                    Html.esc(t.getBusId()), String.valueOf(t.getSeatNo()), String.format("%.2f", t.getFare()),
                    Html.esc(t.getBookedOn().format(Html.DATE_TIME)),
                    "<span class=\"tag " + t.getStatus() + "\">" + t.getStatus() + "</span>"));
        }
        b.append("<div class=\"card\"><h2>All bookings</h2>")
                .append(Html.table(new String[]{"Ticket", "Passenger", "Bus", "Seat", "Fare (Rs)", "Booked on", "Status"}, ticketRows))
                .append("</div>");

        res.html(Html.page("Admin", user, req.get("msg"), req.get("err"), b.toString()));
    }
}

class AddRouteController extends SecureController {
    public AddRouteController(ServiceRegistry services) {
        super(services, true);
    }

    @Override
    protected void process(WebRequest req, WebResponse res, User user) throws Exception {
        if (!req.isPost()) {
            res.redirect("/admin");
            return;
        }
        try {
            Route r = services.getBusService().addRoute(req.get("id"), req.get("source"),
                    req.get("destination"), req.get("fare"));
            success(res, "/admin", "Route " + r.getId() + " added.");
        } catch (BusReservationException e) {
            failure(res, "/admin", e.getMessage());
        }
    }
}

class AddBusController extends SecureController {
    public AddBusController(ServiceRegistry services) {
        super(services, true);
    }

    @Override
    protected void process(WebRequest req, WebResponse res, User user) throws Exception {
        if (!req.isPost()) {
            res.redirect("/admin");
            return;
        }
        try {
            BusType type;
            try {
                type = BusType.valueOf(req.get("type"));
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("Invalid bus type.");
            }
            Bus bus = services.getBusService().addBus(req.get("id"), req.get("name"), type,
                    req.get("seats"), req.get("routeId"), req.get("date"), req.get("time"));
            success(res, "/admin", "Bus " + bus.getId() + " added.");
        } catch (BusReservationException e) {
            failure(res, "/admin", e.getMessage());
        }
    }
}

/* ---------------------------- Passenger pages ---------------------------- */

class PassengerController extends SecureController {
    public PassengerController(ServiceRegistry services) {
        super(services, false);
    }

    @Override
    protected void process(WebRequest req, WebResponse res, User user) throws Exception {
        Passenger passenger = (Passenger) user;
        String routeId = req.get("route");
        String error = req.get("err");

        StringBuilder b = new StringBuilder();
        b.append("<div class=\"card\"><h2>Find a bus</h2>")
                .append("<form method=\"get\" action=\"/passenger\" class=\"row\">")
                .append("<select name=\"route\"><option value=\"\">All routes</option>");
        for (Route r : services.getBusService().getRoutes()) {
            b.append("<option value=\"").append(Html.esc(r.getId())).append("\"")
                    .append(r.getId().equals(routeId) ? " selected" : "").append(">")
                    .append(Html.esc(r)).append("</option>");
        }
        b.append("</select><input name=\"date\" type=\"date\" value=\"").append(Html.esc(req.get("date")))
                .append("\" title=\"Date (optional)\">")
                .append("<button type=\"submit\">Search</button></form></div>");

        List<List<String>> rows = new ArrayList<>();
        try {
            List<Bus> buses = services.getBusService().searchBuses(
                    routeId.isEmpty() ? null : routeId, req.get("date"));
            for (Bus bus : buses) {
                double fare = services.getBookingService().calculateFare(passenger, bus);
                rows.add(Arrays.asList(Html.esc(bus.getId()), Html.esc(bus.getName()), Html.esc(bus.getType()),
                        Html.esc(services.getBusService().getRouteLabel(bus.getRouteId())),
                        Html.esc(bus.getDepartureDateTime().format(Html.DATE_TIME)),
                        String.valueOf(bus.getAvailableSeats()), String.format("%.2f", fare),
                        "<a class=\"btn\" href=\"/seats?bus=" + Html.url(bus.getId()) + "\">Choose seat</a>"));
            }
        } catch (BusReservationException e) {
            error = e.getMessage();
        }
        b.append("<div class=\"card\"><h2>Available buses</h2>");
        if (passenger.isSenior()) {
            b.append("<p class=\"muted\">Senior discount (20%) is already applied to the fares below.</p>");
        }
        b.append(Html.table(new String[]{"Bus", "Name", "Type", "Route", "Departure", "Seats left", "Fare (Rs)", ""}, rows))
                .append("</div>");

        res.html(Html.page("Find buses", user, req.get("msg"), error, b.toString()));
    }
}

class SeatController extends SecureController {
    public SeatController(ServiceRegistry services) {
        super(services, false);
    }

    @Override
    protected void process(WebRequest req, WebResponse res, User user) throws Exception {
        Passenger passenger = (Passenger) user;
        Bus bus;
        try {
            bus = services.getBusService().getBus(req.get("bus"));
        } catch (BusReservationException e) {
            failure(res, "/passenger", e.getMessage());
            return;
        }
        double fare = services.getBookingService().calculateFare(passenger, bus);

        StringBuilder b = new StringBuilder();
        b.append("<div class=\"card\"><h2>").append(Html.esc(bus.getName())).append(" (")
                .append(Html.esc(bus.getId())).append(")</h2>")
                .append("<p>").append(Html.esc(services.getBusService().getRouteLabel(bus.getRouteId())))
                .append(" &middot; ").append(Html.esc(bus.getType())).append(" &middot; departs ")
                .append(Html.esc(bus.getDepartureDateTime().format(Html.DATE_TIME)))
                .append(" &middot; fare <b>Rs ").append(String.format("%.2f", fare)).append("</b></p>")
                .append("<p class=\"muted\">Green = available, red = booked. Click a green seat to book it.</p>")
                .append("<form method=\"post\" action=\"/book\">")
                .append("<input type=\"hidden\" name=\"bus\" value=\"").append(Html.esc(bus.getId())).append("\">")
                .append("<div class=\"seats\">");
        for (int seat = 1; seat <= bus.getTotalSeats(); seat++) {
            if (bus.isSeatBooked(seat)) {
                b.append("<button type=\"button\" class=\"seat booked\" disabled>").append(seat).append("</button>");
            } else {
                b.append("<button type=\"submit\" name=\"seat\" value=\"").append(seat)
                        .append("\" class=\"seat free\">").append(seat).append("</button>");
            }
        }
        b.append("</div></form><a href=\"/passenger\">&larr; Back to search</a></div>");

        res.html(Html.page("Select seat", user, req.get("msg"), req.get("err"), b.toString()));
    }
}

class BookController extends SecureController {
    public BookController(ServiceRegistry services) {
        super(services, false);
    }

    @Override
    protected void process(WebRequest req, WebResponse res, User user) throws Exception {
        if (!req.isPost()) {
            res.redirect("/passenger");
            return;
        }
        String busId = req.get("bus");
        try {
            int seat = Validator.parseInt(req.get("seat"), "Seat", 1, 1000);
            Ticket t = services.getBookingService().book((Passenger) user, busId, seat);
            success(res, "/tickets", "Booking confirmed! Ticket " + t.getId() + ", seat " + t.getSeatNo()
                    + ", fare Rs " + String.format("%.2f", t.getFare()) + ".");
        } catch (BusReservationException e) {
            res.redirect("/seats?bus=" + Html.url(busId) + "&err=" + Html.url(e.getMessage()));
        }
    }
}

class TicketsController extends SecureController {
    public TicketsController(ServiceRegistry services) {
        super(services, false);
    }

    @Override
    protected void process(WebRequest req, WebResponse res, User user) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        for (Ticket t : services.getBookingService().getTicketsFor(user.getUsername())) {
            String action = t.getStatus() == TicketStatus.CONFIRMED
                    ? "<form method=\"post\" action=\"/cancel\" style=\"margin:0\">"
                    + "<input type=\"hidden\" name=\"ticket\" value=\"" + Html.esc(t.getId()) + "\">"
                    + "<button type=\"submit\" class=\"danger\">Cancel</button></form>"
                    : "";
            rows.add(Arrays.asList(Html.esc(t.getId()), Html.esc(t.getBusId()), String.valueOf(t.getSeatNo()),
                    String.format("%.2f", t.getFare()), Html.esc(t.getBookedOn().format(Html.DATE_TIME)),
                    "<span class=\"tag " + t.getStatus() + "\">" + t.getStatus() + "</span>", action));
        }
        String body = "<div class=\"card\"><h2>My tickets</h2>"
                + Html.table(new String[]{"Ticket", "Bus", "Seat", "Fare (Rs)", "Booked on", "Status", ""}, rows)
                + "</div>";
        res.html(Html.page("My tickets", user, req.get("msg"), req.get("err"), body));
    }
}

class CancelController extends SecureController {
    public CancelController(ServiceRegistry services) {
        super(services, false);
    }

    @Override
    protected void process(WebRequest req, WebResponse res, User user) throws Exception {
        if (!req.isPost()) {
            res.redirect("/tickets");
            return;
        }
        try {
            services.getBookingService().cancel((Passenger) user, req.get("ticket"));
            success(res, "/tickets", "Ticket " + req.get("ticket") + " cancelled.");
        } catch (BusReservationException e) {
            failure(res, "/tickets", e.getMessage());
        }
    }
}

/* ======================================================================
 *                                 WEB SERVER
 * ====================================================================== */

/** Registers every page (controller) against its URL and runs the HTTP server. */
class WebServer {
    private final Map<String, Controller> routes = new HashMap<>();
    private final SessionManager sessions = new SessionManager();

    public WebServer(ServiceRegistry services) {
        routes.put("/", new HomeController());
        routes.put("/login", new LoginController(services, sessions));
        routes.put("/register", new RegisterController(services));
        routes.put("/logout", new LogoutController(sessions));
        routes.put("/admin", new AdminController(services));
        routes.put("/admin/route", new AddRouteController(services));
        routes.put("/admin/bus", new AddBusController(services));
        routes.put("/passenger", new PassengerController(services));
        routes.put("/seats", new SeatController(services));
        routes.put("/book", new BookController(services));
        routes.put("/tickets", new TicketsController(services));
        routes.put("/cancel", new CancelController(services));
    }

    /** Starts on the first free port from startPort upwards and returns the port used. */
    public int start(int startPort) throws IOException {
        IOException last = null;
        for (int port = startPort; port < startPort + 20; port++) {
            try {
                HttpServer server = HttpServer.create(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
                server.createContext("/", this::dispatch);
                server.setExecutor(Executors.newFixedThreadPool(8));
                server.start();
                return port;
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException("No free port found: " + (last == null ? "" : last.getMessage()));
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        WebResponse res = new WebResponse(exchange);
        try {
            WebRequest req = new WebRequest(exchange, sessions);
            String path = exchange.getRequestURI().getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            Controller controller = routes.get(path);
            if (controller == null) {
                res.html(404, Html.page("Not found", req.getUser(), null, null,
                        "<div class=\"card\"><h2>404 - Page not found</h2><a href=\"/\">Go home</a></div>"));
            } else {
                controller.handle(req, res);
            }
        } catch (BusReservationException e) {
            res.html(400, Html.page("Error", null, null, e.getMessage(),
                    "<div class=\"card\"><a href=\"/\">Go home</a></div>"));
        } catch (Exception e) {
            e.printStackTrace();
            res.html(500, Html.page("Error", null, null, "Something went wrong: " + e.getMessage(),
                    "<div class=\"card\"><a href=\"/\">Go home</a></div>"));
        } finally {
            exchange.close();
        }
    }
}
