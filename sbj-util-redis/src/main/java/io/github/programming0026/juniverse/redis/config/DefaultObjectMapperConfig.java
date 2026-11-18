package io.github.programming0026.juniverse.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.DefaultTypeResolverBuilder;

/**
 *
 * <p>Builds a Jackson 3 {@link JsonMapper}. Jackson 3 mappers are immutable, so everything is
 * configured in the constructor.
 * Support for {@code java.time} and {@code Optional} is built into Jackson 3 (there is no module to register).
 *
 * @author Java Universe
 */
public class DefaultObjectMapperConfig implements ObjectMapperConfig {

    /** Value in the package list that allows any class. */
    public static final String ANY_PACKAGE = "*";

    /** Trusted packages; {@code null} if any class is allowed. */
    private final List<String> trustedPackages;

    /**
     * Creates the configuration without class restrictions: any class name that appears
     * in the JSON can be instantiated. This is the behavior prior to version 3 and is only
     * suitable if nobody else can write to that Redis.
     */
    public DefaultObjectMapperConfig() {
        this.trustedPackages = null;
    }

    /**
     * Creates the configuration allowing only classes from the given packages, in addition to the
     * basic types of {@code java.lang}, {@code java.util}, {@code java.time} and {@code java.math}.
     *
     * @param trustedPackages trusted packages; if it contains {@value #ANY_PACKAGE}, any
     *                        class is allowed
     */
    public DefaultObjectMapperConfig(Collection<String> trustedPackages) {
        this.trustedPackages = trustedPackages.contains(ANY_PACKAGE) ? null : List.copyOf(trustedPackages);
    }

    /**
     * Configures the mapper.
     *
     * @return object mapper
     */
    @Override
    public ObjectMapper configure() {
        PolymorphicTypeValidator ptv = typeValidator();

        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)

                .changeDefaultVisibility(visibility -> visibility
                        .withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                        .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY))

                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, false)
                .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, false)
                .configure(EnumFeature.READ_ENUMS_USING_TO_STRING, false)
                .configure(EnumFeature.WRITE_ENUMS_USING_TO_STRING, false)

                .setDefaultTyping(new AllTypesResolverBuilder(ptv))
                .build();
    }

    /**
     * Stores the class name alongside all values except those that JSON already distinguishes
     * on its own (strings, booleans, integers and decimals).
     *
     * <p>Jackson's default typing ({@code NON_FINAL}) skips final classes, which is why a
     * {@code Long} came back as {@code Integer} and records, enums, dates and
     * immutable lists could not be read when the expected type was {@code Object}.</p>
     */
    private static final class AllTypesResolverBuilder extends DefaultTypeResolverBuilder {

        @Serial
        private static final long serialVersionUID = 1L;

        private AllTypesResolverBuilder(PolymorphicTypeValidator typeValidator) {
            super(typeValidator, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        }

        @Override
        public boolean useForType(JavaType type) {
            if (type.isArrayType()) {
                return true;
            }
            JavaType actual = type;
            while (actual.isReferenceType()) {
                actual = actual.getReferencedType();
            }
            Class<?> rawClass = actual.getRawClass();
            boolean naturalJsonType = rawClass.isPrimitive()
                    || rawClass == String.class
                    || rawClass == Boolean.class
                    || rawClass == Integer.class
                    || rawClass == Double.class;
            return !naturalJsonType && !TreeNode.class.isAssignableFrom(rawClass);
        }
    }

    /**
     * Decides which classes may be instantiated from the name stored in the JSON.
     *
     * @return type validator
     */
    private PolymorphicTypeValidator typeValidator() {
        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder()
                .allowIfSubTypeIsArray();
        if (trustedPackages == null) {
            return builder.allowIfSubType(Object.class).build();
        }

        builder.allowIfSubType(String.class)
                .allowIfSubType(Number.class)
                .allowIfSubType(Boolean.class)
                .allowIfSubType(Character.class)
                .allowIfSubType(Enum.class)
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.");
        for (String trustedPackage : trustedPackages) {
            String name = trustedPackage.trim();
            if (!name.isEmpty()) {
                builder.allowIfSubType(name.endsWith(".") ? name : name + ".");
            }
        }
        return builder.build();
    }
}
