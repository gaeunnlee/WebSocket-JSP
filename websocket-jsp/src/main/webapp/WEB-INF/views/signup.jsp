<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<h2>회원가입</h2>

<c:if test="${not empty error}">
  <p style="color:red">${error}</p>
</c:if>

<form method="post" action="${pageContext.request.contextPath}/signup">
  <div>이메일: <input name="email" type="email" required></div>
  <div>비밀번호: <input name="pwd" type="password" required></div>
  <div>닉네임: <input name="nickname" required></div>
  <button type="submit">가입</button>
</form>

<a href="${pageContext.request.contextPath}/login">로그인</a>
