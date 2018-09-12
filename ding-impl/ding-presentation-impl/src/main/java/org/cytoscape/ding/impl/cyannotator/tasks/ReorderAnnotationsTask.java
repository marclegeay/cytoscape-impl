package org.cytoscape.ding.impl.cyannotator.tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.cytoscape.ding.impl.DGraphView;
import org.cytoscape.ding.impl.cyannotator.AnnotationTree;
import org.cytoscape.ding.impl.cyannotator.CyAnnotator;
import org.cytoscape.ding.impl.cyannotator.annotations.DingAnnotation;
import org.cytoscape.task.AbstractNetworkViewTask;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.work.TaskMonitor;

/*
 * #%L
 * Cytoscape Ding View/Presentation Impl (ding-presentation-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2018 The Cytoscape Consortium
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

public class ReorderAnnotationsTask extends AbstractNetworkViewTask {

	private final List<DingAnnotation> annotations;
	private final String canvasName;
	
	// negative means bring forward (up), positive means send backward (down) we only move one step at a time.
	private final Integer direction;


	public ReorderAnnotationsTask(
			CyNetworkView view,
			Collection<DingAnnotation> annotations,
			String canvasName,
			Integer direction
	) {
		super(view);
		this.annotations = annotations != null ? new ArrayList<>(annotations) : Collections.emptyList();
		this.canvasName = canvasName;
		this.direction = direction;
	}

	@Override
	public void run(TaskMonitor tm) throws Exception {
		if (!(view instanceof DGraphView))
			return;
		if (annotations.isEmpty())
			return;
		if (canvasName == null && (direction == null || direction == 0))
			return;
		
		if (canvasName != null) {
			changeCanvas();
		} else if (direction != null && direction != 0) {
			reorder(direction);
		}
		
		CyAnnotator cyAnnotator = ((DGraphView)view).getCyAnnotator();
		cyAnnotator.fireAnnotationsReordered();
	}

	private void changeCanvas() {
		for (int i = annotations.size() - 1; i >= 0; i--) {
			annotations.get(i).changeCanvas(canvasName);
		}
		
		// need to rebuild the tree AFTER changing the canvas
		CyAnnotator cyAnnotator = ((DGraphView)view).getCyAnnotator();
		AnnotationTree tree = cyAnnotator.getAnnotationTree();
		tree.resetZOrder();
	}
	

	private void reorder(Integer offset) {
		CyAnnotator cyAnnotator = ((DGraphView)view).getCyAnnotator();
		AnnotationTree tree = cyAnnotator.getAnnotationTree();
		
		tree.shift(offset, annotations);
		tree.resetZOrder();
	}
	
}
