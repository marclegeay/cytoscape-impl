package org.cytoscape.tableimport.internal;

import static org.cytoscape.io.DataCategory.NETWORK;
import static org.cytoscape.io.DataCategory.TABLE;
import static org.cytoscape.tableimport.internal.util.IconUtil.COLORS_3;
import static org.cytoscape.tableimport.internal.util.IconUtil.LAYERED_IMPORT_TABLE;
import static org.cytoscape.work.ServiceProperties.COMMAND;
import static org.cytoscape.work.ServiceProperties.COMMAND_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_EXAMPLE_JSON;
import static org.cytoscape.work.ServiceProperties.COMMAND_LONG_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_NAMESPACE;
import static org.cytoscape.work.ServiceProperties.COMMAND_SUPPORTS_JSON;
import static org.cytoscape.work.ServiceProperties.INSERT_SEPARATOR_BEFORE;
import static org.cytoscape.work.ServiceProperties.IN_TOOL_BAR;
import static org.cytoscape.work.ServiceProperties.LARGE_ICON_ID;
import static org.cytoscape.work.ServiceProperties.MENU_GRAVITY;
import static org.cytoscape.work.ServiceProperties.PREFERRED_MENU;
import static org.cytoscape.work.ServiceProperties.TITLE;
import static org.cytoscape.work.ServiceProperties.TOOLTIP;
import static org.cytoscape.work.ServiceProperties.TOOLTIP_LONG_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.TOOL_BAR_GRAVITY;

import java.awt.Font;
import java.util.Properties;

import org.cytoscape.application.swing.events.CytoPanelComponentSelectedListener;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.io.util.StreamUtil;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.tableimport.internal.io.WildCardCyFileFilter;
import org.cytoscape.tableimport.internal.task.ImportAttributeTableReaderFactory;
import org.cytoscape.tableimport.internal.task.ImportNetworkTableReaderFactory;
import org.cytoscape.tableimport.internal.task.ImportNoGuiNetworkReaderFactory;
import org.cytoscape.tableimport.internal.task.ImportNoGuiTableReaderFactory;
import org.cytoscape.tableimport.internal.task.ImportTableDataTaskFactoryImpl;
import org.cytoscape.tableimport.internal.task.LoadTableFileTaskFactoryImpl;
import org.cytoscape.tableimport.internal.task.LoadTableURLTaskFactoryImpl;
import org.cytoscape.tableimport.internal.task.TableImportContext;
import org.cytoscape.tableimport.internal.tunable.AttributeMappingParametersHandlerFactory;
import org.cytoscape.tableimport.internal.tunable.NetworkTableMappingParametersHandlerFactory;
import org.cytoscape.tableimport.internal.util.ImportType;
import org.cytoscape.task.edit.ImportDataTableTaskFactory;
import org.cytoscape.task.read.LoadTableFileTaskFactory;
import org.cytoscape.task.read.LoadTableURLTaskFactory;
import org.cytoscape.util.swing.IconManager;
import org.cytoscape.util.swing.TextIcon;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.swing.GUITunableHandlerFactory;
import org.osgi.framework.BundleContext;

/*
 * #%L
 * Cytoscape Table Import Impl (table-import-impl)
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

public class CyActivator extends AbstractCyActivator {

	private static float LARGE_ICON_FONT_SIZE = 32f;
	private static int LARGE_ICON_SIZE = 32;
	
	private Font iconFont;
	
    @Override
    public void start(BundleContext bc) {
        final CyServiceRegistrar serviceRegistrar = getService(bc, CyServiceRegistrar.class);
        final StreamUtil streamUtil = getService(bc, StreamUtil.class);
        final IconManager iconManager = getService(bc, IconManager.class);

        iconFont = iconManager.getIconFont("cytoscape-3", LARGE_ICON_FONT_SIZE);
        
        final TableImportContext tableImportContext = new TableImportContext();
        
		{
			// ".xls"
			WildCardCyFileFilter filter = new WildCardCyFileFilter(
					new String[] { "xls", "xlsx" },
					new String[] { "application/excel" },
					"Excel",
					TABLE,
					streamUtil
			);
			ImportAttributeTableReaderFactory factory =
					new ImportAttributeTableReaderFactory(filter, tableImportContext, serviceRegistrar);
			Properties props = new Properties();
			props.setProperty("readerDescription", "Attribute Table file reader");
			props.setProperty("readerId", "attributeTableReader");
			registerService(bc, factory, InputStreamTaskFactory.class, props);
		}
		{
			// ".txt"
			WildCardCyFileFilter filter = new WildCardCyFileFilter(
					new String[] { "csv", "tsv", "txt", "tab", "net", "" },
					new String[] { "text/csv", "text/tab-separated-values", "text/plain", "" },
					"Comma or Tab Separated Value",
					TABLE,
					streamUtil
			);
			filter.setBlacklist("xml", "xgmml", "rdf", "owl", "zip", "rar", "jar", "doc", "docx", "ppt", "pptx",
					"pdf", "jpg", "jpeg", "gif", "png", "svg", "tiff", "ttf", "mp3", "mp4", "mpg", "mpeg",
					"exe", "dmg", "iso", "cys");

			ImportAttributeTableReaderFactory factory =
					new ImportAttributeTableReaderFactory(filter, tableImportContext, serviceRegistrar);
			Properties props = new Properties();
			props.setProperty("readerDescription", "Attribute Table file reader");
			props.setProperty("readerId", "attributeTableReader_txt");
			registerService(bc, factory, InputStreamTaskFactory.class, props);
		}
		{
//			BasicCyFileFilter filter = new BasicCyFileFilter(
//					new String[] { "obo" },
//					new String[] { "text/obo" },
//					"OBO",
//					NETWORK,
//					streamUtil
//			);
//			OBONetworkReaderFactory factory = new OBONetworkReaderFactory(filter, serviceRegistrar);
//			Properties props = new Properties();
//			props.setProperty("readerDescription", "Open Biomedical Ontology (OBO) file reader");
//			props.setProperty("readerId", "oboReader");
//			registerService(bc, factory, InputStreamTaskFactory.class, props);
//
//			ImportOntologyAndAnnotationAction action = new ImportOntologyAndAnnotationAction(factory, serviceRegistrar);
//			registerService(bc, action, CyAction.class);
		}
		{
			// "txt"
			WildCardCyFileFilter filter = new WildCardCyFileFilter(
					new String[] { "csv", "tsv", "txt", "" },
					new String[] { "text/csv", "text/tab-separated-values", "text/plain", "" },
					"Comma or Tab Separated Value", NETWORK,
					streamUtil
			);
			filter.setBlacklist("xml", "xgmml", "rdf", "owl", "zip", "rar", "jar", "doc", "docx", "ppt", "pptx",
					"pdf", "jpg", "jpeg", "gif", "png", "svg", "tiff", "ttf", "mp3", "mp4", "mpg", "mpeg",
					"exe", "dmg", "iso", "cys");

			ImportNetworkTableReaderFactory factory = new ImportNetworkTableReaderFactory(filter, serviceRegistrar);
			Properties props = new Properties();
			props.setProperty("readerDescription", "Network Table file reader");
			props.setProperty("readerId", "networkTableReader_txt");
			registerService(bc, factory, InputStreamTaskFactory.class, props);
		}
		{
			// ".xls"
			WildCardCyFileFilter filter = new WildCardCyFileFilter(
					new String[] { "xls", "xlsx" },
					new String[] { "application/excel" },
					"Excel",
					NETWORK,
					streamUtil
			);
			ImportNetworkTableReaderFactory factory = new ImportNetworkTableReaderFactory(filter, serviceRegistrar);
			Properties props = new Properties();
			props.setProperty("readerDescription", "Network Table file reader");
			props.setProperty("readerId", "networkTableReader_xls");
			registerService(bc, factory, InputStreamTaskFactory.class, props);
		}
		{
			AttributeMappingParametersHandlerFactory factory =
					new AttributeMappingParametersHandlerFactory(ImportType.TABLE_IMPORT, tableImportContext, serviceRegistrar);
			registerService(bc, factory, GUITunableHandlerFactory.class);
		}
		{
			NetworkTableMappingParametersHandlerFactory factory = 
					new NetworkTableMappingParametersHandlerFactory(ImportType.NETWORK_IMPORT, tableImportContext, serviceRegistrar);
			registerService(bc, factory, GUITunableHandlerFactory.class);
		}
		{
			TaskFactory factory = new ImportNoGuiTableReaderFactory(false, tableImportContext, serviceRegistrar);
			Properties props = new Properties();
			props.setProperty(COMMAND, "import file");
			props.setProperty(COMMAND_NAMESPACE, "table");
			props.setProperty(COMMAND_DESCRIPTION, "Import a table from a file");
			props.setProperty(COMMAND_LONG_DESCRIPTION, "This uses a long list of input parameters to specify the attributes of the table, the mapping keys, and the destination table for the input.");
			props.setProperty(COMMAND_SUPPORTS_JSON, "true");
			props.setProperty(COMMAND_EXAMPLE_JSON, "{\"mappedTables\":[101,102]}");
			// Register the service as a TaskFactory for commands
			registerService(bc, factory, TaskFactory.class, props);
		}
		{
			TaskFactory importURLTableFactory = new ImportNoGuiTableReaderFactory(true, tableImportContext, serviceRegistrar);
			Properties props = new Properties();
			props.setProperty(COMMAND, "import url");
			props.setProperty(COMMAND_NAMESPACE, "table");
			props.setProperty(COMMAND_DESCRIPTION, "Import a table from a URL");
			props.setProperty(COMMAND_LONG_DESCRIPTION, "Similar to Import Table this uses a long list of input parameters to specify the attributes of the table, the mapping keys, and the destination table for the input.");
			props.setProperty(COMMAND_SUPPORTS_JSON, "true");
			props.setProperty(COMMAND_EXAMPLE_JSON, "{\"mappedTables\":[101,102]}");
			// Register the service as a TaskFactory for commands
			registerService(bc, importURLTableFactory, TaskFactory.class, props);
		}
//		{
//			TaskFactory mapColumnTaskFactory = new ImportNoGuiTableReaderFactory(true, serviceRegistrar);
//			Properties props = new Properties();
//			props.setProperty(COMMAND, "map column");
//			props.setProperty(COMMAND_NAMESPACE, "table");
//			props.setProperty(COMMAND_DESCRIPTION, "Map column content from one namespace to another");
//			props.setProperty(COMMAND_LONG_DESCRIPTION, "Uses the BridgeDB service to look up analogous identifiers from a wide selection of other databases");
//			props.setProperty(COMMAND_SUPPORTS_JSON, "true");
//			// Register the service as a TaskFactory for commands
//			registerService(bc, mapColumnTaskFactory, MapColumnTaskFactory.class, props);
//		}
		{
			TaskFactory factory = new ImportNoGuiNetworkReaderFactory(false, tableImportContext, serviceRegistrar);
			Properties props = new Properties();
			props.setProperty(COMMAND, "import file");
			props.setProperty(COMMAND_NAMESPACE, "network");
			props.setProperty(COMMAND_DESCRIPTION, "Import a network from a file");
			props.setProperty(COMMAND_LONG_DESCRIPTION,
			                  "Import a new network from a tabular formatted file type "+
                        "(e.g. ``csv``, ``tsv``, ``Excel``, etc.).  Use ``network load file`` "+
                        "to load network formatted files.  This command will create a "+
                        "new network collection if no current network collection is selected, otherwise "+
                        "it will add the network to the current collection. The SUIDs of the new networks "+
                        "and views are returned.");
			props.setProperty(COMMAND_SUPPORTS_JSON, "true");
			props.setProperty(COMMAND_EXAMPLE_JSON, ImportNoGuiNetworkReaderFactory.JSON_EXAMPLE);
			// Register the service as a TaskFactory for commands
			registerService(bc, factory, TaskFactory.class, props);
		}
		{
			TaskFactory factory = new ImportNoGuiNetworkReaderFactory(true, tableImportContext, serviceRegistrar);
			Properties props = new Properties();
			props.setProperty(COMMAND, "import url");
			props.setProperty(COMMAND_NAMESPACE, "network");
			props.setProperty(COMMAND_DESCRIPTION, "Import a network from a URL");
			props.setProperty(COMMAND_LONG_DESCRIPTION,
			                  "Import a new network from a URL that points to a tabular formatted file type "+
                        "(e.g. ``csv``, ``tsv``, ``Excel``, etc.).  Use ``network load url`` "+
                        "to load network formatted files.  This command will create a "+
                        "new network collection if no current network collection is selected, otherwise "+
                        "it will add the network to the current collection. The SUIDs of the new networks "+
                        "and views are returned.");
			props.setProperty(COMMAND_SUPPORTS_JSON, "true");
			props.setProperty(COMMAND_EXAMPLE_JSON, ImportNoGuiNetworkReaderFactory.JSON_EXAMPLE);
			// Register the service as a TaskFactory for commands
			registerService(bc, factory, TaskFactory.class, props);
		}
		{
			LoadTableFileTaskFactoryImpl factory = new LoadTableFileTaskFactoryImpl(tableImportContext, serviceRegistrar);
			
			TextIcon icon = new TextIcon(LAYERED_IMPORT_TABLE, iconFont, COLORS_3, LARGE_ICON_SIZE, LARGE_ICON_SIZE, 1);
			String iconId = "cy::IMPORT_TABLE";
			iconManager.addIcon(iconId, icon);
			
			Properties props = new Properties();
			props.setProperty(PREFERRED_MENU, "File.Import[23.0]");		//.Import.Table[23.0]
			props.setProperty(INSERT_SEPARATOR_BEFORE, "true");
			props.setProperty(MENU_GRAVITY, "5.1");
			props.setProperty(TOOL_BAR_GRAVITY, "2.1");
			props.setProperty(TITLE, "Table from File...");
			props.setProperty(LARGE_ICON_ID, iconId);
			props.setProperty(IN_TOOL_BAR, "true");
			props.setProperty(TOOLTIP, "Import Table from File");
			props.setProperty(TOOLTIP_LONG_DESCRIPTION, "Reads a table from the file system and adds it to the current session.");
			props.setProperty(COMMAND_LONG_DESCRIPTION, "Reads a table from the file system.  Requires a string containing the absolute path of the file. Returns the SUID of the table created.");
			props.setProperty(COMMAND_SUPPORTS_JSON, "true");
			props.setProperty(COMMAND_EXAMPLE_JSON, "{\"mappedTables\": [101,102]}");
			registerService(bc, factory, TaskFactory.class, props);
			registerService(bc, factory, LoadTableFileTaskFactory.class, props);
			registerService(bc, factory, CytoPanelComponentSelectedListener.class);
		}
		{
			LoadTableURLTaskFactoryImpl factory = new LoadTableURLTaskFactoryImpl(tableImportContext, serviceRegistrar);
			Properties props = new Properties();
			props.setProperty(PREFERRED_MENU, "File.Import[23.0]");			//.Table[23.0]
			props.setProperty(MENU_GRAVITY, "6.0");
			props.setProperty(TITLE, "Table from URL...");
			props.setProperty(TOOLTIP, "Import Table From URL");
			props.setProperty(COMMAND_LONG_DESCRIPTION, "Reads a table from the Internet.  Requires a valid URL pointing to the file. Returns the SUID of the table created.");
			props.setProperty(COMMAND_SUPPORTS_JSON, "true");
			props.setProperty(COMMAND_EXAMPLE_JSON, "{\"mappedTables\": [101,102]}");
			registerService(bc, factory, TaskFactory.class, props);
			registerService(bc, factory, LoadTableURLTaskFactory.class, props);
		}
		{
			ImportTableDataTaskFactoryImpl factory = new ImportTableDataTaskFactoryImpl(tableImportContext, serviceRegistrar);
			registerService(bc, factory, ImportDataTableTaskFactory.class);
		}
    }
}
