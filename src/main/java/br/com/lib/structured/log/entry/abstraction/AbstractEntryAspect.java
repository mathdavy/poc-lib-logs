package br.com.lib.structured.log.entry.abstraction;

import br.com.lib.structured.log.LoggerLifecycle;
import br.com.lib.structured.log.model.EntryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public abstract class AbstractEntryAspect {

    private final LoggerLifecycle loggerLifecycle;

    protected Object execute(ProceedingJoinPoint jp, EntryData entry) throws Throwable {

        try {

            loggerLifecycle.start(entry);

            Object response = jp.proceed();

            return response;

        } catch (Exception e) {
            //LoggerContextHolder.get().setOutput(e.getMessage());
            throw e;

        } finally {
            loggerLifecycle.finish();
        }
    }
}
