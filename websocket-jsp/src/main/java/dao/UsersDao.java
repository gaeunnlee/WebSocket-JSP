package dao;

import db.Db;
import model.UserSession;
import util.UuidRaw;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class UsersDao {

    public Optional<UserSession> login(String email, String pwdHash) throws Exception {
        String sql = """
            SELECT u.ID, u.EMAIL, ui.NICKNAME
            FROM USERS u
            JOIN USER_INFO ui ON u.ID = ui.USER_ID
            WHERE u.EMAIL = ?
              AND u.PWD = ?
        """;

        try (Connection con = Db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setString(2, pwdHash);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                UUID userId = UuidRaw.rawToUuid(rs.getBytes("ID"));
                return Optional.of(
                        new UserSession(
                                userId,
                                rs.getString("EMAIL"),
                                rs.getString("NICKNAME")
                        )
                );
            }
        }
    }
}
