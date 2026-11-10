package br.com.lib.structured.log;

import br.com.lib.structured.log.core.LogRequestAttributes;
import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.core.RequestContextHolderEnum;
import br.com.lib.structured.log.entry.SqsEntryInterceptor;
import br.com.lib.structured.log.enums.EntryTypeEnum;
import br.com.lib.structured.log.interceptor.SqsProducerInterceptor;
import br.com.lib.structured.log.interceptor.TraceIdFeignInterceptor;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.model.logger.LoggerModel;
import br.com.lib.structured.log.utils.Constants;
import feign.RequestTemplate;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdPropagationTest {

    private static final String QUEUE_URL = "http://localhost:9324/000000000000/destino";

    @AfterEach
    void tearDown() {
        LoggerContextHolder.clear();
    }

    @Test
    void shouldKeepIncomingTraceId() {
        assertEquals("trace-recebido", startFlow("trace-recebido").getTraceId());
    }

    @Test
    void shouldGenerateTraceIdWhenMissing() {
        String traceId = startFlow(null).getTraceId();
        assertNotNull(traceId);
        UUID.fromString(traceId); // é um UUID válido
    }

    @Test
    void feignShouldSendTraceIdHeader() {
        startFlow("trace-1");
        RequestTemplate template = new RequestTemplate();

        new TraceIdFeignInterceptor().apply(template);

        assertEquals(List.of("trace-1"), List.copyOf(template.headers().get(Constants.TRACE_ID_KEY)));
    }

    @Test
    void feignShouldNotOverrideTraceIdSetByApp() {
        startFlow("trace-1");
        RequestTemplate template = new RequestTemplate().header(Constants.TRACE_ID_KEY, "do-app");

        new TraceIdFeignInterceptor().apply(template);

        assertEquals(List.of("do-app"), List.copyOf(template.headers().get(Constants.TRACE_ID_KEY)));
    }

    @Test
    void feignShouldDoNothingOutsideAFlow() {
        RequestTemplate template = new RequestTemplate();
        new TraceIdFeignInterceptor().apply(template);
        assertFalse(template.headers().containsKey(Constants.TRACE_ID_KEY));
    }

    @Test
    void sqsProducerShouldAddTraceIdMessageAttribute() {
        startFlow("trace-2");
        SendMessageRequest request = SendMessageRequest.builder().queueUrl(QUEUE_URL).messageBody("{}").build();

        SendMessageRequest modified = (SendMessageRequest) runProducerInterceptor(request);

        MessageAttributeValue attribute = modified.messageAttributes().get(Constants.TRACE_ID_KEY);
        assertEquals("String", attribute.dataType());
        assertEquals("trace-2", attribute.stringValue());
    }

    @Test
    void sqsProducerShouldAddTraceIdToEveryBatchEntry() {
        startFlow("trace-3");
        SendMessageBatchRequest request = SendMessageBatchRequest.builder()
                .queueUrl(QUEUE_URL)
                .entries(SendMessageBatchRequestEntry.builder().id("1").messageBody("a").build(),
                        SendMessageBatchRequestEntry.builder().id("2").messageBody("b").build())
                .build();

        SendMessageBatchRequest modified = (SendMessageBatchRequest) runProducerInterceptor(request);

        modified.entries().forEach(entry ->
                assertEquals("trace-3", entry.messageAttributes().get(Constants.TRACE_ID_KEY).stringValue()));
    }

    @Test
    void sqsProducerShouldRespectTenAttributesLimit() {
        startFlow("trace-4");
        Map<String, MessageAttributeValue> full = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            full.put("attr" + i, MessageAttributeValue.builder().dataType("String").stringValue("v").build());
        }
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(QUEUE_URL).messageBody("{}").messageAttributes(full).build();

        SendMessageRequest modified = (SendMessageRequest) runProducerInterceptor(request);

        assertEquals(10, modified.messageAttributes().size());
        assertNull(modified.messageAttributes().get(Constants.TRACE_ID_KEY));
    }

    @Test
    void sqsEntryShouldReadTraceIdFromMessageHeader() {
        LoggerLifecycle lifecycle = mock(LoggerLifecycle.class);
        AtomicReference<EntryData> captured = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return null;
        }).when(lifecycle).start(org.mockito.ArgumentMatchers.any());

        Message<Object> message = MessageBuilder.<Object>withPayload("{\"nome\":\"abc\"}")
                .setHeader(Constants.TRACE_ID_KEY, "trace-da-origem")
                .setHeader(SqsHeaders.SQS_QUEUE_NAME_HEADER, "pedidos-queue")
                .build();

        SqsEntryInterceptor interceptor = new SqsEntryInterceptor(lifecycle);
        interceptor.intercept(message);
        interceptor.afterProcessing(message, null);

        assertEquals("trace-da-origem", captured.get().getTraceId());
        assertEquals("pedidos-queue", captured.get().getSource());
        assertEquals(EntryTypeEnum.SQS, captured.get().getType());
        org.mockito.Mockito.verify(lifecycle).finish();
    }

    private LoggerModel startFlow(String traceId) {
        LogRequestAttributes attributes = new LogRequestAttributes(Map.of());
        attributes.put(Constants.CALL_ID_KEY, UUID.randomUUID().toString());
        RequestContextHolderEnum.get().setRequestAttributes(attributes);

        EntryData entry = new EntryData();
        entry.setType(EntryTypeEnum.REST);
        entry.setTraceId(traceId);
        LoggerContextHolder.init(entry);
        return LoggerContextHolder.get();
    }

    private SdkRequest runProducerInterceptor(SdkRequest request) {
        SqsProducerInterceptor interceptor = new SqsProducerInterceptor();
        ExecutionAttributes attributes = new ExecutionAttributes();
        interceptor.beforeExecution(() -> request, attributes);
        Context.ModifyRequest modifyContext = mock(Context.ModifyRequest.class);
        when(modifyContext.request()).thenReturn(request);
        return interceptor.modifyRequest(modifyContext, attributes);
    }
}
