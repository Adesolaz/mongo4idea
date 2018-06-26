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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.tree.TreeUtil;
import com.mongodb.DBRef;
import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import org.codinjutsu.tools.mongo.logic.Notifier;
import org.codinjutsu.tools.mongo.model.MongoCollectionResult;
import org.codinjutsu.tools.mongo.model.NbPerPage;
import org.codinjutsu.tools.mongo.utils.GuiUtils;
import org.codinjutsu.tools.mongo.view.model.JsonTableUtils;
import org.codinjutsu.tools.mongo.view.model.JsonTreeNode;
import org.codinjutsu.tools.mongo.view.model.JsonTreeUtils;
import org.codinjutsu.tools.mongo.view.model.Pagination;
import org.codinjutsu.tools.mongo.view.nodedescriptor.MongoKeyValueDescriptor;
import org.codinjutsu.tools.mongo.view.nodedescriptor.MongoNodeDescriptor;
import org.codinjutsu.tools.mongo.view.nodedescriptor.MongoResultDescriptor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MongoResultPanel extends JPanel implements Disposable {

    private final MongoPanel.MongoDocumentOperations mongoDocumentOperations;
    private final Notifier notifier;
    private JPanel mainPanel;
    private JPanel containerPanel;
    private final Splitter splitter;
    private final JPanel resultTreePanel;
    private final MongoEditionPanel mongoEditionPanel;

    JsonTreeTableView resultTreeTableView;

    private ViewMode currentViewMode = ViewMode.TREE;


    public MongoResultPanel(Project project, MongoPanel.MongoDocumentOperations mongoDocumentOperations, Notifier notifier) {
        this.mongoDocumentOperations = mongoDocumentOperations;
        this.notifier = notifier;
        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);

        splitter = new Splitter(true, 0.6f);

        resultTreePanel = new JPanel(new BorderLayout());

        splitter.setFirstComponent(resultTreePanel);

        mongoEditionPanel = createMongoEditionPanel();

        containerPanel.setLayout(new JBCardLayout());
        containerPanel.add(splitter);

        Disposer.register(project, this);
    }

    private MongoEditionPanel createMongoEditionPanel() {
        return new MongoEditionPanel().init(mongoDocumentOperations, new ActionCallback() {
            public void onOperationSuccess(String shortMessage, String detailedMessage) {
                hideEditionPanel();
                GuiUtils.showNotification(MongoResultPanel.this.resultTreePanel, MessageType.INFO, shortMessage, Balloon.Position.above);
                notifier.notifyInfo(detailedMessage);
            }

            @Override
            public void onOperationFailure(Exception exception) {
                notifier.notifyError(exception.getMessage());
                GuiUtils.showNotification(MongoResultPanel.this.mongoEditionPanel, MessageType.ERROR, "An error occured (see Event Log)", Balloon.Position.above);
            }

            @Override
            public void onOperationCancelled(String message) {
                hideEditionPanel();
            }
        });
    }

    void updateResultView(MongoCollectionResult mongoCollectionResult, Pagination pagination) {
        if (ViewMode.TREE.equals(currentViewMode)) {
            updateResultTreeTable(mongoCollectionResult, pagination);
        } else {
            updateResultTable(mongoCollectionResult);
        }
    }

    private void updateResultTreeTable(MongoCollectionResult mongoCollectionResult, Pagination pagination) {
        resultTreeTableView = new JsonTreeTableView(JsonTreeUtils.buildJsonTree(mongoCollectionResult.getCollectionName(),
                extractDocuments(pagination, mongoCollectionResult.getDocuments()), pagination.getStartIndex()),
                JsonTreeTableView.COLUMNS_FOR_READING);
        resultTreeTableView.setName("resultTreeTable");

        displayResult(resultTreeTableView);
    }

    private static List<Document> extractDocuments(Pagination pagination, List<Document> documents) {
        if (NbPerPage.ALL.equals(pagination.getNbPerPage())) {
            return documents;
        }
        int startIndex = pagination.getStartIndex();
        int endIndex = startIndex + pagination.getNbDocumentsPerPage();

        return IntStream.range(startIndex, endIndex)
                .mapToObj(documents::get)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private void updateResultTable(MongoCollectionResult mongoCollectionResult) {
        displayResult(new JsonTableView(JsonTableUtils.buildJsonTable(mongoCollectionResult)));
    }

    private void displayResult(JComponent tableView) {
        resultTreePanel.invalidate();
        resultTreePanel.removeAll();
        resultTreePanel.add(new JBScrollPane(tableView));
        resultTreePanel.validate();
    }


    public void editSelectedMongoDocument() {
        Document mongoDocument = getSelectedMongoDocument();
        if (mongoDocument == null) {
            return;
        }

        mongoEditionPanel.updateEditionTree(mongoDocument);
        splitter.setSecondComponent(mongoEditionPanel);
    }


    public void addMongoDocument() {
        mongoEditionPanel.updateEditionTree(null);
        splitter.setSecondComponent(mongoEditionPanel);
    }

    private Document getSelectedMongoDocument() {
        TreeTableTree tree = resultTreeTableView.getTree();
        JsonTreeNode treeNode = (JsonTreeNode) tree.getLastSelectedPathComponent();
        if (treeNode == null) {
            return null;
        }

        MongoNodeDescriptor descriptor = treeNode.getDescriptor();
        if (descriptor instanceof MongoKeyValueDescriptor) {
            MongoKeyValueDescriptor keyValueDescriptor = (MongoKeyValueDescriptor) descriptor;
            if (StringUtils.equals(keyValueDescriptor.getKey(), "_id")) {
                return mongoDocumentOperations.getMongoDocument(keyValueDescriptor.getValue());
            }
        }

        return null;
    }


    public boolean isSelectedNodeId() {
        if (resultTreeTableView == null) {
            return false;
        }
        TreeTableTree tree = resultTreeTableView.getTree();
        JsonTreeNode treeNode = (JsonTreeNode) tree.getLastSelectedPathComponent();
        if (treeNode == null) {
            return false;
        }

        MongoNodeDescriptor descriptor = treeNode.getDescriptor();
        if (descriptor instanceof MongoKeyValueDescriptor) {
            return "_id".equals(((MongoKeyValueDescriptor) descriptor).getKey());
        }

        return false;
    }


    public boolean isSelectedDBRef() {
        if (resultTreeTableView == null) {
            return false;
        }

        TreeTableTree tree = resultTreeTableView.getTree();
        JsonTreeNode treeNode = (JsonTreeNode) tree.getLastSelectedPathComponent();
        if (treeNode == null) {
            return false;
        }

        MongoNodeDescriptor descriptor = treeNode.getDescriptor();
        if (descriptor instanceof MongoKeyValueDescriptor) {
            if (descriptor.getValue() instanceof DBRef) {
                return true;
            } else {
                JsonTreeNode parentNode = (JsonTreeNode) treeNode.getParent();
                return parentNode.getDescriptor().getValue() instanceof DBRef;
            }
        }

        return false;
    }

    void expandAll() {
        TreeUtil.expandAll(resultTreeTableView.getTree());
    }

    void collapseAll() {
        TreeTableTree tree = resultTreeTableView.getTree();
        TreeUtil.collapseAll(tree, 1);
    }

    public String getSelectedNodeStringifiedValue() {
        JsonTreeNode lastSelectedResultNode = (JsonTreeNode) resultTreeTableView.getTree().getLastSelectedPathComponent();
        if (lastSelectedResultNode == null) {
            lastSelectedResultNode = (JsonTreeNode) resultTreeTableView.getTree().getModel().getRoot();
        }
        MongoNodeDescriptor userObject = lastSelectedResultNode.getDescriptor();
        if (userObject instanceof MongoResultDescriptor) {
            return stringifyResult(lastSelectedResultNode);
        }

        return userObject.toString();
    }

    public DBRef getSelectedDBRef() {
        TreeTableTree tree = resultTreeTableView.getTree();
        JsonTreeNode treeNode = (JsonTreeNode) tree.getLastSelectedPathComponent();

        MongoNodeDescriptor descriptor = treeNode.getDescriptor();
        DBRef selectedDBRef = null;
        if (descriptor instanceof MongoKeyValueDescriptor) {
            if (descriptor.getValue() instanceof DBRef) {
                selectedDBRef = (DBRef) descriptor.getValue();
            } else {
                JsonTreeNode parentNode = (JsonTreeNode) treeNode.getParent();
                MongoNodeDescriptor parentDescriptor = parentNode.getDescriptor();
                if (parentDescriptor.getValue() instanceof DBRef) {
                    selectedDBRef = (DBRef) parentDescriptor.getValue();
                }
            }
        }

        return selectedDBRef;
    }


    private void hideEditionPanel() {
        splitter.setSecondComponent(null);
    }

    private String stringifyResult(DefaultMutableTreeNode selectedResultNode) {
        List<Object> stringifiedObjects = new LinkedList<>();
        for (int i = 0; i < selectedResultNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) selectedResultNode.getChildAt(i);
            stringifiedObjects.add(childNode.getUserObject());
        }

        return String.format("[ %s ]", StringUtils.join(stringifiedObjects, ", "));
    }

    @Override
    public void dispose() {
        resultTreeTableView = null;
        mongoEditionPanel.dispose();
    }

    void setCurrentViewMode(ViewMode viewMode) {
        this.currentViewMode = viewMode;
    }

    ViewMode getCurrentViewMode() {
        return currentViewMode;
    }

    public Document getReferencedDocument(DBRef selectedDBRef) {
        return mongoDocumentOperations.getReferenceDocument(
                selectedDBRef.getCollectionName(), selectedDBRef.getId(), selectedDBRef.getDatabaseName()
        );
    }

    interface ActionCallback {

        void onOperationSuccess(String label, String message);

        void onOperationFailure(Exception exception);

        void onOperationCancelled(String message);
    }

    public enum ViewMode {
        TREE, TABLE
    }

}
