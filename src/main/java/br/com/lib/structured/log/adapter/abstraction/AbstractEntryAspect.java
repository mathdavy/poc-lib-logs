package br.com.lib.structured.log.adapter.abstraction;

import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.model.LoggerModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;

import static br.com.lib.structured.log.utils.JsonUtils.normalizeOutput;

@Slf4j
public abstract class AbstractEntryAspect {

    private final ObjectMapper mapper = new ObjectMapper();

    protected Object execute(ProceedingJoinPoint jp, EntryData entry) throws Throwable {

        try {
            LoggerContextHolder.init(entry);

            Object response = jp.proceed();

            var normalizedOutput = normalizeOutput(response);

            LoggerContextHolder.get().setOutput(normalizedOutput);

            return response;

        } catch (Exception e) {
            LoggerContextHolder.get().setOutput(e.getMessage());
            throw e;

        } finally {
            logFinal();
            LoggerContextHolder.clear();
        }
    }

    private void logFinal() {
        try {
            LoggerModel model = LoggerContextHolder.get();

            String json = new ObjectMapper().writeValueAsString(model);

            log.info(json);

        } catch (Exception e) {
            System.out.println("Erro ao logar");
        }
    }
}
