package org.cytoscape.internal.view;

import static org.cytoscape.internal.util.ViewUtil.createUniqueKey;
import static org.cytoscape.internal.util.ViewUtil.getTitle;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.internal.view.NetworkViewGrid.ThumbnailPanel;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.destroy.DestroyNetworkViewTaskFactory;
import org.cytoscape.util.swing.LookAndFeelUtil;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.presentation.RenderingEngine;
import org.cytoscape.view.presentation.RenderingEngineFactory;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.swing.DialogTaskManager;

/*
 * #%L
 * Cytoscape Swing Application Impl (swing-application-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2016 The Cytoscape Consortium
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

@SuppressWarnings("serial")
public class NetworkViewMainPanel extends JPanel {

	private JPanel contentPane;
	private final CardLayout cardLayout;
	private final NetworkViewGrid networkViewGrid;
	
	private final Map<String, NetworkViewContainer> viewContainers;
	private final Map<String, NetworkViewFrame> viewFrames;
	private final Map<String, NetworkViewComparisonPanel> comparisonPanels;
	
	private final Set<CyNetworkView> dirtyThumbnails;
	
	private NetworkViewFrame currentViewFrame;
	private boolean gridMode;
	
	private final MousePressedAWTEventListener mousePressedAWTEventListener;
	
	private final CytoscapeMenus cyMenus;
	private final Comparator<CyNetworkView> viewComparator;
	private final CyServiceRegistrar serviceRegistrar;

	public NetworkViewMainPanel(final CytoscapeMenus cyMenus, final Comparator<CyNetworkView> viewComparator,
			final CyServiceRegistrar serviceRegistrar) {
		this.cyMenus = cyMenus;
		this.viewComparator = viewComparator;
		this.serviceRegistrar = serviceRegistrar;
		
		viewContainers = new LinkedHashMap<>();
		viewFrames = new HashMap<>();
		comparisonPanels = new HashMap<>();
		dirtyThumbnails = new HashSet<>();
		
		mousePressedAWTEventListener = new MousePressedAWTEventListener();
		
		cardLayout = new CardLayout();
		networkViewGrid = createNetworkViewGrid();
		
		init();
	}

	public RenderingEngine<CyNetwork> addNetworkView(final CyNetworkView view,
			final RenderingEngineFactory<CyNetwork> engineFactory, boolean showView) {
		if (isRendered(view))
			return null;
		
		final GraphicsConfiguration gc = currentViewFrame != null ? currentViewFrame.getGraphicsConfiguration() : null;
		
		final NetworkViewContainer vc = new NetworkViewContainer(view, view.equals(getCurrentNetworkView()),
				engineFactory, serviceRegistrar);
		
		vc.getGridModeButton().addActionListener((ActionEvent e) -> {
			setGridMode(true);
			networkViewGrid.requestFocusInWindow();
		});
		vc.getDetachViewButton().addActionListener((ActionEvent e) -> {
			detachNetworkView(view);
		});
		vc.getReattachViewButton().addActionListener((ActionEvent e) -> {
			reattachNetworkView(view);
		});
		vc.getViewTitleTextField().addActionListener((ActionEvent e) -> {
			changeCurrentViewTitle(vc);
			vc.requestFocusInWindow();
		});
		vc.getViewTitleTextField().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					cancelViewTitleChange(vc);
			}
		});
		vc.getViewTitleTextField().addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				changeCurrentViewTitle(vc);
				vc.requestFocusInWindow();
				Toolkit.getDefaultToolkit().addAWTEventListener(mousePressedAWTEventListener,
						MouseEvent.MOUSE_EVENT_MASK);
			}
			@Override
			public void focusGained(FocusEvent e) {
				Toolkit.getDefaultToolkit().removeAWTEventListener(mousePressedAWTEventListener);
			}
		});
		
		viewContainers.put(vc.getName(), vc);
		networkViewGrid.addItem(vc.getRenderingEngine());
		getContentPane().add(vc, vc.getName());
		
		if (showView) {
			setDirtyThumbnail(view);
			
			if (isGridMode())
				updateGrid();
			else
				showViewContainer(vc.getName());
			
			// If the latest focused view was in a detached frame,
			// detach the new one as well and put it in the same monitor
			if (gc != null)
				detachNetworkView(view, gc);
		} else {
			if (isGridMode())
				updateGrid();
			else
				setGridMode(true);
		}
		
		return vc.getRenderingEngine();
	}
	
	public boolean isRendered(final CyNetworkView view) {
		final String name = createUniqueKey(view);
		return viewContainers.containsKey(name) || viewFrames.containsKey(name);
	}

	public void remove(final CyNetworkView view) {
		if (view == null)
			return;
		
		dirtyThumbnails.remove(view);
		
		RenderingEngine<CyNetwork> re = null;
		final int total = getContentPane().getComponentCount();
		
		for (int i = 0; i < total; i++) {
			final Component c = getContentPane().getComponent(i);
			
			if (c instanceof NetworkViewContainer) {
				final NetworkViewContainer vc = (NetworkViewContainer) c;
				
				if (vc.getNetworkView().equals(view)) {
					cardLayout.removeLayoutComponent(c);
					viewContainers.remove(vc.getName());
					re = vc.getRenderingEngine();
					
					vc.dispose();
					
					break;
				}
			} else if (c instanceof NetworkViewComparisonPanel) {
				// TODO
			}
		}
		
		final NetworkViewFrame frame = viewFrames.remove(createUniqueKey(view));
		
		if (frame != null) {
			re = frame.getRenderingEngine();
			
			frame.getRootPane().getLayeredPane().removeAll();
			frame.getRootPane().getContentPane().removeAll();
			frame.dispose();
			
			for (ComponentListener l : frame.getComponentListeners())
				frame.removeComponentListener(l);
			
			for (WindowListener l : frame.getWindowListeners())
				frame.removeWindowListener(l);
		}
		
		if (re != null) {
			networkViewGrid.removeItems(Collections.singleton(re));
			showGrid();
		}
	}
	
	public void setSelectedNetworkViews(final Collection<CyNetworkView> networkViews) {
		networkViewGrid.setSelectedNetworkViews(networkViews);
	}
	
	public List<CyNetworkView> getSelectedNetworkViews() {
		return networkViewGrid.getSelectedNetworkViews();
	}

	public CyNetworkView getCurrentNetworkView() {
		return networkViewGrid.getCurrentNetworkView();
	}
	
	public void setCurrentNetworkView(final CyNetworkView view) {
		final boolean currentViewChanged = networkViewGrid.setCurrentNetworkView(view);
		
		if (currentViewChanged) {
			if (view == null) {
				showGrid();
			} else {
				if (isGridMode()) {
					if (isGridVisible())
						updateGrid();
					else
						showGrid();
				} else {
					showViewContainer(createUniqueKey(view));
				}
				
				if (isGridVisible()) {
					final ThumbnailPanel tp = networkViewGrid.getCurrentItem();
				
					if (tp != null && tp.getParent() instanceof JComponent)
						((JComponent) tp.getParent()).scrollRectToVisible(tp.getBounds());
				}
			}
		}
	}
	
	public void showGrid() {
		if (!isGridVisible()) {
			cardLayout.show(getContentPane(), networkViewGrid.getName());
			updateGrid();
		}
	}

	public void updateGrid() {
		networkViewGrid.update(networkViewGrid.getThumbnailSlider().getValue()); // TODO remove it when already updating after view changes
		networkViewGrid.getReattachAllViewsButton().setEnabled(!viewFrames.isEmpty());
		
		final HashSet<CyNetworkView> dirtySet = new HashSet<>(dirtyThumbnails);
		
		for (CyNetworkView view : dirtySet)
			updateThumbnail(view);
	}
	
	public NetworkViewFrame detachNetworkView(final CyNetworkView view) {
		if (view == null)
			return null;
		
		final GraphicsConfiguration gc = serviceRegistrar.getService(CySwingApplication.class).getJFrame()
				.getGraphicsConfiguration();
		
		return detachNetworkView(view, gc);
	}
	
	public NetworkViewFrame detachNetworkView(final CyNetworkView view, final GraphicsConfiguration gc) {
		if (view == null)
			return null;
		
		final NetworkViewContainer vc = getNetworkViewContainer(view);
		
		if (vc == null)
			return null;
		
		// Show grid first to prevent changing the current view
		getNetworkViewGrid().setDetached(vc.getNetworkView(), true);
		showGrid();
		
		// Remove the container from the card layout
		cardLayout.removeLayoutComponent(vc);
		viewContainers.remove(vc.getName());
		
		// Create and show the frame
		final NetworkViewFrame frame = new NetworkViewFrame(vc, gc, cyMenus.createViewFrameToolBar(), serviceRegistrar);
		vc.setDetached(true);
		vc.setComparing(false);
		
		viewFrames.put(vc.getName(), frame);
		
		if (!LookAndFeelUtil.isAquaLAF())
			frame.setJMenuBar(cyMenus.createDummyMenuBar());
		
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowActivated(WindowEvent e) {
				// So Tunable dialogs open in the same monitor of the current frame
				serviceRegistrar.getService(DialogTaskManager.class).setExecutionContext(frame);
				
				currentViewFrame = frame;
				
				// This is necessary because the same menu bar is used by other frames, including CytoscapeDesktop
				final JMenuBar menuBar = cyMenus.getJMenuBar();
				final Window window = SwingUtilities.getWindowAncestor(menuBar);

				if (!frame.equals(window)) {
					if (window instanceof JFrame && !LookAndFeelUtil.isAquaLAF()) {
						// Do this first, or the user could see the menu disappearing from the out-of-focus windows
						final JMenuBar dummyMenuBar = cyMenus.createDummyMenuBar();
						((JFrame) window).setJMenuBar(dummyMenuBar);
						dummyMenuBar.updateUI();
						window.repaint();
					}

					frame.setJMenuBar(menuBar);
					menuBar.updateUI();
				}
			}
			@Override
			public void windowClosed(WindowEvent e) {
				reattachNetworkView(view);
			}
			@Override
			public void windowOpened(WindowEvent e) {
				// Add another window listener so subsequent Window Activated events trigger
				// a current view change action.
				// It has to be done this way (after the Open event), otherwise it can cause infinite loops
				// when detaching more than one view at the same time.
				frame.addWindowListener(new WindowAdapter() {
					@Override
					public void windowActivated(WindowEvent e) {
						setSelectedNetworkViews(Collections.singletonList(frame.getNetworkView()));
						setCurrentNetworkView(frame.getNetworkView());
					}
				});
			}
		});
		frame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				view.setVisualProperty(BasicVisualLexicon.NETWORK_WIDTH, (double)frame.getContentPane().getWidth());
				view.setVisualProperty(BasicVisualLexicon.NETWORK_HEIGHT, (double)frame.getContentPane().getHeight());
				view.updateView();
			};
		});
		
		int w = view.getVisualProperty(BasicVisualLexicon.NETWORK_WIDTH).intValue();
		int h = view.getVisualProperty(BasicVisualLexicon.NETWORK_HEIGHT).intValue();
		final boolean resizable = !view.isValueLocked(BasicVisualLexicon.NETWORK_WIDTH) &&
				!view.isValueLocked(BasicVisualLexicon.NETWORK_HEIGHT);
		
		if (w > 0 && h > 0)
			frame.getContentPane().setPreferredSize(new Dimension(w, h));
		
		frame.pack();
		frame.setResizable(resizable);
		frame.setVisible(true);
		
		return frame;
	}
	
	public void reattachNetworkView(final CyNetworkView view) {
		final NetworkViewFrame frame = getNetworkViewFrame(view);
		
		if (frame != null) {
			final NetworkViewContainer vc = frame.getNetworkViewContainer();
			
			frame.setJMenuBar(null);
			frame.dispose();
			viewFrames.remove(vc.getName());
			
			vc.setDetached(false);
			vc.setComparing(false);
			getContentPane().add(vc, vc.getName());
			viewContainers.put(vc.getName(), vc);
			getNetworkViewGrid().setDetached(view, false);
			
			if (!isGridMode() && view.equals(getCurrentNetworkView()))
				showViewContainer(vc.getName());
		}
	}
	
	public void updateThumbnail(final CyNetworkView view) {
		networkViewGrid.updateThumbnail(view);
		dirtyThumbnails.remove(view);
	}
	
	public void updateThumbnailPanel(final CyNetworkView view, final boolean redraw) {
		// If the Grid is not visible, just flag this view as dirty.
		if (isGridVisible()) {
			final ThumbnailPanel tp = networkViewGrid.getItem(view);
			
			if (tp != null)
				tp.update(redraw);
			
			if (redraw)
				dirtyThumbnails.remove(view);
		} else {
			setDirtyThumbnail(view);
		}
	}
	
	public void setDirtyThumbnail(final CyNetworkView view) {
		dirtyThumbnails.add(view);
	}
	
	public void update(final CyNetworkView view) {
		final NetworkViewFrame frame = getNetworkViewFrame(view);
		
		if (frame != null) {
			// Frame Title
			frame.setTitle(getTitle(view));
			
			// Frame Size
			final int w = view.getVisualProperty(BasicVisualLexicon.NETWORK_WIDTH).intValue();
			final int h = view.getVisualProperty(BasicVisualLexicon.NETWORK_HEIGHT).intValue();
			final boolean resizable = !view.isValueLocked(BasicVisualLexicon.NETWORK_WIDTH) &&
					!view.isValueLocked(BasicVisualLexicon.NETWORK_HEIGHT);
			
			if (w > 0 && h > 0) {
				if (w != frame.getContentPane().getWidth() && 
					h != frame.getContentPane().getHeight()) {
					frame.getContentPane().setPreferredSize(new Dimension(w, h));
					frame.pack();
				}
			}
			
			frame.setResizable(resizable);
			frame.update();
			frame.invalidate();
		} else if (!isGridVisible()) {
			final NetworkViewContainer vc = getNetworkViewContainer(view);
			
			if (vc != null && vc.equals(getCurrentViewContainer()))
				vc.update();
			
			final NetworkViewComparisonPanel cp = getComparisonPanel(view);
			
			if (cp != null)
				cp.update();
		}
		
		updateThumbnailPanel(view, false);
	}
	
	public boolean isEmpty() {
		return viewFrames.isEmpty() && viewContainers.isEmpty();
	}
	
	public NetworkViewGrid getNetworkViewGrid() {
		return networkViewGrid;
	}
	
	public Set<NetworkViewFrame> getAllNetworkViewFrames() {
		return new HashSet<>(viewFrames.values());
	}
	
	public Set<NetworkViewContainer> getAllNetworkViewContainers() {
		final Set<NetworkViewContainer> set = new HashSet<>(viewContainers.values());
		
		for (NetworkViewFrame f : viewFrames.values())
			set.add(f.getNetworkViewContainer());
		
		for (NetworkViewComparisonPanel c : comparisonPanels.values()) {
			set.add(c.getContainer1());
			set.add(c.getContainer2());
		}
		
		return set;
	}
	
	/**
	 * @param view
	 * @return The current NetworkViewContainer
	 */
	public NetworkViewContainer showViewContainer(final CyNetworkView view) {
		return view != null ? showViewContainer(createUniqueKey(view)) : null;
	}
	
	/**
	 * @param name
	 * @return The current NetworkViewContainer
	 */
	private NetworkViewContainer showViewContainer(final String name) {
		NetworkViewContainer viewContainer = null;
		
		if (name != null) {
			viewContainer = viewContainers.get(name);
			
			if (viewContainer != null) {
				cardLayout.show(getContentPane(), name);
				viewContainer.update();
				currentViewFrame = null;
			} else {
				for (NetworkViewComparisonPanel cp : comparisonPanels.values()) {
					if (name.equals(cp.getName())
							|| name.equals(cp.getContainer1().getName())
							|| name.equals(cp.getContainer2().getName())) {
						cardLayout.show(getContentPane(), cp.getName());
						cp.update();
						currentViewFrame = null;
						viewContainer = cp.getViewPanel1().isCurrent() ? cp.getContainer1() : cp.getContainer2();
						break;
					}
				}
			}
		} else {
			showGrid();
		}
		
		return viewContainer;
	}

	private void showViewFrame(final NetworkViewFrame frame) {
		frame.setVisible(true);
		frame.toFront();
		showGrid();
	}
	
	protected void showComparisonPanel(final int orientation, final CyNetworkView view1, final CyNetworkView view2) {
		final CyNetworkView currentView = getCurrentNetworkView();
		final String key = NetworkViewComparisonPanel.createUniqueKey(view1, view2);
		NetworkViewComparisonPanel cp = comparisonPanels.get(key);
		
		if (cp == null) {
			// End previous comparison panels that have one of the new selected views first
			cp = getComparisonPanel(view1);
			
			if (cp != null)
				endComparison(cp);
			
			cp = getComparisonPanel(view2);
			
			if (cp != null)
				endComparison(cp);
			
			// Then check if any of the views are detached
			final NetworkViewFrame frame1 = getNetworkViewFrame(view1);
			
			if (frame1 != null)
				reattachNetworkView(view1);
			
			final NetworkViewFrame frame2 = getNetworkViewFrame(view2);
			
			if (frame2 != null)
				reattachNetworkView(view2);
			
			final NetworkViewContainer vc1 = getNetworkViewContainer(view1);
			final NetworkViewContainer vc2 = getNetworkViewContainer(view2);
			
			cardLayout.removeLayoutComponent(vc1);
			viewContainers.remove(vc1.getName());
			cardLayout.removeLayoutComponent(vc2);
			viewContainers.remove(vc2.getName());
			
			// Now we can create the comparison panel
			cp = new NetworkViewComparisonPanel(orientation, vc1, vc2, currentView, serviceRegistrar);
			
			cp.getGridModeButton().addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					final Component currentCard = getCurrentCard();
					
					if (currentCard instanceof NetworkViewComparisonPanel) {
						endComparison((NetworkViewComparisonPanel) currentCard);
						networkViewGrid.requestFocusInWindow();
					}
				}
			});
			
			cp.getDetachComparedViewsButton().addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					final Component currentCard = getCurrentCard();
					
					if (currentCard instanceof NetworkViewComparisonPanel) {
						final NetworkViewComparisonPanel cp = (NetworkViewComparisonPanel) currentCard;
						final CyNetworkView view1 = cp.getContainer1().getNetworkView();
						final CyNetworkView view2 = cp.getContainer2().getNetworkView();
						
						// End comparison first
						endComparison(cp);
						
						// Then detach the views
						detachNetworkView(view1);
						detachNetworkView(view2);
					}
				}
			});
			
			cp.addPropertyChangeListener("currentNetworkView", new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					final CyNetworkView newCurrentView = (CyNetworkView) evt.getNewValue();
					setCurrentNetworkView(newCurrentView);
				}
			});
			
			getContentPane().add(cp, cp.getName());
			comparisonPanels.put(cp.getName(), cp);
		}
		
		if (cp != null) {
			setGridMode(false);
			showViewContainer(cp.getName());
		}
	}
	
	private void endComparison(final NetworkViewComparisonPanel cp) {
		if (cp != null) {
			final NetworkViewContainer vc1 = cp.getContainer1();
			final NetworkViewContainer vc2 = cp.getContainer2();
			
			cardLayout.removeLayoutComponent(cp);
			comparisonPanels.remove(cp.getName());
			cp.dispose(); // Don't forget to call this method!
			
			getContentPane().add(vc1, vc1.getName());
			viewContainers.put(vc2.getName(), vc2);
			getContentPane().add(vc2, vc2.getName());
			viewContainers.put(vc1.getName(), vc1);
			
			showGrid();
		}
	}
	
	private NetworkViewComparisonPanel getComparisonPanel(final CyNetworkView view) {
		for (NetworkViewComparisonPanel cp : comparisonPanels.values()) {
			if (cp.getContainer1().getNetworkView().equals(view)
					|| cp.getContainer2().getNetworkView().equals(view))
				return cp;
		}
		
		return null;
	}
	
	public void setGridMode(final boolean newValue) {
		final boolean changed = newValue != gridMode;
		final boolean oldValue = gridMode;
		gridMode = newValue;
		
		final JToggleButton btn = gridMode ? networkViewGrid.getGridModeButton() : networkViewGrid.getViewModeButton();
		networkViewGrid.getModeButtonGroup().setSelected(btn.getModel(), true);
		networkViewGrid.updateModeButtons();
		
		if (newValue)
			showGrid();
		
		if (changed)
			firePropertyChange("gridMode", oldValue, newValue);
	}
	
	protected boolean isGridMode() {
		return gridMode;
	}
	
	protected boolean isGridVisible() {
		return getCurrentCard() == networkViewGrid;
	}
	
	/**
	 * @return The current attached View container
	 */
	protected NetworkViewContainer getCurrentViewContainer() {
		final Component c = getCurrentCard();

		return c instanceof NetworkViewContainer ? (NetworkViewContainer) c : null;
	}
	
	private Component getCurrentCard() {
		Component current = null;
		
		for (Component comp : getContentPane().getComponents()) {
			if (comp.isVisible())
				current = comp;
		}
		
		return current;
	}
	
	private NetworkViewGrid createNetworkViewGrid() {
		final NetworkViewGrid nvg = new NetworkViewGrid(viewComparator, serviceRegistrar);
		
		nvg.getDetachSelectedViewsButton().addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final List<ThumbnailPanel> selectedItems = networkViewGrid.getSelectedItems();

				if (selectedItems != null) {
					// Get the current view first
					final CyNetworkView currentView = getCurrentNetworkView();

					// Detach the views
					for (ThumbnailPanel tp : selectedItems) {
						if (getNetworkViewContainer(tp.getNetworkView()) != null)
							detachNetworkView(tp.getNetworkView());
					}

					// Set the original current view by bringing its frame to front, if it is detached
					final NetworkViewFrame frame = getNetworkViewFrame(currentView);

					if (frame != null)
						frame.toFront();
				}
			}
		});
		
		nvg.getReattachAllViewsButton().addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final Collection<NetworkViewFrame> allFrames = new ArrayList<>(viewFrames.values());

				for (NetworkViewFrame f : allFrames)
					reattachNetworkView(f.getNetworkView());
			}
		});
		
		nvg.getDestroySelectedViewsButton().addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				final List<CyNetworkView> selectedViews = getSelectedNetworkViews();
				
				if (selectedViews != null && !selectedViews.isEmpty()) {
					final DialogTaskManager taskMgr = serviceRegistrar.getService(DialogTaskManager.class);
					final DestroyNetworkViewTaskFactory taskFactory =
							serviceRegistrar.getService(DestroyNetworkViewTaskFactory.class);
					taskMgr.execute(taskFactory.createTaskIterator(selectedViews));
				}
			}
		});
		
		return nvg;
	}
	
	@SuppressWarnings("unchecked")
	private void init() {
		setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, UIManager.getColor("Separator.foreground")));
		
		setLayout(new BorderLayout());
		add(getContentPane(), BorderLayout.CENTER);
		
		final JToggleButton selectedBtn = isGridMode() ? networkViewGrid.getGridModeButton()
				: networkViewGrid.getViewModeButton();
		networkViewGrid.getModeButtonGroup().setSelected(selectedBtn.getModel(), true);
		networkViewGrid.updateModeButtons();
		
		// Add Listeners
		networkViewGrid.addPropertyChangeListener("thumbnailPanels", (PropertyChangeEvent e) -> {
			networkViewGrid.updateToolBar();
			networkViewGrid.getReattachAllViewsButton().setEnabled(!viewFrames.isEmpty()); // TODO Should not be done here
			
			for (ThumbnailPanel tp : networkViewGrid.getItems()) {
				tp.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						if (e.getClickCount() == 2) {
							// Double-Click: set this one as current and show attached view or view frame
							final NetworkViewFrame frame = getNetworkViewFrame(tp.getNetworkView());
								
							if (frame != null) {
								showViewFrame(frame);
							} else {
								final NetworkViewContainer vc = showViewContainer(tp.getNetworkView());
								
								if (vc != null)
									vc.getContentPane().requestFocusInWindow();
								
								setGridMode(false);
							}
						}
					}
				});
				tp.addComponentListener(new ComponentAdapter() {
					@Override
					public void componentResized(ComponentEvent e) {
						updateThumbnail(tp.getNetworkView());
					};
				});
			}
		});
		
		networkViewGrid.addPropertyChangeListener("selectedNetworkViews", (PropertyChangeEvent e) -> {
			// Just fire the same event
			firePropertyChange("selectedNetworkViews", e.getOldValue(), e.getNewValue());
		});
		networkViewGrid.addPropertyChangeListener("selectedItems", (PropertyChangeEvent e) -> {
			networkViewGrid.updateToolBar();
			networkViewGrid.getReattachAllViewsButton().setEnabled(!viewFrames.isEmpty()); // TODO
		});
		networkViewGrid.addPropertyChangeListener("currentNetworkView", (PropertyChangeEvent e) -> {
			final CyNetworkView curView = (CyNetworkView) e.getNewValue();
			
			for (NetworkViewContainer vc : getAllNetworkViewContainers())
				vc.setCurrent(vc.getNetworkView().equals(curView));
		});
		
		Toolkit.getDefaultToolkit().addAWTEventListener(mousePressedAWTEventListener, MouseEvent.MOUSE_EVENT_MASK);
		
		// Update
		showGrid();
	}
	
	private JPanel getContentPane() {
		if (contentPane == null) {
			contentPane = new JPanel();
			contentPane.setLayout(cardLayout);
			// Add the first panel in the card layout
			contentPane.add(networkViewGrid, networkViewGrid.getName());
		}
		
		return contentPane;
	}

	private NetworkViewContainer getNetworkViewContainer(final CyNetworkView view) {
		return view != null ? viewContainers.get(createUniqueKey(view)) : null;
	}
	
	protected NetworkViewFrame getNetworkViewFrame(final CyNetworkView view) {
		return view != null ? viewFrames.get(createUniqueKey(view)) : null;
	}
	
	private void changeCurrentViewTitle(final NetworkViewContainer vc) {
		String text = vc.getViewTitleTextField().getText();
		
		if (text != null) {
			text = text.trim();
			
			// TODO Make sure it's unique
			if (!text.isEmpty()) {
				vc.getViewTitleLabel().setText(text);
				
				// TODO This will fire a ViewChangedEvent - Just let the NetworkViewManager ask this panel to update itself instead?
				final CyNetworkView view = vc.getNetworkView();
				view.setVisualProperty(BasicVisualLexicon.NETWORK_TITLE, text);
				
				updateThumbnailPanel(view, false);
			}
		}
		
		vc.getViewTitleTextField().setText(null);
		vc.getViewTitleTextField().setVisible(false);
		vc.getViewTitleLabel().setVisible(true);
		vc.getToolBar().updateUI();
	}
	
	private void cancelViewTitleChange(final NetworkViewContainer vc) {
		vc.getViewTitleTextField().setText(null);
		vc.getViewTitleTextField().setVisible(false);
		vc.getViewTitleLabel().setVisible(true);
	}
	
	private class MousePressedAWTEventListener implements AWTEventListener {
		
        @Override
        public void eventDispatched(AWTEvent event) {
            if (event.getID() == MouseEvent.MOUSE_PRESSED && event instanceof MouseEvent) {
				final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
				final Window window = keyboardFocusManager.getActiveWindow();
				
				if (!(window instanceof NetworkViewFrame || window instanceof CytoscapeDesktop))
					return;
				
				// Detect if a new view container received the mouse pressed event.
				// If so, it must request focus.
				MouseEvent me = (MouseEvent) event;
                NetworkViewContainer vc = null;
                Component target = null;
                
                // Find the view container to be verified
                if (window instanceof NetworkViewFrame) {
                	vc = ((NetworkViewFrame) window).getNetworkViewContainer();
                	target = ((NetworkViewFrame) window).getContainerRootPane().getContentPane();
                } else {
                	final Component currentCard = getCurrentCard();
                	
                	if (currentCard instanceof NetworkViewContainer) {
                		vc = (NetworkViewContainer) currentCard;
                		target = vc.getContentPane();
                	} else if (currentCard instanceof NetworkViewComparisonPanel) {
                		// Get the view component which is not in focus
                		final NetworkViewComparisonPanel cp = (NetworkViewComparisonPanel) currentCard;
                		final NetworkViewContainer currentContainer = cp.getCurrentContainer();
                		vc = currentContainer == cp.getContainer1() ? cp.getContainer2() : cp.getContainer1();
                		target = vc.getContentPane();
                	}
                }
                
                if (target != null) {
                	me = SwingUtilities.convertMouseEvent(me.getComponent(), me, target);
                	
                	// Received the mouse event? So it should get focus now.
                	if (target.getBounds().contains(me.getPoint()))
                		target.requestFocusInWindow();
                }
            }
        }
    }
}
