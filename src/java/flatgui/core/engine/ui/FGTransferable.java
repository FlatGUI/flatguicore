/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Denis Lebedev
 */
public class FGTransferable implements Transferable
{

    private final DataFlavor[] flavors_;
    private final Map<DataFlavor, Object> data_;

    private FGTransferable(Map<DataFlavor, Object> data)
    {
        data_ = data;
        flavors_ = data.keySet().toArray(new DataFlavor[data.size()]);
    }

    public static FGTransferable createTextTransferable(String text)
    {
        Map<DataFlavor, Object> data = new HashMap<>();
        data.put(DataFlavor.stringFlavor, text);
        return new FGTransferable(data);
    }

    public static FGTransferable createImageTransferable(Image image)
    {
        Map<DataFlavor, Object> data = new HashMap<>();
        data.put(DataFlavor.imageFlavor, image);
        return new FGTransferable(data);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors()
    {
        return flavors_;
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor)
    {
        return data_.keySet().contains(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
    {
        return data_.get(flavor);
    }
}
