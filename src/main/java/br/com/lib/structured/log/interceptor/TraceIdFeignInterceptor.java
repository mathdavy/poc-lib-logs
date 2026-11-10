package br.com.lib.structured.log.interceptor;

import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.model.logger.LoggerModel;
import br.com.lib.structured.log.utils.Constants;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Propaga o traceId do fluxo atual no header {@code X-Trace-Id} de toda chamada Feign,
 * para que a app de destino continue o mesmo trace.
 */
public class TraceIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        LoggerModel model = LoggerContextHolder.get();
        if (model == null || model.getTraceId() == null) {
            return;
        }
        // não sobrescreve se a app já definiu o header explicitamente
        if (!template.headers().containsKey(Constants.TRACE_ID_KEY)) {
            template.header(Constants.TRACE_ID_KEY, model.getTraceId());
        }
    }
}
