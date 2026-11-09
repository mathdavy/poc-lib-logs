package br.com.lib.structured.log.interceptor;

import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.enums.RouteStepsType;
import br.com.lib.structured.log.model.logger.LoggerModel;
import br.com.lib.structured.log.model.routes.impl.FeignRouteStep;
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

        FeignRouteStep step = null;

        if (ctx != null) {
            step = new FeignRouteStep();
            step.setType(RouteStepsType.REST);
            step.setTarget(request.url());
            step.setHttpMethod(request.httpMethod().name());

            step.setRequestBody(extractRequest(request));
        }

        try {
            Response response = delegate.execute(request, options);

            if (step != null) {
                step.setResponseStatus(String.valueOf(response.status()));

                Object body = extractResponseBody(response);
                step.setResponseBody(body);

                ctx.addStep(step);

                String bodyStr = body == null ? null : body.toString();
                return rebuildResponse(response, bodyStr);
            }

            return response;

        } catch (Exception e) {

            if (step != null) {
                step.setResponseStatus("ERROR");
                step.setResponseBody(e.getMessage());

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