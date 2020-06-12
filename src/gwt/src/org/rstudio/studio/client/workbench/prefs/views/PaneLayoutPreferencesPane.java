/*
 * PaneLayoutPreferencesPane.java
 *
 * Copyright (C) 2020 by RStudio, PBC
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.prefs.views;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.prefs.RestartRequirement;
import org.rstudio.core.client.resources.ImageResource2x;
import org.rstudio.core.client.widget.FormLabel;
import org.rstudio.core.client.widget.ImageButton;
import org.rstudio.core.client.widget.ScrollPanelWithClick;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefs;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefsAccessor;
import org.rstudio.studio.client.workbench.ui.PaneConfig;
import org.rstudio.studio.client.workbench.ui.PaneManager;

import java.util.ArrayList;
import java.util.List;

public class PaneLayoutPreferencesPane extends PreferencesPane
{
   class ExclusiveSelectionMaintainer
   {
      class ListChangeHandler implements ChangeHandler
      {
         ListChangeHandler(int whichList)
         {
            whichList_ = whichList;
         }

         public void onChange(ChangeEvent event)
         {
            int selectedIndex = lists_[whichList_].getSelectedIndex();

            for (int i = 0; i < lists_.length; i++)
            {
               if (i != whichList_
                   && lists_[i].getSelectedIndex() == selectedIndex)
               {
                  lists_[i].setSelectedIndex(notSelectedIndex());
               }
            }

            updateTabSetPositions();
         }

         private Integer notSelectedIndex()
         {
            boolean[] seen = new boolean[4];
            for (ListBox listBox : lists_)
               seen[listBox.getSelectedIndex()] = true;
            for (int i = 0; i < seen.length; i++)
               if (!seen[i])
                  return i;
            return null;
         }

         private final int whichList_;
      }

      ExclusiveSelectionMaintainer(ListBox[] lists)
      {
         lists_ = lists;
         for (int i = 0; i < lists.length; i++)
            lists[i].addChangeHandler(new ListChangeHandler(i));
      }

      private final ListBox[] lists_;
   }

   class ModuleList extends Composite implements ValueChangeHandler<Boolean>,
                                                 HasValueChangeHandlers<ArrayList<Boolean>>
   {
      ModuleList(String width)
      {
         checkBoxes_ = new ArrayList<>();
         ScrollPanel scrollPanel = new ScrollPanelWithClick();
         FlowPanel flowPanel = new FlowPanel();
         for (String module : PaneConfig.getAllTabs())
         {
            CheckBox checkBox = new CheckBox(module, false);
            checkBox.addValueChangeHandler(this);
            checkBox.setWidth(width);
            checkBoxes_.add(checkBox);
            flowPanel.add(checkBox);
            if (module == "Presentation")
               checkBox.setVisible(false);
         }
         scrollPanel.setStyleName(res_.styles().paneLayoutTable());
         scrollPanel.setHeight("180px");
         scrollPanel.setWidth(width);
         scrollPanel.add(flowPanel);
         initWidget(scrollPanel);
      }

      public void onValueChange(ValueChangeEvent<Boolean> event)
      {
         ValueChangeEvent.fire(this, getSelectedIndices());
      }

      public ArrayList<Boolean> getSelectedIndices()
      {
         ArrayList<Boolean> results = new ArrayList<>();
         for (CheckBox checkBox : checkBoxes_)
            results.add(checkBox.getValue());
         return results;
      }

      public void setSelectedIndices(ArrayList<Boolean> selected)
      {
         for (int i = 0; i < selected.size(); i++)
            checkBoxes_.get(i).setValue(selected.get(i), false);
      }

      public ArrayList<String> getValue()
      {
         ArrayList<String> value = new ArrayList<>();
         for (CheckBox checkBox : checkBoxes_)
         {
            if (checkBox.getValue())
               value.add(checkBox.getText());
         }
         return value;
      }

      public void setValue(ArrayList<String> tabs)
      {
         for (CheckBox checkBox : checkBoxes_)
            checkBox.setValue(tabs.contains(checkBox.getText()), false);
      }

      public boolean presentationVisible()
      {
         if (checkBoxes_.size() <= 0)
            return false;

         CheckBox lastCheckBox = checkBoxes_.get(checkBoxes_.size() - 1);
         return StringUtil.equals(lastCheckBox.getText(), "Presentation") &&
                                  lastCheckBox.isVisible();
      }

      public HandlerRegistration addValueChangeHandler(
            ValueChangeHandler<ArrayList<Boolean>> handler)
      {
         return addHandler(handler, ValueChangeEvent.getType());
      }

      private final ArrayList<CheckBox> checkBoxes_;
   }


   @Inject
   public PaneLayoutPreferencesPane(PreferencesDialogResources res,
                                    UserPrefs userPrefs,
                                    Provider<PaneManager> pPaneManager)
   {
      res_ = res;
      userPrefs_ = userPrefs;
      paneManager_ = pPaneManager.get();

      add(new Label("Choose the layout of the panes in RStudio by selecting from the controls in each quadrant.", true));

      // temporary check for enabling additional columns feature
      additionalColumnCount_ = userPrefs.panes().getGlobalValue().getAdditionalSourceColumns();
      if (userPrefs.enableAdditionalColumns().getGlobalValue())
      {
         // !!! temporary debugging call
         paneManager_.syncAdditionalColumnCount(additionalColumnCount_);
         addSource_ = new ImageButton("Add source", res_.iconAddSourcePane2x());
         addSource_.setVisible(true);
         add(addSource_);
         addSource_.addClickHandler(event ->
         {
            additionalColumnCount_ = paneManager_.addSourceWindow();
            updateTable();
         });
      }
      else
      {
         if (additionalColumnCount_ > 0)
            additionalColumnCount_ = paneManager_.closeAllAdditionalColumns();
         addSource_ = null;
      }

      String[] visiblePanes = PaneConfig.getVisiblePanes();

      leftTop_ = new ListBox();
      Roles.getListboxRole().setAriaLabelProperty(leftTop_.getElement(), "Top left quadrant");
      leftBottom_ = new ListBox();
      Roles.getListboxRole().setAriaLabelProperty(leftBottom_.getElement(), "Bottom left quadrant");
      rightTop_ = new ListBox();
      Roles.getListboxRole().setAriaLabelProperty(rightTop_.getElement(), "Top right quadrant");
      rightBottom_ = new ListBox();
      Roles.getListboxRole().setAriaLabelProperty(rightBottom_.getElement(), "Bottom right quadrant");
      visiblePanes_ = new ListBox[]{leftTop_, leftBottom_, rightTop_, rightBottom_};
      for (ListBox lb : visiblePanes_)
      {
         for (String value : visiblePanes)
            lb.addItem(value);
      }

      PaneConfig value = userPrefs.panes().getGlobalValue().cast();
      if (value == null || !value.validateAndAutoCorrect())
         userPrefs.panes().setGlobalValue(PaneConfig.createDefault(), false);

      JsArrayString origPanes = userPrefs.panes().getGlobalValue().getQuadrants();
      for (int i = 0; i < 4; i++)
      {
         boolean success = selectByValue(visiblePanes_[i], origPanes.get(i));
         if (!success)
         {
            Debug.log("Bad config! Falling back to a reasonable default");
            leftTop_.setSelectedIndex(0);
            leftBottom_.setSelectedIndex(1);
            rightTop_.setSelectedIndex(2);
            rightBottom_.setSelectedIndex(3);
            break;
         }
      }

      new ExclusiveSelectionMaintainer(visiblePanes_);

      for (ListBox lb : visiblePanes_)
         lb.addChangeHandler(new ChangeHandler()
         {
            public void onChange(ChangeEvent event)
            {
               dirty_ = true;
            }
         });

      additionalColumnCount_ = value.getAdditionalSourceColumns();

      String cellWidth = updateTable();
      visiblePanePanels_ = new VerticalPanel[] {leftTopPanel_, leftBottomPanel_,
                                            rightTopPanel_, rightBottomPanel_};

      tabSet1ModuleList_ = new ModuleList(cellWidth);
      tabSet1ModuleList_.setValue(toArrayList(userPrefs.panes().getGlobalValue().getTabSet1()));
      tabSet2ModuleList_ = new ModuleList(cellWidth);
      tabSet2ModuleList_.setValue(toArrayList(userPrefs.panes().getGlobalValue().getTabSet2()));
      hiddenTabSetModuleList_ = new ModuleList(cellWidth);
      hiddenTabSetModuleList_.setValue(toArrayList(
               userPrefs.panes().getGlobalValue().getHiddenTabSet()));


      ValueChangeHandler<ArrayList<Boolean>> vch = new ValueChangeHandler<ArrayList<Boolean>>()
      {
         public void onValueChange(ValueChangeEvent<ArrayList<Boolean>> e)
         {
            dirty_ = true;

            ModuleList source = (ModuleList) e.getSource();
            ModuleList other = (source == tabSet1ModuleList_)
                               ? tabSet2ModuleList_
                               : tabSet1ModuleList_;

            // an index should only be on for one of these lists,
            ArrayList<Boolean> indices = source.getSelectedIndices();
            ArrayList<Boolean> otherIndices = other.getSelectedIndices();
            ArrayList<Boolean> hiddenIndices = hiddenTabSetModuleList_.getSelectedIndices();
            if (!PaneConfig.isValidConfig(source.getValue()))
            {
               // when the configuration is invalid, we must reset sources to the prior valid
               // configuration based on the values of the other two lists
               for (int i = 0; i < indices.size(); i++)
                  indices.set(i, !(otherIndices.get(i) || hiddenIndices.get(i)));
               source.setSelectedIndices(indices);
            }
            else
            {
               for (int i = 0; i < indices.size(); i++)
               {
                  if (indices.get(i))
                  {
                     otherIndices.set(i, false);
                     hiddenIndices.set(i, false);
                  }
                  else if (!otherIndices.get(i))
                     hiddenIndices.set(i, true);
               }
               other.setSelectedIndices(otherIndices);
               hiddenTabSetModuleList_.setSelectedIndices(hiddenIndices);

               updateTabSetLabels();
            }
         }
      };
      tabSet1ModuleList_.addValueChangeHandler(vch);
      tabSet2ModuleList_.addValueChangeHandler(vch);

      updateTabSetPositions();
      updateTabSetLabels();
   }

   private String updateTable()
   {
      // nothing has changed since the last update
      if (grid_ != null && lastKnownAdditionalColumnCount_ == additionalColumnCount_)
         return "";

      int tableWidth = 375;

      // cells will be twice a wide as columns to preserve space (only cells have checkboxes)
      Debug.logToConsole("additionalColumnCount_: " + additionalColumnCount_);
      double columnCount = additionalColumnCount_ + 4;
      Debug.logToConsole("columnCount: "  + columnCount);

      double columnWidthValue = (double)tableWidth / columnCount;
      double cellWidthValue = columnWidthValue * 2;
      Debug.logToConsole("initial cellWidthValue: " + cellWidthValue);
      Debug.logToConsole("initial columnWidthValue: " + columnWidthValue);

      // We never need more than 42 px for a column, so if we have extra, give it back to the cells
      if (Math.min(columnWidthValue, 42) != columnWidthValue)
      {
         double extra = columnWidthValue - 42;
         cellWidthValue += extra / 2;
         columnWidthValue = 42;
      }

      cellWidthValue -= GRID_CELL_SPACING;
      columnWidthValue -= GRID_CELL_SPACING;
      Debug.logToConsole("final cellWidthValue: " + cellWidthValue);
      Debug.logToConsole("final columnWidthValue: " + columnWidthValue);
      String columnWidth = columnWidthValue + "px";
      String cellWidth = cellWidthValue + "px";

      leftTop_.setWidth(cellWidth);
      leftBottom_.setWidth(cellWidth);
      rightTop_.setWidth(cellWidth);
      rightBottom_.setWidth(cellWidth);

      // this is the first time we're setting up the table
      if (grid_ == null)
      {
         grid_ = new FlexTable();
         grid_.addStyleName(res_.styles().newSection());
         grid_.addStyleName(res_.styles().paneLayoutTable());
         grid_.setCellSpacing(GRID_CELL_SPACING);
         grid_.setCellPadding(6);

         // the two rows have different columns because the source columns only use one row
         int topColumn;
         int bottomColumn = 0;
         for (topColumn = 0; topColumn < additionalColumnCount_; topColumn++)
         {
            ScrollPanel sp = createColumn(columnWidth);
            visibleColumns_.add(sp);
            grid_.setWidget(0, topColumn, sp);
            grid_.getFlexCellFormatter().setRowSpan(0, topColumn, 2);
            grid_.getCellFormatter().setStyleName(0, topColumn, res_.styles().column());
            grid_.getColumnFormatter().setWidth(topColumn, columnWidth);
         }

         grid_.setWidget(0, topColumn, leftTopPanel_ = createPane(leftTop_));
         grid_.getCellFormatter().setStyleName(0, topColumn, res_.styles().paneLayoutTable());

         grid_.setWidget(0, ++topColumn, rightTopPanel_ = createPane(rightTop_));
         grid_.getCellFormatter().setStyleName(0, topColumn, res_.styles().paneLayoutTable());

         grid_.setWidget(1, bottomColumn, leftBottomPanel_ = createPane(leftBottom_));
         grid_.getCellFormatter().setStyleName(1, bottomColumn, res_.styles().paneLayoutTable());

         grid_.setWidget(1, ++bottomColumn, rightBottomPanel_ = createPane(rightBottom_));
         grid_.getCellFormatter().setStyleName(1, bottomColumn, res_.styles().paneLayoutTable());

         add(grid_);
         lastKnownAdditionalColumnCount_ = additionalColumnCount_;
         return cellWidth;
      }

      leftTopPanel_.setWidth(cellWidth);
      leftBottomPanel_.setWidth(cellWidth);
      rightTopPanel_.setWidth(cellWidth);
      rightBottomPanel_.setWidth(cellWidth);
      int topColumn = lastKnownAdditionalColumnCount_;

      grid_.getCellFormatter().setWidth(0, topColumn, cellWidth);
      grid_.getCellFormatter().setWidth(1, topColumn, cellWidth);

      grid_.getCellFormatter().setWidth(0, ++topColumn, cellWidth);
      grid_.getCellFormatter().setWidth(1, ++topColumn, cellWidth);

      int difference = additionalColumnCount_ - lastKnownAdditionalColumnCount_;
      lastKnownAdditionalColumnCount_ = additionalColumnCount_;

      // when the number of columns has decreased, remove some
      for (int i = 0; i > difference && !visibleColumns_.isEmpty(); difference++)
      {
         visibleColumns_.remove(0);
         grid_.removeCell(0, i);
      }

      // when the number of columns has increased, add some
      for (int i = 0; i < difference; i++)
      {
         grid_.insertCell(0, 0);

         ScrollPanel sp = createColumn(columnWidth);
         visibleColumns_.add(sp);

         grid_.setWidget(0, 0, sp);
         grid_.getFlexCellFormatter().setRowSpan(0, 0, 2);
         grid_.getCellFormatter().setStyleName(0, 0, res_.styles().column());
      }

      // update the width
      for (int i = 0; i < additionalColumnCount_; i++)
         grid_.getCellFormatter().setWidth(0, i, columnWidth);

      return cellWidth;
   }

   private VerticalPanel createPane(ListBox listBox)
   {
      VerticalPanel vp = new VerticalPanel();
      vp.add(listBox);
      return vp;
   }

   private ScrollPanel createColumn(String width)
   {
      FormLabel label = new FormLabel();
      label.setText(UserPrefsAccessor.Panes.QUADRANTS_SOURCE);
      // !!! Roles.getTextboxRole().setAriaLabelProperty(tb.getElement(), "Additional source
      // column");
      label.setWidth(width);

      ScrollPanel sp = new ScrollPanel();
      sp.add(label);
      sp.setWidth(width);

      return sp;
   }

   private static boolean selectByValue(ListBox listBox, String value)
   {
      for (int i = 0; i < listBox.getItemCount(); i++)
      {
         if (listBox.getValue(i) == value)
         {
            listBox.setSelectedIndex(i);
            return true;
         }
      }

      return false;
   }

   @Override
   public ImageResource getIcon()
   {
      return new ImageResource2x(res_.iconPanes2x());
   }
   
   @Override
   protected void initialize(UserPrefs prefs)
   {
   }

   @Override
   public RestartRequirement onApply(UserPrefs rPrefs)
   {
      RestartRequirement restartRequirement = super.onApply(rPrefs);

      if (dirty_)
      {
         JsArrayString panes = JsArrayString.createArray().cast();
         panes.push(leftTop_.getValue(leftTop_.getSelectedIndex()));
         panes.push(leftBottom_.getValue(leftBottom_.getSelectedIndex()));
         panes.push(rightTop_.getValue(rightTop_.getSelectedIndex()));
         panes.push(rightBottom_.getValue(rightBottom_.getSelectedIndex()));

         JsArrayString tabSet1 = JsArrayString.createArray().cast();
         for (String tab : tabSet1ModuleList_.getValue())
            tabSet1.push(tab);

         JsArrayString tabSet2 = JsArrayString.createArray().cast();
         for (String tab : tabSet2ModuleList_.getValue())
            tabSet2.push(tab);

         JsArrayString hiddenTabSet = JsArrayString.createArray().cast();
         for (String tab : hiddenTabSetModuleList_.getValue())
            hiddenTabSet.push(tab);
         
         // Determine implicit preference for console top/bottom location
         // This needs to be saved so that when the user executes the 
         // Console on Left/Right commands we know whether to position 
         // the Console on the Top or Bottom
         PaneConfig prevConfig = userPrefs_.panes().getGlobalValue().cast();
         boolean consoleLeftOnTop = prevConfig.getConsoleLeftOnTop();
         boolean consoleRightOnTop = prevConfig.getConsoleRightOnTop();
         final String kConsole = "Console";
         if (panes.get(0) == kConsole)
            consoleLeftOnTop = true;
         else if (panes.get(1) == kConsole)
            consoleLeftOnTop = false;
         else if (panes.get(2) == kConsole)
            consoleRightOnTop = true;
         else if (panes.get(3) == kConsole)
            consoleRightOnTop = false;
         
         userPrefs_.panes().setGlobalValue(PaneConfig.create(
               panes, tabSet1, tabSet2, hiddenTabSet,
               consoleLeftOnTop, consoleRightOnTop, additionalColumnCount_));

         dirty_ = false;
      }

      return restartRequirement;
   }

   @Override
   public String getName()
   {
      return "Pane Layout";
   }

   private void updateTabSetPositions()
   {
      for (int i = 0; i < visiblePanes_.length; i++)
      {
         String value = visiblePanes_[i].getValue(visiblePanes_[i].getSelectedIndex());
         if (value == "TabSet1")
            visiblePanePanels_[i].add(tabSet1ModuleList_);
         else if (value == "TabSet2")
            visiblePanePanels_[i].add(tabSet2ModuleList_);
      }
   }

   private void updateTabSetLabels()
   {
      // If no tabs are values in a tabset pane, give the pane a generic name,
      // otherwise the name is created from the selected values 
      String itemText1 = tabSet1ModuleList_.getValue().isEmpty() ?
         "TabSet" : StringUtil.join(tabSet1ModuleList_.getValue(), ", "); 
      String itemText2 = tabSet2ModuleList_.getValue().isEmpty() ?
         "TabSet" : StringUtil.join(tabSet2ModuleList_.getValue(), ", "); 
      if (StringUtil.equals(itemText1, "Presentation") && !tabSet1ModuleList_.presentationVisible())
         itemText1 = "TabSet";

      for (ListBox pane : visiblePanes_)
      {
         pane.setItemText(2, itemText1);
         pane.setItemText(3, itemText2);
      }
   }

   private ArrayList<String> toArrayList(JsArrayString strings)
   {
      ArrayList<String> results = new ArrayList<>();
      for (int i = 0; strings != null && i < strings.length(); i++)
         results.add(strings.get(i));
      return results;
   }

   private final PreferencesDialogResources res_;
   private final UserPrefs userPrefs_;
   private final ImageButton addSource_;
   private final ListBox leftTop_;
   private final ListBox leftBottom_;
   private final ListBox rightTop_;
   private final ListBox rightBottom_;
   private final ListBox[] visiblePanes_;
   private final List<ScrollPanel> visibleColumns_ = new ArrayList<>();
   private final VerticalPanel[] visiblePanePanels_;
   private final ModuleList tabSet1ModuleList_;
   private final ModuleList tabSet2ModuleList_;
   private final ModuleList hiddenTabSetModuleList_;
   private boolean dirty_ = false;

   private VerticalPanel leftTopPanel_;
   private VerticalPanel leftBottomPanel_;
   private VerticalPanel rightTopPanel_;
   private VerticalPanel rightBottomPanel_;

   private PaneManager paneManager_;
   private int additionalColumnCount_;
   private int lastKnownAdditionalColumnCount_ = 0;
   private FlexTable grid_;

   private final static int GRID_CELL_SPACING = 8;
}
