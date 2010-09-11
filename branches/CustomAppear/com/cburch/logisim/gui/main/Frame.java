/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package com.cburch.logisim.gui.main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.appear.AppearanceEditor;
import com.cburch.logisim.gui.generic.AttributeTable;
import com.cburch.logisim.gui.generic.AttributeTableListener;
import com.cburch.logisim.gui.generic.CanvasPane;
import com.cburch.logisim.gui.generic.ZoomControl;
import com.cburch.logisim.gui.generic.ZoomModel;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.HorizontalSplitPane;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringUtil;
import com.cburch.logisim.util.VerticalSplitPane;

public class Frame extends JFrame
		implements LocaleListener {
	class MyProjectListener
			implements ProjectListener, LibraryListener, CircuitListener,
				AttributeListener, ChangeListener {
		public void projectChanged(ProjectEvent event) {
			int action = event.getAction();

			if (action == ProjectEvent.ACTION_COMPLETE
					|| action == ProjectEvent.UNDO_COMPLETE
					|| action == ProjectEvent.ACTION_SET_FILE) {
				enableSave();
			}

			if (action == ProjectEvent.ACTION_SET_FILE) {
				computeTitle();
				proj.setTool(proj.getOptions().getToolbarData().getFirstTool());
				
				AttributeSet attrs = proj.getOptions().getAttributeSet();
				attrs.addAttributeListener(this);
				placeToolbar(attrs.getValue(Options.ATTR_TOOLBAR_LOC));
			} else if (action == ProjectEvent.ACTION_SET_CURRENT) {
				mainPanel.setView(MainPanel.LAYOUT);
				appearance.setCircuit(proj, proj.getCircuitState());
				viewAttributes(proj.getTool());
				computeTitle();
			} else if (action == ProjectEvent.ACTION_SET_TOOL) {
				if (attrTable == null) return; // for startup
				viewAttributes((Tool) event.getOldData(), (Tool) event.getData());
			}
		}

		public void libraryChanged(LibraryEvent e) {
			if (e.getAction() == LibraryEvent.SET_NAME) {
				computeTitle();
			}
		}

		public void circuitChanged(CircuitEvent event) {
			if (event.getAction() == CircuitEvent.ACTION_SET_NAME) {
				computeTitle();
			}
		}

		private void enableSave() {
			Project proj = getProject();
			boolean ok = proj.isFileDirty();
			getRootPane().putClientProperty("windowModified", Boolean.valueOf(ok));
		}

		public void attributeListChanged(AttributeEvent e) { }

		public void attributeValueChanged(AttributeEvent e) {
			if (e.getAttribute() == Options.ATTR_TOOLBAR_LOC) {
				placeToolbar(e.getValue());
			}
		}

		public void stateChanged(ChangeEvent e) {
			if (e.getSource() == mainPanel) {
				if (MainPanel.APPEARANCE.equals(mainPanel.getView())) {
					toolbar.setToolbarModel(appearance.getToolbarModel());
					attrTable.setAttributeSet(appearance.getAttributeSet(),
							appearance.getAttributeManager(attrTable));
					zoom.setZoomModel(appearance.getZoomModel());
				} else {
					toolbar.setToolbarModel(layoutToolbarModel);
					zoom.setZoomModel(projectZoomModel);
				}
			}
		}
	}

	class MyWindowListener extends WindowAdapter {
		@Override
		public void windowClosing(WindowEvent e) {
			if (confirmClose(Strings.get("confirmCloseTitle"))) {
				layoutCanvas.closeCanvas();
				Frame.this.dispose();
			}
		}

		@Override
		public void windowOpened(WindowEvent e) {
			layoutCanvas.computeSize(true);
		}
	}

	private static class ComponentAttributeListener
			implements AttributeTableListener {
		Project proj;
		Circuit circ;
		Component comp;

		ComponentAttributeListener(Project proj, Circuit circ,
				Component comp) {
			this.proj = proj;
			this.circ = circ;
			this.comp = comp;
		}

		public void valueChangeRequested(AttributeTable table,
				AttributeSet attrs, Attribute<?> attr, Object value) {
			if (!proj.getLogisimFile().contains(circ)) {
				JOptionPane.showMessageDialog(proj.getFrame(),
					Strings.get("cannotModifyCircuitError"));
			} else {
				SetAttributeAction act = new SetAttributeAction(circ,
						Strings.getter("changeAttributeAction"));
				act.set(comp, attr, value);
				proj.doAction(act);
			}
		}
	}
	
	private Project         proj;

	private LogisimMenuBar  menubar;
	private MenuListener    menuListener;
	private Toolbar         toolbar;
	private MainPanel       mainPanel;
	
	private LayoutToolbarModel layoutToolbarModel;
	private Canvas          layoutCanvas;
	private JPanel          canvasPanel;
	private AppearanceEditor appearance;
	private Toolbar         projectToolbar;
	private ProjectToolbarModel projectToolbarModel;
	private Explorer        explorer;
	private AttributeTable  attrTable;
	private ZoomModel       projectZoomModel;
	private ZoomControl     zoom;
	private MyProjectListener myProjectListener = new MyProjectListener();

	public Frame(Project proj) {
		this.proj = proj;
		proj.setFrame(this);

		setBackground(Color.white);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new MyWindowListener());

		proj.addProjectListener(myProjectListener);
		proj.addLibraryListener(myProjectListener);
		proj.addCircuitListener(myProjectListener);
		proj.getOptions().getAttributeSet().addAttributeListener(myProjectListener);
		computeTitle();
		
		projectToolbarModel = new ProjectToolbarModel(this);
		projectToolbar = new Toolbar(projectToolbarModel);

		// set up menu bar
		menubar = new LogisimMenuBar(this, proj);
		setJMenuBar(menubar);
		menuListener = new MenuListener(this, menubar, projectToolbarModel);

		// set up the content-bearing components
		layoutToolbarModel = new LayoutToolbarModel(this, proj);
		toolbar = new Toolbar(layoutToolbarModel);
		explorer = new Explorer(proj);
		explorer.setListener(new ExplorerManip(proj, explorer));
		canvasPanel = new JPanel(new BorderLayout());
		layoutCanvas = new Canvas(proj);
		appearance = new AppearanceEditor();
		appearance.setCircuit(proj, proj.getCircuitState());
		projectZoomModel = new ProjectZoomModel(proj);
		layoutCanvas.getGridPainter().setZoomModel(projectZoomModel);
		zoom = new ZoomControl(projectZoomModel);
		attrTable = new AttributeTable(this);

		// set up the central area
		CanvasPane canvasPane = new CanvasPane(layoutCanvas);
		canvasPane.setZoomModel(projectZoomModel);
		mainPanel = new MainPanel();
		mainPanel.addChangeListener(myProjectListener);
		mainPanel.addView(MainPanel.LAYOUT, canvasPane);
		mainPanel.addView(MainPanel.APPEARANCE, appearance.getCanvasPane());
		mainPanel.setView(MainPanel.LAYOUT);
		canvasPanel.add(mainPanel, BorderLayout.CENTER);

		// set up the contents, split down the middle, with the canvas
		// on the right and a split pane on the left containing the
		// explorer and attribute values.
		JPanel explPanel = new JPanel(new BorderLayout());
		explPanel.add(projectToolbar, BorderLayout.NORTH);
		explPanel.add(new JScrollPane(explorer), BorderLayout.CENTER);
		JPanel attrPanel = new JPanel(new BorderLayout());
		attrPanel.add(new JScrollPane(attrTable), BorderLayout.CENTER);
		attrPanel.add(zoom, BorderLayout.SOUTH);

		VerticalSplitPane contents = new VerticalSplitPane(
			new HorizontalSplitPane(explPanel, attrPanel, 0.5),
			canvasPanel, 0.25);

		placeToolbar(proj.getOptions().getAttributeSet().getValue(Options.ATTR_TOOLBAR_LOC));
		getContentPane().add(contents, BorderLayout.CENTER);

		computeTitle();

		this.setSize(640, 480);
		menuListener.register(mainPanel);
		KeyboardToolSelection.register(toolbar);

		if (proj.getTool() == null) {
			proj.setTool(proj.getOptions().getToolbarData().getFirstTool());
		}
		LocaleManager.addLocaleListener(this);
	}
	
	private void placeToolbar(Object loc) {
		
		Container contents = getContentPane();
		contents.remove(toolbar);
		canvasPanel.remove(toolbar);
		if (loc == Options.TOOLBAR_HIDDEN) {
			; // don't place value anywhere
		} else if (loc == Options.TOOLBAR_DOWN_MIDDLE) {
			toolbar.setOrientation(Toolbar.VERTICAL);
			canvasPanel.add(toolbar, BorderLayout.WEST);
		} else { // it is a BorderLayout constant
			Object value;
			if (loc == Direction.EAST)       value = BorderLayout.EAST;
			else if (loc == Direction.SOUTH) value = BorderLayout.SOUTH;
			else if (loc == Direction.WEST)  value = BorderLayout.WEST;
			else                            value = BorderLayout.NORTH;

			contents.add(toolbar, value);
			boolean vertical = value == BorderLayout.WEST || value == BorderLayout.EAST;
			toolbar.setOrientation(vertical ? Toolbar.VERTICAL : Toolbar.HORIZONTAL);
		}
		contents.validate();
	}
	
	public Project getProject() {
		return proj;
	}

	public void viewComponentAttributes(Circuit circ, Component comp) {
		if (comp == null) {
			attrTable.setAttributeSet(null, null);
			layoutCanvas.setHaloedComponent(null, null);
		} else {
			attrTable.setAttributeSet(comp.getAttributeSet(),
				new ComponentAttributeListener(proj, circ, comp));
			layoutCanvas.setHaloedComponent(circ, comp);
		}
		layoutToolbarModel.setHaloedTool(null);
		explorer.setHaloedTool(null);
	}

	boolean getShowHalo() {
		return layoutCanvas.getShowHalo();
	}

	public AttributeTable getAttributeTable() {
		return attrTable;
	}
	
	MainPanel getMainPanel() {
		return mainPanel;
	}

	public Canvas getCanvas() {
		return layoutCanvas;
	}

	private void computeTitle() {
		String s;
		Circuit circuit = proj.getCurrentCircuit();
		String name = proj.getLogisimFile().getName();
		if (circuit != null) {
			s = StringUtil.format(Strings.get("titleCircFileKnown"),
				circuit.getName(), name);
		} else {
			s = StringUtil.format(Strings.get("titleFileKnown"), name);
		}
		this.setTitle(s);
	}
	
	void viewAttributes(Tool newTool) {
		viewAttributes(null, newTool);
	}

	private void viewAttributes(Tool oldTool, Tool newTool) {
		if (newTool == null) return;
		AttributeSet newAttrs = newTool.getAttributeSet();
		if (newAttrs == null) {
			AttributeSet oldAttrs = oldTool == null ? null : oldTool.getAttributeSet();
			AttributeTableListener listen = attrTable.getAttributeTableListener();
			if (attrTable.getAttributeSet() != oldAttrs
					&& !(listen instanceof CircuitAttributeListener)) {
				return;
			}
		}
		if (newAttrs == null) {
			Circuit circ = proj.getCurrentCircuit();
			if (circ != null) {
				attrTable.setAttributeSet(circ.getStaticAttributes(),
						new CircuitAttributeListener(proj, circ));
			}
		} else {
			attrTable.setAttributeSet(newAttrs, newTool.getAttributeTableListener(proj));
		}
		if (newAttrs != null && newAttrs.getAttributes().size() > 0) {
			layoutToolbarModel.setHaloedTool(newTool);
			explorer.setHaloedTool(newTool);
		} else {
			layoutToolbarModel.setHaloedTool(null);
			explorer.setHaloedTool(null);
		}
		layoutCanvas.setHaloedComponent(null, null);
	}

	public void localeChanged() {
		computeTitle();
	}
	
	public boolean confirmClose() {
		return confirmClose(Strings.get("confirmCloseTitle"));
	}
	
	// returns true if user is OK with proceeding
	public boolean confirmClose(String title) {
		String message = StringUtil.format(Strings.get("confirmDiscardMessage"),
				proj.getLogisimFile().getName());
		
		if (!proj.isFileDirty()) return true;
		toFront();
		String[] options = { Strings.get("saveOption"), Strings.get("discardOption"), Strings.get("cancelOption") };
		int result = JOptionPane.showOptionDialog(this,
				message, title, 0, JOptionPane.QUESTION_MESSAGE, null,
				options, options[0]);
		boolean ret;
		if (result == 0) {
			ret = ProjectActions.doSave(proj);
		} else if (result == 1) {
			ret = true;
		} else {
			ret = false;
		}
		if (ret) {
			dispose();
		}
		return ret;
	}
}
