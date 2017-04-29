/*
 * Copyright (c) 2017 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package flatgui.run;

import clojure.lang.Var;
import flatgui.core.engine.ui.FGAWTAppContainer;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/**
 * @author Denis Lebedev
 */
public class FGDesktopRunner
{
    public static FGAWTAppContainer runDesktop(
            String containerNs, String containerName, String windowTitle, Image windowIcon, Consumer<Throwable> exceptionHandler)
    {
        Var containerVar = FGRunUtil.getVar(containerNs, containerName);
        return runDesktop(containerVar, windowTitle, windowIcon, exceptionHandler);
    }

    public static FGAWTAppContainer runDesktop(Var containerVar, String windowTitle, Image windowIcon, Consumer<Throwable> exceptionHandler)
    {
        FGAWTAppContainer appContainer = FGAWTAppContainer.createAndInit(containerVar);

        EventQueue.invokeLater(() -> {
            try
            {
                Frame frame = new Frame(windowTitle);
                frame.setSize(600, 400);
                frame.setLocation(400, 300);
                frame.setLayout(new BorderLayout());
                if (windowIcon != null)
                {
                    frame.setIconImage(windowIcon);
                }

                Component awtComponent = appContainer.getComponent();

                frame.add(awtComponent, BorderLayout.CENTER);
                frame.addWindowListener(new WindowAdapter()
                {
                    public void windowClosing(WindowEvent we)
                    {
                        appContainer.unInitialize();
                        System.exit(0);
                    }
                });
                frame.setVisible(true);
                awtComponent.requestFocusInWindow();

                awtComponent.repaint();
            }
            catch(Exception ex)
            {
                if (exceptionHandler != null)
                {
                    exceptionHandler.accept(ex);
                }
                else
                {
                    ex.printStackTrace();
                }
            }
        });

        return appContainer;
    }
}
