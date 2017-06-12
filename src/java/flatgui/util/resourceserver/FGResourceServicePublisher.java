/*
 * Copyright Denys Lebediev
 */
package flatgui.util.resourceserver;

import flatgui.core.awt.FGImageLoader;
import flatgui.core.websocket.IFGCustomServlet;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Accepts images through POST and returns through GET. To store/retrieve, utilizes
 * underling {@link IFGResourceServer}
 *
 * @author Denis Lebedev
 */
public class FGResourceServicePublisher extends HttpServlet implements IFGCustomServlet
{
    private final IFGResourceServer imageServer_;

    // TODO combine with image server?
    private final FGImageLoader imageLoader_;

    public FGResourceServicePublisher(IFGResourceServer imageServer)
    {
        imageServer_ = imageServer;
        imageLoader_ = new FGImageLoader();
    }

    @Override
    public void acceptInputEvent(Map<String, String[]> inputEvent)
    {
        // TODO get image data from the input (it shouldn't be a string) and delegate to local server
    }

    @Override
    protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        // TODO org.eclipse.jetty.websocket.api.MessageTooLargeException: Binary message size [112430] exceeds maximum size [65536]
        String imageName = req.getParameter("image");

        Image image = imageLoader_.getImage(imageName);
        if (image instanceof RenderedImage)
        {
            resp.setContentType("image/png");
            OutputStream o = resp.getOutputStream();
            ImageIO.write((RenderedImage)image, "png", o);
            o.flush();
            o.close();

            System.out.println("Served requested image: " + image);
        }
        else
        {
            System.out.println("Could NOT find requested image: " + image);
        }

    }
}
