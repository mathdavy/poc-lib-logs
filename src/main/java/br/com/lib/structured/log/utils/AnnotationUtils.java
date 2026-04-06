package br.com.lib.structured.log.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

public class AnnotationUtils {

    public static Optional<Annotation> findAnnotation(Method method, String className) {
        return Arrays.stream(method.getAnnotations())
                .filter(a -> a.annotationType().getName().equals(className))
                .findFirst();
    }

    public static Object getValue(Annotation annotation, String methodName) {
        try {
            Method method = annotation.annotationType().getMethod(methodName);
            return method.invoke(annotation);
        } catch (Exception e) {
            return null;
        }
    }
}
