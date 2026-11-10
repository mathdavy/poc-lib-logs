package br.com.lib.structured.log.model.logger;

import br.com.lib.structured.log.enums.LogLevel;
import br.com.lib.structured.log.model.routes.RouteStep;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class LoggerModel {

    private String traceId;
    private String entryType;
    private String entryPoint;

    private Object requestBody;

    // thread-safe: steps de producers assíncronos (ex.: SQS) são adicionados por threads do SDK
    private List<RouteStep> routeSteps = new CopyOnWriteArrayList<>();

    private Object error; // para armazenar detalhes de erros, se necessário, Presente quando o erro borbulha até a raiz

    private long timeSpent; // tempo gasto em ms, calculado no final do processamento

    private LocalDateTime date; // para registrar o momento do log+

    private LogLevel logLevel;

    // início do fluxo (System.nanoTime) guardado no próprio modelo: o fim pode ocorrer em outra thread
    @JsonIgnore
    private long startNanos;

    public void addStep(RouteStep step) {
        this.routeSteps.add(step);
    }
}
