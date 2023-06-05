package com.etendoerp.integration.powerbi.processes;

import com.etendoerp.integration.powerbi.data.BiConnection;
import com.etendoerp.integration.powerbi.data.BiDataDestination;
import com.etendoerp.integration.powerbi.data.BiExecutionVariables;

import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedwebhookAccess;
import com.etendoerp.webhookevents.data.DefinedwebhookToken;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.ad.system.Client;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;

import java.io.File;
import java.util.List;
import java.util.Properties;

public class CallPythonScript extends DalBaseProcess {

    private static final Logger log = Logger.getLogger(CallPythonScript.class);
    private static Client clientObj = OBContext.getOBContext().getCurrentClient();


    @Override
    protected void doExecute(ProcessBundle bundle) throws Exception {
        log.info("java process running");

        ProcessLogger logger = bundle.getLogger();
        logger.logln("Process started");
        try {
            OBContext.setAdminMode(true);
            Organization contextOrg = OBContext.getOBContext().getCurrentOrganization();
            StringBuilder argsStr = new StringBuilder();

            OrganizationStructureProvider orgProvider = new OrganizationStructureProvider();
            Organization orgHavingConn = contextOrg;
            int parentListCount = orgProvider.getParentList(contextOrg.getId(), true).size();
            BiConnection config = getBiConnection(orgProvider, orgHavingConn, parentListCount, logger);

            // get webhook name
            OBCriteria<DefinedWebHook> dwCrit = OBDal.getInstance().createCriteria(DefinedWebHook.class);
            dwCrit.add(Restrictions.eq(DefinedWebHook.PROPERTY_ID, config.getWebhook().getId()));
            DefinedWebHook dw = (DefinedWebHook) dwCrit.setMaxResults(1).uniqueResult();

            checkWebhookNull(dw == null, "ETPBIC_NoWebhookError");

            // get webhook access
            OBCriteria<DefinedwebhookAccess> dwaCrit = OBDal.getInstance().createCriteria(DefinedwebhookAccess.class);
            dwaCrit.add(Restrictions.eq(DefinedwebhookAccess.PROPERTY_SMFWHEDEFINEDWEBHOOK, dw));
            // suppose to have just 1 dw access active.
            DefinedwebhookAccess dwa = (DefinedwebhookAccess) dwaCrit.setMaxResults(1).uniqueResult();

            checkWebhookAccessNull(dwa == null, "ETPBIC_NoWebhookAccessError");

            // get webhook token
            DefinedwebhookToken dwt = dwa.getSmfwheDefinedwebhookToken();

            checkWebhookTokenNull(dwt == null, "ETPBIC_NoWebhookTokenError");

            String whName = dw.getName();
            String whToken = dwt.getAPIKey();

            String repoPath = config.getRepositoryPath();
            Properties obProperties = OBPropertiesProvider.getInstance().getOpenbravoProperties();

            checkContextUrlExists(obProperties.containsKey("context.url"), "ETPBIC_ContextUrlNotFound");

            String url = obProperties.getProperty("context.url");

            String bbddSid = getBbddSid(obProperties);

            String bbddUser = getBbddUser(obProperties);

            String bbddPassword = getBbddPassword(obProperties);

            String bbddUrl = getBbddUrl(obProperties);

            String[] parts = bbddUrl.split("://|:");
            String bbddHost = parts[2];
            String bbddPort = parts[3];

            argsStr.append(bbddSid + ",");
            argsStr.append(bbddUser + ",");
            argsStr.append(bbddPassword + ",");
            argsStr.append(bbddHost + ",");
            argsStr.append(bbddPort + ",");
            argsStr.append(url + ",");
            argsStr.append(clientObj.getId() + ",");
            argsStr.append(contextOrg.getId() + ",");
            argsStr.append(whName + ",");
            argsStr.append(whToken + ",");

            OBCriteria<BiDataDestination> dataDestCrit = OBDal.getInstance().createCriteria(BiDataDestination.class);
            dataDestCrit.add(Restrictions.eq(BiDataDestination.PROPERTY_BICONNECTION, config));
            List<BiDataDestination> dataDestList = dataDestCrit.list();
            if (dataDestList.isEmpty()) {
                throw new OBException(OBMessageUtils.messageBD("ETPBIC_NoDataDestError"));
            }

            for (BiDataDestination dataDest : dataDestList) {
                OBCriteria<BiExecutionVariables> execVarCrit = OBDal.getInstance().createCriteria(BiExecutionVariables.class);
                execVarCrit.add(Restrictions.eq(BiExecutionVariables.PROPERTY_BIDATADESTINATION, dataDest));
                List<BiExecutionVariables> execVarList = execVarCrit.list();
                String filewsUser = "";
                String clientStr = "";
                for (BiExecutionVariables execVar : execVarList) {
                    if (StringUtils.isNotEmpty(execVar.getVariable()) && StringUtils.equals("client", execVar.getVariable().toLowerCase())) {
                        clientStr = execVar.getValue();
                    } else if (StringUtils.isNotEmpty(execVar.getVariable()) && StringUtils.equals("filews_user", execVar.getVariable().toLowerCase())) {
                        filewsUser = execVar.getValue();
                    }
                }

                if (StringUtils.isEmpty(clientStr) || StringUtils.isEmpty(filewsUser)) {
                    throw new OBException(OBMessageUtils.messageBD("ETPBIC_VariablesNotFoundError"));
                }
                argsStr.append(clientStr + ",");
                argsStr.append(filewsUser + ",");

                log.debug("calling function to execute script");
                callPythonScript(repoPath, dataDest.getScriptPath(), argsStr.toString());
                logger.logln("executing " + dataDest.getScriptPath());
            }

        } catch (OBException e) {
            logger.logln(e.getMessage());
            throw new OBException(e.getMessage());
        } catch (Exception e) {
            logger.logln(e.getMessage());
            throw new OBException(e.getMessage());
        } finally {
            log.debug("java process end");
            OBContext.restorePreviousMode();
        }

    }

    private static String getBbddUrl(Properties obProperties) {
        return obProperties.containsKey("bbdd.readonly.url")
                ? obProperties.getProperty("bbdd.readonly.url")
                : obProperties.getProperty("bbdd.url");
    }

    private static String getBbddPassword(Properties obProperties) {
        return obProperties.containsKey("bbdd.readonly.password")
                ? obProperties.getProperty("bbdd.readonly.password")
                : obProperties.getProperty("bbdd.password");
    }

    private static String getBbddUser(Properties obProperties) {
        return obProperties.containsKey("bbdd.readonly.user")
                ? obProperties.getProperty("bbdd.readonly.user")
                : obProperties.getProperty("bbdd.user");
    }

    private static String getBbddSid(Properties obProperties) {
        return obProperties.containsKey("bbdd.readonly.sid")
                ? obProperties.getProperty("bbdd.readonly.sid")
                : obProperties.getProperty("bbdd.sid");
    }

    private static void checkContextUrlExists(boolean contextUrlExists, String contextUrlNotFoundMsg) {
        if (!contextUrlExists) {
            throw new OBException(OBMessageUtils.messageBD(contextUrlNotFoundMsg));
        }
    }

    private static void checkWebhookTokenNull(boolean isNull, String noWebhookTokenErrorMsg) {
        if (isNull) {
            throw new OBException(OBMessageUtils.messageBD(noWebhookTokenErrorMsg));
        }
    }

    private static void checkWebhookAccessNull(boolean isNull, String noWebhookAccessErrorMsg) {
        if (isNull) {
            throw new OBException(OBMessageUtils.messageBD(noWebhookAccessErrorMsg));
        }
    }

    private static void checkWebhookNull(boolean isNull, String noWebhookErrorMsg) {
        if (isNull) {
            throw new OBException(OBMessageUtils.messageBD(noWebhookErrorMsg));
        }
    }

    private static BiConnection getBiConnection(OrganizationStructureProvider orgProvider, Organization orgHavingConn, int parentListCount, ProcessLogger logger) {
        BiConnection conf = null;
        for (int i = 0; i < parentListCount && conf == null; i++) {
            OBCriteria<BiConnection> configCrit = OBDal.getInstance().createCriteria(BiConnection.class);
            configCrit.add(Restrictions.eq(BiConnection.PROPERTY_ORGANIZATION, orgHavingConn));
            configCrit.setMaxResults(1);
            conf = (BiConnection) configCrit.uniqueResult();
            if (conf == null) {
                orgHavingConn = orgProvider.getParentOrg(orgHavingConn);
            }
        }
        if (conf == null) {
            logger.logln("No config found for client/organization.");
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_NullConfigError")); // catch will capture
        }
        return conf;
    }

    public void callPythonScript(String repositoryPath, String scriptName, String argsStr) {

        // repositoryPath is supposed to be a directory
        repositoryPath = repositoryPath.endsWith("/") ? repositoryPath : repositoryPath + "/";
        scriptName = scriptName.endsWith(".py") ? scriptName : scriptName + ".py";
        String finalScriptPath = repositoryPath + scriptName;
        File file = new File(finalScriptPath);
        if (!file.exists()) {
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_ScriptNotFound"));
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", finalScriptPath,
                    argsStr);
            pb.directory(new File(repositoryPath));
            pb.redirectErrorStream(true);
            log.debug("executing python script: " + scriptName);
            pb.start();
        } catch (Exception e) {
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_ExecutePythonError"));
        }
    }

}
