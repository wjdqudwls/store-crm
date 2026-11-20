package com.crmpos.common.security;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.repository.StaffRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 Access 토큰을 확인한다 (AUTH-02, NFR-04c, ADR-025): 서명·만료, 계정이 아직 활성인지, 토큰이 로그인 기준
 * 시각(tokens_valid_after) 뒤에 발급됐는지. 빈으로 등록하지 않고 SecurityConfig가 만든다(서블릿 필터로 두 번 도는 것을 막는다). 비활성화하면 남은 토큰도 바로 막힌다. 토큰이 없으면 그냥 통과시키고
 * (공개 주소일 수 있다) 보호된 주소는 SecurityConfig가 401로 막는다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 필터에서 난 인증 실패를 진입점(401 JSON)에 넘기는 요청 속성. */
    static final String ATTR_ERROR = "crm.auth.error";

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final StaffRepository staffRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            try {
                authenticate(header.substring(BEARER.length()).trim());
            } catch (BusinessException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(ATTR_ERROR, e.getErrorCode());
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        JwtService.ParsedToken parsed = jwtService.parseAccess(token);
        Staff staff = staffRepository.findById(parsed.staffId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        if (!staff.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (parsed.issuedBefore(staff.getTokensValidAfter())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        Long storeId = staff.getStore() == null ? null : staff.getStore().getId();
        CurrentAccount account = new CurrentAccount(staff.getId(), staff.getRole(), storeId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(account, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + staff.getRole().name()))));
    }
}
