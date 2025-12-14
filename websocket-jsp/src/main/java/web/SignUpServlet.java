package web;

import dao.AuthDao;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.IOException;
import java.sql.SQLException;

@WebServlet("/signup")
public class SignUpServlet extends HttpServlet {
    private final AuthDao authDao = new AuthDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/signup.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String email = req.getParameter("email");
        String pwd = req.getParameter("pwd");
        String nickname = req.getParameter("nickname");

        try {
            authDao.signUp(email, pwd, nickname);
            resp.sendRedirect(req.getContextPath() + "/login?signup=success");
        } catch (SQLException e) {
            // ORA-00001: unique constraint (EMAIL/NICKNAME 중복)
            req.setAttribute("error", "회원가입 실패: 이메일/닉네임 중복 또는 DB 오류");
            req.getRequestDispatcher("/WEB-INF/views/signup.jsp").forward(req, resp);
        } catch (Exception e) {
            req.setAttribute("error", "회원가입 실패: 서버 오류");
            req.getRequestDispatcher("/WEB-INF/views/signup.jsp").forward(req, resp);
        }
    }
}
