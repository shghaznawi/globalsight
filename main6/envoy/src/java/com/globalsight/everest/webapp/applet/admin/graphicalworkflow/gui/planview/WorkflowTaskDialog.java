/**
 *  Copyright 2009 Welocalize, Inc. 
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  
 *  You may obtain a copy of the License at 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */
package com.globalsight.everest.webapp.applet.admin.graphicalworkflow.gui.planview;


import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Choice;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import javax.swing.JTextArea;

import CoffeeTable.Grid.GridAdapter;
import CoffeeTable.Grid.GridAttributes;
import CoffeeTable.Grid.GridData;
import CoffeeTable.Grid.GridEvent;
import CoffeeTable.Grid.GridPanel;

import com.globalsight.everest.costing.Rate;
import com.globalsight.everest.webapp.applet.common.AbstractEnvoyDialog;
import com.globalsight.everest.webapp.applet.common.AppletHelper;
import com.globalsight.everest.webapp.applet.common.EnvoyAppletConstants;
import com.globalsight.everest.webapp.applet.common.EnvoyConstraints;
import com.globalsight.everest.webapp.applet.common.EnvoyFonts;
import com.globalsight.everest.webapp.applet.common.EnvoyLabel;
import com.globalsight.everest.webapp.applet.common.EnvoyLineLayout;
import com.globalsight.everest.webapp.javabean.TaskInfoBean;
import com.globalsight.everest.workflow.Activity;
import com.globalsight.everest.workflow.SystemAction;
import com.globalsight.everest.workflow.WorkflowConstants;
import com.globalsight.everest.workflow.WorkflowTask;
import com.globalsight.everest.workflow.WorkflowTaskInstance;
import com.globalsight.util.date.DateHelper;


/**
 * The dialog is used for creating/modifying a Workflow Task
 *
 */
public class WorkflowTaskDialog
    extends AbstractEnvoyDialog
    implements EnvoyAppletConstants
{
    //
    // PRIVATE MEMBER VARIABLES
    //
    private TextField m_daysToAccept;
    private TextField m_hoursToAccept;
    private TextField m_minutesToAccept;
    private TextField m_daysToComplete;
    private TextField m_hoursToComplete;
    private TextField m_minutesToComplete;
    private TextField m_daysOverDueToPM;
    private TextField m_hoursOverDueToPM;
    private TextField m_minutesOverDueToPM;
    private TextField m_daysOverDueToUser;
    private TextField m_hoursOverDueToUser;
    private TextField m_minutesOverDueToUser;

    private Choice m_activityTypeChoice;
    private Choice m_systemActionChoice;
    private String m_defaultChoose;
    private Choice m_expenseRateChoice;
    private Choice m_revenueRateChoice;
    private EnvoyLabel m_hourlyRateLabel;
    private TextField m_hourlyRateField;
    private Vector m_activities;
    private Vector m_expenseRatesForSelectedActivity;
    private Vector m_revenueRatesForSelectedActivity;
    private TaskInfoBean m_taskInfoBean;
    private long m_selectedExpenseRateId = -1;      // the rate id of the MODIFIED rate
    private long m_selectedRevenueRateId = -1;      // the rate id of the MODIFIED rate
    private String m_initialExpenseRateName;
    private String m_initialRevenueRateName;
    private String[] m_roles;
    private boolean m_initialRoleType;
    private int m_initialRateCriteria;
    private JTextArea noteText;

    private String m_expenseRateDefaultChoice;
    private String m_participantDefaultChoice;
    private String m_revenueRateDefaultChoice;

    private int m_rateSelectionCriteria = WorkflowConstants.USE_ONLY_SELECTED_RATE;
    private Choice m_participantChoice;
    private Checkbox[] m_rateSelectionCheckbox;
    private Hashtable m_allRates;  //key into index is the activity chosen.

    private boolean m_isCalendarInstalled;
    private String m_selectUserOption;
    private String[] m_labels;
    private String[] m_messages;
    private Vector m_systemActions;

    private Vector m_values = new Vector();
    private static GVPane m_parent;
    private boolean m_isModifyMode = false;

    private int m_width;
    private int m_height;
    private Panel m_panel;
    private GridPanel m_grid;
    private String[] m_header;

    private boolean m_costingEnabled;
    private boolean m_revenueEnabled;
    private boolean m_isWorkflowTaskInstance;
    
    //
    // PUBLIC CONSTRUCTOR
    //
    /**
     * Create a new LocProfileTaskDialog.
     * <p>
     * @param p_parent - The parent frame component.
     * @param p_title - The title of the dialog.
     * @param p_hashtable - Contains the labels.
     */
    public WorkflowTaskDialog(GVPane p_parent,
                              String p_title,
                              Hashtable p_hashtable)
    { 
        super(p_parent.getParentFrame(), p_title, p_hashtable);
        updateButtonStatus(isDirty());
    }

    //
    // PUBLIC METHODS
    //
    /**
     * Get the panel that should be displayed in this dialog.
     * @return The editor panel.
     */
    public Panel getEditorPanel()
    {
        m_labels = (String[])getValue(LABELS);
        m_messages = (String[])getValue(MESSAGE);
        m_defaultChoose = m_labels[8];  //choose...
        m_activities = (Vector)getValue(ACTIVITIES);
        m_costingEnabled = 
            ((Boolean)getValue(COSTING_ENABLED)).booleanValue();
        m_revenueEnabled = 
            ((Boolean)getValue(REVENUE_ENABLED)).booleanValue();
        m_systemActions = (Vector)getValue(SYSTEM_ACTION);
        WorkflowTask workflowtask = (WorkflowTask)getValue("wft");
        
        m_taskInfoBean = (TaskInfoBean)getValue("taskInfoBean");

        m_isWorkflowTaskInstance = 
            workflowtask instanceof WorkflowTaskInstance;

        m_participantDefaultChoice = m_labels[4];

        if (m_costingEnabled)
        {
            Long targetLocaleId = (Long)getValue("targetLocaleId");
            
            m_allRates = (Hashtable)getValue("rates");
            m_expenseRateDefaultChoice = m_labels[21];
            if(m_revenueEnabled)
            {
                m_revenueRateDefaultChoice = m_labels[21];
            }
        }

        Panel panel = new Panel(new EnvoyLineLayout(5, 5, 5, 5));
        setPanel(panel);
        
        // text field.
        m_daysToAccept 			= new TextField(WorkflowConstants.daysToAccept);
        m_hoursToAccept 		= new TextField(WorkflowConstants.hoursToAccept);
        m_minutesToAccept 		= new TextField(WorkflowConstants.minutesToAccept);
        m_daysToComplete 		= new TextField(WorkflowConstants.daysToComplete);
        m_hoursToComplete 		= new TextField(WorkflowConstants.hoursToComplete);
        m_minutesToComplete 	= new TextField(WorkflowConstants.minutesToComplete);
        
        m_daysOverDueToPM 		= new TextField(WorkflowConstants.daysOverDueToPM);
        m_hoursOverDueToPM 		= new TextField(WorkflowConstants.hoursOverDueToPM);
        m_minutesOverDueToPM 	= new TextField(WorkflowConstants.minutesOverDueToPM);
        m_daysOverDueToUser 	= new TextField(WorkflowConstants.daysOverDueToUser);
        m_hoursOverDueToUser 	= new TextField(WorkflowConstants.hoursOverDueToUser);
        m_minutesOverDueToUser 	= new TextField(WorkflowConstants.minutesOverDueToUser);
        
        // ACTIVITY_TYPE
        m_activityTypeChoice = new Choice();
        m_activityTypeChoice.addItem(m_defaultChoose); //choose...
        for (int i = 0; i < m_activities.size(); i++)
        {
            Activity activity = (Activity)m_activities.elementAt(i);
            //m_activityTypeChoice.addItem(activity.toString());
            
            //This block make sure shows the activity's display name, not name;
            m_activityTypeChoice.addItem(activity.getDisplayName());
        }
        
        m_activityTypeChoice.addItemListener(new ItemListener()
        {
            public void itemStateChanged(ItemEvent e) 
            {
                if (e.getStateChange() == e.SELECTED)
                {
                    Activity activity = 
                        getSelectedActivity(m_activityTypeChoice.getSelectedItem());
                    
                    String note = AppletHelper.getI18nContent("msg_note_ok_gray");
                    
                    if(activity.isType(activity.TYPE_AUTOACTION)) {
                        m_grid.setMultipleSelection(false);
                        
                        note = note + "\n\n";
                        note = note + "          ";
                        note = note + AppletHelper.getI18nContent("msg_note_auto_action");
                        noteText.setText(note);
                    }
                    else if(activity.isType(activity.TYPE_GSEDITION)) {
                        m_grid.setMultipleSelection(false);
                        
                        note = note + "\n\n";
                        note = note + "          ";
                        note = note + AppletHelper.getI18nContent("msg_note_gs_action");
                        noteText.setText(note);
                    }
                    else {
                        m_participantChoice.enable();
                        m_grid.setMultipleSelection(true);
                        noteText.setText(note);
                    }
                }
            }
        });

        // SYSTEM ACTION choice
        m_systemActionChoice = new Choice();
        for (int i = 0; i < m_systemActions.size(); i++)
        {
            SystemAction systemAction = (SystemAction)m_systemActions.elementAt(i);
            m_systemActionChoice.addItem(systemAction.getDisplayName());
        }

        ///check if costing enabled
        if (m_costingEnabled)
        {
            // RATE SELECTION CRITERIA
            CheckboxGroup rcbg = new CheckboxGroup();
            m_rateSelectionCheckbox = new Checkbox[2];
            for (int i = 0; i < 2; i++)
            {
                boolean initial = i == 0;// set the first as default
                m_rateSelectionCheckbox[i] = new Checkbox((String)m_labels[i+28], 
                                                        rcbg, initial);
                m_rateSelectionCheckbox[i].
                    addItemListener(new ItemListener()
                                    {
                                        public void itemStateChanged(ItemEvent e)
                                        {
                                            if (e.getStateChange() == e.SELECTED)
                                            {
                                                // Do nothing
                                            }
                                        }
                                    });
            }
            // EXPENSE RATE TYPE
            m_expenseRateChoice = new Choice();
            m_expenseRateChoice.addItem(m_expenseRateDefaultChoice);
            m_expenseRateChoice.addItemListener(new ItemListener()
                {
                    public void itemStateChanged(ItemEvent e) 
                    {
                        // Now revenue rate is independent of 
                        // expense rate so no need to reset
                        // revenue rate when expense rate changes
                        // populateRevenueRateDropDown(false);
                        if (e.getStateChange() == e.SELECTED)
                        {
                            // determine whether to show the hour field 
                            boolean toShow =   false;
                            int expenseRateComboIndex = 
                                m_expenseRateChoice.getSelectedIndex();
                            if(expenseRateComboIndex > 0)
                            {
                                Rate expRate = (Rate)m_expenseRatesForSelectedActivity.get(expenseRateComboIndex -1);
                                toShow =   expRate != null && expRate.getRateType().equals(Rate.UnitOfWork.HOURLY);
                            }
                            if(m_revenueEnabled)
                            {
                                int revenueRateComboIndex = 
                                    m_revenueRateChoice.getSelectedIndex();
                                if(revenueRateComboIndex > 0)
                                {
                                    Rate revRate = (Rate)m_revenueRatesForSelectedActivity.get(revenueRateComboIndex -1);
                                    toShow =  toShow || (revRate != null && revRate.getRateType().equals(Rate.UnitOfWork.HOURLY));
                                }
                            }
                            showHideHourlyRateField(toShow);

                            updateButtonStatus(isDirty());
                        }
                    }
                });
            if(m_revenueEnabled)
            {
                // REVENUE RATE TYPE
                m_revenueRateChoice = new Choice();
                m_revenueRateChoice.addItem(m_revenueRateDefaultChoice);
                m_revenueRateChoice.addItemListener(new ItemListener()
                    {
                        public void itemStateChanged(ItemEvent e) 
                        {
                            if (e.getStateChange() == e.SELECTED)
                            {
                                // determine whether to show the hour field 
                                boolean toShow =   false;
                                int expenseRateComboIndex = 
                                    m_expenseRateChoice.getSelectedIndex();
                                if(expenseRateComboIndex > 0)
                                {
                                    Rate expRate = (Rate)m_expenseRatesForSelectedActivity.get(expenseRateComboIndex -1);
                                    toShow =   expRate != null && expRate.getRateType().equals(Rate.UnitOfWork.HOURLY);
                                }
                                int revenueRateComboIndex = 
                                    m_revenueRateChoice.getSelectedIndex();
                                if(revenueRateComboIndex > 0)
                                {
                                    Rate revRate = (Rate)m_revenueRatesForSelectedActivity.get(revenueRateComboIndex -1);
                                    toShow =  toShow || (revRate != null && revRate.getRateType().equals(Rate.UnitOfWork.HOURLY));
                                }
                                showHideHourlyRateField(toShow);
                                updateButtonStatus(isDirty());
                            }
                        }
                    });

            }
            if (m_isWorkflowTaskInstance)
            {
                m_hourlyRateLabel = new EnvoyLabel(
                    m_labels[23], Label.LEFT, m_width, m_height);
                
                // Hourly rate text field
                m_hourlyRateField = new TextField();
                m_hourlyRateField.addTextListener(new TextListener(){
                        public void textValueChanged(TextEvent e) 
                        {
                            updateButtonStatus(isDirty());
                        }
                        });
            }
        }

        m_isCalendarInstalled = ((Boolean)getValue(
            "isCalendarInstalled")).booleanValue();

        // PARTICIPANT
        m_selectUserOption = m_labels[3];
        m_participantChoice = new Choice();
        m_participantChoice.addItem(m_participantDefaultChoice);
        if(m_isCalendarInstalled)     
        {
            // Calendering is on.
            m_participantChoice.addItem(m_labels[32]);
            m_participantChoice.addItem(m_labels[33]);
        }
        m_participantChoice.addItem(m_selectUserOption);
       
        m_participantChoice.addItemListener(new ItemListener()
            {
                public void itemStateChanged(ItemEvent e) 
                {
                    if (e.getStateChange() == e.SELECTED)
                    {
                        // first populate the grid based on selection
                        populateRoleGrid();
                        updateButtonStatus(isDirty());
                    }
                }
            });

        m_daysToComplete.
            addTextListener(new TextListener()
                            {
                                public void textValueChanged(TextEvent e)
                                {
                                    updateButtonStatus(isDirty());
                                }
                            });

        m_hoursToComplete.
            addTextListener(new TextListener()
                            {
                                public void textValueChanged(TextEvent e)
                                {
                                    updateButtonStatus(isDirty());
                                }
                            });

        m_minutesToComplete.
            addTextListener(new TextListener()
                            {
                                public void textValueChanged(TextEvent e)
                                {
                                    updateButtonStatus(isDirty());
                                }
                            });
        m_daysToAccept.
            addTextListener(new TextListener()
                            {
                                public void textValueChanged(TextEvent e)
                                {
                                    updateButtonStatus(isDirty());
                                }
                            });

        m_hoursToAccept.
            addTextListener(new TextListener()
                            {
                                public void textValueChanged(TextEvent e)
                                {
                                    updateButtonStatus(isDirty());
                                }
                            });

        m_minutesToAccept.
            addTextListener(new TextListener()
                            {
                                public void textValueChanged(TextEvent e)
                                {
                                    updateButtonStatus(isDirty());
                                }
                            });
        
        m_daysOverDueToPM.
        addTextListener(new TextListener()
                        {
                            public void textValueChanged(TextEvent e)
                            {
                                updateButtonStatus(isDirty());
                            }
                        });

        m_hoursOverDueToPM.
        addTextListener(new TextListener()
                        {
                            public void textValueChanged(TextEvent e)
                            {
                                updateButtonStatus(isDirty());
                            }
                        });

        m_minutesOverDueToPM.
        addTextListener(new TextListener()
                        {
                            public void textValueChanged(TextEvent e)
                            {
                                updateButtonStatus(isDirty());
                            }
                        });
        m_daysOverDueToUser.
        addTextListener(new TextListener()
                        {
                            public void textValueChanged(TextEvent e)
                            {
                                updateButtonStatus(isDirty());
                            }
                        });

        m_hoursOverDueToUser.
        addTextListener(new TextListener()
                        {
                            public void textValueChanged(TextEvent e)
                            {
                                updateButtonStatus(isDirty());
                            }
                        });

        m_minutesOverDueToUser.
        addTextListener(new TextListener()
                        {
                            public void textValueChanged(TextEvent e)
                            {
                                updateButtonStatus(isDirty());
                            }
                        });
        
        m_activityTypeChoice.
            addItemListener(new ItemListener()
                            {
                                public void itemStateChanged(ItemEvent e)
                                {
                                    // first populate the grid based on selection
                                    populateRoleGrid();
                                    populateExpenseRateDropDown(false);
                                    populateRevenueRateDropDown(false);
                                    updateButtonStatus(isDirty());
                                }
                            });

        m_systemActionChoice.
            addItemListener(new ItemListener()
                            {
                                public void itemStateChanged(ItemEvent e)
                                {
                                    updateButtonStatus(isDirty());
                                }
                            });

        String[] stmp = {m_labels[5], m_labels[6], m_labels[7], m_labels[31]};
        m_header = stmp;
        m_grid = new GridPanel(0, m_header.length);
        setDisplayOptions(m_grid);
        // set selection options.
        m_grid.setCellSelection(false);
        m_grid.setRowSelection(true);
        m_grid.setColSelection(false);
        m_grid.setMultipleSelection(true);
        // set fonts.
        m_grid.setGridAttributes(new GridAttributes(this, EnvoyFonts.getCellFont(), ENVOY_BLACK, ENVOY_WHITE,
                                                    GridPanel.JUST_LEFT, GridPanel.TEXT));
        m_grid.setHeaderAttributes(new GridAttributes(this, EnvoyFonts.getHeaderFont(), ENVOY_WHITE, ENVOY_BLUE,
                                                      GridPanel.JUST_LEFT, GridPanel.TEXT), true);
        // set headers.
        m_grid.setColHeaders(m_header);
        m_grid.setThreeDBorder(false);
        //  by default the role grid is invisible since the default is for
        //  container role
        //
        m_grid.setVisible(false);
        // add grid listeners.
        m_grid.addGridListener(new GridAdapter()
                               {
                                   public void gridCellsClicked(GridEvent event)
                                   {
                                       populateExpenseRateDropDown(false);
                                       // Now Revenue rate is independent of expense
                                       // rate type. So no need to repopulate revenue
                                       // rate here. Commenting out.
                                       // populateRevenueRateDropDown(false);
                                       updateButtonStatus(isDirty());
                                   }
                               });

        // activity title
        Font panelFont = new Font("Arial", Font.BOLD, 18);
        Label titleLabel = null;
        if (m_isModifyMode)
            titleLabel = new Label(m_labels[13], Label.LEFT);
        else
            titleLabel = new Label(m_labels[15], Label.LEFT);
        titleLabel.setFont(panelFont);

        panelFont = new Font("Arial", Font.BOLD, 12);
        // estimate width needed for dialog box
        FontMetrics panelFontMetric = new Label().getFontMetrics(panelFont);
        int w1 = panelFontMetric.stringWidth(m_selectUserOption);
        int w2 = panelFontMetric.stringWidth(m_labels[4]);
        int len;
        if (w1 > w2)
        {
            len = m_selectUserOption.length();
            m_width = w1 + 10*(w1 / len); // allow roughly 10 spaces for checkbox
        }
        else
        {
            len = m_labels[4].length();
            m_width = w2 + 10*(w2 / len); // allow roughly 10 spaces for checkbox
        }

        m_width = getDialogWidth()/3;
        m_height = 24;

        EnvoyLabel nameLabel = new EnvoyLabel(m_labels[0], Label.LEFT, m_width, m_height);
        nameLabel.setFont(panelFont);
        EnvoyLabel systemActionLabel = new EnvoyLabel(m_labels[24], Label.LEFT, m_width, m_height);
        systemActionLabel.setFont(panelFont);
        EnvoyLabel timeToAcceptLabel = new EnvoyLabel(m_labels[16], Label.LEFT, m_width, m_height);
        timeToAcceptLabel.setFont(panelFont);
        EnvoyLabel timeToCompleteLabel = new EnvoyLabel(m_labels[1], Label.LEFT, m_width, m_height);
        timeToCompleteLabel.setFont(panelFont);
        EnvoyLabel dayAcceptLabel = new EnvoyLabel(m_labels[17], Label.LEFT, m_width, m_height);
        EnvoyLabel hourAcceptLabel = new EnvoyLabel(m_labels[18], Label.LEFT, m_width, m_height);
        EnvoyLabel minuteAcceptLabel = new EnvoyLabel(m_labels[19], Label.LEFT, m_width, m_height);
        EnvoyLabel dayCompleteLabel = new EnvoyLabel(m_labels[17], Label.LEFT, m_width, m_height);
        EnvoyLabel hourCompleteLabel = new EnvoyLabel(m_labels[18], Label.LEFT, m_width, m_height);
        EnvoyLabel minuteCompleteLabel = new EnvoyLabel(m_labels[19], Label.LEFT, m_width, m_height);
        EnvoyLabel rateSelectionLabel = new EnvoyLabel(m_labels[30], Label.LEFT, m_width, m_height);
        
        EnvoyLabel timeOverDueToPMLabel = new EnvoyLabel(m_labels[34], Label.LEFT, m_width, m_height);
        timeOverDueToPMLabel.setFont(panelFont);
        EnvoyLabel timeOverDueToUserLabel = new EnvoyLabel(m_labels[35], Label.LEFT, m_width, m_height);
        timeOverDueToUserLabel.setFont(panelFont);
        EnvoyLabel dayOverDueToPMLabel = new EnvoyLabel(m_labels[17], Label.LEFT, m_width, m_height);
        EnvoyLabel hourOverDueToPMLabel = new EnvoyLabel(m_labels[18], Label.LEFT, m_width, m_height);
        EnvoyLabel minuteOverDueToPMLabel = new EnvoyLabel(m_labels[19], Label.LEFT, m_width, m_height);
        EnvoyLabel dayOverDueToUserLabel = new EnvoyLabel(m_labels[17], Label.LEFT, m_width, m_height);
        EnvoyLabel hourOverDueToUserLabel = new EnvoyLabel(m_labels[18], Label.LEFT, m_width, m_height);
        EnvoyLabel minuteOverDueToUserLabel = new EnvoyLabel(m_labels[19], Label.LEFT, m_width, m_height);
        
        EnvoyLabel participantLabel = new EnvoyLabel(m_labels[2], Label.LEFT, m_width, m_height);
        participantLabel.setFont(panelFont);
        
        String note = AppletHelper.getI18nContent("msg_note_ok_gray");
        noteText = new JTextArea(note);
        noteText.setEditable(false);
        noteText.setBackground(this.getBackground());
        noteText.setLineWrap(true);
        noteText.setWrapStyleWord(true);
        noteText.setFont(EnvoyFonts.getCellFont());

        panel.add(titleLabel,
                  new EnvoyConstraints(getDialogWidth(), m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE,
                                       EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.END_OF_LINE));
        panel.add(nameLabel,
                  new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(m_activityTypeChoice,
                  new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.CENTER,
                                       EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.END_OF_LINE));
        panel.add(systemActionLabel,
                  new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE,
                                       EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(m_systemActionChoice,
                  new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.CENTER,
                                       EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.END_OF_LINE));
        panel.add(timeToAcceptLabel,
                  new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(m_daysToAccept,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(dayAcceptLabel,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                       EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(m_hoursToAccept,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(hourAcceptLabel,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                       EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(m_minutesToAccept,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(minuteAcceptLabel,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                       EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.END_OF_LINE));

        panel.add(timeToCompleteLabel,
                  new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(m_daysToComplete,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(dayCompleteLabel,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                       EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(m_hoursToComplete,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(hourCompleteLabel,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                       EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(m_minutesToComplete,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(minuteCompleteLabel,
                  new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                       EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.END_OF_LINE));
        

      panel.add(timeOverDueToPMLabel,
                new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                     EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
        
      panel.add(m_daysOverDueToPM,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                     EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(dayOverDueToPMLabel,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                     EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(m_hoursOverDueToPM,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                     EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(hourOverDueToPMLabel,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                     EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(m_minutesOverDueToPM,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                     EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(minuteOverDueToPMLabel,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                     EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.END_OF_LINE));

      panel.add(timeOverDueToUserLabel,
                new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                     EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(m_daysOverDueToUser,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                     EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(dayOverDueToUserLabel,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                     EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(m_hoursOverDueToUser,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                     EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(hourOverDueToUserLabel,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                     EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(m_minutesOverDueToUser,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.LEFT,
                                     EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.NOT_END_OF_LINE));
      panel.add(minuteOverDueToUserLabel,
                new EnvoyConstraints(getDialogWidth()/12, m_height, 1, EnvoyConstraints.CENTER,
                                     EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.END_OF_LINE));
        // Add Participant information grid.
        panel.add(participantLabel,
                  new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                       EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.NOT_END_OF_LINE));
        panel.add(m_participantChoice,
                  new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.CENTER,
                                       EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                       EnvoyConstraints.END_OF_LINE));
        panel.add(m_grid, new EnvoyConstraints(0, 0, 1, EnvoyConstraints.CENTER, EnvoyConstraints.X_RESIZABLE,
                                               EnvoyConstraints.Y_RESIZABLE, EnvoyConstraints.END_OF_LINE));
        // Add the Costing info if costing is enabled.

        if (m_costingEnabled)
        {
            panel.add(rateSelectionLabel,
                      new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                           EnvoyConstraints.X_NOT_RESIZABLE,
                                           EnvoyConstraints.Y_NOT_RESIZABLE,
                                           EnvoyConstraints.NOT_END_OF_LINE));
            rateSelectionLabel.setFont(panelFont);
            panel.add(m_rateSelectionCheckbox[0],
                      new EnvoyConstraints(getDialogWidth()/4, m_height, 1, EnvoyConstraints.CENTER,
                                           EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                           EnvoyConstraints.NOT_END_OF_LINE));
            panel.add(m_rateSelectionCheckbox[1],
                      new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.CENTER,
                                           EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                           EnvoyConstraints.END_OF_LINE));
            EnvoyLabel expenseRateLabel = new EnvoyLabel(m_labels[20], Label.LEFT, m_width, m_height);
            expenseRateLabel.setFont(panelFont);
            panel.add(expenseRateLabel,
                      new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                           EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                           EnvoyConstraints.NOT_END_OF_LINE));
            panel.add(m_expenseRateChoice, 
                      new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                           EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                           EnvoyConstraints.END_OF_LINE));

            if(m_revenueEnabled)
            {
                EnvoyLabel revenueRateLabel = new EnvoyLabel(m_labels[22], Label.LEFT, m_width, m_height);
                revenueRateLabel.setFont(panelFont);
                panel.add(revenueRateLabel,
                          new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                               EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                               EnvoyConstraints.NOT_END_OF_LINE));
                panel.add(m_revenueRateChoice, 
                          new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                               EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                               EnvoyConstraints.END_OF_LINE));

            }

            if (m_isWorkflowTaskInstance)
            {
                m_hourlyRateLabel.setFont(panelFont);

                panel.add(m_hourlyRateLabel,
                          new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                               EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                               EnvoyConstraints.NOT_END_OF_LINE));
                panel.add(m_hourlyRateField, 
                          new EnvoyConstraints(m_width/3, m_height, 1, EnvoyConstraints.CENTER,
                                               EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                              EnvoyConstraints.NOT_END_OF_LINE));
                panel.add(new EnvoyLabel(),
                          new EnvoyConstraints(m_width, m_height, 1, EnvoyConstraints.LEFT,
                                               EnvoyConstraints.X_NOT_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                               EnvoyConstraints.END_OF_LINE));

            }
        }

        panel.add(noteText,
                new EnvoyConstraints(m_width * 4, 65, 1, EnvoyConstraints.LEFT,
                                     EnvoyConstraints.X_RESIZABLE, EnvoyConstraints.Y_NOT_RESIZABLE,
                                     EnvoyConstraints.END_OF_LINE));
        
        
        
        populateDialog(workflowtask);

        //  Resize the Dialog to accomodate for the role grid or lack of it
        //
        resizeDialog();

        return panel;
    }

    private void resizeDialog()
    {
        int width   = getDialogWidth() + getInsets().left + 
            getInsets().right ;
        int height  = getDialogHeight() + getInsets().top + 
            getInsets().bottom ;
/*
        System.out.println("resizeDialog-->width:\t"+width+"="+getDialogWidth() +"+"+ getInsets().left +"+"+ getInsets().right);
        System.out.println("resizeDialog-->height:\t"+height+"="+getDialogHeight() +"+"+ getInsets().top +"+"+ getInsets().bottom);
*/
        setSize(width, height);

        // resize the parent dialog as required by Netscape - defect 4941
        super.setSize(width, height);
        super.invalidate();
        super.validate();
        super.repaint();
    }

    private void populateDialog(WorkflowTask p_workflowtask)
    {
        if (p_workflowtask.getActivityName() != null)
        {
            m_roles = p_workflowtask.getRoles();
            //  Finally, add the current activity in the choice and also
            //  select it in GUI
            //
            if(m_costingEnabled)
            {
                m_selectedExpenseRateId = m_taskInfoBean == null ? 
                    p_workflowtask.getExpenseRateId() :
                    (m_taskInfoBean.getExpenseRate() == null ? 
                     -1 : 
                    m_taskInfoBean.getExpenseRate().getId());
                if(m_revenueEnabled)
                {
                    m_selectedRevenueRateId = m_taskInfoBean == null ? 
                        p_workflowtask.getRevenueRateId() :
                        (m_taskInfoBean.getRevenueRate() == null ? 
                         -1 : 
                        m_taskInfoBean.getRevenueRate().getId());
                }
            }

            //m_activityTypeChoice.select(p_workflowtask.getActivityName());
            m_activityTypeChoice.select(p_workflowtask.getActivityDisplayName());

            // Get the System Action Display Name that corresponds to the 
            // stored System Action Type
            String systemActionType = p_workflowtask.getActionType();
            for (int i = 0; i < m_systemActions.size(); i++)
            {
                SystemAction systemAction = (SystemAction)m_systemActions.elementAt(i);
                if (systemAction.getType().equals(systemActionType)) 
                {
                    m_systemActionChoice.select(systemAction.getDisplayName());
                    break;
                }
            }
            
            // TomyD -- commented out this code as a fix to bug 8745.
            // Leave this code here in case we decide to disable the activity
            // combo-box for an active node.  We've been going back and forth
            // for quite a while!!!
            // if the task is active and selection is not "choose...", 
            // disable the combo-box
            /*if (m_isWorkflowTaskInstance && 
                ((WorkflowTaskInstance)p_workflowtask).getTaskState() == 
                WorkflowConstants.ACTIVE_TASK && 
                !m_activityTypeChoice.getSelectedItem().equals(
                    m_defaultChoose))
            {
                m_activityTypeChoice.setEnabled(false);
            }*/

            // handle participant choices.  It's a combination of the 
            // role type and role preference
            m_initialRoleType = p_workflowtask.getRoleType();
            String initialRolePreference = p_workflowtask.getRolePreference();
            // the the role type is user type, select the user option.
            String selectedChoice = m_defaultChoose;
            if (m_initialRoleType)
            {
                selectedChoice = m_selectUserOption;
            }

            if(m_isCalendarInstalled && initialRolePreference != null)
            {
                if (WorkflowConstants.AVAILABLE_ROLE_PREFERENCE.
                    equals(initialRolePreference))
                {
                    selectedChoice = m_labels[32];
                }
                else if (WorkflowConstants.FASTEST_ROLE_PREFERENCE.
                         equals(initialRolePreference))
                {
                    selectedChoice = m_labels[33];
                }
            }
            
            m_participantChoice.select(selectedChoice);
            
            // handle participant choices
            long dhm[] = DateHelper.daysHoursMinutes(
                p_workflowtask.getAcceptTime());
            m_daysToAccept.setText(String.valueOf(dhm[0]));
            m_hoursToAccept.setText(String.valueOf(dhm[1]));
            m_minutesToAccept.setText(String.valueOf(dhm[2]));
            dhm = DateHelper.daysHoursMinutes(
                p_workflowtask.getCompletedTime());
            m_daysToComplete.setText(String.valueOf(dhm[0]));
            m_hoursToComplete.setText(String.valueOf(dhm[1]));
            m_minutesToComplete.setText(String.valueOf(dhm[2]));
            
            dhm = DateHelper.daysHoursMinutes(
                    p_workflowtask.getOverdueToPM());
            m_daysOverDueToPM.setText(String.valueOf(dhm[0]));
            m_hoursOverDueToPM.setText(String.valueOf(dhm[1]));
            m_minutesOverDueToPM.setText(String.valueOf(dhm[2]));
            dhm = DateHelper.daysHoursMinutes(
                  p_workflowtask.getOverdueToUser());
            m_daysOverDueToUser.setText(String.valueOf(dhm[0]));
            m_hoursOverDueToUser.setText(String.valueOf(dhm[1]));
            m_minutesOverDueToUser.setText(String.valueOf(dhm[2]));

            populateRoleGrid();        
            if(m_costingEnabled)
            {
                // handle selection criteria choices
                m_initialRateCriteria = ((WorkflowTask)p_workflowtask).getRateSelectionCriteria();
                int rateSelectedCheckbox = 0;
                if(m_initialRateCriteria == WorkflowConstants.USE_ONLY_SELECTED_RATE )
                {
                    rateSelectedCheckbox = 0;
                }
                else
                {
                    rateSelectedCheckbox = 1;
                }
                m_rateSelectionCheckbox[rateSelectedCheckbox].setState(true);
                Rate exp = populateExpenseRateDropDown(true);
                Rate rev = populateRevenueRateDropDown(true);
                if ((exp != null && exp.getRateType().equals(Rate.UnitOfWork.HOURLY)) ||
                     (rev != null && rev.getRateType().equals(Rate.UnitOfWork.HOURLY)))
                {
                    showHideHourlyRateField(true);
                }
                else
                {
                    showHideHourlyRateField(false);
                }
            }
            
            Activity activity = 
                getSelectedActivity(p_workflowtask.getActivityDisplayName());
                  
            if(activity.isType(activity.TYPE_AUTOACTION)) {
                m_grid.setMultipleSelection(false);
                
                String note = AppletHelper.getI18nContent("msg_note_ok_gray");
                note = note + "\n\n";
                note = note + "          ";
                note = note + AppletHelper.getI18nContent("msg_note_auto_action");
                noteText.setText(note);
            }
        }
        else
        {
            if(m_costingEnabled)
            {
                showHourlyRateField(null);
            }
        }

        m_isModifyMode = p_workflowtask.getTaskId() > -1;        
    }

    /**
     * Perform a specific action when the ok button is clicked.
     */
    public void performAction()
    {
        int activityComboIndex = 
            m_activityTypeChoice.getSelectedIndex() - 1;
        Activity activity = (Activity)m_activities.elementAt(
            activityComboIndex);
        
        boolean isAllQualified = m_participantDefaultChoice.equals(
                m_participantChoice.getSelectedItem());

        if((activity.isType(activity.TYPE_AUTOACTION) || 
                activity.isType(activity.TYPE_GSEDITION)) && isAllQualified){
            //Get all autoaction users, if user numbers more than one, forbidden 
            // to select one user.
            String selectedActivityDisplayName = 
                m_activityTypeChoice.getSelectedItem();
            String selectedActivityName = 
                getSelectedActivityName(selectedActivityDisplayName);
            // ask the parent screen to get the grid data from the server side
            GridData gridData = null;

            if (m_isWorkflowTaskInstance)
            {
                WorkflowTask workflowtask = (WorkflowTask)getValue("wft");
                long taskId = workflowtask.getTaskId();

                gridData = (selectedActivityDisplayName.equals(m_defaultChoose)) ?
                        new GridData(0, m_header.length, false, true) :
                        m_parent.getRoleInfo(selectedActivityName, true, taskId);
            }
            else
            {
                gridData = (selectedActivityDisplayName.equals(m_defaultChoose)) ?
                        new GridData(0, m_header.length, false, true) :
                        m_parent.getRoleInfo(selectedActivityName, true);
            }

            if(gridData.getNumRows() > 1) {
                if(activity.isType(activity.TYPE_AUTOACTION)) {
                    m_parent.getEnvoyJApplet().getErrorDlg(m_messages[1]);
                }
                else if(activity.isType(activity.TYPE_GSEDITION)) {
                    m_parent.getEnvoyJApplet().getErrorDlg(m_messages[4]);
                }
                
                return;
            }
        }

        if(activity.isType(activity.TYPE_GSEDITION)) {
            Vector selectedRows = m_grid.getSelectedRows();
            if(selectedRows.size() > 1) {
                m_parent.getEnvoyJApplet().getErrorDlg(m_messages[4]);
                return;
            }
        }

        long acceptMillis =
            DateHelper.milliseconds(parseLong(m_daysToAccept.getText()),
                                    parseLong(m_hoursToAccept.getText()),
                                    parseLong(m_minutesToAccept.getText()));
        long completeMillis =
            DateHelper.milliseconds(parseLong(m_daysToComplete.getText()),
                                    parseLong(m_hoursToComplete.getText()),
                                    parseLong(m_minutesToComplete.getText()));
        
        long OverDueToPMMillis =
            DateHelper.milliseconds(parseLong(m_daysOverDueToPM.getText()),
                                    parseLong(m_hoursOverDueToPM.getText()),
                                    parseLong(m_minutesOverDueToPM.getText()));
        long OverDueToUserMillis =
            DateHelper.milliseconds(parseLong(m_daysOverDueToUser.getText()),
                                    parseLong(m_hoursOverDueToUser.getText()),
                                    parseLong(m_minutesOverDueToUser.getText()));
        if (acceptMillis == 0 || completeMillis == 0)
        {
            m_parent.getEnvoyJApplet().getErrorDlg(m_messages[0]);
        }
        else if(OverDueToPMMillis == 0 || OverDueToUserMillis == 0) {
            m_parent.getEnvoyJApplet().getErrorDlg(m_messages[2]);
        }
        else if(OverDueToPMMillis < OverDueToUserMillis || 
                OverDueToPMMillis == OverDueToUserMillis) {
            m_parent.getEnvoyJApplet().getErrorDlg(m_messages[3]);
        }
        else
        {
            boolean isUserRoleType = m_selectUserOption.equals(
                m_participantChoice.getSelectedItem());

            // The selected row header contains the role name. Even though there is no
            // grid displayed for container role, there is one there and we select
            // the first one automatically(See populateRoleGrid() in this class)
            Vector selectedRows = m_grid.getSelectedRows();
            int size = selectedRows == null ? 0 : selectedRows.size();
            String[] roles = new String[size];

            StringBuffer displayName = new StringBuffer();
            for (int i = 0; i < size; i++)
            {
                int selectedRow = ((Integer)selectedRows.get(i)).intValue();
                roles[i] = (String)m_grid.getGridData().
                    getRowHeaderData(selectedRow);

                Vector row = m_grid.getRowData(selectedRow);
                
                if (i > 0)
                {
                    displayName.append(",");
                }

                displayName.append(
                    isUserRoleType ? 
                    row.elementAt(0) + " " + row.elementAt(1) : 
                    m_labels[4]);
            }
            
            WorkflowTask workflowtask = (WorkflowTask)getValue("wft");

            //accept and complete time
            workflowtask.setAcceptedTime(acceptMillis);
            workflowtask.setCompletedTime(completeMillis);
            workflowtask.setOverdueToPM(OverDueToPMMillis);
            workflowtask.setOverdueToUser(OverDueToUserMillis);
            //role and role type
            workflowtask.setRoles(roles);
            workflowtask.setRoleType(isUserRoleType);
            workflowtask.setDisplayRoleName(displayName.toString());
            // reset the role preference since it's only used if
            // calendaring is installed (will be set below).
            workflowtask.setRolePreference(null);
            
            if(m_isCalendarInstalled)     
            {
                if (m_labels[32].equals(m_participantChoice.getSelectedItem()))
                {
                    workflowtask.setRolePreference(
                        WorkflowConstants.AVAILABLE_ROLE_PREFERENCE);
                }
                else if (m_labels[33].equals(m_participantChoice.getSelectedItem()))
                {
                    workflowtask.setRolePreference(
                        WorkflowConstants.FASTEST_ROLE_PREFERENCE);
                }
            }
            
            // activity name
            workflowtask.setActivity(activity);

            // System Action type
            int systemActionComboIndex = 
                m_systemActionChoice.getSelectedIndex();
            SystemAction systemActionSelected = 
                (SystemAction)m_systemActions.elementAt(systemActionComboIndex);
            String systemActionDisplayName = systemActionSelected.getDisplayName();
            
            // Get the System Action Type that corresponds to the 
            // System Action Display name in the pulldown
            for (int i = 0; i < m_systemActions.size(); i++)
            {
                SystemAction systemAction = (SystemAction)m_systemActions.elementAt(i);                
                if (systemAction.getDisplayName().equals(systemActionDisplayName)) 
                {
                    workflowtask.setActionType(systemAction.getType());
                    break;
                }
            }

            if (m_costingEnabled)
            {
                int rateSelectionCriteria = m_initialRateCriteria; 
                Rate expenseRate = findExpenseRateFromChoice(activity);
                Rate revenueRate = null;
                boolean isHourly = expenseRate == null ? false :
                    expenseRate.getRateType().equals(Rate.UnitOfWork.HOURLY);
                boolean hasAmountChanged = false;
                boolean hasRevenueChanged = false;
                boolean hasRateSelectionChanged = false;
                String hours = null;
                String text = null;
                boolean useOnlySelectedRate = m_rateSelectionCheckbox[0].getState();
                if(useOnlySelectedRate)
                {
                    rateSelectionCriteria = WorkflowConstants.USE_ONLY_SELECTED_RATE;
                }
                else
                {
                    rateSelectionCriteria = WorkflowConstants.USE_SELECTED_RATE_UNTIL_ACCEPTANCE;
                }
                if(m_initialRateCriteria != rateSelectionCriteria)
                {
                    hasRateSelectionChanged = true;
                }
                workflowtask.setRateSelectionCriteria(rateSelectionCriteria);
                if (expenseRate != null)
                {
                    workflowtask.setExpenseRateId(expenseRate.getId());
                }
                else    //clear the rate
                {
                    workflowtask.setExpenseRateId(-1);                    
                }
                if(m_revenueEnabled)
                {
                    revenueRate = findRevenueRateFromChoice(activity);
                    if (revenueRate != null)
                    {
                        isHourly = isHourly || revenueRate.getRateType().equals(Rate.UnitOfWork.HOURLY);
                        workflowtask.setRevenueRateId(revenueRate.getId());
                    }
                    else    //clear the rate
                    {
                        workflowtask.setRevenueRateId(-1);                    
                    }
                    hasRevenueChanged = !m_revenueRateChoice.getSelectedItem().equals(m_initialRevenueRateName);
                }
                if (m_taskInfoBean != null && isHourly)
                {
                    text = m_hourlyRateField.getText().trim();
                    hours = m_taskInfoBean.getActualHours();
                    // if the actual hours aren't set yet - then must be
                    // setting the estimated amount
                    if (hours == null)
                    {
                        hours = m_taskInfoBean.getEstimatedHours();
                    }
                    hasAmountChanged = 
                        m_taskInfoBean == null ? false : 
                        (hours == null ? 
                         (text != null && text.length() > 0) :
                         (!hours.equals(text)));                        
                }
                else
                {
                    if(isHourly)
                    {
                        if(m_hourlyRateField != null)
                        {
                            text = m_hourlyRateField.getText().trim();
                        }
                        hasAmountChanged = true;
                    }
                }
                // compare the currently selected rate with the initial value
                // if they are not the same, add TaskInfoBean to m_values (if rate type
                // is Hourly, add the estimated value as well).

                if (m_isWorkflowTaskInstance && 
                    !m_expenseRateChoice.getSelectedItem().equals(
                        m_initialExpenseRateName) || 
                    hasRevenueChanged || 
                    hasAmountChanged ||
                    hasRateSelectionChanged )
                {
                    String estimatedHours = null;
                    String actualHours = null;
                    if (isHourly)
                    {
                        // if the actual hours aren't set yet 
                        // then this is updating the estimated hours.
                        if ((m_taskInfoBean == null) || (m_taskInfoBean.getActualHours() == null))
                        {
                            estimatedHours = text;
                        }
                        else    // updating the actual hours.  estimated hours
                                // stay the same
                        {
                            estimatedHours = m_taskInfoBean.getEstimatedHours();
                            actualHours = text;
                        }
                    }
                    // create the object and set taskId, rate, and estimated val (for hour type)
                    TaskInfoBean taskInfo = new TaskInfoBean(
                        m_taskInfoBean == null ? WorkflowTask.ID_UNSET : 
                        m_taskInfoBean.getTaskId(),
                        estimatedHours,
                        actualHours,
                        expenseRate,
                        revenueRate,
                        rateSelectionCriteria);

                    if(getValue("modifiedTaskInfoMap") != null)
                    {
                        if(taskInfo.getTaskId() == WorkflowTask.ID_UNSET)
                        {
                            ((Hashtable)getValue("modifiedTaskInfoMap")).put(
                                new Long(workflowtask.getSequence()), taskInfo);                    
                        }
                        else
                        {
                            ((Hashtable)getValue("modifiedTaskInfoMap")).put(
                                new Long(taskInfo.getTaskId()), taskInfo);                    
                        }
                    }
                }
            }
            //add common things to wft
            m_values.addElement(workflowtask);
            dispose();
        }
    }

    /**
     * Invokes a dialog that is an instance of this class.
     */
    public static Vector getDialog(GVPane p_parent,
                                   String p_title,
                                   Hashtable p_hashtable)
    {
        m_parent = p_parent;
        WorkflowTaskDialog dlg = 
            new WorkflowTaskDialog(p_parent, p_title, p_hashtable);
        return dlg.doModal();
    }

    /**
     * Get the height for the dialog to be displayed.
     * @return The dialog height.
     */
    public int getDialogHeight()
    {
        if (m_grid.isVisible())
        {
            return 575;
        }
        else if (m_costingEnabled)
        {
        	if(m_isWorkflowTaskInstance) 
        	{
        		return 500;
        	}
            else 
            {
                return 470;
            }
        }
        else
        {
            return 350;			
        }
    }

    /**
     * Get the width for the dialog to be displayed.
     * @return The dialog width.
     */
    public int getDialogWidth()
    {
        if (500 > 3*m_width + 100)
            return 500;
        else
            return 3*m_width + 100;
    }

    //
    // PRIVATE SUPPORT METHODS
    //
    /* set the panel */
    private void setPanel(Panel p_panel)
    {
        m_panel = p_panel;
    }

    /* get the panel */
    private Panel getPanel()
    {
        return m_panel;
    }

    /* return the dialog values */
    private Vector doModal()
    {
        show();
        return m_values;
    }

    /* determines whether we're in the dirty mode. */
    private boolean isDirty()
    {       
        return(timesAreValid() &&
               isRateValid() &&
               m_grid.getFirstSelectedRow() > 0 &&
               !(m_activityTypeChoice.getSelectedItem().equals(
                   m_defaultChoose)));
    }

    // checks to see if rating info is valid.  If costing is enabled,
    // at least one rate should be selected in the rate combo-box.  Also
    // if the selected rate is of Hourly type, the value within the estimated
    // hourly rate should be valid (empty or a valid float)
    private boolean isRateValid()
    {
        boolean isDirty = true;
        if (m_costingEnabled)
        {
            boolean ignoreRevenue = true;
            if(m_revenueEnabled)
            {
                ignoreRevenue = false;
            }
            if (m_expenseRateChoice == null ||
                m_revenueRateChoice == null ||
                m_expenseRateChoice.countItems() <= 0 || 
                ignoreRevenue ? false : 
                m_revenueRateChoice.countItems() <= 0 || 
                isEstimatedExpenseRateInvalid() ||
                ignoreRevenue ? false :
                isEstimatedRevenueRateInvalid()
                )
            {
                isDirty = false;
            }            
        }

        return isDirty;
    }

    private Integer getSelectedExpenseRateType()
    {
        int rateComboIndex = m_expenseRateChoice.getSelectedIndex();
        if (rateComboIndex == 0)
        {
            return new Integer(0); // Invalid or non existant Type
        }

        Rate rate = 
            (Rate)m_expenseRatesForSelectedActivity.get(rateComboIndex -1);
        return rate.getRateType();
    }

    private boolean isEstimatedExpenseRateInvalid()
    {
        int rateComboIndex = m_expenseRateChoice.getSelectedIndex();
        if (rateComboIndex == 0 || m_hourlyRateField == null)
        {
            return false;
        }

        Rate rate = 
            (Rate)m_expenseRatesForSelectedActivity.get(rateComboIndex -1);
        return (rate.getRateType().equals(Rate.UnitOfWork.HOURLY) && 
                !isValidFloat(m_hourlyRateField));
    }

    private boolean isEstimatedRevenueRateInvalid()
    {
        int rateComboIndex = m_revenueRateChoice.getSelectedIndex();
        if (rateComboIndex == 0 || m_hourlyRateField == null)
        {
            return false;
        }

        Rate rate = 
            (Rate)m_revenueRatesForSelectedActivity.get(rateComboIndex -1);
        return (rate.getRateType().equals(Rate.UnitOfWork.HOURLY) && 
                !isValidFloat(m_hourlyRateField));
    }

    /* Return true if the contents of the given number field are between the */
    /* specified limits. */
    private boolean isValidFloat(TextField p_number)
    {
        String text = p_number.getText();
        boolean valid = false;
        try
        {
            float x = Float.parseFloat(
                (text == null || text.length() == 0) ? "0" : text.trim());
            // validation: make sure it's a positive value which is less than
            // the max float and has only 2 digits of precision.
            long val = Math.round(x*100);
            valid = (x >= 0) && (x == val/100.0f) && (x < Float.MAX_VALUE);
        }
        catch (Exception e)
        {            
        }

        if (!valid)
        {
            p_number.selectAll();
            p_number.requestFocus();
        }
        
        return valid;
    }

    /* ensure that all input time values are valid */
    private boolean timesAreValid()
    {
        return(acceptTimesAreValid() && completeTimesAreValid()
               && overduetoPMAreValid() && overduetoUserAreValid());
    }

    /* Return true if the accept times are valid */
    private boolean acceptTimesAreValid()
    {
        return timesAreValid(m_daysToAccept,
                             m_hoursToAccept,
                             m_minutesToAccept);
    }

    /* Return true if the complete times are valid */
    private boolean completeTimesAreValid()
    {
        return timesAreValid(m_daysToComplete, 
                             m_hoursToComplete,
                             m_minutesToComplete);
    }
    
    private boolean overduetoPMAreValid()
    {
        return timesAreValid(m_daysOverDueToPM, 
                             m_hoursOverDueToPM,
                             m_minutesOverDueToPM);
    }
    
    private boolean overduetoUserAreValid()
    {
        return timesAreValid(m_daysOverDueToUser, 
                             m_hoursOverDueToUser,
                             m_minutesOverDueToUser);
    }

    /* Return true if all of the given times are valid */
    private boolean timesAreValid(TextField p_days,
                                  TextField p_hours,
                                  TextField p_minutes)
    {
        return numberIsValid(p_days, 0, 365) &&
            numberIsValid(p_hours, 0, 24 * 7) &&
            numberIsValid(p_minutes, 0, 12 * 60);
    }

    /* Return true if the contents of the given number field are between the */
    /* specified limits. */
    private boolean numberIsValid(TextField p_number, long p_low, 
                                  long p_high)
    {
        String text = p_number.getText();
        boolean valid = false;
        try
        {
            long x = Long.parseLong(text);
            valid = ((x >= p_low) && (x <= p_high));
        }
        catch (Exception e)
        {
        }
        if (!valid)
        {
            p_number.selectAll();
            p_number.requestFocus();
        }
        return valid;
    }

    /* Convert the given string into a long; 0 if it doesn't parse. */
    private long parseLong(String p_string)
    {
        long v = 0;
        try
        {
            v = Long.parseLong(p_string);
        }
        catch (NumberFormatException e)
        {
            // ignore
        }
        return v;
    }

    /* Populate the rates according to the activity selected. */
    private Rate populateExpenseRateDropDown(boolean p_isInitialPopulate)
    {
        Rate selectedRate = null;
        if (m_costingEnabled)
        {
            try
            {
                // clear it of all - and repopulate
                m_expenseRateChoice.removeAll();
                m_expenseRateChoice.addItem(m_expenseRateDefaultChoice);
                m_expenseRateChoice.select(m_expenseRateDefaultChoice);
                long selectRateId = m_selectedExpenseRateId;
                String rateSelectId = null;
                
                if (m_selectUserOption.equals(m_participantChoice.getSelectedItem()))
                {
                    Vector selectedRows = m_grid.getSelectedRows();
                    // if ONLY ONE user is selected, use that user's rate.
                    if(selectedRows != null && selectedRows.size() == 1 )
                    {
                        int selectedRow = ((Integer)selectedRows.get(0)).
                            intValue();
                        Vector row = m_grid.getRowData(selectedRow);
                        if(m_isModifyMode || !p_isInitialPopulate)
                        {
                            rateSelectId = (String)row.elementAt(5);
                            if(rateSelectId != null && !rateSelectId.equals(""))
                            {
                                selectRateId = (new Long(rateSelectId)).longValue();
                            }
                        }
                    }
                }

                if (m_allRates != null && m_allRates.size() > 0)
                {
                    //String selectedActivity = m_activityTypeChoice.getSelectedItem();
                    String selectedActivity = getSelectedActivityName(m_activityTypeChoice.getSelectedItem());
                    
                    // get the rates associated with the particular activity
                    m_expenseRatesForSelectedActivity = 
                        (Vector)m_allRates.get(selectedActivity);
                    int size = m_expenseRatesForSelectedActivity == null ? 
                        0 : m_expenseRatesForSelectedActivity.size();

                    for (int i=0 ; i < size ; i++)
                    {
                        Rate rate = 
                            (Rate)m_expenseRatesForSelectedActivity.elementAt(i);
                        m_expenseRateChoice.addItem(rate.getName());     
                        // if this is the selected rate
                        if (selectRateId != -1)
                        {
                            if (rate.getId() == selectRateId)
                            {
                                selectedRate = rate;
                                m_expenseRateChoice.select(rate.getName());
                            }
                        }
                    }
                }

                showHourlyRateField(selectedRate);
                // Keep the initial rate name when the dialog is populated
                if (p_isInitialPopulate || !m_isModifyMode)
                {
                    m_initialExpenseRateName = m_expenseRateChoice.getSelectedItem();
                    if (m_taskInfoBean != null)
                    {
                        if (m_taskInfoBean.getActualHours() == null)
                        {
                            m_hourlyRateField.setText(
                                m_taskInfoBean.getEstimatedHours());
                        }
                        else
                        {
                            m_hourlyRateField.setText(
                                m_taskInfoBean.getActualHours());
                        }
                    }
                }
            }
            catch (Exception e)
            {
                //ignore
                e.printStackTrace();

            }
        }
        return selectedRate;
    }
    
    /* Gets the name of the selected activity by the selected display name. */ 
    private String getSelectedActivityName(String selectedActivityDisplayName)
    {
        String selectedActivity = null;
        Activity activity = null;
        for (int i = 0; i < m_activities.size(); i++)
        {
            activity = (Activity) m_activities.get(i);
            if (activity.getDisplayName().equals(selectedActivityDisplayName))
            {
                selectedActivity = activity.getActivityName();
                break;
            }
        }
        
        return selectedActivity;
    }
    
    /* Gets the name of the selected activity by the selected display name. */ 
    private Activity getSelectedActivity(String selectedActivityDisplayName)
    {
        Activity selectedActivity = null;
        Activity activity = null;
        for (int i = 0; i < m_activities.size(); i++)
        {
            activity = (Activity) m_activities.get(i);
            
            if (activity.getDisplayName().equals(selectedActivityDisplayName))
            {
                selectedActivity = activity;
                break;
            }
        }
        
        return selectedActivity;
    }

    /* Populate the revenue rates according to the activity selected. */
    private Rate populateRevenueRateDropDown(boolean p_isInitialPopulate)
    {
        Rate selectedRate = null;
        if (m_costingEnabled)
        {
            if(m_revenueEnabled)
            {
                // clear it of all - and repopulate
                m_revenueRateChoice.removeAll();
                m_revenueRateChoice.addItem(m_revenueRateDefaultChoice);
                m_revenueRateChoice.select(m_revenueRateDefaultChoice);
                if (m_allRates != null && m_allRates.size() > 0)
                {
                    //String selectedActivity = m_activityTypeChoice.getSelectedItem();
                    String selectedActivity = getSelectedActivityName(m_activityTypeChoice.getSelectedItem());
                    
                    // get the rates associated with the particular activity
                    m_revenueRatesForSelectedActivity = 
                        (Vector)m_allRates.get(selectedActivity);
                    int size = m_revenueRatesForSelectedActivity == null ? 
                        0 : m_revenueRatesForSelectedActivity.size();

                    for (int i=0 ; i < size ; i++)
                    {
                        Rate rate = 
                            (Rate)m_revenueRatesForSelectedActivity.elementAt(i);
                        // removing the type check. Revenue rate can be of
                        // any type and is idepepndent of Expense rate.
                        // if(rate.getRateType().equals(getSelectedExpenseRateType()))
                        m_revenueRateChoice.addItem(rate.getName());     
                        // if this is the selected rate
                        if (m_selectedRevenueRateId != -1)
                        {
                            if (rate.getId() == m_selectedRevenueRateId)
                            {
                                m_revenueRateChoice.select(rate.getName());
                                selectedRate = rate;
                                showHourlyRateField(rate);
                            }
                        }
                    }
                }

                // Keep the initial rate name when the dialog is populated
                if (p_isInitialPopulate)
                {
                    m_initialRevenueRateName = m_revenueRateChoice.getSelectedItem();
                    if (m_taskInfoBean != null)
                    {
                        if (m_taskInfoBean.getActualHours() == null)
                        {
                            m_hourlyRateField.setText(
                                m_taskInfoBean.getEstimatedHours());
                        }
                        else
                        {
                            m_hourlyRateField.setText(
                                m_taskInfoBean.getActualHours());
                        }
                    }
                }
            }
        }
        return selectedRate;
    }

    private void showHourlyRateField(Rate p_selectedRate)
    {
        if (m_costingEnabled && m_isWorkflowTaskInstance)
        {
            boolean visible = false;
            visible =   p_selectedRate != null &&
                        p_selectedRate.getRateType().equals(Rate.UnitOfWork.HOURLY);
            showHideHourlyRateField(visible);
        }
    }

    private void showHideHourlyRateField(boolean p_show)
    {
        if (m_costingEnabled && m_isWorkflowTaskInstance)
        {
            m_hourlyRateLabel.setVisible(p_show);
            m_hourlyRateField.setVisible(p_show);
            invalidate();
            validate();
            repaint();      
        }
    }

    /* Find the expense rate object in the Hashtable from the
       rate chosen (specified by activity). */
    private Rate findExpenseRateFromChoice(Activity p_activity)
    {
        String rateName = m_expenseRateChoice.getSelectedItem();
        Rate r = null;
        boolean found = false;
        // if it isn't the default which is "no Rate"
        if (!rateName.equals(m_expenseRateDefaultChoice))
        {
            Vector rates = (Vector)m_allRates.get(p_activity.getName());
            for (int i=0 ; !found && i < rates.size() ; i++)
            {
                r = (Rate)rates.elementAt(i);
                found = r.getName().equals(rateName);            
            }
        }
        return found? r : null;        
    }
    /* Find the revenue rate object in the Hashtable from the
       rate chosen (specified by activity). */
    private Rate findRevenueRateFromChoice(Activity p_activity)
    {
        String rateName = m_revenueRateChoice.getSelectedItem();
        Rate r = null;
        boolean found = false;
        // if it isn't the default which is "no Rate"
        if (!rateName.equals(m_revenueRateDefaultChoice))
        {
            Vector rates = (Vector)m_allRates.get(p_activity.getName());
            for (int i=0 ; !found && i < rates.size() ; i++)
            {
                r = (Rate)rates.elementAt(i);
                found = r.getName().equals(rateName);            
            }
        }
        return found? r : null;        
    }

    /* Populate the grid with role information */
    private void populateRoleGrid()
    {
        //String selectedActivity = m_activityTypeChoice.getSelectedItem();
        
        //Gets activity name by activity's display name.
        String selectedActivityDisplayName = m_activityTypeChoice.getSelectedItem();
        String selectedActivityName = getSelectedActivityName(selectedActivityDisplayName);

        // ask the parent screen to get the grid data from the server side
        GridData gridData = null;
        boolean isUser = m_selectUserOption.equals(m_participantChoice.getSelectedItem());
        if (m_isWorkflowTaskInstance)
        {
            WorkflowTask workflowtask = (WorkflowTask)getValue("wft");
            long taskId = workflowtask.getTaskId();
//            gridData = (selectedActivity.equals(m_defaultChoose)) ?
//                            new GridData(0, m_header.length, false, true) :
//                            m_parent.getRoleInfo(selectedActivity,
//                                                 isUser, taskId);
            gridData = (selectedActivityDisplayName.equals(m_defaultChoose)) ?
                    new GridData(0, m_header.length, false, true) :
                    m_parent.getRoleInfo(selectedActivityName, isUser, taskId);
        }
        else
        {
//            gridData = (selectedActivity.equals(m_defaultChoose)) ?
//                            new GridData(0, m_header.length, false, true) :
//                            m_parent.getRoleInfo(selectedActivity, isUser);
            gridData = (selectedActivityDisplayName.equals(m_defaultChoose)) ?
                    new GridData(0, m_header.length, false, true) :
                    m_parent.getRoleInfo(selectedActivityName, isUser);
        }

        // set the size of the grid and the data.
        m_grid.setNumRows(gridData.getNumRows());
        m_grid.setNumCols(gridData.getNumCols());
        m_grid.setGridData(gridData, true);
        m_grid.setColHeaders(m_header);
        setColumnWidth(gridData.getNumCols());
        m_grid.setSortEnable(true);

        //  Don't display the grid if "All Qualified Users" option was
        //  selected. We still continue to populate the grid object in anycase
        //  since, we need a place to hold the role information for that also.
        if (isUser)
        {
            m_grid.setVisible(true);            
        }
        else
        {
            m_grid.setVisible(false);

            //  Select the first and only row, for container role selection.
            //  This should force the dirty bit on
            //
            if (m_grid.getNumRows() > 0)
                m_grid.selectRow(1, true);
        }
        //  Resize the dialog to account for showing the role grid or not.
        resizeDialog();
        // update the grid after any changes
        m_grid.repaintGrid();
        //now that the grid is displayed, we can select the user role in the grid.
        selectUserRole(gridData);
    }

    // select the user role during modification of an activity
    private void selectUserRole(GridData gridData)
    {
        // only make selection during "modify" state and for a "user role"
        if (m_roles != null && m_initialRoleType)
        {
            List roles = Arrays.asList(m_roles);
            int numOfRoles = m_roles.length;
            int rows = gridData == null ? 0 : gridData.getNumRows();
            boolean isEqual = false;
            int countVisitedRoles = 0;
            for (int i=0; (countVisitedRoles != numOfRoles) && i<rows; i++)
            {
                int rowNum = i+1;
                String role = (String)gridData.getRowHeaderData(rowNum);
                isEqual = role != null && roles.contains(role);
                if (isEqual)
                {
                    //m_grid.revealRow(rowNum); // is supposed to scroll up but not working yet
                    m_grid.selectRow(rowNum, true);
                    countVisitedRoles++;                    
                }
            }
        }
    }

    /**
     * Sets the width of the columns depending on how many there are.
     */
    private void setColumnWidth(int gridDataColumns)
    {
        int colWidth = (getDialogWidth()/gridDataColumns) - 10;
        String colWidthValues = (colWidth - 5) + ", " +colWidth + ", " + (colWidth-20);

        if (gridDataColumns == m_header.length)
        {
            colWidthValues = colWidthValues + ", " + (colWidth+25);
        }        

        m_grid.setColWidths(colWidthValues);
    }
}