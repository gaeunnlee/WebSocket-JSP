<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:if test="${empty sessionScope.loginUser}">
  <c:redirect url="${pageContext.request.contextPath}/login"/>
</c:if>

<h2>방</h2>

<div style="margin:8px 0;">
  <b>현재 입장한 플레이어</b>
  <ul id="playerList">
    <li>불러오는 중...</li>
  </ul>
</div>

<button type="button" id="btnLeave">나가기</button>

<script>
  const ctx = "<c:out value='${pageContext.request.contextPath}'/>";
  const roomId = new URLSearchParams(location.search).get('roomId');
  const playerList = document.getElementById('playerList');

  function escapeHtml(s){
    return String(s ?? "").replace(/[&<>"']/g, (m) => ({
      "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
    }[m]));
  }

  function renderPlayers(players){
    if (!players || players.length === 0) {
      playerList.innerHTML = "<li>입장한 플레이어가 없습니다.</li>";
      return;
    }
    playerList.innerHTML = players.map(p => {
      const color = (p.stoneColor == 1) ? "흑" : (p.stoneColor == 2 ? "백" : "-");
      return "<li>" + escapeHtml(p.nickname) + " (" + color + ")</li>";
    }).join("");
  }


  const roomWsUrl = (location.protocol === "https:" ? "wss://" : "ws://")
    + location.host + ctx + "/ws/room?roomId=" + encodeURIComponent(roomId);
  const wsRoom = new WebSocket(roomWsUrl);

  wsRoom.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.type === "room_players") renderPlayers(msg.players);
    if (msg.type === "room_deleted") {
      alert("방이 삭제되었습니다.");
      location.href = ctx + "/lobby";
    }
  };


  const lobbyWsUrl = (location.protocol === "https:" ? "wss://" : "ws://")
    + location.host + ctx + "/ws/lobby";
  const wsLobby = new WebSocket(lobbyWsUrl);

  document.getElementById('btnLeave').onclick = () => {
    wsLobby.send(JSON.stringify({ type:'leave_room', roomId: roomId }));
    location.href = ctx + "/lobby";
  };
</script>
