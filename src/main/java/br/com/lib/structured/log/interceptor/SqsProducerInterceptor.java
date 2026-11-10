package br.com.lib.structured.log.interceptor;

import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.model.logger.LoggerModel;
import br.com.lib.structured.log.model.routes.impl.SqsRouteStep;
import br.com.lib.structured.log.utils.Constants;
import br.com.lib.structured.log.utils.JsonUtils;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static br.com.lib.structured.log.enums.RouteStepsType.SQS;
import static br.com.lib.structured.log.utils.JsonUtils.normalizeOutput;

/**
 * Interceptor nativo do AWS SDK v2: registra um {@link SqsRouteStep} para cada
 * {@code SendMessage} / {@code SendMessageBatch} feito pelo {@code SqsAsyncClient}
 * (e, portanto, pelo {@code SqsTemplate}, que usa esse client por baixo).
 *
 * <p>O {@link LoggerModel} é capturado no {@code beforeExecution} e guardado nos
 * {@link ExecutionAttributes}: o {@code afterExecution}/{@code onExecutionFailure}
 * de um client assíncrono roda numa thread do SDK, onde o contexto da lib não existe.
 */
public class SqsProducerInterceptor implements ExecutionInterceptor {

    private static final ExecutionAttribute<LoggerModel> LOGGER_MODEL = new ExecutionAttribute<>("trace-log.sqs.loggerModel");
    private static final ExecutionAttribute<SqsRouteStep> STEP = new ExecutionAttribute<>("trace-log.sqs.step");
    private static final ExecutionAttribute<Long> START = new ExecutionAttribute<>("trace-log.sqs.start");
    private static final int MAX_MESSAGE_ATTRIBUTES = 10;

    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes attributes) {
        SdkRequest request = context.request();
        if (!(request instanceof SendMessageRequest) && !(request instanceof SendMessageBatchRequest)) {
            return;
        }

        LoggerModel model = LoggerContextHolder.get();
        if (model == null) {
            return;
        }

        SqsRouteStep step = new SqsRouteStep();
        step.setType(SQS);

        if (request instanceof SendMessageRequest send) {
            step.setTarget(queueName(send.queueUrl()));
            step.setRequestBody(normalizeOutput(send.messageBody()));
        } else {
            SendMessageBatchRequest batch = (SendMessageBatchRequest) request;
            step.setTarget(queueName(batch.queueUrl()));
            step.setRequestBody(batch.entries().stream()
                    .map(SendMessageBatchRequestEntry::messageBody)
                    .map(JsonUtils::normalizeOutput)
                    .toList());
        }

        attributes.putAttribute(LOGGER_MODEL, model);
        attributes.putAttribute(STEP, step);
        attributes.putAttribute(START, System.nanoTime());
    }

    /**
     * Propaga o traceId do fluxo como message attribute {@code X-Trace-Id}: a app consumidora
     * (com esta lib) o lê no {@code SqsEntryInterceptor} e continua o mesmo trace.
     * Roda logo após o {@code beforeExecution}, na mesma thread.
     */
    @Override
    public SdkRequest modifyRequest(Context.ModifyRequest context, ExecutionAttributes attributes) {
        SdkRequest request = context.request();
        LoggerModel model = attributes.getAttribute(LOGGER_MODEL);
        if (model == null || model.getTraceId() == null) {
            return request;
        }
        String traceId = model.getTraceId();

        if (request instanceof SendMessageRequest send) {
            return send.toBuilder()
                    .messageAttributes(withTraceId(send.messageAttributes(), traceId))
                    .build();
        }
        if (request instanceof SendMessageBatchRequest batch) {
            return batch.toBuilder()
                    .entries(batch.entries().stream()
                            .map(entry -> entry.toBuilder()
                                    .messageAttributes(withTraceId(entry.messageAttributes(), traceId))
                                    .build())
                            .toList())
                    .build();
        }
        return request;
    }

    private Map<String, MessageAttributeValue> withTraceId(Map<String, MessageAttributeValue> current, String traceId) {
        // não sobrescreve um traceId enviado pela app e respeita o limite de 10 attributes do SQS
        if (current.containsKey(Constants.TRACE_ID_KEY) || current.size() >= MAX_MESSAGE_ATTRIBUTES) {
            return current;
        }
        Map<String, MessageAttributeValue> attributes = new HashMap<>(current);
        attributes.put(Constants.TRACE_ID_KEY, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(traceId)
                .build());
        return attributes;
    }

    @Override
    public void afterExecution(Context.AfterExecution context, ExecutionAttributes attributes) {
        SqsRouteStep step = attributes.getAttribute(STEP);
        if (step == null) {
            return;
        }

        // SendMessageBatch pode "dar certo" na chamada e falhar em parte das mensagens.
        if (context.response() instanceof SendMessageBatchResponse batch && batch.hasFailed() && !batch.failed().isEmpty()) {
            step.setError(batchError(batch.failed()));
        }

        complete(attributes, step);
    }

    @Override
    public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes attributes) {
        SqsRouteStep step = attributes.getAttribute(STEP);
        if (step == null) {
            return;
        }

        Throwable exception = context.exception();
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("exceptionClass", exception.getClass().getName());
        error.put("message", exception.getMessage());
        step.setError(error);

        complete(attributes, step);
    }

    private void complete(ExecutionAttributes attributes, SqsRouteStep step) {
        Long start = attributes.getAttribute(START);
        step.setTimeSpent((int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        attributes.getAttribute(LOGGER_MODEL).addStep(step);
    }

    private Map<String, Object> batchError(List<BatchResultErrorEntry> failed) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("exceptionClass", "SendMessageBatchPartialFailure");
        error.put("message", failed.size() + " mensagem(ns) não enviada(s)");
        error.put("failedEntries", failed.stream()
                .map(entry -> Map.of(
                        "id", String.valueOf(entry.id()),
                        "code", String.valueOf(entry.code()),
                        "message", String.valueOf(entry.message())))
                .toList());
        return error;
    }

    /** {@code https://sqs.us-east-1.amazonaws.com/000000000000/minha-fila} → {@code minha-fila}. */
    private String queueName(String queueUrl) {
        if (queueUrl == null) {
            return null;
        }
        int idx = queueUrl.lastIndexOf('/');
        return idx >= 0 ? queueUrl.substring(idx + 1) : queueUrl;
    }
}
