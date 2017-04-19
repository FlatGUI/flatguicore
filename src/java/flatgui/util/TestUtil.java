/*
 * Copyright (c) 2017 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */
package flatgui.util;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.function.Function;

/**
 * @author Denis Lebedev
 */
public class TestUtil
{
    private static final String TEST = "test";
    private static final String JAVA = "java";
    private static final String DOT = ".";
    private static final String JAVA_EXT = DOT+JAVA;

    public static int runJavaTests()
    {
        JUnitCore junit = new JUnitCore();
        int failureCount = 0;
        try
        {
            Enumeration<URL> res = ClassLoader.getSystemClassLoader().getResources("");
            while (res.hasMoreElements())
            {
                URL url = res.nextElement();
                String path = url.getPath();
                if (path.contains(TEST))
                {
                    System.out.println("Looking for unit tests in " + path);
                    failureCount += listRecursively(new File(url.toURI()), file -> runJavaTest(junit, file));
                }
            }
            return failureCount;
        }
        catch (IOException | URISyntaxException e)
        {
            e.printStackTrace();
            return -1;
        }
    }

    private static int runJavaTest(JUnitCore junit, File file)
    {
        int failureCount = 0;

        String path = file.getPath();
        int indexOfJava = path.indexOf(File.separator+JAVA+File.separator);
        if (indexOfJava >= 0)
        {
            String name = path.substring(indexOfJava + File.separator.length() + JAVA.length() + File.separator.length(), path.length()-JAVA_EXT.length());
            name = name.replace(File.separator, DOT);
            System.out.println(" ===== Trying load and run test class " + name + " === ");
            try
            {
                Class<?> clazz = Class.forName(name);
                Result result = junit.run(clazz);
                System.out.println(" -- run count: " + result.getRunCount());
                System.out.println(" -- ignore count: " + result.getIgnoreCount());
                System.out.println(" -- failure count: " + result.getFailureCount());
                System.out.println(" -- run time: " + result.getRunTime());

                if (result.getFailureCount() > 0)
                {
                    failureCount += result.getFailureCount();
                    result.getFailures().forEach(f -> {
                        System.out.println(f.toString());
                        f.getException().printStackTrace();
                    });
                }
            }
            catch(Throwable ex)
            {
                System.out.println("Failed to run test class " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        return failureCount;
    }

    private static int listRecursively(File file, Function<File, Integer> runToFailCnt)
    {
        int failCnt = 0;

        if (file.exists())
        {
            if (file.isDirectory())
            {
                File[] internalFiles = file.listFiles();
                for (File f : internalFiles)
                {
                    failCnt += listRecursively(f, runToFailCnt);
                }
            }
            else
            {
                 failCnt += runToFailCnt.apply(file).intValue();
            }
        }

        return failCnt;
    }
}
