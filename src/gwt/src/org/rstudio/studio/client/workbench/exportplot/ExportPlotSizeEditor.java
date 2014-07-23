/*
 * ExportPlotSizeEditor.java
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
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
package org.rstudio.studio.client.workbench.exportplot;

import org.rstudio.core.client.Rectangle;
import org.rstudio.core.client.Size;
import org.rstudio.core.client.widget.*;

import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.CellPanel;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

public class ExportPlotSizeEditor extends Composite 
{  
   public interface Observer
   {
      void onResized(boolean withMouse);
   }
   
   public ExportPlotSizeEditor(int initialWidth, 
                               int initialHeight,
                               boolean keepRatio,
                               ExportPlotPreviewer previewer,
                               Observer observer)
   {
      this(initialWidth, initialHeight, keepRatio, null, previewer, observer);
   }
   
   public ExportPlotSizeEditor(int initialWidth, 
                               int initialHeight,
                               boolean keepRatio,
                               Widget extraWidget,
                               ExportPlotPreviewer previewer,
                               final Observer observer)
   {
      // alias objects and resources
      previewer_ = previewer;
      ExportPlotResources resources = ExportPlotResources.INSTANCE;
           
      // main widget
      VerticalPanel verticalPanel = new VerticalPanel();
           
      // if we have an extra widget then enclose it within a horizontal
      // panel with it on the left and the options on the right
      HorizontalPanel topPanel = new HorizontalPanel();
      CellPanel optionsPanel = null;
      HorizontalPanel widthAndHeightPanel = null;
      if (extraWidget != null)
      {
         topPanel.setWidth("100%");
         
         topPanel.add(extraWidget);
         topPanel.setCellHorizontalAlignment(extraWidget, 
                                             HasHorizontalAlignment.ALIGN_LEFT);
         
         optionsPanel = new VerticalPanel();
         optionsPanel.setStylePrimaryName(
                                    resources.styles().verticalSizeOptions());
         optionsPanel.setSpacing(0);
         topPanel.add(optionsPanel);
         topPanel.setCellHorizontalAlignment(
                                       optionsPanel,
                                       HasHorizontalAlignment.ALIGN_RIGHT);
         
         widthAndHeightPanel = new HorizontalPanel();
         widthAndHeightPanel.setStylePrimaryName(
                                    resources.styles().widthAndHeightEntry());
         configureHorizontalOptionsPanel(widthAndHeightPanel);
         optionsPanel.add(widthAndHeightPanel);
      }
      else
      {
         optionsPanel = topPanel ;
         optionsPanel.setStylePrimaryName(
                                 resources.styles().horizontalSizeOptions());
         widthAndHeightPanel = topPanel;
         configureHorizontalOptionsPanel(topPanel);  
      }
          
      // image width
      widthAndHeightPanel.add(createImageOptionLabel("Width:"));
      widthTextBox_ = createImageSizeTextBox();
      widthTextBox_.addChangeHandler(new ChangeHandler() {
         @Override
         public void onChange(ChangeEvent event)
         {
            // screen out programmatic sets
            if (settingDimenensionInProgress_)
               return;
            
            // enforce min size
            int width = constrainWidth(getImageWidth());
           
            // preserve aspect ratio if requested
            if (getKeepRatio())
            {  
               double ratio = (double)lastHeight_ / (double)lastWidth_;
               int height = constrainHeight((int) (ratio * (double)width));
               setHeightTextBox(height);
            }
  
            // set width
            setWidthTextBox(width);
         }
         
      });
      widthAndHeightPanel.add(widthTextBox_);
     
      // image height
      widthAndHeightPanel.add(new HTML("&nbsp;&nbsp;"));
      widthAndHeightPanel.add(createImageOptionLabel("Height:"));
      heightTextBox_ = createImageSizeTextBox();
      heightTextBox_.addChangeHandler(new ChangeHandler() {
         @Override
         public void onChange(ChangeEvent event)
         {
            // screen out programmatic sets
            if (settingDimenensionInProgress_)
               return;
            
            // enforce min size
            int height = constrainHeight(getImageHeight());
            
            // preserve aspect ratio if requested
            if (getKeepRatio())
            {
               double ratio = (double)lastWidth_ / (double)lastHeight_;
               int width = constrainWidth((int) (ratio * (double)height));
               setWidthTextBox(width);
            }
           
            // always set height
            setHeightTextBox(height);
         }
         
      });
      widthAndHeightPanel.add(heightTextBox_);
      
      // add width and height panel to options panel container if necessary
      if (widthAndHeightPanel != optionsPanel)
         optionsPanel.add(widthAndHeightPanel);
  
      // lock ratio check box
      keepRatioCheckBox_ = new CheckBox();
      keepRatioCheckBox_.setStylePrimaryName(
                           resources.styles().maintainAspectRatioCheckBox());
      keepRatioCheckBox_.setValue(keepRatio);
      keepRatioCheckBox_.setText("Maintain aspect ratio");
      optionsPanel.add(keepRatioCheckBox_);
      
      // image and sizer in layout panel (create now so we can call
      // setSize in update button click handler)
      previewPanel_ = new LayoutPanel(); 
     
      
      // update button
      ThemedButton updateButton = new ThemedButton("Update Preview", 
                                                    new ClickHandler(){
         public void onClick(ClickEvent event) 
         {
            setPreviewPanelSize(getImageWidth(), getImageHeight());         
            previewer_.updatePreview(getImageWidth(), getImageHeight());
            observer.onResized(false);
         }
      });
      updateButton.setStylePrimaryName(
                                 resources.styles().updateImageSizeButton());
      optionsPanel.add(updateButton);

      // add top panel
      verticalPanel.add(topPanel);

      // previewer
      Widget previewWidget = previewer_.getWidget();
     
      // Stops mouse events from being routed to the iframe, which would
      // interfere with resizing
      final GlassPanel glassPanel = new GlassPanel(previewWidget);
      glassPanel.getChildContainerElement().getStyle().setOverflow(
                                                            Overflow.VISIBLE);
      glassPanel.setSize("100%", "100%");
      
      

      previewPanel_.add(glassPanel);
      previewPanel_.setWidgetLeftRight(glassPanel,
                                      0, Unit.PX, 
                                      IMAGE_INSET, Unit.PX);
      previewPanel_.setWidgetTopBottom(glassPanel,
                                      0, Unit.PX, 
                                      IMAGE_INSET, Unit.PX);
      previewPanel_.getWidgetContainerElement(
                     glassPanel).getStyle().setOverflow(Overflow.VISIBLE);
      
      // resize gripper
      ResizeGripper gripper = new ResizeGripper(new ResizeGripper.Observer() 
      {
         @Override
         public void onResizingStarted()
         {    
            int startWidth = getImageWidth();
            int startHeight = getImageHeight();
            
            widthAspectRatio_ = (double)startWidth / (double)startHeight;
            heightAspectRatio_ = (double)startHeight / (double)startWidth;

            glassPanel.setGlass(true);
         }
         
         @Override
         public void onResizing(int xDelta, int yDelta)
         {
            // get start width and height
            int startWidth = getImageWidth();
            int startHeight = getImageHeight();
            
            // calculate new height and width 
            int newWidth = constrainWidth(startWidth + xDelta);
            int newHeight = constrainHeight(startHeight + yDelta);
            
            // preserve aspect ratio if requested
            if (getKeepRatio())
            {
               if (Math.abs(xDelta) > Math.abs(yDelta))
                  newHeight = (int) (heightAspectRatio_ * (double)newWidth);
               else
                  newWidth = (int) (widthAspectRatio_ * (double)newHeight);
            }
            
            // set text boxes
            setWidthTextBox(newWidth);
            setHeightTextBox(newHeight);  
            
            // set image preview size
            setPreviewPanelSize(newWidth,  newHeight);
         }

         @Override
         public void onResizingCompleted()
         {
            glassPanel.setGlass(false);
            previewer_.updatePreview(getImageWidth(), getImageHeight());
            observer.onResized(true);
         } 
         
         private double widthAspectRatio_ = 1.0;
         private double heightAspectRatio_ = 1.0;
      });
      
      // layout gripper
      previewPanel_.add(gripper);
      previewPanel_.setWidgetRightWidth(gripper, 
                                      0, Unit.PX, 
                                      gripper.getImageWidth(), Unit.PX);
      previewPanel_.setWidgetBottomHeight(gripper, 
                                        0, Unit.PX, 
                                        gripper.getImageHeight(), Unit.PX);
     
      // constrain dimensions
      initialWidth = constrainWidth(initialWidth);
      initialHeight = constrainHeight(initialHeight);
            
      // initialie text boxes
      setWidthTextBox(initialWidth);
      setHeightTextBox(initialHeight);
 
      // initialize preview
      setPreviewPanelSize(initialWidth, initialHeight);
     
      verticalPanel.add(previewPanel_);
      
      // set initial focus widget
      if (extraWidget == null)
         initialFocusWidget_ = widthTextBox_;
      else
         initialFocusWidget_ = null;
     
      initWidget(verticalPanel);
     
   }
 
   public void onSizerShown()
   {  
      previewer_.updatePreview(getImageWidth(), getImageHeight());
      
      if (initialFocusWidget_ != null)
         FocusHelper.setFocusDeferred(initialFocusWidget_);
   }
  
  
   public int getImageWidth()
   {
      try
      {
         return Integer.parseInt(widthTextBox_.getText().trim());
      }
      catch(NumberFormatException ex)
      {
         setWidthTextBox(lastWidth_);
         return lastWidth_;
      }
   }
      
   public int getImageHeight()
   {
      try
      {
         return Integer.parseInt(heightTextBox_.getText().trim());
      }
      catch(NumberFormatException ex)
      {
         setHeightTextBox(lastHeight_);
         return lastHeight_;
      }
   } 
   
   public boolean getKeepRatio()
   {
      return keepRatioCheckBox_.getValue();
   }
   
   public Rectangle getImageClientRect()
   {
      return previewer_.getPreviewClientRect();
   }
   
   private void setWidthTextBox(int width)
   {
      settingDimenensionInProgress_ = true;
      lastWidth_ = width;
      widthTextBox_.setText(Integer.toString(width));
      settingDimenensionInProgress_ = false;
   }
   
   
   private void setHeightTextBox(int height)
   {
      settingDimenensionInProgress_ = true;
      lastHeight_ = height;
      heightTextBox_.setText(Integer.toString(height));
      settingDimenensionInProgress_ = false;
   }
   
   private int constrainWidth(int width)
   {
      if (width < MIN_SIZE)
      {
         keepRatioCheckBox_.setValue(false);
         return MIN_SIZE;
      }
      else
      {
         return width;
      }
   }
   
   private int constrainHeight(int height)
   {
      if (height < MIN_SIZE)
      {
         keepRatioCheckBox_.setValue(false);
         return MIN_SIZE;
      }
      else
      {
         return height;
      }
   }
   
   private void setPreviewPanelSize(int width, int height)
   {
      Size maxSize = new Size(Window.getClientWidth() - 100,
                              Window.getClientHeight() - 200);
      
      if (width <= maxSize.width && height <= maxSize.height)
      {
         previewPanel_.setVisible(true);
         previewPanel_.setSize((width + IMAGE_INSET) + "px", 
                               (height + IMAGE_INSET) + "px");
      }
      else
      {
         previewPanel_.setVisible(false);
      }
   }
   
   private Label createImageOptionLabel(String text)
   {
      Label label = new Label(text);
      label.setStylePrimaryName(
            ExportPlotResources.INSTANCE.styles().imageOptionLabel());
      return label;
   }
   
   private TextBox createImageSizeTextBox()
   {
      TextBox textBox = new TextBox();
      textBox.setStylePrimaryName(
            ExportPlotResources.INSTANCE.styles().imageSizeTextBox());
      return textBox;
   }
  
   
   private void configureHorizontalOptionsPanel(HorizontalPanel panel)
   {
      panel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
      panel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
   }
   
   private static final int IMAGE_INSET = 6;
   
   private final ExportPlotPreviewer previewer_;
   private final TextBox widthTextBox_;
   private final TextBox heightTextBox_;
   private final CheckBox keepRatioCheckBox_;
   
   private final Focusable initialFocusWidget_;
     
   private int lastWidth_;
   private int lastHeight_ ;
  
   private boolean settingDimenensionInProgress_ = false;
   
   private final int MIN_SIZE = 100;
   private LayoutPanel previewPanel_;
}
