/*
 * Copyright Denys Lebediev
 */
package flatgui.core.engine.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * @author Denis Lebedev
 */
public class FGTransferable implements Transferable
{
    private final static DataFlavor[] FLAVORS = {DataFlavor.stringFlavor};

    private final Object data_;

    public FGTransferable(Object data)
    {
        data_ = data;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors()
    {
        return FLAVORS;
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor)
    {
        return flavor == DataFlavor.stringFlavor;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
    {
        return data_;
    }
}
