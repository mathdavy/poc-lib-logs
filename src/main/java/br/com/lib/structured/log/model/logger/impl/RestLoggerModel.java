package br.com.lib.structured.log.model.logger.impl;

import br.com.lib.structured.log.model.logger.LoggerModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"traceId", "entryType", "httpMethod", "entryPoint", "requestHeaders", "requestBody", "responseBody", "routeSteps", "error", "timeSpent", "date", "level"})
public class RestLoggerModel extends LoggerModel {

    private String httpMethod;
    private Object requestHeaders;
    private Object responseBody;
}
