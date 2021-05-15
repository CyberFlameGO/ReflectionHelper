package org.inventivetalent.reflection.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Helper class to set fields, methods &amp; constructors accessible
 */
public abstract class AccessUtil {

    public static final boolean VERBOSE = System.getProperty("org.inventivetalent.reflection.verbose", "false").equals("true");

    private static final Object modifiersVarHandle;
    private static final Field modifiersField;

    /**
     * Sets the field accessible and removes final modifiers
     *
     * @param field Field to set accessible
     * @return the Field
     * @throws ReflectiveOperationException (usually never)
     */

    public static Field setAccessible(Field field) throws ReflectiveOperationException {
        return setAccessible(field, false);
    }

    public static Field setAccessible(Field field, boolean readOnly) throws ReflectiveOperationException {
        return setAccessible(field, readOnly, false);
    }

    private static Field setAccessible(Field field, boolean readOnly, boolean privileged) throws ReflectiveOperationException {
        try {
            field.setAccessible(true);
        } catch (InaccessibleObjectException e) {
            if (VERBOSE) {
                System.err.println("field.setAccessible");
                e.printStackTrace();
            }
            if (!privileged) {
                return AccessController.doPrivileged((PrivilegedAction<Field>) () -> {
                    try {
                        return setAccessible(field, readOnly, true);
                    } catch (Exception e1) {
                        if (VERBOSE) {
                            System.err.println("privileged setAccessible");
                            e1.printStackTrace();
                        }
                    }
                    return field;
                });
            }
        }
        if (readOnly) {
            return field;
        }
        int modifiers = field.getModifiers();
        if (Modifier.isFinal(modifiers)) {
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, modifiers & ~Modifier.FINAL);
            } catch (NoSuchFieldException e1) {
                if (VERBOSE) {
                    System.err.println("modifiersField.setInt");
                    e1.printStackTrace();
                }
                try {
                    int newModifiers = field.getModifiers() & ~Modifier.FINAL;
                    if (modifiersVarHandle != null) {
                        ((VarHandle) modifiersVarHandle).set(field, newModifiers);
                    } else {
                        modifiersField.setInt(field, newModifiers);
                    }
                } catch (Exception e2) {
                    if (VERBOSE) {
                        System.err.println("JDK9+  modifiersField.setInt");
                        e2.printStackTrace();
                    }
                    try {
                        // https://github.com/ViaVersion/ViaVersion/blob/e07c994ddc50e00b53b728d08ab044e66c35c30f/bungee/src/main/java/us/myles/ViaVersion/bungee/platform/BungeeViaInjector.java
                        // Java 12 compatibility *this is fine*
                        Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
                        getDeclaredFields0.setAccessible(true);
                        Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
                        for (Field classField : fields) {
                            if ("modifiers".equals(classField.getName())) {
                                classField.setAccessible(true);
                                classField.set(field, modifiers & ~Modifier.FINAL);
                                break;
                            }
                        }
                    } catch (Exception e3) {
                        if (VERBOSE) {
                            System.err.println("Field/declaredFields");
                            e3.printStackTrace();
                        }
                        if (!privileged) {
                            return AccessController.doPrivileged((PrivilegedAction<Field>) () -> {
                                try {
                                    setAccessible(field, false, true);
                                } catch (Exception e) {
                                    System.err.println("privileged setAccessible");
                                    e1.printStackTrace();
                                }
                                return field;
                            });
                        }
                    }
                }
            }
            if (Modifier.isFinal(field.getModifiers())) {
                System.err.println("[ReflectionHelper] Failed to make " + field + " non-final");
            }
        }
        return field;
    }

    /**
     * Sets the method accessible
     *
     * @param method Method to set accessible
     * @return the Method
     * @throws ReflectiveOperationException (usually never)
     */
    public static Method setAccessible(Method method) throws ReflectiveOperationException {
        method.setAccessible(true);
        return method;
    }

    /**
     * Sets the constructor accessible
     *
     * @param constructor Constructor to set accessible
     * @return the Constructor
     * @throws ReflectiveOperationException (usually never)
     */
    public static Constructor setAccessible(Constructor constructor) throws ReflectiveOperationException {
        constructor.setAccessible(true);
        return constructor;
    }

    private static Object initModifiersVarHandle() {
        try {
            VarHandle.class.getName(); // Makes this method fail-fast on JDK 8
            return MethodHandles.privateLookupIn(Field.class, MethodHandles.lookup())
                    .findVarHandle(Field.class, "modifiers", int.class);
        } catch (IllegalAccessException | NoClassDefFoundError | NoSuchFieldException ignored) {}
        return null;
    }

    private static Field initModifiersField() {
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            return modifiersField;
        } catch (NoSuchFieldException ignored) {}
        return null;
    }

    static {
        modifiersVarHandle = initModifiersVarHandle();
        modifiersField = initModifiersField();
    }

}
