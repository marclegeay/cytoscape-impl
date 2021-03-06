package org.cytoscape.ding.impl.cyannotator.annotations;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.swing.JDialog;

import org.cytoscape.ding.impl.DRenderingEngine;
import org.cytoscape.ding.impl.cyannotator.CyAnnotator;
import org.cytoscape.ding.impl.cyannotator.utils.ViewUtils;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.presentation.annotations.Annotation;
import org.cytoscape.view.presentation.annotations.ArrowAnnotation;
import org.cytoscape.view.presentation.annotations.GroupAnnotation;

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

public abstract class AbstractAnnotation implements DingAnnotation {
	
	protected static final String ID = "id";
	protected static final String TYPE = "type";
	protected static final String ANNOTATION_ID = "uuid";
	protected static final String PARENT_ID = "parent";
	
	
	protected final CyAnnotator cyAnnotator;
	private UUID uuid = UUID.randomUUID();
	
	private Set<ArrowAnnotation> arrowList = new HashSet<>();
	protected final boolean usedForPreviews;
	protected DRenderingEngine re;
	protected CanvasID canvas;
	protected GroupAnnotationImpl groupParent;
	protected String name;
	
	// location in node coordinates
	protected double x;
	protected double y;
	protected double width;
	protected double height;
	
	protected int zOrder;
	protected Rectangle2D initialBounds;
	
	
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	/**
	 * This constructor is used to create an empty annotation
	 * before adding to a specific view.  In order for this annotation
	 * to be functional, it must be added to the AnnotationManager
	 * and setView must be called.
	 */
	protected AbstractAnnotation(DRenderingEngine re, boolean usedForPreviews) {
		this.re = re;
		this.cyAnnotator = re == null ? null : re.getCyAnnotator();
		this.usedForPreviews = usedForPreviews;
		this.canvas = CanvasID.FOREGROUND;
		this.name = getDefaultName();
	}

	protected AbstractAnnotation(AbstractAnnotation c, boolean usedForPreviews) {
		this(c.re, usedForPreviews);
		this.arrowList = new HashSet<>(c.arrowList);
		this.canvas = c.canvas;
	}

	protected AbstractAnnotation(DRenderingEngine re, double x, double y) {
		this(re, false);
		setLocation(x, y);
	}

	protected AbstractAnnotation(DRenderingEngine re, Map<String, String> argMap) {
		this(re, false);

		if (argMap.containsKey(Annotation.X))
			x = Double.parseDouble(argMap.get(Annotation.X));
		if (argMap.containsKey(Annotation.Y))
			y = Double.parseDouble(argMap.get(Annotation.Y));
		
		this.zOrder = ViewUtils.getDouble(argMap, Z, 0.0).intValue();
		
		if (argMap.get(NAME) != null)
			name = argMap.get(NAME);
		
		String canvasString = ViewUtils.getString(argMap, CANVAS, FOREGROUND);
		
		if (canvasString != null && canvasString.equals(BACKGROUND)) {
			this.canvas = CanvasID.BACKGROUND;
		}

		if (argMap.containsKey(ANNOTATION_ID))
			this.uuid = UUID.fromString(argMap.get(ANNOTATION_ID));
	}

	
	//------------------------------------------------------------------------

	protected String getDefaultName() {
		if(cyAnnotator == null)
			return "Annotation";
		return cyAnnotator.getDefaultAnnotationName(getType().getSimpleName().replace("Annotation", ""));
	}
	
	
	@Override
	public double getX() {
		return x;
	}
	
	@Override
	public double getY() {
		return y;
	}
	
	@Override
	public void setLocation(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	public void setX(double x) {
		this.x = x;
	}
	
	public void setY(double y) {
		this.y = y;
	}
	
	@Override
	public double getWidth() {
		return width;
	}
	
	@Override
	public double getHeight() {
		return height;
	}
	
	public void setBounds(Rectangle2D bounds) {
		this.x = bounds.getX();
		this.y = bounds.getY();
		this.width = bounds.getWidth();
		this.height = bounds.getHeight();
	}
	
	public Rectangle2D getBounds() {
		return new Rectangle2D.Double(x, y, width, height);
	}
	
	@Override
	public void setSize(double width, double height) {
		this.width = width;
		this.height = height;
	}
	
	// Save the bounds (in node coordinates)
	@Override
	public void saveBounds() {
		initialBounds = getBounds().getBounds2D();
	}

	@Override
	public Rectangle2D getInitialBounds() {
		return initialBounds;
	}
		
	public void resizeAnnotationRelative(Rectangle2D initialBounds, Rectangle2D outlineBounds) {
		Rectangle2D daBounds = getInitialBounds();
		
		double deltaW = outlineBounds.getWidth()/initialBounds.getWidth();
		double deltaH = outlineBounds.getHeight()/initialBounds.getHeight();
		
		double deltaX = (daBounds.getX()-initialBounds.getX())/initialBounds.getWidth();
		double deltaY = (daBounds.getY()-initialBounds.getY())/initialBounds.getHeight();
		Rectangle2D newBounds = adjustBounds(daBounds, outlineBounds, deltaX, deltaY, deltaW, deltaH);

		setBounds(newBounds);
	}
	
	private static Rectangle2D adjustBounds(Rectangle2D bounds, Rectangle2D outerBounds, double dx, double dy, double dw, double dh) {
		double newX = outerBounds.getX() + dx * outerBounds.getWidth();
		double newY = outerBounds.getY() + dy * outerBounds.getHeight();
		double newWidth  = bounds.getWidth() * dw;
		double newHeight = bounds.getHeight()* dh;
		return new Rectangle2D.Double(newX,  newY, newWidth, newHeight);
	}
	
	@Override
	public double getZoom() {
		return 1; // Legacy
	}
	

	@Override
	public CanvasID getCanvas() {
		return canvas;
	}

	@Override
	public void setCanvas(String name) {
		CanvasID canvasID = CanvasID.fromArgName(name); 
		changeCanvas(canvasID);
		update();		// Update network attributes
	}

	@Override
	public void changeCanvas(CanvasID canvasID) {
		if(this.canvas == canvasID)
			return;

		this.canvas = canvasID;
		
		for(ArrowAnnotation arrow: arrowList) {
			if(arrow instanceof DingAnnotation) {
				((DingAnnotation)arrow).changeCanvas(canvasID);
			}
		}
	}

	@Override
	public CyNetworkView getNetworkView() {
		return re.getViewModel();
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public int getZOrder() {
		return zOrder;
	}
    
	@Override
	public void setZOrder(int z) {
		this.zOrder = z;
	}
	
	@Override
	public CyAnnotator getCyAnnotator() {
		return cyAnnotator;
	}

	@Override
	public void setGroupParent(GroupAnnotation parent) {
		if (parent instanceof GroupAnnotationImpl) {
			this.groupParent = (GroupAnnotationImpl) parent;
		} else if (parent == null) {
			this.groupParent = null;
		}
//		cyAnnotator.addAnnotation(this);
	}

	@Override
	public GroupAnnotation getGroupParent() {
		return groupParent;
	}
    
	// Assumes location is node coordinates.
	@Override
	public void moveAnnotation(Point2D location) {
		if (!(this instanceof ArrowAnnotationImpl)) {
			setLocation(location.getX(), location.getY());
		}
	}

	@Override
	public void removeAnnotation() {
		cyAnnotator.removeAnnotation(this);
		for(ArrowAnnotation arrow: arrowList) {
			if(arrow instanceof DingAnnotation) {
				((DingAnnotation)arrow).removeAnnotation();
			}
		}
		if(groupParent != null) {
			groupParent.removeMember(this);
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		if (!Objects.equals(name, this.name)) {
			this.name = name;
			update();
		}
	}

	@Override
	public boolean isSelected() {
		return cyAnnotator.getAnnotationSelection().contains(this);
	}

	@Override
	public void setSelected(boolean selected) {
		if(selected != isSelected()) {
			cyAnnotator.setSelectedAnnotation(this, selected);
			pcs.firePropertyChange("selected", !selected, selected);
		}
	}

	@Override
	public void addArrow(ArrowAnnotation arrow) {
		arrowList.add(arrow);
		update();
	}

	@Override
	public void removeArrow(ArrowAnnotation arrow) {
		arrowList.remove(arrow);
		update();
	}

	@Override
	public Set<ArrowAnnotation> getArrows() {
		return arrowList;
	}

	@Override
	public Map<String,String> getArgMap() {
		Map<String, String> argMap = new HashMap<>();
		if (name != null)
			argMap.put(NAME, name);
		
		argMap.put(X, Double.toString(getX()));
		argMap.put(Y, Double.toString(getY()));
		argMap.put(ZOOM, Double.toString(re.getZoom())); // Legacy
		argMap.put(CANVAS, canvas.toArgName());
		argMap.put(ANNOTATION_ID, uuid.toString());

		if (groupParent != null)
			argMap.put(PARENT_ID, groupParent.getUUID().toString());

		argMap.put(Z, Integer.toString(getZOrder()));
		return argMap;
	}
	
	@Override
	public boolean isUsedForPreviews() {
		return usedForPreviews;
	}

	@Override
	public void update() {
		contentChanged();
	}

	// Component overrides
	@Override
	public void paint(Graphics g, boolean showSelected) {
		Graphics2D g2 = (Graphics2D)g;

		/* Set up all of our anti-aliasing, etc. here to avoid doing it redundantly */
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

		// High quality color rendering is ON.
		g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

		// Text antialiasing is ON.
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);


		if (!isUsedForPreviews()) {
			// We need to control composite ourselves for previews...
			g2.setComposite(AlphaComposite.Src);
		}
	}

	@Override
	public JDialog getModifyDialog() {
		return null;
	}


	@Override
	public void contentChanged() {
		if (re != null)
			re.setContentChanged();
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

	public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(propertyName, listener);
	}

	public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(propertyName, listener);
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getName() + "]";
	}
}
