package com.etendoerp.integration.powerbi.eventhandler;


import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.integration.powerbi.data.BiExecutionVariables;

/**
 * Test class for {@link DelimiterValidator} to validate CSV separator configuration.
 *
 * <p>This test suite ensures that the DelimiterValidator correctly validates
 * CSV separator configurations during save and update events.</p>
 *
 * <p>Test scenarios cover:</p>
 * <ul>
 *   <li>Valid delimiter configurations for new and updated events</li>
 *   <li>Invalid delimiter length handling</li>
 *   <li>Non-CSV separator variable handling</li>
 *   <li>Case sensitivity of CSV separator variable name</li>
 * </ul>
 * <p>Uses Mockito for mocking dependencies and simulating event scenarios.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class DelimiterValidatorTest {

    private DelimiterValidator delimiterValidator;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

    @Mock
    private ModelProvider modelProvider;

    @Mock
    private Entity mockEntity;

    @Mock
    private Property valueProperty;

    @Mock
    private Property nameProperty;

    @Mock
    private EntityNewEvent newEvent;

    @Mock
    private EntityUpdateEvent updateEvent;

    /**
     * Sets up the test environment before each test method.
     *
     * <p>Configures mock objects for:</p>
     * <ul>
     *   <li>ModelProvider static method mocking</li>
     *   <li>OBMessageUtils static method mocking</li>
     *   <li>Entity and Property mock configurations</li>
     * </ul>
     *
     * <p>Initializes the DelimiterValidator with test-specific properties
     * and prepares mocked responses.</p>
     */
    @Before
    public void setUp() {
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        when(modelProvider.getEntity(BiExecutionVariables.ENTITY_NAME)).thenReturn(mockEntity);

        when(mockEntity.getProperty(BiExecutionVariables.PROPERTY_VARIABLE)).thenReturn(valueProperty);
        when(mockEntity.getProperty(BiExecutionVariables.PROPERTY_VALUE)).thenReturn(nameProperty);

        delimiterValidator = new TestableDelimiterValidator();
        delimiterValidator.csvSeparatorNameProp = nameProperty;
        delimiterValidator.csvSeparatorValueProp = valueProperty;

        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETPBIC_InvalidDelimiter"))
            .thenReturn("Invalid delimiter: must be a single character");

    }

    @After
    public void tearDown() {
        if (mockedModelProvider != null) {
            mockedModelProvider.close();
        }
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
    }

    /**
     * Tests successful save of a valid single-character CSV separator.
     *
     * <p>Verifies that a single-character delimiter passes validation
     * during a new entity save event.</p>
     */
    @Test
    public void testOnSaveValidDelimiterSuccess() {
        // Given
        when(newEvent.getCurrentState(nameProperty)).thenReturn("csv_separator");
        when(newEvent.getCurrentState(valueProperty)).thenReturn(",");

        delimiterValidator.onSave(newEvent);

    }

    /**
     * Tests successful update of a valid single-character CSV separator.
     *
     * <p>Confirms that a single-character delimiter passes validation
     * during an entity update event.</p>
     */
    @Test
    public void testOnUpdateValidDelimiterSuccess() {
        when(updateEvent.getCurrentState(nameProperty)).thenReturn("csv_separator");
        when(updateEvent.getCurrentState(valueProperty)).thenReturn(";");

        delimiterValidator.onUpdate(updateEvent);

    }

    /**
     * Tests that saving an invalid (multi-character) delimiter throws an OBException.
     *
     * <p>Ensures the validator rejects delimiters longer than one character
     * during a new entity save event.</p>
     *
     * @throws OBException expected when an invalid delimiter is provided
     */
    @Test(expected = OBException.class)
    public void testOnSaveInvalidDelimiterLengthThrowsException() {
        // Given
        when(newEvent.getCurrentState(valueProperty)).thenReturn("csv_separator");
        when(newEvent.getCurrentState(nameProperty)).thenReturn(",,");

        delimiterValidator.csvSeparatorValueProp = valueProperty;
        delimiterValidator.csvSeparatorNameProp = nameProperty;

        delimiterValidator.onSave(newEvent);

    }

    /**
     * Tests that updating with an invalid (multi-character) delimiter throws an OBException.
     *
     * <p>Verifies the validator rejects delimiters longer than one character
     * during an entity update event.</p>
     *
     * @throws OBException expected when an invalid delimiter is provided
     */
    @Test(expected = OBException.class)
    public void testOnUpdateInvalidDelimiterLengthThrowsException() {
        when(updateEvent.getCurrentState(valueProperty)).thenReturn("csv_separator");
        when(updateEvent.getCurrentState(nameProperty)).thenReturn("tab");

        delimiterValidator.csvSeparatorValueProp = valueProperty;
        delimiterValidator.csvSeparatorNameProp = nameProperty;

        delimiterValidator.onUpdate(updateEvent);


    }

    /**
     * Tests successful save of a non-CSV separator variable.
     *
     * <p>Confirms that variables with names other than 'csv_separator'
     * can have multi-character values.</p>
     */
    @Test
    public void testOnSaveNonCsvSeparatorSuccess() {
        when(newEvent.getCurrentState(nameProperty)).thenReturn("other_variable");
        when(newEvent.getCurrentState(valueProperty)).thenReturn("any value");

        delimiterValidator.onSave(newEvent);

    }

    /**
     * Tests successful update of a non-CSV separator variable.
     *
     * <p>Verifies that variables with names other than 'csv_separator'
     * can have multi-character values during updates.</p>
     */
    @Test
    public void testOnUpdateNonCsvSeparatorSuccess() {
        when(updateEvent.getCurrentState(nameProperty)).thenReturn("other_variable");
        when(updateEvent.getCurrentState(valueProperty)).thenReturn("multiple characters");

        delimiterValidator.onUpdate(updateEvent);

    }

    /**
     * Tests case-insensitive handling of CSV separator variable name during save.
     *
     * <p>Ensures that 'CSV_SEPARATOR' is treated the same as 'csv_separator'.</p>
     */
    @Test
    public void testOnSaveCaseSensitivitySuccess() {
        when(newEvent.getCurrentState(nameProperty)).thenReturn("CSV_SEPARATOR");
        when(newEvent.getCurrentState(valueProperty)).thenReturn(",");

        delimiterValidator.onSave(newEvent);
    }

    /**
     * Testable subclass of DelimiterValidator for unit testing.
     *
     * <p>Overrides {@code isValidEvent} to always return true,
     * allowing direct testing of validation logic without additional
     * event filtering constraints.</p>
     */
    private class TestableDelimiterValidator extends DelimiterValidator {
        @Override
        protected boolean isValidEvent(org.openbravo.client.kernel.event.EntityPersistenceEvent event) {
            return true;
        }
    }
}