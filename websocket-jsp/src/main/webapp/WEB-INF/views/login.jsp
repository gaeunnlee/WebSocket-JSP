<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<h2>로그인</h2>

<c:if test="${not empty error}">
  <div style="color:red; margin-bottom:8px;">
    ${error}
  </div>
</c:if>

<form method="post" action="${pageContext.request.contextPath}/login">
  <div style="margin-bottom:8px;">
    이메일:
    <input type="email" name="email" required />
  </div>

  <div style="margin-bottom:8px;">
    비밀번호:
    <input type="password" name="password" required />
  </div>

  <button type="submit">로그인</button>
</form>

<hr/>

<!-- 👇 회원가입 링크 -->
<div>
  아직 회원이 아니신가요?
  <a href="${pageContext.request.contextPath}/signup">회원가입</a>
</div>
