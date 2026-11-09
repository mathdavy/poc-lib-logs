package br.com.lib.structured.log.interceptor;

import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.model.logger.LoggerModel;
import br.com.lib.structured.log.model.routes.impl.SqsRouteStep;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import static br.com.lib.structured.log.enums.RouteStepsType.SQS;

@Aspect
@Component
public class SqsProducerAspect {

    @Around("execution(* io.awspring.cloud.sqs.operations.SqsTemplate.*(..))")
    public Object intercept(ProceedingJoinPoint jp) throws Throwable {

        LoggerModel ctx = LoggerContextHolder.get();
        if (ctx == null) return jp.proceed();

        SqsRouteStep step = new SqsRouteStep();
        step.setType(SQS);
        step.setTarget("queue");

        step.setRequestBody(jp.getArgs());

        try {
            Object result = jp.proceed();
            //step.setStatus("SUCCESS");
            return result;

        } catch (Exception e) {
            //step.setStatus("ERROR");
            throw e;

        } finally {
            ctx.addStep(step);
        }
    }
}
