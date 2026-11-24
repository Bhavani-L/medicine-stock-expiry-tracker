# 💊 Medicine Stock and Expiry Tracker

A Java console application that helps a pharmacy or medical shop track medicine batches, get warnings about **expired**, **expiring-soon** and **low-stock** medicines, and sell stock safely using **FEFO** (First Expiry, First Out).

## Features

| # | Feature | Description |
|---|---------|-------------|
| 1 | Dashboard | Totals, stock value, today's sales and colored alerts |
| 2 | Add batch | Name, batch number, quantity, price, expiry date, reorder level |
| 3 | View stock | Table sorted by earliest expiry, colored by status |
| 4 | Search | By medicine name or batch number |
| 5 | Sell + bill | FEFO selling, never sells expired stock, prints a bill |
| 6 | Restock | Add units to an existing batch |
| 7 | Expiry report | Expired batches (with loss amount) and expiring in N days |
| 8 | Low stock list | Medicines at or below reorder level, with suggested order quantity |
| 9 | Sales history | All sales and total revenue |
| 10 | Disposal | Remove expired stock and see the loss |
| 11 | Export | Save a text report file |

## Tech stack

- **Language:** Java 11+
- **Libraries:** Java standard library only (`java.time`, `java.util`, `java.io`)
- **Storage:** CSV files (`medicines.csv`, `sales.csv`)
- **Interface:** Console (text menu, ASCII tables, ANSI colors)

## How to run

1. Install [Java JDK 11 or newer](https://adoptium.net) and check with `java -version`.
2. Clone or download this repository.
3. From the project folder, run:

```bash
java src/MedicineTracker.java
```

The first run loads demo data so all features can be tried immediately.
To start with an empty inventory, delete `medicines.csv` and `sales.csv`.

> **Windows note:** colors are turned off automatically in old consoles. Use Windows Terminal, VS Code or IntelliJ to see them.

## Sample output

```
== DASHBOARD  -  2026-11-23 ==
  Medicine batches in stock : 8
  Value of usable stock     : Rs. 3123.00

  ALERTS
  [EXPIRED]  Cough Syrup (batch C303) expired 15 day(s) ago, 25 units
  [EXPIRING] Paracetamol 500mg (batch P102) in 20 day(s), 30 units
  [LOW]      Amoxicillin 250mg: only 8 left (reorder level 20)
```

```
  ============ BILL ============
  Item    : Paracetamol 500mg
  Batch    Expiry       Qty x Rate    = Amount
  P102     2026-12-13     30 x    2.50 =     75.00
  P101     2027-12-28     20 x    2.50 =     50.00
  TOTAL: Rs. 125.00
```

## How FEFO works

If a medicine has two batches, one expiring in 20 days and one in 400 days, a sale takes units from the **20-day batch first**. In the code, `usableBatches()` sorts batches by expiry date and `sellMedicine()` deducts from the first batch, then the next, until the quantity is met. This reduces waste from expired stock.

## Project structure

```
MedicineTracker/
├── src/
│   └── MedicineTracker.java   # the whole application
├── README.md
├── LICENSE
└── .gitignore
```

Inside `MedicineTracker.java` the code is organised in 5 sections: settings, data classes (`Medicine`, `Sale`), `main()` menu loop, one method per feature, and helper methods.

## Java concepts used

Classes and objects, collections (`ArrayList`, `LinkedHashMap`), `LocalDate` date handling, file I/O, exception handling, sorting with `Comparator`, input validation.

## Future improvements

- Database storage (MySQL / SQLite with JDBC)
- Login with admin and pharmacist roles
- Supplier details and purchase orders
- GUI (JavaFX/Swing) or web version (Spring Boot)
- Email/SMS alerts for expiring stock

## License

MIT License. See [LICENSE](LICENSE).
