import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;



public class HotelReservationSystem {
    private static String url = System.getenv().getOrDefault("DB_URL", "jdbc:sqlite:hotel.db");
    private static String username = System.getenv().getOrDefault("DB_USER", "");
    private static String password = System.getenv().getOrDefault("DB_PASS", "");

    public static void main(String[] args) {
        try {
            loadDriverForUrl(url);
        } catch (ClassNotFoundException e) {
            System.out.println("JDBC Driver not found: " + e.getMessage());
            return;
        }

        Scanner scanner = new Scanner(System.in);
        Connection connection = null;
        try {
            connection = connectWithRetry(scanner);
            if (connection == null) {
                System.out.println("Unable to connect to database. Exiting.");
                return;
            }

            initSchemaIfNeeded(connection);

            while (true) {
                System.out.println();
                System.out.println("HOTEL MANAGEMENT SYSTEM");
                System.out.println("1. Reserve a room");
                System.out.println("2. View Reservations");
                System.out.println("3. Get Room Number");
                System.out.println("4. Update Reservations");
                System.out.println("5. Delete Reservations");
                System.out.println("0. Exit");
                System.out.print("Choose an option: ");
                int choice = readInt(scanner);
                switch (choice) {
                    case 1:
                        reserveRoom(connection, scanner);
                        break;
                    case 2:
                        viewReservations(connection);
                        break;
                    case 3:
                        getRoomNumber(connection, scanner);
                        break;
                    case 4:
                        updateReservation(connection, scanner);
                        break;
                    case 5:
                        deleteReservation(connection, scanner);
                        break;
                    case 0:
                        exit();
                        return;
                    default:
                        System.out.println("Invalid choice. Try again.");
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            try { if (connection != null && !connection.isClosed()) connection.close(); } catch (SQLException ignore) {}
            scanner.close();
        }
    }

    private static Connection connectWithRetry(Scanner scanner) {
        for (int attempts = 0; attempts < 3; attempts++) {
            try {
                if (isSqlite(url)) {
                    return DriverManager.getConnection(url);
                } else {
                    return DriverManager.getConnection(url, username, password);
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                System.out.println("Enter DB credentials (or press Enter to keep current)");
                System.out.print("DB URL [" + url + "]: ");
                String newUrl = scanner.nextLine();
                if (!newUrl.trim().isEmpty()) url = newUrl.trim();
                try { loadDriverForUrl(url); } catch (ClassNotFoundException ignore) {}
                System.out.print("DB Username [" + username + "]: ");
                String newUser = scanner.nextLine();
                if (!newUser.trim().isEmpty()) username = newUser.trim();
                System.out.print("DB Password [hidden]: ");
                String newPass = scanner.nextLine();
                if (!newPass.trim().isEmpty()) password = newPass.trim();
            }
        }
        try {
            if (isSqlite(url)) {
                return DriverManager.getConnection(url);
            } else {
                return DriverManager.getConnection(url, username, password);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    private static boolean isSqlite(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlite:");
    }

    private static void loadDriverForUrl(String jdbcUrl) throws ClassNotFoundException {
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } else if (jdbcUrl.startsWith("jdbc:sqlite:")) {
            Class.forName("org.sqlite.JDBC");
        }
    }

    private static void initSchemaIfNeeded(Connection connection) {
        String ddl = "CREATE TABLE IF NOT EXISTS reservations (" +
                "reservation_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "guest_name VARCHAR(100) NOT NULL, " +
                "room_number INTEGER NOT NULL, " +
                "contact_number VARCHAR(30) NOT NULL, " +
                "reservation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(ddl);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static int readInt(Scanner scanner) {
        while (true) {
            try {
                int value = Integer.parseInt(scanner.nextLine().trim());
                return value;
            } catch (NumberFormatException e) {
                System.out.print("Please enter a valid number: ");
            }
        }
    }

    private static void reserveRoom(Connection connection, Scanner scanner) {
        try {
            System.out.print("Enter guest name: ");
            String guestName = scanner.nextLine().trim();
            System.out.print("Enter room number: ");
            int roomNumber = readInt(scanner);
            System.out.print("Enter contact number: ");
            String contactNumber = scanner.nextLine().trim();

            String sql = "INSERT INTO reservations (guest_name, room_number, contact_number) VALUES (?, ?, ?)";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, guestName);
                ps.setInt(2, roomNumber);
                ps.setString(3, contactNumber);
                int affectedRows = ps.executeUpdate();

                if (affectedRows > 0) {
                    System.out.println("Reservation successful!");
                } else {
                    System.out.println("Reservation failed.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void viewReservations(Connection connection) {
        String sql = "SELECT reservation_id, guest_name, room_number, contact_number, reservation_date FROM reservations";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            System.out.println("Current Reservations:");
            System.out.println("+----------------+-----------------+---------------+----------------------+-------------------------+");
            System.out.println("| Reservation ID | Guest           | Room Number   | Contact Number      | Reservation Date        |");
            System.out.println("+----------------+-----------------+---------------+----------------------+-------------------------+");

            while (resultSet.next()) {
                int reservationId = resultSet.getInt("reservation_id");
                String guestName = resultSet.getString("guest_name");
                int roomNumber = resultSet.getInt("room_number");
                String contactNumber = resultSet.getString("contact_number");
                String reservationDate = resultSet.getTimestamp("reservation_date").toString();

                // Format and display the reservation data in a table-like format
                System.out.printf("| %-14d | %-15s | %-13d | %-20s | %-19s   |\n",
                        reservationId, guestName, roomNumber, contactNumber, reservationDate);
            }

            System.out.println("+----------------+-----------------+---------------+----------------------+-------------------------+");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private static void getRoomNumber(Connection connection, Scanner scanner) {
        try {
            System.out.print("Enter reservation ID: ");
            int reservationId = readInt(scanner);
            System.out.print("Enter guest name: ");
            String guestName = scanner.nextLine().trim();

            String sql = "SELECT room_number FROM reservations WHERE reservation_id = ? AND guest_name = ?";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, reservationId);
                ps.setString(2, guestName);
                try (ResultSet resultSet = ps.executeQuery()) {

                    if (resultSet.next()) {
                        int roomNumber = resultSet.getInt("room_number");
                        System.out.println("Room number for Reservation ID " + reservationId +
                                " and Guest " + guestName + " is: " + roomNumber);
                    } else {
                        System.out.println("Reservation not found for the given ID and guest name.");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private static void updateReservation(Connection connection, Scanner scanner) {
        try {
            System.out.print("Enter reservation ID to update: ");
            int reservationId = readInt(scanner);

            if (!reservationExists(connection, reservationId)) {
                System.out.println("Reservation not found for the given ID.");
                return;
            }

            System.out.print("Enter new guest name: ");
            String newGuestName = scanner.nextLine().trim();
            System.out.print("Enter new room number: ");
            int newRoomNumber = readInt(scanner);
            System.out.print("Enter new contact number: ");
            String newContactNumber = scanner.nextLine().trim();

            String sql = "UPDATE reservations SET guest_name = ?, room_number = ?, contact_number = ? WHERE reservation_id = ?";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, newGuestName);
                ps.setInt(2, newRoomNumber);
                ps.setString(3, newContactNumber);
                ps.setInt(4, reservationId);
                int affectedRows = ps.executeUpdate();

                if (affectedRows > 0) {
                    System.out.println("Reservation updated successfully!");
                } else {
                    System.out.println("Reservation update failed.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void deleteReservation(Connection connection, Scanner scanner) {
        try {
            System.out.print("Enter reservation ID to delete: ");
            int reservationId = readInt(scanner);

            if (!reservationExists(connection, reservationId)) {
                System.out.println("Reservation not found for the given ID.");
                return;
            }

            String sql = "DELETE FROM reservations WHERE reservation_id = ?";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, reservationId);
                int affectedRows = ps.executeUpdate();

                if (affectedRows > 0) {
                    System.out.println("Reservation deleted successfully!");
                } else {
                    System.out.println("Reservation deletion failed.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static boolean reservationExists(Connection connection, int reservationId) {
        try {
            String sql = "SELECT reservation_id FROM reservations WHERE reservation_id = ?";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, reservationId);
                try (ResultSet resultSet = ps.executeQuery()) {
                    return resultSet.next();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false; // Handle database errors as needed
        }
    }


    public static void exit() throws InterruptedException {
        System.out.print("Exiting System");
        int i = 5;
        while(i!=0){
            System.out.print(".");
            Thread.sleep(1000);
            i--;
        }
        System.out.println();
        System.out.println("ThankYou For Using Hotel Reservation System!!!");
    }
}

