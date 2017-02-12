/*
 * Copyright Denys Lebediev
 */
package flatgui.util;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

/**
 * @author Denis Lebedev
 */
public class TestUtil
{
    public static Result runClass(JUnitCore junit, Class<?> clazz)
    {
//        Class<?>[] ca = new Class[1];
//        ca[0] = clazz;
        return junit.run(clazz);
    }
}
