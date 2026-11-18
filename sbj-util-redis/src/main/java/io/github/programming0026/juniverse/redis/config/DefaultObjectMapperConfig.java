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
 * <p>Construye un {@link JsonMapper} de Jackson 3. Los mappers de Jackson 3 son inmutables, así que todo se
 * configura en el constructor.
 * El soporte de {@code java.time} y {@code Optional} viene integrado en Jackson 3 (no hay módulo que registrar).
 *
 * @author Java Universe
 */
public class DefaultObjectMapperConfig implements ObjectMapperConfig {

    /** Valor de la lista de paquetes que admite cualquier clase. */
    public static final String ANY_PACKAGE = "*";

    /** Paquetes de confianza; {@code null} si se admite cualquier clase. */
    private final List<String> trustedPackages;

    /**
     * Crea la configuración sin restricción de clases: cualquier nombre de clase que aparezca
     * en el JSON se puede instanciar. Es el comportamiento anterior a la versión 3 y solo es
     * adecuado si nadie más puede escribir en ese Redis.
     */
    public DefaultObjectMapperConfig() {
        this.trustedPackages = null;
    }

    /**
     * Crea la configuración admitiendo solo clases de los paquetes indicados, además de los
     * tipos básicos de {@code java.lang}, {@code java.util}, {@code java.time} y {@code java.math}.
     *
     * @param trustedPackages paquetes de confianza; si contiene {@value #ANY_PACKAGE} se admite
     *                        cualquier clase
     */
    public DefaultObjectMapperConfig(Collection<String> trustedPackages) {
        this.trustedPackages = trustedPackages.contains(ANY_PACKAGE) ? null : List.copyOf(trustedPackages);
    }

    /**
     * Configura.
     *
     * @return conversor de objeto
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
     * Guarda el nombre de la clase junto a todos los valores salvo los que JSON ya distingue
     * por sí mismo (texto, booleanos, enteros y decimales).
     *
     * <p>El tipado predeterminado de Jackson ({@code NON_FINAL}) omite las clases finales, y por
     * eso un {@code Long} volvía como {@code Integer} y los records, los enums, las fechas y las
     * listas inmutables no se podían leer cuando el tipo esperado era {@code Object}.</p>
     */
    private static final class AllTypesResolverBuilder extends DefaultTypeResolverBuilder {

        @Serial
        private static final long serialVersionUID = 1L;

        AllTypesResolverBuilder(PolymorphicTypeValidator typeValidator) {
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
     * Decide qué clases se pueden instanciar a partir del nombre guardado en el JSON.
     *
     * @return validador de tipos
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
