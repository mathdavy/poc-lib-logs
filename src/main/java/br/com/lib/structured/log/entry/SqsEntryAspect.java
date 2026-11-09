package br.com.lib.structured.log.entry;

import br.com.lib.structured.log.entry.abstraction.AbstractEntryAspect;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.utils.AnnotationUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

import static br.com.lib.structured.log.enums.EntryTypeEnum.SQS;

@Aspect
@Component
public class SqsEntryAspect extends AbstractEntryAspect {

    @Around("@annotation(io.awspring.cloud.sqs.annotation.SqsListener)")
    public Object intercept(ProceedingJoinPoint jp) throws Throwable {

        EntryData entry = new EntryData();
        entry.setType(SQS);
        entry.setSource(resolveQueueName(jp));
        entry.setPayload(jp.getArgs());

        return execute(jp, entry);

    }

    private String resolveQueueName(ProceedingJoinPoint jp) {

        Method method = ((MethodSignature) jp.getSignature()).getMethod();

        return AnnotationUtils.findAnnotation(
                        method,
                        "io.awspring.cloud.sqs.annotation.SqsListener"
                )
                .map(annotation -> {
                    String[] queues = (String[]) AnnotationUtils.getValue(annotation, "value");
                    return (queues != null && queues.length > 0) ? queues[0] : "unknown";
                })
                .orElse("unknown");
    }
}
