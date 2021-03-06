package org.cytoscape.task.internal.select;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.cytoscape.event.CyEventHelper;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.events.RowSetRecord;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.hide.HideTaskFactory;
import org.cytoscape.task.hide.UnHideTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;

/*
 * #%L
 * Cytoscape Core Task Impl (core-task-impl)
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

public final class SelectUtils {
	
	private final CyServiceRegistrar serviceRegistrar;

	public SelectUtils(CyServiceRegistrar serviceRegistrar) {
		this.serviceRegistrar = serviceRegistrar;
	}

	public void setSelectedNodes(final CyNetwork network, final Collection<CyNode> nodes, final boolean select) {
		setSelected(network,nodes, select, network.getDefaultNodeTable());
	}

	public void setSelectedEdges(final CyNetwork network, final Collection<CyEdge> edges, final boolean select) {
		setSelected(network,edges, select, network.getDefaultEdgeTable());
	}

	private void setSelected(final CyNetwork network, final Collection<? extends CyIdentifiable> objects, 
	                         final boolean select, final CyTable table) {
		// Don't autobox
		final Boolean value;
		
		if (select)
			value = Boolean.TRUE;
		else
			value = Boolean.FALSE;

		// Disable all events from our table
		CyEventHelper eventHelper = serviceRegistrar.getService(CyEventHelper.class);
		eventHelper.silenceEventSource(table);

		// Create the RowSetRecord collection
		List<RowSetRecord> rowsChanged = new ArrayList<>();

		// The list of objects will be all nodes or all edges
		for (final CyIdentifiable nodeOrEdge : objects) {
			CyRow row = table.getRow(nodeOrEdge.getSUID());

			if (row != null) {
				row.set(CyNetwork.SELECTED, value);
				rowsChanged.add(new RowSetRecord(row, CyNetwork.SELECTED, value, value));
			}
		}

		// Enable all events from our table
		eventHelper.unsilenceEventSource(table);

		RowsSetEvent event = new RowsSetEvent(table, rowsChanged);
		eventHelper.fireEvent(event);
	}
	
	public void setVisible(CyNetworkView networkView, Collection<CyNode> selectedNodes, Collection<CyEdge> selectedEdges) {
		CyNetwork network = networkView.getModel();
		HideTaskFactory hideFactory = serviceRegistrar.getService(HideTaskFactory.class);
		TaskIterator hideTasks = hideFactory.createTaskIterator(networkView, network.getNodeList(), network.getEdgeList());
		
		UnHideTaskFactory unhideFactory = serviceRegistrar.getService(UnHideTaskFactory.class);
		TaskIterator unhideTasks = unhideFactory.createTaskIterator(networkView, selectedNodes, selectedEdges);
		
		TaskIterator taskIterator = new TaskIterator();
		taskIterator.append(hideTasks);
		taskIterator.append(unhideTasks);
		
		SynchronousTaskManager<?> taskManager = serviceRegistrar.getService(SynchronousTaskManager.class);
		taskManager.execute(taskIterator);
	}
}
