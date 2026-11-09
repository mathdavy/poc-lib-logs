package br.com.lib.structured.log.model.routes.impl;

import br.com.lib.structured.log.model.routes.RouteStep;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "target", "requestBody", "timeSpent", "error"})
public class SqsRouteStep extends RouteStep {
}
