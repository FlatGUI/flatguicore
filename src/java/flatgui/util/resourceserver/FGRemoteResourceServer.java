/*
 * Copyright Denys Lebediev
 */
package flatgui.util.resourceserver;

import java.awt.*;

/**
 * Image Server that routes image data to a remote service using POST,
 * and retrieves from a remote service using GET.
 *
 * @author Denis Lebedev
 */
public class FGRemoteResourceServer implements IFGResourceServer
{
    @Override
    public String storeImage(String name, Image image)
    {
        return null;
    }
}
