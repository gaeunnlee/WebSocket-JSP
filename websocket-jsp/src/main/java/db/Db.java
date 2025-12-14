package db;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;

public class Db {
    private static final DataSource ds;

    static {
        try {
            ds = (DataSource) new InitialContext()
                    .lookup("java:/comp/env/jdbc/oracle");
        } catch (Exception e) {
            throw new RuntimeException("JNDI lookup failed", e);
        }
    }

    public static Connection getConnection() throws Exception {
        return ds.getConnection();
    }
}