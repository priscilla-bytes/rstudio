/*
 * PythonPreferencesPane.java
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

import org.rstudio.core.client.resources.ImageResource2x;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefs;

import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.Label;
import com.google.inject.Inject;

public class PythonPreferencesPane extends PreferencesPane
{
   @Inject
   public PythonPreferencesPane(PythonDialogResources res)
   {
      res_ = res;
      
      Label placeholder = new Label("Placeholder");
      add(placeholder);
   }

   @Override
   public ImageResource getIcon()
   {
      return new ImageResource2x(res_.iconPython2x());
   }

   @Override
   public String getName()
   {
      return "Python";
   }

   @Override
   protected void initialize(UserPrefs prefs)
   {
      // TODO Auto-generated method stub
      
   }
   
   private final PythonDialogResources res_;

}
