package com.etendoerp.integration.powerbi.eventhandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;

import com.etendoerp.integration.powerbi.data.BiQueryCustom;

/**
 * Test class for {@link CustomQueryHandler} to validate event handling
 * and query validation for Power BI custom queries.
 *
 * <p>This test suite uses Mockito for mocking dependencies and static method calls,
 * ensuring robust testing of the custom query handler's behavior during
 * entity creation and update events.</p>
 *
 * <p>Key test scenarios include:
 * <ul>
 *   <li>Validating query on new entity save</li>
 *   <li>Validating query on entity update</li>
 *   <li>Verifying observed entities</li>
 * </ul>
 * </p>
 */
@RunWith(MockitoJUnitRunner.class)
public class CustomQueryHandlerTest {

    private CustomQueryHandler customQueryHandler;
    private MockedStatic<ModelProvider> mockedModelProvider;
    private MockedStatic<QueryValidationUtil> mockedQueryValidationUtil;

    @Mock
    private ModelProvider modelProvider;

    @Mock
    private Entity mockEntity;

    @Mock
    private Property customQueryProperty;

    @Mock
    private EntityNewEvent newEvent;

    @Mock
    private EntityUpdateEvent updateEvent;

    /**
     * Sets up the test environment before each test method.
     *
     * <p>Configures mocks for:
     * <ul>
     *   <li>ModelProvider</li>
     *   <li>Entity</li>
     *   <li>Property</li>
     *   <li>Query validation utility</li>
     * </ul>
     * </p>
     */
    @Before
    public void setUp() {
        mockedModelProvider = mockStatic(ModelProvider.class);
        mockedQueryValidationUtil = mockStatic(QueryValidationUtil.class);

        mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
        when(modelProvider.getEntity(BiQueryCustom.ENTITY_NAME)).thenReturn(mockEntity);

        when(mockEntity.getProperty(BiQueryCustom.PROPERTY_QUERY)).thenReturn(customQueryProperty);

        customQueryHandler = new TestableCustomQueryHandler();
        customQueryHandler.customQueryProp = customQueryProperty;


        mockedQueryValidationUtil.when(() ->
                QueryValidationUtil.queryValidation(any(), any(), any()))
            .thenAnswer(invocation -> null);
    }

    /**
     * Cleans up mocked static resources after each test.
     */
    @After
    public void tearDown() {
        if (mockedModelProvider != null) {
            mockedModelProvider.close();
        }
        if (mockedQueryValidationUtil != null) {
            mockedQueryValidationUtil.close();
        }
    }

    /**
     * Tests successful query validation during entity creation.
     *
     * <p>Verifies that {@link QueryValidationUtil#queryValidation}
     * is called when a new entity is saved.</p>
     */
    @Test
    public void testOnSaveValidQuerySuccess() {

        customQueryHandler.onSave(newEvent);

        mockedQueryValidationUtil.verify(() ->
            QueryValidationUtil.queryValidation(any(), any(), any()));
    }

    /**
     * Tests successful query validation during entity update.
     *
     * <p>Verifies that {@link QueryValidationUtil#queryValidation}
     * is called when an existing entity is updated.</p>
     */
    @Test
    public void testOnUpdateValidQuerySuccess() {
        customQueryHandler.onUpdate(updateEvent);

        mockedQueryValidationUtil.verify(() ->
            QueryValidationUtil.queryValidation(any(), any(), any()));
    }

    /**
     * Tests retrieval of observed entities.
     *
     * <p>Confirms that:
     * <ul>
     *   <li>Returned entities array is not null</li>
     *   <li>Only one entity is returned</li>
     *   <li>Returned entity matches the mocked entity</li>
     * </ul>
     * </p>
     */
    @Test
    public void testGetObservedEntities() {
        Entity[] entities = customQueryHandler.getObservedEntities();

        assertNotNull(entities);
        assertEquals(1, entities.length);
        assertSame(mockEntity, entities[0]);
    }

    /**
     * Test-specific implementation of {@link CustomQueryHandler}
     * that overrides methods to facilitate unit testing.
     *
     * <p>Provides controlled test implementations for:
     * <ul>
     *   <li>Event validation</li>
     *   <li>Observed entities retrieval</li>
     * </ul>
     * </p>
     */
    private class TestableCustomQueryHandler extends CustomQueryHandler {
        @Override
        protected boolean isValidEvent(org.openbravo.client.kernel.event.EntityPersistenceEvent event) {
            return true;
        }

        @Override
        protected Entity[] getObservedEntities() {
            return new Entity[]{mockEntity};
        }
    }
}