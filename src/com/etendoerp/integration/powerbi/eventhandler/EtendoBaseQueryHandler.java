package com.etendoerp.integration.powerbi.eventhandler;


import javax.enterprise.event.Observes;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.*;

import com.etendoerp.integration.powerbi.data.BiQuery;
import com.etendoerp.integration.powerbi.eventhandler.QueryValidationUtil;

class EtendoBaseQueryHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {ModelProvider.getInstance().getEntity(BiQuery.ENTITY_NAME)};
  private static final Logger logger = LogManager.getLogger();
  Property queryProp = entities[0].getProperty(BiQuery.PROPERTY_QUERY);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    QueryValidationUtil.queryValidation(event, queryProp, logger);
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    QueryValidationUtil.queryValidation(event, queryProp, logger);
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    Property isEtendoBaseProp = entities[0].getProperty(BiQuery.PROPERTY_ISETENDOBASE);
    boolean isEtendoBase = (boolean) event.getCurrentState(isEtendoBaseProp);

    if (isEtendoBase) {
      logger.debug("Can't delete Etendo base query");
      throw new OBException("ETPBIC_CantDeleteEtendoQuery");
    }
  }
}