/*
 * Copyright Denys Lebediev
 */
package flatgui.util.resourceserver;

import flatgui.core.awt.IFGImageLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Simple Image Server that is bound within local host.
 * Returned URI is good for local host only.<br><br>
 *
 * Image name is used as subfolder. It is assumed that
 * only one instance of image servers manages one subfolder.
 * Otherwise this implementation does not guarantee name
 * uniqueness.
 *
 * @author Denis Lebedev
 */
public class FGLocalResourceServer implements IFGResourceServer
{
    private static final String SUBFOLDER = "imagesrv";
    private static final String EXTENTION = ".png";

    @Override
    public String storeImage(String name, Image image)
    {
        RenderedImage renderedImage;
        if (image instanceof RenderedImage)
        {
            renderedImage = (RenderedImage) image;
        }
        else
        {
            renderedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = ((BufferedImage)renderedImage).createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }

        String fileName = SUBFOLDER + File.separator + name + File.separator + UUID.randomUUID().toString().replace("-", "_") + EXTENTION;
        try
        {
            File file = new File(fileName);
            File parentFire = file.getParentFile();
            if (!parentFire.exists() && !parentFire.mkdirs())
            {
                throw new IOException("Couldn't create dir: " + parentFire);
            }

            System.out.println("Storing image file '" + fileName + "'...");
            ImageIO.write(renderedImage, "png", file);
            return fileName;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return null;
        }
    }

}
