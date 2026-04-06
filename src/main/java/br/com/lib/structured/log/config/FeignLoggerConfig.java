package br.com.lib.structured.log.config;

import br.com.lib.structured.log.interceptor.LoggingFeignClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignLoggerConfig {

    @Bean
    public Client feignClient() {
        return new LoggingFeignClient(new Client.Default(null, null));
    }
}
