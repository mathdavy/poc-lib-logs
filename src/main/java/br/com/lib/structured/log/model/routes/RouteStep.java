package br.com.lib.structured.log.model.routes;

import br.com.lib.structured.log.enums.RouteStepsType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "target", "method", "status", "request", "response"})
public abstract class RouteStep {

    private RouteStepsType type; // HTTP, SQS, KAFKA
    private String target;
    private Object requestBody;
    private Integer timeSpent;
    private Object error; // para armazenar detalhes de erros, se necessário, Presente quando o erro borbulha até a raiz

}
