package com.etendoerp.integration.powerbi.eventhandler;


import javax.enterprise.event.Observes;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;

import com.etendoerp.integration.powerbi.data.PbiQuery;

class EtendoBaseQueryHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = { ModelProvider.getInstance().getEntity(PbiQuery.ENTITY_NAME) };
  private static final Logger logger = LogManager.getLogger();

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    Property isEtendoBaseProp = entities[0].getProperty(PbiQuery.PROPERTY_ISETENDOBASE);
    boolean isEtendoBase = (boolean) event.getCurrentState(isEtendoBaseProp);
    if(isEtendoBase){
      throw new OBException("ETPBIC_CantDeleteEtendoQuery");
    }
  }
}