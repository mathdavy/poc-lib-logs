package br.com.lib.structured.log.entry;

import br.com.lib.structured.log.LoggerLifecycle;
import br.com.lib.structured.log.core.LogRequestAttributes;
import br.com.lib.structured.log.core.RequestContextHolderEnum;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.utils.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static br.com.lib.structured.log.enums.EntryTypeEnum.REST;

@Component
@RequiredArgsConstructor
public class RestEntryFilter extends OncePerRequestFilter {

    private final LoggerLifecycle loggerLifecycle;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Map<String, String> headers = extractHeaders(request);
        LogRequestAttributes logRequestAttributes = new LogRequestAttributes(headers);
        final String uuid = UUID.randomUUID().toString();
        logRequestAttributes.put(Constants.CALL_ID_KEY, uuid);
        final RequestContextHolderEnum contextHolderEnum = RequestContextHolderEnum.INSTANCE;
        contextHolderEnum.setRequestAttributes(logRequestAttributes);

        EntryData entryData = buildEntryData(request, headers);
        loggerLifecycle.start(entryData);

        try {
            filterChain.doFilter(request, response);
        } finally {
            loggerLifecycle.finish();
        }
    }

    private EntryData buildEntryData(HttpServletRequest request, Map<String, String> headers) {
        EntryData entry = new EntryData();
        entry.setType(REST);
        entry.setSource(request.getRequestURL().toString());
        entry.setMethod(request.getMethod());
        //entry.setPayload(request.get);
        entry.setHeaders(headers);
        // getHeader é case-insensitive (o Map de headers vem em minúsculas do Tomcat).
        // Se não vier, o LoggerContextHolder gera um novo traceId.
        entry.setTraceId(request.getHeader(Constants.TRACE_ID_KEY));

        return entry;
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames())
                .stream()
                .collect(Collectors.toMap(name -> name, request::getHeader));
    }
}
