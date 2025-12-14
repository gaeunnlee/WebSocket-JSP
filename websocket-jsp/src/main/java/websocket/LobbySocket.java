package websocket;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import dao.RoomDao;
import model.RoomDto;
import model.UserSession;
import util.PasswordHash;

@ServerEndpoint(value = "/ws/lobby", configurator = LobbySocket.HttpSessionConfigurator.class)
public class LobbySocket {

	private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();
	private static final ObjectMapper om = new ObjectMapper();

	private final RoomDao roomDao = new RoomDao();

	public static class HttpSessionConfigurator extends ServerEndpointConfig.Configurator {
		@Override
		public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
			HttpSession httpSession = (HttpSession) request.getHttpSession();
			if (httpSession != null) {
				sec.getUserProperties().put("HTTP_SESSION", httpSession);
			}
		}
	}

	private HttpSession getHttpSession(Session wsSession) {
		return (HttpSession) wsSession.getUserProperties().get("HTTP_SESSION");
	}

	private UserSession requireLogin(Session wsSession) {
		HttpSession hs = getHttpSession(wsSession);
		if (hs == null)
			throw new RuntimeException("NO_HTTP_SESSION");
		Object obj = hs.getAttribute("loginUser");
		if (obj == null)
			throw new RuntimeException("NOT_LOGGED_IN");
		return (UserSession) obj;
	}

	@OnOpen
	public void onOpen(Session session) {
		sessions.add(session);
		try {
			requireLogin(session);
			sendRoomList(session); // 초기 목록
		} catch (Exception e) {
			safeSend(session, Map.of("type", "error", "message", "로그인이 필요합니다."));
			tryClose(session);
		}
	}

	@OnClose
	public void onClose(Session session) {
		sessions.remove(session);
	}

	@OnError
	public void onError(Session session, Throwable thr) {
		sessions.remove(session);
		// 필요하면 로깅
	}

	@OnMessage
	public void onMessage(Session s, String text) {
		try {
			UserSession loginUser = requireLogin(s);

			@SuppressWarnings("unchecked")
			Map<String, Object> p = om.readValue(text, Map.class);
			String type = asString(p.get("type"));

			if (type == null) {
				safeSend(s, Map.of("type", "error", "message", "type이 없습니다."));
				return;
			}

			switch (type) {

			case "refresh": {
				sendRoomList(s);
				break;
			}

			case "create_room": {
				handleCreateRoom(s, loginUser, p);
				break;
			}

			case "enter_room": {
				handleEnterRoom(s, loginUser, p);
				break;
			}

			case "leave_room": {
				handleLeaveRoom(s, loginUser, p);
				break;
			}

			default:
				safeSend(s, Map.of("type", "error", "message", "지원하지 않는 type: " + type));
			}

		} catch (Exception e) {
			safeSend(s, Map.of("type", "error", "message", "요청 처리 실패: " + e.getMessage()));
		}
	}

	private void handleCreateRoom(Session s, UserSession loginUser, Map<String, Object> p) throws Exception {
		String roomName = asString(p.get("roomName"));
		int isPublic = asInt(p.get("isPublic"), 1);
		int playType = asInt(p.get("playType"), 1);
		int totalUserCnt = asInt(p.get("totalUserCnt"), 2);
		String roomPwd = asStringAllowNull(p.get("roomPwd"));

		if (roomName == null || roomName.isBlank()) {
			safeSend(s, Map.of("type", "error", "message", "방 이름이 비어있습니다."));
			return;
		}
		if (totalUserCnt <= 0) {
			safeSend(s, Map.of("type", "error", "message", "정원은 1 이상이어야 합니다."));
			return;
		}

		String pwdHash = null;
		if (isPublic == 0) {
			if (roomPwd == null || roomPwd.isBlank()) {
				safeSend(s, Map.of("type", "error", "message", "비공개 방은 비밀번호가 필요합니다."));
				return;
			}
			pwdHash = PasswordHash.sha256(roomPwd);
		}

		RoomDto created = roomDao.createRoomAndEnter(loginUser.getId(), roomName, isPublic, playType, totalUserCnt,
				pwdHash, 1 // host는 BLACK
		);

		broadcast(Map.of("type", "room_created", "room", created));
		broadcastRoomList();

		RoomSocket.broadcastPlayers(created.getId());

		safeSend(s, Map.of("type", "create_room_ok", "roomId", created.getId().toString()));

	}

	private void handleEnterRoom(Session s, UserSession loginUser, Map<String, Object> p) throws Exception {
		String roomIdStr = asString(p.get("roomId"));
		if (roomIdStr == null) {
			safeSend(s, Map.of("type", "error", "message", "roomId가 없습니다."));
			return;
		}
		UUID roomId = UUID.fromString(roomIdStr);

		String roomPwd = asStringAllowNull(p.get("roomPwd"));
		if (roomPwd != null && !roomPwd.isBlank()) {
			boolean ok = roomDao.checkRoomPassword(roomId, PasswordHash.sha256(roomPwd));
			if (!ok) {
				safeSend(s, Map.of("type", "error", "message", "비밀번호가 틀렸습니다."));
				return;
			}
		}

		int stoneColor = 1;

		roomDao.enterRoom(roomId, loginUser.getId(), stoneColor);

		broadcastRoomState(roomId);

		RoomSocket.broadcastPlayers(roomId);

		safeSend(s, Map.of("type", "enter_ok", "roomId", roomId.toString()));
	}

	private void handleLeaveRoom(Session s, UserSession loginUser, Map<String, Object> p) throws Exception {
		String roomIdStr = asString(p.get("roomId"));
		if (roomIdStr == null) {
			safeSend(s, Map.of("type", "error", "message", "roomId가 없습니다."));
			return;
		}
		UUID roomId = UUID.fromString(roomIdStr);

		RoomDao.LeaveResult result = roomDao.leaveRoomWithHostTransfer(roomId, loginUser.getId());

		if (result.type == RoomDao.LeaveResultType.ROOM_DELETED) {

			broadcast(Map.of("type", "room_deleted", "roomId", roomId.toString()));
			broadcastRoomList();

			RoomSocket.broadcastRoomDeleted(roomId);

		} else {

			broadcastRoomState(roomId);

			RoomSocket.broadcastPlayers(roomId);

			if (result.type == RoomDao.LeaveResultType.HOST_TRANSFERRED) {

				broadcast(Map.of("type", "host_changed", "roomId", roomId.toString(), "newHostUserId",
						result.newHostUserId.toString()));
			}
		}

		safeSend(s, Map.of("type", "leave_ok", "roomId", roomId.toString()));
	}

	private void sendRoomList(Session s) throws Exception {
		List<RoomDto> rooms = roomDao.listPublicRooms(); // 비공개 제외
		safeSend(s, Map.of("type", "room_list", "rooms", rooms));
	}

	private void broadcastRoomList() throws Exception {
		List<RoomDto> rooms = roomDao.listPublicRooms();
		broadcast(Map.of("type", "room_list", "rooms", rooms));
	}

	private void broadcastRoomState(UUID roomId) throws Exception {
		Optional<RoomDto> roomOpt = roomDao.findRoom(roomId);
		if (roomOpt.isEmpty()) {

			broadcastRoomList();
			return;
		}
		broadcast(Map.of("type", "room_state", "room", roomOpt.get()));
	}

	private void broadcast(Map<String, Object> msg) throws Exception {
		String json = om.writeValueAsString(msg);
		for (Session sess : sessions) {
			if (sess != null && sess.isOpen()) {
				sess.getAsyncRemote().sendText(json);
			}
		}
	}

	private void safeSend(Session s, Map<String, Object> msg) {
		try {
			if (s != null && s.isOpen()) {
				s.getAsyncRemote().sendText(om.writeValueAsString(msg));
			}
		} catch (Exception ignore) {
		}
	}

	private void tryClose(Session s) {
		try {
			if (s != null && s.isOpen())
				s.close();
		} catch (Exception ignore) {
		}
	}

	private String asString(Object v) {
		if (v == null)
			return null;
		return String.valueOf(v);
	}

	private String asStringAllowNull(Object v) {
		if (v == null)
			return null;
		String s = String.valueOf(v);
		return "null".equalsIgnoreCase(s) ? null : s;
	}

	private int asInt(Object v, int def) {
		if (v == null)
			return def;
		if (v instanceof Number)
			return ((Number) v).intValue();
		try {
			return Integer.parseInt(String.valueOf(v));
		} catch (Exception e) {
			return def;
		}
	}
}
