package org.cytoscape.webservice.psicquic;

import java.net.URI;

import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.task.create.CreateNetworkViewTaskFactory;
import org.cytoscape.util.swing.OpenBrowser;
import org.cytoscape.webservice.AbstractWebServiceClientTest;
import org.cytoscape.work.swing.DialogTaskManager;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PSICQUICClientTest {

	@Mock
	private OpenBrowser openBrowser;
	@Mock
	private DialogTaskManager tm;
	@Mock
	CyNetworkFactory cyNetworkFactoryServiceRef;
	@Mock
	CyNetworkManager cyNetworkManagerServiceRef;
	@Mock
	CreateNetworkViewTaskFactory createViewTaskFactoryServiceRef;

	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);

//		final String uriString = "http://www.ebi.ac.uk/Tools/webservices/psicquic/registry/registry";
//		this.locationUri = new URI(uriString);
//		this.description = "PSICUIC Web Service Client";
//		this.displayName = "PSICQUIC";
//		this.queryObject = "brca1 AND brca2";
		
		// Enable this test only when you need to actual connection to the registry.
//		client = new PSICQUICWebServiceClient(uriString, displayName, description, cyNetworkFactoryServiceRef,
//				cyNetworkManagerServiceRef, tm, createViewTaskFactoryServiceRef, openBrowser);
	}
}
