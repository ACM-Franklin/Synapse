package edu.franklin.acm.synapse.activity.migrations;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToCreateStatementException;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Startup
@Singleton
public class MigrationManager implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(MigrationManager.class);

    private final Jdbi jdbi;
    private final MigrationDao migrations;
    private final String schema;
    private final List<String> migrationFiles;

    public MigrationManager(
            Jdbi jdbi,
            MigrationDao migrationDao,
            @ConfigProperty(name = "synapse.datasource.auto-migrate", defaultValue = "false") boolean doMigrations,
            @ConfigProperty(name = "synapse.datasource.schema") String schema,
            @ConfigProperty(name = "synapse.datasource.migrations") String migrations) {
        this.jdbi = jdbi;
        this.schema = schema;
        this.migrations = migrationDao;
        this.migrationFiles = getMigrationFiles(migrations.endsWith("/") ? migrations : migrations + "/");

        if (doMigrations) {
            run();
        }
    }

    public void run() {
        runResource(schema, true);

        final var previousMigrations = migrations.getAll();

        for (final var file : migrationFiles) {
            final var successfulRun = previousMigrations
                    .stream()
                    .filter(m -> m.name().equals(file) && m.succeeded())
                    .findFirst();

            if (successfulRun.isEmpty())  {
                runResource(file, false);
            }
        }
    }

    private List<String> getMigrationFiles(final String folder) {
        try (final var is = resource(folder).openStream();
             final var r  = new InputStreamReader(is);
             final var br = new BufferedReader(r)) {
            return br
                    .lines()
                    .map(s -> folder + s)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void runResource(String resourcePath, boolean forced) {
        boolean success = false;

        try (final var is  = resource(resourcePath).openStream();
             final var r   = new InputStreamReader(is);
             final var h   = jdbi.open()) {
            h.createScript(r.readAllAsString()).execute();
            success = true;
        } catch (IOException e) {
            log.warn("Invalid resource path passed as migration: {} ({})", resourcePath, e.getMessage());
            throw new IllegalStateException(e);
        } catch (UnableToCreateStatementException e) {
            log.error("Underlying issue in DataSource: {}", e.getMessage());
        } catch (UnableToExecuteStatementException e) {
            log.error("Migration file \"{}\" contains invalid SQL: {}", resourcePath, e.getMessage());
        } finally {
            if (!forced) migrations.commit(resourcePath, success);
        }
    }

    private URL resource(String path) {
        final var resource = MigrationManager.class.getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException(
                    "Resource path provided does not resolve to a real resource: " + path);
        }

        return resource;
    }
}
