package edu.franklin.acm.synapse.activity.migrations;

import io.quarkus.runtime.Startup;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToCreateStatementException;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.List;

/**
 * Singleton Bean that runs on startup. This bean manages the execution and state tracking
 * of the migration and schema files used to keep the database up to date.
 */
@Startup
@Singleton
public class MigrationManager implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(MigrationManager.class);

    private final Jdbi jdbi;
    private final MigrationDao migrations;
    private final String schema;
    private final List<String> migrationFiles;

    /**
     *
     * @param jdbi         A live JDBI connection.
     * @param migrationDao A DAO for maintaining migration execution state.
     * @param doMigrations Whether to run the migrations automatically or not.
     * @param schema       The schema file, which is the initial backbone. ALWAYS EXECUTED.
     * @param migrations   The resource folder that contains the migration files to run.
     */
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

    /**
     * Collects all migration files from the given resource folder.
     * @param folder The folder to collect files from.
     * @return A list of absolute resource paths to migration files.
     */
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

    /**
     * Executes a resource SQL file, optionally committing the execution to the migrations table.
     * @param resourcePath The path of the SQL file to execute.
     * @param forced       Whether this execution was forced. If it was, it is not logged in the table.
     */
    private void runResource(String resourcePath, boolean forced) {
        boolean success = false;

        try (final var is  = resource(resourcePath).openStream();
             final var r   = new InputStreamReader(is);
             final var h   = jdbi.open()) {
            h.createScript(r.readAllAsString()).execute();
            success = true;

            log.info("Successfully evaluated SQL resource \"{}\" (Forced: {})", resourcePath, forced);
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

    /**
     * Safety wrapper for collecting JAR resources without worrying about implicit null values.
     * @param path The path to resolve to a resource URL.
     * @return The resource URL.
     */
    private URL resource(String path) {
        final var resource = MigrationManager.class.getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException(
                    "Resource path provided does not resolve to a real resource: " + path);
        }

        return resource;
    }
}
