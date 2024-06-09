package coursemaker.coursemaker.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.lang.reflect.Parameter;

@RequiredArgsConstructor
@Component
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        log.info("@@@@@@@@@@@@인터셉터 쌈@뽕 하게 동작중@@@@@@@@@@@@@@@");

        if(HttpMethod.OPTIONS.matches(request.getMethod())) {
            System.out.println("matches");
            return true;
        }

        if (handler instanceof HandlerMethod handlerMethod) {
            System.out.println("handler");
            boolean requiresAuthorization = true;


            if (requiresAuthorization) {
                String accessToken = jwtUtil.getTokenFromRequest(request);

                log.info("[preHandle] accessToken 값 추출 완료, token: {}", accessToken);
                log.info("[preHandle] accessToken 값 유효성 체크 시작");

                if (accessToken != null && jwtTokenProvider.validateToken(accessToken, request)) {
                    log.info("[preHandle] accessToken 값 유효성 체크 완료");
                    return HandlerInterceptor.super.preHandle(request, response, handler);
                } else {
                    log.warn("[preHandle] AccessToken이 만료되었습니다.");
                    return jwtUtil.refreshAuthentication(request, response);
                }
            }
        }
        return true;
    }
}
