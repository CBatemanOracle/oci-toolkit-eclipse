package com.oracle.oci.eclipse.ui.explorer.database;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.oracle.bmc.core.model.Vcn;
import com.oracle.bmc.database.model.AutonomousDatabaseSummary;
import com.oracle.oci.eclipse.Activator;
import com.oracle.oci.eclipse.sdkclients.ADBInstanceWrapper;
import com.oracle.oci.eclipse.sdkclients.NetworkClient;
import com.oracle.oci.eclipse.ui.explorer.database.model.AccessControlRowHolder;
import com.oracle.oci.eclipse.ui.explorer.database.model.AccessControlType;
import com.oracle.oci.eclipse.ui.explorer.database.model.AccessControlType.Category;
import com.oracle.oci.eclipse.ui.explorer.database.model.IPAddressType;
import com.oracle.oci.eclipse.ui.explorer.database.model.OcidBasedAccessControlType;
import com.oracle.oci.eclipse.ui.explorer.database.provider.AclTableLabelProvider;
import com.oracle.oci.eclipse.ui.explorer.database.provider.EditingSupportFactory;
import com.oracle.oci.eclipse.ui.explorer.database.provider.EditingSupportFactory.IPTypeColumnEditingSupport;
import com.oracle.oci.eclipse.ui.explorer.database.provider.EditingSupportFactory.IPValueColumnEditingSupport;
import com.oracle.oci.eclipse.ui.explorer.database.provider.PropertyListeningArrayList;
import com.oracle.oci.eclipse.ui.explorer.database.provider.VCNTableLabelProvider;

public class UpdateADBAccessControlWizardPage extends WizardPage {

    private final AutonomousDatabaseSummary instance;

    private PropertyListeningArrayList<AccessControlRowHolder> ipConfigs = new PropertyListeningArrayList<>();
    private PropertyListeningArrayList<AccessControlRowHolder> vcnConfigs = new PropertyListeningArrayList<>();

    private TableViewerColumn ipTypeColumn;
    private TableViewerColumn valuesColumn;
    private Table configureAnywhereTable;
    private TableColumn privateVCN;
    private TableColumn privateVCNCompartment;
    private Table privateEndpointTable;

    private ToolBar actionPanelIpAddress;

    private TableViewer ipAddressAclTableViewer;

    private Button configureSecurityCheckbox;
    private Button enableOneWayTls;
    private ToolBar actionPanelVCN;
    private TableViewer vcnAclTableViewer;

    private TableViewerColumn vcnDisplayNameColumn;
    private TableViewerColumn vcnOcidColumn;
    private TableViewerColumn vcnIPRestrictions;

    //private PropertyListeningArrayList<VcnWrapper> vcnInfos;

    public UpdateADBAccessControlWizardPage(AutonomousDatabaseSummary instance) {
        super("wizardPage");
        setTitle("Update Autonomous Database Access Control");
        setDescription("");
        this.instance = instance;
    }

    @Override
    public boolean isPageComplete() {
        // TODO Auto-generated method stub
        return super.isPageComplete();
    }

    @Override
    public void createControl(Composite parent) {

        Composite container = new Composite(parent, SWT.NULL);
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        container.setLayout(layout);

        Composite networkSetupContainer = new Composite(container, SWT.NONE);
        GridLayout innerTopLayout = new GridLayout();
        innerTopLayout.numColumns = 2;
        networkSetupContainer.setLayout(innerTopLayout);
        networkSetupContainer
                .setLayoutData(new GridData(GridData.FILL_BOTH | GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL));

        TabFolder connectionTypeFolder = new TabFolder(networkSetupContainer, SWT.TOP | SWT.MULTI);
        connectionTypeFolder.setLayoutData(new GridData(GridData.FILL_BOTH));

        TabItem secureFromEverywhere = new TabItem(connectionTypeFolder, SWT.NONE);
        secureFromEverywhere.setText("Secure From Anywhere");
        Composite secureFromEverywhereTabComp = new Composite(connectionTypeFolder, SWT.BORDER);

        secureFromEverywhereTabComp
                .setLayoutData(new GridData(GridData.FILL_BOTH | GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL));
        secureFromEverywhere.setControl(secureFromEverywhereTabComp);
        createSecureFromEverywhere(secureFromEverywhereTabComp);

//        TabItem privateEndpoint = new TabItem(connectionTypeFolder, SWT.NONE);
//        privateEndpoint.setText("Private Endpoint");
//        Composite privateNetworkTabComp = new Composite(connectionTypeFolder, SWT.BORDER);
//        privateNetworkTabComp.setLayoutData(new GridData(GridData.FILL_BOTH | GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL));
//        privateEndpoint.setControl(privateNetworkTabComp);
//        createPrivateEndpoint(privateNetworkTabComp);

        setControl(container);

    }

    private void createSecureFromEverywhere(final Composite secureFromEverywhereTabComp) {
        secureFromEverywhereTabComp.setLayout(new GridLayout(2, false));
        Label explainMlsLabel = new Label(secureFromEverywhereTabComp, SWT.WRAP);
        explainMlsLabel.setText(
                "Specify the IP addresses and VCNs allowed to access this database. An access control list blocks all IP addresses that are not in the list from accessing the database.");
        GridDataFactory.defaultsFor(explainMlsLabel).span(2, 1).applyTo(explainMlsLabel);

        this.configureSecurityCheckbox = new Button(secureFromEverywhereTabComp, SWT.CHECK);
        configureSecurityCheckbox.setText("Configure access control rules");
        GridDataFactory.defaultsFor(configureSecurityCheckbox).span(2, 1).applyTo(configureSecurityCheckbox);

        this.enableOneWayTls = new Button(secureFromEverywhereTabComp, SWT.CHECK);
        enableOneWayTls.setText("Require mutual TLS (mTLS) authentication");
        enableOneWayTls.setToolTipText(
                "If you select this option, mTLS will be required to authenticate connections to your Autonomous Database.");

        // create toolbar and table for ip address acls
        createIPAddressPanel(secureFromEverywhereTabComp);
        final Sash sash = new Sash(secureFromEverywhereTabComp, SWT.BORDER | SWT.HORIZONTAL);
        GridDataFactory.swtDefaults().span(2, 1).align(SWT.FILL, SWT.BEGINNING).applyTo(sash);
        sash.addListener(SWT.Selection, e -> sash.setBounds(e.x, e.y, e.width, e.height));
        Rectangle clientArea = secureFromEverywhereTabComp.getClientArea();
        sash.setBounds(180, clientArea.y, 32, clientArea.height);
        createVCNPanel(secureFromEverywhereTabComp);

        populateAccessFromAnywhere(this.instance);

        // add listener after initialization.
        configureSecurityCheckbox.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateAclUpdate(configureSecurityCheckbox.getSelection());
            }
        });
        this.ipTypeColumn.getColumn().pack();
        this.valuesColumn.getColumn().pack();

        this.vcnIPRestrictions.getColumn().pack();
        this.vcnDisplayNameColumn.getColumn().pack();
        this.vcnOcidColumn.getColumn().pack();
    }

    private void createIPAddressPanel(final Composite secureFromEverywhereTabComp) {
        Group ipAddressPanel = new Group(secureFromEverywhereTabComp, SWT.NONE);
        ipAddressPanel.setText("Secure By IP Address");
        ipAddressPanel.setLayout(new GridLayout(2, false));
        GridData layoutData = new GridData(GridData.FILL_BOTH | GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL);
        layoutData.horizontalSpan = 2;
        ipAddressPanel.setLayoutData(layoutData);

        this.actionPanelIpAddress = new ToolBar(ipAddressPanel, SWT.NONE);
        GridDataFactory.swtDefaults().grab(true, false).align(SWT.END, SWT.END).span(2, 1)
                .applyTo(actionPanelIpAddress);
        ToolItem addItem = new ToolItem(actionPanelIpAddress, SWT.PUSH);
        addItem.setText("Add");
        ToolItem rmItem = new ToolItem(actionPanelIpAddress, SWT.PUSH);
        rmItem.setText("Remove");

        this.ipAddressAclTableViewer = new TableViewer(ipAddressPanel, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        this.configureAnywhereTable = ipAddressAclTableViewer.getTable();
        configureAnywhereTable.setHeaderVisible(true);
        configureAnywhereTable.setLinesVisible(true);
        configureAnywhereTable.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).span(2, 1).create());
        this.ipTypeColumn = new TableViewerColumn(this.ipAddressAclTableViewer, SWT.NONE);
        ipTypeColumn.getColumn().setText("IP Notation");
        ipTypeColumn.setEditingSupport(new IPTypeColumnEditingSupport(ipTypeColumn.getViewer()));

        this.valuesColumn = new TableViewerColumn(ipAddressAclTableViewer, SWT.NONE);
        valuesColumn.getColumn().setText("Value");
        valuesColumn.setEditingSupport(new IPValueColumnEditingSupport(ipAddressAclTableViewer));

        ipAddressAclTableViewer.setContentProvider(new IStructuredContentProvider() {
            @Override
            public Object[] getElements(Object inputElement) {
                if (inputElement instanceof List<?>) {
                    return ((List<?>) inputElement).toArray();
                }
                return new Object[0];
            }
        });

        ipAddressAclTableViewer.setLabelProvider(new AclTableLabelProvider());
        addItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IPAddressType addressType = new IPAddressType("");
                ipConfigs.add(new AccessControlRowHolder(addressType, true));
                ipAddressAclTableViewer.refresh();
            }
        });

        rmItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IStructuredSelection selection = ipAddressAclTableViewer.getStructuredSelection();
                Object firstElement = selection.getFirstElement();
                ipConfigs.remove(firstElement);
                ipAddressAclTableViewer.refresh();
            }
        });
    }

    private void createVCNPanel(final Composite parent) {
        Group ipAddressPanel = new Group(parent, SWT.NONE);
        ipAddressPanel.setText("Secure By VCN");
        ipAddressPanel.setLayout(new GridLayout(2, false));
        GridData layoutData = new GridData(GridData.FILL_BOTH | GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL);
        layoutData.horizontalSpan = 2;
        ipAddressPanel.setLayoutData(layoutData);

        this.actionPanelVCN = new ToolBar(ipAddressPanel, SWT.NONE);
        GridDataFactory.swtDefaults().grab(true, false).align(SWT.END, SWT.END).span(2, 1).applyTo(actionPanelVCN);
        ToolItem addItem = new ToolItem(actionPanelVCN, SWT.PUSH);
        addItem.setText("Add");
        ToolItem rmItem = new ToolItem(actionPanelVCN, SWT.PUSH);
        rmItem.setText("Remove");

        this.vcnAclTableViewer = new TableViewer(ipAddressPanel, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        this.configureAnywhereTable = vcnAclTableViewer.getTable();
        configureAnywhereTable.setHeaderVisible(true);
        configureAnywhereTable.setLinesVisible(true);
        configureAnywhereTable.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).span(2, 1).create());

        this.vcnDisplayNameColumn = new TableViewerColumn(vcnAclTableViewer, SWT.NONE);
        vcnDisplayNameColumn.getColumn().setText("Display Name");
        vcnDisplayNameColumn.setEditingSupport(new EditingSupportFactory.VcnDisplayNameColumnEditingSupport(vcnAclTableViewer));

        this.vcnIPRestrictions = new TableViewerColumn(vcnAclTableViewer, SWT.NONE);
        this.vcnIPRestrictions.getColumn().setText("IP Restrictions (Optional)");

        this.vcnOcidColumn = new TableViewerColumn(vcnAclTableViewer, SWT.NONE);
        this.vcnOcidColumn.getColumn().setText("Ocid");
        this.vcnOcidColumn.setEditingSupport(new EditingSupportFactory.VcnOcidColumnEditingSupport(vcnAclTableViewer));
        
        vcnAclTableViewer.setContentProvider(new IStructuredContentProvider() {
            @Override
            public Object[] getElements(Object inputElement) {
                if (inputElement instanceof List<?>) {
                    return ((List<?>) inputElement).toArray();
                }
                return new Object[0];
            }
        });

        vcnAclTableViewer.setLabelProvider(new VCNTableLabelProvider());
        addItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                OcidBasedAccessControlType addressType = 
                    new OcidBasedAccessControlType("", Collections.emptyList());
                AccessControlRowHolder e2 = new AccessControlRowHolder(addressType, false);
                e2.setNew(true);
                vcnConfigs.add(e2);
                vcnAclTableViewer.refresh();
                e2.addPropertyChangeListener(new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        if ("ocid".equals(evt.getPropertyName()))
                        {
                            NetworkClient networkClient = new NetworkClient();
                            Job.create("Load VCN", new ICoreRunnable() {
                                @Override
                                public void run(IProgressMonitor monitor) throws CoreException {
                                    Vcn vcn = networkClient.getVcn((String) evt.getNewValue());
                                    if (vcn != null)
                                    {
                                        ((OcidBasedAccessControlType)evt.getSource()).setVcn(vcn);
                                    }
                                }
                            }).schedule();;
                        }
                    }
                });
            }
        });

        rmItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IStructuredSelection selection = vcnAclTableViewer.getStructuredSelection();
                Object firstElement = selection.getFirstElement();
                vcnConfigs.remove(firstElement);
                vcnAclTableViewer.refresh();
            }
        });
    }

    private void updateStatus() {
        getShell().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                MultiStatus result = validate();
                if (!result.isOK()) {
                    setErrorMessage(result.getChildren()[0].getMessage());
                    setPageComplete(false);
                } else {
                    setErrorMessage(null);
                    setPageComplete(true);
                }
                ipAddressAclTableViewer.refresh(true);
                vcnAclTableViewer.refresh(true);
            }
        });
    }

    private MultiStatus validate() {
        MultiStatus multiStatus = new MultiStatus(Activator.PLUGIN_ID, -1, null, null);
        for (AccessControlRowHolder source : this.ipConfigs) {
            String validation = source.getAclType().isValueValid();
            if (validation != null) {
                IStatus status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, validation, null);
                multiStatus.add(status);
            }
        }
        return multiStatus;
    }

    private void updateAclUpdate(boolean enabled) {
        actionPanelIpAddress.setEnabled(enabled);
        configureAnywhereTable.setEnabled(enabled);
    }

    private void populateAccessFromAnywhere(AutonomousDatabaseSummary instance2) {
        ADBInstanceWrapper wrapper = new ADBInstanceWrapper(instance);
        List<String> whiteListedIps = wrapper.getWhiteListedIps();
        boolean aclEnabled;
        if (!whiteListedIps.isEmpty()) {
            aclEnabled = true;
        } else {
            aclEnabled = false;
        }
        this.configureSecurityCheckbox.setSelection(aclEnabled);
        updateAclUpdate(aclEnabled);
        this.ipConfigs = parseAclsFromText(whiteListedIps, Category.IP_BASED);
        this.ipAddressAclTableViewer.setInput(this.ipConfigs);

        this.vcnConfigs = parseAclsFromText(whiteListedIps, Category.VCN_BASED);

        //this.vcnInfos = new PropertyListeningArrayList<VcnWrapper>();
        final NetworkClient networkClient = new NetworkClient();
        for (final AccessControlRowHolder aclHolder : this.vcnConfigs) {
            if (!aclHolder.isFullyLoaded()) {
                Job.create("Load vcn info for acl", new ICoreRunnable() {
                    @Override
                    public void run(IProgressMonitor monitor) throws CoreException {
                        final OcidBasedAccessControlType aclType = 
                            (OcidBasedAccessControlType) aclHolder.getAclType();
                        String ocid = aclType.getOcid();
                        Vcn vcn = networkClient.getVcn(ocid);
                        if (vcn != null)
                        {
                            System.out.println(vcn.getDisplayName());
                            aclType.setVcn(vcn);
                            aclHolder.setFullyLoaded(true);
                            Display.getDefault().asyncExec(new Runnable() {
                                @Override
                                public void run() {
                                    vcnAclTableViewer.refresh();
                                }
                                
                            });
                        }
                    }
                }).schedule();;
            }
        }
        List<Vcn> listVcns = networkClient.listVcns();
        for (Vcn vcn : listVcns) {
            System.out.printf("id=%s, compartment=%s, displayName=%s\n", vcn.getId(), vcn.getCompartmentId(),
                    vcn.getDisplayName());
        }
        this.vcnAclTableViewer.setInput(this.vcnConfigs);
    }

    private PropertyListeningArrayList<AccessControlRowHolder> parseAclsFromText(List<String> whiteListedIps,
            Category category) {
        PropertyListeningArrayList<AccessControlRowHolder> acls = new PropertyListeningArrayList<>();

        for (final String whitelisted : whiteListedIps) {
            AccessControlType acl = AccessControlType.parseAcl(whitelisted);
            if (acl.getCategory() == category) {
                // IP Based is always loaded; vcn needs to wait for thec actual network
                // metadata.
                acls.add(new AccessControlRowHolder(acl, acl.getCategory() == Category.IP_BASED));
            }
        }

        acls.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                updateStatus();
            }
        });
        return acls;
    }

    private void createPrivateEndpoint(Composite privateNetworkTabComp) {
        privateNetworkTabComp.setLayout(new GridLayout(3, false));

        GridData inputGridData = new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL);

        Label vcnLabel = new Label(privateNetworkTabComp, SWT.NONE);
        vcnLabel.setText("Virtual cloud network:");
        CompartmentLabelLink vcnLink = new CompartmentLabelLink(privateNetworkTabComp);
        vcnLink.setText("Compartment: <a>cbateman</a>");
        Combo vcnCombo = new Combo(privateNetworkTabComp, SWT.DROP_DOWN);
        vcnCombo.setLayoutData(inputGridData);

        Label subnetLbl = new Label(privateNetworkTabComp, SWT.NONE);
        subnetLbl.setText("Subnet:");
        CompartmentLabelLink subnetLink = new CompartmentLabelLink(privateNetworkTabComp);
        subnetLink.setText("Compartment: <a>cbateman</a>");
        Combo subnetCombo = new Combo(privateNetworkTabComp, SWT.DROP_DOWN);
        subnetCombo.setLayoutData(inputGridData);

        Label hostnamePrefixLbl = new Label(privateNetworkTabComp, SWT.NONE);
        hostnamePrefixLbl.setText("Host name prefix");
        CompartmentLabelLink hostnameLink = new CompartmentLabelLink(privateNetworkTabComp);
        hostnameLink.setText("Compartment: <a>cbateman</a>");
        Text hostnamePrefix = new Text(privateNetworkTabComp, SWT.NONE);
        hostnamePrefix.setLayoutData(inputGridData);

        Group networkSecGroups = new Group(privateNetworkTabComp, SWT.NONE);
        networkSecGroups.setText("Network Security Groups");
        networkSecGroups.setLayout(new GridLayout(1, false));
        GridData layoutData = new GridData(GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL | GridData.FILL_BOTH);
        layoutData.horizontalSpan = 3;
        networkSecGroups.setLayoutData(layoutData);

        TableViewer viewer = new TableViewer(networkSecGroups, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        this.privateEndpointTable = viewer.getTable();
        privateEndpointTable.setHeaderVisible(true);
        privateEndpointTable.setLinesVisible(true);
        privateEndpointTable.setLayoutData(GridDataFactory.defaultsFor(configureAnywhereTable).align(SWT.FILL, SWT.FILL)
                .grab(true, true).create());
        this.privateVCN = new TableColumn(configureAnywhereTable, SWT.NONE);
        privateVCN.setText("Virtual Cloud Network");
        this.privateVCNCompartment = new TableColumn(configureAnywhereTable, SWT.NONE);
        privateVCNCompartment.setText("Compartment");

        this.privateVCN.pack();
        this.privateVCNCompartment.pack();

        viewer.setContentProvider(new IStructuredContentProvider() {
            @Override
            public Object[] getElements(Object inputElement) {
                if (inputElement instanceof List<?>) {
                    return ((List<?>) inputElement).toArray();
                }
                return new Object[0];
            }
        });
    }

    private static class CompartmentLabelLink {
        private Link compartmentLink;

        public CompartmentLabelLink(final Composite parent) {
            Composite linkComposite = new Composite(parent, SWT.NONE);
            linkComposite.setLayout(new GridLayout(1, false));
            this.compartmentLink = new Link(linkComposite, SWT.NONE);
        }

        public void setText(String text) {
            this.compartmentLink.setText(text);
        }
    }

    public boolean isEnableOnWayTls() {
        return this.enableOneWayTls.getSelection();
    }

    public List<String> getWhitelistedIps() {
        List<String> whitelistedIps = new ArrayList<>();
        for (AccessControlRowHolder holder : this.ipConfigs) {
            whitelistedIps.add(holder.getAclType().getValue());
        }
        for (AccessControlRowHolder holder : this.vcnConfigs) {
            whitelistedIps.add(holder.getAclType().getValue());
        }
        return whitelistedIps;
    }

}
