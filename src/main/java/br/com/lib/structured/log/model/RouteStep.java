package br.com.lib.structured.log.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "target", "method", "status", "request", "response"})
public class RouteStep {

    private String type; // HTTP, SQS, KAFKA
    private String target;

    private String method;

    private String status;

    private Object request;
    private Object response;
}
