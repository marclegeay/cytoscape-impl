package org.cytoscape.internal.view.help;

/*
 * #%L
 * Cytoscape Swing Application Impl (swing-application-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2013 The Cytoscape Consortium
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */


import static org.cytoscape.application.swing.CyNetworkViewDesktopMgr.ArrangeType.*;

import org.cytoscape.application.swing.CyNetworkViewDesktopMgr.ArrangeType;
import org.cytoscape.internal.view.CyDesktopManager;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;


public class ArrangeTask extends AbstractTask {
	private ArrangeType arrange;
	private CyDesktopManager desktopMgr;

	public ArrangeTask(CyDesktopManager desktopMgr, ArrangeType arrange) {
		this.desktopMgr = desktopMgr;
		this.arrange = arrange;
	}

	public void run(TaskMonitor tm) {
		desktopMgr.arrangeWindows(arrange);
	}

	@Override
	public void cancel() {
	}
}

