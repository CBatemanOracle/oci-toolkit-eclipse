package com.oracle.oci.eclipse.ui.explorer.database;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.oracle.bmc.database.model.AutonomousDatabaseSummary;
import com.oracle.oci.eclipse.Activator;
import com.oracle.oci.eclipse.sdkclients.ADBInstanceWrapper;

public class UpdateADBAccessControlWizardPage extends WizardPage {
    private final static Pattern IP_ADDR_PATTERN = Pattern.compile("(\\d+).(\\d+).(\\d+).(\\d+)");
    private final static Pattern CIDR_BLOCK_PATTERN = Pattern.compile("(\\d+).(\\d+).(\\d+).(\\d+)/(\\d+)");

    private final AutonomousDatabaseSummary instance;

    private PropertyListeningArrayList<AccessControlRowHolder> ipConfigs = new PropertyListeningArrayList<>();
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
        Label explainMlsLabel = new Label(secureFromEverywhereTabComp, SWT.NONE);
        explainMlsLabel
                .setText("To enable one-way TLS (walletless mode) you must click on 'Configure access control rules'");
        GridDataFactory.defaultsFor(explainMlsLabel).span(2, 1).applyTo(explainMlsLabel);

        this.configureSecurityCheckbox = new Button(secureFromEverywhereTabComp, SWT.CHECK);
        configureSecurityCheckbox.setText("Configure access control rules");
        GridDataFactory.defaultsFor(configureSecurityCheckbox).span(2, 1).applyTo(configureSecurityCheckbox);

        this.enableOneWayTls = new Button(secureFromEverywhereTabComp, SWT.CHECK);
        enableOneWayTls.setText("Enable One-Way TLS");

        this.actionPanelIpAddress = new ToolBar(secureFromEverywhereTabComp, SWT.NONE);
        GridDataFactory.swtDefaults().grab(true, false).align(SWT.END, SWT.END).span(2, 1)
                .applyTo(actionPanelIpAddress);
        ToolItem addItem = new ToolItem(actionPanelIpAddress, SWT.PUSH);
        addItem.setText("Add");
        ToolItem rmItem = new ToolItem(actionPanelIpAddress, SWT.PUSH);
        rmItem.setText("Remove");

        this.ipAddressAclTableViewer = new TableViewer(secureFromEverywhereTabComp,
                SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
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

        ipAddressAclTableViewer.setLabelProvider(new LabelProvider());
        addItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IPAddressType addressType = new IPAddressType("");
                ipConfigs.add(new AccessControlRowHolder(addressType));
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

    }

    private void updateStatus() {
        MultiStatus result = validate();
        if (!result.isOK()) {
            setErrorMessage(result.getMessage());
            setPageComplete(false);
        } else {
            setErrorMessage(null);
            setPageComplete(true);
        }
        ipAddressAclTableViewer.refresh(true);
    }

    private MultiStatus validate() {
        MultiStatus multiStatus = new MultiStatus(Activator.PLUGIN_ID, -1, null, null);
        for (AccessControlRowHolder source : this.ipConfigs) {
            String validation = source.getAclType().isValueValid();
            if (validation != null) {
                IStatus status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, "", null);
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
        this.ipConfigs = parseAclsFromText(whiteListedIps);
        this.ipAddressAclTableViewer.setInput(this.ipConfigs);
    }

    // private final static Pattern ipAddress = Pattern.compile("(\d+).)
    private PropertyListeningArrayList<AccessControlRowHolder> parseAclsFromText(List<String> whiteListedIps) {
        PropertyListeningArrayList<AccessControlRowHolder> acls = new PropertyListeningArrayList<>();

        for (final String whitelisted : whiteListedIps) {
            IPBasedAccessControlType acl = parseAcl(whitelisted);
            acls.add(new AccessControlRowHolder(acl));
        }

        acls.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                updateStatus();
            }

        });
        return acls;
    }

    private IPBasedAccessControlType parseAcl(String aclStr) {
        Matcher matcher = IP_ADDR_PATTERN.matcher(aclStr);
        if (matcher.matches()) {
            return new IPAddressType(aclStr);
        } else {
            matcher = CIDR_BLOCK_PATTERN.matcher(aclStr);
            if (matcher.matches()) {
                return new CIDRBlockType(aclStr);
            } else {
                return new UnknownAccessControlType();
            }
        }
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

    private static class IPTypeColumnEditingSupport extends EditingSupport {
        private final static List<IPBasedAccessControlType.Types> supportedTypes = Stream
                .of(IPBasedAccessControlType.Types.values()).filter(e -> e != IPBasedAccessControlType.Types.Unknown)
                .collect(Collectors.toList());
        private final static List<String> ipNotationValues = supportedTypes.stream().map(Enum::name)
                .collect(Collectors.toList());

        TableViewer ipAddressAclTableViewer;

        private IPTypeColumnEditingSupport(ColumnViewer viewer) {
            super(viewer);
            ipAddressAclTableViewer = (TableViewer) viewer;
        }

        @Override
        protected void setValue(Object element, Object value) {
            if (element instanceof AccessControlRowHolder) {
                if (value instanceof Integer) {
                    Integer index = (Integer) value;
                    if (index < ipNotationValues.size()) {
                        IPBasedAccessControlType.Types type = supportedTypes.get(index);
                        if (((AccessControlRowHolder) element).getAclType().getType() != type) {
                            IPBasedAccessControlType newType = createAclType(type);
                            ((AccessControlRowHolder) element).setAclType(newType);
                        }
                    }
                }
            }
            ipAddressAclTableViewer.update(element, null);
        }

        private IPBasedAccessControlType createAclType(IPBasedAccessControlType.Types type) {
            switch (type) {
            case CIDR:
                return new CIDRBlockType("");
            case IP:
                return new IPAddressType("");
            default:
                throw new AssertionError("Unknown ACL type");
            }
        }

        @Override
        protected Object getValue(Object element) {
            if (element instanceof AccessControlRowHolder) {
                return ((AccessControlRowHolder) element).getAclType().getType().ordinal();
            }
            return null;
        }

        @Override
        protected CellEditor getCellEditor(Object element) {
            return new ComboBoxCellEditor(ipAddressAclTableViewer.getTable(), ipNotationValues.toArray(new String[0]));
        }

        @Override
        protected boolean canEdit(Object element) {
            return true;
        }
    }

    private static class IPValueColumnEditingSupport extends EditingSupport {
        TableViewer ipAddressAclTableViewer;

        private IPValueColumnEditingSupport(ColumnViewer viewer) {
            super(viewer);
            ipAddressAclTableViewer = (TableViewer) viewer;
        }

        @Override
        protected void setValue(Object element, Object value) {
            if (element instanceof AccessControlRowHolder) {
                if (value instanceof String) {
                    ((AccessControlRowHolder) element).getAclType().setValue((String) value);
                }
            }
            ipAddressAclTableViewer.update(element, null);
        }

        @Override
        protected Object getValue(Object element) {
            if (element instanceof AccessControlRowHolder) {
                return ((AccessControlRowHolder) element).getAclType().getValue();
            }
            return null;
        }

        @Override
        protected CellEditor getCellEditor(Object element) {
            TextCellEditor textCellEditor = new TextCellEditor(this.ipAddressAclTableViewer.getTable());
//            if (((AccessControlRowHolder) element).getAclType().getType() == IPBasedAccessControlType.Types.CIDR) {
//                textCellEditor.setValidator(new ICellEditorValidator() {
//                    @Override
//                    public String isValid(Object value) {
//                        return CIDRBlockType.isValid((String) value);
//                    }
//                });
//            } else if (((AccessControlRowHolder) element).getAclType()
//                    .getType() == IPBasedAccessControlType.Types.CIDR) {
//                textCellEditor.setValidator(new ICellEditorValidator() {
//                    @Override
//                    public String isValid(Object value) {
//                        return IPAddressType.isValid((String) value);
//                    }
//                });
//            }

            return textCellEditor;
        }

        @Override
        protected boolean canEdit(Object element) {
            return true;
        }
    }

    private static class LabelProvider implements ITableLabelProvider, ITableColorProvider {
        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            return null;
        }

        @Override
        public String getColumnText(Object element, int columnIndex) {
            if (element instanceof AccessControlRowHolder) {
                switch (columnIndex) {
                case 0:
                    return ((AccessControlRowHolder) element).getAclType().getTypeLabel();
                case 1:
                    return ((AccessControlRowHolder) element).getAclType().getValue();
                }
            }
            return "";
        }

        @Override
        public void addListener(ILabelProviderListener listener) {
            // TODO Auto-generated method stub

        }

        @Override
        public void dispose() {
            // TODO Auto-generated method stub

        }

        @Override
        public boolean isLabelProperty(Object element, String property) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void removeListener(ILabelProviderListener listener) {
            // TODO Auto-generated method stub

        }

        @Override
        public Color getForeground(Object element, int columnIndex) {
            return null;
        }

        @Override
        public Color getBackground(Object element, int columnIndex) {
            if (columnIndex == 1)
            {
                if (((AccessControlRowHolder)element).getAclType().isValueValid() != null)
                {
                    return Display.getCurrent().getSystemColor(SWT.COLOR_RED);
                }
            }
            return null;
        }

    }

    private static class AccessControlRowHolder extends EventSource implements PropertyChangeListener {
        private IPBasedAccessControlType aclType;

        public AccessControlRowHolder(IPBasedAccessControlType type) {
            this.aclType = type;
            this.aclType.addPropertyChangeListener(this);
        }

        public void setAclType(IPBasedAccessControlType newType) {

            IPBasedAccessControlType oldAclType = this.aclType;
            oldAclType.removePropertyChangeListener(this);
            this.aclType = newType;
            this.aclType.addPropertyChangeListener(this);
            this.pcs.firePropertyChange("aclType", oldAclType, this.aclType);
        }

        public IPBasedAccessControlType getAclType() {
            return aclType;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("value".equals(evt.getPropertyName())) {
                this.pcs.firePropertyChange("value", evt.getOldValue(), evt.getNewValue());
            }
        }

    }

//    private static class CellEditor extends 
    private static abstract class IPBasedAccessControlType extends EventSource {
        public enum Types {
            IP("IP Address"), CIDR("CIDR Block"), Unknown("Unknown ACL Type");

            private String label;

            private Types(String label) {
                this.label = label;
            }
        }

        private final Types type;

        public IPBasedAccessControlType(Types type) {
            this.type = type;
        }

        public Types getType() {
            return type;
        }

        public String getTypeLabel() {
            return this.type.label;
        }

        public final void setValue(String value) {
            String oldValue = this.getValue();
            doSetValue(value);
            String newValue = getValue();
            if (!oldValue.equals(newValue)) {
                this.pcs.firePropertyChange("value", oldValue, newValue);
            }
        }

        protected List<String> parseOctals(String value) {
            if (value != null && !value.trim().isEmpty()) {
                Matcher matcher = IP_ADDR_PATTERN.matcher(value);
                if (matcher.matches()) {
                    List<String> octals = new ArrayList<>();
                    octals.add(matcher.group(1));
                    octals.add(matcher.group(2));
                    octals.add(matcher.group(3));
                    octals.add(matcher.group(4));
                    return octals;
                }
            }
            return Collections.emptyList();
        }

        public abstract String isValueValid();

        public abstract void doSetValue(String value);

        public abstract String getValue();
    }

    private static class IPAddressType extends IPBasedAccessControlType {
        private List<String> octalStrings;
        private String strValue;

        public IPAddressType(String asStr) {
            super(Types.IP);
            this.strValue = asStr;
            this.octalStrings = parseOctals(this.strValue);
        }

        @Override
        public String getValue() {
            return strValue;
        }

        @Override
        public void doSetValue(String value) {
            this.strValue = value;
            this.octalStrings = parseOctals(value);
        }


        @SuppressWarnings("unused")
        public String isValueValid() {
            return isValid(this.octalStrings);
        }

        public static String isValid(List<String> octals) {
            if (octals != null && octals.size() == 4)
            {
                OCTAL_LOOP: for (String octalStr : octals)
                {
                    try
                    {
                        Integer octal = Integer.valueOf(octalStr);
                        if (octal < 0 || octal > 255)
                        {
                            break OCTAL_LOOP;
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        break OCTAL_LOOP;
                    }
                }
                return null;
            }
            return "IP Address must be of the form 192.168.0.5.  Each number must be between 0 and 255.";
        }
    }

    private static class CIDRBlockType extends IPBasedAccessControlType {
        private String block;
        private List<String> octalStrings;
        private String cidrStr;

        public CIDRBlockType(String cidrStr) {
            super(Types.CIDR);
            this.cidrStr = cidrStr;
            parseCidrBlock();
        }

        private void parseCidrBlock()
        {
            if (this.cidrStr != null && !this.cidrStr.trim().isEmpty()) {
                Matcher matcher = CIDR_BLOCK_PATTERN.matcher(this.cidrStr);
                if (matcher.matches()) {
                    this.octalStrings = new ArrayList<>();
                    this.octalStrings.add(matcher.group(1));
                    this.octalStrings.add(matcher.group(2));
                    this.octalStrings.add(matcher.group(3));
                    this.octalStrings.add(matcher.group(4));
                    this.block = matcher.group(5);
                    return;
                }
            }
            this.octalStrings = Collections.emptyList();
            this.block = "";
        }
        @Override
        public String getValue() {
            return this.cidrStr;
//            if (this.octalStrings != null && !this.octalStrings.isEmpty() && this.block != null
//                    && !this.block.isEmpty()) {
//                String str = StringUtils.join(this.octalStrings, ".");
//                str += "/" + block;
//                return str;
//            }
//            return "";
        }

        @Override
        public void doSetValue(String value) {
            String oldStr = getValue();
            this.cidrStr = value;
            parseCidrBlock();
            if (!oldStr.equals(this.cidrStr)) {
                this.pcs.firePropertyChange("value", oldStr, this.cidrStr);
            }
        }

        @SuppressWarnings("unused")
        public String isValueValid() {
            return isValid(getValue());
        }

        public static String isValid(String cidrStr) {
            if (cidrStr != null && !cidrStr.trim().isEmpty()) {
                Matcher matcher = CIDR_BLOCK_PATTERN.matcher(cidrStr);
                if (matcher.matches()) {
                    return null;
                }
            }
            return "CIDR block must be of the form 10.0.0.1/16";
        }
    }

    private static class UnknownAccessControlType extends IPBasedAccessControlType {
        public UnknownAccessControlType() {
            super(Types.Unknown);
        }

        @Override
        public String getValue() {
            return "Unknown Access Control Type";
        }

        @Override
        public void doSetValue(String value) {
            throw new AssertionError();
        }

        @Override
        public String isValueValid() {
            return null;
        }
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

    private abstract static class EventSource {
        protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            this.pcs.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            this.pcs.removePropertyChangeListener(listener);
        }

    }

    private static class PropertyListeningArrayList<E extends EventSource> extends AbstractList<E>
            implements PropertyChangeListener {
        private List<E> wrapped = new ArrayList<>();
        private PropertyChangeSupport pcs = new PropertyChangeSupport(this);

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            pcs.firePropertyChange(evt);
        }

        @Override
        public E get(int index) {
            return this.wrapped.get(index);
        }

        @Override
        public int size() {
            return this.wrapped.size();
        }

        @Override
        public boolean add(E e) {
            int curIndex = this.wrapped.size();
            if (this.wrapped.add(e)) {
                e.addPropertyChangeListener(this);
                this.pcs.fireIndexedPropertyChange("acl", curIndex, null, e);
                return true;
            }
            return false;
        }

        @Override
        public E set(int index, E element) {
            E oldValue = this.wrapped.set(index, element);
            if (oldValue != null) {
                oldValue.removePropertyChangeListener(this);
            }
            this.pcs.fireIndexedPropertyChange("acl", index, oldValue, element);
            return oldValue;
        }

        @Override
        public E remove(int index) {
            E oldValue = this.wrapped.remove(index);
            if (oldValue != null) {
                oldValue.removePropertyChangeListener(this);
            }
            this.pcs.fireIndexedPropertyChange("acl", index, oldValue, null);
            return oldValue;
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            this.pcs.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            this.pcs.removePropertyChangeListener(listener);
        }

    }

    public boolean isEnableOnWayTls() {
        return this.enableOneWayTls.getSelection();
    }

    public List<String> getWhitelistedIps() {
        List<String> whitelistedIps = new ArrayList<>();
        for (AccessControlRowHolder holder : this.ipConfigs)
        {
            whitelistedIps.add(holder.getAclType().getValue());
        }
        return whitelistedIps;
    }
    
}
