/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package flatgui.core.awt;

import flatgui.core.IFGContainer;
import flatgui.core.IFGContainerHost;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

/**
 * @author Denis Lebedev
 */
public class FGAWTContainerHost implements IFGContainerHost<Component>
{
    @Override
    public Component hostContainer(IFGContainer container)
    {
        HostComponent c = new HostComponent(container);
        ActionListener eventFedCallBack = c.getEventFedCallback();
        Consumer<Object> inputEventConsumer = container.connect(eventFedCallBack, c);
        c.setInputEventConsumer(inputEventConsumer);
        return c;
    }
}