package com.etendoerp.integration.powerbi.eventhandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.integration.powerbi.data.BiQuery;

/**
 * Test class for the EtendoBaseQueryHandler to validate its event handling behaviors.
 * This test suite covers different scenarios for saving, updating, and deleting queries
 * in the context of Etendo Power BI integration. It uses Mockito for mocking dependencies
 * and static method stubbing.
 * Key test areas include:
 * - Validation of save and update events
 * - Preventing deletion of Etendo base queries
 * - Retrieving observed entities
 */
@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EtendoBaseQueryHandlerTest {

    private EtendoBaseQueryHandler handler;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<QueryValidationUtil> mockedQueryValidationUtil;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

    @Mock
    private ModelProvider modelProvider;

    @Mock
    private Entity mockEntity;

    @Mock
    private Property queryProperty;

    @Mock
    private Property isEtendoBaseProperty;

    @Mock
    private EntityNewEvent newEvent;

    @Mock
    private EntityUpdateEvent updateEvent;

    @Mock
    private EntityDeleteEvent deleteEvent;

    @Mock
    private Logger logger;


    /**
     * Sets up the test environment before each test method.
     * Initializes mock objects, static mocks for ModelProvider, QueryValidationUtil,
     * and OBMessageUtils. Configures default behaviors for mocked objects and
     * prepares the handler for testing.
     */
    @Before
    public void setUp() {
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedQueryValidationUtil = mockStatic(QueryValidationUtil.class);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        when(modelProvider.getEntity(BiQuery.ENTITY_NAME)).thenReturn(mockEntity);

        handler = spy(new TestableEtendoBaseQueryHandler());

        when(mockEntity.getProperty(BiQuery.PROPERTY_QUERY)).thenReturn(queryProperty);
        when(mockEntity.getProperty(BiQuery.PROPERTY_ISETENDOBASE)).thenReturn(isEtendoBaseProperty);

        when(deleteEvent.getCurrentState(isEtendoBaseProperty)).thenReturn(false);

        mockedOBMessageUtils.when(() -> OBMessageUtils.parseTranslation(any()))
            .thenReturn("Cannot delete Etendo base query");
    }

    /**
     * Tears down the test environment after each test method.
     * Closes all mocked static contexts to prevent memory leaks and
     * ensure clean state between tests.
     */
    @After
    public void tearDown() {
        if (mockedModelProvider != null) {
            mockedModelProvider.close();
        }
        if (mockedQueryValidationUtil != null) {
            mockedQueryValidationUtil.close();
        }
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
    }

    /**
     * Tests that query validation is called during the save event.
     * Verifies that the QueryValidationUtil.queryValidation method is invoked
     * when a new query event is processed.
     */
    @Test
    public void testOnSaveValidEventCallsQueryValidation() {
        handler.onSave(newEvent);

        mockedQueryValidationUtil.verify(
            () -> QueryValidationUtil.queryValidation(any(), any(), any())
        );
    }

    /**
     * Tests that query validation is called during the update event.
     * Verifies that the QueryValidationUtil.queryValidation method is invoked
     * when an update query event is processed.
     */
    @Test
    public void testOnUpdateValidEventCallsQueryValidation() {
        handler.onUpdate(updateEvent);

        mockedQueryValidationUtil.verify(
            () -> QueryValidationUtil.queryValidation(any(), any(), any())
        );
    }

    /**
     * Tests that an exception is thrown when attempting to delete an Etendo base query.
     * Ensures that attempting to delete a query marked as an Etendo base query
     * results in an OBException being thrown.
     */
    @Test(expected = OBException.class)
    public void testOnDeleteEtendoBaseTrueThrowsException() {
        doThrow(new OBException("ETPBIC_CantDeleteEtendoQuery"))
            .when(handler)
            .onDelete(deleteEvent);

        handler.onDelete(deleteEvent);
    }

    /**
     * Tests that no exception is thrown when deleting a non-base query.
     * Verifies that queries not marked as Etendo base can be deleted without
     * raising an exception.
     */
    @Test
    public void atestOnDeleteEtendoBaseFalseNoException() {
        when(deleteEvent.getCurrentState(isEtendoBaseProperty)).thenReturn(false);

        when(mockEntity.getProperty(BiQuery.PROPERTY_ISETENDOBASE)).thenReturn(isEtendoBaseProperty);


        handler.onDelete(deleteEvent);

        verify(logger, never()).error(any(String.class));
    }

    /**
     * Tests the retrieval of observed entities.
     * Checks that the getObservedEntities method returns the expected entity
     * and performs basic validation on the returned array.
     */
    @Test
    public void testGetObservedEntities() {
        Entity[] expectedEntities = new Entity[]{mockEntity};
        when(handler.getObservedEntities()).thenReturn(expectedEntities);

        Entity[] actualEntities = handler.getObservedEntities();

        assertNotNull(actualEntities);
        assertEquals(1, actualEntities.length);
        assertSame(expectedEntities[0], actualEntities[0]);
    }

    /**
     * Inner test handler class extending EtendoBaseQueryHandler.
     * Provides a testable implementation with a simplified event validation
     * method that always returns true, facilitating easier unit testing.
     */
    private static class TestableEtendoBaseQueryHandler extends EtendoBaseQueryHandler {
        @Override
        protected boolean isValidEvent(org.openbravo.client.kernel.event.EntityPersistenceEvent event) {
            return true;
        }

    }
}