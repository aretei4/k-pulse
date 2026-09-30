package com.kahga.pluse.voter.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.user.repository.UserRepository;
import com.kahga.pluse.voter.entity.VoterUpload;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class VoterUploadRepository {

    private static final String SELECT = """
            SELECT x.id, x.file_name, x.row_count, x.status, x.message, x.uploaded_at,
                   %s,
                   %s
              FROM voter_upload x
              JOIN unit u ON u.id = x.unit_id
              JOIN app_user ub ON ub.id = x.uploaded_by_id
            """.formatted(UnitRepository.columns("u", "unit_"), UserRepository.columns("ub", "uploaded_by_"));

    private static final String INSERT = """
            INSERT INTO voter_upload (id, file_name, unit_id, row_count, status, message, uploaded_by_id, uploaded_at)
            VALUES (:id, :fileName, :unitId, :rowCount, :status, :message, :uploadedById, :uploadedAt)
            """;

    private static final String UPDATE = """
            UPDATE voter_upload
               SET file_name = :fileName, unit_id = :unitId, row_count = :rowCount, status = :status,
                   message = :message, uploaded_by_id = :uploadedById, uploaded_at = :uploadedAt
             WHERE id = :id
            """;

    private static final RowMapper<VoterUpload> MAPPER = (rs, rowNum) -> VoterUpload.builder()
            .id(uuid(rs, "id"))
            .fileName(rs.getString("file_name"))
            .unit(UnitRepository.map(rs, "unit_"))
            .rowCount(rs.getInt("row_count"))
            .status(rs.getString("status"))
            .message(rs.getString("message"))
            .uploadedBy(UserRepository.map(rs, "uploaded_by_"))
            .uploadedAt(instant(rs, "uploaded_at"))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    public List<VoterUpload> findAllByOrderByUploadedAtDesc() {
        return jdbc.query(SELECT + " ORDER BY x.uploaded_at DESC", MAPPER);
    }

    public VoterUpload save(VoterUpload upload) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", upload.getId())
                .addValue("fileName", upload.getFileName())
                .addValue("unitId", upload.getUnit().getId())
                .addValue("rowCount", upload.getRowCount())
                .addValue("status", upload.getStatus())
                .addValue("message", upload.getMessage())
                .addValue("uploadedById", upload.getUploadedBy().getId())
                .addValue("uploadedAt", timestamp(upload.getUploadedAt()));
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return upload;
    }

    public long countByUnitId(UUID unitId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM voter_upload WHERE unit_id = :unitId",
                new MapSqlParameterSource("unitId", unitId),
                Long.class);
        return value == null ? 0 : value;
    }
}
