package org.cytoscape.ding.impl.canvas;

import java.util.Set;

import org.cytoscape.ding.impl.DRenderingEngine;
import org.cytoscape.ding.impl.work.ProgressMonitor;
import org.cytoscape.graph.render.immed.GraphGraphics;
import org.cytoscape.graph.render.stateful.GraphRenderer;
import org.cytoscape.graph.render.stateful.RenderDetailFlags;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualPropertyDependency;

/*
 * #%L
 * Cytoscape Ding View/Presentation Impl (ding-presentation-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2019 The Cytoscape Consortium
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

/**
 * Canvas to be used for drawing actual network visualization
 */
public class NodeCanvas<GP extends GraphicsProvider> extends DingCanvas<GP> {

	private final VisualMappingManager vmm;
	private final DRenderingEngine re;

	public NodeCanvas(GP graphics, DRenderingEngine re) {
		super(graphics);
		this.re = re;
		this.vmm = re.getServiceRegistrar().getService(VisualMappingManager.class);
	}
	
	private Set<VisualPropertyDependency<?>> getVPDeps() {
		CyNetworkView netView = re.getViewModel();
		return vmm.getVisualStyle(netView).getAllVisualPropertyDependencies();
	}

	@Override
	public void paint(ProgressMonitor pm, RenderDetailFlags flags) {
		var dependencies = getVPDeps();
		var snapshot = re.getViewModelSnapshot();
		var graphics = new GraphGraphics(graphicsProvider); // MKTODO don't need to create a GraphGraphics object on every frame
		var edgeDetails = re.getEdgeDetails();
		var nodeDetails = re.getNodeDetails();
		
		if(pm.isCancelled())
			return;
		
		GraphRenderer.renderNodes(pm, graphics, snapshot, flags, nodeDetails, edgeDetails, dependencies);
		
		pm.done();
	}
}
