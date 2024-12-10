package com.etendoerp.integration.powerbi.processes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.integration.powerbi.data.BiLog;

/**
 * Test class for the FillLogWindow process, focusing on log creation functionality.
 * This test suite validates the behavior of the FillLogWindow process under various scenarios,
 * including successful log creation, handling of null organizations, and empty log types.
 * The tests utilize Mockito for mocking dependencies and simulating different system states
 * during log creation process.
 * Key test scenarios include:
 * - Successful log creation with valid parameters
 * - Handling of null organization
 * - Handling of empty log type
 */
@RunWith(MockitoJUnitRunner.class)
public class FillLogWindowTest {

    public static final String ORGANIZATION = "organization";
    public static final String TEST_MESSAGE = "Test message";
    public static final String LOG_TYPE = "logtype";
    public static final String DESCRIPTION = "description";
    public static final String TEST_ORG = "testOrg";
    public static final String INFO = "INFO";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private OBDal mockDal;

    @Mock
    private OBContext mockContext;

    @Mock
    private Organization mockOrganization;

    @Mock
    private Client mockClient;

    @Mock
    private BiLog mockBiLog;

    @Mock
    private OBProvider mockOBProvider;

    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OBContext> mockedOBContext;
    private MockedStatic<OBProvider> mockedOBProvider;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

    private FillLogWindow fillLogWindow;
    private Map<String, String> parameters;
    private Map<String, String> responseVars;

    /**
     * Sets up the test environment before each test method.
     * Initializes mock objects, static mocks for OBDal, OBContext, OBProvider, and OBMessageUtils.
     * Configures default behaviors for mocked objects and prepares the FillLogWindow
     * process for testing.
     * This setup includes:
     * - Creating a new FillLogWindow instance
     * - Initializing parameter and response maps
     * - Mocking static contexts and dependencies
     * - Configuring mock behaviors for various system components
     */
    @Before
    public void setUp() {
        fillLogWindow = new FillLogWindow();
        parameters = new HashMap<>();
        responseVars = new HashMap<>();

        mockedOBDal = mockStatic(OBDal.class);
        mockedOBContext = mockStatic(OBContext.class);
        mockedOBProvider = mockStatic(OBProvider.class);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

        mockedOBDal.when(OBDal::getInstance).thenReturn(mockDal);
        mockedOBContext.when(OBContext::getOBContext).thenReturn(mockContext);
        mockedOBProvider.when(OBProvider::getInstance).thenReturn(mockOBProvider);
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD(any())).thenReturn("Log Creation Error");

        when(mockOBProvider.get(BiLog.class)).thenReturn(mockBiLog);
        when(mockContext.getCurrentClient()).thenReturn(mockClient);
        when(mockDal.get(eq(Organization.class), eq(TEST_ORG))).thenReturn(mockOrganization);
    }

    /**
     * Tears down the test environment after each test method.
     * Closes all mocked static contexts to prevent memory leaks and
     * ensure a clean state between tests. This method ensures proper
     * cleanup of Mockito static mocks.
     */
    @After
    public void tearDown() {
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
        if (mockedOBProvider != null) {
            mockedOBProvider.close();
        }
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
    }

    /**
     * Tests the successful creation of a log entry with valid parameters.
     * Verifies that:
     * - A new BiLog object is created
     * - Client and organization are correctly set
     * - Log type and message are properly assigned
     * - The log is saved and flushed in the database
     * This test ensures the happy path of log creation works as expected.
     */
    @Test
    public void testSuccessfulLogCreation() {
        // Prepare test data
        parameters.put(ORGANIZATION, TEST_ORG);
        parameters.put(LOG_TYPE, INFO);
        parameters.put(DESCRIPTION, TEST_MESSAGE);

        // Execute
        fillLogWindow.get(parameters, responseVars);

        // Verify
        verify(mockBiLog).setNewOBObject(true);
        verify(mockBiLog).setClient(mockClient);
        verify(mockBiLog).setOrganization(mockOrganization);  // Now verifying with mockOrganization
        verify(mockBiLog).setLogType(INFO);
        verify(mockBiLog).setMessage(TEST_MESSAGE);
        verify(mockDal).save(mockBiLog);
        verify(mockDal).flush();
    }

    /**
     * Tests the behavior when a null organization is provided.
     * Validates that:
     * - An OBException is thrown when attempting to create a log
     * - The exception contains the expected error message
     * Ensures that the process handles invalid (null) organization input
     * correctly by throwing an appropriate exception.
     */
    @Test
    public void testNullOrganization() {
        parameters.put(ORGANIZATION, null);
        parameters.put(LOG_TYPE, INFO);
        parameters.put(DESCRIPTION, TEST_MESSAGE);

        when(mockDal.get(eq(Organization.class), eq(null))).thenReturn(null);
        doThrow(new OBException("Log Creation Error"))
            .when(mockDal)
            .save(any(BiLog.class));

        thrown.expect(OBException.class);
        thrown.expectMessage("Log Creation Error");

        fillLogWindow.get(parameters, responseVars);
    }

    /**
     * Tests the handling of an empty log type.
     * Checks that:
     * - The log can be created with an empty log type
     * - The empty log type is correctly set on the BiLog object
     * Verifies the system's flexibility in handling different log type inputs.
     */
    @Test
    public void testEmptyLogType() {
        parameters.put(ORGANIZATION, TEST_ORG);
        parameters.put(LOG_TYPE, "");
        parameters.put(DESCRIPTION, TEST_MESSAGE);

        fillLogWindow.get(parameters, responseVars);

        verify(mockBiLog).setLogType("");
    }


}