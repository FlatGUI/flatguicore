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

import java.awt.*;

/**
 * @author Denis Lebedev
 */
public interface IFGInteropUtil
{
    double getStringWidth(String str, String font);

    double getFontHeight(String font);

    double getFontAscent(String font);

    void setReferenceFont(String fontStr, Font font);

    String getReferenceFontStr();
}