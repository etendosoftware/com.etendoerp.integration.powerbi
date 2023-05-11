package com.etendoerp.integration.powerbi.processes;

import com.etendoerp.integration.powerbi.data.PbiConnection;
import com.etendoerp.integration.powerbi.data.PbiDataDestination;
import org.apache.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;
import org.redisson.misc.Hash;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class CallPythonScript extends DalBaseProcess {

    private static final Logger log = Logger.getLogger(CallPythonScript.class);

    @Override
    protected void doExecute(ProcessBundle bundle) throws Exception {
        log.info("java process running");
        final String DEFAULT_URL="localhost:8080/etendo";

        ProcessLogger logger = bundle.getLogger();
        try {
            OBContext.setAdminMode(true);
            OBCriteria<PbiConnection> configCrit = OBDal.getInstance().createCriteria(PbiConnection.class);
            configCrit.setMaxResults(1);
            PbiConnection config = (PbiConnection) configCrit.uniqueResult();

            String repoPath = config.getRepositoryPath();

            if(config==null) {
                throw new OBException(OBMessageUtils.messageBD("ETPBIC_NullConfigError")); // catch will capture
            }
            HashMap<String, String> dbCredentials = new HashMap<>();

            Properties obProperties = OBPropertiesProvider.getInstance().getOpenbravoProperties();

            String url = obProperties.getProperty("context.url", DEFAULT_URL);

            String bbdd_sid = obProperties.containsKey("bbdd.readonly.sid")
                    ? obProperties.getProperty("bbdd.readonly.sid")
                    : obProperties.getProperty("bbdd.sid");

            String bbdd_user = obProperties.containsKey("bbdd.readonly.user")
                    ? obProperties.getProperty("bbdd.readonly.user")
                    : obProperties.getProperty("bbdd.user");

            String bbdd_password = obProperties.containsKey("bbdd.readonly.password")
                    ? obProperties.getProperty("bbdd.readonly.password")
                    : obProperties.getProperty("bbdd.password");

            String bbdd_url = obProperties.containsKey("bbdd.readonly.url")
                    ? obProperties.getProperty("bbdd.readonly.url")
                    : obProperties.getProperty("bbdd.url");

            String[] parts = bbdd_url.split("://|:");
            String bbdd_host = parts[2];
            String bbdd_port = parts[3];

            dbCredentials.put("bbdd_sid", bbdd_sid);
            dbCredentials.put("bbdd_user", bbdd_user);
            dbCredentials.put("bbdd_password", bbdd_password);
            dbCredentials.put("bbdd_host", bbdd_host);
            dbCredentials.put("bbdd_port", bbdd_port);

            OBCriteria<PbiDataDestination> dataDestCrit = OBDal.getInstance().createCriteria(PbiDataDestination.class);
            dataDestCrit.add(Restrictions.eq(PbiDataDestination.PROPERTY_PBICONNECTION, config));
            List<PbiDataDestination> dataDestList = dataDestCrit.list();
            for(PbiDataDestination dataDest : dataDestList){
                log.debug("calling function to execute script");
                callPythonScript(repoPath, dataDest.getScriptPath(), dbCredentials, url);
            }

        } catch (OBException e) {
            throw new OBException(e.getMessage());
        }catch(Exception e){
            throw new OBException(e.getMessage());
        }
        finally {
            log.debug("java process end");
            OBContext.restorePreviousMode();
        }

    }

    public void callPythonScript(String repositoryPath, String scriptName, HashMap<String, String> dbCredentials, String url) {

        // repositoryPath is supposed to be a directory
        repositoryPath = repositoryPath.endsWith("/") ? repositoryPath : repositoryPath + "/";
        scriptName = scriptName.endsWith(".py") ? scriptName : scriptName + ".py";
        String finalScriptPath = repositoryPath + scriptName;
        File file = new File(finalScriptPath);
        if (!file.exists()){
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_ScriptNotFound"));
        }
        try{
            ProcessBuilder pb = new ProcessBuilder("python3", finalScriptPath,
                    dbCredentials.get("bbdd_sid"),
                    dbCredentials.get("bbdd_user"),
                    dbCredentials.get("bbdd_password"),
                    dbCredentials.get("bbdd_host"),
                    dbCredentials.get("bbdd_port"),
                    url);
            pb.directory(new File(repositoryPath));
            pb.redirectErrorStream(true);
            log.debug("executing python script: " + scriptName);
            Process p = pb.start();
        } catch(Exception e){
            throw new OBException(OBMessageUtils.messageBD("ETPBIC_ExecutePythonError"));
        }
    }

}
