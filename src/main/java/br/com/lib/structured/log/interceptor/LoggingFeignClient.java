package br.com.lib.structured.log.interceptor;

import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.model.LoggerModel;
import br.com.lib.structured.log.model.RouteStep;
import feign.Client;
import feign.Request;
import feign.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static br.com.lib.structured.log.utils.JsonUtils.normalizeOutput;

public class LoggingFeignClient implements Client {

    private final Client delegate;

    public LoggingFeignClient(Client delegate) {
        this.delegate = delegate;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {

        LoggerModel ctx = LoggerContextHolder.get();

        RouteStep step = null;

        if (ctx != null) {
            step = new RouteStep();
            step.setType("HTTP");
            step.setTarget(request.url());
            step.setMethod(request.httpMethod().name());

            step.setRequest(extractRequest(request));
        }

        try {
            Response response = delegate.execute(request, options);

            if (step != null) {
                step.setStatus(String.valueOf(response.status()));

                Object body = extractResponseBody(response);
                step.setResponse(body);

                ctx.addStep(step);

                String bodyStr = body == null ? null : body.toString();
                return rebuildResponse(response, bodyStr);
            }

            return response;

        } catch (Exception e) {

            if (step != null) {
                step.setStatus("ERROR");
                step.setResponse(e.getMessage());

                ctx.addStep(step);
            }

            throw e;
        }
    }

    private Object extractRequest(Request request) {

        if (request.body() != null) {
            return new String(request.body(), StandardCharsets.UTF_8);
        }

        return null;
    }

    private Object extractResponseBody(Response response) throws IOException {

        if (response.body() == null) return null;

        return normalizeOutput(new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private Response rebuildResponse(Response original, String body) {

        return Response.builder()
                .status(original.status())
                .reason(original.reason())
                .headers(original.headers())
                .request(original.request())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}