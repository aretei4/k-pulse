package com.kahga.pluse.auth.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.auth.entity.OtpToken;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OtpTokenRepository {

    private static final String SELECT = "SELECT id, phone, code, expires_at, consumed, created_at FROM otp_token";

    private static final String INSERT = """
            INSERT INTO otp_token (id, phone, code, expires_at, consumed, created_at)
            VALUES (:id, :phone, :code, :expiresAt, :consumed, :createdAt)
            """;

    private static final String UPDATE = """
            UPDATE otp_token
               SET phone = :phone, code = :code, expires_at = :expiresAt, consumed = :consumed, created_at = :createdAt
             WHERE id = :id
            """;

    private static final RowMapper<OtpToken> MAPPER = (rs, rowNum) -> OtpToken.builder()
            .id(uuid(rs, "id"))
            .phone(rs.getString("phone"))
            .code(rs.getString("code"))
            .expiresAt(instant(rs, "expires_at"))
            .consumed(rs.getBoolean("consumed"))
            .createdAt(instant(rs, "created_at"))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<OtpToken> findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(String phone) {
        return jdbc.query(
                        SELECT + " WHERE phone = :phone AND consumed = false ORDER BY created_at DESC LIMIT 1",
                        new MapSqlParameterSource("phone", phone),
                        MAPPER)
                .stream()
                .findFirst();
    }

    public OtpToken save(OtpToken token) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", token.getId())
                .addValue("phone", token.getPhone())
                .addValue("code", token.getCode())
                .addValue("expiresAt", timestamp(token.getExpiresAt()))
                .addValue("consumed", token.isConsumed())
                .addValue("createdAt", timestamp(token.getCreatedAt()));
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return token;
    }

    /** Sign-in codes hold the phone number, so they go with the account. */
    public int deleteByPhone(String phone) {
        return jdbc.update("DELETE FROM otp_token WHERE phone = :phone", new MapSqlParameterSource("phone", phone));
    }
}
