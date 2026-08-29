package com.project.web.config;

import com.project.web.filter.TraceIdFilter;
import com.project.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * it is used to  Makes this module work by just adding the dependency - no configuration in the services.
 *
 * <h2>Why this class is necessary</h2>
 * <p>Spring only component-scans packages under the {@code @SpringBootApplication} class.
 * Every service in this project lives under {@code com.example.*}; this module lives under
 * {@code com.project.web}. So {@code @RestControllerAdvice} on {@code GlobalExceptionHandler}
 * would be completely invisible - the class would sit on the classpath doing nothing, and
 * errors would silently keep returning 500 HTML pages.</p>
 *
 * <p>The fix is Spring Boot's auto-configuration mechanism: this class is listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * which tells Boot to load it regardless of package. It then registers the beans explicitly.
 * {@code common-security} already uses exactly this trick for its HeaderAuthFilter.</p>
 *
 * <p>{@code @ConditionalOnWebApplication(type = SERVLET)} is a safety belt: if this module is
 * ever added to a reactive app (like APIGateway) by mistake, it quietly does nothing instead
 * of breaking startup.</p>
 *
 * <p>{@code @ConditionalOnMissingBean} means With @ConditionalOnMissingBean, Spring checks:
 * "Does the application already have its own GlobalExceptionHandler bean defined?"
 *
 * If yes → Spring skips creating the shared one. The service's own custom handler wins.
 * If no → Spring falls back to creating the shared default handler. fro that service</p>
 * without using {@code @ConditionalOnMissingBean} each sevice
 * either need to Accept the shared handler even if  service already have its own or want to use something different.
 * or it have to Exclude the whole module just to disable that one bean (overkill).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfig {

    /**
     * Registered as a plain @Bean rather than found by scanning.
     * Spring's exception-resolving machinery discovers @ControllerAdvice beans by looking
     * through the application context, so how the bean got there does not matter.
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * Normally, just adding @Bean on a Traceid filter is enough .
     * But we wraps it into  FilterRegistrationBean to gain extra control (specifically: ordering.HIGHEST_PRECEDENCE  and apply to all the requests).
     *odering.HIGHEST_PRECEDENCE :means run it before everything else otherwise early log lines have no Traceid.
     */
    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
