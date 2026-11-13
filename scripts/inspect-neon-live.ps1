$dbUrl = "jdbc:postgresql://ep-young-dew-b4fozts7-pooler.c-6.us-east-2.aws.neon.tech:5432/neondb?sslmode=require&channel_binding=require"
$dbUser = "neondb_owner"
$dbPass = "npg_VcXLOe6N9tSJ"

$jarPath = "C:\Users\adars\.m2\repository\org\postgresql\postgresql\42.7.4\postgresql-42.7.4.jar"
$javaCode = @"
import java.sql.*;

public class NeonInspector {
    public static void main(String[] args) throws Exception {
        String url = "$dbUrl";
        String user = "$dbUser";
        String password = "$dbPass";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== NEON DB CONNECTED ===");
            System.out.println("Database: " + conn.getCatalog());
            System.out.println("User: " + conn.getMetaData().getUserName());
            
            // List tables in public schema
            System.out.println("\n=== TABLES IN PUBLIC SCHEMA ===");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name")) {
                while (rs.next()) {
                    String table = rs.getString("table_name");
                    System.out.println("Table: " + table);
                }
            }

            // Count rows in users, products, orders, order_items
            String[] tables = {"users", "products", "orders", "order_items"};
            for (String t : tables) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + t)) {
                    if (rs.next()) {
                        System.out.println("Row count in " + t + ": " + rs.getInt(1));
                    }
                } catch (Exception e) {
                    System.out.println("Error querying " + t + ": " + e.getMessage());
                }
            }

            // Print users sample
            System.out.println("\n=== USERS SAMPLE ===");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, name, email, role FROM users LIMIT 5")) {
                while (rs.next()) {
                    System.out.println(String.format("User [%d] %s (%s) - %s",
                        rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("role")));
                }
            }

            // Print products sample
            System.out.println("\n=== PRODUCTS SAMPLE ===");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, name, price, stock FROM products LIMIT 5")) {
                while (rs.next()) {
                    System.out.println(String.format("Product [%d] %s - $%s (Stock: %d)",
                        rs.getLong("id"), rs.getString("name"), rs.getString("price"), rs.getInt("stock")));
                }
            }

            // Print orders sample
            System.out.println("\n=== ORDERS SAMPLE ===");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, user_id, status, total_amount FROM orders LIMIT 5")) {
                while (rs.next()) {
                    System.out.println(String.format("Order [%d] User: %d - %s ($%s)",
                        rs.getLong("id"), rs.getLong("user_id"), rs.getString("status"), rs.getString("total_amount")));
                }
            }
        }
    }
}
"@

[System.IO.File]::WriteAllText("NeonInspector.java", $javaCode)
$javac = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\javac.exe"
$java = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr\bin\java.exe"

& $javac -cp $jarPath NeonInspector.java
& $java -cp ".;$jarPath" NeonInspector
Remove-Item NeonInspector.java, NeonInspector.class -ErrorAction SilentlyContinue
