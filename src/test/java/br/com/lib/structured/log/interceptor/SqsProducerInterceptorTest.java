package br.com.lib.structured.log.interceptor;

import br.com.lib.structured.log.core.LogRequestAttributes;
import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.core.RequestContextHolderEnum;
import br.com.lib.structured.log.enums.EntryTypeEnum;
import br.com.lib.structured.log.enums.RouteStepsType;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.model.logger.LoggerModel;
import br.com.lib.structured.log.model.routes.impl.SqsRouteStep;
import br.com.lib.structured.log.utils.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqsProducerInterceptorTest {

    private static final String QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/000000000000/pedidos-queue";

    private final SqsProducerInterceptor interceptor = new SqsProducerInterceptor();

    @BeforeEach
    void setUp() {
        LogRequestAttributes attributes = new LogRequestAttributes(Map.of());
        attributes.put(Constants.CALL_ID_KEY, UUID.randomUUID().toString());
        RequestContextHolderEnum.get().setRequestAttributes(attributes);

        EntryData entry = new EntryData();
        entry.setType(EntryTypeEnum.SQS);
        entry.setSource("entrada-queue");
        LoggerContextHolder.init(entry);
    }

    @AfterEach
    void tearDown() {
        LoggerContextHolder.clear();
    }

    @Test
    void shouldAddSqsStepOnSuccessfulSend() {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .messageBody("{\"id\":1}")
                .build();
        ExecutionAttributes attributes = new ExecutionAttributes();

        interceptor.beforeExecution(before(request), attributes);
        interceptor.afterExecution(after(request, SendMessageResponse.builder().messageId("m-1").build()), attributes);

        LoggerModel model = LoggerContextHolder.get();
        assertEquals(1, model.getRouteSteps().size());
        SqsRouteStep step = assertInstanceOf(SqsRouteStep.class, model.getRouteSteps().get(0));
        assertEquals(RouteStepsType.SQS, step.getType());
        assertEquals("pedidos-queue", step.getTarget());
        assertEquals(1, ((com.fasterxml.jackson.databind.JsonNode) step.getRequestBody()).get("id").asInt());
        assertNotNull(step.getTimeSpent());
        assertNull(step.getError());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAddStepWithErrorOnFailure() {
        SendMessageRequest request = SendMessageRequest.builder().queueUrl(QUEUE_URL).messageBody("texto").build();
        ExecutionAttributes attributes = new ExecutionAttributes();

        interceptor.beforeExecution(before(request), attributes);

        Context.FailedExecution failed = mock(Context.FailedExecution.class);
        when(failed.exception()).thenReturn(QueueDoesNotExistException.builder().message("fila não existe").build());
        interceptor.onExecutionFailure(failed, attributes);

        SqsRouteStep step = (SqsRouteStep) LoggerContextHolder.get().getRouteSteps().get(0);
        assertEquals("texto", step.getRequestBody());
        Map<String, Object> error = (Map<String, Object>) step.getError();
        assertEquals(QueueDoesNotExistException.class.getName(), error.get("exceptionClass"));
        assertTrue(error.get("message").toString().contains("fila não existe"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRegisterPartialBatchFailure() {
        SendMessageBatchRequest request = SendMessageBatchRequest.builder()
                .queueUrl(QUEUE_URL)
                .entries(SendMessageBatchRequestEntry.builder().id("1").messageBody("a").build(),
                        SendMessageBatchRequestEntry.builder().id("2").messageBody("b").build())
                .build();
        SendMessageBatchResponse response = SendMessageBatchResponse.builder()
                .failed(BatchResultErrorEntry.builder().id("2").code("InternalError").message("boom").build())
                .build();
        ExecutionAttributes attributes = new ExecutionAttributes();

        interceptor.beforeExecution(before(request), attributes);
        interceptor.afterExecution(after(request, response), attributes);

        SqsRouteStep step = (SqsRouteStep) LoggerContextHolder.get().getRouteSteps().get(0);
        assertEquals(List.of("a", "b"), step.getRequestBody());
        Map<String, Object> error = (Map<String, Object>) step.getError();
        assertEquals(1, ((List<?>) error.get("failedEntries")).size());
    }

    @Test
    void shouldCompleteStepFromAnotherThread() throws Exception {
        SendMessageRequest request = SendMessageRequest.builder().queueUrl(QUEUE_URL).messageBody("x").build();
        ExecutionAttributes attributes = new ExecutionAttributes();
        interceptor.beforeExecution(before(request), attributes);

        // simula a thread do SDK (client assíncrono), sem o contexto da lib
        CompletableFuture.runAsync(() -> {
            RequestContextHolderEnum.get().cleanUp();
            interceptor.afterExecution(after(request, SendMessageResponse.builder().build()), attributes);
        }).get();

        assertEquals(1, LoggerContextHolder.get().getRouteSteps().size());
    }

    @Test
    void shouldIgnoreWhenThereIsNoLogContext() {
        LoggerContextHolder.clear();
        SendMessageRequest request = SendMessageRequest.builder().queueUrl(QUEUE_URL).messageBody("x").build();
        ExecutionAttributes attributes = new ExecutionAttributes();

        interceptor.beforeExecution(before(request), attributes);
        interceptor.afterExecution(after(request, SendMessageResponse.builder().build()), attributes);

        assertNull(LoggerContextHolder.get());
    }

    private Context.BeforeExecution before(SdkRequest request) {
        return () -> request;
    }

    private Context.AfterExecution after(SdkRequest request, SdkResponse response) {
        Context.AfterExecution context = mock(Context.AfterExecution.class);
        when(context.request()).thenReturn(request);
        when(context.response()).thenReturn(response);
        return context;
    }
}
