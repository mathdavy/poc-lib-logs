package br.com.lib.structured.log.config;

import br.com.lib.structured.log.interceptor.LoggingFeignClient;
import br.com.lib.structured.log.interceptor.TraceIdFeignInterceptor;
import feign.Client;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(Client.class)
public class FeignLoggerConfig {

    @Bean
    public Client feignClient() {
        return new LoggingFeignClient(new Client.Default(null, null));
    }

    /** RequestInterceptor global: o Spring Cloud OpenFeign aplica em todos os @FeignClient. */
    @Bean
    public RequestInterceptor traceIdFeignInterceptor() {
        return new TraceIdFeignInterceptor();
    }
}
