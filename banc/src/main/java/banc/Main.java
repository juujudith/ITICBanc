package banc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;

public class Main {
    public static void main(String[] args) {

        // Carreguem les credencials des del fitxer conn.properties
        Properties prop = new Properties();
        try {
            prop.load(Files.newBufferedReader(Path.of("banc", "src", "main", "resources", "conn.properties")));
        } catch (IOException ex) {
            System.getLogger(Main.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

        HikariConfig hkcfg = new HikariConfig();

        // Parametria de la connexió
        hkcfg.setJdbcUrl(prop.getProperty("DB_URL"));
        hkcfg.setUsername(prop.getProperty("DB_USER"));
        hkcfg.setPassword(prop.getProperty("DB_PASSWORD"));
        hkcfg.setMaximumPoolSize(Integer.parseInt(prop.getProperty("DB_MAX_POOL", "5")));

        // Iniciem el datasource i controlem excepcions
        try (HikariDataSource hkds = new HikariDataSource(hkcfg)) {

            // Connectem la base de dades (el try tanca la connexió sol)
            try (Connection cnn = hkds.getConnection()) {
                System.out.println("Connectat!");

                // Mostrar tots els registres
                for (String resultat : readAllStatements(cnn)) {
                    System.out.println("moviment amb id: " + resultat);
                }

                // Exercici 1: statements amb import >= quantitat
                System.out.println("\n--- Moviments amb import >= 100 ---");
                readStatementsByImport(cnn, 100.0);

                // Exercici 2: inserir un client nou
                System.out.println("\n--- Nou client ---");
                try (Scanner sc = new Scanner(System.in)) {
                    List<String> dades = new ArrayList<>();

                    System.out.print("Nom: ");
                    dades.add(sc.nextLine().trim());
                    System.out.print("Cognoms: ");
                    dades.add(sc.nextLine().trim());
                    System.out.print("Id fiscal: ");
                    dades.add(sc.nextLine().trim());

                    insertClient(cnn, dades);
                }

            } catch (SQLException e) {
                System.out.println("No connectat!");
                e.printStackTrace();
            }

        } catch (PoolInitializationException e) {
            System.out.println("No s'ha pogut connectar a la base de dades: " + e.getMessage());
        }
    }

    // Llegir tots els statements
    public static List<String> readAllStatements(Connection conn) throws SQLException {
        List<String> resultatQuery = new ArrayList<>();
        String query = "SELECT * FROM statements";

        try (PreparedStatement ps = conn.prepareStatement(query);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String resultat = String.join(";",
                        rs.getString("id_statement"),
                        rs.getString("tipus_moviment"),
                        rs.getString("import"),
                        rs.getString("divisa"));
                resultatQuery.add(resultat);
            }
        }
        return resultatQuery;
    }

    // Exercici 1: statements amb import >= quantitat (amb capçalera)
    public static void readStatementsByImport(Connection conn, double quantitat) throws SQLException {
        String query = "SELECT * FROM statements WHERE import >= ?";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, quantitat);

            try (ResultSet rs = ps.executeQuery()) {

                // Capçalera amb el nom dels camps (posicions 1, 3, 8 i 10)
                ResultSetMetaData meta = rs.getMetaData();
                String header = String.join(";",
                        meta.getColumnName(1),
                        meta.getColumnName(3),
                        meta.getColumnName(8),
                        meta.getColumnName(10));
                System.out.println(header);

                while (rs.next()) {
                    String resultat = String.join(";",
                            rs.getString("id_statement"),
                            rs.getString("tipus_moviment"),
                            rs.getString("import"),
                            rs.getString("divisa"));
                    System.out.println("moviment amb id: " + resultat);
                }
            }
        }
    }

    // Exercici 2: comprovar si el client ja existeix
    public static boolean clientExists(Connection conn, String idFiscal) throws SQLException {
        String query = "SELECT 1 FROM clients WHERE id_fiscal = ?";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, idFiscal);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // true si ha trobat alguna fila
            }
        }
    }

    // Exercici 2: inserir client amb commit i rollback
    // dades = [nom, cognoms, id_fiscal]
    public static void insertClient(Connection conn, List<String> dades) {
        String insert = "INSERT INTO clients (nom, cognoms, id_fiscal) VALUES (?, ?, ?)";

        try {
            conn.setAutoCommit(false); // iniciem transacció

            // Validem que no existeix
            if (clientExists(conn, dades.get(2))) {
                System.out.println("El client amb id fiscal " + dades.get(2) + " ja existeix!");
                conn.rollback();
                return;
            }

            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setString(1, dades.get(0));
                ps.setString(2, dades.get(1));
                ps.setString(3, dades.get(2));
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("Client inserit correctament!");

        } catch (SQLException e) {
            System.out.println("Error en la inserció, es fa rollback.");
            e.printStackTrace();
            try {
                conn.rollback(); // desfem els canvis
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                conn.setAutoCommit(true); // deixem la connexió com estava
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}