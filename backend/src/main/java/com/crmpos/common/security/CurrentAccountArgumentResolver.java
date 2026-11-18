package com.crmpos.common.security;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 컨트롤러 메서드의 {@link CurrentAccount} 인자를 채운다. 계정을 꺼내는 구현이 없으면 401이다. */
@Component
@RequiredArgsConstructor
public class CurrentAccountArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectProvider<CurrentAccountProvider> provider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentAccount.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        CurrentAccountProvider current = provider.getIfAvailable();
        if (current == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return current.current(webRequest.getNativeRequest(HttpServletRequest.class));
    }
}
