package org.cytoscape.task.internal.table;

/*
 * #%L
 * Cytoscape Core Task Impl (core-task-impl)
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableManager;
import org.cytoscape.model.CyTableUtil;
import org.cytoscape.work.AbstractTask;

public abstract class AbstractTableDataTask extends AbstractTask {
	CyTableManager cyTableManager;

	AbstractTableDataTask(CyTableManager cyTableManager) {
		this.cyTableManager = cyTableManager;
	}

	public Map<String, Object> getCyIdentifierData(CyTable table,
	                                               CyIdentifiable id, 
	                                               List<String> columnList) {
		if (id == null) return null;
	
		if (columnList == null)
			columnList = new ArrayList<String>(CyTableUtil.getColumnNames(table));
		
		return getDataFromTable(table, id.getSUID(), columnList);
	}

	public CyTable getNetworkTable(CyNetwork network, Class<? extends CyIdentifiable> type, String namespace) {
		if (namespace == null || namespace.equalsIgnoreCase(CyNetwork.DEFAULT_ATTRS) || namespace.equalsIgnoreCase("default"))
			return network.getTable(type, CyNetwork.DEFAULT_ATTRS);
		else if (namespace.equalsIgnoreCase(CyNetwork.HIDDEN_ATTRS))
			return network.getTable(type, CyNetwork.HIDDEN_ATTRS);
		else if (namespace.equalsIgnoreCase(CyNetwork.LOCAL_ATTRS))
			return network.getTable(type, CyNetwork.LOCAL_ATTRS);
		else
			return network.getTable(type, namespace);
	}


	public int setCyIdentifierData(CyTable table, CyIdentifiable id, Map<CyColumn, Object> valueMap) {
		if (id == null || valueMap.size() == 0) return 0;

		int count = 0;
		CyRow row = table.getRow(id.getSUID());
		for (CyColumn column: valueMap.keySet()) {
			Class type = column.getType();
				String name = column.getName();
			Object value = valueMap.get(column);
			if (value != null) {
				row.set(name, type.cast(value));
				count++;
			}
		}
		return count;
	}

	public void createColumn(CyTable table, String name, String typeName, String elementTypeName) {
		Class type = getType(typeName);
		Class elementType = getType(elementTypeName);

		if (table.getColumn(name) != null)
			throw new IllegalArgumentException("A column named "+name+" already exists");
		if (type.equals(List.class))
			table.createListColumn(name, elementType, false);
		else
			table.createColumn(name, type, false);
		return;
	}

	public CyTable getUnattachedTable(String tableName) {
		Set<CyTable> tables = cyTableManager.getAllTables(false);
		for (CyTable table: tables) {
			if (tableName.equalsIgnoreCase(table.getTitle()))
				return table;
		}
		return null;
	}

	public Map<String, Object> getDataFromTable(CyTable table, Object key, List<String> columnList) {
		Map<String, Object> result = new HashMap<String, Object>();
		if (table.rowExists(key)) {
			CyRow row = table.getRow(key);
			for (String column: columnList) {
				result.put(column, row.getRaw(column));
			}
		}
		return result;
	}

	public String getNodeName(CyTable table, CyNode node) {
		String name = table.getRow(node.getSUID()).get(CyNetwork.NAME, String.class);
		name += " (SUID: "+node.getSUID()+")";
		return name;
	}

	public String getEdgeName(CyTable table, CyEdge edge) {
		String name = table.getRow(edge.getSUID()).get(CyNetwork.NAME, String.class);
		name += " (SUID: "+edge.getSUID()+")";
		return name;
	}

	public String convertData(Object data) {
		if (data instanceof List)
			return convertListToString((List)data);
		else if (data instanceof Map)
			return convertMapToString((Map)data);
		else
			return data.toString();
	}

	public String convertMapToString(Map data) {
		String result = "{";
		for (Object key: data.keySet()) {
			Object v = data.get(key);
			if (v == null) continue;
			result += key.toString()+":";
			if (v instanceof List)
				result += convertListToString((List)v)+",";
			else if (v instanceof Map)
				result += convertMapToString((Map)v)+",";
			else
				result += v.toString()+",";
		}
		return result.substring(0, result.length()-1)+"}";
	}

	public String convertListToString(List<Object> data) {
		String result = "[";
		for (Object v: data) {
			result += v.toString()+",";
		}
		return result.substring(0, result.length()-1)+"]";
	}

	public String getNetworkTitle(CyNetwork network) {
		return network.getRow(network).get(CyNetwork.NAME, String.class);
	}

	Class getType(String type) {
		if ("double".equalsIgnoreCase(type))
			return Double.class;
		else if ("integer".equalsIgnoreCase(type))
			return Integer.class;
		else if ("long".equalsIgnoreCase(type))
			return Long.class;
		else if ("boolean".equalsIgnoreCase(type))
			return Boolean.class;
		else if ("string".equalsIgnoreCase(type))
			return String.class;
		else if ("list".equalsIgnoreCase(type))
			return List.class;

		return String.class;
	}

	String getType(Class type) {
		if (type.equals(Double.class))
			return "double";
		else if (type.equals(Integer.class))
			return "integer";
		else if (type.equals(Long.class))
			return "long";
		else if (type.equals(Boolean.class))
			return "boolean";
		else if (type.equals(String.class))
			return "string";
		else if (type.equals(List.class))
			return "list";
		else 
			return type.getName();
	}

}