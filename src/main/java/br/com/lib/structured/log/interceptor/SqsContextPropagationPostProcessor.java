package br.com.lib.structured.log.interceptor;

import br.com.lib.structured.log.core.LogRequestAttributes;
import br.com.lib.structured.log.core.RequestContextHolderEnum;
import org.springframework.beans.factory.config.BeanPostProcessor;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;

/**
 * Envolve o {@link SqsAsyncClient} para propagar o contexto da lib pela cadeia assíncrona.
 *
 * <p>Problema: no 1º envio a uma fila, o {@code SqsTemplate} faz {@code getQueueUrl} e só
 * depois, num {@code thenCompose}, o {@code sendMessage} — que então roda numa thread do SDK,
 * sem o contexto do fluxo (ou com um contexto antigo herdado via {@code InheritableThreadLocal}).
 *
 * <p>Solução: cada chamada feita numa thread com contexto captura esse contexto e devolve um
 * future que é completado com o contexto restaurado. Assim, os estágios encadeados
 * (inclusive o {@code sendMessage} do {@code SqsTemplate}) enxergam o fluxo correto.
 */
public class SqsContextPropagationPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SqsAsyncClient client && !Proxy.isProxyClass(bean.getClass())) {
            return Proxy.newProxyInstance(
                    SqsAsyncClient.class.getClassLoader(),
                    new Class<?>[]{SqsAsyncClient.class},
                    (proxy, method, args) -> invoke(client, method, args));
        }
        return bean;
    }

    private Object invoke(SqsAsyncClient delegate, Method method, Object[] args) throws Throwable {
        // Captura na thread de quem chamou (ex.: thread do request ou do listener)
        LogRequestAttributes captured = RequestContextHolderEnum.get().getRequestAttributes();

        Object result;
        try {
            result = method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }

        if (captured == null || !(result instanceof CompletableFuture<?> future)) {
            return result;
        }
        return propagate(future, captured);
    }

    private <T> CompletableFuture<T> propagate(CompletableFuture<T> source, LogRequestAttributes captured) {
        CompletableFuture<T> target = new CompletableFuture<>();
        source.whenComplete((value, error) -> {
            RequestContextHolderEnum holder = RequestContextHolderEnum.get();
            LogRequestAttributes previous = holder.getRequestAttributes();
            holder.setRequestAttributes(captured);
            try {
                // dependentes registrados antes da conclusão rodam aqui, com o contexto do fluxo
                if (error != null) {
                    target.completeExceptionally(error);
                } else {
                    target.complete(value);
                }
            } finally {
                if (previous == null) {
                    holder.cleanUp();
                } else {
                    holder.setRequestAttributes(previous);
                }
            }
        });
        return target;
    }
}
