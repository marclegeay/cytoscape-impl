package org.cytoscape.ding.impl.cyannotator;

import static org.cytoscape.ding.internal.util.ViewUtil.invokeOnEDT;

import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.event.SwingPropertyChangeSupport;

import org.cytoscape.application.CyUserLog;
import org.cytoscape.ding.impl.AnnotationCanvas;
import org.cytoscape.ding.impl.DRenderingEngine;
import org.cytoscape.ding.impl.cyannotator.annotations.AbstractAnnotation;
import org.cytoscape.ding.impl.cyannotator.annotations.AnnotationSelection;
import org.cytoscape.ding.impl.cyannotator.annotations.ArrowAnnotationImpl;
import org.cytoscape.ding.impl.cyannotator.annotations.DingAnnotation;
import org.cytoscape.ding.impl.cyannotator.tasks.ReloadImagesTask;
import org.cytoscape.ding.impl.undo.AnnotationEdit;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.session.events.SessionAboutToBeSavedEvent;
import org.cytoscape.session.events.SessionAboutToBeSavedListener;
import org.cytoscape.view.presentation.annotations.Annotation;
import org.cytoscape.view.presentation.annotations.GroupAnnotation;
import org.cytoscape.work.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class CyAnnotator implements SessionAboutToBeSavedListener {
	
	private static final String ANNOTATION_ATTRIBUTE = "__Annotations";
	
	private static final String DEF_ANNOTATION_NAME_PREFIX = "Annotation";
	private static final int MAX_NAME_LENGH = 200;

	private final DRenderingEngine re;
	private final AnnotationCanvas foreGroundCanvas;
	private final AnnotationCanvas backGroundCanvas;
	private final AnnotationFactoryManager annotationFactoryManager; 
	private final CyServiceRegistrar registrar; 
	private final AnnotationSelection annotationSelection;
//	private MyViewportChangeListener myViewportChangeListener;
	private AbstractAnnotation resizing;
	private Rectangle2D resizeBounds;
	private ArrowAnnotationImpl repositioning;
	
	private Set<DingAnnotation> annotationSet = new HashSet<>();
	
	private AnnotationEdit undoEdit;
	
	private final SwingPropertyChangeSupport propChangeSupport = new SwingPropertyChangeSupport(this);
	private boolean loading;
	
	private static final Logger logger = LoggerFactory.getLogger(CyUserLog.NAME);

	public CyAnnotator(DRenderingEngine re, AnnotationFactoryManager annotationFactoryManager, CyServiceRegistrar registrar) {
		this.re = re;
		this.registrar = registrar;
		this.foreGroundCanvas = re.getAnnotationCanvas(DingAnnotation.CanvasID.FOREGROUND);
		this.backGroundCanvas = re.getAnnotationCanvas(DingAnnotation.CanvasID.BACKGROUND);
		this.annotationFactoryManager = annotationFactoryManager;
		this.annotationSelection = new AnnotationSelection(this);
		
//		initListeners();
	}
	
	public void markUndoEdit(String label) {
		undoEdit = new AnnotationEdit(label, this, registrar);
	}
	
	public void postUndoEdit() {
		if(undoEdit != null) {
			undoEdit.post();
		}
	}
	
	public void clearUndoEdit() {
		undoEdit = null;
	}
	
	
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		propChangeSupport.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		propChangeSupport.removePropertyChangeListener(listener);
	}

	public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		propChangeSupport.addPropertyChangeListener(propertyName, listener);
	}

	public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		propChangeSupport.removePropertyChangeListener(propertyName, listener);
	}
	
//	private void initListeners() {
//		//We also setup this class as a ViewportChangeListener to the current networkview
//		myViewportChangeListener=new MyViewportChangeListener();
//		re.addViewportChangeListener(myViewportChangeListener);
//	}
	
	public void dispose() {
//		re.removeViewportChangeListener(myViewportChangeListener);
//		foreGroundCanvas.dispose();
//		backGroundCanvas.dispose();
	}

	
	public void loadAnnotations() {
		CyNetwork network = re.getViewModel().getModel();
		// Now, see if this network has any existing annotations
		final CyTable networkAttributes = network.getTable(CyNetwork.class, CyNetwork.LOCAL_ATTRS);

		if (networkAttributes.getColumn(ANNOTATION_ATTRIBUTE) == null)
			networkAttributes.createListColumn(ANNOTATION_ATTRIBUTE, String.class, false, Collections.emptyList());

		List<String> annotations = network.getRow(network, CyNetwork.LOCAL_ATTRS).getList(ANNOTATION_ATTRIBUTE,String.class);
		loadAnnotations(annotations);
	}
	
	public void loadAnnotations(List<String> annotations) {
		invokeOnEDT(() -> {
			loading = true;
			
			try {
				List<Map<String, String>> arrowList = new ArrayList<>(); // Keep a list of arrows
				Map<GroupAnnotation, String> groupMap = new HashMap<>(); // Keep a map of groups and uuids
				Map<String, Annotation> uuidMap = new HashMap<>();
				Map<Object, Map<Integer, DingAnnotation>> zOrderMap = new HashMap<>();
		
				if (annotations != null) {
					loadRegularAnnotations(annotations, arrowList, groupMap, uuidMap, zOrderMap);
					loadGroups(groupMap, uuidMap);
					loadArrows(arrowList, zOrderMap);
					// Now, handle our Z-Order.  This needs to be done after everything else is
					// added to make sure that we have the proper number of components
					// We use a TreeMap so that the keys (the zOrder are ordered)
					restoreZOrder(zOrderMap);
				}
			} catch (Exception e) {
				logger.error("Annotations were not loaded correctly.", e);
			} finally {
				loading = false;
				propChangeSupport.firePropertyChange("annotations", Collections.emptySet(), new HashSet<>(annotationSet));
			}
		});
	}

	public DingAnnotation getAnnotation(UUID annotationID) {
		for (DingAnnotation a: annotationSet) {
			if (a.getUUID().equals(annotationID))
				return a;
		}
		
		return null;
	}

//	public void update() {
//		re.updateView();
//	}

	public DRenderingEngine getRenderingEngine() {
		return re;
	}

	public CyServiceRegistrar getRegistrar() {
		return registrar;
	}
	
	/**
 	 * Find all of our annotations that are at this point.  Return the top annotation
 	 * (the one with the lowest Z value) if there are more than one.
 	 */
	private DingAnnotation getComponentAt(AnnotationCanvas cnvs, int x, int y) {
		DingAnnotation top = null;
		
		for (DingAnnotation a : annotationSet) {
			if (a.getCanvas().equals(cnvs) && a.contains(x, y)) {
				if ((top == null) || (cnvs.getZOrder(top) > cnvs.getZOrder(a))) {
					top = a;
				}
			}
		}
		return top;
	}

	/**
 	 * Find all of our annotations that are at this point. 
 	 */
	private List<DingAnnotation> getComponentsAt(AnnotationCanvas cnvs, int x, int y) {
		List<DingAnnotation> list = new ArrayList<>();
		
		// MKTODO it should not be necessary to z-sort, the canvas should return the annotations in z-order
		for (DingAnnotation a : annotationSet) {
			if (a.getCanvas().equals(cnvs) && a.contains(x, y)) {
				// Make sure to find the parent if this is a group
				while (a.getGroupParent() != null) {
					a = (DingAnnotation) a.getGroupParent();
				}

				if (!list.contains(a))
					list.add(a);
			}
		}

		// Now sort the list by Z order, smallest to largest
		Collections.sort(list, new ZComparator(cnvs));
		return list;
	}

	// MKTODO should we do this???
	public DingAnnotation getAnnotationAt(Point2D position) {
		DingAnnotation a = getAnnotationAt(foreGroundCanvas, position);
		if(a == null)
			a = getAnnotationAt(backGroundCanvas, position);
		return a;
	}
	
	public DingAnnotation getAnnotationAt(AnnotationCanvas canvas, Point2D position) {
		DingAnnotation a = getComponentAt(canvas, (int)position.getX(), (int)position.getY());
		if(a != null) {
			while(a.getGroupParent() != null) {
				a = (DingAnnotation)a.getGroupParent();
			}
		}
		return a;
	}

	public List<DingAnnotation> getAnnotationsAt(Point2D position) {
		List<DingAnnotation> a = getComponentsAt(foreGroundCanvas, (int)position.getX(), (int)position.getY());
		a.addAll(getComponentsAt(backGroundCanvas, (int)position.getX(), (int)position.getY()));
		return a;
	}

	public List<DingAnnotation> getAnnotationsIn(Rectangle2D rect) {
		List<DingAnnotation> anns = new ArrayList<>();
		for (Annotation ann: getAnnotations()) {
			DingAnnotation d = (DingAnnotation)ann;
			Rectangle2D bounds = d.getBounds();
			if (d.getGroupParent() == null && rect.contains(bounds))
				anns.add(d);
		}
		return anns;
	}
	
	public List<DingAnnotation> getAnnotationsInPath(GeneralPath path) {
		List<DingAnnotation> anns = new ArrayList<>();
		for (Annotation ann: getAnnotations()) {
			DingAnnotation d = (DingAnnotation)ann;
			Rectangle2D bounds = d.getBounds();
			if(d.getGroupParent() == null && path.intersects(bounds))
				anns.add(d);
		}
		return anns;
	}
	


	public AnnotationCanvas getForeGroundCanvas() {
		return foreGroundCanvas;
	}

	public AnnotationCanvas getBackGroundCanvas() {
		return backGroundCanvas;
	}
	
	
	public AnnotationTree getAnnotationTree() {
		return AnnotationTree.buildTree(annotationSet, this);
	}
	
	
	
	public void checkCycle() throws IllegalAnnotationStructureException {
		if(AnnotationTree.containsCycle(annotationSet)) {
			throw new IllegalAnnotationStructureException("Adding annotation would create a cycle. Group annotations must be a tree.");
		}
	}
	
	public void checkCycle(Annotation annotation) throws IllegalAnnotationStructureException {
		if(annotation instanceof GroupAnnotation) {
			if(AnnotationTree.containsCycle(annotationSet, (DingAnnotation)annotation)) {
				throw new IllegalAnnotationStructureException("Adding annotation would create a cycle. Group annotations must be a tree.");
			}
		}
	}
	
	public void checkCycle(Collection<DingAnnotation> annotations) throws IllegalAnnotationStructureException {
		if(annotations.isEmpty())
			return;
		if(AnnotationTree.containsCycle(annotationSet, annotations)) {
			throw new IllegalAnnotationStructureException("Adding annotation would create a cycle. Group annotations must be a tree.");
		}
	}
	
	
	public void addAnnotation(Annotation annotation) {
		if (annotationSet.contains(annotation))
			return;
		
		if (!(annotation instanceof DingAnnotation))
			return;
		
		Set<DingAnnotation> oldValue = new HashSet<>(annotationSet);
		
		annotationSet.add((DingAnnotation) annotation);
		
		getAnnotationTree().resetZOrder();
		
		if (!loading) {
			propChangeSupport.firePropertyChange("annotations", oldValue, new HashSet<>(annotationSet));
		}
	}
	
	
	public void addAnnotations(Collection<? extends Annotation> annotations) {
		if (annotationSet.containsAll(annotations))
			return;
		
		Set<DingAnnotation> oldValue = new HashSet<>(annotationSet);
		
		for(Annotation a : annotations) {
			if(a instanceof DingAnnotation) {
				annotationSet.add((DingAnnotation)a);
			}
		}
		
		getAnnotationTree().resetZOrder();
		
		if (!loading) {
			propChangeSupport.firePropertyChange("annotations", oldValue, new HashSet<>(annotationSet));
		}
	}

	
	public void removeAnnotation(Annotation annotation) {
		Set<DingAnnotation> oldValue = new HashSet<>(annotationSet);
		
		boolean changed = annotationSet.remove((DingAnnotation) annotation);
		annotationSelection.remove(annotation);
		
		if (changed && !loading) {
			propChangeSupport.firePropertyChange("annotations", oldValue, new HashSet<>(annotationSet));
		}
	}

	public void removeAnnotations(Collection<? extends Annotation> annotations) {
		boolean changed = false;
		Set<DingAnnotation> oldValue = new HashSet<>(annotationSet);
		
		for (Annotation annotation : annotations) {
			if (annotationSet.remove((DingAnnotation) annotation))
				changed = true;
			
			annotationSelection.remove(annotation);
		}
		
		if (changed && !loading) {
			propChangeSupport.firePropertyChange("annotations", oldValue, new HashSet<>(annotationSet));
		}
	}
	

	public List<Annotation> getAnnotations() {
		return annotationSet.isEmpty() ? Collections.emptyList() : new ArrayList<>(annotationSet);
	}
	
	public boolean contains(Annotation a) {
		if(a == null)
			return false;
		return annotationSet.contains(a);
	}

	public void setSelectedAnnotation(DingAnnotation a, boolean selected) {
		if (selected) {
//			requestFocusInWindow(a);
			annotationSelection.add(a);
		} else {
			annotationSelection.remove(a);
		}
	}

	public void clearSelectedAnnotations() {
		if(annotationSelection.isEmpty())
			return;
		
		for(DingAnnotation a : annotationSelection) {
			a.setSelected(false);
		}
		annotationSelection.clear();
//		re.updateView();
	}

	public AnnotationSelection getAnnotationSelection() {
		return annotationSelection;
	}

	public void resizeShape(AbstractAnnotation shape) {
		if(shape == null) {
			resizing = null;
			resizeBounds = null;
		} else {
			resizing = shape;
			resizeBounds = shape.getBounds();
//			requestFocusInWindow(resizing);
		}
	}

	public AbstractAnnotation getResizeShape() {
		return resizing;
	}
	
	public Rectangle2D getResizeBounds() {
		return resizeBounds;
	}

	public void positionArrow(ArrowAnnotationImpl arrow) {
		repositioning = arrow;
//		if (repositioning != null)
//			requestFocusInWindow(repositioning);
	}

	public ArrowAnnotationImpl getRepositioningArrow() {
		return repositioning;
	}

	public Task getReloadImagesTask() {
		return new ReloadImagesTask(this);
	}


	public String getDefaultAnnotationName(String desiredName) {
		if (desiredName == null || "".equals(desiredName.trim()))
			desiredName = DEF_ANNOTATION_NAME_PREFIX;
		
		Pattern p = Pattern.compile(".*\\s(\\d*)$"); // capture just the digits
		Matcher m = p.matcher(desiredName);
		int start = 1;

		if (m.matches()) {
			desiredName = desiredName.substring(0, m.start(1) - 1);
			String gr = m.group(1); // happens to be "" (empty str.) because of \\d*
			start = (gr.isEmpty()) ? 1 : Integer.decode(gr) + 1;
		}

		if (desiredName.length() > MAX_NAME_LENGH)
			desiredName = desiredName.substring(0, MAX_NAME_LENGH);

		for (int i = start; true; i++) {
			final String candidate = desiredName + " " + i;

			if (!isAnnotationNameTaken(candidate))
				return candidate;
		}
	}
	
	private boolean isAnnotationNameTaken(String candidate) {
		for (Annotation a : getAnnotations()) {
			final String name = a.getName();

			if (name != null && name.equals(candidate))
				return true;
		}

		return false;
	}
	
	@Override
	public void handleEvent(SessionAboutToBeSavedEvent e) {
		CyNetwork network = re.getViewModel().getModel();
		List<String> networkAnnotation = createSavableNetworkAttribute();
		
		if (network.getDefaultNetworkTable().getColumn(ANNOTATION_ATTRIBUTE) == null) {
			network.getDefaultNetworkTable().createListColumn(ANNOTATION_ATTRIBUTE, String.class, false, Collections.emptyList());
		}

		network.getRow(network, CyNetwork.LOCAL_ATTRS).set(ANNOTATION_ATTRIBUTE, networkAnnotation);
	}

	public List<String> createSavableNetworkAttribute() {
		List<Map<String,String>> networkAnnotations = new ArrayList<>();
		
		for (DingAnnotation annotation : annotationSet)
			networkAnnotations.add(annotation.getArgMap());
		
		// Save it in the network attributes
		return convertAnnotationMap(networkAnnotations);
	}
	
	private List<String> convertAnnotationMap(List<Map<String, String>>networkAnnotations) {
		List<String> result = new ArrayList<>();

		if (networkAnnotations == null || networkAnnotations.size() == 0)
			return result;

		for (Map<String,String> map: networkAnnotations) {
			StringBuilder props = new StringBuilder();
			Iterator<Map.Entry<String,String>> iter = map.entrySet().iterator();
			if(iter.hasNext()) {
				Map.Entry<String,String> entry = iter.next();
				props.append(entry.getKey()).append('=').append(entry.getValue());
			}
			while(iter.hasNext()) {
				Map.Entry<String,String> entry = iter.next();
				props.append('|').append(entry.getKey()).append('=').append(entry.getValue());
			}
			result.add(props.toString());
		}
		return result;
	}
	
	public void fireAnnotationsReordered() {
		propChangeSupport.firePropertyChange("annotationsReordered", null, null);
	}

	private void loadRegularAnnotations(
			List<String> annotations,
			List<Map<String, String>> arrowList,
			Map<GroupAnnotation, String> groupMap,
			Map<String, Annotation> uuidMap,
			Map<Object, Map<Integer, DingAnnotation>> zOrderMap
	) {
		for (String s: annotations) {
			Map<String, String> argMap = createArgMap(s);
			DingAnnotation annotation = null;
			String type = argMap.get("type");
			
			if (type == null)
				continue;

			if (type.equals("ARROW") || type.equals("org.cytoscape.view.presentation.annotations.ArrowAnnotation")) {
				arrowList.add(argMap);
				continue;
			}

			Annotation a = annotationFactoryManager.createAnnotation(type, re.getViewModel() ,argMap);
			
			if (a == null || !(a instanceof DingAnnotation))
				continue;

			annotation = (DingAnnotation)a;

			uuidMap.put(annotation.getUUID().toString(), annotation);
			Object canvas;

			if (annotation.getCanvas() != null) {
				annotation.getCanvas().add(annotation);
				canvas = annotation.getCanvas();
			} else {
				canvas = foreGroundCanvas;
				foreGroundCanvas.add(annotation);
			}

			if (argMap.containsKey(Annotation.Z)) {
				int zOrder = Integer.parseInt(argMap.get(Annotation.Z));
				
				if (zOrder >= 0) {
					if (!zOrderMap.containsKey(canvas))
						zOrderMap.put(canvas, new TreeMap<>());
					zOrderMap.get(canvas).put(zOrder,annotation);
				}
			}

			addAnnotation(annotation);

			// If this is a group, save the annotation and the memberUIDs list
			if (type.equals("GROUP") || type.equals("org.cytoscape.view.presentation.annotations.GroupAnnotation")) {
				// Don't bother adding the group if it doesn't have any children
				if (argMap.containsKey("memberUUIDs"))
					groupMap.put((GroupAnnotation)a, argMap.get("memberUUIDs"));
			}
		}
	}
	
	private void loadGroups(Map<GroupAnnotation, String> groupMap, Map<String, Annotation> uuidMap) {
		for (GroupAnnotation group: groupMap.keySet()) {
			String uuids = groupMap.get(group);
			String[] uuidArray = uuids.split(",");
			
			for (String uuid: uuidArray) {
				if (uuidMap.containsKey(uuid)) {
					Annotation child = uuidMap.get(uuid);
					group.addMember(child);
				}
			}
		}
	}
	
	private void loadArrows(List<Map<String, String>> arrowList, Map<Object, Map<Integer, DingAnnotation>> zOrderMap) {
		for (Map<String, String> argMap : arrowList) {
			String type = argMap.get("type");
			Annotation annotation = annotationFactoryManager.createAnnotation(type, re.getViewModel(), argMap);
			
			if (annotation instanceof ArrowAnnotationImpl) {
				ArrowAnnotationImpl arrow = (ArrowAnnotationImpl)annotation;
				arrow.getSource().addArrow(arrow);
				Object canvas;
				
				if (arrow.getCanvas() != null) {
					arrow.getCanvas().add(arrow);
					canvas = arrow.getCanvas();
				} else {
					foreGroundCanvas.add(arrow);
					canvas = foreGroundCanvas;
				}

				if (argMap.containsKey(Annotation.Z)) {
					int zOrder = Integer.parseInt(argMap.get(Annotation.Z	));
					
					if (zOrder >= 0) {
						if (!zOrderMap.containsKey(canvas))
							zOrderMap.put(canvas, new TreeMap<>());
						
						zOrderMap.get(canvas).put(zOrder,arrow);
					}
				}

				addAnnotation(arrow);
			}
		}
	}
	
	private void restoreZOrder(Map<Object, Map<Integer, DingAnnotation>> zOrderMap) {
		for (Map<Integer, DingAnnotation> map: zOrderMap.values()) {
			for (Integer zOrder: map.keySet()) {
				DingAnnotation a = map.get(zOrder);
				
				if (a.getCanvas() != null)
					a.getCanvas().setZOrder(a, zOrder);
				else
					foreGroundCanvas.setZOrder(a, zOrder);
			}
		}
	}
	
	private Map<String, String> createArgMap(String mapstring) {
		Map<String, String> result = new HashMap<>();
		String[] argList = mapstring.split("[|]");

		if (argList.length == 0)
			return result;

		for (int argIndex = 0; argIndex < argList.length; argIndex++) {
			String arg = argList[argIndex];
			String[] keyValue = arg.split("=");

			if (keyValue.length != 2)
				continue;
			
			result.put(keyValue[0], keyValue[1]);
		}

		return result;
	}

//	private void requestFocusInWindow(final Annotation annotation) {
//		invokeOnEDT(() -> {
//			if (annotation != null && annotation instanceof DingAnnotation)
//				((DingAnnotation) annotation).getCanvas().requestFocusInWindow();
//		});
//	}

/*
	private String printCurrentAnnotations() {
		String result = "Foreground: \n";
		Component[] annotations=foreGroundCanvas.getComponents();
		for(int i=0;i<annotations.length;i++){
			if(annotations[i] instanceof Annotation) {
				result += "  "+((Annotation)annotations[i]).toString()+"\n";
			}
		}
		annotations=backGroundCanvas.getComponents();
		result += "Background: \n";
		for(int i=0;i<annotations.length;i++){
			if(annotations[i] instanceof Annotation) {
				result += "  "+((Annotation)annotations[i]).toString()+"\n";
			}
		}
		return result;
	}
*/


	class ZComparator implements Comparator<DingAnnotation> {
		
		final AnnotationCanvas cnvs;
		
		public ZComparator(AnnotationCanvas c) {
			this.cnvs = c;
		}
		
		@Override
		public int compare(DingAnnotation o1, DingAnnotation o2) {
			int z1 = cnvs.getZOrder(o1);
			int z2 = cnvs.getZOrder(o2);
			if (z1 < z2) return -1;
			if (z1 > z2) return 1;
			return 0;
		}

		public boolean equals(DingAnnotation o1, DingAnnotation o2) {
			int z1 = cnvs.getZOrder(o1);
			int z2 = cnvs.getZOrder(o2);
			if (z1 == z2) return true;
			return false;
		}
	}
}
