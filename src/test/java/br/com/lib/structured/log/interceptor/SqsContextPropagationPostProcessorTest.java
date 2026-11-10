package br.com.lib.structured.log.interceptor;

import br.com.lib.structured.log.core.LogRequestAttributes;
import br.com.lib.structured.log.core.RequestContextHolderEnum;
import br.com.lib.structured.log.utils.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqsContextPropagationPostProcessorTest {

    @AfterEach
    void tearDown() {
        RequestContextHolderEnum.get().cleanUp();
    }

    @Test
    void dependentStagesShouldSeeCallerContextWhenFutureCompletesOnAnotherThread() throws Exception {
        CompletableFuture<GetQueueUrlResponse> sdkFuture = new CompletableFuture<>();
        SqsAsyncClient delegate = mock(SqsAsyncClient.class);
        when(delegate.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(sdkFuture);

        SqsAsyncClient client = (SqsAsyncClient) new SqsContextPropagationPostProcessor()
                .postProcessAfterInitialization(delegate, "sqsAsyncClient");

        LogRequestAttributes attributes = new LogRequestAttributes(Map.of());
        attributes.put(Constants.CALL_ID_KEY, "fluxo-1");
        RequestContextHolderEnum.get().setRequestAttributes(attributes);

        AtomicReference<String> callIdInDependentStage = new AtomicReference<>();
        CompletableFuture<Void> chain = client.getQueueUrl(GetQueueUrlRequest.builder().queueName("q").build())
                .thenAccept(r -> callIdInDependentStage.set(RequestContextHolderEnum.get().getCallId()));

        // thread do SDK: sem contexto (ou com contexto antigo de outro fluxo)
        Thread sdkThread = new Thread(() -> {
            LogRequestAttributes stale = new LogRequestAttributes(Map.of());
            stale.put(Constants.CALL_ID_KEY, "fluxo-antigo");
            RequestContextHolderEnum.get().setRequestAttributes(stale);
            sdkFuture.complete(GetQueueUrlResponse.builder().queueUrl("http://x/q").build());
            // depois de completar, a thread do SDK volta ao estado anterior
            assertEquals("fluxo-antigo", RequestContextHolderEnum.get().getCallId());
        });
        sdkThread.start();
        sdkThread.join();
        chain.get();

        assertEquals("fluxo-1", callIdInDependentStage.get());
    }

    @Test
    void shouldReturnOriginalFutureWhenThereIsNoContext() {
        CompletableFuture<GetQueueUrlResponse> sdkFuture = new CompletableFuture<>();
        SqsAsyncClient delegate = mock(SqsAsyncClient.class);
        when(delegate.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(sdkFuture);

        SqsAsyncClient client = (SqsAsyncClient) new SqsContextPropagationPostProcessor()
                .postProcessAfterInitialization(delegate, "sqsAsyncClient");

        assertSame(sdkFuture, client.getQueueUrl(GetQueueUrlRequest.builder().queueName("q").build()));
    }
}
