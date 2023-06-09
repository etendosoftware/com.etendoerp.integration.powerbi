package com.etendoerp.integration.powerbi.eventhandler;


import javax.enterprise.event.Observes;

import com.etendoerp.integration.powerbi.data.BiQueryCustom;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;

import org.openbravo.client.kernel.event.EntityUpdateEvent;
import com.etendoerp.integration.powerbi.eventhandler.QueryValidationUtil;

class CustomQueryHandler extends EntityPersistenceEventObserver {
    private static Entity[] entities = {ModelProvider.getInstance().getEntity(BiQueryCustom.ENTITY_NAME)};
    private static final Logger logger = LogManager.getLogger();
    Property customQueryProp = entities[0].getProperty(BiQueryCustom.PROPERTY_QUERY);
    @Override
    protected Entity[] getObservedEntities() {
        return entities;
    }

    public void onUpdate(@Observes EntityUpdateEvent event) {
        if (!isValidEvent(event)) {
            return;
        }
        QueryValidationUtil.queryValidation(event, customQueryProp, logger);
    }

    public void onSave(@Observes EntityNewEvent event) {
        if (!isValidEvent(event)) {
            return;
        }
        QueryValidationUtil.queryValidation(event, customQueryProp, logger);
    }


}