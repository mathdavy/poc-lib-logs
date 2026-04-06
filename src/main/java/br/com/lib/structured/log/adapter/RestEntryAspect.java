package br.com.lib.structured.log.adapter;

import br.com.lib.structured.log.adapter.abstraction.AbstractEntryAspect;
import br.com.lib.structured.log.model.EntryData;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RestEntryAspect extends AbstractEntryAspect {

    @Autowired
    private HttpServletRequest request;

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object intercept(ProceedingJoinPoint jp) throws Throwable {

        EntryData entry = new EntryData();
        entry.setType("REST");
        entry.setSource(request.getRequestURL().toString());
        entry.setMethod(request.getMethod());
        entry.setPayload(jp.getArgs() != null && jp.getArgs().length > 0 ? jp.getArgs() : null);

        return execute(jp, entry);
    }
}
