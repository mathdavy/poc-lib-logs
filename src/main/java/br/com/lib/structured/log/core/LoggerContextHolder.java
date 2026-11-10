package br.com.lib.structured.log.core;

import br.com.lib.structured.log.enums.EntryTypeEnum;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.model.logger.LoggerModel;
import br.com.lib.structured.log.model.logger.impl.RestLoggerModel;
import br.com.lib.structured.log.model.logger.impl.AsyncLoggerModel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class LoggerContextHolder {

    private LoggerContextHolder() {}

    private static final Map<String, LoggerModel> CONTEXT = new ConcurrentHashMap<>();

    private static final Map<EntryTypeEnum, Function<EntryData, LoggerModel>> MODEL_FACTORIES = Map.of(
            EntryTypeEnum.REST, entryData -> {
                RestLoggerModel restModel = new RestLoggerModel();
                restModel.setHttpMethod(entryData.getMethod());
                restModel.setRequestHeaders(entryData.getHeaders());
                return restModel;
            },
            EntryTypeEnum.KAFKA, entryData -> new AsyncLoggerModel(),
            EntryTypeEnum.SQS, entryData -> new AsyncLoggerModel()
    );

    public static void init(EntryData entryData) {
        LoggerModel model = createModel(entryData);
        CONTEXT.put(RequestContextHolderEnum.get().getCallId(), model);
    }

    private static LoggerModel createModel(EntryData entryData) {
        EntryTypeEnum type = entryData.getType();
        Function<EntryData, LoggerModel> factory = MODEL_FACTORIES.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown entry type: " + type);
        }
        LoggerModel model = factory.apply(entryData);
        // traceId recebido (header HTTP / message attribute SQS) é mantido; senão o fluxo nasce aqui
        String traceId = entryData.getTraceId();
        model.setTraceId(traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
        model.setEntryType(type.name());
        model.setEntryPoint(entryData.getSource());
        model.setRequestBody(entryData.getPayload());
        return model;
    }

    public static LoggerModel get() {
        return CONTEXT.get(RequestContextHolderEnum.get().getCallId());
    }

    public static void clear() {
        CONTEXT.remove(RequestContextHolderEnum.get().getCallId());
        RequestContextHolderEnum.get().cleanUp();
    }
}
