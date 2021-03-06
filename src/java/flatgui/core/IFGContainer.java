/*
 * Copyright (c) 2015 Denys Lebediev and contributors. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */
package flatgui.core;

import clojure.lang.Keyword;

import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * @author Denys Lebediev
 *         Date: 10/1/13
 *         Time: 9:27 PM
 */
public interface IFGContainer
{
    // TODO
    public int UNIT_SIZE_PX = 64;

    String getId();

    void initialize();

    void unInitialize();

    boolean isActive();

    void addEvolveConsumer(IFGEvolveConsumer consumer);

    IFGModule getFGModule();

    IFGModule getForkedFGModule(Object evolveReason);

    IFGInteropUtil getInterop();

    // TODO(new core)
    // ContainerAccessor - no impl
    // FGContainer
    //   - hostContext is not used
    //   - eventFedCallback (callback invoked after event is passed to evolver executor service) - not used for remote impl
    //   - returns: fn that feeds event to container and returns future to get results
    Function<FGEvolveInputData, Future<FGEvolveResultData>> connect(ActionListener eventFedCallback, Object hostContext);

    <T> Future<T> submitTask(Callable<T> callable);

    Future<FGEvolveResultData> feedEvent(FGEvolveInputData inputData);

    Future<FGEvolveResultData> feedTargetedEvent(List<Keyword> targetCellIdPath, FGEvolveInputData inputData);

    Future<FGEvolveResultData> feedTargetedEvent(List<Keyword> targetCellIdPath, Object evolveReason);

    List<Keyword> getLastMouseTargetIdPath();

    // Diagnostics

    int getQueueSizeWaiting();
}
