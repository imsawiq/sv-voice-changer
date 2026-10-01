package probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Reaches classes of the voice changer and the voice mods by name. Never used
 * for Minecraft's own members, whose names differ between dev and production.
 */
final class Reflect {
    private Reflect() {
    }

    static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    static Object staticField(String owner, String name) throws ReflectiveOperationException {
        Field field = type(owner).getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    /** Calls the first method with this name and argument count; a Class target means a static call. */
    static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?> start = target instanceof Class<?> c ? c : target.getClass();
        Object receiver = target instanceof Class<?> ? null : target;
        for (Class<?> type = start; type != null; type = type.getSuperclass()) {
            Method method = find(type.getDeclaredMethods(), name, args.length);
            if (method != null) {
                method.setAccessible(true);
                return method.invoke(receiver, args);
            }
        }
        Method method = find(start.getMethods(), name, args.length);
        if (method == null) {
            throw new NoSuchMethodException(start.getName() + "." + name + "/" + args.length);
        }
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }

    private static Method find(Method[] methods, String name, int arity) {
        return Arrays.stream(methods)
                .filter(m -> m.getName().equals(name) && m.getParameterCount() == arity)
                .findFirst()
                .orElse(null);
    }
}
