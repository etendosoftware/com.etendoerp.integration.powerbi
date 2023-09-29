package com.etendoerp.integration.powerbi.eventhandler;

import com.etendoerp.integration.powerbi.data.BiConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private static Entity[] entities = { ModelProvider.getInstance().getEntity(BiConnection.ENTITY_NAME) };
    private static final Logger logger = LogManager.getLogger();

    Property csvSeparatorProp = entities[0].getProperty(BiConnection.PROPERTY_CSVSEPARATOR);

    @Override
    protected Entity[] getObservedEntities() {
        return entities;
    }

    public void onUpdate(@Observes EntityUpdateEvent event) {
        if (!isValidEvent(event)) {
            return;
        }
        validateDelimiter(event, csvSeparatorProp);
    }

    public void onSave(@Observes EntityNewEvent event) {
        if (!isValidEvent(event)) {
            return;
        }
        validateDelimiter(event, csvSeparatorProp);
    }

    public void validateDelimiter(EntityPersistenceEvent event, Property prop) {
        String csvSeparator = (String) event.getCurrentState(prop);
        if (csvSeparator.length() != 1) {
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_InvalidDelimiter"));
        }
    }
}
