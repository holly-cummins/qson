package io.quarkus.qson.generator;

import io.quarkus.qson.QsonIgnore;
import io.quarkus.qson.QsonProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PropertyReference {
    public Method getter;
    public Method setter;
    public Type genericType;
    public Class type;
    /**
     * Method/field name without get/set
     *
     */
    public String javaName;
    /**
     * JSON property name
     *
     */
    public String jsonName;

    private QsonProperty fieldAnnotation;
    private QsonProperty getterAnnotation;
    private QsonProperty setterAnnotation;

    public static List<PropertyReference> getProperties(Class type) {
        if (type.equals(Object.class)) return Collections.emptyList();
        Map<String, PropertyReference> properties = new HashMap<>();
        Set<String> ignored = new HashSet<>();
        for (Method m : type.getMethods()) {
            if (isSetter(m)) {
                String javaName;
                if (m.getName().length() > 4) {
                    javaName = Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
                } else {
                    javaName = m.getName().substring(3).toLowerCase();
                }
                if (ignored.contains(javaName)) continue;
                if (m.isAnnotationPresent(QsonIgnore.class)) {
                    ignored.add(javaName);
                    properties.remove(javaName);
                    continue;
                };

                Class paramType = m.getParameterTypes()[0];
                Type paramGenericType = m.getGenericParameterTypes()[0];
                PropertyReference ref = properties.get(javaName);
                if (ref != null) {
                    if (ref.setter != null) {
                        throw new RuntimeException("Duplicate setter methods: " + type.getName() + "." + m.getName());
                    }
                    if (!ref.type.equals(paramType) || !ref.genericType.equals(paramGenericType)) {
                        throw new RuntimeException("Type mismatch between getter and setter methods: "+ type.getName() + "." + m.getName());
                    }
                } else {
                    ref = new PropertyReference();
                    ref.type = paramType;
                    ref.genericType = paramGenericType;
                    ref.javaName = javaName;
                    ref.jsonName = javaName;
                    properties.put(javaName, ref);
                }
                ref.setter = m;
                ref.setterAnnotation = m.getAnnotation(QsonProperty.class);
            } else if (isGetter(m)) {
                String javaName;
                if (m.getName().startsWith("is")) {
                    if (m.getName().length() > 3) {
                        javaName = Character.toLowerCase(m.getName().charAt(2)) + m.getName().substring(3);
                    } else {
                        javaName = m.getName().substring(2).toLowerCase();
                    }

                } else {
                    if (m.getName().length() > 4) {
                        javaName = Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
                    } else {
                        javaName = m.getName().substring(3).toLowerCase();
                    }
                }
                if (ignored.contains(javaName)) continue;
                if (m.isAnnotationPresent(QsonIgnore.class)) {
                    ignored.add(javaName);
                    properties.remove(javaName);
                    continue;
                };
                Class mType = m.getReturnType();
                Type mGenericType = m.getGenericReturnType();
                PropertyReference ref = properties.get(javaName);
                if (ref != null) {
                    if (ref.getter != null) {
                        throw new RuntimeException("Duplicate getter methods: " + type.getName() + "." + m.getName());
                    }
                    if (!ref.type.equals(mType) || !ref.genericType.equals(mGenericType)) {
                        throw new RuntimeException("Type mismatch between getter and setter methods: "+ type.getName() + "." + m.getName());
                    }
                } else {
                    ref = new PropertyReference();
                    ref.type = mType;
                    ref.genericType = mGenericType;
                    ref.javaName = javaName;
                    ref.jsonName = javaName;
                    properties.put(javaName, ref);
                }
                ref.getter = m;
                ref.getterAnnotation = m.getAnnotation(QsonProperty.class);
            }
        }
        Class target = type;
        while (target != null && !target.equals(Object.class)) {
            for (Field field : target.getDeclaredFields()) {
                PropertyReference ref = properties.get(field.getName());
                if (ref == null) continue;
                if (ignored.contains(field.getName())) continue;
                if (field.isAnnotationPresent(QsonIgnore.class)) {
                    properties.remove(field.getName());
                    continue;
                }
                if (field.isAnnotationPresent(QsonProperty.class)) {
                    QsonProperty property = field.getAnnotation(QsonProperty.class);
                    ref.fieldAnnotation = property;
                }
            }
            target = target.getSuperclass();
        }
        for (PropertyReference ref : properties.values()) {
            QsonProperty property = null;
            if (ref.fieldAnnotation != null) {
                if (property != null) {
                    throw new RuntimeException("Conflicting @JsonProperty annotations between field and setter/getter methods: " + ref.javaName);
                }
                property = ref.fieldAnnotation;

            }
            if (ref.getterAnnotation != null) {
                if (property != null) {
                    throw new RuntimeException("Conflicting @JsonProperty annotations between field and setter/getter methods: " + ref.javaName);
                }
                property = ref.getterAnnotation;

            }
            if (ref.setterAnnotation != null) {
                if (property != null) {
                    throw new RuntimeException("Conflicting @JsonProperty annotations between field and setter/getter methods: " + ref.javaName);
                }
                property = ref.setterAnnotation;

            }
            if (property != null) {
                if (property.serialization() == QsonProperty.Serialization.DESERIALIZED_ONLY) {
                    ref.getter = null;
                } else if (property.serialization() == QsonProperty.Serialization.SERIALIZED_ONLY) {
                    ref.setter = null;
                }
                if (!property.value().isEmpty()) ref.jsonName = property.value();
            }
        }
        return new ArrayList<>(properties.values());
    }

    static boolean isSetter(Method m) {
        return !Modifier.isStatic(m.getModifiers()) && m.getName().startsWith("set") && m.getName().length() > "set".length()
                && m.getParameterCount() == 1;
    }

    static boolean isGetter(Method m) {
        return !Modifier.isStatic(m.getModifiers()) && ((m.getName().startsWith("get") && m.getName().length() > "get".length()) || (m.getName().startsWith("is")) && m.getName().length() > "is".length())
                && m.getParameterCount() == 0 && !m.getReturnType().equals(void.class)
                && !m.getDeclaringClass().equals(Object.class);
    }


}
