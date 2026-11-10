package br.com.lib.structured.log.entry;

import br.com.lib.structured.log.LoggerLifecycle;
import br.com.lib.structured.log.core.LogRequestAttributes;
import br.com.lib.structured.log.core.RequestContextHolderEnum;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.utils.Constants;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.interceptor.MessageInterceptor;
import org.springframework.messaging.Message;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static br.com.lib.structured.log.enums.EntryTypeEnum.SQS;
import static br.com.lib.structured.log.utils.JsonUtils.normalizeOutput;

/**
 * Entrada via SQS ({@code @SqsListener}) usando o interceptor nativo do Spring Cloud AWS,
 * no lugar do antigo aspect. Diferente do aspect, ele enxerga os headers da mensagem
 * (message attributes do SQS), de onde vem o {@code X-Trace-Id} propagado pela app de origem.
 *
 * <p>{@code intercept} roda antes do listener e {@code afterProcessing} depois (com sucesso ou erro).
 * O {@code callId} de cada mensagem fica num mapa por id da mensagem, para o
 * {@code afterProcessing} restaurar o contexto mesmo se rodar em outra thread.
 */
public class SqsEntryInterceptor implements MessageInterceptor<Object> {

    private final LoggerLifecycle loggerLifecycle;
    private final Map<UUID, String> callIdsByMessage = new ConcurrentHashMap<>();

    public SqsEntryInterceptor(LoggerLifecycle loggerLifecycle) {
        this.loggerLifecycle = loggerLifecycle;
    }

    @Override
    public Message<Object> intercept(Message<Object> message) {
        String callId = UUID.randomUUID().toString();
        callIdsByMessage.put(message.getHeaders().getId(), callId);
        bindContext(callId);

        EntryData entry = new EntryData();
        entry.setType(SQS);
        entry.setSource(message.getHeaders().get(SqsHeaders.SQS_QUEUE_NAME_HEADER, String.class));
        entry.setPayload(normalizeOutput(message.getPayload()));
        entry.setTraceId(traceId(message));

        loggerLifecycle.start(entry);
        return message;
    }

    @Override
    public void afterProcessing(Message<Object> message, Throwable t) {
        String callId = callIdsByMessage.remove(message.getHeaders().getId());
        if (callId == null) {
            return;
        }
        bindContext(callId);
        loggerLifecycle.finish(); // loga, limpa o modelo e o ThreadLocal
    }

    private String traceId(Message<Object> message) {
        Object value = message.getHeaders().get(Constants.TRACE_ID_KEY);
        return value == null ? null : value.toString();
    }

    private void bindContext(String callId) {
        LogRequestAttributes attributes = new LogRequestAttributes(Map.of());
        attributes.put(Constants.CALL_ID_KEY, callId);
        RequestContextHolderEnum.get().setRequestAttributes(attributes);
    }
}
