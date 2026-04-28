package com.vitbon.kkm.config

import com.vitbon.kkm.domain.service.security.SessionAuthFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SecurityConfig {
    @Bean
    fun sessionAuthFilterRegistration(filter: SessionAuthFilter): FilterRegistrationBean<SessionAuthFilter> {
        return FilterRegistrationBean<SessionAuthFilter>().apply {
            this.filter = filter
            addUrlPatterns("/api/v1/*")
            order = 1
        }
    }
}
