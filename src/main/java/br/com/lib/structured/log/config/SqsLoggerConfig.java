package br.com.lib.structured.log.config;

import br.com.lib.structured.log.LoggerLifecycle;
import br.com.lib.structured.log.entry.SqsEntryInterceptor;
import br.com.lib.structured.log.interceptor.SqsContextPropagationPostProcessor;
import io.awspring.cloud.sqs.listener.interceptor.MessageInterceptor;
import br.com.lib.structured.log.interceptor.SqsProducerInterceptor;
import io.awspring.cloud.autoconfigure.core.AwsClientCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

/**
 * Registra o {@link SqsProducerInterceptor} no {@link SqsAsyncClient} criado pela
 * autoconfiguração do Spring Cloud AWS (o mesmo client usado pelo {@code SqsTemplate}).
 *
 * <p>Só é ativada se a app tiver Spring Cloud AWS SQS no classpath.
 * Apps que constroem o próprio {@code SqsAsyncClient} podem injetar o bean
 * {@link SqsProducerInterceptor} e registrá-lo via
 * {@code overrideConfiguration(c -> c.addExecutionInterceptor(interceptor))}.
 */
@Configuration
@ConditionalOnClass({SqsAsyncClient.class, AwsClientCustomizer.class})
public class SqsLoggerConfig {

    @Bean
    public SqsProducerInterceptor sqsProducerInterceptor() {
        return new SqsProducerInterceptor();
    }

    /** Entrada via @SqsListener: a autoconfiguração do Spring Cloud AWS registra todo bean MessageInterceptor na factory padrão. */
    @Bean
    public MessageInterceptor<Object> sqsEntryInterceptor(LoggerLifecycle loggerLifecycle) {
        return new SqsEntryInterceptor(loggerLifecycle);
    }

    /** static: BeanPostProcessor precisa ser criado antes dos demais beans da configuração. */
    @Bean
    public static SqsContextPropagationPostProcessor sqsContextPropagationPostProcessor() {
        return new SqsContextPropagationPostProcessor();
    }

    @Bean
    public AwsClientCustomizer<SqsAsyncClientBuilder> sqsProducerLoggingCustomizer(SqsProducerInterceptor interceptor) {
        return new AwsClientCustomizer<>() {
            @Override
            public ClientOverrideConfiguration overrideConfiguration() {
                return ClientOverrideConfiguration.builder()
                        .addExecutionInterceptor(interceptor)
                        .build();
            }
        };
    }
}
