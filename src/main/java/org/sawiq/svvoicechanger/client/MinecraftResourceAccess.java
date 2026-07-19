package org.sawiq.svvoicechanger.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MinecraftResourceAccess {
    private static final Map<Class<?>, Method> FACTORIES = new ConcurrentHashMap<>();

    private MinecraftResourceAccess() {
    }

    public static Object create(Class<?> resourceType, String namespace, String path) {
        Method factory = FACTORIES.computeIfAbsent(resourceType, MinecraftResourceAccess::findFactory);
        try {
            return factory.invoke(null, namespace, path);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to create a Minecraft resource identifier", exception);
        }
    }

    private static Method findFactory(Class<?> resourceType) {
        for (Method method : resourceType.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == resourceType
                    && parameters.length == 2
                    && parameters[0] == String.class
                    && parameters[1] == String.class) {
                method.trySetAccessible();
                return method;
            }
        }

        throw new IllegalStateException(
                "No two-string resource factory exists on " + resourceType.getName()
        );
    }
}
