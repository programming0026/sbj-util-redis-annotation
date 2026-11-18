package io.github.programming0026.juniverse.redis.util;

import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
/**
 * Resolves placeholders in the text attributes of an annotation.
 * Annotations are immutable, so a synthesized copy is created in which each {@code String}
 * or {@code String[]} attribute (also inside nested annotations) goes through the resolver.
 * The other attributes are kept as they are.
 */
public final class AnnotationPlaceholderResolver {
	/**
	 * Private constructor to prevent instantiation.
	 */
	private AnnotationPlaceholderResolver() {
	}

	/**
	 * Returns a copy of the annotation with its text attributes resolved.
	 *
	 * @param annotation the original annotation
	 * @param resolver the function that resolves each text value
	 * @param <A> the annotation type
	 * @return the annotation with the resolved values
	 */
	public static <A extends Annotation> A resolve(A annotation, UnaryOperator<String> resolver) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		AnnotationUtils.getAnnotationAttributes(annotation, false, false)
				.forEach((name, value) -> attributes.put(name, resolveValue(value, resolver)));
		@SuppressWarnings("unchecked")
		Class<A> type = (Class<A>) annotation.annotationType();
		return AnnotationUtils.synthesizeAnnotation(attributes, type, null);
	}

	/**
	 * Returns a copy of the attributes with their text values resolved.
	 * Useful for attributes that Spring delivers as a map, for example in {@code ImportAware}.
	 * Nested annotations are traversed as {@link AnnotationAttributes}.
	 *
	 * @param attributes the original attributes
	 * @param resolver the function that resolves each text value
	 * @return the attributes with the resolved values
	 */
	public static AnnotationAttributes resolve(AnnotationAttributes attributes, UnaryOperator<String> resolver) {
		AnnotationAttributes resolved = new AnnotationAttributes(attributes);
		resolved.replaceAll((name, value) -> resolveAttributeValue(value, resolver));
		return resolved;
	}

	/**
	 * Resolves a value of an attribute map according to its type.
	 *
	 * @param value the attribute value
	 * @param resolver the function that resolves each text value
	 * @return the resolved value
	 */
	private static Object resolveAttributeValue(Object value, UnaryOperator<String> resolver) {
		if (value instanceof AnnotationAttributes nested) {
			return resolve(nested, resolver);
		}
		if (value instanceof AnnotationAttributes[] nestedArray) {
			AnnotationAttributes[] resolved = new AnnotationAttributes[nestedArray.length];
			for (int i = 0; i < nestedArray.length; i++) {
				resolved[i] = nestedArray[i] == null ? null : resolve(nestedArray[i], resolver);
			}
			return resolved;
		}
		return resolveValue(value, resolver);
	}

	/**
	 * Resolves an attribute value according to its type.
	 *
	 * @param value the attribute value
	 * @param resolver the function that resolves each text value
	 * @return the resolved value
	 */
	private static Object resolveValue(Object value, UnaryOperator<String> resolver) {
		if (value instanceof String text) {
			return resolver.apply(text);
		}
		if (value instanceof Annotation nested) {
			return resolve(nested, resolver);
		}
		if (value instanceof String[] || value instanceof Annotation[]) {
			int length = Array.getLength(value);
			Object resolved = Array.newInstance(value.getClass().getComponentType(), length);
			for (int i = 0; i < length; i++) {
				Array.set(resolved, i, resolveValue(Array.get(value, i), resolver));
			}
			return resolved;
		}
		return value;
	}
}
