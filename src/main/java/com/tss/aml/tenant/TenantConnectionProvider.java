package com.tss.aml.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantConnectionProvider
        implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection)
            throws SQLException {

        resetSchema(connection);
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier)
            throws SQLException {

        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            throw new SQLException("Tenant identifier cannot be null or blank");
        }

        Connection connection = dataSource.getConnection();

        try {
            connection.setSchema(tenantIdentifier);

            log.debug(
                    "Database connection configured for tenant schema: {}",
                    tenantIdentifier
            );

            return connection;

        } catch (SQLException ex) {
            connection.close();
            throw ex;
        }
    }

    @Override
    public void releaseConnection(
            String tenantIdentifier,
            Connection connection
    ) throws SQLException {

        try {
            resetSchema(connection);
        } finally {
            connection.close();
        }
    }

    private void resetSchema(Connection connection) throws SQLException {
        connection.setSchema("public");
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new IllegalArgumentException(
                "Unknown unwrap type: " + unwrapType
        );
    }
}
