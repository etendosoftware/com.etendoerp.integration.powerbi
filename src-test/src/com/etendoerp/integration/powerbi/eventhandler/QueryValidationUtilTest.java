package com.etendoerp.integration.powerbi.eventhandler;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

/**
 * Unit tests for the QueryValidationUtil class.
 * This class validates SQL queries within the context of an EntityPersistenceEvent.
 * It ensures that only valid SELECT statements are allowed and throws an OBException
 * for invalid queries.
 */
@RunWith(MockitoJUnitRunner.class)
public class QueryValidationUtilTest {

    private static final String CUSTOM_QUERY_CREATED = "custom query created";

    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

    @Mock
    private EntityPersistenceEvent event;

    @Mock
    private Property property;

    @Mock
    private Logger logger;

    /**
     * Sets up the test environment by mocking OBMessageUtils and configuring default behavior.
     */
    @Before
    public void setUp() {
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETPBIC_InvalidQuerySyntax"))
            .thenReturn("Invalid query syntax: Query must start with SELECT");
    }

    /**
     * Cleans up the test environment by closing mocked static instances.
     */
    @After
    public void tearDown() {
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
    }

    /**
     * Tests validation of a simple valid SELECT query.
     * Verifies that the logger records the appropriate message.
     */
    @Test
    public void testQueryValidationValidSelectQuerySuccess() {
        String validQuery = "SELECT * FROM C_Order";
        when(event.getCurrentState(property)).thenReturn(validQuery);
        doNothing().when(logger).info(anyString());

        QueryValidationUtil.queryValidation(event, property, logger);

        verify(logger).info(CUSTOM_QUERY_CREATED);
    }

    /**
     * Tests validation of a valid SELECT query containing whitespace.
     * Verifies that the logger records the appropriate message.
     */
    @Test
    public void testQueryValidationValidSelectQueryWithWhitespaceSuccess() {
        String validQuery = "  SELECT id, name FROM AD_Table  ";
        when(event.getCurrentState(property)).thenReturn(validQuery);
        doNothing().when(logger).info(anyString());

        QueryValidationUtil.queryValidation(event, property, logger);

        verify(logger).info(CUSTOM_QUERY_CREATED);
    }

    /**
     * Tests validation of an invalid INSERT query.
     * Verifies that an OBException is thrown.
     */
    @Test(expected = OBException.class)
    public void testQueryValidationInvalidQueryThrowsException() {
        String invalidQuery = "INSERT INTO table_name VALUES (1, 2, 3)";
        when(event.getCurrentState(property)).thenReturn(invalidQuery);

        QueryValidationUtil.queryValidation(event, property, logger);

    }

    /**
     * Tests validation of an invalid UPDATE query.
     * Verifies that an OBException is thrown.
     */
    @Test(expected = OBException.class)
    public void testQueryValidationUpdateQueryThrowsException() {
        String invalidQuery = "UPDATE table_name SET column1 = value1";
        when(event.getCurrentState(property)).thenReturn(invalidQuery);

        QueryValidationUtil.queryValidation(event, property, logger);

    }

    /**
     * Tests validation of an invalid DELETE query.
     * Verifies that an OBException is thrown.
     */
    @Test(expected = OBException.class)
    public void testQueryValidationDeleteQueryThrowsException() {
        String invalidQuery = "DELETE FROM table_name";
        when(event.getCurrentState(property)).thenReturn(invalidQuery);

        QueryValidationUtil.queryValidation(event, property, logger);

    }

    /**
     * Tests validation of a complex SELECT query with various SQL clauses.
     * Verifies that the logger records the appropriate message.
     */
    @Test
    public void testQueryValidationComplexSelectQuerySuccess() {
        String complexQuery = "SELECT a.column1, b.column2 " +
            "FROM table1 a " +
            "JOIN table2 b ON a.id = b.id " +
            "WHERE a.status = 'Active' " +
            "GROUP BY a.column1 " +
            "HAVING COUNT(*) > 1 " +
            "ORDER BY b.column2 DESC";
        when(event.getCurrentState(property)).thenReturn(complexQuery);
        doNothing().when(logger).info(anyString());

        QueryValidationUtil.queryValidation(event, property, logger);

        verify(logger).info(CUSTOM_QUERY_CREATED);
    }
}