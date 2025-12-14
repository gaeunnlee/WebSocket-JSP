package web;

import dao.UsersDao;
import model.UserSession;
import util.PasswordHash;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Optional;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private final UsersDao usersDao = new UsersDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 이미 로그인 되어 있으면 바로 로비로
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("loginUser") != null) {
            resp.sendRedirect(req.getContextPath() + "/lobby");
            return;
        }

        req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");

        String email = req.getParameter("email");
        String password = req.getParameter("password");

        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            req.setAttribute("error", "이메일과 비밀번호를 입력하세요.");
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
            return;
        }

        try {
            // 비밀번호 해시 (DB에 해시 저장 기준)
            String pwdHash = PasswordHash.sha256(password);

            Optional<UserSession> userOpt = usersDao.login(email, pwdHash);

            if (userOpt.isEmpty()) {
                req.setAttribute("error", "이메일 또는 비밀번호가 올바르지 않습니다.");
                req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
                return;
            }

            // 로그인 성공
            HttpSession session = req.getSession(true);
            session.setAttribute("loginUser", userOpt.get());
            session.setMaxInactiveInterval(30 * 60); // 30분

            // 로비로 이동
            resp.sendRedirect(req.getContextPath() + "/lobby");

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
