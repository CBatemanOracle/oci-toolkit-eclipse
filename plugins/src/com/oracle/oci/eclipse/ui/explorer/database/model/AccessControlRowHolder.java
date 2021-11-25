package com.oracle.oci.eclipse.ui.explorer.database.model;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class AccessControlRowHolder extends EventSource implements PropertyChangeListener {
    private AccessControlType aclType;
    private boolean isFullyLoaded;
    private boolean isNew;

    public AccessControlRowHolder(AccessControlType type, boolean isFullyLoaded) {
        this.aclType = type;
        this.aclType.addPropertyChangeListener(this);
        this.isFullyLoaded = isFullyLoaded;
    }

    public void setAclType(AccessControlType newType) {

        AccessControlType oldAclType = this.aclType;
        oldAclType.removePropertyChangeListener(this);
        this.aclType = newType;
        this.aclType.addPropertyChangeListener(this);
        this.pcs.firePropertyChange("aclType", oldAclType, this.aclType);
    }

    public AccessControlType getAclType() {
        return aclType;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("value".equals(evt.getPropertyName())) {
            this.pcs.firePropertyChange("value", evt.getOldValue(), evt.getNewValue());
        }
    }

    public boolean isFullyLoaded() {
        return isFullyLoaded;
    }

    public void setFullyLoaded(boolean isFullyLoaded) {
        boolean oldValue = this.isFullyLoaded;
        this.isFullyLoaded = isFullyLoaded;
        if (oldValue != this.isFullyLoaded) {
            this.pcs.firePropertyChange("fullyLoaded", oldValue, this.isFullyLoaded);
        }
    }

    public boolean isNew() {
        return this.isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

}