package com.rubengarcia.choir.scene.adapter.out.persistence;

import com.rubengarcia.choir.transition.application.port.out.SceneRuleSeparationSettingsPort;
import java.math.BigDecimal;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Component
public class R2dbcSceneRuleSeparationSettingsAdapter
        implements SceneRuleSeparationSettingsPort {

    private final DatabaseClient database;

    public R2dbcSceneRuleSeparationSettingsAdapter(DatabaseClient database) {
        this.database = database;
    }

    @Override
    public Mono<Setting> getCurrent() {
        return database.sql("""
                SELECT revision, threshold
                FROM scene_rule_separation_settings
                WHERE id = 1
                """)
                .map((row, metadata) -> new Setting(
                        row.get("revision", Long.class),
                        row.get("threshold", BigDecimal.class)))
                .one()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "No está configurado el umbral global de separación")));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Setting> updateCurrent(
            BigDecimal threshold, String changedByEmail) {
        return database.sql("""
                SELECT revision, threshold
                FROM scene_rule_separation_settings
                WHERE id = 1
                FOR UPDATE
                """)
                .map((row, metadata) -> new Setting(
                        row.get("revision", Long.class),
                        row.get("threshold", BigDecimal.class)))
                .one()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "No está configurado el umbral global de separación")))
                .flatMap(previous -> recordRevision(
                        previous, threshold, changedByEmail));
    }

    private Mono<Setting> recordRevision(
            Setting previous, BigDecimal threshold, String changedByEmail) {
        long nextRevision = previous.revision() + 1;
        return database.sql("""
                INSERT INTO scene_rule_separation_setting_revisions (
                    revision, previous_threshold, new_threshold, changed_by_email
                ) VALUES (
                    :revision, :previousThreshold, :newThreshold, :changedByEmail
                )
                """)
                .bind("revision", nextRevision)
                .bind("previousThreshold", previous.threshold())
                .bind("newThreshold", threshold)
                .bind("changedByEmail", changedByEmail)
                .fetch()
                .rowsUpdated()
                .flatMap(inserted -> inserted == 1
                        ? updateCurrentRow(nextRevision, threshold, changedByEmail)
                        : Mono.error(new IllegalStateException(
                                "No se pudo registrar la revisión del umbral")));
    }

    private Mono<Setting> updateCurrentRow(
            long revision, BigDecimal threshold, String changedByEmail) {
        return database.sql("""
                UPDATE scene_rule_separation_settings
                SET revision = :revision,
                    threshold = :threshold,
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by_email = :changedByEmail
                WHERE id = 1
                """)
                .bind("revision", revision)
                .bind("threshold", threshold)
                .bind("changedByEmail", changedByEmail)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> updated == 1
                        ? Mono.just(new Setting(revision, threshold))
                        : Mono.error(new IllegalStateException(
                                "No se pudo actualizar el umbral global")));
    }
}
