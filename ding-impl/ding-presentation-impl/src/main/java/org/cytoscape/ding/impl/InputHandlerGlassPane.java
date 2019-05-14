package org.cytoscape.ding.impl;

import static java.awt.event.KeyEvent.VK_BACK_SPACE;
import static java.awt.event.KeyEvent.VK_DELETE;
import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_ESCAPE;
import static java.awt.event.KeyEvent.VK_LEFT;
import static java.awt.event.KeyEvent.VK_RIGHT;
import static java.awt.event.KeyEvent.VK_UP;
import static org.cytoscape.ding.impl.DRenderingEngine.Canvas.BACKGROUND_CANVAS;
import static org.cytoscape.ding.impl.DRenderingEngine.Canvas.FOREGROUND_CANVAS;
import static org.cytoscape.ding.internal.util.ViewUtil.invokeOnEDT;
import static org.cytoscape.ding.internal.util.ViewUtil.isControlOrMetaDown;
import static org.cytoscape.ding.internal.util.ViewUtil.isDragSelectionKeyDown;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;

import org.cytoscape.ding.DVisualLexicon;
import org.cytoscape.ding.ViewChangeEdit;
import org.cytoscape.ding.impl.BendStore.HandleKey;
import org.cytoscape.ding.impl.cyannotator.CyAnnotator;
import org.cytoscape.ding.impl.cyannotator.annotations.AbstractAnnotation;
import org.cytoscape.ding.impl.cyannotator.annotations.AnchorLocation;
import org.cytoscape.ding.impl.cyannotator.annotations.AnnotationSelection;
import org.cytoscape.ding.impl.cyannotator.annotations.ArrowAnnotationImpl;
import org.cytoscape.ding.impl.cyannotator.annotations.DingAnnotation;
import org.cytoscape.ding.impl.cyannotator.annotations.ShapeAnnotationImpl;
import org.cytoscape.ding.impl.cyannotator.tasks.AnnotationEdit;
import org.cytoscape.ding.impl.cyannotator.tasks.EditAnnotationTaskFactory;
import org.cytoscape.ding.internal.util.OrderedMouseAdapter;
import org.cytoscape.graph.render.stateful.GraphRenderer;
import org.cytoscape.graph.render.stateful.NodeDetails;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNode;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.NetworkTaskFactory;
import org.cytoscape.task.destroy.DeleteSelectedNodesAndEdgesTaskFactory;
import org.cytoscape.util.swing.IconManager;
import org.cytoscape.util.swing.LookAndFeelUtil;
import org.cytoscape.view.model.CyNetworkViewConfig;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.values.Bend;
import org.cytoscape.view.presentation.property.values.Handle;
import org.cytoscape.view.presentation.property.values.Position;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.swing.DialogTaskManager;

@SuppressWarnings("serial")
public class InputHandlerGlassPane extends JComponent {
	
	private static final Color SELECTION_RECT_BORDER_COLOR_1 = UIManager.getColor("Focus.color");
	private static final Color SELECTION_RECT_BORDER_COLOR_2 = new Color(255, 255, 255, 160);
	
	
	private final CyServiceRegistrar registrar;
	private final DRenderingEngine re;
	private final CyAnnotator cyAnnotator;
	private final PopupMenuHelper popupMenuHelper;

	private final DingCanvas  backgroundCanvas;
	private final InnerCanvas networkCanvas;
	private final DingCanvas  foregroundCanvas;
	
	private GeneralPath selectionLasso;
	private Rectangle selectionRect;
	
//	private Point2D addingEdgeStartPoint;
//	private ViewChangeEdit undoableEdit;

	
	public InputHandlerGlassPane(CyServiceRegistrar registrar, DRenderingEngine re) {
		this.registrar = registrar;
		this.re = re;
		this.cyAnnotator = re.getCyAnnotator();
		
		this.backgroundCanvas = re.getCanvas(BACKGROUND_CANVAS);
		this.networkCanvas    = re.getCanvas();
		this.foregroundCanvas = re.getCanvas(FOREGROUND_CANVAS);

		this.popupMenuHelper = new PopupMenuHelper(re, this, registrar);
		
		setFocusable(true);
		addKeyListener(new CanvasKeyListener());
        addMouseWheelListener(new CanvasMouseWheelListener());
        
        OrderedMouseAdapter orderedMouseAdapter = new OrderedMouseAdapter(
        	new ContextMenuListener(),
        	new SelectionLassoListener(),
        	new SelectionRectangleListener(),
        	new CanvasPanListener() // panning only happens if no node/edge/annotation/handle is selected, so it needs to go after
//        	new CanvasMouseListener()
        );
        
		addMouseListener(orderedMouseAdapter);
		addMouseMotionListener(orderedMouseAdapter);
	}
	
	
	@Override
	protected void paintComponent(Graphics g) {
		drawSelectionRectangle(g);
		drawSelectionLasso(g);
		// drawAddingEdge(g);
	}
	
	private void drawSelectionRectangle(Graphics graphics) {
		// Draw selection rectangle
		if(selectionRect != null) {
			Graphics2D g = (Graphics2D) graphics.create();
			// External border
			g.setColor(SELECTION_RECT_BORDER_COLOR_1);
			g.draw(selectionRect);
			// Internal border
			if (selectionRect.width > 4 && selectionRect.height > 4) {
				g.setColor(SELECTION_RECT_BORDER_COLOR_2);
				g.drawRect(
						selectionRect.x + 1,
						selectionRect.y + 1,
						selectionRect.width - 2,
						selectionRect.height - 2
				);
			}
		}
	}
	
	private void drawSelectionLasso(Graphics graphics) {
		if(selectionLasso != null) {
			Graphics2D g = (Graphics2D) graphics.create();
			g.setColor(SELECTION_RECT_BORDER_COLOR_1);
			GeneralPath path = new GeneralPath(selectionLasso);
			path.closePath();
			g.draw(path);
		}
	}
	
	private static void drawAddingEdge(Graphics2D g, Point2D startPoint, Point2D endPoint) {
		if(startPoint == null || endPoint == null)
			return;

        double x1 = startPoint.getX();
        double y1 = startPoint.getY();
        double x2 = endPoint.getX();
        double y2 = endPoint.getY();
        double offset = 5;
        
        double lineLen = Math.sqrt((x2 - x1) * (x2 - x1)) + ((y2 - y1) * (y2 - y1));
        if(lineLen == 0)
            lineLen = 1;

        y2 += ((y1 - y2) / lineLen) * offset;
        x2 += ((x1 - x2) / lineLen) * offset;

        Color saveColor = g.getColor();
        g.setColor(Color.BLACK);
        g.drawLine(((int) x1) - 1, ((int) y1) - 1, ((int) x2) + 1, ((int) y2) + 1);
        g.setColor(saveColor);
	}
	
	
	
	private class CanvasKeyListener extends KeyAdapter {

		@Override
		public void keyPressed(KeyEvent e) {
			System.out.println("InputHandlerGlassPane.CanvasKeyListener.keyPressed()");
			int code = e.getKeyCode();
			
			if(code == VK_UP || code == VK_DOWN || code == VK_LEFT || code == VK_RIGHT) {
				if(annotationSelectionEnabled()) {
					moveAnnotations(e);
				}
				if(isControlOrMetaDown(e)) {
					panCanvas(e);
				} else if(nodeSelectionEnabled()) {
					moveNodesAndHandles(e);
				}
				
			} else if(code == VK_ESCAPE) {
				cancelAddingEdge();
				if(annotationSelectionEnabled()) {
					cancelAnnotations();
				}
				
			} else if(code == VK_BACK_SPACE) {
				deleteSelectedNodesAndEdges();
			}
			
			networkCanvas.repaint();
		}
		
		@Override
		public void keyReleased(KeyEvent e) { 
			int code = e.getKeyCode();
			AnnotationSelection annotationSelection = cyAnnotator.getAnnotationSelection();

			if(annotationSelectionEnabled() && annotationSelection.count() > 0 && code == VK_DELETE) {
				Set<DingAnnotation> selectedAnnotations = annotationSelection.getSelectedAnnotations();
				for (DingAnnotation ann: selectedAnnotations)
					ann.removeAnnotation();
			} 
		}
		
		private void moveAnnotations(KeyEvent e) {
			//Some annotations have been double clicked and selected
			int code = e.getKeyCode();
			final int move = 2;
			
			AnnotationSelection annotationSelection = cyAnnotator.getAnnotationSelection();
			
			for (DingAnnotation annotation : annotationSelection) {
				Component c = annotation.getComponent();
				int x = c.getX(), y = c.getY();
				if (annotation instanceof ShapeAnnotationImpl && e.isShiftDown()) {
					ShapeAnnotationImpl sa = (ShapeAnnotationImpl)annotation;
					int width = c.getWidth(), height = c.getHeight();
					int borderWidth = (int)sa.getBorderWidth(); // We need to take this into account
					if (code == VK_UP) {
						height -= move*2; width -= borderWidth*2;
					} else if (code == VK_DOWN) {
						height += move; width -= borderWidth*2;
					} else if (code == VK_LEFT) {
						width -= move*2; height -= borderWidth*2;
					} else if (code == VK_RIGHT) {
						width += move; height -= borderWidth*2;
					}
					// Adjust the size of the selected annotations
					sa.setSize((double)width, (double)height);
				} else {
					if (code == VK_UP)
						y-=move;
					else if (code == VK_DOWN)
						y+=move;
					else if (code == VK_LEFT)
						x-=move;
					else if (code == VK_RIGHT)
						x+=move;

					//Adjust the locations of the selected annotations
					annotation.getComponent().setLocation(x,y);
				}
				annotation.update();
				annotation.getCanvas().repaint();	
			}
		}
		
		private void cancelAnnotations() {
			if (cyAnnotator.getResizeShape() != null) {
				cyAnnotator.getResizeShape().contentChanged();
				cyAnnotator.resizeShape(null);
				cyAnnotator.postUndoEdit();
			} else if(cyAnnotator.getRepositioningArrow() != null) {
				cyAnnotator.getRepositioningArrow().contentChanged();
				cyAnnotator.positionArrow(null);
				cyAnnotator.postUndoEdit();
			}
		}
		
		
		private void panCanvas(KeyEvent k) {
			int code = k.getKeyCode();
			final float move = k.isShiftDown() ? 15.0f : 1.0f;

			if(code == VK_UP)
				re.pan(0, move);
			else if (code == VK_DOWN)
				re.pan(0, -move);
			else if (code == VK_LEFT)
				re.pan(-move, 0);
			else if (code == VK_RIGHT)
				re.pan(move, 0);
		}
		

		private void moveNodesAndHandles(KeyEvent k) {
			final int code = k.getKeyCode();
			final float move = k.isShiftDown() ? 15.0f : 1.0f;
			
			Collection<View<CyNode>> selectedNodes = re.getViewModelSnapshot().getTrackedNodes(CyNetworkViewConfig.SELECTED_NODES);
			for (View<CyNode> node : selectedNodes) {
				double xPos = re.getNodeDetails().getXPosition(node);
				double yPos = re.getNodeDetails().getYPosition(node);

				if (code == VK_UP) {
					yPos -= move;
				} else if (code == VK_DOWN) {
					yPos += move;
				} else if (code == VK_LEFT) {
					xPos -= move;
				} else if (code == VK_RIGHT) {
					xPos += move;
				}

				// MKTODO better way of doing this???
				View<CyNode> mutableNodeView = re.getViewModel().getNodeView(node.getSUID());
				mutableNodeView.setVisualProperty(BasicVisualLexicon.NODE_X_LOCATION, xPos);
				mutableNodeView.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, yPos);
			}

			Set<HandleKey> handlesToMove = re.getBendStore().getSelectedHandles();
			for (HandleKey handleKey : handlesToMove) {
				View<CyEdge> ev = re.getViewModelSnapshot().getEdgeView(handleKey.getEdgeSuid());

				// MKTODO this code is copy-pasted in a few places, clean it up
				if(!ev.isValueLocked(BasicVisualLexicon.EDGE_BEND)) {
					Bend defaultBend = re.getViewModelSnapshot().getViewDefault(BasicVisualLexicon.EDGE_BEND);
					View<CyEdge> mutableEdgeView = re.getViewModel().getEdgeView(ev.getSUID());
					if(mutableEdgeView != null) {
						if(ev.getVisualProperty(BasicVisualLexicon.EDGE_BEND) == defaultBend) {
							mutableEdgeView.setLockedValue(BasicVisualLexicon.EDGE_BEND, new BendImpl((BendImpl)defaultBend));
						} else {
							Bend bend = re.getEdgeDetails().getBend(ev, true);
							mutableEdgeView.setLockedValue(BasicVisualLexicon.EDGE_BEND, new BendImpl((BendImpl)bend));
						}
					}
				}
				
				Bend bend = ev.getVisualProperty(BasicVisualLexicon.EDGE_BEND);
				Handle handle = bend.getAllHandles().get(handleKey.getHandleIndex());
				Point2D newPoint = handle.calculateHandleLocation(re.getViewModel(),ev);
				
				float x = (float) newPoint.getX();
				float y = (float) newPoint.getY();

				if (code == VK_UP) {
					re.getBendStore().moveHandle(handleKey, x, y - move);
				} else if (code == VK_DOWN) {
					re.getBendStore().moveHandle(handleKey, x, y + move);
				} else if (code == VK_LEFT) {
					re.getBendStore().moveHandle(handleKey, x - move, y);
				} else if (code == VK_RIGHT) {
					re.getBendStore().moveHandle(handleKey, x + move, y);
				}
			}
		}
		
		private void cancelAddingEdge() {
			AddEdgeStateMonitor.reset(re.getViewModelSnapshot());
		}
		
		private void deleteSelectedNodesAndEdges() {
			final TaskManager<?, ?> taskManager = registrar.getService(TaskManager.class);
			NetworkTaskFactory taskFactory = registrar.getService(DeleteSelectedNodesAndEdgesTaskFactory.class);
			taskManager.execute(taskFactory.createTaskIterator(re.getViewModel().getModel()));
		}
	}
	
	
	private class CanvasMouseWheelListener implements MouseWheelListener {
		
		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			networkCanvas.adjustZoom(e.getWheelRotation());
		}
		
	}

	
	private class ContextMenuListener extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent e) {
			if(isSingleRightClick(e)) {
				// MKTODO what about the annotation specific menu???
				showContextMenu(e.getPoint());
				e.consume();
			}
		}
		
		private void showContextMenu(Point p) {
			// Node menu
			View<CyNode> nodeView = re.getPickedNodeView(p);
			if(nodeView != null) {
				popupMenuHelper.createNodeViewMenu(nodeView, p.x, p.y, PopupMenuHelper.ACTION_NEW);
				return;
			}
			// Edge menu
			View<CyEdge> edgeView = re.getPickedEdgeView(p);
			if(edgeView != null) {
				popupMenuHelper.createEdgeViewMenu(edgeView, p.x, p.y, PopupMenuHelper.ACTION_NEW);
				return;
			}
			// Network canvas menu
			double[] loc = { p.getX(), p.getY() };
			re.xformComponentToNodeCoords(loc);
			Point xformP = new Point();
			xformP.setLocation(loc[0], loc[1]); 
			popupMenuHelper.createNetworkViewMenu(p, xformP, PopupMenuHelper.ACTION_NEW);
		}
	}

	
	private class SelectionLassoListener extends MouseAdapter {
		
		@Override
		public void mousePressed(MouseEvent e) {
			if(e.isShiftDown() && isControlOrMetaDown(e)) { // Temporary
				selectionLasso = new GeneralPath();
				selectionLasso.moveTo(e.getX(), e.getY());
				e.consume();
			}
		}
		
		@Override
		public void mouseDragged(MouseEvent e) {
			if(selectionLasso != null) {
				selectionLasso.lineTo(e.getX(), e.getY());
				repaint();
			}
		}
		
		@Override
		public void mouseReleased(MouseEvent e) {
			if(selectionLasso != null) {
				selectionLasso.closePath();
				
				List<DingAnnotation> annotations = Collections.emptyList();
				List<HandleKey> handles  = Collections.emptyList();
				List<View<CyNode>> nodes = Collections.emptyList();
				List<View<CyEdge>> edges = Collections.emptyList();

				if(annotationSelectionEnabled()) {
					annotations = cyAnnotator.getAnnotationsInPath(selectionLasso);
				}
				if(nodeSelectionEnabled()) {
					nodes = networkCanvas.getNodesInPath(selectionLasso);
				}
				if(edgeSelectionEnabled()) {
					// MKTODO
					edges = networkCanvas.getEdgesInPath(selectionLasso);
					handles = networkCanvas.getHandlesInPath(selectionLasso);
				}
				
				// Select
				if(!nodes.isEmpty())
					re.select(nodes, CyNode.class, true);
				if(!edges.isEmpty())
					re.select(edges, CyEdge.class, true);
				for(HandleKey handle : handles)
					re.getBendStore().selectHandle(handle);
				for(DingAnnotation a : annotations)
					a.setSelected(true);
				
			}
			selectionLasso = null;
			repaint(); // repaint the glass pane
		}
	}

	
	private class SelectionRectangleListener extends MouseAdapter {
		
		private Point mousePressedPoint = null;
		
		@Override
		public void mousePressed(MouseEvent e) {
			if(isDragSelectionKeyDown(e)) {
				mousePressedPoint = e.getPoint();
				selectionRect = new Rectangle(e.getX(), e.getY(), 0, 0);
				changeCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				e.consume();
			}
		}
		
		@Override
		public void mouseDragged(MouseEvent e) {
			if(selectionRect != null) {
				int x = Math.min(mousePressedPoint.x, e.getX());
				int y = Math.min(mousePressedPoint.y, e.getY());
				int w = Math.abs(mousePressedPoint.x - e.getX());
				int h = Math.abs(mousePressedPoint.y - e.getY());
				selectionRect.setBounds(x, y, w, h);
				
				repaint(); // repaint the glass pane
			}
		}
		
		@Override
		public void mouseReleased(MouseEvent e) {
			if(selectionRect != null) {
				List<DingAnnotation> annotations = Collections.emptyList();
				List<HandleKey> handles  = Collections.emptyList();
				List<View<CyNode>> nodes = Collections.emptyList();
				List<View<CyEdge>> edges = Collections.emptyList();

				if(annotationSelectionEnabled()) {
					annotations = cyAnnotator.getAnnotationsIn(selectionRect);
				}
				if(nodeSelectionEnabled()) {
					nodes = networkCanvas.getNodesInRectangle(selectionRect);
				}
				if(edgeSelectionEnabled()) {
					edges = networkCanvas.getEdgesInRectangle(selectionRect);
					handles = networkCanvas.getHandlesInRectangle(selectionRect);
				}
				
				// Select
				if(!nodes.isEmpty())
					re.select(nodes, CyNode.class, true);
				if(!edges.isEmpty())
					re.select(edges, CyEdge.class, true);
				for(HandleKey handle : handles)
					re.getBendStore().selectHandle(handle);
				for(DingAnnotation a : annotations)
					a.setSelected(true);
			}
			selectionRect = null;
			mousePressedPoint = null;
			repaint(); // repaint the glass pane
		}
	}
	
	
	private class CanvasPanListener extends MouseAdapter {
		
		private final Cursor panCursor = createPanCursor();
		private final double[] coords = new double[2];
		
		private Point mousePressedPoint;
		private ViewChangeEdit undoPanEdit;
		
		@Override
		public void mousePressed(MouseEvent e) {
			changeCursor(panCursor);
			mousePressedPoint = e.getPoint();
			e.consume();
		}
		
		@Override
		public void mouseDragged(MouseEvent e) {
			if(mousePressedPoint != null) {
				if(undoPanEdit == null) {
					// Save state on start of drag, that way we don't post an undo edit if the user just clicks.
					// Pass null, don't save node state, just the center location of the canvas.
					undoPanEdit = new ViewChangeEdit(re, null, "Pan", registrar); 
				}
				
				// MKTODO does holding SHIFT matter??
				coords[0] = mousePressedPoint.getX();
				coords[1] = mousePressedPoint.getY();
				re.xformComponentToNodeCoords(coords);
				double oldX = coords[0];
				double oldY = coords[1];
				
				coords[0] = e.getX();
				coords[1] = e.getY();
				re.xformComponentToNodeCoords(coords);
				double newX = coords[0];
				double newY = coords[1];
				
				mousePressedPoint = e.getPoint();
				
				double deltaX = oldX - newX;
				double deltaY = oldY - newY;
				re.pan(deltaX, deltaY);
			}
		}
		
		@Override
		public void mouseReleased(MouseEvent e) {
			if(undoPanEdit != null)
				undoPanEdit.post();
			
			changeCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			mousePressedPoint = null;
			undoPanEdit = null;
		}
		
		private Cursor createPanCursor() {
			if (LookAndFeelUtil.isMac()) {
				Dimension size = Toolkit.getDefaultToolkit().getBestCursorSize(24, 24);
				Image image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
				Graphics graphics = image.getGraphics();
				String icon = IconManager.ICON_ARROWS;
				JLabel label = new JLabel(icon);
				label.setBounds(0, 0, size.width, size.height);
				label.setFont(registrar.getService(IconManager.class).getIconFont(14));
				label.paint(graphics);
				graphics.dispose();
				return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(0, 0), "custom:" + (int) icon.charAt(0));
			} else {
				Cursor panCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
				if(panCursor == null) {
					panCursor = new Cursor(Cursor.MOVE_CURSOR);
				}
				return panCursor;
			}
		}
	}
	
	
	public enum Toggle {
		SELECT, DESELECT, NOCHANGE
	}
	
	
	private class CanvasMouseListener extends MouseAdapter {

		private AnnotationEdit resizeUndoEdit;
		private AnnotationEdit movingUndoEdit;
		private Point mousePressedPoint;
		private ViewChangeEdit undoableEdit;
		
		@Override
		public void mousePressed(MouseEvent e) {
			requestFocusInWindow(); // need to do this to receive key events
			
			// We only care about left mouse button
			if(!isSingleLeftClick(e)) {
				return;
			}
			
			if (1 == 1) return;
			
			mousePressedPoint = e.getPoint();
			networkCanvas.repaint();
			
			if(annotationSelectionEnabled()) {
				AnnotationSelection annotationSelection = cyAnnotator.getAnnotationSelection();
				AnchorLocation anchor = annotationSelection.overAnchor(e.getX(), e.getY());
				
				if(!annotationSelection.isEmpty() && anchor != null) {
					mousePressedHandleAnnotationAnchor(anchor, e);
					return;
				}
				
				DingAnnotation annotation = cyAnnotator.getAnnotationAt(foregroundCanvas, e.getPoint());
				if(annotation != null) {
					mousePressedHandleAnnotation(annotation, e);
					return;
				}
			}
			
			boolean clicked = mousePressedHandleNodesAndEdges(e);
			if(clicked) {
				return;
			}
			
			if(annotationSelectionEnabled()) {
				DingAnnotation annotation = cyAnnotator.getAnnotationAt(backgroundCanvas, e.getPoint());
				if(annotation != null) {
					mousePressedHandleAnnotation(annotation, e);
					return;
				}
			}
		}
		
		private void mousePressedHandleAnnotationAnchor(AnchorLocation anchor, MouseEvent e) {
			AnnotationSelection annotationSelection = cyAnnotator.getAnnotationSelection();
			// save the distance between the anchor location and the mouse location
			double offsetX = e.getX() - annotationSelection.getX() - anchor.getX();
			double offsetY = e.getY() - annotationSelection.getY() - anchor.getY();
			
			changeCursor(getResizeCursor(anchor.getPosition()));
			annotationSelection.setResizing(true);
			annotationSelection.saveAnchor(anchor.getPosition(), offsetX, offsetY);
			annotationSelection.saveBounds();
			
			for(DingAnnotation a: cyAnnotator.getAnnotationSelection()) 
				a.saveBounds();
			
			resizeUndoEdit = new AnnotationEdit("Resize Annotation", cyAnnotator, registrar);
		}
		
		private void mousePressedHandleAnnotation(DingAnnotation annotation, MouseEvent e) {
			boolean selected = annotation.isSelected();
			if(selected && e.isShiftDown()) {
				annotation.setSelected(false);
			} else {
				if(!selected && !e.isPopupTrigger() && !e.isShiftDown() && !((e.isControlDown() || e.isMetaDown()) && !e.isAltDown())) {
					cyAnnotator.clearSelectedAnnotations();
				}
				annotation.setSelected(true);
			}

			AnnotationSelection annotationSelection = cyAnnotator.getAnnotationSelection();
			if(!annotationSelection.isEmpty()) {
//				changeCursor(panCursor);
				annotationSelection.setMoving(true);
				movingUndoEdit = new AnnotationEdit("Move Annotation", cyAnnotator, registrar);
			} else {
				annotationSelection.setMoving(false);
			}

			annotation.getCanvas().repaint();

			// OK, now for all of our selected annotations, remember this mousePressed
			for(DingAnnotation a: cyAnnotator.getAnnotationSelection()) {
				a.setOffset(e.getPoint());
			}
		}
		
		private boolean mousePressedHandleNodesAndEdges(MouseEvent e) {
			View<CyNode> node = null;
			View<CyEdge> edge = null;
			HandleKey handle = null;
			
			Toggle nodeSelected = Toggle.NOCHANGE;
			Toggle edgeSelected = Toggle.NOCHANGE;
			
			// MKTODO move getPickedXXX to InnerCanvas ???
			
			if(nodeSelectionEnabled())
				node = re.getPickedNodeView(e.getPoint());
			
			if(edgeSelectionEnabled() && node == null && checkLOD(GraphRenderer.LOD_EDGE_ANCHORS))
				handle = re.getPickedEdgeHandle(e.getPoint());

			if(edgeSelectionEnabled() && node == null && handle == null)
				edge = re.getPickedEdgeView(e.getPoint());

			if(node != null)
			    nodeSelected = toggleSelectedNode(node, e);

			if(handle != null)
				toggleChosenAnchor(handle, e); // MKTODO this creates an undo edit, figure out what to do with it

			if(edge != null)
				edgeSelected = toggleSelectedEdge(edge, e);

			if((node != null || edge != null) && !(e.isShiftDown() || isControlOrMetaDown(e)))
				re.getBendStore().unselectAllHandles();
			
			if (node == null && edge == null && handle == null) {
//				changeCursor(panCursor);
				// Save all node positions for panning
				undoableEdit = new ViewChangeEdit(re, ViewChangeEdit.SavedObjs.NODES, "Move", registrar);
				// MKTODO what???
//				lod.setDrawEdges(false);
			} else {
				maybeDeselectAll(e, node, edge, handle);
				toggleSelection(node, CyNode.class, nodeSelected);
				toggleSelection(edge, CyEdge.class, edgeSelected);
			}
			
			return node != null || edge != null || handle != null;
		}
		
		private <T extends CyIdentifiable> void toggleSelection(View<T> element, Class<T> type, Toggle toggle) {
			if(element != null) {
				if(toggle == Toggle.SELECT)
					re.select(Collections.singletonList(element), type, true);
				else if(toggle == Toggle.DESELECT)
					re.select(Collections.singletonList(element), type, false);
			}
		}
		
		private Toggle toggleSelectedNode(View<CyNode> nodeView, MouseEvent e) {
			boolean wasSelected = re.getNodeDetails().isSelected(nodeView);
			// Ignore Ctrl if Alt is down so that Ctrl-Alt can be used for edge bends without side effects
			if(wasSelected && (e.isShiftDown() || (isControlOrMetaDown(e) && !e.isAltDown()))) {
				return Toggle.DESELECT;
			} else if(!wasSelected) {
				return Toggle.SELECT;
			}
			return Toggle.NOCHANGE;
		}
		
		private void toggleChosenAnchor(HandleKey chosenAnchor, MouseEvent e) {
			final long edge = chosenAnchor.getEdgeSuid();
			View<CyEdge> ev = re.getViewModelSnapshot().getEdgeView(edge);
			
			// Linux users should use Ctrl-Alt since many window managers capture Alt-drag to move windows
			if(e.isAltDown()) { // Remove handle
				undoableEdit = new ViewChangeEdit(re, ViewChangeEdit.SavedObjs.SELECTED_EDGES, "Remove Edge Handle", registrar);
				setBendAsLockedValue(ev);
				re.getBendStore().removeHandle(chosenAnchor);
			} else {
				boolean selected = re.getBendStore().isHandleSelected(chosenAnchor);
				// Ignore Ctrl if Alt is down so that Ctrl-Alt can be used for edge bends without side effects
				if(selected && (e.isShiftDown() || (isControlOrMetaDown(e) && !e.isAltDown()))) {
					re.getBendStore().unselectHandle(chosenAnchor);
				} else if(!selected) {
					if(!e.isShiftDown() && !(isControlOrMetaDown(e) && !e.isAltDown())) {
						re.getBendStore().unselectAllHandles();
					}
					re.getBendStore().selectHandle(chosenAnchor);
				}
			}
			re.setContentChanged();	
		}
		
		private void setBendAsLockedValue(View<CyEdge> ev) {
			Bend bend = null;
			if(!ev.isValueLocked(BasicVisualLexicon.EDGE_BEND)) {
				Bend defaultBend = re.getViewModelSnapshot().getViewDefault(BasicVisualLexicon.EDGE_BEND);
				if(re.getEdgeDetails().getBend(ev) == defaultBend) {
					bend = new BendImpl((BendImpl) defaultBend);
				} else {
					bend = new BendImpl((BendImpl) re.getEdgeDetails().getBend(ev));
				}
			}
			if(bend != null) {
				View<CyEdge> mutableEdgeView = re.getViewModel().getEdgeView(ev.getModel());
				if(mutableEdgeView != null) {
					mutableEdgeView.setLockedValue(BasicVisualLexicon.EDGE_BEND, bend);
				}
			}
		}
		
		private Toggle toggleSelectedEdge(View<CyEdge> edgeView, MouseEvent e) {
			if(edgeView == null)
				return Toggle.NOCHANGE;
			
			boolean wasSelected = re.getEdgeDetails().isSelected(edgeView);
			
			// Add new Handle for Edge Bend.
			// Linux users should use Ctrl-Alt since many window managers capture Alt-drag to move windows
			if ((e.isAltDown()) && checkLOD(GraphRenderer.LOD_EDGE_ANCHORS)) {
				re.getBendStore().unselectAllHandles();
				double[] ptBuff = {e.getX(), e.getY()};
				re.xformComponentToNodeCoords(ptBuff);
				// Store current handle list
				undoableEdit = new ViewChangeEdit(re, ViewChangeEdit.SavedObjs.SELECTED_EDGES, "Add Edge Handle", registrar);
				
				Point2D newHandlePoint = new Point2D.Float((float) ptBuff[0], (float) ptBuff[1]);
				Bend defaultBend = re.getViewModelSnapshot().getViewDefault(BasicVisualLexicon.EDGE_BEND);
				
				if (edgeView.getVisualProperty(BasicVisualLexicon.EDGE_BEND) == defaultBend) {
					View<CyEdge> mutableEdgeView = re.getViewModel().getEdgeView(edgeView.getSUID());
					if(mutableEdgeView != null) {
						if (defaultBend instanceof BendImpl)
							mutableEdgeView.setLockedValue(BasicVisualLexicon.EDGE_BEND, new BendImpl((BendImpl) defaultBend));
						else
							mutableEdgeView.setLockedValue(BasicVisualLexicon.EDGE_BEND, new BendImpl());
					}
				}
				
				HandleKey handleKey = re.getBendStore().addHandle(edgeView, newHandlePoint);
				re.getBendStore().selectHandle(handleKey);
			}

			Toggle toggle = Toggle.NOCHANGE;
			// Ignore Ctrl if Alt is down so that Ctrl-Alt can be used for edge bends without side effects
			if (wasSelected && (e.isShiftDown() || (isControlOrMetaDown(e) && !e.isAltDown()))) {
				toggle = Toggle.DESELECT;
			} else if (!wasSelected) {
				toggle = Toggle.SELECT;

				if (checkLOD(GraphRenderer.LOD_EDGE_ANCHORS)) {
					double[] ptBuff = {e.getX(), e.getY()};
					re.xformComponentToNodeCoords(ptBuff);
					HandleKey hit = re.getBendStore().pickHandle((float) ptBuff[0], (float) ptBuff[1]);
					if(hit != null) {
						re.getBendStore().selectHandle(hit);
					}
				}
			}

			re.setContentChanged();
			return toggle;
		}
		
		
		@Override
		public void mouseReleased(MouseEvent e) {
			changeCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			
			if(!isSingleLeftClick(e)) // We only care about left mouse button
				return;
			
			if(annotationSelectionEnabled()) {
				mouseReleasedHandleAnnotations(e);
			}
//			mouseReleasedHandleNodesAndEdges(e);
			
			mousePressedPoint = null;
			
			networkCanvas.repaint();
			if(undoableEdit != null)
				undoableEdit.post();
		}
		
		private void mouseReleasedHandleAnnotations(MouseEvent e) {
//			AnnotationSelection annotationSelection = cyAnnotator.getAnnotationSelection();
//			annotationSelection.setResizing(false);
//			annotationSelection.setMoving(false);
//
//			if(resizeUndoEdit != null) 
//				resizeUndoEdit.post();
//			if(movingUndoEdit != null)
//				movingUndoEdit.post();
			
			// MKTODO replace this with the selection rectangle
			if(mousePressedPoint != null) {
				double startX = Math.min(mousePressedPoint.getX(), e.getX());
				double startY = Math.min(mousePressedPoint.getY(), e.getY());
				double endX   = Math.max(mousePressedPoint.getX(), e.getX());
				double endY   = Math.max(mousePressedPoint.getY(), e.getY());
				// Assume we did a sweep select
				Rectangle2D sweepArea = new Rectangle2D.Double(startX, startY, endX-startX, endY-startY);
				List<DingAnnotation> annotations = cyAnnotator.getAnnotationsIn(sweepArea);
				for (DingAnnotation a: annotations) {
					a.setSelected(true);
				}
			} 
//			else {
//				// now for all of our selected annotations, clear the mousePressed
//				for (DingAnnotation a: annotationSelection) {
//					a.setOffset(null);
//				}
//			}
		}
		
//		private void mouseReleasedHandleNodesAndEdges(MouseEvent e) {
//			if(selectionRect != null) {
//				List<HandleKey> handles  = Collections.emptyList();
//				List<View<CyNode>> nodes = Collections.emptyList();
//				List<View<CyEdge>> edges = Collections.emptyList();
//
//				// MKTODO these methods should not be side effecting
//					
//				if(nodeSelectionEnabled()) {
//					nodes = networkCanvas.getNodesInRectangle(selectionRect);
//				}
//				if(edgeSelectionEnabled()) {
//					edges = networkCanvas.getEdgesInRectangle(selectionRect);
//					handles = networkCanvas.getHandlesInRectangle(selectionRect);
//				}
//				
//				if(!nodes.isEmpty())
//					re.select(nodes, CyNode.class, true);
//				if(!edges.isEmpty())
//					re.select(edges, CyEdge.class, true);
//				if(!handles.isEmpty()) {
//					for(HandleKey handle : handles) {
//						re.getBendStore().selectHandle(handle);
//					}
//				}
//			}
//			selectionRect = null;
//		}
		
		
		@Override
		public void mouseClicked(MouseEvent e) {
			if (1 == 1) return;
			
			// DONE probably
			if(cyAnnotator.getResizeShape() != null) {
				cyAnnotator.getResizeShape().contentChanged();
				cyAnnotator.resizeShape(null);
				cyAnnotator.postUndoEdit(); // markUndoEdit() is in the dialogs like ShapeAnnotationDialog
				return;
			}
			if(cyAnnotator.getRepositioningArrow() != null) {
				cyAnnotator.getRepositioningArrow().contentChanged();
				cyAnnotator.positionArrow(null);
				cyAnnotator.postUndoEdit(); // markUndoEdit() is in ArrowAnnotationDialog
				return;
			}

			if(annotationSelectionEnabled()) {
				DingAnnotation annotation = cyAnnotator.getAnnotationAt(e.getPoint());
				if(annotation != null && isDoubleLeftClick(e)) {
					editAnnotation(annotation, e.getPoint());
				}
				if(annotation == null && (!e.isPopupTrigger() && !e.isShiftDown() && !(isControlOrMetaDown(e) && !e.isAltDown()))) {
					cyAnnotator.clearSelectedAnnotations();
				}
			}
		}
		
		
		private void editAnnotation(DingAnnotation annotation, Point p) {
			invokeOnEDT(() -> {
				EditAnnotationTaskFactory tf = new EditAnnotationTaskFactory(registrar.getService(DingRenderer.class));
				DialogTaskManager dtm = cyAnnotator.getRegistrar().getService(DialogTaskManager.class);
				dtm.execute(tf.createTaskIterator(re.getViewModel(), annotation, p));
			});
		}
		
		
		@Override
		public void mouseDragged(MouseEvent e) {
			if(!isSingleLeftClick(e))
				return;
			
//			AnnotationSelection annotationSelection = cyAnnotator.getAnnotationSelection();
//			if(!annotationSelection.isEmpty()) {
//				if(annotationSelection.isResizing()) {
//					annotationSelection.resizeAnnotationsRelative(e.getX(), e.getY());
//				} else if(annotationSelection.isMoving()) {
//					annotationSelection.moveSelection(e.getX(), e.getY());
//				}
//			}
			
//			if(selectionRect != null) {
//				int x = Math.min(mousePressedPoint.x, e.getX());
//				int y = Math.min(mousePressedPoint.y, e.getY());
//				int w = Math.abs(mousePressedPoint.x - e.getX());
//				int h = Math.abs(mousePressedPoint.y - e.getY());
//				selectionRect.setBounds(x, y, w, h);
//				// repaint the glass pane
//				repaint(); // MKTODO is this needed?
//			}
			
//			if(!isDragSelectionKeyDown(e)) {
//				double deltaX = e.getX() - mousePressedPoint.getX();
//				double deltaY = e.getY() - mousePressedPoint.getY();
//				mousePressedPoint = e.getPoint();
//				re.pan(deltaX, deltaY);
//				return;
//			}
//			
//			mouseDraggedHandleNodesAndEdges(e);
			
			networkCanvas.repaint();
		}

		private void mouseDraggedHandleNodesAndEdges(MouseEvent e) {
			if(undoableEdit == null)
				undoableEdit = new ViewChangeEdit(re, ViewChangeEdit.SavedObjs.SELECTED, "Move", registrar);
			
			double[] ptBuff = {mousePressedPoint.getX(), mousePressedPoint.getY()};
			re.xformComponentToNodeCoords(ptBuff);
			final double oldX = ptBuff[0];
			final double oldY = ptBuff[1];
			mousePressedPoint = e.getPoint();
			ptBuff[0] = mousePressedPoint.getX();
			ptBuff[1] = mousePressedPoint.getY();
			re.xformComponentToNodeCoords(ptBuff);

			final double newX = ptBuff[0];
			final double newY = ptBuff[1];
			double deltaX = newX - oldX;
			double deltaY = newY - oldY;

			// If the shift key is down, then only move horizontally, vertically, or diagonally, depending on the slope.
			if (e.isShiftDown()) {
				final double slope = deltaY / deltaX;

				// slope of 2.41 ~ 67.5 degrees (halfway between 45 and 90)
				// slope of 0.41 ~ 22.5 degrees (halfway between 0 and 45)
				if ((slope > 2.41) || (slope < -2.41)) {
					deltaX = 0.0; // just move vertical
				} else if ((slope < 0.41) && (slope > -0.41)) {
					deltaY = 0.0; // just move horizontal
				} else {
					final double avg = (Math.abs(deltaX) + Math.abs(deltaY)) / 2.0;
					deltaX = (deltaX < 0) ? (-avg) : avg;
					deltaY = (deltaY < 0) ? (-avg) : avg;
				}
			}

			Collection<View<CyNode>> selectedNodes = re.getViewModelSnapshot().getTrackedNodes(CyNetworkViewConfig.SELECTED_NODES);
			
			// MKTODO rename to 'handlesToMove'
			Set<HandleKey> anchorsToMove = re.getBendStore().getSelectedHandles();
			
			if (anchorsToMove.isEmpty()) { // If we are moving anchors of edges, no need to move nodes (bug #2360).
				for (View<CyNode> node : selectedNodes) {
					View<CyNode> mutableNode = re.getViewModel().getNodeView(node.getSUID());
					if(mutableNode != null) {
						NodeDetails nodeDetails = re.getNodeDetails();
						double oldXPos = nodeDetails.getXPosition(mutableNode);
						double oldYPos = nodeDetails.getYPosition(mutableNode);
						// MKTODO Should setting VPs be done using NodeDetails as well??
						mutableNode.setVisualProperty(BasicVisualLexicon.NODE_X_LOCATION, oldXPos + deltaX);
						mutableNode.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, oldYPos + deltaY);
					}
			    }
			} else {
				for (HandleKey handleKey : anchorsToMove) {
					View<CyEdge> ev = re.getViewModelSnapshot().getEdgeView(handleKey.getEdgeSuid());

					if (!ev.isValueLocked(BasicVisualLexicon.EDGE_BEND)) {
						Bend defaultBend = re.getViewModelSnapshot().getViewDefault(BasicVisualLexicon.EDGE_BEND);
						View<CyEdge> mutableEdgeView = re.getViewModel().getEdgeView(ev.getSUID());
						if(mutableEdgeView != null) {
							if( ev.getVisualProperty(BasicVisualLexicon.EDGE_BEND) == defaultBend ) {
								if( defaultBend instanceof BendImpl )
									mutableEdgeView.setLockedValue(BasicVisualLexicon.EDGE_BEND, new BendImpl((BendImpl)defaultBend));
								else
									mutableEdgeView.setLockedValue(BasicVisualLexicon.EDGE_BEND, new BendImpl());
							} else {
								Bend bend = re.getEdgeDetails().getBend(ev, true);
								mutableEdgeView.setLockedValue(BasicVisualLexicon.EDGE_BEND, new BendImpl((BendImpl)bend));
							}
						}
					}
					final Bend bend = ev.getVisualProperty(BasicVisualLexicon.EDGE_BEND);
					//TODO: Refactor to fix this ordering problem.
					//This test is necessary because in some instances, an anchor can still be present in the selected
					//anchor list, even though the anchor has been removed. A better fix would be to remove the
					//anchor from that list before this code is ever reached. However, this is not currently possible
					//under the present API, so for now we just detect this situation and continue.
					if( bend.getAllHandles().isEmpty() )
						continue;
					final Handle handle = bend.getAllHandles().get(handleKey.getHandleIndex());
					final Point2D newPoint = handle.calculateHandleLocation(re.getViewModelSnapshot(), ev);
					
					float x = (float) newPoint.getX();
					float y = (float) newPoint.getY();
					
					re.getBendStore().moveHandle(handleKey, x + (float)deltaX, y + (float)deltaY);
				}
			}
			
			if (!selectedNodes.isEmpty() || !re.getBendStore().getSelectedHandles().isEmpty()) {
				re.setContentChanged();
			}
			if (!selectedNodes.isEmpty() && re.getBendStore().getSelectedHandles().isEmpty()) {
				networkCanvas.setHideEdges();
			}
		}
		
		
		@Override
		public void mouseMoved(MouseEvent e) {
			if (1 == 1) return;
			
			mouseMovedHandleAnnotations(e);
		}
		
		private void mouseMovedHandleAnnotations(MouseEvent e) {
			// I think this handles when you first add an annotation to the canvas and it auto-resizes
			
			AbstractAnnotation resizeAnnotation = cyAnnotator.getResizeShape();
			// DingAnnotation moveAnnotation = cyAnnotator.getMovingAnnotation();
			AnnotationSelection annotationSelection = cyAnnotator.getAnnotationSelection();
			ArrowAnnotationImpl repositionAnnotation = cyAnnotator.getRepositioningArrow();
			
			if (resizeAnnotation == null && annotationSelection.isEmpty() && repositionAnnotation == null) {
				return;
			}

			int mouseX = e.getX();
			int mouseY = e.getY();

			if (resizeAnnotation != null) {
				Rectangle2D initialBounds = cyAnnotator.getResizeBounds();
				Rectangle2D bounds = AnnotationSelection.resize(Position.SOUTH_EAST, initialBounds, mouseX, mouseY);
				// must call setLocation and setSize instead of setBounds because those methods are overridden
				resizeAnnotation.setLocation((int)bounds.getX(), (int)bounds.getY());
				resizeAnnotation.resizeAnnotation(bounds.getWidth(), bounds.getHeight());
				resizeAnnotation.update();
				resizeAnnotation.getCanvas().repaint();
			} else if (repositionAnnotation != null) {
				Point2D mousePoint = new Point2D.Double(mouseX, mouseY);

				// See what's under our mouse
				// Annotation?
				List<DingAnnotation> annotations = cyAnnotator.getAnnotationsAt(mousePoint);
				if (annotations.contains(repositionAnnotation))
					annotations.remove(repositionAnnotation);

				if (annotations.size() > 0) {
					repositionAnnotation.setTarget(annotations.get(0));

				// Node?
				} else if (overNode(mousePoint)) {
					CyNode overNode = getNodeAtLocation(mousePoint);
					repositionAnnotation.setTarget(overNode);

				// Nope, just set the point
				} else {
					repositionAnnotation.setTarget(mousePoint);
				}

				repositionAnnotation.update();
				repositionAnnotation.getCanvas().repaint();
			}
		}
		
	}
	
	
	@Override
	public void processMouseEvent(MouseEvent e) {
		// expose processMouseEvent so that the birds-eye-view can pass mouse events here.
		super.processMouseEvent(e);
	}
	
	@Override
	public void processMouseWheelEvent(MouseWheelEvent e) {
		// expose processMouseWheelEvent so that the birds-eye-view can pass mouse wheel events here.
		super.processMouseWheelEvent(e);
	}
	
	
	// Utility methods below
	
	private static boolean isLeftClick(MouseEvent e) {
		boolean b = e.getButton() == MouseEvent.BUTTON1;
		if(LookAndFeelUtil.isMac()) {
			return !e.isControlDown() && b;
		}
		return b;
	}

	private static boolean isRightClick(MouseEvent e) {
		boolean b = e.getButton() == MouseEvent.BUTTON3; 
		if(!b && LookAndFeelUtil.isMac()) {
			// control - right click
			return e.isControlDown() && !e.isMetaDown() && (e.getButton() == MouseEvent.BUTTON1);
		}
		return b;
	}

	private static boolean isMiddleClick(MouseEvent e) {
		return e.getButton() == MouseEvent.BUTTON2; 
	}
	
	private static boolean isSingleClick(MouseEvent e) {
		return e.getClickCount() == 1;
	}
	
	private static boolean isDoubleClick(MouseEvent e) {
		return e.getClickCount() == 2;
	}
	
	private static boolean isSingleLeftClick(MouseEvent e) {
		return isLeftClick(e) && isSingleClick(e);
	}
	
	private static boolean isSingleRightClick(MouseEvent e) {
		return isRightClick(e) && isSingleClick(e);
	}
	
	private static boolean isDoubleLeftClick(MouseEvent e) {
		return isLeftClick(e) && isDoubleClick(e);
	}
	
	
	private boolean annotationSelectionEnabled() {
		return re.getViewModelSnapshot().getVisualProperty(DVisualLexicon.NETWORK_ANNOTATION_SELECTION);
	}
	
	private boolean nodeSelectionEnabled() {
		return re.getViewModelSnapshot().getVisualProperty(DVisualLexicon.NETWORK_NODE_SELECTION);
	}
	
	private boolean edgeSelectionEnabled() {
		return re.getViewModelSnapshot().getVisualProperty(DVisualLexicon.NETWORK_EDGE_SELECTION);
	}
	
	
	private boolean checkLOD(int flag) {
		return (networkCanvas.getLastRenderDetail() & flag) != 0;
	}

	private boolean overNode(Point2D mousePoint) {
		return re.getPickedNodeView(mousePoint) != null;
	}

	private CyNode getNodeAtLocation(Point2D mousePoint) {
		return re.getPickedNodeView(mousePoint).getModel();
	}
	
	
	private void maybeDeselectAll(MouseEvent e, View<CyNode> chosenNode, View<CyEdge> chosenEdge, HandleKey chosenAnchor) {
		// Ignore Ctrl if Alt is down so that Ctrl-Alt can be used for edge bends without side effects
		if ((!e.isShiftDown() && !(e.isControlDown() && !e.isAltDown()) && !e.isMetaDown()) // If shift is down never unselect.
		    && ((chosenNode == null && chosenEdge == null && chosenAnchor == null) // Mouse missed all.
		       // Not [we hit something but it was already selected].
		       || !(((chosenNode != null) && re.isNodeSelected(chosenNode)) || (chosenAnchor != null) || ((chosenEdge != null) && re.isEdgeSelected(chosenEdge)) ))) {
			
			Collection<View<CyNode>> selectedNodes = re.getViewModelSnapshot().getTrackedNodes(CyNetworkViewConfig.SELECTED_NODES);
			Collection<View<CyEdge>> selectedEdges = re.getViewModelSnapshot().getTrackedEdges(CyNetworkViewConfig.SELECTED_EDGES);
			// de-select
			re.select(selectedNodes, CyNode.class, false);
			re.select(selectedEdges, CyEdge.class, false);
		}
	}
	
	private void changeCursor(Cursor cursor) {
		String componentName = "__CyNetworkView_" + re.getViewModel().getSUID(); // see ViewUtil.createUniqueKey(CyNetworkView)
		Container parent = this;
		while(parent != null) {
			if(componentName.equals(parent.getName())) {
				parent.setCursor(cursor);
				break;
			}
			parent = parent.getParent();
		}
	}
	
	
	private static Cursor getResizeCursor(Position anchor) {
		switch(anchor) {
			case NORTH_EAST: return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
			case NORTH:      return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
			case NORTH_WEST: return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
			case WEST:       return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
			case SOUTH_WEST: return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
			case SOUTH:      return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
			case SOUTH_EAST: return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
			case EAST:       return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
			default:         return null;
		}
	}
	
	
}
