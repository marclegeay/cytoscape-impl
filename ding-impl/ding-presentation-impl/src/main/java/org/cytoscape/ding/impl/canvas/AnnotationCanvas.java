package org.cytoscape.ding.impl.canvas;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.cytoscape.ding.impl.DRenderingEngine;
import org.cytoscape.ding.impl.cyannotator.annotations.DingAnnotation;
import org.cytoscape.ding.impl.work.ProgressMonitor;
import org.cytoscape.graph.render.stateful.RenderDetailFlags;

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

public class AnnotationCanvas<GP extends GraphicsProvider> extends DingCanvas<GP> {
	
	private final DingAnnotation.CanvasID canvasID;
	private final DRenderingEngine re;
	private boolean showSelection = true;
	
	public AnnotationCanvas(GP graphics, DRenderingEngine re, DingAnnotation.CanvasID canvasID) {
		super(graphics);
		this.re = re;
		this.canvasID = canvasID;
	}

	public AnnotationCanvas(GP graphics, DRenderingEngine re, DingAnnotation.CanvasID canvasID, boolean showSelection) {
		this(graphics, re, canvasID);
		this.showSelection = showSelection;
	}

	public DingAnnotation.CanvasID getCanvasID() {
		return canvasID;
	}
	
	public void setShowSelection(boolean showSelection) {
		this.showSelection = showSelection;
	}

	@Override
	public void paint(ProgressMonitor pm, RenderDetailFlags flags) {
		// only paint if we have an image to paint on
		// get image graphics
		if(pm.isCancelled())
			return;
		
		Graphics2D g = graphicsProvider.getGraphics();
		if(g == null)
			return;
		
		var transform = graphicsProvider.getTransform();
		g.setTransform(transform.getAffineTransform());
		
		Rectangle2D visibleArea = transform.getNetworkVisibleAreaNodeCoords();
		
		var annotations = re.getCyAnnotator().getAnnotations(canvasID, false);
		var dpm = pm.toDiscrete(annotations.size());
		
		for(DingAnnotation a : annotations) {
			if(dpm.isCancelled()) {
				return;
			}
			if(visibleArea.intersects(a.getBounds())) {
				a.paint(g, showSelection);
			}
			dpm.increment();
		}
		
		pm.done();
		g.dispose();
	}
	
}
