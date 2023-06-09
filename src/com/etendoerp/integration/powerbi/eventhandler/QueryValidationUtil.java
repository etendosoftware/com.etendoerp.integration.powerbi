package com.etendoerp.integration.powerbi.eventhandler;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.apache.logging.log4j.Logger;

public final class QueryValidationUtil {

    // avoid instantiation
    private QueryValidationUtil() {
    }

    public static void queryValidation(EntityPersistenceEvent event, Property prop, Logger logger) {
        String query = (String) event.getCurrentState(prop);
        if(!query.trim().toLowerCase().startsWith("select")){
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_InvalidQuerySyntax"));
        }
        logger.info("custom query created");
    }
}
