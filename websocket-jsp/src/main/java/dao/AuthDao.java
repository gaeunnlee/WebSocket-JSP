package dao;

import db.Db;
import model.UserSession;
import util.PasswordHash;
import util.UuidRaw;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class AuthDao {

    // 회원가입: USERS + USER_INFO 같이 저장 (트랜잭션)
    public void signUp(String email, String plainPwd, String nickname) throws Exception {
        UUID uuid = UUID.randomUUID();
        byte[] rawId = UuidRaw.uuidToRaw(uuid);
        String pwdHash = PasswordHash.sha256(plainPwd);

        String insertUsers = """
            INSERT INTO USERS (ID, EMAIL, PWD, CREATED_AT, LOGIN_TYPE)
            VALUES (?, ?, ?, SYSDATE, 1)
        """;

        String insertInfo = """
            INSERT INTO USER_INFO (USER_ID, NICKNAME, TOTAL_WIN, TOTAL_LOSE, COIN)
            VALUES (?, ?, 0, 0, 0)
        """;

        try (Connection con = Db.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps1 = con.prepareStatement(insertUsers);
                 PreparedStatement ps2 = con.prepareStatement(insertInfo)) {

                ps1.setBytes(1, rawId);
                ps1.setString(2, email);
                ps1.setString(3, pwdHash);
                ps1.executeUpdate();

                ps2.setBytes(1, rawId);
                ps2.setString(2, nickname);
                ps2.executeUpdate();

                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    // 로그인: email로 조회 후 비번 해시 비교 + 닉네임 조인해서 세션용 객체 반환
    public Optional<UserSession> login(String email, String plainPwd) throws Exception {
        String pwdHash = PasswordHash.sha256(plainPwd);

        String sql = """
            SELECT u.ID, u.EMAIL, u.PWD, i.NICKNAME
            FROM USERS u
            JOIN USER_INFO i ON i.USER_ID = u.ID
            WHERE u.EMAIL = ?
        """;

        try (Connection con = Db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                String dbHash = rs.getString("PWD");
                if (!pwdHash.equals(dbHash)) return Optional.empty();

                UUID id = UuidRaw.rawToUuid(rs.getBytes("ID"));
                String nickname = rs.getString("NICKNAME");

                return Optional.of(UserSession.builder()
                        .id(id)
                        .email(rs.getString("EMAIL"))
                        .nickname(nickname)
                        .build());
            }
        }
    }
}
