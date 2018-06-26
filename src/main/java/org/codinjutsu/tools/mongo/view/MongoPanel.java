/*
 * Copyright (c) 2016 David Boissier.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codinjutsu.tools.mongo.view;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.NumberDocument;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.mongodb.DBRef;
import org.bson.Document;
import org.codinjutsu.tools.mongo.ServerConfiguration;
import org.codinjutsu.tools.mongo.logic.MongoManager;
import org.codinjutsu.tools.mongo.logic.Notifier;
import org.codinjutsu.tools.mongo.model.MongoCollection;
import org.codinjutsu.tools.mongo.model.MongoCollectionResult;
import org.codinjutsu.tools.mongo.model.MongoQueryOptions;
import org.codinjutsu.tools.mongo.utils.GuiUtils;
import org.codinjutsu.tools.mongo.view.action.*;
import org.codinjutsu.tools.mongo.view.model.navigation.Navigation;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MongoPanel extends JPanel implements Disposable {

    private final LoadingDecorator loadingDecorator;
    private JPanel rootPanel;
    private Splitter splitter;
    private JPanel toolBar;
    private JPanel errorPanel;
    private final JTextField rowLimitField = new JTextField(Integer.toString(MongoQueryOptions.DEFAULT_RESULT_LIMIT));
    private final MongoResultPanel resultPanel;
    private final QueryPanel queryPanel;

    private final MongoManager mongoManager;
    private final ServerConfiguration configuration;
    private final Navigation navigation;
    private MongoCollectionResult currentResults;

    public MongoPanel(Project project, final MongoManager mongoManager, final ServerConfiguration configuration, final Navigation navigation) {
        this.mongoManager = mongoManager;
        this.navigation = navigation;
        this.configuration = configuration;
        this.currentResults = new MongoCollectionResult(navigation.getCurrentWayPoint().getLabel());

        errorPanel.setLayout(new BorderLayout());

        queryPanel = new QueryPanel(project);
        queryPanel.setVisible(false);

        resultPanel = createResultPanel(project, Notifier.getInstance(project));

        loadingDecorator = new LoadingDecorator(resultPanel, this, 0);
        splitter.setOrientation(true);
        splitter.setProportion(0.2f);
        splitter.setSecondComponent(loadingDecorator.getComponent());

        setLayout(new BorderLayout());
        add(rootPanel);

        initToolBar();
    }

    private MongoResultPanel createResultPanel(Project project, Notifier notifier) {
        return new MongoResultPanel(project, new MongoDocumentOperations() {

            public Document getMongoDocument(Object _id) {
                return mongoManager.findMongoDocument(configuration, navigation.getCurrentWayPoint().getCollection(), _id);
            }

            public void updateMongoDocument(Document mongoDocument) {
                mongoManager.update(configuration, navigation.getCurrentWayPoint().getCollection(), mongoDocument);
                executeQuery();
            }

            @Override
            public Document getReferenceDocument(String collection, Object _id, String database) {
                return mongoManager.findMongoDocument(
                        configuration,
                        new MongoCollection(collection, database != null ? database : navigation.getCurrentWayPoint().getCollection().getDatabaseName()),
                        _id);
            }

            public void deleteMongoDocument(Object objectId) {
                mongoManager.delete(configuration, navigation.getCurrentWayPoint().getCollection(), objectId);
                executeQuery();
            }
        }, notifier);
    }

    private void initToolBar() {
        toolBar.setLayout(new BorderLayout());

        JPanel rowLimitPanel = createRowLimitComponent();
        toolBar.add(rowLimitPanel, BorderLayout.WEST);

        JComponent actionToolBarComponent = createResultActionsComponent();
        toolBar.add(actionToolBarComponent, BorderLayout.CENTER);

        JComponent viewToolbarComponent = createSelectViewActionsComponent();
        toolBar.add(viewToolbarComponent, BorderLayout.EAST);
    }

    @NotNull
    private JPanel createRowLimitComponent() {
        rowLimitField.setColumns(5);
        rowLimitField.setDocument(new NumberDocument());

        JPanel rowLimitPanel = new NonOpaquePanel();
        rowLimitPanel.add(new JLabel("Row limit:"), BorderLayout.WEST);
        rowLimitPanel.add(rowLimitField, BorderLayout.CENTER);
        rowLimitPanel.add(Box.createHorizontalStrut(5), BorderLayout.EAST);
        return rowLimitPanel;
    }


    @NotNull
    private JComponent createResultActionsComponent() {
        DefaultActionGroup actionResultGroup = new DefaultActionGroup("MongoResultGroup", true);
        actionResultGroup.add(new ExecuteQuery(this));
        actionResultGroup.add(new OpenFindAction(this));
        actionResultGroup.add(new EnableAggregateAction(queryPanel));
        actionResultGroup.addSeparator();
        actionResultGroup.add(new AddMongoDocumentAction(resultPanel));
        actionResultGroup.add(new EditMongoDocumentAction(resultPanel));
        actionResultGroup.add(new CopyResultAction(resultPanel));
        actionResultGroup.addSeparator();
        actionResultGroup.add(new NavigateBackwardAction(this));

        addBasicTreeActions(actionResultGroup);
        actionResultGroup.add(new CloseFindEditorAction(this));

        ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("MongoResultGroupActions", actionResultGroup, true);
        actionToolBar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
        JComponent actionToolBarComponent = actionToolBar.getComponent();
        actionToolBarComponent.setBorder(null);
        actionToolBarComponent.setOpaque(false);
        return actionToolBarComponent;
    }

    @NotNull
    private JComponent createSelectViewActionsComponent() {
        DefaultActionGroup viewSelectGroup = new DefaultActionGroup("MongoViewSelectGroup", false);
        viewSelectGroup.add(new ViewAsTreeAction(this));
        viewSelectGroup.add(new ViewAsTableAction(this));

        ActionToolbar viewToolbar = ActionManager.getInstance().createActionToolbar("MongoViewSelectedActions", viewSelectGroup, true);
        viewToolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
        JComponent viewToolbarComponent = viewToolbar.getComponent();
        viewToolbarComponent.setBorder(null);
        viewToolbarComponent.setOpaque(false);
        return viewToolbarComponent;
    }

    private void addBasicTreeActions(DefaultActionGroup actionResultGroup) {
        final TreeExpander treeExpander = new TreeExpander() {
            @Override
            public void expandAll() {
                resultPanel.expandAll();
            }

            @Override
            public boolean canExpand() {
                return true;
            }

            @Override
            public void collapseAll() {
                resultPanel.collapseAll();
            }

            @Override
            public boolean canCollapse() {
                return true;
            }
        };

        CommonActionsManager actionsManager = CommonActionsManager.getInstance();
        final AnAction expandAllAction = actionsManager.createExpandAllAction(treeExpander, resultPanel);
        final AnAction collapseAllAction = actionsManager.createCollapseAllAction(treeExpander, resultPanel);

        Disposer.register(this, new Disposable() {
            @Override
            public void dispose() {
                collapseAllAction.unregisterCustomShortcutSet(resultPanel);
                expandAllAction.unregisterCustomShortcutSet(resultPanel);
            }
        });


        actionResultGroup.addSeparator();
        actionResultGroup.add(expandAllAction);
        actionResultGroup.add(collapseAllAction);
    }

    public Navigation.WayPoint getCurrentWayPoint() {
        return navigation.getCurrentWayPoint();
    }

    public void showResults() {
        executeQuery();
    }

    private void executeQuery(final boolean useCachedResults, final Navigation.WayPoint wayPoint) {
        errorPanel.setVisible(false);
        validateQuery();
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                try {
                    GuiUtils.runInSwingThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingDecorator.startLoading(false);
                        }
                    });

                    if (!useCachedResults) {
                        currentResults = mongoManager.loadCollectionValues(
                                configuration,
                                wayPoint.getCollection(),
                                wayPoint.getQueryOptions());
                    }
                    GuiUtils.runInSwingThread(new Runnable() {
                        @Override
                        public void run() {
                            resultPanel.updateResultView(currentResults);
                            initActions(resultPanel.resultTreeTableView);

                        }
                    });
                } catch (final Exception ex) {
                    GuiUtils.runInSwingThread(new Runnable() {
                        @Override
                        public void run() {
                            errorPanel.invalidate();
                            errorPanel.removeAll();
                            errorPanel.add(new ErrorPanel(ex), BorderLayout.CENTER);
                            errorPanel.validate();
                            errorPanel.setVisible(true);
                        }
                    });
                } finally {
                    GuiUtils.runInSwingThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingDecorator.stopLoading();
                        }
                    });
                }
            }
        });
    }

    private void initActions(JsonTreeTableView resultTreeTableView) {
        resultTreeTableView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2 && resultPanel.isSelectedNodeId()) {
                    resultPanel.editSelectedMongoDocument();
                }
            }
        });

        DefaultActionGroup actionPopupGroup = new DefaultActionGroup("MongoResultPopupGroup", true);
        if (ApplicationManager.getApplication() != null) {
            actionPopupGroup.add(new EditMongoDocumentAction(resultPanel));
            actionPopupGroup.add(new CopyResultAction(resultPanel));
            actionPopupGroup.add(new GoToMongoDocumentAction(this));
        }

        PopupHandler.installPopupHandler(resultTreeTableView, actionPopupGroup, "POPUP", ActionManager.getInstance());
    }

    public void executeQuery() {
        Navigation.WayPoint currentWayPoint = navigation.getCurrentWayPoint();
        currentWayPoint.setQueryOptions(queryPanel.getQueryOptions(rowLimitField.getText()));
        executeQuery(false, currentWayPoint);
    }

    private void validateQuery() {
        queryPanel.validateQuery();
    }

    @Override
    public void dispose() {
        resultPanel.dispose();
    }

    public MongoResultPanel getResultPanel() {
        return resultPanel;
    }

    public void openFindEditor() {
        queryPanel.setVisible(true);
        splitter.setFirstComponent(queryPanel);
        GuiUtils.runInSwingThread(new Runnable() {
            @Override
            public void run() {
                focusOnEditor();
            }
        });
    }

    public void closeFindEditor() {
        splitter.setFirstComponent(null);
        queryPanel.setVisible(false);
    }

    public void focusOnEditor() {
        queryPanel.requestFocusOnEditor();
    }

    public boolean isFindEditorOpened() {
        return splitter.getFirstComponent() == queryPanel;
    }

    public void setViewMode(MongoResultPanel.ViewMode viewMode) {
        if (resultPanel.getCurrentViewMode().equals(viewMode)) {
            return;
        }
        this.resultPanel.setCurrentViewMode(viewMode);
        executeQuery(true, navigation.getCurrentWayPoint());
    }

    public ServerConfiguration getConfiguration() {
        return configuration;
    }

    public void navigateBackward() {
        navigation.moveBackward();
        executeQuery(false, navigation.getCurrentWayPoint());
    }

    public boolean hasNavigationHistory() {
        return navigation.getWayPoints().size() > 1;
    }

    public void goToReferencedDocument() {
        DBRef selectedDBRef = resultPanel.getSelectedDBRef();

        Document referencedDocument = resultPanel.getReferencedDocument(selectedDBRef);
        if (referencedDocument == null) {
            Messages.showErrorDialog(this, "Referenced document was not found");
            return;
        }

        navigation.addNewWayPoint(
                new MongoCollection(selectedDBRef.getCollectionName(), selectedDBRef.getDatabaseName() != null ? selectedDBRef.getDatabaseName() :
                        navigation.getCurrentWayPoint().getCollection().getDatabaseName()),
                new MongoQueryOptions().setFilter(
                        new Document("_id", selectedDBRef.getId())
                ));
        executeQuery(false, navigation.getCurrentWayPoint());
    }

    interface MongoDocumentOperations {
        Document getMongoDocument(Object _id);

        void deleteMongoDocument(Object mongoDocument);

        void updateMongoDocument(Document mongoDocument);

        Document getReferenceDocument(String collection, Object _id, String database);
    }
}
