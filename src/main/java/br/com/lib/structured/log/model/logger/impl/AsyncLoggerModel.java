package br.com.lib.structured.log.model.logger.impl;

import br.com.lib.structured.log.model.logger.LoggerModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"traceId", "entryType", "entryPoint", "requestBody", "routeSteps", "error", "timeSpent", "date", "level"})
public class AsyncLoggerModel extends LoggerModel {
}