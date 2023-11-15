package com.etendoerp.integration.powerbi.eventhandler;

import com.etendoerp.integration.powerbi.data.BiExecutionVariables;
import org.apache.commons.lang.StringUtils;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.base.exception.OBException;

import javax.enterprise.event.Observes;

class DelimiterValidator extends EntityPersistenceEventObserver {
    private static Entity[] entities = { ModelProvider.getInstance().getEntity(BiExecutionVariables.ENTITY_NAME) };

    private static final String CSV_SEPARATOR_VARIABLE_NAME = "csv_separator";

    Property csvSeparatorValueProp = entities[0].getProperty(BiExecutionVariables.PROPERTY_VARIABLE);
    Property csvSeparatorNameProp = entities[0].getProperty(BiExecutionVariables.PROPERTY_VALUE);

    @Override
    protected Entity[] getObservedEntities() {
        return entities;
    }

    public void onUpdate(@Observes EntityUpdateEvent event) {
        if (!isValidEvent(event)) {
            return;
        }
        validateDelimiter(event, csvSeparatorNameProp, csvSeparatorValueProp);
    }

    public void onSave(@Observes EntityNewEvent event) {
        if (!isValidEvent(event)) {
            return;
        }
        validateDelimiter(event, csvSeparatorNameProp, csvSeparatorValueProp);
    }

    public void validateDelimiter(EntityPersistenceEvent event, Property valueProp, Property nameProp) {
        String csvSeparatorName = (String) event.getCurrentState(nameProp);
        String csvSeparatorValue = (String) event.getCurrentState(valueProp);
        if (StringUtils.equalsIgnoreCase(csvSeparatorName, CSV_SEPARATOR_VARIABLE_NAME) && csvSeparatorValue.length() != 1) {
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_InvalidDelimiter"));
        }
    }
}
