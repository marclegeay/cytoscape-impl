package org.cytoscape.ding.impl;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.print.Printable;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.UIManager;

import org.cytoscape.ding.impl.cyannotator.annotations.DingAnnotation;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.model.CyNetworkViewSnapshot;
import org.cytoscape.view.model.View;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngine;

@SuppressWarnings("serial")
public final class BirdsEyeView2 extends Component implements RenderingEngine<CyNetwork> {

	private static final Dimension MIN_SIZE = new Dimension(180, 180);
	private final Color VIEW_WINDOW_COLOR;
	private final Color VIEW_WINDOW_BORDER_COLOR;
	
	
	// Ratio of the graph image to panel size 
	private static final double SCALE_FACTOR = 0.97;
		
	private final double[] extents = new double[4];
	
	private final DRenderingEngine re;
	private final CompositeCanvas canvas;
	private boolean contentChanged = true;
	
	
	public BirdsEyeView2(DRenderingEngine re, CyServiceRegistrar registrar) {
		this.re = re;
		
		Color c = UIManager.getColor("Table.focusCellBackground");
		VIEW_WINDOW_COLOR = new Color(c.getRed(), c.getGreen(), c.getBlue(), 60);
		c = UIManager.getColor("Table.background");
		VIEW_WINDOW_BORDER_COLOR = new Color(c.getRed(), c.getGreen(), c.getBlue(), 90);
		
		setPreferredSize(MIN_SIZE);
		setMinimumSize(MIN_SIZE);
		
		re.addContentChangeListener(new BirdsEyeViewContentChangeListener());
		
		BirdsEyeViewLOD lod = getLOD();
		canvas = new CompositeCanvas(registrar, re, lod, re.getExecutorService());
	}	
	
	private final class BirdsEyeViewContentChangeListener implements ContentChangeListener {
		/** Will be called when something is changed in the main view.  */
		@Override
		public void contentChanged() {
			contentChanged = true;
			repaint();
		}
	}
	
	private BirdsEyeViewLOD getLOD() {
		// Now draw the network
		// System.out.println("Drawing snapshot");
		BirdsEyeViewLOD bevLOD;
		if (re.getGraphLOD() instanceof DingGraphLOD)
			bevLOD = new BirdsEyeViewLOD(new DingGraphLOD((DingGraphLOD)re.getGraphLOD()));
		else
			bevLOD = new BirdsEyeViewLOD(re.getGraphLOD());

		bevLOD.setDrawEdges(true);
		return bevLOD;
	}
	
	
	/**
	 * Returns the extents of the nodes, in node coordinates.
	 */
	private boolean getNetworkExtents(double[] extents) {
		CyNetworkViewSnapshot netViewSnapshot = re.getViewModelSnapshot();
		if(netViewSnapshot.getNodeCount() == 0) {
			Arrays.fill(extents, 0.0);
			return false;
		}
		netViewSnapshot.getSpacialIndex2D().getMBR(extents);
		return true;
	}
	
	/**
	 * Adjusts the extents to also include annotations.
	 */
	private boolean adjustExtentsToIncludeAnnotations(double[] extents) {
		List<DingAnnotation> annotations = re.getCyAnnotator().getAnnotations();
		if(annotations.isEmpty())
			return false;

		for(DingAnnotation a : annotations) {
			if (a.getX() < extents[0]) 
				extents[0] = a.getX();
			if (a.getY() < extents[1]) 
				extents[1] = a.getY();
			
			double x2 = a.getX() + a.getWidth();
			double y2 = a.getY() + a.getHeight();

			if (x2 > extents[2])
				extents[2] = x2;
			if (y2 > extents[3])
				extents[3] = y2;
		}
		return true;
	}
	
	private void fitCanvasToNetwork() {
		boolean hasComponents = getNetworkExtents(extents);
		hasComponents |= adjustExtentsToIncludeAnnotations(extents);
		
		double myXCenter;
		double myYCenter;
		double myScaleFactor;
		
		if(hasComponents) {
			myXCenter = (extents[0] + extents[2]) / 2.0d;
			myYCenter = (extents[1] + extents[3]) / 2.0d;
			myScaleFactor = SCALE_FACTOR * 
			                  Math.min(((double) getWidth())  / (extents[2] - extents[0]), 
			                           ((double) getHeight()) / (extents[3] - extents[1]));
		} else {
			myXCenter = 0.0d;
			myYCenter = 0.0d;
			myScaleFactor = 1.0d;
		}
		
		canvas.setCenter(myXCenter, myYCenter);
		canvas.setScaleFactor(myScaleFactor);
	}
	
	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		contentChanged = true;
		canvas.setViewport(width, height);
	}
	
	@Override
	public void paint(Graphics g) {
		update(g);
	}
	
	@Override 
	public void update(Graphics g) {
		System.out.println("BirdsEyeView2.update()");
		if(contentChanged) {
			// render a new image
			fitCanvasToNetwork();
			canvas.paintBlocking(null);
			contentChanged = false;
		} 
		g.drawImage(canvas.getImage(), 0, 0, null);
		paintRectangle(g);
	}
	
	private void paintRectangle(Graphics g) {
		// extents of network viewable in birds-eye-view (node coords)
		double myXCenter = canvas.getTransform().getCenterX();
		double myYCenter = canvas.getTransform().getCenterY();
		double myScaleFactor = canvas.getTransform().getScaleFactor();
		
		// get extents of the main network view (node coords)
		double viewXCenter = re.getTransform().getCenterX();
		double viewYCenter = re.getTransform().getCenterY();
		double viewScaleFactor = re.getTransform().getScaleFactor();
		
		// Swing width/height of main view (image coords)
		int viewWidth  = re.getTransform().getWidth();
		int viewHeight = re.getTransform().getHeight();
		
		// Compute view area
		final int rectWidth  = (int) (myScaleFactor * (((double) viewWidth)  / viewScaleFactor));
		final int rectHeight = (int) (myScaleFactor * (((double) viewHeight) / viewScaleFactor));
		
		final double rectXCenter = (((double) getWidth())  / 2.0d) + (myScaleFactor * (viewXCenter - myXCenter));
		final double rectYCenter = (((double) getHeight()) / 2.0d) + (myScaleFactor * (viewYCenter - myYCenter));
		
		final int x = (int) (rectXCenter - (rectWidth  / 2));
		final int y = (int) (rectYCenter - (rectHeight / 2));
		
		// Draw the view area window		
		g.setColor(VIEW_WINDOW_COLOR);
		g.fillRect(x, y, rectWidth, rectHeight);
		g.setColor(VIEW_WINDOW_BORDER_COLOR);
		g.drawRect(x, y, rectWidth, rectHeight);
	}
	
	@Override
	public Dimension getMinimumSize() {
		return MIN_SIZE;
	}

	@Override
	public View<CyNetwork> getViewModel() {
		return re.getViewModel();
	}

	@Override
	public VisualLexicon getVisualLexicon() {
		return re.getVisualLexicon();
	}

	@Override
	public Properties getProperties() {
		return re.getProperties();
	}
	
	@Override
	public Printable createPrintable() {
		return re.createPrintable();
	}

	@Override
	public <V> Icon createIcon(VisualProperty<V> vp, V value, int width, int height) {
		return re.createIcon(vp, value, width, height);
	}

	@Override
	public void printCanvas(Graphics printCanvas) {
		throw new UnsupportedOperationException("Printing is not supported for Bird's eye view.");
	}

	@Override
	public void dispose() {
	}

	@Override
	public String getRendererId() {
		return DingRenderer.ID;
	}
	
}