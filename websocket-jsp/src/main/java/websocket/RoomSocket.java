package websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import dao.RoomDao;

import javax.servlet.http.HttpSession;
import javax.websocket.*;
import javax.websocket.server.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(
        value = "/ws/room",
        configurator = RoomSocket.HttpSessionConfigurator.class
)
public class RoomSocket {

    private static final ObjectMapper om = new ObjectMapper();


    private static final Map<UUID, Set<Session>> roomSessions = new ConcurrentHashMap<>();

    public static class HttpSessionConfigurator extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            HttpSession httpSession = (HttpSession) request.getHttpSession();
            if (httpSession != null) sec.getUserProperties().put("HTTP_SESSION", httpSession);
        }
    }

    private static UUID roomIdFromQuery(Session s) {
        String q = s.getQueryString(); // roomId=...
        if (q == null) throw new RuntimeException("NO_QUERY");
        for (String part : q.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                String k = part.substring(0, idx);
                String v = part.substring(idx + 1);
                if ("roomId".equals(k)) return UUID.fromString(v);
            }
        }
        throw new RuntimeException("NO_ROOM_ID");
    }

    @OnOpen
    public void onOpen(Session s) {
        try {
            UUID roomId = roomIdFromQuery(s);
            roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(s);


            sendPlayersToOne(s, roomId);
        } catch (Exception e) {
            safeSend(s, Map.of("type","error","message", e.getMessage()));
            tryClose(s);
        }
    }

    @OnClose
    public void onClose(Session s) {
        roomSessions.values().forEach(set -> set.remove(s));
    }

    @OnError
    public void onError(Session s, Throwable t) {
        roomSessions.values().forEach(set -> set.remove(s));
    }

    @OnMessage
    public void onMessage(Session s, String text) {
        try {
            @SuppressWarnings("unchecked")
            Map<String,Object> p = om.readValue(text, Map.class);
            String type = (String) p.get("type");
            if ("refresh_players".equals(type)) {
                UUID roomId = UUID.fromString(String.valueOf(p.get("roomId")));
                sendPlayersToOne(s, roomId);
            }
        } catch (Exception ignore) {}
    }


    public static void broadcastPlayers(UUID roomId) {
        try {
            RoomDao dao = new RoomDao();
            List<Map<String,Object>> players = dao.listRoomPlayers(roomId);
            broadcastToRoom(roomId, Map.of("type","room_players","roomId",roomId.toString(),"players", players));
        } catch (Exception e) {
       
        }
    }

    public static void broadcastRoomDeleted(UUID roomId) {
        try {
            broadcastToRoom(roomId, Map.of("type","room_deleted","roomId",roomId.toString()));
        } catch (Exception e) {
  
        }
    }


    private static void broadcastToRoom(UUID roomId, Map<String,Object> msg) throws Exception {
        String json = om.writeValueAsString(msg);

        Set<Session> set = roomSessions.getOrDefault(roomId, Collections.emptySet());
        for (Session s : set) {
            if (s != null && s.isOpen()) {
                s.getAsyncRemote().sendText(json);
            }
        }
    
        set.removeIf(ss -> ss == null || !ss.isOpen());
    }

    private void sendPlayersToOne(Session s, UUID roomId) throws Exception {
        RoomDao dao = new RoomDao();
        List<Map<String,Object>> players = dao.listRoomPlayers(roomId);
        safeSend(s, Map.of("type","room_players","roomId",roomId.toString(),"players", players));
    }

    private static void safeSend(Session s, Map<String,Object> msg) {
        try {
            if (s != null && s.isOpen()) s.getAsyncRemote().sendText(om.writeValueAsString(msg));
        } catch (Exception ignore) {}
    }

    private static void tryClose(Session s) {
        try { if (s != null && s.isOpen()) s.close(); } catch (Exception ignore) {}
    }
}
