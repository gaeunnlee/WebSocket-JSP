package dao;

import db.Db;
import model.RoomDto;
import util.UuidRaw;

import java.sql.*;
import java.util.*;

public class RoomDao {
	public List<RoomDto> listPublicRooms() throws Exception {
		String sql = """
				    SELECT ID, HOST_USER_ID, ROOM_NAME, IS_PUBLIC, PLAY_TYPE,
				           TOTAL_USER_CNT, CURRENT_USER_CNT
				    FROM ROOM
				    WHERE IS_PUBLIC = 1
				    ORDER BY CREATED_AT DESC
				""";

		List<RoomDto> list = new ArrayList<>();

		try (Connection con = Db.getConnection();
				PreparedStatement ps = con.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {

			while (rs.next()) {
				list.add(mapRoom(rs));
			}
		}
		return list;
	}

	public Optional<RoomDto> findRoom(UUID roomId) throws Exception {
		String sql = """
				    SELECT ID, HOST_USER_ID, ROOM_NAME, IS_PUBLIC, PLAY_TYPE,
				           TOTAL_USER_CNT, CURRENT_USER_CNT
				    FROM ROOM
				    WHERE ID = ?
				""";

		try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					return Optional.empty();
				return Optional.of(mapRoom(rs));
			}
		}
	}

	public boolean isHost(UUID roomId, UUID userId) throws Exception {
		String sql = "SELECT COUNT(*) FROM ROOM WHERE ID = ? AND HOST_USER_ID = ?";

		try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
			ps.setBytes(2, UuidRaw.uuidToRaw(userId));

			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1) > 0;
			}
		}
	}
	
	public RoomDto createRoom(UUID hostUserId, String roomName, int isPublic, int playType, int totalUserCnt,
			String pwdHash) throws Exception {

		UUID roomId = UUID.randomUUID();

		String sql = """
				    INSERT INTO ROOM
				      (ID, HOST_USER_ID, ROOM_NAME, IS_PUBLIC, PLAY_TYPE,
				       TOTAL_USER_CNT, CURRENT_USER_CNT, ROOM_PWD_HASH, CREATED_AT)
				    VALUES
				      (?, ?, ?, ?, ?, ?, 0, ?, SYSDATE)
				""";

		try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
			ps.setBytes(2, UuidRaw.uuidToRaw(hostUserId));
			ps.setString(3, roomName);
			ps.setInt(4, isPublic);
			ps.setInt(5, playType);
			ps.setInt(6, totalUserCnt);

			if (pwdHash == null)
				ps.setNull(7, Types.VARCHAR);
			else
				ps.setString(7, pwdHash);

			ps.executeUpdate();
		}

		return RoomDto.builder().id(roomId).hostUserId(hostUserId).roomName(roomName).isPublic(isPublic)
				.playType(playType).totalUserCnt(totalUserCnt).currentUserCnt(0).build();
	}

	public boolean checkRoomPassword(UUID roomId, String pwdHash) throws Exception {
		String sql = "SELECT COUNT(*) FROM ROOM WHERE ID = ? AND ROOM_PWD_HASH = ?";

		try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
			ps.setString(2, pwdHash);

			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1) > 0;
			}
		}
	}

	/**
	 * 방 입장
	 */
	public void enterRoom(UUID roomId, UUID userId, int stoneColor) throws Exception {
		String updCnt = """
				    UPDATE ROOM
				    SET CURRENT_USER_CNT = CURRENT_USER_CNT + 1
				    WHERE ID = ?
				      AND CURRENT_USER_CNT < TOTAL_USER_CNT
				""";

		String insPlayer = """
				    INSERT INTO ROOM_PLAYER (ID, ROOM_ID, USER_ID, JOINED_AT, STONE_COLOR)
				    VALUES (?, ?, ?, SYSDATE, ?)
				""";

		try (Connection con = Db.getConnection()) {
			con.setAutoCommit(false);

			try (PreparedStatement ps1 = con.prepareStatement(updCnt);
					PreparedStatement ps2 = con.prepareStatement(insPlayer)) {

				ps1.setBytes(1, UuidRaw.uuidToRaw(roomId));
				int updated = ps1.executeUpdate();
				if (updated == 0) {
					throw new RuntimeException("ROOM_FULL_OR_NOT_FOUND");
				}

				ps2.setBytes(1, UuidRaw.uuidToRaw(UUID.randomUUID()));
				ps2.setBytes(2, UuidRaw.uuidToRaw(roomId));
				ps2.setBytes(3, UuidRaw.uuidToRaw(userId));
				ps2.setInt(4, stoneColor);
				ps2.executeUpdate();

				con.commit();

			} catch (Exception e) {
				con.rollback();
				throw e;

			} finally {
				con.setAutoCommit(true);
			}
		}
	}

	/**
	 * 방 퇴장
	 */
	public void leaveRoom(UUID roomId, UUID userId) throws Exception {
		String delPlayer = "DELETE FROM ROOM_PLAYER WHERE ROOM_ID = ? AND USER_ID = ?";
		String decCnt = """
				    UPDATE ROOM
				    SET CURRENT_USER_CNT = CURRENT_USER_CNT - 1
				    WHERE ID = ?
				      AND CURRENT_USER_CNT > 0
				""";

		try (Connection con = Db.getConnection()) {
			con.setAutoCommit(false);

			try (PreparedStatement ps1 = con.prepareStatement(delPlayer);
					PreparedStatement ps2 = con.prepareStatement(decCnt)) {

				ps1.setBytes(1, UuidRaw.uuidToRaw(roomId));
				ps1.setBytes(2, UuidRaw.uuidToRaw(userId));
				int deleted = ps1.executeUpdate();

				if (deleted > 0) {
					ps2.setBytes(1, UuidRaw.uuidToRaw(roomId));
					ps2.executeUpdate();
				}

				con.commit();

			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		}
	}

	public void deleteRoom(UUID roomId) throws Exception {
		String delPlayers = "DELETE FROM ROOM_PLAYER WHERE ROOM_ID = ?";
		String delRoom = "DELETE FROM ROOM WHERE ID = ?";

		try (Connection con = Db.getConnection()) {
			con.setAutoCommit(false);

			try (PreparedStatement ps1 = con.prepareStatement(delPlayers);
					PreparedStatement ps2 = con.prepareStatement(delRoom)) {

				ps1.setBytes(1, UuidRaw.uuidToRaw(roomId));
				ps1.executeUpdate();

				ps2.setBytes(1, UuidRaw.uuidToRaw(roomId));
				ps2.executeUpdate();

				con.commit();

			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		}
	}

	public List<Map<String, Object>> listRoomPlayers(UUID roomId) throws Exception {
		String sql = """
				    SELECT rp.USER_ID, ui.NICKNAME, rp.STONE_COLOR
				    FROM ROOM_PLAYER rp
				    JOIN USER_INFO ui ON ui.USER_ID = rp.USER_ID
				    WHERE rp.ROOM_ID = ?
				    ORDER BY rp.JOINED_AT ASC
				""";

		List<Map<String, Object>> list = new ArrayList<>();

		try (Connection con = Db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setBytes(1, UuidRaw.uuidToRaw(roomId));

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					UUID uid = UuidRaw.rawToUuid(rs.getBytes("USER_ID"));
					String nickname = rs.getString("NICKNAME");
					int stoneColor = rs.getInt("STONE_COLOR");

					Map<String, Object> row = new HashMap<>();
					row.put("userId", uid.toString());
					row.put("nickname", nickname);
					row.put("stoneColor", stoneColor);
					list.add(row);
				}
			}
		}
		return list;
	}

	public enum LeaveResultType {
		LEFT, HOST_TRANSFERRED, ROOM_DELETED
	}

	public static class LeaveResult {
		public final LeaveResultType type;
		public final UUID newHostUserId; 

		public LeaveResult(LeaveResultType type, UUID newHostUserId) {
			this.type = type;
			this.newHostUserId = newHostUserId;
		}

		public static LeaveResult left() {
			return new LeaveResult(LeaveResultType.LEFT, null);
		}

		public static LeaveResult hostTransferred(UUID newHost) {
			return new LeaveResult(LeaveResultType.HOST_TRANSFERRED, newHost);
		}

		public static LeaveResult roomDeleted() {
			return new LeaveResult(LeaveResultType.ROOM_DELETED, null);
		}
	}

	public LeaveResult leaveRoomWithHostTransfer(UUID roomId, UUID userId) throws Exception {

		String selHost = "SELECT HOST_USER_ID FROM ROOM WHERE ID = ?";
		String delPlayer = "DELETE FROM ROOM_PLAYER WHERE ROOM_ID = ? AND USER_ID = ?";
		String decCnt = """
				    UPDATE ROOM
				    SET CURRENT_USER_CNT = CURRENT_USER_CNT - 1
				    WHERE ID = ? AND CURRENT_USER_CNT > 0
				""";

		// 호스트 위임
		String selNextHost = """
				    SELECT USER_ID
				    FROM (
				        SELECT USER_ID
				        FROM ROOM_PLAYER
				        WHERE ROOM_ID = ?
				        ORDER BY JOINED_AT ASC
				    )
				    WHERE ROWNUM = 1
				""";

		String updHost = "UPDATE ROOM SET HOST_USER_ID = ? WHERE ID = ?";
		String selCnt = "SELECT CURRENT_USER_CNT FROM ROOM WHERE ID = ?";
		String delRoom = "DELETE FROM ROOM WHERE ID = ?";

		try (Connection con = Db.getConnection()) {
			con.setAutoCommit(false);

			try {
				UUID hostId;
				try (PreparedStatement ps = con.prepareStatement(selHost)) {
					ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
					try (ResultSet rs = ps.executeQuery()) {
						if (!rs.next())
							throw new RuntimeException("ROOM_NOT_FOUND");
						hostId = UuidRaw.rawToUuid(rs.getBytes(1));
					}
				}
				boolean leavingIsHost = hostId.equals(userId);

				int deleted;
				try (PreparedStatement ps = con.prepareStatement(delPlayer)) {
					ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
					ps.setBytes(2, UuidRaw.uuidToRaw(userId));
					deleted = ps.executeUpdate();
				}

				if (deleted == 0) {
					con.commit();
					return LeaveResult.left();
				}

				try (PreparedStatement ps = con.prepareStatement(decCnt)) {
					ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
					ps.executeUpdate();
				}

				int curCnt;
				try (PreparedStatement ps = con.prepareStatement(selCnt)) {
					ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
					try (ResultSet rs = ps.executeQuery()) {
						rs.next();
						curCnt = rs.getInt(1);
					}
				}

				if (curCnt <= 0) {
					try (PreparedStatement ps = con.prepareStatement(delRoom)) {
						ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
						ps.executeUpdate();
					}
					con.commit();
					return LeaveResult.roomDeleted();
				}

				if (leavingIsHost) {
					UUID newHost = null;

					try (PreparedStatement ps = con.prepareStatement(selNextHost)) {
						ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
						try (ResultSet rs = ps.executeQuery()) {
							if (rs.next()) {
								newHost = UuidRaw.rawToUuid(rs.getBytes(1));
							}
						}
					}

					if (newHost != null) {
						try (PreparedStatement ps = con.prepareStatement(updHost)) {
							ps.setBytes(1, UuidRaw.uuidToRaw(newHost));
							ps.setBytes(2, UuidRaw.uuidToRaw(roomId));
							ps.executeUpdate();
						}
						con.commit();
						return LeaveResult.hostTransferred(newHost);
					}
				}

				con.commit();
				return LeaveResult.left();

			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		}
	}

	public RoomDto createRoomAndEnter(UUID hostUserId, String roomName, int isPublic, int playType, int totalUserCnt,
			String pwdHash, int stoneColor) throws Exception {

		UUID roomId = UUID.randomUUID();

		String insRoom = """
				    INSERT INTO ROOM
				      (ID, HOST_USER_ID, ROOM_NAME, IS_PUBLIC, PLAY_TYPE,
				       TOTAL_USER_CNT, CURRENT_USER_CNT, ROOM_PWD_HASH, CREATED_AT)
				    VALUES
				      (?, ?, ?, ?, ?, ?, 1, ?, SYSDATE)
				""";

		String insPlayer = """
				    INSERT INTO ROOM_PLAYER (ID, ROOM_ID, USER_ID, JOINED_AT, STONE_COLOR)
				    VALUES (?, ?, ?, SYSDATE, ?)
				""";

		try (Connection con = Db.getConnection()) {
			con.setAutoCommit(false);

			try (PreparedStatement ps1 = con.prepareStatement(insRoom);
					PreparedStatement ps2 = con.prepareStatement(insPlayer)) {

				ps1.setBytes(1, UuidRaw.uuidToRaw(roomId));
				ps1.setBytes(2, UuidRaw.uuidToRaw(hostUserId));
				ps1.setString(3, roomName);
				ps1.setInt(4, isPublic);
				ps1.setInt(5, playType);
				ps1.setInt(6, totalUserCnt);

				if (pwdHash == null)
					ps1.setNull(7, Types.VARCHAR);
				else
					ps1.setString(7, pwdHash);

				ps1.executeUpdate();

				ps2.setBytes(1, UuidRaw.uuidToRaw(UUID.randomUUID()));
				ps2.setBytes(2, UuidRaw.uuidToRaw(roomId));
				ps2.setBytes(3, UuidRaw.uuidToRaw(hostUserId));
				ps2.setInt(4, stoneColor);
				ps2.executeUpdate();

				con.commit();

			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		}

		return RoomDto.builder().id(roomId).hostUserId(hostUserId).roomName(roomName).isPublic(isPublic)
				.playType(playType).totalUserCnt(totalUserCnt).currentUserCnt(1).build();
	}

	public int enterRoomAutoColor(UUID roomId, UUID userId) throws Exception {

		String selectColor = """
				    SELECT STONE_COLOR
				    FROM ROOM_PLAYER
				    WHERE ROOM_ID = ?
				    FOR UPDATE
				""";

		String updateCnt = """
				    UPDATE ROOM
				    SET CURRENT_USER_CNT = CURRENT_USER_CNT + 1
				    WHERE ID = ?
				      AND CURRENT_USER_CNT < TOTAL_USER_CNT
				""";

		String insertPlayer = """
				    INSERT INTO ROOM_PLAYER (ID, ROOM_ID, USER_ID, JOINED_AT, STONE_COLOR)
				    VALUES (?, ?, ?, SYSDATE, ?)
				""";

		try (Connection con = Db.getConnection()) {
			con.setAutoCommit(false);

			try {
				boolean blackExists = false;

				try (PreparedStatement ps = con.prepareStatement(selectColor)) {
					ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) {
							if (rs.getInt(1) == 1) {
								blackExists = true;
							}
						}
					}
				}

				int myColor = blackExists ? 2 : 1;

				try (PreparedStatement ps = con.prepareStatement(updateCnt)) {
					ps.setBytes(1, UuidRaw.uuidToRaw(roomId));
					if (ps.executeUpdate() == 0) {
						throw new RuntimeException("ROOM_FULL");
					}
				}

				try (PreparedStatement ps = con.prepareStatement(insertPlayer)) {
					ps.setBytes(1, UuidRaw.uuidToRaw(UUID.randomUUID()));
					ps.setBytes(2, UuidRaw.uuidToRaw(roomId));
					ps.setBytes(3, UuidRaw.uuidToRaw(userId));
					ps.setInt(4, myColor);
					ps.executeUpdate();
				}

				con.commit();
				return myColor;

			} catch (Exception e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		}
	}

	private RoomDto mapRoom(ResultSet rs) throws SQLException {
		UUID id = UuidRaw.rawToUuid(rs.getBytes("ID"));
		UUID hostId = UuidRaw.rawToUuid(rs.getBytes("HOST_USER_ID"));

		return RoomDto.builder().id(id).hostUserId(hostId).roomName(rs.getString("ROOM_NAME"))
				.isPublic(rs.getInt("IS_PUBLIC")).playType(rs.getInt("PLAY_TYPE"))
				.totalUserCnt(rs.getInt("TOTAL_USER_CNT")).currentUserCnt(rs.getInt("CURRENT_USER_CNT")).build();
	}

}
