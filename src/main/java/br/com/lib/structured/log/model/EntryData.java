package br.com.lib.structured.log.model;

import br.com.lib.structured.log.enums.EntryTypeEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "source", "method", "headers", "payload"})
public class EntryData {

    private String traceId; // ID de rastreamento para correlacionar logs

    private EntryTypeEnum type; // API, SQS, KAFKA
    private String source; // URL, queue, topic

    private String method; // GET, POST (se aplicável)

    private Object payload;
    private Map<String, String> headers;
}
