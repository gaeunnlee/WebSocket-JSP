<%@ page contentType="text/html; charset=UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<c:if test="${empty sessionScope.loginUser}">
	<c:redirect url="${pageContext.request.contextPath}/login" />
</c:if>

<h2>로비</h2>

<div style="display: flex; gap: 12px; align-items: center;">
	<div style="display: flex; gap: 12px; align-items: center;">
		<div>로그인: ${sessionScope.loginUser.nickname}
			(${sessionScope.loginUser.email})</div>

		<button id="btnOpenModal" type="button">방 생성</button>
		<form action="${pageContext.request.contextPath}/logout" method="get"
			style="margin: 0;">
			<button type="submit">로그아웃</button>
		</form>
	</div>

</div>

<hr />

<table border="1" cellpadding="8" cellspacing="0"
	style="width: 100%; max-width: 900px;">
	<thead>
		<tr>
			<th>방 이름</th>
			<th>공개</th>
			<th>타입</th>
			<th>인원</th>
			<th>입장</th>
		</tr>
	</thead>
	<tbody id="roomTbody">
		<tr>
			<td colspan="5">불러오는 중...</td>
		</tr>
	</tbody>
</table>

<!-- 모달 -->
<div id="modalBackdrop"
	style="display: none; position: fixed; inset: 0; background: rgba(0, 0, 0, .35);">
	<div
		style="background: white; width: 360px; margin: 120px auto; padding: 16px; border-radius: 10px;">
		<h3>방 생성</h3>

		<div style="margin: 8px 0;">
			방 이름: <input id="roomName" style="width: 100%;"
				placeholder="예: 오목 한 판?" />
		</div>

		<div style="margin: 8px 0;">
			공개 여부: <select id="isPublic" style="width: 100%;">
				<option value="1">공개</option>
				<option value="0">비공개</option>
			</select>
		</div>

		<!-- ✅ 비공개일 때만 쓰는 비밀번호 -->
		<div style="margin: 8px 0;">
			비밀번호(비공개일 때): <input id="roomPwd" type="password"
				style="width: 100%;" placeholder="비공개 방 비밀번호" />
		</div>

		<div style="margin: 8px 0;">
			플레이 타입: <select id="playType" style="width: 100%;">
				<option value="1">개인전</option>
				<option value="0">팀전</option>
			</select>
		</div>

		<div style="margin: 8px 0;">
			정원: <input id="totalUserCnt" type="number" min="2" max="10" value="2"
				style="width: 100%;" />
		</div>

		<div
			style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 12px;">
			<button type="button" id="btnCloseModal">취소</button>
			<button type="button" id="btnCreateRoom">생성</button>
		</div>
	</div>
</div>

<script>
  const ctx = "${pageContext.request.contextPath}";
  const tbody = document.getElementById("roomTbody");
  const modal = document.getElementById("modalBackdrop");

  console.log("tbody element:", tbody);
  
  function openModal(){ modal.style.display = "block"; }
  function closeModal(){ modal.style.display = "none"; }

  document.getElementById("btnOpenModal").addEventListener("click", openModal);
  document.getElementById("btnCloseModal").addEventListener("click", closeModal);
  modal.addEventListener("click", (e) => { if (e.target === modal) closeModal(); });


  function renderRooms(rooms){
	  if (!tbody) return;

	  if (!rooms || rooms.length === 0) {
	    tbody.innerHTML = `<tr><td colspan="5">현재 생성된 공개 방이 없습니다.</td></tr>`;
	    return;
	  }
	  

  tbody.innerHTML = rooms.map(r => `
	    <tr>
	      <td>\${r.roomName}</td>
	      <td>\${r.isPublic == 1 ? '공개' : '비공개'}</td>
	      <td>\${r.playType == 1 ? '개인전' : '팀전'}</td>
	      <td>\${r.currentUserCnt}/\${r.totalUserCnt}</td>
	      <td><button type="button" onclick="enter('\${r.id}', \${r.isPublic})">입장</button></td>
	    </tr>
	  `).join('');
	}


  function enter(roomId, isPublic){
    if (isPublic == 0) {
      const pwd = prompt("비공개 방 비밀번호를 입력하세요");
      if (pwd == null) return;
      ws.send(JSON.stringify({ type:'enter_room', roomId, roomPwd: pwd }));
    } else {
      ws.send(JSON.stringify({ type:'enter_room', roomId }));
    }
    location.href = ctx + '/room?roomId=' + encodeURIComponent(roomId);
  }

  const wsUrl = (location.protocol === "https:" ? "wss://" : "ws://")
    + location.host + ctx + "/ws/lobby";
  const ws = new WebSocket(wsUrl);

  ws.onopen = () => {
    ws.send(JSON.stringify({ type: "refresh" }));
  };

  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);

    if (msg.type === "room_list") {
    	console.log("renderRooms called", msg.rooms);
    	renderRooms(msg.rooms);
    }

    if (msg.type === "room_created" || msg.type === "room_deleted" || msg.type === "room_state") {
      ws.send(JSON.stringify({ type: "refresh" }));
    }
    

    if (msg.type === "create_room_ok") {
      location.href = ctx + "/room?roomId=" + encodeURIComponent(msg.roomId);
      return;
    }

    if (msg.type === "room_created") {
      // 필요하면 refresh 요청 (이미 서버가 room_list도 뿌려주니까 생략 가능)
    }

    if (msg.type === "error") alert(msg.message || "에러");
  };

  ws.onerror = () => {
    alert("웹소켓 연결 오류. 로그인 상태/서버 로그를 확인해줘.");
  };

  // 방 생성
  document.getElementById("btnCreateRoom").addEventListener("click", () => {
    const roomName = document.getElementById("roomName").value.trim();
    const isPublic = parseInt(document.getElementById("isPublic").value, 10);
    const playType = parseInt(document.getElementById("playType").value, 10);
    const totalUserCnt = parseInt(document.getElementById("totalUserCnt").value, 10);
    const roomPwd = document.getElementById("roomPwd").value;

    if (!roomName) { alert("방 이름을 입력해주세요"); return; }


    if (isPublic === 0 && (!roomPwd || roomPwd.trim().length === 0)) {
      alert("비밀번호가 필요해요");
      return;
    }


    ws.send(JSON.stringify({
      type: "create_room",
      roomName,
      isPublic,
      playType,
      totalUserCnt,
      roomPwd: isPublic === 0 ? roomPwd : null
    }));

    closeModal();
  });
</script>
