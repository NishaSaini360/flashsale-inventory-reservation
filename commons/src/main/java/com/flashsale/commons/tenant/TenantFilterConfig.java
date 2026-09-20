package com.flashsale.commons.tenant;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;

@Configuration
class TenantFilterWebConfig implements WebMvcConfigurer {

    private final TenantHibernateInterceptor interceptor;

    TenantFilterWebConfig(TenantHibernateInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}

@Component
class TenantHibernateInterceptor implements HandlerInterceptor {

    public static final String FILTER_NAME = "tenantFilter";
    public static final String PARAM = "tenantId";

    private final EntityManager entityManager;

    TenantHibernateInterceptor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String tenantId = TenantContext.get();
        if (tenantId != null) {
            entityManager.unwrap(Session.class)
                    .enableFilter(FILTER_NAME)
                    .setParameter(PARAM, tenantId);
        }
        return true;
    }
}
