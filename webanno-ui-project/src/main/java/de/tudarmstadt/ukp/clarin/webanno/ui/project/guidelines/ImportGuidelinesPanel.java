/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.ui.project.guidelines;

import static org.apache.commons.collections.CollectionUtils.isEmpty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import de.tudarmstadt.ukp.clarin.webanno.api.ImportExportService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxButton;
import de.tudarmstadt.ukp.clarin.webanno.support.wicket.WicketUtil;

public class ImportGuidelinesPanel
    extends Panel
{
    private static final long serialVersionUID = 3503932088128261675L;

    private @SpringBean ProjectService projectRepository;
    private @SpringBean ImportExportService importExportService;

    private FileUploadField fileUpload;

    private IModel<Project> projectModel;

    public ImportGuidelinesPanel(String aId, IModel<Project> aProject)
    {
        super(aId);

        projectModel = aProject;

        Form<Void> form = new Form<>("form");
        add(form);

        form.add(fileUpload = new FileUploadField("guidelines"));

        form.add(new LambdaAjaxButton<>("import", this::actionImport));
    }

    private void actionImport(AjaxRequestTarget aTarget, Form<Void> aForm)
    {
        List<FileUpload> uploadedFiles = fileUpload.getFileUploads();
        Project project = projectModel.getObject();

        if (project.getId() == 0) {
            error("Project not yet created, please save project Details!");
            return;
        }
        if (isEmpty(uploadedFiles)) {
            error("No document is selected to upload, please select a document first");
            return;
        }

        for (FileUpload guidelineFile : uploadedFiles) {
            try {
                // Workaround for WICKET-6425
                File tempFile = File.createTempFile("webanno-guidelines", null);
                try (InputStream is = guidelineFile.getInputStream();
                        OutputStream os = new FileOutputStream(tempFile);) {
                    IOUtils.copyLarge(is, os);

                    String fileName = guidelineFile.getClientFileName();
                    projectRepository.createGuideline(project, tempFile, fileName);
                }
                finally {
                    tempFile.delete();
                }
            }
            catch (Exception e) {
                error("Unable to write guideline file " + ExceptionUtils.getRootCauseMessage(e));
            }
        }

        //aTarget.addChildren(getPage(), IFeedback.class);
        WicketUtil.refreshPage(aTarget, getPage());
    }
}
