/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.presentations.util;

import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.decorators.ContributingPluginDecorator;
import org.eclipse.ui.internal.presentations.PresentablePart;
import org.eclipse.ui.internal.util.Util;
import org.eclipse.ui.presentations.IPresentablePart;
import org.osgi.framework.FrameworkUtil;

/**
 * @since 3.1
 */
public final class PartInfo {
    public String name;
    public String title;
    public String contentDescription;
    public String toolTip;
    public Image image;
    public boolean dirty;
	public String bundleId;
    
    public PartInfo() {
        name = Util.ZERO_LENGTH_STRING;
        title = Util.ZERO_LENGTH_STRING;
        contentDescription = Util.ZERO_LENGTH_STRING;
        toolTip = Util.ZERO_LENGTH_STRING;
        image = null;
    }
    
    public PartInfo(IPresentablePart part) {
        set(part);
    }
    
    public void set(IPresentablePart part) {
        name = part.getName();
        title = part.getTitle();
        contentDescription = part.getTitleStatus();
        image = part.getTitleImage();
        toolTip = part.getTitleToolTip();
        dirty = part.isDirty();
		bundleId = null;
		if (part instanceof PresentablePart
				&& PlatformUI.getWorkbench().getDecoratorManager().getLabelDecorator(
						ContributingPluginDecorator.ID) != null) {
			PresentablePart pp = (PresentablePart) part;
			if (pp.getPane() != null) {
				IWorkbenchPart p = pp.getPane().getPartReference().getPart(false);
				if (p != null) {
					bundleId = FrameworkUtil.getBundle(p.getClass()).getSymbolicName();
				}
			}
		}
    }
}
