package io.github.programming0026.juniverse.redis.dto;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Identifies the annotation of a given type on a method.
 *
 * @param method intercepted method
 * @param annotationType annotation type
 * @author Java Universe
 */
public record AnnotationKey(Method method, Class<? extends Annotation> annotationType) {
}
