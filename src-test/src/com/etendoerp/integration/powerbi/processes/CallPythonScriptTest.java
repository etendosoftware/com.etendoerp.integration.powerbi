package com.etendoerp.integration.powerbi.processes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.DalContextListener;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.security.OrganizationStructureProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.ad.system.Client;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessLogger;

import com.etendoerp.integration.powerbi.data.BiConnection;
import com.etendoerp.integration.powerbi.data.BiDataDestination;

/**
 * Test class for CallPythonScript functionality which handles Python script execution
 * in the Power BI integration context. Tests cover script path resolution, database
 * connection handling, and various error scenarios.
 *
 * @see CallPythonScript
 */
@RunWith(MockitoJUnitRunner.class)
public class CallPythonScriptTest extends WeldBaseTest {

  private static final String DEFAULT_TEST_PATH = "/test/path";
  private static final String RESOURCE_PATH = "path/to/something/";
  private static final String NORMAL_URL = "normal-url";

  /**
   * Rule for handling expected exceptions in tests.
   * Configures the expected exception type and message for validation during test execution.
   */
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock private ProcessBundle processBundle;
  @Mock private ProcessLogger processLogger;
  @Mock private OBContext obContext;
  @Mock private Organization organization;
  @Mock private OrganizationStructureProvider orgProvider;
  @Mock private OBDal obDal;
  @Mock private ServletContext servletContext;
  @Mock private Client client;
  @Mock private Properties properties;
  @Mock private OBPropertiesProvider propertiesProvider;

  @Mock private OBCriteria<BiConnection> biConnectionCriteria;

  @Mock private BiConnection biConnection;
  @Mock private BiDataDestination biDataDestination;

  private MockedStatic<OBContext> obContextStatic;
  private MockedStatic<OBDal> obDalStatic;
  private MockedStatic<OBPropertiesProvider> propertiesProviderStatic;
  private MockedStatic<DalContextListener> dalContextListenerStatic;
  private MockedStatic<OBMessageUtils> obMessageUtilsStatic;

  private CallPythonScript callPythonScript;
  private Method resolvePathDelimiterMethod;
  private Method resolveEmptyPortMethod;
  private Method getBbddUrlMethod;
  private Method getBbddPasswordMethod;
  private Method getBbddUserMethod;
  private Method getBbddSidMethod;
  private Method checkNullMethod;
  private Method getBiConnectionMethod;


  /**
   * Sets up the test environment before each test execution.
   * Initialize mocks, reflected methods, and basic test configurations.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setUp() throws Exception {
    callPythonScript = spy(new CallPythonScript());

    obContextStatic = mockStatic(OBContext.class);
    obDalStatic = mockStatic(OBDal.class);
    propertiesProviderStatic = mockStatic(OBPropertiesProvider.class);
    dalContextListenerStatic = mockStatic(DalContextListener.class);
    obMessageUtilsStatic = mockStatic(OBMessageUtils.class);

    setupReflectionMethods();

    setupBasicMocks();

    setupCriteriaAndDataMocks();

    setupProperties();
  }

  /**
   * Configures reflection access to private methods of CallPythonScript class for testing purposes.
   * This setup is necessary to test private methods and verify their behavior in isolation.
   * Makes all required methods accessible and stores them in class fields for use in test methods.
   *
   * @throws Exception if any reflection setup fails
   */
  private void setupReflectionMethods() throws Exception {
    resolvePathDelimiterMethod = CallPythonScript.class.getDeclaredMethod("resolvePathDelimiter", String.class);
    resolvePathDelimiterMethod.setAccessible(true);

    resolveEmptyPortMethod = CallPythonScript.class.getDeclaredMethod("resolveEmptyPort", String.class);
    resolveEmptyPortMethod.setAccessible(true);

    getBbddUrlMethod = CallPythonScript.class.getDeclaredMethod("getBbddUrl", Properties.class);
    getBbddUrlMethod.setAccessible(true);

    getBbddPasswordMethod = CallPythonScript.class.getDeclaredMethod("getBbddPassword", Properties.class);
    getBbddPasswordMethod.setAccessible(true);

    getBbddUserMethod = CallPythonScript.class.getDeclaredMethod("getBbddUser", Properties.class);
    getBbddUserMethod.setAccessible(true);

    getBbddSidMethod = CallPythonScript.class.getDeclaredMethod("getBbddSid", Properties.class);
    getBbddSidMethod.setAccessible(true);

    checkNullMethod = CallPythonScript.class.getDeclaredMethod("checkNull", boolean.class, String.class);
    checkNullMethod.setAccessible(true);

    getBiConnectionMethod = CallPythonScript.class.getDeclaredMethod("getBiConnection",
        OrganizationStructureProvider.class, Organization.class, int.class, ProcessLogger.class);
    getBiConnectionMethod.setAccessible(true);
  }

  /**
   * Configures basic mock objects and their behavior for the test environment.
   * Sets up core system components including process bundle, context, organization,
   * and servlet context with their expected default behaviors.
   */
  private void setupBasicMocks() {
    when(processBundle.getLogger()).thenReturn(processLogger);

    obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
    when(obContext.getCurrentOrganization()).thenReturn(organization);
    when(organization.getId()).thenReturn("testOrgId");
    when(obContext.getCurrentClient()).thenReturn(client);
    when(client.getId()).thenReturn("testClientId");

    dalContextListenerStatic.when(DalContextListener::getServletContext).thenReturn(servletContext);
    when(servletContext.getRealPath(anyString())).thenReturn(DEFAULT_TEST_PATH);
  }

  /**
   * Configures database criteria and data-related mock objects.
   * Sets up the behavior of database queries and connections required for testing,
   * including BiConnection criteria and expected query results.
   */
  private void setupCriteriaAndDataMocks() {
    obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

    when(obDal.createCriteria(BiConnection.class)).thenReturn(biConnectionCriteria);

    when(biConnectionCriteria.add(any())).thenReturn(biConnectionCriteria);
    when(biConnectionCriteria.setMaxResults(1)).thenReturn(biConnectionCriteria);
    when(biConnectionCriteria.uniqueResult()).thenReturn(biConnection);
  }

  /**
   * Configures properties provider mock for the test environment.
   * Sets up the behavior of the properties provider to return expected
   * configuration values during tests.
   */
  private void setupProperties() {
    propertiesProviderStatic.when(OBPropertiesProvider::getInstance).thenReturn(propertiesProvider);
  }

  /**
   * Tests path delimiter resolution for paths without trailing slash.
   *
   * @throws Exception if the test fails
   */
  @Test
  public void testResolvePathDelimiterWithoutSlash() throws Exception {
    String result = (String) resolvePathDelimiterMethod.invoke(null, "path/to/something");
    assertEquals(RESOURCE_PATH, result);
  }

  /**
   * Tests database URL resolution when readonly URL exists.
   * Verifies that readonly URL takes precedence over normal URL.
   *
   * @throws Exception if the test fails
   */
  @Test
  public void testResolvePathDelimiterWithSlash() throws Exception {
    String result = (String) resolvePathDelimiterMethod.invoke(null, RESOURCE_PATH);
    assertEquals(RESOURCE_PATH, result);
  }

  /**
   * Tests port resolution when an empty port value is provided.
   * Verifies that the default port value (22) is returned when an empty string is passed.
   * This test ensures proper handling of missing port configurations.
   *
   * @throws Exception if the method invocation fails
   */
  @Test
  public void testResolveEmptyPortEmpty() throws Exception {
    String result = (String) resolveEmptyPortMethod.invoke(null, "");
    assertEquals("22", result);
  }

  /**
   * Tests port resolution when a specific port value is provided.
   * Verifies that the provided port value is returned without modification.
   * This test ensures that valid port configurations are preserved.
   *
   * @throws Exception if the method invocation fails
   */
  @Test
  public void testResolveEmptyPortWithValue() throws Exception {
    String result = (String) resolveEmptyPortMethod.invoke(null, "8080");
    assertEquals("8080", result);
  }

  /**
   * Tests error handling when a Python script file is not found at the specified location.
   * This test verifies that:
   * - Appropriate error handling occurs when the script file doesn't exist
   * - The correct error message is generated
   * - The OBException is thrown with the expected message
   *
   * @throws Exception if the test setup fails
   */
  @Test
  public void testScriptNotFound() throws Exception {
    when(servletContext.getRealPath(anyString())).thenReturn("/nonexistent/path");

    MockedConstruction<File> mockedFile = mockConstruction(File.class,
        (mock, context) -> when(mock.exists()).thenReturn(false));

    obMessageUtilsStatic.when(() -> OBMessageUtils.messageBD("ETPBIC_ScriptNotFound"))
        .thenReturn("Script not found error message");

    Exception exception = assertThrows(OBException.class, () ->
        callPythonScript.callPythonScript("/test/repo", "test_script.py", "arg1,arg2"));

    assertEquals("Script not found error message", exception.getMessage());
    mockedFile.close();
  }

  /**
   * Tests execution behavior when no webhook is configured.
   * Verifies that the system properly handles missing webhook configurations by:
   * - Detecting the absence of a required webhook
   * - Throwing the appropriate exception with the correct error message
   *
   * @throws Exception if the test execution fails in an unexpected way
   */
  @Test(expected = OBException.class)
  public void testDoExecuteWithNoWebhook() throws Exception {
    obMessageUtilsStatic.when(() -> OBMessageUtils.messageBD("ETPBIC_NullWebhookError"))
        .thenReturn("Webhook not found error");

    callPythonScript.doExecute(processBundle);
  }

  /**
   * Tests execution behavior when no data destinations are configured.
   * Verifies that the system properly handles missing data destination configurations by:
   * - Detecting the absence of required data destinations
   * - Throwing the appropriate exception with the correct error message
   *
   * @throws Exception if the test execution fails in an unexpected way
   */
  @Test(expected = OBException.class)
  public void testDoExecuteWithNoDataDestinations() throws Exception {
    obMessageUtilsStatic.when(() -> OBMessageUtils.messageBD("ETPBIC_NoDataDestinations"))
        .thenReturn("No data destinations found");

    callPythonScript.doExecute(processBundle);
  }

  /**
   * Tests database URL resolution when readonly URL exists.
   * Verifies that readonly URL takes precedence over normal URL.
   *
   * @throws Exception if the test fails
   */
  @Test
  public void testGetBbddUrlReadonlyExists() throws Exception {
    Properties props = new Properties();
    props.setProperty("bbdd.readonly.url", "readonly-url");
    props.setProperty("bbdd.url", NORMAL_URL);

    String result = (String) getBbddUrlMethod.invoke(null, props);
    assertEquals("readonly-url", result);
  }

  /**
   * Tests database URL resolution when readonly URL is not present.
   * Verifies that the normal URL is returned when no readonly URL is configured.
   *
   * @throws Exception if the test fails
   */
  @Test
  public void testGetBbddUrlNoReadonly() throws Exception {
    Properties props = new Properties();
    props.setProperty("bbdd.url", NORMAL_URL);

    String result = (String) getBbddUrlMethod.invoke(null, props);
    assertEquals(NORMAL_URL, result);
  }

  /**
   * Tests database password resolution when readonly password exists.
   * Verifies that readonly password takes precedence over normal password.
   *
   * @throws Exception if the test fails
   */
  @Test
  public void testGetBbddPasswordReadonlyExists() throws Exception {
    Properties props = new Properties();
    props.setProperty("bbdd.readonly.password", "readonly-pass");
    props.setProperty("bbdd.password", "normal-pass");

    String result = (String) getBbddPasswordMethod.invoke(null, props);
    assertEquals("readonly-pass", result);
  }

  /**
   * Tests database user resolution when readonly user exists.
   * Verifies that readonly user takes precedence over normal user.
   *
   * @throws Exception if the test fails
   */
  @Test
  public void testGetBbddUserReadonlyExists() throws Exception {
    Properties props = new Properties();
    props.setProperty("bbdd.readonly.user", "readonly-user");
    props.setProperty("bbdd.user", "normal-user");

    String result = (String) getBbddUserMethod.invoke(null, props);
    assertEquals("readonly-user", result);
  }

  /**
   * Tests database SID resolution when readonly SID exists.
   * Verifies that readonly SID takes precedence over normal SID.
   *
   * @throws Exception if the test fails
   */
  @Test
  public void testGetBbddSidReadonlyExists() throws Exception {
    Properties props = new Properties();
    props.setProperty("bbdd.readonly.sid", "readonly-sid");
    props.setProperty("bbdd.sid", "normal-sid");

    String result = (String) getBbddSidMethod.invoke(null, props);
    assertEquals("readonly-sid", result);
  }

  /**
   * Tests web content path resolution functionality.
   * Verifies that the correct real path is returned for a given web content path.
   */
  @Test
  public void testGetWebContentPath() {
    String testPath = DEFAULT_TEST_PATH;
    when(servletContext.getRealPath(testPath)).thenReturn("/real/test/path");

    String result = callPythonScript.getWebContentPath(testPath);
    assertEquals("/real/test/path", result);
  }

  /**
   * Tests the null check functionality when an error condition is present.
   * Verifies that the appropriate exception is thrown with the correct error message.
   *
   * @throws Exception if the test execution fails unexpectedly
   */
  @Test
  public void testCheckNullThrowsException() throws Exception {
    obMessageUtilsStatic.when(() -> OBMessageUtils.messageBD("ERROR_MSG"))
        .thenReturn("Error message");

    try {
      checkNullMethod.invoke(null, true, "ERROR_MSG");
      fail("Expected OBException to be thrown");
    } catch (InvocationTargetException e) {
      assertTrue(e.getCause() instanceof OBException);
      assertEquals("Error message", e.getCause().getMessage());
    }
  }

  /**
   * Tests BiConnection retrieval with valid input data.
   * Verifies that a valid BiConnection object is returned when all required
   * parameters are correctly provided.
   *
   * @throws Exception if the test execution fails unexpectedly
   */
  @Test
  public void testBiConnectionWithValidData() throws Exception {
    BiConnection mockConnection = mock(BiConnection.class);
    when(biConnectionCriteria.setMaxResults(1)).thenReturn(biConnectionCriteria);
    when(biConnectionCriteria.uniqueResult()).thenReturn(mockConnection);

    BiConnection result = (BiConnection) getBiConnectionMethod.invoke(null,
        orgProvider, organization, 1, processLogger);

    assertNotNull(result);
    assertEquals(mockConnection, result);
  }

  /**
   * Tests script execution with invalid script path.
   * Verifies that appropriate exception is thrown with correct error message.
   *
   * @throws Exception if the test execution fails unexpectedly
   */
  @Test
  public void testExecuteWithInvalidScriptPath() throws Exception {
    obMessageUtilsStatic.when(() -> OBMessageUtils.messageBD("ETPBIC_InvalidScriptPath"))
        .thenReturn("Invalid script path");

    assertThrows(OBException.class, () -> callPythonScript.doExecute(processBundle));
  }

  /**
   * Cleans up resources and closes static mocks after each test.
   */
  @After
  public void tearDown() {
    if (obContextStatic != null) {
      obContextStatic.close();
    }
    if (obDalStatic != null) {
      obDalStatic.close();
    }
    if (propertiesProviderStatic != null) {
      propertiesProviderStatic.close();
    }
    if (dalContextListenerStatic != null) {
      dalContextListenerStatic.close();
    }
    if (obMessageUtilsStatic != null) {
      obMessageUtilsStatic.close();
    }
  }
}
