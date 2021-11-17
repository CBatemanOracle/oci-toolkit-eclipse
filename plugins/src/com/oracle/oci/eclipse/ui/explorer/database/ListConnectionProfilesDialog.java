package com.oracle.oci.eclipse.ui.explorer.database;

import java.io.ByteArrayInputStream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ContainerSelectionDialog;

import com.oracle.bmc.database.model.DatabaseConnectionStringProfile;
import com.oracle.oci.eclipse.ErrorHandler;
import com.oracle.oci.eclipse.sdkclients.ADBInstanceClient.DatabaseConnectionProfiles;

public class ListConnectionProfilesDialog extends TrayDialog {

    private DatabaseConnectionProfiles profiles;
    private StyledText mTlsProfilesText;
    private StyledText walletlessProfiles;

    public ListConnectionProfilesDialog(Shell shell, DatabaseConnectionProfiles profiles) {
        super(shell);
        this.profiles = profiles;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite composite = (Composite) super.createDialogArea(parent);
        
        CTabFolder folder = new CTabFolder(composite, SWT.NONE);
        CTabItem mtlsProfilesTab = new CTabItem(folder, SWT.NONE);
        mtlsProfilesTab.setText("mTLS profiles");

        Composite mtlsProfilesTabPanel = new Composite(folder, SWT.NONE);
        mtlsProfilesTabPanel.setLayout(new GridLayout(1, false));
        Label mtlsProfilesLbl = new Label(mtlsProfilesTabPanel, SWT.NONE);
        mtlsProfilesLbl.setText("You will a need valid wallet to use these profiles");
        this.mTlsProfilesText = new StyledText(mtlsProfilesTabPanel, SWT.H_SCROLL);
        mTlsProfilesText.setEditable(false);
        GridDataFactory.defaultsFor(mTlsProfilesText).align(SWT.FILL, SWT.END).applyTo(mTlsProfilesText);
        mtlsProfilesTab.setControl(mtlsProfilesTabPanel);

        for (DatabaseConnectionStringProfile profile : this.profiles.getmTLSProfiles()) {
            String profileText = String.format("%s = %s", profile.getDisplayName(), profile.getValue());
            mTlsProfilesText.setText(mTlsProfilesText.getText()+profileText+mTlsProfilesText.getLineDelimiter());
        }
        GridDataFactory.defaultsFor(mTlsProfilesText).align(SWT.FILL, SWT.BEGINNING).applyTo(mTlsProfilesText);

        
        if (!this.profiles.getWalletLessProfiles().isEmpty())
        {
            CTabItem walletLessProfilesTab = new CTabItem(folder, SWT.NONE);
            walletLessProfilesTab.setText("Walletless (one-way) TLS");

            Composite walletLessProfilePanel = new Composite(folder, SWT.NONE);
            walletLessProfilePanel.setLayout(new GridLayout(1, false));
            walletLessProfilePanel.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
            
            Label walletlessProfilesLbl = new Label(walletLessProfilePanel, SWT.NONE);
            walletlessProfilesLbl.setText("One-way TLS profiles.  You have set up custom IP or VCN restrictions if you can see these profiles");

            this.walletlessProfiles = new StyledText(walletLessProfilePanel,SWT.H_SCROLL);
            this.walletlessProfiles.setEditable(false);
            for (DatabaseConnectionStringProfile profile : this.profiles.getWalletLessProfiles()) {
                String profileText = String.format("%s = %s", profile.getDisplayName(), profile.getValue());
                walletlessProfiles.setText(walletlessProfiles.getText()+profileText+walletlessProfiles.getLineDelimiter());
            }
            GridDataFactory.defaultsFor(walletlessProfiles).align(SWT.FILL, SWT.END).applyTo(walletlessProfiles);
            walletLessProfilesTab.setControl(walletLessProfilePanel);
        }
        
        Composite mTlsButtonPanel = new Composite(composite, SWT.NONE);
        mTlsButtonPanel.setLayout(new GridLayout(2, false));
        Button mTlsSaveToProjBtn = new Button(mTlsButtonPanel, SWT.PUSH);
        mTlsSaveToProjBtn.setText("Save to project...");
        mTlsSaveToProjBtn.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                saveProfilesToProject(folder.getSelection() == mtlsProfilesTab ? mTlsProfilesText : walletlessProfiles);
            }

        });
        Button mTlsSaveToFolderBtn = new Button(mTlsButtonPanel, SWT.PUSH);
        mTlsSaveToFolderBtn.setText("Save to file...");

        Shell myShell = getShell();
        int width = convertWidthInCharsToPixels(80);
        myShell.setSize(width, (int) width * 5 / 7);
        Rectangle parentSize = myShell.getParent().getBounds();
        myShell.setLocation((parentSize.width - myShell.getBounds().width) / 2, (parentSize.height - myShell.getBounds().height) / 2);
        
        return composite;
    }

    private void saveProfilesToProject(StyledText text) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        ContainerSelectionDialog selectionDialog = new ContainerSelectionDialog(getShell(), 
                root, true, "Select folder for tnsnames.ora");
        int open = selectionDialog.open();
        if (open == Dialog.OK)
        {
            Object[] result = selectionDialog.getResult();
            if (result.length > 0)
            {
                IPath containerPath = (IPath) result[0];
                IResource findMember = root.findMember(containerPath, false);
                IContainer container = null;
                if (findMember instanceof IContainer)
                {
                    container = (IContainer) findMember;
                }
                if (container == null)
                {
                    return;
                }
                IFile file = container.getFile(new Path("tnsnames.ora"));
                boolean createFile = true;
                if (file.exists())
                {
                    createFile = MessageDialog.openQuestion(getShell(), "File will be overwritten", "File be will be overwritten: "+file.toString());
                }
                if (createFile)
                {
                    try {
                        file.create(new ByteArrayInputStream(text.getText().getBytes()), true, new NullProgressMonitor());
                    } catch (CoreException e) {
                        ErrorHandler.logErrorStack("Error creating tnsnames.ora", e);
                    }
                }
            }
        }
    }

    @Override
    protected boolean isResizable() {
        return true;
    }
}
