package com.oracle.oci.eclipse.ui.explorer.database;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.Wizard;

import com.oracle.bmc.database.model.AutonomousDatabaseSummary;
import com.oracle.oci.eclipse.sdkclients.ADBInstanceClient;

public class UpdateADBAccessControlWizard extends Wizard {

    private AutonomousDatabaseSummary instance;
    private UpdateADBAccessControlWizardPage updateADBAccessControlPage;

    public UpdateADBAccessControlWizard(final AutonomousDatabaseSummary instance) {
        super();
        this.instance = instance;
        setNeedsProgressMonitor(true);
    }

    @Override
    public void addPages() {
        this.updateADBAccessControlPage = new UpdateADBAccessControlWizardPage(instance);
        addPage(updateADBAccessControlPage);
    }

    /**
     * This method is called when 'Finish' button is pressed in
     * the wizard. We will create an operation and run it
     * using wizard as execution context.
     */
    @Override
    public boolean performFinish() {
        List<String> whitelistIps = updateADBAccessControlPage.getWhitelistedIps();
        boolean enableOneWayTls = updateADBAccessControlPage.isEnableOnWayTls();
        IRunnableWithProgress op = new IRunnableWithProgress() {
            @Override
            public void run(IProgressMonitor monitor) throws InvocationTargetException {
                ADBInstanceClient.getInstance().updateAcl(instance, whitelistIps, enableOneWayTls);
                monitor.done();
            }
        };
        try {
            getContainer().run(true, false, op);
        } catch (InterruptedException e) {
            return false;
        } catch (InvocationTargetException e) {
            Throwable realException = e.getTargetException();
            MessageDialog.openError(getShell(), "Failed to update License model for ADB instance : "+instance.getDbName(), realException.getMessage());
            return false;
        }

        return true;

    }

}
