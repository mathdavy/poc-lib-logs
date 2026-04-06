package br.com.lib.structured.log.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"traceId", "entryType", "method", "entryPoint", "input", "output", "routeSteps"})
public class LoggerModel {

    private String traceId = UUID.randomUUID().toString();
    private String entryType; // API, SQS, KAFKA
    private String entryPoint;
    private String method;

    private Object input;
    private Object output;

    private List<RouteStep> routeSteps = new ArrayList<>();

    public void addStep(RouteStep step) {
        this.routeSteps.add(step);
    }
}
