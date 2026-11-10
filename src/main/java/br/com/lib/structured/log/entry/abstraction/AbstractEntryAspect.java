package br.com.lib.structured.log.entry.abstraction;

import br.com.lib.structured.log.LoggerLifecycle;
import br.com.lib.structured.log.core.LogRequestAttributes;
import br.com.lib.structured.log.core.RequestContextHolderEnum;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public abstract class AbstractEntryAspect {

    private final LoggerLifecycle loggerLifecycle;

    protected Object execute(ProceedingJoinPoint jp, EntryData entry) throws Throwable {

        // Cada mensagem consumida é um fluxo próprio: callId novo, igual ao RestEntryFilter.
        // Sem isso, threads do listener (pool) usariam callId vazio ou herdado de outro fluxo.
        LogRequestAttributes attributes = new LogRequestAttributes(Map.of());
        attributes.put(Constants.CALL_ID_KEY, UUID.randomUUID().toString());
        RequestContextHolderEnum.get().setRequestAttributes(attributes);

        try {
            loggerLifecycle.start(entry);
            return jp.proceed();
        } finally {
            loggerLifecycle.finish();
        }
    }
}
