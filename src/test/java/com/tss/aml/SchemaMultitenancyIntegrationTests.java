package com.tss.aml;

import com.tss.aml.entities.tenant.TransactionBatch;
import com.tss.aml.enums.BatchStatus;
import com.tss.aml.repositories.TransactionBatchRepository;
import com.tss.aml.security.CustomUserDetails;
import com.tss.aml.security.CustomUserDetailsService;
import com.tss.aml.security.JwtAuthenticationFilter;
import com.tss.aml.security.JwtTokenProvider;
import com.tss.aml.tenant.TenantContext;
import com.tss.aml.tenant.TenantService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class SchemaMultitenancyIntegrationTests {

    private static final String HDFC_SCHEMA = "tenant_hdfc";
    private static final String ICICI_SCHEMA = "tenant_icici";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionBatchRepository transactionBatchRepository;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUpSchemas() {
        recreateTenantSchema(HDFC_SCHEMA);
        recreateTenantSchema(ICICI_SCHEMA);
        TenantContext.clear();
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Prove JPA repository reads & writes route to target tenant schema and remain isolated")
    void repositoryQueriesRouteToCurrentTenantSchemaAndRemainIsolated() {
        // 1. Create HDFC-specific record via JPA in tenant_hdfc schema
        withTenant(HDFC_SCHEMA, () -> {
            TransactionBatch hdfcBatch = TransactionBatch.builder()
                    .batchCode("HDFC-BATCH-001")
                    .fileReference("hdfc_statement.xlsx")
                    .status(BatchStatus.QUEUED)
                    .totalRecords(10)
                    .alertsGeneratedCount(0)
                    .uploadedAt(LocalDateTime.now())
                    .build();
            transactionBatchRepository.save(hdfcBatch);
            return null;
        });

        // 2. Create ICICI-specific record via JPA in tenant_icici schema
        withTenant(ICICI_SCHEMA, () -> {
            TransactionBatch iciciBatch = TransactionBatch.builder()
                    .batchCode("ICICI-BATCH-001")
                    .fileReference("icici_statement.xlsx")
                    .status(BatchStatus.QUEUED)
                    .totalRecords(25)
                    .alertsGeneratedCount(2)
                    .uploadedAt(LocalDateTime.now())
                    .build();
            transactionBatchRepository.save(iciciBatch);
            return null;
        });

        // 3. Verify: With TenantContext = tenant_hdfc
        withTenant(HDFC_SCHEMA, () -> {
            // HDFC record IS visible
            TransactionBatch hdfcRead = transactionBatchRepository.findByBatchCode("HDFC-BATCH-001").orElseThrow();
            assertThat(hdfcRead.getFileReference()).isEqualTo("hdfc_statement.xlsx");
            assertThat(hdfcRead.getTotalRecords()).isEqualTo(10);

            // ICICI record is NOT visible
            assertThat(transactionBatchRepository.findByBatchCode("ICICI-BATCH-001")).isEmpty();

            return null;
        });

        // 4. Verify: With TenantContext = tenant_icici
        withTenant(ICICI_SCHEMA, () -> {
            // ICICI record IS visible
            TransactionBatch iciciRead = transactionBatchRepository.findByBatchCode("ICICI-BATCH-001").orElseThrow();
            assertThat(iciciRead.getFileReference()).isEqualTo("icici_statement.xlsx");
            assertThat(iciciRead.getTotalRecords()).isEqualTo(25);

            // HDFC record is NOT visible
            assertThat(transactionBatchRepository.findByBatchCode("HDFC-BATCH-001")).isEmpty();

            return null;
        });

        // 5. Verify context is cleared afterwards
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("Prove findAll and count queries return only records from current active tenant schema")
    void repositoryListAndCountQueriesAreIsolatedPerTenantSchema() {
        // Seed HDFC schema
        withTenant(HDFC_SCHEMA, () -> {
            transactionBatchRepository.save(TransactionBatch.builder()
                    .batchCode("HDFC-REC")
                    .fileReference("hdfc.xlsx")
                    .status(BatchStatus.QUEUED)
                    .totalRecords(5)
                    .alertsGeneratedCount(0)
                    .uploadedAt(LocalDateTime.now())
                    .build());
            return null;
        });

        // Seed ICICI schema
        withTenant(ICICI_SCHEMA, () -> {
            transactionBatchRepository.save(TransactionBatch.builder()
                    .batchCode("ICICI-REC")
                    .fileReference("icici.xlsx")
                    .status(BatchStatus.QUEUED)
                    .totalRecords(15)
                    .alertsGeneratedCount(1)
                    .uploadedAt(LocalDateTime.now())
                    .build());
            return null;
        });

        // Verify HDFC list and count
        withTenant(HDFC_SCHEMA, () -> {
            List<TransactionBatch> batches = transactionBatchRepository.findAll();
            assertThat(batches).hasSize(1);
            assertThat(batches.get(0).getBatchCode()).isEqualTo("HDFC-REC");
            assertThat(transactionBatchRepository.count()).isEqualTo(1);
            return null;
        });

        // Verify ICICI list and count
        withTenant(ICICI_SCHEMA, () -> {
            List<TransactionBatch> batches = transactionBatchRepository.findAll();
            assertThat(batches).hasSize(1);
            assertThat(batches.get(0).getBatchCode()).isEqualTo("ICICI-REC");
            assertThat(transactionBatchRepository.count()).isEqualTo(1);
            return null;
        });
    }

    @Test
    @DisplayName("Prove JwtAuthenticationFilter clears TenantContext in finally block after request execution")
    void jwtFilterClearsTenantContextAfterRequestExecution() throws Exception {
        String token = "test-token";
        UUID tenantId = UUID.randomUUID();
        String schemaName = HDFC_SCHEMA;

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getTenantId(token)).thenReturn(tenantId);
        when(jwtTokenProvider.getUsername(token)).thenReturn("bank.admin@hdfc.com");
        when(tenantService.getSchemaName(tenantId)).thenReturn(schemaName);

        CustomUserDetails userDetails = CustomUserDetails.builder()
                .userId(UUID.randomUUID())
                .username("bank.admin@hdfc.com")
                .password("{noop}password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .tenantId(tenantId)
                .tenantCode("HDFC")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        when(customUserDetailsService.loadUserByUsername("bank.admin@hdfc.com"))
                .thenReturn(userDetails);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> tenantSeenInChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                tenantSeenInChain.set(TenantContext.getCurrentTenant());

        jwtAuthenticationFilter.doFilter(request, response, chain);

        assertThat(tenantSeenInChain.get()).isEqualTo(schemaName);
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("Prove TenantContext.clear() successfully removes ThreadLocal tenant state")
    void tenantContextClearResetsContext() {
        TenantContext.setCurrentTenant("tenant_hdfc");
        assertThat(TenantContext.getCurrentTenant()).isEqualTo("tenant_hdfc");

        TenantContext.clear();
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    private <T> T withTenant(String schemaName, java.util.function.Supplier<T> action) {
        TenantContext.setCurrentTenant(schemaName);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    private void recreateTenantSchema(String schemaName) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA " + schemaName);

        jdbcTemplate.execute("CREATE TABLE " + schemaName + ".transaction_batch ("
                + "batch_id UUID PRIMARY KEY, "
                + "batch_code VARCHAR(20) NOT NULL, "
                + "uploaded_by UUID, "
                + "file_reference VARCHAR(255) NOT NULL, "
                + "status VARCHAR(50) NOT NULL, "
                + "total_records INTEGER, "
                + "alerts_generated_count INTEGER, "
                + "uploaded_at TIMESTAMP NOT NULL, "
                + "processed_at TIMESTAMP"
                + ")");

        jdbcTemplate.execute("CREATE TABLE " + schemaName + ".financial_transaction ("
                + "transaction_id UUID PRIMARY KEY, "
                + "batch_id UUID NOT NULL REFERENCES " + schemaName + ".transaction_batch(batch_id), "
                + "txn_no VARCHAR(255) NOT NULL, "
                + "originator_account_id UUID, "
                + "amount NUMERIC(20, 4) NOT NULL, "
                + "currency VARCHAR(3) NOT NULL, "
                + "txn_type VARCHAR(50) NOT NULL, "
                + "direction VARCHAR(50) NOT NULL, "
                + "counterparty_name VARCHAR(200), "
                + "counterparty_account_no VARCHAR(30), "
                + "counterparty_bank VARCHAR(200), "
                + "counterparty_country_code VARCHAR(2), "
                + "txn_timestamp TIMESTAMP NOT NULL, "
                + "country_code VARCHAR(2) NOT NULL, "
                + "created_at TIMESTAMP, "
                + "updated_at TIMESTAMP, "
                + "is_deleted BOOLEAN DEFAULT FALSE"
                + ")");

        jdbcTemplate.execute("CREATE TABLE " + schemaName + ".batch_validation_error ("
                + "error_id BIGSERIAL PRIMARY KEY, "
                + "batch_id UUID NOT NULL REFERENCES " + schemaName + ".transaction_batch(batch_id), "
                + "row_number INTEGER NOT NULL, "
                + "field_name VARCHAR(255) NOT NULL, "
                + "error_message TEXT NOT NULL, "
                + "created_at TIMESTAMP NOT NULL"
                + ")");
    }
}