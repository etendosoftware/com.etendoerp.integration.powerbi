package com.etendoerp.integration.powerbi.processes;

import com.etendoerp.integration.powerbi.data.BiLog;
import com.etendoerp.webhookevents.services.BaseWebhookService;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import java.util.Map;

public class FillLogWindow extends BaseWebhookService {

    @Override
    public void get(Map<String, String> parameter, Map<String, String> responseVars) {
        try {
            String orgId = parameter.get("organization");
            Organization org = OBDal.getInstance().get(Organization.class, orgId);
            String clientId = parameter.get("client");
            Client client = OBDal.getInstance().get(Client.class, clientId);
            BiLog log = OBProvider.getInstance().get(BiLog.class);
            log.setNewOBObject(true);
            log.setClient(client);
            log.setOrganization(org);
            log.setLogType(parameter.get("logtype"));
            log.setMessage(parameter.get("description"));
            OBDal.getInstance().save(log);
            OBDal.getInstance().flush();
        } catch (Exception e) {
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_LogCreationError"));
        }
    }
}
