package br.com.lib.structured.log.core;

public interface RequestContextHolderInterface {

    LogRequestAttributes getRequestAttributes();

    void setRequestAttributes(LogRequestAttributes attributes);

    String getCallId();

    void cleanUp();
}
