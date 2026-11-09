package br.com.lib.structured.log.entry;

import br.com.lib.structured.log.entry.abstraction.AbstractEntryAspect;
import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.model.logger.LoggerModel;
import br.com.lib.structured.log.model.logger.impl.RestLoggerModel;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static br.com.lib.structured.log.enums.EntryTypeEnum.REST;

@Aspect
@Component
public class RestEntryAspect extends AbstractEntryAspect {

    @Autowired
    private HttpServletRequest request;

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object intercept(ProceedingJoinPoint jp) throws Throwable {

        EntryData entry = new EntryData();
        entry.setType(REST);
        entry.setSource(request.getRequestURL().toString());
        entry.setMethod(request.getMethod());
        entry.setPayload(jp.getArgs() != null && jp.getArgs().length > 0 ? jp.getArgs() : null);
        entry.setHeaders(extractHeaders(request));

        return execute(jp, entry);
    }



    /*@Override
    protected void handleOutput(Object response) {
        LoggerModel model = LoggerContextHolder.get();
        if (model instanceof RestLoggerModel restModel) {
            restModel.setResponseBody(response);
        }
    }*/
}
