package com.etendoerp.integration.powerbi.eventhandler;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.apache.logging.log4j.Logger;
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
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.integration.powerbi.data.BiQuery;

/**
 * Test class for the EtendoBaseQueryHandler component.
 * This class contains unit tests to validate the behavior of the EtendoBaseQueryHandler.
 */
@RunWith(MockitoJUnitRunner.class)
public class EtendoBaseQueryHandlerTest {

  @Mock
  private ModelProvider modelProvider;

  @Mock
  private Entity mockEntity;

  @Mock
  private Property isEtendoBaseProperty;

  @Mock
  private Property queryProperty;

  @Mock
  private EntityDeleteEvent deleteEvent;

  @Mock
  private EntityNewEvent newEvent;

  @Mock
  private Logger logger;

  private MockedStatic<ModelProvider> mockedModelProvider;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;
  private MockedStatic<QueryValidationUtil> mockedQueryValidationUtil;

  private TestableEtendoBaseQueryHandler handler;

  /**
   * Sets up the test environment by mocking static classes and initializing required objects.
   *
   * @throws Exception
   *     if any error occurs during setup.
   */
  @Before
  public void setUp() throws Exception {
    mockedModelProvider = mockStatic(ModelProvider.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);
    mockedQueryValidationUtil = mockStatic(QueryValidationUtil.class);

    mockedModelProvider.when(ModelProvider::getInstance).thenReturn(modelProvider);
    when(modelProvider.getEntity(BiQuery.ENTITY_NAME)).thenReturn(mockEntity);

    when(mockEntity.getProperty(BiQuery.PROPERTY_ISETENDOBASE)).thenReturn(isEtendoBaseProperty);
    when(mockEntity.getProperty(BiQuery.PROPERTY_QUERY)).thenReturn(queryProperty);

    handler = new TestableEtendoBaseQueryHandler(logger);

    setEntitiesFieldUsingReflection(handler, new Entity[]{ mockEntity });
  }

  /**
   * Utility method to set the "entities" field of the handler using reflection.
   *
   * @param handler
   *     the EtendoBaseQueryHandler instance.
   * @param entities
   *     the entities array to set.
   */
  private void setEntitiesFieldUsingReflection(EtendoBaseQueryHandler handler, Entity[] entities) {
    try {
      Field entitiesField = EtendoBaseQueryHandler.class.getDeclaredField("entities");
      entitiesField.setAccessible(true);
      entitiesField.set(handler, entities);
    } catch (Exception e) {
      throw new EntityReflectionException("Error setting entities field via reflection", e);
    }
  }

  /**
   * Tears down the test environment by closing mocked static objects.
   */
  @After
  public void tearDown() {
    if (mockedQueryValidationUtil != null) {
      mockedQueryValidationUtil.close();
    }
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
    if (mockedModelProvider != null) {
      mockedModelProvider.close();
    }
  }

  /**
   * Tests the onSave method of the EtendoBaseQueryHandler for valid events.
   * Verifies that the query property is validated correctly.
   */
  @Test
  public void testOnSaveValidEvent() {
    mock(BiQuery.class);

    handler.onSave(newEvent);

    verify(mockEntity, times(1)).getProperty(BiQuery.PROPERTY_QUERY);
    mockedQueryValidationUtil.verify(
        () -> QueryValidationUtil.queryValidation(eq(newEvent), eq(queryProperty), any(Logger.class)), times(1));
  }

  /**
   * Tests the onDelete method of the EtendoBaseQueryHandler for events
   * with a non-Etendo base query.
   * Verifies that the isEtendoBaseProperty state is checked.
   */
  @Test
  public void testOnDeleteWithNonEtendoBaseQuery() {
    when(deleteEvent.getCurrentState(isEtendoBaseProperty)).thenReturn(false);
    mock(BiQuery.class);

    handler.onDelete(deleteEvent);

    verify(deleteEvent).getCurrentState(isEtendoBaseProperty);
  }

  /**
   * Tests the onDelete method of the EtendoBaseQueryHandler for events
   * with an Etendo base query.
   * Expects an OBException to be thrown.
   */
  @Test(expected = OBException.class)
  public void testOnDeleteWithEtendoBaseQuery() {
    when(deleteEvent.getCurrentState(isEtendoBaseProperty)).thenReturn(true);
    mock(BiQuery.class);
    mockedOBMessageUtils.when(() -> OBMessageUtils.parseTranslation(any())).thenReturn(
        "Cannot delete Etendo base query");

    handler.onDelete(deleteEvent);
  }

  /**
   * Tests the getObservedEntities method to verify that the correct entities are returned.
   */
  @Test
  public void testGetObservedEntities() {
    Entity[] entities = handler.getObservedEntities();

    assertSame("Should return mock entity", mockEntity, entities[0]);
  }

  /**
   * Inner testable implementation of the EtendoBaseQueryHandler to override specific behavior for testing purposes.
   */
  private static class TestableEtendoBaseQueryHandler extends EtendoBaseQueryHandler {

    /**
     * Constructs a testable EtendoBaseQueryHandler instance.
     *
     * @param logger
     *     the Logger instance to use.
     */
    public TestableEtendoBaseQueryHandler(Logger logger) {
    }

    /**
     * Overrides the isValidEvent method to always return true for testing purposes.
     *
     * @param event
     *     the EntityPersistenceEvent to validate.
     * @return true, indicating the event is valid.
     */
    @Override
    protected boolean isValidEvent(org.openbravo.client.kernel.event.EntityPersistenceEvent event) {
      return true;
    }
  }

  /**
   * Exception thrown when an error occurs while setting the entities field via reflection.
   */
  public class EntityReflectionException extends RuntimeException {
    public EntityReflectionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}