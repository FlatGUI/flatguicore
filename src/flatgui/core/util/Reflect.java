/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */
package flatgui.core.util;

import java.lang.reflect.Field;

/**
 * @author Denis Lebedev
 */
public class Reflect
{
    public static Field getField(Class<?> cl, String fieldName) throws NoSuchFieldException
    {
        Field field = null;
        do
        {
            try
            {
                field = cl.getDeclaredField(fieldName);
            }
            catch (NoSuchFieldException ex)
            {
                cl = cl.getSuperclass();
            }
        }
        while (field == null && cl != null);

        if (field == null)
        {
            throw new NoSuchFieldException(fieldName);
        }
        field.setAccessible(true);
        return field;
    }

    public static Object getFieldValue(Object obj, String fieldName) throws NoSuchFieldException, IllegalAccessException
    {
        return getField(obj.getClass(), fieldName).get(obj);
    }
}