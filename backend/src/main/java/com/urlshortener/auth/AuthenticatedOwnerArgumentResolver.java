package com.urlshortener.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves an authenticated principal directly into controller methods.
 */
@Component
public class AuthenticatedOwnerArgumentResolver implements HandlerMethodArgumentResolver {

    private final ActiveRequestAuthenticator authenticator;

    public AuthenticatedOwnerArgumentResolver(ActiveRequestAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedOwner.class)
                && AuthenticatedPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("No HttpServletRequest available");
        }
        return authenticator.authenticate(request);
    }
}
