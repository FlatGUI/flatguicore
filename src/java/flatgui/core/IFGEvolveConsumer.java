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

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Lebedev
 */
public interface IFGEvolveConsumer
{
    /**
     * @return Id Path of a component in which this consumer is interested.
     */
    List<Object> getTargetPath();

    /**
     * @return properties in which this consumer is interested
     *         or null if it is interested in all properties
     */
    Collection<Object> getTargetProperties();

    /**
     * This method is called by FlatGUI Core when Container is evolved for
     * Evolve Reason object targeted to a component identified by
     * {@link IFGEvolveConsumer#getTargetPath()} method. The call is made
     * after evolve cycle is fully finished.
     *
     * @param sessionId Session Id of the session in which Evolve Reason has been
     *                  processed by Container, or null for local desktop
     *                  applications
     * @param containerObject reference to freshly evolved Container
     * @param originalReason original reason that caused evolve cycle
     */
    void acceptEvolveResult(Object sessionId, Object containerObject, Object originalReason);
}
