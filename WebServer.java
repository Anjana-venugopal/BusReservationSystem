import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Read-only web viewer for the Bharat Yatra bus reservation database.
 *
 * Compile (both files together, same folder):
 *     javac BusReservationGUI.java WebServer.java
 * Run (from the SAME folder that contains bharat_yatra_graphic_db.dat):
 *     java WebServer            -> http://localhost:8080
 *     java WebServer 9000       -> http://localhost:9000
 *
 * Pages:
 *     /           Dashboard (stats, users, routes, buses + seat maps, tickets)
 *     /api/data   Same data as JSON
 *
 * The database is re-read from disk on every request, so bookings made in the
 * Swing app show up here after a refresh (the page also auto-refreshes).
 */
public class WebServer {

    private static final String DB_FILE = "bharat_yatra_graphic_db.dat";

    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", WebServer::handleDashboard);
        server.createContext("/api/data", WebServer::handleJson);
        server.setExecutor(null);
        server.start();

        System.out.println("Bharat Yatra DB Viewer running at http://localhost:" + port);
        System.out.println("Reading: " + new File(DB_FILE).getAbsolutePath());
        System.out.println("Press Ctrl+C to stop.");
    }

    /* ----------------------------- helpers ----------------------------- */

    private static IDatabaseRepository freshDb() {
        FileDatabase db = FileDatabase.getInstance();
        db.loadFromDisk(); // pick up changes made by the Swing app
        return db;
    }

    private static void send(HttpExchange ex, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String js(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String hex(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /* --------------------------- JSON endpoint -------------------------- */

    private static void handleJson(HttpExchange ex) throws IOException {
        IDatabaseRepository db = freshDb();
        StringBuilder sb = new StringBuilder("{\n");

        sb.append("  \"users\": [");
        List<User> users = db.getAllUsers();
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            sb.append(i > 0 ? "," : "").append("\n    {\"username\":").append(js(u.getUsername()))
              .append(",\"fullName\":").append(js(u.getFullName()))
              .append(",\"role\":").append(js(u.getRole())).append("}");
        }
        sb.append("\n  ],\n  \"routes\": [");
        List<Route> routes = db.getAllRoutes();
        for (int i = 0; i < routes.size(); i++) {
            Route r = routes.get(i);
            sb.append(i > 0 ? "," : "").append("\n    {\"routeId\":").append(js(r.getRouteId()))
              .append(",\"origin\":").append(js(r.getOrigin()))
              .append(",\"destination\":").append(js(r.getDestination()))
              .append(",\"distanceKm\":").append(r.getDistanceKm())
              .append(",\"departure\":").append(js(r.getDepartureTime()))
              .append(",\"arrival\":").append(js(r.getArrivalTime()))
              .append(",\"platform\":").append(js(r.getBoardingPlatform())).append("}");
        }
        sb.append("\n  ],\n  \"buses\": [");
        List<Bus> buses = db.getAllBuses();
        for (int i = 0; i < buses.size(); i++) {
            Bus b = buses.get(i);
            sb.append(i > 0 ? "," : "").append("\n    {\"busId\":").append(js(b.getBusId()))
              .append(",\"registration\":").append(js(b.getRegistrationNumber()))
              .append(",\"service\":").append(js(b.getBusServiceType()))
              .append(",\"routeId\":").append(js(b.getRoute().getRouteId()))
              .append(",\"totalSeats\":").append(b.getSeats().size())
              .append(",\"availableSeats\":").append(b.getAvailableSeatCount())
              .append(",\"baseFare\":").append((int) b.calculateBaseFare())
              .append(",\"totalFareWithGst\":").append((int) b.calculateTotalFareWithGst()).append("}");
        }
        sb.append("\n  ],\n  \"tickets\": [");
        List<Ticket> tickets = db.getAllTickets();
        for (int i = 0; i < tickets.size(); i++) {
            Ticket t = tickets.get(i);
            sb.append(i > 0 ? "," : "").append("\n    {\"pnr\":").append(js(t.getPnrNumber()))
              .append(",\"busId\":").append(js(t.getBusId()))
              .append(",\"seat\":").append(t.getSeatNumber())
              .append(",\"passenger\":").append(js(t.getPassengerName()))
              .append(",\"username\":").append(js(t.getPassengerUsername()))
              .append(",\"mobile\":").append(js(t.getPassengerMobile()))
              .append(",\"fare\":").append((int) t.getFare())
              .append(",\"bookedOn\":").append(js(t.getBookingDate()))
              .append(",\"active\":").append(t.isActive()).append("}");
        }
        sb.append("\n  ]\n}\n");

        send(ex, 200, "application/json", sb.toString());
    }

    /* ----------------------------- Dashboard ---------------------------- */

    private static void handleDashboard(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) {
            send(ex, 404, "text/plain", "Not found");
            return;
        }

        IDatabaseRepository db = freshDb();

        List<User> users = db.getAllUsers();
        List<Route> routes = db.getAllRoutes();
        List<Bus> buses = db.getAllBuses();
        List<Ticket> tickets = db.getAllTickets();

        users.sort(Comparator.comparing(User::getUsername));
        routes.sort(Comparator.comparing(Route::getRouteId));
        buses.sort(Comparator.comparing(Bus::getBusId));
        tickets.sort(Comparator.comparing(Ticket::getPnrNumber));

        int activeTickets = 0;
        double revenue = 0;
        for (Ticket t : tickets) {
            if (t.isActive()) {
                activeTickets++;
                revenue += t.getFare();
            }
        }

        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
         .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
         .append("<meta http-equiv='refresh' content='5'>")
         .append("<title>Bharat Yatra - Database Viewer</title><style>")
         .append("*{box-sizing:border-box}")
         .append("body{margin:0;font-family:'Segoe UI',Arial,sans-serif;background:#f5f7fa;color:#212529}")
         .append("header{background:linear-gradient(90deg,#0f2043,#1e50a0);color:#fff;padding:18px 28px;border-top:4px solid #ff7a00}")
         .append("header h1{margin:0;font-size:20px}header p{margin:4px 0 0;color:#cfd8ea;font-size:13px}")
         .append("main{max-width:1200px;margin:0 auto;padding:20px}")
         .append(".stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:14px;margin-bottom:22px}")
         .append(".stat{background:#fff;border:1px solid #e1e6ee;border-radius:12px;padding:16px}")
         .append(".stat b{display:block;font-size:26px;color:#0f2043}.stat span{font-size:12px;color:#6c757d;text-transform:uppercase;letter-spacing:.5px}")
         .append("section{background:#fff;border:1px solid #e1e6ee;border-radius:12px;padding:18px;margin-bottom:22px;overflow-x:auto}")
         .append("h2{margin:0 0 12px;font-size:16px;color:#182b49}")
         .append("table{width:100%;border-collapse:collapse;font-size:13px}")
         .append("th{background:#182b49;color:#fff;text-align:left;padding:9px 10px}")
         .append("td{padding:8px 10px;border-bottom:1px solid #edf0f5}tr:hover td{background:#f8fafc}")
         .append(".pill{padding:2px 9px;border-radius:10px;color:#fff;font-size:11px;font-weight:600}")
         .append(".ok{background:#28a745}.bad{background:#dc3545}")
         .append(".bus{border:1px solid #e1e6ee;border-radius:10px;padding:14px;margin-bottom:14px}")
         .append(".bus h3{margin:0 0 4px;font-size:14px}.bus small{color:#6c757d}")
         .append(".seats{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}")
         .append(".seat{width:38px;height:34px;border-radius:7px;color:#fff;font-size:12px;font-weight:700;display:flex;align-items:center;justify-content:center}")
         .append(".free{background:#17a2b8}.taken{background:#dc3545}")
         .append(".legend span{margin-right:16px;font-size:12px}.legend i{display:inline-block;width:12px;height:12px;border-radius:3px;margin-right:5px;vertical-align:middle}")
         .append("footer{text-align:center;color:#6c757d;font-size:12px;padding:10px 0 24px}")
         .append("a{color:#1e50a0}")
         .append("</style></head><body>");

        h.append("<header><h1>BHARAT YATRA &mdash; Database Viewer</h1>")
         .append("<p>Read-only view of <code>").append(esc(DB_FILE)).append("</code> &middot; auto-refreshes every 5 seconds &middot; ")
         .append("<a style='color:#ffd700' href='/api/data'>JSON</a></p></header><main>");

        if (!new File(DB_FILE).exists()) {
            h.append("<section><h2>No database found</h2><p>Could not find <code>")
             .append(esc(new File(DB_FILE).getAbsolutePath()))
             .append("</code>.<br>Run the Swing app once (<code>java BusReservationGUI</code>) so it creates the file, ")
             .append("and start this server from the same folder.</p></section></main></body></html>");
            send(ex, 200, "text/html", h.toString());
            return;
        }

        // Stats
        h.append("<div class='stats'>")
         .append(stat(users.size(), "Registered Users"))
         .append(stat(routes.size(), "Routes"))
         .append(stat(buses.size(), "Buses"))
         .append(stat(activeTickets + " / " + tickets.size(), "Active / Total Tickets"))
         .append(stat("INR " + (int) revenue, "Revenue (Active Tickets)"))
         .append("</div>");

        // Users (passwords are never shown)
        h.append("<section><h2>Users</h2><table><tr><th>Username</th><th>Full Name</th><th>Role</th></tr>");
        for (User u : users) {
            h.append("<tr><td>").append(esc(u.getUsername())).append("</td><td>")
             .append(esc(u.getFullName())).append("</td><td>").append(esc(u.getRole())).append("</td></tr>");
        }
        h.append("</table></section>");

        // Routes
        h.append("<section><h2>Routes</h2><table><tr><th>Route ID</th><th>Origin</th><th>Destination</th>")
         .append("<th>Distance</th><th>Departure</th><th>Arrival</th><th>Platform</th></tr>");
        for (Route r : routes) {
            h.append("<tr><td>").append(esc(r.getRouteId())).append("</td><td>").append(esc(r.getOrigin()))
             .append("</td><td>").append(esc(r.getDestination())).append("</td><td>").append((int) r.getDistanceKm())
             .append(" km</td><td>").append(esc(r.getDepartureTime())).append("</td><td>").append(esc(r.getArrivalTime()))
             .append("</td><td>").append(esc(r.getBoardingPlatform())).append("</td></tr>");
        }
        h.append("</table></section>");

        // Buses with seat maps
        h.append("<section><h2>Buses &amp; Live Seat Maps</h2>")
         .append("<div class='legend'><span><i style='background:#17a2b8'></i>Available</span>")
         .append("<span><i style='background:#dc3545'></i>Booked</span></div><br>");
        for (Bus b : buses) {
            Route r = b.getRoute();
            h.append("<div class='bus' style='border-left:5px solid ").append(hex(b.getThemeColor())).append("'>")
             .append("<h3>").append(esc(b.getBusId())).append(" &mdash; ").append(esc(b.getBusServiceType())).append("</h3>")
             .append("<small>Reg: ").append(esc(b.getRegistrationNumber())).append(" &middot; ")
             .append(esc(r.getOrigin())).append(" &rarr; ").append(esc(r.getDestination()))
             .append(" &middot; Dept ").append(esc(r.getDepartureTime()))
             .append(" &middot; Fare INR ").append((int) b.calculateTotalFareWithGst()).append(" (incl. 5% GST)")
             .append(" &middot; <b>").append(b.getAvailableSeatCount()).append("/").append(b.getSeats().size())
             .append(" seats free</b></small><div class='seats'>");
            List<Integer> nums = new ArrayList<>(b.getSeats().keySet());
            Collections.sort(nums);
            for (int n : nums) {
                boolean booked = b.getSeats().get(n).isBooked();
                h.append("<div class='seat ").append(booked ? "taken" : "free").append("'>")
                 .append(booked ? "X" : String.valueOf(n)).append("</div>");
            }
            h.append("</div></div>");
        }
        h.append("</section>");

        // Tickets
        h.append("<section><h2>Tickets</h2><table><tr><th>PNR</th><th>Bus</th><th>Seat</th><th>Passenger</th>")
         .append("<th>Mobile</th><th>Fare</th><th>Booked On</th><th>Status</th></tr>");
        if (tickets.isEmpty()) {
            h.append("<tr><td colspan='8'>No tickets booked yet.</td></tr>");
        }
        for (Ticket t : tickets) {
            h.append("<tr><td>").append(esc(t.getPnrNumber())).append("</td><td>").append(esc(t.getBusId()))
             .append("</td><td>").append(t.getSeatNumber()).append("</td><td>").append(esc(t.getPassengerName()))
             .append("</td><td>").append(esc(t.getPassengerMobile())).append("</td><td>INR ").append((int) t.getFare())
             .append("</td><td>").append(esc(t.getBookingDate())).append("</td><td><span class='pill ")
             .append(t.isActive() ? "ok'>CONFIRMED" : "bad'>CANCELLED").append("</span></td></tr>");
        }
        h.append("</table></section>");

        h.append("</main><footer>Bharat Yatra Luxury ERP &middot; Database Viewer</footer></body></html>");
        send(ex, 200, "text/html", h.toString());
    }

    private static String stat(Object value, String label) {
        return "<div class='stat'><b>" + esc(String.valueOf(value)) + "</b><span>" + esc(label) + "</span></div>";
    }
}
