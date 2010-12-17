/*
 Part of the ReplicatorG project - http://www.replicat.org
 Copyright (c) 2008 Zach Smith

 Forked from Arduino: http://www.arduino.cc

 Based on Processing http://www.processing.org
 Copyright (c) 2004-05 Ben Fry and Casey Reas
 Copyright (c) 2001-04 Massachusetts Institute of Technology

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 
 $Id: MainWindow.java 370 2008-01-19 16:37:19Z mellis $
 */

package replicatorg.app.ui;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;
import replicatorg.app.Base;
import replicatorg.app.MachineController;
import replicatorg.app.ui.controlpanel.ExtruderPanel;
import replicatorg.app.ui.controlpanel.Jog3AxisPanel;
import replicatorg.drivers.Driver;
import replicatorg.drivers.RetryException;
import replicatorg.machine.MachineListener;
import replicatorg.machine.MachineProgressEvent;
import replicatorg.machine.MachineState;
import replicatorg.machine.MachineStateChangeEvent;
import replicatorg.machine.MachineToolStatusEvent;
import replicatorg.machine.model.Axis;
import replicatorg.machine.model.Endstops;
import replicatorg.machine.model.ToolModel;

public class ControlPanelWindow extends JFrame implements
		ChangeListener, WindowListener,
		MachineListener {
	// Autogenerated by serialver
	static final long serialVersionUID = -3494348039028986935L;

	protected JPanel mainPanel;

	protected Jog3AxisPanel jogPanel;

	protected JTabbedPane toolsPane;

	protected MachineController machine;

	protected Driver driver;

	protected UpdateThread updateThread;

	protected PollThread pollThread;

	private static ControlPanelWindow instance = null;

	public static synchronized ControlPanelWindow getControlPanel(MachineController m) {
		if (instance == null) {
			instance = new ControlPanelWindow(m);
		} else {
			if (instance.machine != m) {
				instance.dispose();
				instance = new ControlPanelWindow(m);
			}
		}
		return instance;
	}
	
	private ControlPanelWindow(MachineController m) {
		super("Control Panel");

		// save our machine!
		machine = m;
		driver = machine.getDriver();
		driver.invalidatePosition(); // Always force a query when we open the panel

		// Listen to it-- stop and close if we're in build mode.
		machine.addMachineStateListener(this);
		
		// default behavior
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		// no menu bar.
		setJMenuBar(createMenuBar());

		// create all our GUI interfaces
		mainPanel = new JPanel();
		mainPanel.setLayout(new MigLayout());
		mainPanel.add(createJogPanel(),"split 2,flowy");
		mainPanel.add(createActivationPanel(),"flowy,growx");
		mainPanel.add(createToolsPanel(),"spany,grow");
		add(mainPanel);

		// add our listener hooks.
		addWindowListener(this);
		// addWindowFocusListener(this);
		// addWindowStateListener(this);

		// start our various threads.
		updateThread = new UpdateThread(this);
		updateThread.start();
		pollThread = new PollThread(driver);
		pollThread.start();
	}

	private JMenuItem makeHomeItem(String name,final EnumSet<Axis> set,final boolean positive) {
		JMenuItem item = new JMenuItem(name);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					driver.homeAxes(set,positive,0);
				} catch (RetryException e1) {
					Base.logger.severe("Can't home axis; machine busy");
				}
			}
		});
		return item;
	}

	protected JMenuBar createMenuBar() {
		JMenuBar bar = new JMenuBar();
		JMenu homeMenu = new JMenu("Homing");
		bar.add(homeMenu);
		
		//adding the appropriate homing options for your endstop configuration
		for (Axis axis : Axis.values())
		{
			Endstops endstops = driver.getMachine().getEndstops(axis);
			if (endstops != null)
			{
				if (endstops.hasMin == true)
					homeMenu.add(makeHomeItem("Home "+axis.name()+"-",EnumSet.of(axis),false));
				if (endstops.hasMax == true)
					homeMenu.add(makeHomeItem("Home "+axis.name()+"+",EnumSet.of(axis),true));
			}
		}
		
		// These homing options can be dangerous on some machines, especially ones that require sequential
		// homes.  We'll leave them out until we can improve the safety of these operations.
		/*
		homeMenu.add(new JSeparator());
		homeMenu.add(makeHomeItem("Home XY+",EnumSet.of(Axis.X,Axis.Y),true));
		homeMenu.add(makeHomeItem("Home XY-",EnumSet.of(Axis.X,Axis.Y),false));
		homeMenu.add(makeHomeItem("Home all+",EnumSet.allOf(Axis.class),true));
		homeMenu.add(makeHomeItem("Home all-",EnumSet.allOf(Axis.class),false));
		*/
		return bar;
	}

	protected JTextField createDisplayField() {
		int textBoxWidth = 160;

		JTextField tf = new JTextField();
		tf.setMaximumSize(new Dimension(textBoxWidth, 25));
		tf.setMinimumSize(new Dimension(textBoxWidth, 25));
		tf.setPreferredSize(new Dimension(textBoxWidth, 25));
		tf.setEnabled(false);
		return tf;
	}

	protected JComponent createJogPanel() {
		jogPanel = new Jog3AxisPanel(machine);
		return jogPanel;
	}

	/**
	 * The activation panel contains functions related to pausing, starting, and
	 * powering the steppers up or down.
	 */
	protected JComponent createActivationPanel() {
		JPanel activationPanel = new JPanel();
		activationPanel.setBorder(BorderFactory
				.createTitledBorder("Stepper Motor Controls"));
		activationPanel.setLayout(new BoxLayout(activationPanel,
				BoxLayout.LINE_AXIS));

		// / Enable/disable steppers.
		JButton enableButton = new JButton("Enable");
		enableButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					driver.enableDrives();
				} catch (RetryException e1) {
					Base.logger.severe("Can't change stepper state; machine busy");
				}
			}
		});
		activationPanel.add(enableButton);

		JButton disableButton = new JButton("Disable");
		disableButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					driver.disableDrives();
				} catch (RetryException e1) {
					Base.logger.severe("Can't change stepper state; machine busy");
				}
			}
		});
		activationPanel.add(disableButton);

		activationPanel.add(Box.createHorizontalGlue());

		return activationPanel;
	}

	Vector<ExtruderPanel> extruderPanels = new Vector<ExtruderPanel>();
	
	protected JComponent createToolsPanel() {
		toolsPane = new JTabbedPane();

		for (Enumeration<ToolModel> e = machine.getModel().getTools().elements(); e
				.hasMoreElements();) {
			ToolModel t = e.nextElement();
			if (t == null) continue;
			if (t.getType().equals("extruder")) {
				Base.logger.fine("Creating panel for " + t.getName());
				ExtruderPanel extruderPanel = new ExtruderPanel(machine,t);
				toolsPane.addTab(t.getName(),extruderPanel);
				extruderPanels.add(extruderPanel);
				if (machine.getModel().currentTool() == t) {
					toolsPane.setSelectedComponent(extruderPanel);
				}
			} else {
				Base.logger.warning("Unsupported tool for control panel.");
			}
		} 
		toolsPane.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent ce) {
				final JTabbedPane tp = (JTabbedPane)ce.getSource();
				final ExtruderPanel ep = (ExtruderPanel)tp.getSelectedComponent();
				machine.getModel().selectTool(ep.getTool().getIndex());
			}
		});
		return toolsPane;
	}
	
	public void updateStatus() {
		jogPanel.updateStatus();
		for (ExtruderPanel e : extruderPanels) {
			e.updateStatus();
		}
	}
	
	public void windowClosing(WindowEvent e) {
		updateThread.interrupt();
		pollThread.interrupt();
	}

	public void windowClosed(WindowEvent e) {
		synchronized(getClass()) {
			machine.removeMachineStateListener(this);
			if (instance == this) {
				instance = null;
			}
		}
	}

	public void windowOpened(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	class PollThread extends Thread {
		Driver driver;

		public PollThread(Driver d) {
			super("Control Panel Poll Thread");

			driver = d;
		}

		public void run() {
			// we'll break on interrupts
			try {
				while (true) {
					// driver.readTemperature();
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
			}
		}
	}

	class UpdateThread extends Thread {
		ControlPanelWindow window;

		public UpdateThread(ControlPanelWindow w) {
			super("Control Panel Update Thread");

			window = w;
		}

		public void run() {
			// we'll break on interrupts
			try {
				while (true) {
					try {
						window.updateStatus();
					} catch (AssertionError ae) {
						// probaby disconnected unexpectedly; close window.
						window.dispose();
						break;
					}
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
			}
		}
	}

	public void machineProgress(MachineProgressEvent event) {
	}

	public void machineStateChanged(MachineStateChangeEvent evt) {
		MachineState state = evt.getState();
		if (state.isBuilding() || !state.isConnected() || 
				state.getState() == MachineState.State.RESET) {
			if (updateThread != null) { updateThread.interrupt(); }
			if (pollThread != null) { pollThread.interrupt(); }
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					dispose();
				}
			});
		}
	}

	public void toolStatusChanged(MachineToolStatusEvent event) {
	}

	public void stateChanged(ChangeEvent e) {
	}
}
