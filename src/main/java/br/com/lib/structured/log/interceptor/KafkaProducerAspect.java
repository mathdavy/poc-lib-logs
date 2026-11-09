package br.com.lib.structured.log.interceptor;

import br.com.lib.structured.log.core.LoggerContextHolder;
import br.com.lib.structured.log.enums.RouteStepsType;
import br.com.lib.structured.log.model.logger.LoggerModel;
import br.com.lib.structured.log.model.routes.impl.KafkaRouteStep;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class KafkaProducerAspect {

    @Around("execution(* org.springframework.kafka.core.KafkaTemplate.*(..))")
    public Object intercept(ProceedingJoinPoint jp) throws Throwable {

        LoggerModel ctx = LoggerContextHolder.get();
        if (ctx == null) return jp.proceed();

        KafkaRouteStep step = new KafkaRouteStep();
        step.setType(RouteStepsType.KAFKA);
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
