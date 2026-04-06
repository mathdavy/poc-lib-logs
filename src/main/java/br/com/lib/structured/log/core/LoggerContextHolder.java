package br.com.lib.structured.log.core;

import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.model.LoggerModel;

public final class LoggerContextHolder {

    private LoggerContextHolder() {}

    private static final ThreadLocal<LoggerModel> CONTEXT =
            new ThreadLocal<>();

    public static void init(EntryData entryData) {

        LoggerModel model = new LoggerModel();
        model.setEntryType(entryData.getType());
        model.setEntryPoint(entryData.getSource());
        model.setInput(entryData.getPayload());
        model.setMethod(entryData.getMethod());

        CONTEXT.set(model);
    }

    public static LoggerModel get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
