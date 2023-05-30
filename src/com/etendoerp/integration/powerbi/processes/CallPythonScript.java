package com.etendoerp.integration.powerbi.processes;

import com.etendoerp.integration.powerbi.data.BiConnection;
import com.etendoerp.integration.powerbi.data.BiDataDestination;
import com.etendoerp.integration.powerbi.data.BiExecutionVariables;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.system.Client;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;

import java.io.File;
import java.util.HashMap;
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
            OBCriteria<BiConnection> configCrit = OBDal.getInstance().createCriteria(BiConnection.class);
            configCrit.setMaxResults(1);
            BiConnection config = (BiConnection) configCrit.uniqueResult();

            if (config == null) {
                logger.logln("No config found.");
                throw new OBException(OBMessageUtils.messageBD("ETPBIC_NullConfigError")); // catch will capture
            }

            String repoPath = config.getRepositoryPath();

            HashMap<String, String> dbCredentials = new HashMap<>();

            Properties obProperties = OBPropertiesProvider.getInstance().getOpenbravoProperties();

            if (!obProperties.containsKey("context.url")) {
                throw new OBException(OBMessageUtils.messageBD("ETPBIC_ContextUrlNotFound"));
            }

            String url = obProperties.getProperty("context.url");

            String bbddSid = obProperties.containsKey("bbdd.readonly.sid")
                    ? obProperties.getProperty("bbdd.readonly.sid")
                    : obProperties.getProperty("bbdd.sid");

            String bbddUser = obProperties.containsKey("bbdd.readonly.user")
                    ? obProperties.getProperty("bbdd.readonly.user")
                    : obProperties.getProperty("bbdd.user");

            String bbddPassword = obProperties.containsKey("bbdd.readonly.password")
                    ? obProperties.getProperty("bbdd.readonly.password")
                    : obProperties.getProperty("bbdd.password");

            String bbddUrl = obProperties.containsKey("bbdd.readonly.url")
                    ? obProperties.getProperty("bbdd.readonly.url")
                    : obProperties.getProperty("bbdd.url");

            String[] parts = bbddUrl.split("://|:");
            String bbddHost = parts[2];
            String bbddPort = parts[3];

            dbCredentials.put("bbdd_sid", bbddSid);
            dbCredentials.put("bbdd_user", bbddUser);
            dbCredentials.put("bbdd_password", bbddPassword);
            dbCredentials.put("bbdd_host", bbddHost);
            dbCredentials.put("bbdd_port", bbddPort);


            OBCriteria<BiDataDestination> dataDestCrit = OBDal.getInstance().createCriteria(BiDataDestination.class);
            dataDestCrit.add(Restrictions.eq(BiDataDestination.PROPERTY_BICONNECTION, config));
            List<BiDataDestination> dataDestList = dataDestCrit.list();

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

                log.debug("calling function to execute script");
                logger.logln("executing " + dataDest.getScriptPath());
                callPythonScript(repoPath, dataDest.getScriptPath(), dbCredentials, url, clientStr, filewsUser);
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

    public void callPythonScript(String repositoryPath, String scriptName, HashMap<String, String> dbCredentials, String url, String client, String filewsUser) {

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
                    dbCredentials.get("bbdd_sid"),
                    dbCredentials.get("bbdd_user"),
                    dbCredentials.get("bbdd_password"),
                    dbCredentials.get("bbdd_host"),
                    dbCredentials.get("bbdd_port"),
                    url,
                    client,
                    clientObj.getId(),
                    filewsUser);
            pb.directory(new File(repositoryPath));
            pb.redirectErrorStream(true);
            log.debug("executing python script: " + scriptName);
            pb.start();
        } catch (Exception e) {
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_ExecutePythonError"));
        }
    }

}
