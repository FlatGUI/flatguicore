/*
 * Copyright Denys Lebediev
 */
package flatgui.run;

import clojure.lang.Keyword;
import clojure.lang.Symbol;
import clojure.lang.Var;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Denis Lebedev
 */
public class FGRunUtil
{
    public static Var getVar(String ns, String name)
    {
        Var require = clojure.lang.RT.var("clojure.core", "require");
        require.invoke(Symbol.intern(ns));
        Var v = clojure.lang.RT.var(ns, name);
        return v;
    }

    public static Image loadIconFromClasspath(String resource, Consumer<Throwable> exceptionHandler)
    {
        try
        {
            return resource != null ? ImageIO.read(ClassLoader.getSystemResource(resource)) : null;
        }
        catch (IOException ex)
        {
            if (exceptionHandler != null)
            {
                exceptionHandler.accept(ex);
            }
            else
            {
                ex.printStackTrace();
            }
            return null;
        }
    }

    public static java.util.List<Object> toPath(String... stringIds)
    {
        return Arrays.stream(stringIds)
                .map(Keyword::intern)
                .collect(Collectors.toList());
    }
}
