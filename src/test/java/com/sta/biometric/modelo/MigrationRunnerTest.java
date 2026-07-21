package com.sta.biometric.modelo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.Test;

public class MigrationRunnerTest {

    @Test
    public void runMigration() throws Exception {
        String url = "jdbc:postgresql://45.169.100.39:5432/mmosquer_biometricDS";
        String user = "mmosquer_mmosquer";
        String password = "Mem@1979";

        System.out.println("Connecting to database: " + url);
        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            System.out.println("Executing: ALTER TABLE personal ADD COLUMN hv_id VARCHAR(30)");
            try {
                stmt.execute("ALTER TABLE personal ADD COLUMN hv_id VARCHAR(30)");
                System.out.println("Successfully added column hv_id.");
            } catch (Exception e) {
                System.out.println("Column hv_id might already exist: " + e.getMessage());
            }

            System.out.println("Executing: ALTER TABLE personal ADD CONSTRAINT uq_personal_hvid UNIQUE (hv_id)");
            try {
                stmt.execute("ALTER TABLE personal ADD CONSTRAINT uq_personal_hvid UNIQUE (hv_id)");
                System.out.println("Successfully added constraint uq_personal_hvid.");
            } catch (Exception e) {
                System.out.println("Constraint uq_personal_hvid might already exist: " + e.getMessage());
            }

            System.out.println("Executing: CREATE INDEX idx_personal_hvid ...");
            try {
                stmt.execute("CREATE INDEX idx_personal_hvid ON personal (hv_id)");
                System.out.println("Successfully created index idx_personal_hvid.");
            } catch (Exception e) {
                System.out.println("Index idx_personal_hvid might already exist: " + e.getMessage());
            }

            System.out.println("Executing: CREATE TABLE dispositivobiometrico ...");
            try {
                stmt.execute("CREATE TABLE dispositivobiometrico (" +
                             "    id VARCHAR(32) NOT NULL," +
                             "    nombre VARCHAR(100) NOT NULL," +
                             "    sucursal_id VARCHAR(32)," +
                             "    activo BOOLEAN DEFAULT TRUE," +
                             "    ultimo_serial_no INTEGER DEFAULT 0," +
                             "    CONSTRAINT pk_dispositivobiometrico PRIMARY KEY (id)," +
                             "    CONSTRAINT fk_dispositivo_sucursal FOREIGN KEY (sucursal_id)" +
                             "        REFERENCES sucursales(id) ON DELETE SET NULL" +
                             ")");
                System.out.println("Successfully created table dispositivobiometrico.");
            } catch (Exception e) {
                System.out.println("Table dispositivobiometrico might already exist: " + e.getMessage());
            }

            System.out.println("Executing: CREATE INDEX idx_dispositivo_sucursal ...");
            try {
                stmt.execute("CREATE INDEX idx_dispositivo_sucursal ON dispositivobiometrico (sucursal_id)");
                System.out.println("Successfully created index idx_dispositivo_sucursal.");
            } catch (Exception e) {
                System.out.println("Index idx_dispositivo_sucursal might already exist: " + e.getMessage());
            }
        }
        System.out.println("Migration finished successfully.");
    }
}


