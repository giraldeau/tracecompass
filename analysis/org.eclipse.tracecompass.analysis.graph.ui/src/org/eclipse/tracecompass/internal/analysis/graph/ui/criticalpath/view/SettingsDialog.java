/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.graph.ui.criticalpath.view;

import java.util.ArrayList;
import java.util.Collections;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tracecompass.internal.analysis.graph.core.criticalpath.AlgorithmManager;

/**
 * Set critical path parameters
 *
 * @author Francis Giraldeau
 */
@SuppressWarnings("nls")
public class SettingsDialog extends TitleAreaDialog {

    private @Nullable Combo fAlgorithmCombo;

    private @Nullable String fAlgorithmType;

    /**
     * Settings dialog
     *
     * @param parentShell
     *            the parent shell
     */
    public SettingsDialog(Shell parentShell) {
        super(parentShell);
    }

    @Override
    public void create() {
        super.create();
        setTitle("View settings");
        setMessage("Execution path settings", IMessageProvider.INFORMATION);
    }

    @Override
    protected @Nullable Control createDialogArea(@Nullable Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(GridData.FILL_BOTH));
        GridLayout layout = new GridLayout(2, false);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        container.setLayout(layout);

        createAlgorithmCombo(container);

        return area;
    }

    private void createAlgorithmCombo(Composite container) {
        Label lbtFirstName = new Label(container, SWT.NONE);
        lbtFirstName.setText("Algorithm");

        GridData dataFirstName = new GridData();
        dataFirstName.grabExcessHorizontalSpace = true;
        dataFirstName.horizontalAlignment = GridData.FILL;

        Combo algorithmCombo = new Combo(container, SWT.BORDER | SWT.READ_ONLY);
        fAlgorithmCombo = algorithmCombo;
        algorithmCombo.setLayoutData(dataFirstName);
        AlgorithmManager algo = AlgorithmManager.getInstance();
        ArrayList<String> names = new ArrayList<>(algo.registeredTypes().keySet());
        Collections.sort(names);
        for (String name : names) {
            algorithmCombo.add(name);
        }
        // set default
        if (null != fAlgorithmType) {
            algorithmCombo.setText(fAlgorithmType);
        } else {
            algorithmCombo.setText(names.get(0));
        }
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    // save content of the Text fields because they get disposed
    // as soon as the Dialog closes
    private void saveInput() {
        final Combo algorithmCombo = fAlgorithmCombo;
        if (algorithmCombo != null) {
            fAlgorithmType = algorithmCombo.getText();
        }
    }

    @Override
    protected void okPressed() {
        saveInput();
        super.okPressed();
    }

    /**
     * Return the name of the algorithm
     * @return the algorithm name
     */
    public @Nullable String getAlgorithmType() {
        return fAlgorithmType;
    }

    /**
     * Set the current selected algorithm
     * @param name the name
     */
    public void setAlgorithmType(String name) {
        fAlgorithmType = name;
        if (fAlgorithmCombo != null) {
            fAlgorithmCombo.setText(fAlgorithmType);
        }
    }

}
