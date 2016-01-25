package org.cytoscape.internal.view;

import static javax.swing.GroupLayout.DEFAULT_SIZE;
import static javax.swing.GroupLayout.PREFERRED_SIZE;
import static javax.swing.GroupLayout.Alignment.CENTER;
import static javax.swing.GroupLayout.Alignment.LEADING;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.util.swing.LookAndFeelUtil;

@SuppressWarnings("serial")
public class RootNetworkPanel extends AbstractNetworkPanel<CyRootNetwork> {

	protected static final String PARENT_NETWORK_COLUMN = "__parentNetwork.SUID";
	
	private ExpandCollapseButton expandCollapseBtn;
	private JLabel networkCountLabel;
	private JPanel headerPanel;
	private JPanel subNetListPanel;
	
	private Map<CySubNetwork, SubNetworkPanel> items;
	private boolean showNodeEdgeCount;
	
	public RootNetworkPanel(final RootNetworkPanelModel model, final boolean showNodeEdgeCount,
			final CyServiceRegistrar serviceRegistrar) {
		super(model, serviceRegistrar);
		this.showNodeEdgeCount = showNodeEdgeCount;
	}
	
	public SubNetworkPanel addItem(final CySubNetwork network) {
		if (!getItems().containsKey(network)) {
			final SubNetworkPanelModel model = new SubNetworkPanelModel(network, serviceRegistrar);
			
			final SubNetworkPanel subNetPanel = new SubNetworkPanel(model, serviceRegistrar);
			subNetPanel.setAlignmentX(LEFT_ALIGNMENT);
			
			getSubNetListPanel().add(subNetPanel);
			getItems().put(network, subNetPanel);
			
			updateRootPanel();
			updateCountInfo();
		}
		
		return getItems().get(network);
	}
	
	public SubNetworkPanel removeItem(final CySubNetwork network) {
		final SubNetworkPanel subNetPanel = getItems().remove(network);
		
		if (subNetPanel != null) {
			getSubNetListPanel().remove(subNetPanel);
			updateRootPanel();
			updateCountInfo();
		}
		
		return subNetPanel;
	}
	
	public void removeAllItems() {
		getItems().clear();
		getSubNetListPanel().removeAll();
	}
	
	public SubNetworkPanel getItem(final CySubNetwork network) {
		return getItems().get(network);
	}
	
	public List<SubNetworkPanel> getAllItems() {
		return new ArrayList<>(getItems().values());
	}
	
	public boolean isEmpty() {
		return getItems().isEmpty();
	}
	
	public void expand() {
		if (!isExpanded()) {
			getSubNetListPanel().setVisible(true);
			firePropertyChange("expanded", false, true);
		}
	}
	
	public void collapse() {
		if (isExpanded()) {
			getSubNetListPanel().setVisible(false);
			firePropertyChange("expanded", true, false);
		}
	}
	
	public boolean isExpanded() {
		return getSubNetListPanel().isVisible();
	}
	
	public void setShowNodeEdgeCount(final boolean show) {
		showNodeEdgeCount = show;
		updateCountInfo();
	}
	
	@Override
	public void update() {
		updateRootPanel();
		
		for (SubNetworkPanel snp : getItems().values()) {
			int depth = getDepth(snp.getModel().getNetwork());
			snp.setDepth(depth);
			snp.update();
		}
		
		updateCountInfo();
	}
	
	protected void updateRootPanel() {
		super.update();
		final int netCount = getItems().values().size();
		
		getNetworkCountLabel().setText("" + netCount);
		getNetworkCountLabel().setToolTipText(
				"This collection has " + netCount + " network" + (netCount == 1 ? "" : "s"));
	}
	
	protected void updateItemsDepth() {
		for (SubNetworkPanel snp : getItems().values()) {
			int depth = getDepth(snp.getModel().getNetwork());
			System.out.println(snp.getModel().getNetwork() +  " >> " + depth);
			snp.setDepth(depth);
		}
	}
	
	protected void updateCountInfo() {
		int nodeLabelWidth = 0;
		int edgeLabelWidth = 0;
		
		for (SubNetworkPanel snp : getItems().values()) {
			snp.getNodeCountLabel().setVisible(showNodeEdgeCount);
			snp.getEdgeCountLabel().setVisible(showNodeEdgeCount);
			
			if (showNodeEdgeCount) {
				// Update node/edge count label text
				snp.updateCountLabels();
				// Get max label width
				nodeLabelWidth = Math.max(nodeLabelWidth, snp.getNodeCountLabel().getPreferredSize().width);
				edgeLabelWidth = Math.max(edgeLabelWidth, snp.getEdgeCountLabel().getPreferredSize().width);
			}
		}
		
		if (!showNodeEdgeCount)
			return;
		
		// Apply max width values to all labels so they align properly
		for (SubNetworkPanel snp : getItems().values()) {
			final Dimension nd = new Dimension(nodeLabelWidth, snp.getNodeCountLabel().getPreferredSize().height);
			final Dimension ed = new Dimension(edgeLabelWidth, snp.getEdgeCountLabel().getPreferredSize().height);
			snp.getNodeCountLabel().setPreferredSize(nd);
			snp.getEdgeCountLabel().setPreferredSize(ed);
		}
	}
	
	@Override
	protected void updateSelection() {
		final Color c = UIManager.getColor(isSelected() ? "Table.selectionBackground" : "Table.background");
		getHeaderPanel().setBackground(c);
	}
	
	@Override
	protected void init() {
		setBackground(UIManager.getColor("Table.background"));
		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));
		
		final GroupLayout layout = new GroupLayout(this);
		setLayout(layout);
		layout.setAutoCreateContainerGaps(false);
		layout.setAutoCreateGaps(false);
		
		layout.setHorizontalGroup(layout.createParallelGroup(LEADING, true)
				.addComponent(getHeaderPanel(), DEFAULT_SIZE, DEFAULT_SIZE, Short.MAX_VALUE)
				.addComponent(getSubNetListPanel(), DEFAULT_SIZE, DEFAULT_SIZE, Short.MAX_VALUE)
		);
		layout.setVerticalGroup(layout.createSequentialGroup()
				.addComponent(getHeaderPanel(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
				.addComponent(getSubNetListPanel(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
		);
	}
	
	private ExpandCollapseButton getExpandCollapseBtn() {
		if (expandCollapseBtn == null) {
			expandCollapseBtn = new ExpandCollapseButton(isExpanded(), new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent ae) {
					if (isExpanded())
						collapse();
					else
						expand();
				}
			}, serviceRegistrar);
		}
		
		return expandCollapseBtn;
	}
	
	protected JLabel getNetworkCountLabel() {
		if (networkCountLabel == null) {
			networkCountLabel = new JLabel();
			networkCountLabel.setFont(networkCountLabel.getFont().deriveFont(LookAndFeelUtil.getSmallFontSize()));
			networkCountLabel.setHorizontalAlignment(JLabel.RIGHT);
			networkCountLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
		}
		
		return networkCountLabel;
	}
	
	protected JPanel getHeaderPanel() {
		if (headerPanel == null) {
			headerPanel = new JPanel();
			headerPanel.setBackground(UIManager.getColor("Table.background"));
			
			final GroupLayout layout = new GroupLayout(headerPanel);
			headerPanel.setLayout(layout);
			layout.setAutoCreateContainerGaps(false);
			layout.setAutoCreateGaps(false);
			
			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addComponent(getExpandCollapseBtn(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getNameLabel(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addGap(0, 10, Short.MAX_VALUE)
					.addComponent(getNetworkCountLabel(), DEFAULT_SIZE, DEFAULT_SIZE, Short.MAX_VALUE)
					.addContainerGap()
			);
			layout.setVerticalGroup(layout.createParallelGroup(CENTER, true)
					.addComponent(getExpandCollapseBtn(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getNameLabel(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
					.addComponent(getNetworkCountLabel(), PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
			);
		}
		
		return headerPanel;
	}
	
	private JPanel getSubNetListPanel() {
		if (subNetListPanel == null) {
			subNetListPanel = new JPanel();
			subNetListPanel.setBackground(UIManager.getColor("Table.background"));
			subNetListPanel.setBorder(
					BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("TableHeader.background")));
			subNetListPanel.setVisible(false);
			subNetListPanel.setLayout(new BoxLayout(subNetListPanel, BoxLayout.Y_AXIS));
			
			subNetListPanel.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentShown(final ComponentEvent ce) {
					if (!getExpandCollapseBtn().isSelected())
						getExpandCollapseBtn().setSelected(true);
				}
				@Override
				public void componentHidden(final ComponentEvent ce) {
					if (getExpandCollapseBtn().isSelected())
						getExpandCollapseBtn().setSelected(false);
				}
			});
		}
		
		return subNetListPanel;
	}
	
	private Map<CySubNetwork, SubNetworkPanel> getItems() {
		return items != null ? items : (items = new LinkedHashMap<>());
	}
	
	private int getDepth(final CySubNetwork net) {
		int depth = -1;
		CySubNetwork parent = net;
		
		do {
			parent = getParent(parent);
			depth++;
		} while (parent != null);
		
		return depth;
	}
	
	private CySubNetwork getParent(final CySubNetwork net) {
		final CyTable hiddenTable = net.getTable(CyNetwork.class, CyNetwork.HIDDEN_ATTRS);
		final Long suid = hiddenTable.getRow(net.getSUID()).get(PARENT_NETWORK_COLUMN, Long.class);
		
		if (suid != null) {
			final CyNetwork parent = serviceRegistrar.getService(CyNetworkManager.class).getNetwork(suid);
			
			if (parent instanceof CySubNetwork)
				return (CySubNetwork) parent;
		}
		
		return null;
	}
}
