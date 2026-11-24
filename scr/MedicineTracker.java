import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * MEDICINE STOCK AND EXPIRY TRACKER  (Java console project)
 *
 * What it does:
 *   - Keeps track of medicine batches: quantity, price, expiry date
 *   - Warns about EXPIRED, EXPIRING-SOON and LOW-STOCK medicines
 *   - Sells medicines using FEFO (First Expiry, First Out) and prints a bill
 *   - Remembers everything in CSV files (data is not lost when you close it)
 *   - Exports a report to a text file
 *
 * How to run (Java 11 or newer):   java MedicineTracker.java
 *
 * File layout (read in this order to understand it):
 *   1. Settings          - constants you can change
 *   2. Medicine, Sale    - the two "data" classes
 *   3. main()            - the menu loop
 *   4. Feature methods   - one method per menu option
 *   5. Helper methods    - searching, printing tables, reading input, saving files
 */
public class MedicineTracker {

    // ======================================================================
    // 1. SETTINGS
    // ======================================================================
    static final String STOCK_FILE = "medicines.csv";   // all medicine batches
    static final String SALES_FILE = "sales.csv";       // all sales made
    static final int EXPIRY_WARNING_DAYS = 30;          // "expiring soon" = within 30 days

    // Colors work in most terminals. They are switched off on old Windows consoles.
    static final boolean COLOR = System.getenv("NO_COLOR") == null
            && (!System.getProperty("os.name").toLowerCase().contains("win")
                || System.getenv("WT_SESSION") != null);
    static final String RESET  = COLOR ? "\u001B[0m"  : "";
    static final String BOLD   = COLOR ? "\u001B[1m"  : "";
    static final String RED    = COLOR ? "\u001B[31m" : "";
    static final String GREEN  = COLOR ? "\u001B[32m" : "";
    static final String YELLOW = COLOR ? "\u001B[33m" : "";
    static final String CYAN   = COLOR ? "\u001B[36m" : "";

    // ======================================================================
    // 2. DATA CLASSES
    // ======================================================================

    /** One BATCH of a medicine. The same medicine can have many batches with different expiry dates. */
    static class Medicine {
        int id;
        String name;
        String batch;
        int qty;
        double price;          // price per unit (Rs.)
        LocalDate expiry;
        int reorderLevel;      // warn when total stock of this medicine falls to this level

        Medicine(int id, String name, String batch, int qty, double price,
                 LocalDate expiry, int reorderLevel) {
            this.id = id; this.name = name; this.batch = batch; this.qty = qty;
            this.price = price; this.expiry = expiry; this.reorderLevel = reorderLevel;
        }

        long daysLeft()    { return ChronoUnit.DAYS.between(LocalDate.now(), expiry); }
        boolean isExpired(){ return daysLeft() < 0; }
        boolean isUsable() { return !isExpired() && qty > 0; }   // can be sold

        String status() {
            if (isExpired()) return "EXPIRED";
            if (daysLeft() <= EXPIRY_WARNING_DAYS) return "EXPIRING SOON";
            return "OK";
        }

        String toCsv() {
            return id + "," + name + "," + batch + "," + qty + "," + price + "," + expiry + "," + reorderLevel;
        }

        static Medicine fromCsv(String line) {
            String[] p = line.split(",");
            return new Medicine(Integer.parseInt(p[0]), p[1], p[2], Integer.parseInt(p[3]),
                    Double.parseDouble(p[4]), LocalDate.parse(p[5]), Integer.parseInt(p[6]));
        }
    }

    /** One completed sale (used for the sales history). */
    static class Sale {
        LocalDate date;
        String name;
        int qty;
        double total;

        Sale(LocalDate date, String name, int qty, double total) {
            this.date = date; this.name = name; this.qty = qty; this.total = total;
        }
        String toCsv() { return date + "," + name + "," + qty + "," + total; }
        static Sale fromCsv(String line) {
            String[] p = line.split(",");
            return new Sale(LocalDate.parse(p[0]), p[1], Integer.parseInt(p[2]), Double.parseDouble(p[3]));
        }
    }

    // The whole "database" lives in these two lists while the program runs.
    static List<Medicine> stock = new ArrayList<>();
    static List<Sale> sales = new ArrayList<>();
    static int nextId = 1;
    static final Scanner in = new Scanner(System.in);

    // ======================================================================
    // 3. MAIN - loads data, then repeats: show menu -> do the chosen action
    // ======================================================================
    public static void main(String[] args) {
        loadData();
        System.out.println(BOLD + CYAN);
        System.out.println("  ==============================================");
        System.out.println("     MEDICINE STOCK AND EXPIRY TRACKER");
        System.out.println("  ==============================================" + RESET);
        showDashboard();
        pause();

        while (true) {
            printMenu();
            int choice = readInt("Choose an option: ");
            switch (choice) {
                case 1:  showDashboard();      break;
                case 2:  addMedicine();        break;
                case 3:  viewAllStock();       break;
                case 4:  searchMedicine();     break;
                case 5:  sellMedicine();       break;
                case 6:  restockBatch();       break;
                case 7:  expiryReport();       break;
                case 8:  lowStockReport();     break;
                case 9:  salesHistory();       break;
                case 10: disposeExpired();     break;
                case 11: exportReport();       break;
                case 0:
                    System.out.println(GREEN + "Data saved. Goodbye!" + RESET);
                    return;
                default:
                    System.out.println(RED + "Invalid option, try again." + RESET);
            }
            if (choice != 0) pause();
        }
    }

    static void printMenu() {
        System.out.println("\n" + BOLD + "-------------------- MAIN MENU --------------------" + RESET);
        System.out.println("  1. Dashboard (summary + alerts)");
        System.out.println("  2. Add new medicine batch");
        System.out.println("  3. View all stock (sorted by expiry)");
        System.out.println("  4. Search medicine");
        System.out.println("  5. Sell medicine + print bill   (FEFO)");
        System.out.println("  6. Restock an existing batch");
        System.out.println("  7. Expiry report");
        System.out.println("  8. Low stock / reorder list");
        System.out.println("  9. Sales history");
        System.out.println(" 10. Remove expired stock (disposal)");
        System.out.println(" 11. Export report to text file");
        System.out.println("  0. Exit");
        System.out.println("---------------------------------------------------");
    }

    // ======================================================================
    // 4. FEATURES (one method per menu option)
    // ======================================================================

    /** Option 1: one-screen summary with colored alerts. */
    static void showDashboard() {
        header("DASHBOARD  -  " + LocalDate.now());

        List<Medicine> expired = expiredBatches();
        List<Medicine> soon = expiringWithin(EXPIRY_WARNING_DAYS);
        Map<String, int[]> low = lowStockItems();

        double usableValue = 0, expiredValue = 0;
        for (Medicine m : stock) {
            if (m.isExpired()) expiredValue += m.qty * m.price;
            else usableValue += m.qty * m.price;
        }
        double todaySales = 0;
        for (Sale s : sales) if (s.date.equals(LocalDate.now())) todaySales += s.total;

        System.out.println("  Medicine batches in stock : " + stock.size());
        System.out.println("  Different medicines       : " + distinctNames().size());
        System.out.printf ("  Value of usable stock     : Rs. %.2f%n", usableValue);
        System.out.printf ("  Today's sales             : Rs. %.2f%n", todaySales);
        System.out.println();
        System.out.println("  " + RED    + "Expired batches          : " + expired.size()
                + String.format("  (loss Rs. %.2f)", expiredValue) + RESET);
        System.out.println("  " + YELLOW + "Expiring in " + EXPIRY_WARNING_DAYS + " days       : " + soon.size() + RESET);
        System.out.println("  " + CYAN   + "Low stock medicines      : " + low.size() + RESET);

        if (!expired.isEmpty() || !soon.isEmpty() || !low.isEmpty()) {
            System.out.println("\n  " + BOLD + "ALERTS" + RESET);
            for (Medicine m : expired)
                System.out.println("  " + RED + "[EXPIRED]  " + m.name + " (batch " + m.batch + ") expired "
                        + (-m.daysLeft()) + " day(s) ago, " + m.qty + " units" + RESET);
            for (Medicine m : soon)
                System.out.println("  " + YELLOW + "[EXPIRING] " + m.name + " (batch " + m.batch + ") in "
                        + m.daysLeft() + " day(s), " + m.qty + " units" + RESET);
            for (Map.Entry<String, int[]> e : low.entrySet())
                System.out.println("  " + CYAN + "[LOW]      " + e.getKey() + ": only " + e.getValue()[0]
                        + " left (reorder level " + e.getValue()[1] + ")" + RESET);
        } else {
            System.out.println("\n  " + GREEN + "All good! No alerts." + RESET);
        }
    }

    /** Option 2: add a new batch. */
    static void addMedicine() {
        header("ADD NEW MEDICINE BATCH");
        String name = readText("Medicine name: ");
        String batch = readText("Batch number: ");
        int qty = readInt("Quantity: ", 0, 1000000);
        double price = readDouble("Price per unit (Rs.): ");
        LocalDate expiry = readDate("Expiry date (yyyy-MM-dd): ");
        int reorder = readInt("Reorder level (warn when total stock <= this): ", 0, 1000000);

        Medicine m = new Medicine(nextId++, name, batch, qty, price, expiry, reorder);
        stock.add(m);
        saveData();
        System.out.println(GREEN + "Added with ID " + m.id + "." + RESET);
        if (m.isExpired()) System.out.println(RED + "Warning: this batch is already expired!" + RESET);
    }

    /** Option 3: show everything, earliest expiry first. */
    static void viewAllStock() {
        List<Medicine> sorted = new ArrayList<>(stock);
        sortByExpiry(sorted);
        printTable(sorted, "ALL STOCK (earliest expiry first)");
    }

    /** Option 4: search by name or batch number. */
    static void searchMedicine() {
        header("SEARCH");
        String key = readText("Enter name or batch number: ").toLowerCase();
        List<Medicine> found = new ArrayList<>();
        for (Medicine m : stock)
            if (m.name.toLowerCase().contains(key) || m.batch.toLowerCase().contains(key)) found.add(m);
        sortByExpiry(found);
        printTable(found, "SEARCH RESULTS");
    }

    /**
     * Option 5: SELL using FEFO (First Expiry, First Out).
     * Real pharmacies always sell the batch that expires first, so nothing goes to waste.
     * Expired batches are NEVER sold.
     */
    static void sellMedicine() {
        header("SELL MEDICINE");
        String key = readText("Medicine name (or part of it): ").toLowerCase();

        // Step 1: find which medicines match and still have sellable stock
        List<String> names = new ArrayList<>();
        for (Medicine m : stock)
            if (m.name.toLowerCase().contains(key) && m.isUsable() && !names.contains(m.name))
                names.add(m.name);
        if (names.isEmpty()) {
            System.out.println(RED + "No sellable stock found for that name." + RESET);
            return;
        }
        String name = names.get(0);
        if (names.size() > 1) {
            System.out.println("Multiple matches:");
            for (int i = 0; i < names.size(); i++) System.out.println("  " + (i + 1) + ". " + names.get(i));
            name = names.get(readInt("Pick one: ", 1, names.size()) - 1);
        }

        // Step 2: ask how many
        int available = usableQty(name);
        System.out.println(name + " - available (not expired): " + available);
        int qty = readInt("Quantity to sell: ", 1, available);

        // Step 3: take stock from the batch that expires FIRST, then the next, and so on
        List<Medicine> batches = usableBatches(name);   // already sorted by expiry
        int remaining = qty;
        double total = 0;
        List<String> billLines = new ArrayList<>();
        for (Medicine b : batches) {
            if (remaining == 0) break;
            int take = Math.min(b.qty, remaining);
            b.qty -= take;
            remaining -= take;
            double amount = take * b.price;
            total += amount;
            billLines.add(String.format("  %-8s %-11s %5d x %7.2f = %9.2f", b.batch, b.expiry, take, b.price, amount));
        }

        // Step 4: save the sale and print the bill
        sales.add(new Sale(LocalDate.now(), name, qty, total));
        saveData();

        System.out.println("\n  " + BOLD + "============ BILL ============" + RESET);
        System.out.println("  Date    : " + LocalDate.now());
        System.out.println("  Item    : " + name);
        System.out.println("  Batch    Expiry       Qty x Rate    = Amount");
        for (String line : billLines) System.out.println(line);
        System.out.println("  ------------------------------------");
        System.out.printf ("  " + BOLD + "TOTAL: Rs. %.2f" + RESET + "%n", total);
        System.out.println("  ==================================");

        // Step 5: warn if this sale pushed the medicine below its reorder level
        int[] info = lowStockItems().get(name);
        if (info != null)
            System.out.println(CYAN + "\nAlert: " + name + " is now low (" + info[0] + " left). Please reorder." + RESET);
    }

    /** Option 6: add more units to an existing batch. */
    static void restockBatch() {
        header("RESTOCK");
        int id = readInt("Batch ID: ");
        Medicine m = findById(id);
        if (m == null) { System.out.println(RED + "No batch with that ID." + RESET); return; }
        int add = readInt("Units to add to " + m.name + " (batch " + m.batch + "): ", 1, 1000000);
        m.qty += add;
        saveData();
        System.out.println(GREEN + "Done. New quantity: " + m.qty + RESET);
    }

    /** Option 7: expired and soon-to-expire lists. */
    static void expiryReport() {
        List<Medicine> expired = expiredBatches();
        sortByExpiry(expired);
        printTable(expired, "EXPIRED BATCHES");
        double loss = 0;
        for (Medicine m : expired) loss += m.qty * m.price;
        if (!expired.isEmpty()) System.out.printf(RED + "Total loss from expired stock: Rs. %.2f%n" + RESET, loss);

        int days = readInt("\nShow medicines expiring within how many days? ", 1, 3650);
        List<Medicine> soon = expiringWithin(days);
        sortByExpiry(soon);
        printTable(soon, "EXPIRING WITHIN " + days + " DAYS");
    }

    /**
     * Option 8: low stock is checked per MEDICINE (all batches added together),
     * because 10 units in one batch + 40 in another is 50 units in total.
     */
    static void lowStockReport() {
        header("LOW STOCK / REORDER LIST");
        Map<String, int[]> low = lowStockItems();
        if (low.isEmpty()) { System.out.println(GREEN + "No low stock items." + RESET); return; }
        System.out.printf("  %-24s %10s %10s %15s%n", "Medicine", "In stock", "Reorder at", "Suggested order");
        System.out.println("  " + repeat("-", 63));
        for (Map.Entry<String, int[]> e : low.entrySet()) {
            int have = e.getValue()[0], level = e.getValue()[1];
            int suggest = level * 2 - have;     // simple rule: refill up to 2x the reorder level
            System.out.printf("  %-24s %10d %10d %15d%n", pad(e.getKey(), 24), have, level, suggest);
        }
    }

    /** Option 9: every sale made so far. */
    static void salesHistory() {
        header("SALES HISTORY");
        if (sales.isEmpty()) { System.out.println("No sales yet."); return; }
        System.out.printf("  %-12s %-24s %6s %12s%n", "Date", "Medicine", "Qty", "Amount");
        System.out.println("  " + repeat("-", 58));
        double grand = 0;
        for (Sale s : sales) {
            System.out.printf("  %-12s %-24s %6d %12.2f%n", s.date, pad(s.name, 24), s.qty, s.total);
            grand += s.total;
        }
        System.out.println("  " + repeat("-", 58));
        System.out.printf("  " + BOLD + "Total sales: Rs. %.2f" + RESET + "%n", grand);
    }

    /** Option 10: throw away expired stock. */
    static void disposeExpired() {
        header("REMOVE EXPIRED STOCK");
        List<Medicine> expired = expiredBatches();
        if (expired.isEmpty()) { System.out.println(GREEN + "Nothing expired. Great!" + RESET); return; }
        printTable(expired, "TO BE REMOVED");
        double loss = 0;
        for (Medicine m : expired) loss += m.qty * m.price;
        System.out.printf("Total value being written off: Rs. %.2f%n", loss);
        if (readText("Remove all of these? (y/n): ").equalsIgnoreCase("y")) {
            stock.removeAll(expired);
            saveData();
            System.out.println(GREEN + "Removed " + expired.size() + " batch(es)." + RESET);
        } else {
            System.out.println("Cancelled.");
        }
    }

    /** Option 11: write a plain text report file. */
    static void exportReport() {
        String file = "report-" + LocalDate.now() + ".txt";
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("MEDICINE STOCK REPORT - " + LocalDate.now());
            w.println(repeat("=", 70));
            List<Medicine> sorted = new ArrayList<>(stock);
            sortByExpiry(sorted);
            w.printf("%-4s %-22s %-8s %5s %8s %-11s %-14s%n", "ID", "Name", "Batch", "Qty", "Price", "Expiry", "Status");
            for (Medicine m : sorted)
                w.printf("%-4d %-22s %-8s %5d %8.2f %-11s %-14s%n", m.id, pad(m.name, 22), m.batch,
                        m.qty, m.price, m.expiry, m.status());
            w.println(repeat("-", 70));
            w.println("Expired batches : " + expiredBatches().size());
            w.println("Expiring soon   : " + expiringWithin(EXPIRY_WARNING_DAYS).size());
            w.println("Low stock items : " + lowStockItems().size());
            System.out.println(GREEN + "Report saved as " + file + RESET);
        } catch (IOException e) {
            System.out.println(RED + "Could not write report: " + e.getMessage() + RESET);
        }
    }

    // ======================================================================
    // 5. HELPER METHODS
    // ======================================================================

    // ---------- Searching / filtering the stock list ----------

    static Medicine findById(int id) {
        for (Medicine m : stock) if (m.id == id) return m;
        return null;
    }

    static List<Medicine> expiredBatches() {
        List<Medicine> r = new ArrayList<>();
        for (Medicine m : stock) if (m.isExpired()) r.add(m);
        return r;
    }

    static List<Medicine> expiringWithin(int days) {
        List<Medicine> r = new ArrayList<>();
        for (Medicine m : stock) if (!m.isExpired() && m.daysLeft() <= days) r.add(m);
        return r;
    }

    static List<String> distinctNames() {
        List<String> r = new ArrayList<>();
        for (Medicine m : stock) if (!r.contains(m.name)) r.add(m.name);
        return r;
    }

    /** Sellable batches of one medicine, earliest expiry first (this is what makes it FEFO). */
    static List<Medicine> usableBatches(String name) {
        List<Medicine> r = new ArrayList<>();
        for (Medicine m : stock) if (m.name.equals(name) && m.isUsable()) r.add(m);
        sortByExpiry(r);
        return r;
    }

    static int usableQty(String name) {
        int total = 0;
        for (Medicine m : usableBatches(name)) total += m.qty;
        return total;
    }

    /** name -> {units in stock, reorder level} for every medicine that is running low. */
    static Map<String, int[]> lowStockItems() {
        Map<String, int[]> result = new LinkedHashMap<>();
        for (String name : distinctNames()) {
            int level = 0;
            for (Medicine m : stock) if (m.name.equals(name)) level = Math.max(level, m.reorderLevel);
            int have = usableQty(name);
            if (have <= level) result.put(name, new int[]{have, level});
        }
        return result;
    }

    static void sortByExpiry(List<Medicine> list) {
        Collections.sort(list, new Comparator<Medicine>() {
            public int compare(Medicine a, Medicine b) { return a.expiry.compareTo(b.expiry); }
        });
    }

    // ---------- Printing ----------

    static void header(String title) {
        System.out.println("\n" + BOLD + CYAN + "== " + title + " ==" + RESET);
    }

    static void printTable(List<Medicine> list, String title) {
        header(title);
        if (list.isEmpty()) { System.out.println("  (nothing to show)"); return; }
        int[] w = {4, 22, 8, 6, 9, 10, 5, 13};
        String line = "  +";
        for (int x : w) line += repeat("-", x + 2) + "+";
        System.out.println(line);
        System.out.println(row(w, "ID", "Name", "Batch", "Qty", "Price", "Expiry", "Days", "Status"));
        System.out.println(line);
        for (Medicine m : list) {
            String color = m.isExpired() ? RED : (m.status().equals("EXPIRING SOON") ? YELLOW : GREEN);
            System.out.println(color + row(w, "" + m.id, m.name, m.batch, "" + m.qty,
                    String.format("%.2f", m.price), m.expiry.toString(), "" + m.daysLeft(), m.status()) + RESET);
        }
        System.out.println(line);
    }

    static String row(int[] w, String... cells) {
        StringBuilder sb = new StringBuilder("  |");
        for (int i = 0; i < cells.length; i++) sb.append(" ").append(pad(cells[i], w[i])).append(" |");
        return sb.toString();
    }

    static String pad(String s, int width) {
        if (s.length() > width) s = s.substring(0, width - 1) + "~";
        return String.format("%-" + width + "s", s);
    }

    static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    static void pause() {
        System.out.print("\nPress Enter to continue...");
        in.nextLine();
    }

    // ---------- Reading input safely (keeps asking until the answer is valid) ----------

    static String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = in.nextLine().trim().replace(",", " ");   // commas would break the CSV file
            if (!s.isEmpty()) return s;
            System.out.println(RED + "This cannot be empty." + RESET);
        }
    }

    static int readInt(String prompt) { return readInt(prompt, Integer.MIN_VALUE, Integer.MAX_VALUE); }

    static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int n = Integer.parseInt(in.nextLine().trim());
                if (n >= min && n <= max) return n;
                System.out.println(RED + "Enter a number between " + min + " and " + max + "." + RESET);
            } catch (NumberFormatException e) {
                System.out.println(RED + "Please enter a whole number." + RESET);
            }
        }
    }

    static double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double d = Double.parseDouble(in.nextLine().trim());
                if (d >= 0) return d;
                System.out.println(RED + "Cannot be negative." + RESET);
            } catch (NumberFormatException e) {
                System.out.println(RED + "Please enter a number." + RESET);
            }
        }
    }

    static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return LocalDate.parse(in.nextLine().trim());
            } catch (DateTimeParseException e) {
                System.out.println(RED + "Use the format yyyy-MM-dd, e.g. 2027-03-15." + RESET);
            }
        }
    }

    // ---------- Saving and loading (CSV files) ----------

    static void saveData() {
        try (PrintWriter w = new PrintWriter(new FileWriter(STOCK_FILE))) {
            for (Medicine m : stock) w.println(m.toCsv());
        } catch (IOException e) { System.out.println(RED + "Could not save stock: " + e.getMessage() + RESET); }
        try (PrintWriter w = new PrintWriter(new FileWriter(SALES_FILE))) {
            for (Sale s : sales) w.println(s.toCsv());
        } catch (IOException e) { System.out.println(RED + "Could not save sales: " + e.getMessage() + RESET); }
    }

    static void loadData() {
        File f = new File(STOCK_FILE);
        if (!f.exists()) {            // first run: fill with demo data so the project looks alive
            loadDemoData();
            saveData();
            System.out.println(YELLOW + "First run: demo data loaded. Delete " + STOCK_FILE
                    + " and " + SALES_FILE + " to start fresh." + RESET);
            return;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Medicine m = Medicine.fromCsv(line);
                stock.add(m);
                nextId = Math.max(nextId, m.id + 1);
            }
        } catch (Exception e) { System.out.println(RED + "Problem reading stock file: " + e.getMessage() + RESET); }

        File sf = new File(SALES_FILE);
        if (sf.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(sf))) {
                String line;
                while ((line = r.readLine()) != null)
                    if (!line.trim().isEmpty()) sales.add(Sale.fromCsv(line));
            } catch (Exception e) { System.out.println(RED + "Problem reading sales file: " + e.getMessage() + RESET); }
        }
    }

    /** Dates are relative to today, so the alerts always show something interesting in a demo. */
    static void loadDemoData() {
        LocalDate t = LocalDate.now();
        stock.add(new Medicine(nextId++, "Paracetamol 500mg", "P101", 120, 2.50, t.plusDays(400), 50));
        stock.add(new Medicine(nextId++, "Paracetamol 500mg", "P102", 30,  2.50, t.plusDays(20),  50));
        stock.add(new Medicine(nextId++, "Amoxicillin 250mg", "A210", 8,   12.00, t.plusDays(200), 20));
        stock.add(new Medicine(nextId++, "Cough Syrup",       "C303", 25,  85.00, t.minusDays(15), 10));
        stock.add(new Medicine(nextId++, "Cetirizine 10mg",   "Z055", 60,  3.20, t.plusDays(12),  20));
        stock.add(new Medicine(nextId++, "Vitamin C 500mg",   "V077", 200, 1.50, t.plusDays(500), 50));
        stock.add(new Medicine(nextId++, "Insulin Pen",       "I009", 4,   450.00, t.plusDays(45), 5));
        stock.add(new Medicine(nextId++, "Ibuprofen 400mg",   "B411", 90,  4.00, t.plusDays(300), 30));
    }
}
