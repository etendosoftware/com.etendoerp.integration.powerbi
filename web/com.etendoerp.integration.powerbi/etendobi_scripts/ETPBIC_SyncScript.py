import sys # get Java parameters
import subprocess # bash commands
import psycopg2 # connect to db
import os # handle dirs
import pandas as pd # merge dataframes
import logging # log
from logging.handlers import TimedRotatingFileHandler
import requests # make get req
import shutil # rm output dir
from datetime import datetime

argsStr = sys.argv[1]
args = argsStr.split(",")

BBDD_SID = args[0]
BBDD_HOST = args[1]
BBDD_PORT = args[2]
AD_CLIENT_ID = args[3] # client executing the process
AD_ORG_ID = args[4] # organization executing the process
WEBHOOK_NAME = args[5]
WEBHOOK_APIKEY = args[6]
org_name = args[7] # organization name for creating folder
csv_separator = args[8]
client = args[9]
USER = args[10]
IP = args[11]
PORT = args[12]
PATH = args[13]
BBDD_USER = args[14]
BBDD_PASSWORD = args[15]
PRIVATE_KEY_PATH = args[16]
URL = args[17]
CLIENT_PREFIX = client[:3]
CLIENT_FILTER = 'ad_client_id as clientid'
ORG_FILTER = 'ad_org_id as orgid'


# replace spaces and & in for folder creation
org_name = org_name.replace(" ", "_")
org_name = org_name.replace("&", "+")
client = client.replace(" ", "_")
client = client.replace("&", "+")

CURRENT_DIR = os.getcwd()
FOLDER_DIR = os.path.join(CURRENT_DIR, f'{client}');
OUTPUT_DIR = os.path.join(FOLDER_DIR, f'{org_name}_output')
TMP_DIR = os.path.join(FOLDER_DIR, f'{org_name}_tmp')
LOG_DIR = os.path.join(FOLDER_DIR, f'{org_name}_logs')
LOG_FILE = os.path.join(LOG_DIR, f'syncScript.log.{datetime.now()}')

os.makedirs(LOG_DIR, exist_ok=True)

# configure logging
logging.basicConfig(filename=LOG_FILE, format='%(asctime)s %(message)s', level=logging.DEBUG)
LOGGER = logging.getLogger()

# set output/ directory
if not os.path.exists(OUTPUT_DIR):
    LOGGER.debug(f"creating {org_name}_output directory")
    os.makedirs(OUTPUT_DIR)

LOGGER.debug(f"creating {org_name}_tmp directory")
shutil.rmtree(TMP_DIR, ignore_errors=True)
os.makedirs(TMP_DIR)

WEBHOOKS_URL = URL + "/webhooks/"

LOGGER.debug("connecting to database")
try:
    # connect to db
    CONN = psycopg2.connect(database=BBDD_SID, user=BBDD_USER, password=BBDD_PASSWORD, host=BBDD_HOST, port=BBDD_PORT)
    
    # set session to readonly
    CONN.set_session(readonly=True)

    # open cursor to execute queries
    CUR = CONN.cursor()

    LOGGER.debug("fetching base queries and their customizations")
    # fetch all queries from etpbic_query
    CUR.execute(f"""
                SELECT q.name, q.query, q.isetendobase, qc.query AS cust_query FROM etpbic_query q
                LEFT JOIN etpbic_query_custom qc ON q.etpbic_query_id = qc.etpbic_query_id 
                WHERE q.isactive='Y' AND q.ad_client_id = '{AD_CLIENT_ID}'
                AND (ad_isorgincluded(q.ad_org_id, '{AD_ORG_ID}', '{AD_CLIENT_ID}')<>-1 OR q.ad_org_id = '0')""")

    # iterate over the rows and execute the queries
    for row in CUR.fetchall():
        name = row[0]
        query = row[1]
        isetendobase = row[2]
        custom_query = row[3]

        isValidQuery = True
        
        # filter by ad_client_id
        if CLIENT_FILTER.upper() not in query.upper():            
            isValidQuery = False
            LOGGER.debug(f"{name} query does not contains ad_client_id column with 'ClientID' alias.")
            
        # filter by ad_client_id
        if ORG_FILTER.upper() not in query.upper():            
            isValidQuery = False
            LOGGER.debug(f"{name} query does not contains ad_org_id column with 'OrgID' alias.")
            
        if not isValidQuery:
            continue;

        # executes query
        LOGGER.debug(f'executing \'{name}\' base query')
        
        filtered_query = f"""
        SELECT * FROM ({query}) AS q WHERE q.clientid = '{AD_CLIENT_ID}'
        AND (ad_isorgincluded(q.orgid, '{AD_ORG_ID}', '{AD_CLIENT_ID}')<>-1 OR q.orgid = '0')""" 

        CUR.execute(filtered_query)
        result1 = CUR.fetchall() 

        # set original query dataframe
        df1 = pd.DataFrame(result1, columns=[desc[0] for desc in CUR.description])

        df2 = None
        # executes custom query
        if custom_query:
            LOGGER.debug("executing " + f'\'{name}\'custom query')
            CUR.execute(custom_query)
            result2 = CUR.fetchall()
            # set custom query dataframe
            df2 = pd.DataFrame(result2, columns=[desc[0] for desc in CUR.description])
            cols = []
            pk = df1.columns[0]
            for c in df1.columns:
                if c in df2.columns:
                    cols.append(c) 
                    if pk != c:
                        del df1[c]
            df3 = df2[cols]
            LOGGER.debug("BASE merging for " + f'\'{name}\' query')
            # BASE
            if pk in df3:
                df4 = pd.merge(df1, df3, how='left', on=[pk, pk])
                LOGGER.debug("creating BASE_" + f'{name}.csv file')
                df4.to_csv(os.path.join(TMP_DIR, 'BASE_' + f'{name}.csv'), index = False, sep=csv_separator)
            else:
                LOGGER.debug("pk not found")

            LOGGER.debug("FULL merging for " + f'\'{name}\' query')
            # FULL
            if pk in df2:
                df4 = pd.merge(df1, df2, how='left', on=[pk, pk])
                LOGGER.debug("creating FULL_" + f'{name}.csv file')
                df4.to_csv(os.path.join(TMP_DIR, 'FULL_' + f'{name}.csv'), index = False, sep=csv_separator)
            else:
                LOGGER.debug('pk not found')
        else:
            LOGGER.debug("custom query not found for original query " + f'\'{name}\'' + ".")

        PREFIX = CLIENT_PREFIX + "_" if (isetendobase == 'N') else "EBI_"
        df1.to_csv(os.path.join(TMP_DIR, PREFIX + f'{name}.csv'), index = False, sep=csv_separator)
        
    # Test server connection
    TEST_COMMAND = f'ssh {"-i" if PRIVATE_KEY_PATH != "" else ""} {PRIVATE_KEY_PATH} -o StrictHostKeyChecking=no {USER}@{IP} -p {PORT} "echo 1"'
    TEST_RESULT = subprocess.run(TEST_COMMAND, shell=True, capture_output=True)
    TEST_OUTPUT = TEST_RESULT.stdout.strip().decode()
    if TEST_OUTPUT == "1":
        LOGGER.debug(f'{IP} is reachable')
    else:
        LOGGER.debug(f'{IP} is not reachable')
        raise Exception(f'{IP} is not reachable')


    # Check if PATH exists in the server
    PATH_VERIFICATION = f'ssh {"-i" if PRIVATE_KEY_PATH != "" else ""} {PRIVATE_KEY_PATH} -o StrictHostKeyChecking=no {USER}@{IP} -p {PORT} "test -d \'{PATH}\' && echo 1"'
    VERIFICATION_RESULT = subprocess.run(PATH_VERIFICATION, shell=True, capture_output=True)
    VERIFICATION_OUTPUT = VERIFICATION_RESULT.stdout.strip().decode()
    
    if VERIFICATION_OUTPUT == "1":
        LOGGER.debug(f"{PATH} exists in the server")
    else:
        LOGGER.debug(f"{PATH} does not exist in the server")
        raise Exception(f"{PATH} does not exist in the server")
    
    # Create client and org directories if not exists
    PATH_CREATION = f'ssh {"-i" if PRIVATE_KEY_PATH != "" else ""} {PRIVATE_KEY_PATH} -o StrictHostKeyChecking=no {USER}@{IP} -p {PORT} "test -d {PATH}{client}/{org_name} && echo 1"'
    PATH_CREATION_RESULT = subprocess.run(PATH_CREATION, shell=True, capture_output=True)
    PATH_CREATION_OUTPUT = PATH_CREATION_RESULT.stdout.strip().decode()

    
    if PATH_CREATION_OUTPUT != "1":
        CREATE_PATH = f'ssh {"-i" if PRIVATE_KEY_PATH != "" else ""} {PRIVATE_KEY_PATH} -o StrictHostKeyChecking=no {USER}@{IP} -p {PORT} "mkdir -p {PATH}{client}/{org_name}"'
        subprocess.run(CREATE_PATH, shell=True, capture_output=True)
        LOGGER.debug(f"created {PATH}{client}/{org_name} in the server")

    # SEND FILES TO THE SERVER
    filesAmt = len(os.listdir(TMP_DIR))
    LOGGER.debug(f"sending {filesAmt} files to cloud for client {client} to {IP}")
    DST = f'{USER}@{IP}:{PATH}{client}/{org_name}/' 
    OPTIONS = f'-av {"--delete" if filesAmt > 0 else ""} -e "ssh -p {PORT} {"-i" if PRIVATE_KEY_PATH != "" else ""} {PRIVATE_KEY_PATH} -o StrictHostKeyChecking=no"' # do not delete if no files being uploaded
    COMMAND = f'rsync {OPTIONS} {TMP_DIR}/ {DST} >> "{LOG_DIR}/rsync_$(date +%Y-%m-%d).log" 2>&1'
    LOGGER.debug("executing rsync")
    subprocess.run(COMMAND, shell=True, check=True)

    # Remove tmp directory and rename output directory
    LOGGER.debug("setting tmp directory as output directory")
    shutil.rmtree(OUTPUT_DIR)
    os.rename(TMP_DIR, OUTPUT_DIR)

    # Send logs to BI
    logtype="Success"
    lines = ""
    description = ""
    with open(LOG_FILE, 'r') as log:
        lines = log.readlines()
    for line in lines:
        description += line.strip() + '\n'
    LOGGER.debug("sending logs to BI Logs window")
    LOGGER.debug(f"DESCRIPTION: {description}")

    params = {
        'name': WEBHOOK_NAME,
        'apikey': WEBHOOK_APIKEY,
        'description': description,
        'organization': AD_ORG_ID,
        'logtype': logtype,
        'rule': '649BBFA37BA74FA59AEBE7F28524B0C8'
    }
    response = requests.get(WEBHOOKS_URL, params=params)

except psycopg2.Error as e:
    LOGGER.debug("Database related error: " + e.args[0])
    logtype="Error"
    lines=""
    description=""
    with open(LOG_FILE, 'r') as log:
        lines = log.readlines()
    for line in lines:
        description += line.strip() + '\n'
    shutil.rmtree(TMP_DIR)
    
    params = {
        'name': WEBHOOK_NAME,
        'apikey': WEBHOOK_APIKEY,
        'description': description,
        'organization': AD_ORG_ID,
        'logtype': logtype,
        'rule': '649BBFA37BA74FA59AEBE7F28524B0C8'
    }
    response = requests.get(WEBHOOKS_URL, params=params)
except Exception as e:
    LOGGER.debug("Error occurred: " + e.args[0])
    logtype="Error"
    lines=""
    description=""
    with open(LOG_FILE, 'r') as log:
        lines = log.readlines()
    for line in lines:
        description += line.strip() + '\n'
    shutil.rmtree(TMP_DIR)
    params = {
        'name': WEBHOOK_NAME,
        'apikey': WEBHOOK_APIKEY,
        'description': description,
        'organization': AD_ORG_ID,
        'logtype': logtype,
        'rule': '649BBFA37BA74FA59AEBE7F28524B0C8'
    }
    response = requests.get(WEBHOOKS_URL, params=params)
finally:
    LOGGER.debug("closing database connections")
    CUR.close()
    CONN.close()
