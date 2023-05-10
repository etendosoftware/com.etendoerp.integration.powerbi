package com.etendoerp.integration.powerbi.processes;


import com.etendoerp.integration.powerbi.data.PbiLog;
import com.etendoerp.webhookevents.services.BaseWebhookService;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import java.util.Map;

public class FillLogWindow extends BaseWebhookService {

    @Override
    public void get(Map<String, String> parameter, Map<String, String> responseVars){
        try{
            PbiLog log = OBProvider.getInstance().get(PbiLog.class);
            log.setNewOBObject(true);
            log.setClient(OBContext.getOBContext().getCurrentClient());
            log.setCreatedBy(OBContext.getOBContext().getUser());
            log.setOrganization(OBContext.getOBContext().getCurrentOrganization());
            log.setUpdatedBy(OBContext.getOBContext().getUser());
            log.setLogType(parameter.get("logtype"));
            log.setMessage(parameter.get("description"));
            OBDal.getInstance().save(log);
            OBDal.getInstance().flush();
        } catch (Exception e){
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_LogCreationError"));
        }


    }

}