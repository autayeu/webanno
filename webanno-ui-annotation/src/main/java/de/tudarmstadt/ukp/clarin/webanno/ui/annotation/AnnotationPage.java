/*
 * Copyright 2012
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
package de.tudarmstadt.ukp.clarin.webanno.ui.annotation;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectByAddr;
import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.NoResultException;

import org.apache.uima.jcas.JCas;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.wicketstuff.annotation.mount.MountPath;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.api.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorStateImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.api.dao.SecurityUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotator;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentStateTransition;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentStateTransition;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.dialog.ConfirmationDialog;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxLink;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxSubmitLink;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.AnnotationPreferencesModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.DocumentNamePanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.ExportModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.FinishImage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.GuidelineModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.detail.AnnotationDetailEditorPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.dialog.OpenDocumentDialog;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import wicket.contrib.input.events.EventType;
import wicket.contrib.input.events.InputBehavior;
import wicket.contrib.input.events.key.KeyType;

/**
 * A wicket page for the Brat Annotation/Visualization page. Included components for pagination,
 * annotation layer configuration, and Exporting document
 */
@MountPath(value = "/annotation.html", alt = "/annotate/${" + AnnotationPage.PAGE_PARAM_PROJECT_ID + "}/${"
        + AnnotationPage.PAGE_PARAM_DOCUMENT_ID + "}")
public class AnnotationPage
    extends AnnotationPageBase
{
    private static final Logger LOG = LoggerFactory.getLogger(AnnotationPage.class);

    private static final long serialVersionUID = 1378872465851908515L;
    
    public static final String PAGE_PARAM_PROJECT_ID = "projectId";
    public static final String PAGE_PARAM_DOCUMENT_ID = "documentId";

    @SpringBean(name = "documentRepository")
    private RepositoryService repository;

    @SpringBean(name = "annotationService")
    private AnnotationService annotationService;

    @SpringBean(name = "userRepository")
    private UserDao userRepository;

    private NumberTextField<Integer> gotoPageTextField;
    private Label numberOfPages;
    
    private long currentprojectId;

    // Open the dialog window on first load
    private boolean firstLoad = true;

    private ModalWindow openDocumentsModal;

    private FinishImage finishDocumentIcon;
    private ConfirmationDialog finishDocumentDialog;
    private LambdaAjaxLink finishDocumentLink;
    
    private BratAnnotator annotationEditor;
    private AnnotationDetailEditorPanel detailEditor;    

    public AnnotationPage()
    {
        super();
        LOG.debug("Setting up annotation page without parameters");
        commonInit();
    }
    
    public AnnotationPage(final PageParameters aPageParameters)
    {
        super(aPageParameters);
        LOG.debug("Setting up annotation page with parameters: {}", aPageParameters);

        commonInit();

        long projectId = aPageParameters.get("projectId").toLong();
        Project project;
        try {
            project = repository.getProject(projectId);
        }
        catch (NoResultException e) {
            error("Project [" + projectId + "] does not exist");
            return;
        }
       
        long documentId = aPageParameters.get("documentId").toLong();
        SourceDocument document;
        try {
            document = repository.getSourceDocument(projectId, documentId);
        }
        catch (NoResultException e) {
            error("Document [" + documentId + "] does not exist in project [" + projectId + "]");
            return;
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.get(username);
        
        if (!SecurityUtil.isAnnotator(project, repository, user)) {
            error("You have no permission to access document [" + documentId + "] in project ["
                    + projectId + "]");
            return;
        }

        firstLoad = false;
        
        getModelObject().setUser(user);
        getModelObject().setProject(project);
        getModelObject().setDocument(document, getListOfDocs());

        actionLoadDocument(null);
    }
    
    private void commonInit()
    {
        setVersioned(false);
        
        setModel(Model.of(new AnnotatorStateImpl(Mode.ANNOTATION)));
        
        WebMarkupContainer sidebarCell = new WebMarkupContainer("sidebarCell") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onComponentTag(ComponentTag aTag)
            {
                super.onComponentTag(aTag);
                AnnotatorState state = AnnotationPage.this.getModelObject();
                aTag.put("width", state.getPreferences().getSidebarSize()+"%");
            }
        };
        sidebarCell.setOutputMarkupId(true);
        add(sidebarCell);

        WebMarkupContainer annotationViewCell = new WebMarkupContainer("annotationViewCell") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onComponentTag(ComponentTag aTag)
            {
                super.onComponentTag(aTag);
                AnnotatorState state = AnnotationPage.this.getModelObject();
                aTag.put("width", (100-state.getPreferences().getSidebarSize())+"%");
            }
        };
        annotationViewCell.setOutputMarkupId(true);
        add(annotationViewCell);

        sidebarCell.add(detailEditor = createDetailEditor());
        
        annotationEditor = new BratAnnotator("embedder1", getModel(), detailEditor,
                () -> { return getEditorCas(); });
        annotationViewCell.add(annotationEditor);

        add(createDocumentInfoLabel());

        add(numberOfPages = createPositionInfoLabel());

        add(openDocumentsModal = new OpenDocumentDialog("openDocumentsModal", getModel()) {
            private static final long serialVersionUID = 5474030848589262638L;

            @Override
            public void onDocumentSelected(AjaxRequestTarget aTarget)
            {
                //actionLoadDocument(aTarget);
                PageParameters pageParameters = new PageParameters();
                pageParameters.set(PAGE_PARAM_PROJECT_ID, getModelObject().getProject().getId());
                pageParameters.set(PAGE_PARAM_DOCUMENT_ID, getModelObject().getDocument().getId());
                setResponsePage(AnnotationPage.class, pageParameters);
            }
        });

        add(new AnnotationPreferencesModalPanel("annotationLayersModalPanel", getModel(), detailEditor)
        {
            private static final long serialVersionUID = -4657965743173979437L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                actionCompletePreferencesChange(aTarget);
            }
        });

        add(new ExportModalPanel("exportModalPanel", getModel()){
            private static final long serialVersionUID = -468896211970839443L;

            {
                setOutputMarkupId(true);
                setOutputMarkupPlaceholderTag(true);
            }

            @Override
            protected void onConfigure()
            {
                super.onConfigure();
                AnnotatorState state = AnnotationPage.this.getModelObject();
                setVisible(state.getProject() != null
                        && (SecurityUtil.isAdmin(state.getProject(), repository, state.getUser())
                                || !state.getProject().isDisableExport()));
            }
        });

        Form<Void> gotoPageTextFieldForm = new Form<Void>("gotoPageTextFieldForm");
        gotoPageTextField = new NumberTextField<Integer>("gotoPageText", Model.of(1), Integer.class);
        // FIXME minimum and maximum should be obtained from the annotator state
        gotoPageTextField.setMinimum(1); 
        gotoPageTextField.setOutputMarkupId(true); 
        gotoPageTextFieldForm.add(gotoPageTextField);
        gotoPageTextFieldForm.add(new LambdaAjaxSubmitLink("gotoPageLink", gotoPageTextFieldForm,
                this::actionGotoPage));
        add(gotoPageTextFieldForm);

        add(new LambdaAjaxLink("showOpenDocumentModal", this::actionShowOpenDocumentDialog));
        
        add(new LambdaAjaxLink("showPreviousDocument", this::actionShowPreviousDocument)
                .add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_up },
                        EventType.click)));

        add(new LambdaAjaxLink("showNextDocument", this::actionShowNextDocument)
                .add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_down },
                        EventType.click)));

        add(new LambdaAjaxLink("showNext", this::actionShowNextPage)
                .add(new InputBehavior(new KeyType[] { KeyType.Page_down }, EventType.click)));

        add(new LambdaAjaxLink("showPrevious", this::actionShowPreviousPage)
                .add(new InputBehavior(new KeyType[] { KeyType.Page_up }, EventType.click)));

        add(new LambdaAjaxLink("showFirst", this::actionShowFirstPage)
                .add(new InputBehavior(new KeyType[] { KeyType.Home }, EventType.click)));

        add(new LambdaAjaxLink("showLast", this::actionShowLastPage)
                .add(new InputBehavior(new KeyType[] { KeyType.End }, EventType.click)));

        add(new LambdaAjaxLink("toggleScriptDirection", this::actionToggleScriptDirection));
        
        add(new GuidelineModalPanel("guidelineModalPanel", getModel()));
        
        add(createOrGetResetDocumentDialog());
        add(createOrGetResetDocumentLink());
        
        add(finishDocumentDialog = new ConfirmationDialog("finishDocumentDialog",
                new StringResourceModel("FinishDocumentDialog.title", this, null),
                new StringResourceModel("FinishDocumentDialog.text", this, null)));
        add(finishDocumentLink = new LambdaAjaxLink("showFinishDocumentDialog",
                this::actionFinishDocument)
        {
            private static final long serialVersionUID = 874573384012299998L;

            @Override
            protected void onConfigure()
            {
                super.onConfigure();
                AnnotatorState state = AnnotationPage.this.getModelObject();
                setEnabled(state.getDocument() != null
                        && !repository.isAnnotationFinished(state.getDocument(), state.getUser()));
            }
        });
        finishDocumentIcon = new FinishImage("finishImage", getModel());
        finishDocumentIcon.setOutputMarkupId(true);
        finishDocumentLink.add(finishDocumentIcon);
    }
    
    private DocumentNamePanel createDocumentInfoLabel()
    {
        return new DocumentNamePanel("documentNamePanel", getModel());
    }

    private AnnotationDetailEditorPanel createDetailEditor()
    {
        return new AnnotationDetailEditorPanel("annotationDetailEditorPanel", getModel())
        {
            private static final long serialVersionUID = 2857345299480098279L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                aTarget.addChildren(getPage(), FeedbackPanel.class);
                aTarget.add(numberOfPages);

                try {
                    annotationEditor.bratRender(aTarget, getEditorCas());
                    annotationEditor.bratSetHighlight(aTarget,
                            getModelObject().getSelection().getAnnotation());
                }
                catch (Exception e) {
                    LOG.info("Error reading CAS: {} " + e.getMessage(), e);
                    error("Error reading CAS: " + e.getMessage());
                }
            }

            @Override
            protected void onAutoForward(AjaxRequestTarget aTarget)
            {
                try {
                    annotationEditor.bratRender(aTarget, getEditorCas());
                }
                catch (Exception e) {
                    LOG.info("Error reading CAS: {} " + e.getMessage(), e);
                    error("Error reading CAS " + e.getMessage());
                    return;
                }
            }
        };
    }

    private Label createPositionInfoLabel()
    {
        return new Label("numberOfPages", new StringResourceModel("PositionInfo.text", 
                this, getModel(), 
                PropertyModel.of(getModel(), "firstVisibleSentenceNumber"),
                PropertyModel.of(getModel(), "lastVisibleSentenceNumber"),
                PropertyModel.of(getModel(), "numberOfSentences"),
                PropertyModel.of(getModel(), "documentIndex"),
                PropertyModel.of(getModel(), "numberOfDocuments"))) {
            private static final long serialVersionUID = 7176610419683776917L;

            {
                setOutputMarkupId(true);
                setOutputMarkupPlaceholderTag(true);
            }
            
            @Override
            protected void onConfigure()
            {
                super.onConfigure();
                setVisible(getModelObject().getDocument() != null);
            }
        };
    }

    @Override
    protected List<SourceDocument> getListOfDocs()
    {
        AnnotatorState state = getModelObject();
        return new ArrayList<>(
                repository.listAnnotatableDocuments(state.getProject(), state.getUser()).keySet());
    }

    /**
     * for the first time, open the <b>open document dialog</b>
     */
    @Override
    public void renderHead(IHeaderResponse response)
    {
        super.renderHead(response);

        String jQueryString = "";
        if (firstLoad) {
            jQueryString += "jQuery('#showOpenDocumentModal').trigger('click');";
            firstLoad = false;
        }
        response.render(OnLoadHeaderItem.forScript(jQueryString));
    }

    @Override
    protected JCas getEditorCas()
        throws IOException
    {
        AnnotatorState state = getModelObject();

        if (state.getDocument() == null) {
            throw new IllegalStateException("Please open a document first!");
        }
        
        SourceDocument aDocument = getModelObject().getDocument();

        AnnotationDocument annotationDocument = repository.getAnnotationDocument(aDocument,
                state.getUser());

        // If there is no CAS yet for the annotation document, create one.
        return repository.readAnnotationCas(annotationDocument);
    }

    private void actionShowOpenDocumentDialog(AjaxRequestTarget aTarget)
    {
        getModelObject().getSelection().clear();
        openDocumentsModal.show(aTarget);
    }

    private void actionGotoPage(AjaxRequestTarget aTarget, Form<?> aForm)
        throws Exception
    {
        AnnotatorState state = getModelObject();
        
        JCas jcas = getEditorCas();
        List<Sentence> sentences = new ArrayList<>(select(jcas, Sentence.class));
        int selectedSentence = gotoPageTextField.getModelObject();
        selectedSentence = Math.min(selectedSentence, sentences.size());
        gotoPageTextField.setModelObject(selectedSentence);
        
        state.setFirstVisibleSentence(sentences.get(selectedSentence - 1));
        state.setFocusSentenceNumber(selectedSentence);        
        
        actionRefreshDocument(aTarget, jcas);
    }

    private void actionToggleScriptDirection(AjaxRequestTarget aTarget)
            throws Exception
    {
        getModelObject().toggleScriptDirection();
        annotationEditor.bratRenderLater(aTarget);
    }
    
    private void actionCompletePreferencesChange(AjaxRequestTarget aTarget)
    {
        try {
            AnnotatorState state = getModelObject();
            
            JCas jCas = getEditorCas();
            
            // The number of visible sentences may have changed - let the state recalculate 
            // the visible sentences 
            Sentence sentence = selectByAddr(jCas, Sentence.class,
                    state.getFirstVisibleSentenceAddress());
            state.setFirstVisibleSentence(sentence);
            
            // Re-render the whole page because the width of the sidebar may have changed
            aTarget.add(AnnotationPage.this);
        }
        catch (Exception e) {
            LOG.info("Error reading CAS " + e.getMessage());
            error("Error reading CAS " + e.getMessage());
            return;
        }
    }
    
    private void actionFinishDocument(AjaxRequestTarget aTarget)
    {
        finishDocumentDialog.setConfirmAction((target) -> {
            AnnotatorState state = getModelObject();
            AnnotationDocument annotationDocument = repository.getAnnotationDocument(
                    state.getDocument(), state.getUser());

            annotationDocument.setState(AnnotationDocumentStateTransition.transition(
                    AnnotationDocumentStateTransition.ANNOTATION_IN_PROGRESS_TO_ANNOTATION_FINISHED));
            
            // manually update state change!! No idea why it is not updated in the DB
            // without calling createAnnotationDocument(...)
            repository.createAnnotationDocument(annotationDocument);
            
            target.add(finishDocumentIcon);
            target.add(finishDocumentLink);
            target.add(detailEditor);
            target.add(createOrGetResetDocumentLink());
        });
        finishDocumentDialog.show(aTarget);
    }

    @Override
    protected void actionLoadDocument(AjaxRequestTarget aTarget)
    {
        LOG.info("BEGIN LOAD_DOCUMENT_ACTION");

        AnnotatorState state = getModelObject();
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.get(username);

        state.setUser(user);

        try {
            // Check if there is an annotation document entry in the database. If there is none,
            // create one.
            AnnotationDocument annotationDocument = repository
                    .createOrGetAnnotationDocument(state.getDocument(), user);

            // Read the CAS
            JCas editorCas = repository.readAnnotationCas(annotationDocument);

            // Update the annotation document CAS
            repository.upgradeCas(editorCas.getCas(), annotationDocument);

            // After creating an new CAS or upgrading the CAS, we need to save it
            repository.writeAnnotationCas(editorCas.getCas().getJCas(),
                    annotationDocument.getDocument(), user);

            // (Re)initialize brat model after potential creating / upgrading CAS
            state.clearAllSelections();

            // Load constraints
            state.setConstraints(repository.loadConstraints(state.getProject()));

            // Load user preferences
            PreferencesUtil.loadPreferences(username, repository, annotationService, state,
                    state.getMode());

            // Initialize the visible content
            state.setFirstVisibleSentence(WebAnnoCasUtil.getFirstSentence(editorCas));
            
            // if project is changed, reset some project specific settings
            if (currentprojectId != state.getProject().getId()) {
                state.clearRememberedFeatures();
            }

            currentprojectId = state.getProject().getId();

            LOG.debug("Configured BratAnnotatorModel for user [" + state.getUser() + "] f:["
                    + state.getFirstVisibleSentenceNumber() + "] l:["
                    + state.getLastVisibleSentenceNumber() + "] s:["
                    + state.getFocusSentenceNumber() + "]");

            gotoPageTextField.setModelObject(1);

            // Re-render the whole page because the font size
            if (aTarget != null) {
                aTarget.add(AnnotationPage.this);
            }

            // Update document state
            if (state.getDocument().getState().equals(SourceDocumentState.NEW)) {
                state.getDocument().setState(SourceDocumentStateTransition
                        .transition(SourceDocumentStateTransition.NEW_TO_ANNOTATION_IN_PROGRESS));
                repository.createSourceDocument(state.getDocument());
            }
            
            // Reset the editor
            detailEditor.reset(aTarget);
            // Populate the layer dropdown box
            detailEditor.loadFeatureEditorModels(editorCas, aTarget);
        }
        catch (Exception e) {
            handleException(aTarget, e);
        }

        LOG.info("END LOAD_DOCUMENT_ACTION");
    }
    
    @Override
    protected void actionRefreshDocument(AjaxRequestTarget aTarget, JCas aEditorCas)
    {
        annotationEditor.bratRender(aTarget, aEditorCas);
        gotoPageTextField.setModelObject(getModelObject().getFirstVisibleSentenceNumber());
        aTarget.add(gotoPageTextField);
        aTarget.add(numberOfPages);
    }
}