package br.com.lib.structured.log.entry;

import br.com.lib.structured.log.LoggerLifecycle;
import br.com.lib.structured.log.entry.abstraction.AbstractEntryAspect;
import br.com.lib.structured.log.model.EntryData;
import br.com.lib.structured.log.utils.AnnotationUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

import static br.com.lib.structured.log.enums.EntryTypeEnum.KAFKA;

@Aspect
@Component
public class KafkaEntryAspect extends AbstractEntryAspect {

    public KafkaEntryAspect(LoggerLifecycle loggerLifecycle) {
        super(loggerLifecycle);
    }

    @Around("@annotation(org.springframework.kafka.annotation.KafkaListener)")
    public Object intercept(ProceedingJoinPoint jp) throws Throwable {

        EntryData entry = new EntryData();
        entry.setType(KAFKA);
        entry.setSource(resolveTopic(jp));
        entry.setPayload(jp.getArgs());

        return execute(jp, entry);
    }

    private String resolveTopic(ProceedingJoinPoint jp) {

        Method method = ((MethodSignature) jp.getSignature()).getMethod();

        return AnnotationUtils.findAnnotation(
                        method,
                        "org.springframework.kafka.annotation.KafkaListener"
                )
                .map(annotation -> {
                    String[] topics = (String[]) AnnotationUtils.getValue(annotation, "topics");
                    return (topics != null && topics.length > 0) ? topics[0] : "unknown";
                })
                .orElse("unknown");
    }
}
