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

import flatgui.core.FGEvolveInputData;
import flatgui.core.FGEvolveResultData;
import flatgui.core.IFGContainer;
import flatgui.core.IFGContainerHost;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * @author Denis Lebedev
 */
public class FGAWTContainerHost implements IFGContainerHost<Component>
{
    private final HostComponent c_;

    public FGAWTContainerHost(HostComponent c)
    {
        c_ = c;
    }

    @Override
    public Component hostContainer(IFGContainer container, Set<String> fontsWithMetricsAlreadyReceived)
    {
        c_.initialize(container);
        ActionListener eventFedCallBack = c_.getEventFedCallback();
        Function<FGEvolveInputData, Future<FGEvolveResultData>> inputEventConsumer = container.connect(eventFedCallBack, c_);
        c_.setInputEventConsumer(inputEventConsumer::apply);
        return c_;
    }
}
