package com.kahga.pluse.voter.repository;

import static com.kahga.pluse.common.jdbc.JdbcSupport.enumValue;
import static com.kahga.pluse.common.jdbc.JdbcSupport.instant;
import static com.kahga.pluse.common.jdbc.JdbcSupport.integer;
import static com.kahga.pluse.common.jdbc.JdbcSupport.name;
import static com.kahga.pluse.common.jdbc.JdbcSupport.timestamp;
import static com.kahga.pluse.common.jdbc.JdbcSupport.uuid;

import com.kahga.pluse.common.response.PageResult;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.sentiment.entity.SentimentValue;
import com.kahga.pluse.voter.entity.Gender;
import com.kahga.pluse.voter.entity.Voter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class VoterRepository {

    /** The booth is on every voter row we render, so it is always joined in. */
    private static final String SELECT = """
            SELECT v.id, v.epic_no, v.name, v.relation, v.house_no, v.age, v.gender, v.ward_no, v.created_at,
                   %s
              FROM voter v
              JOIN unit b ON b.id = v.booth_id
            """.formatted(UnitRepository.columns("b", "booth_"));

    private static final String INSERT = """
            INSERT INTO voter (id, epic_no, name, relation, house_no, age, gender, booth_id, ward_no, created_at)
            VALUES (:id, :epicNo, :name, :relation, :houseNo, :age, :gender, :boothId, :wardNo, :createdAt)
            """;

    private static final String UPDATE = """
            UPDATE voter
               SET epic_no = :epicNo, name = :name, relation = :relation, house_no = :houseNo, age = :age,
                   gender = :gender, booth_id = :boothId, ward_no = :wardNo, created_at = :createdAt
             WHERE id = :id
            """;

    private static final RowMapper<Voter> MAPPER = (rs, rowNum) -> Voter.builder()
            .id(uuid(rs, "id"))
            .epicNo(rs.getString("epic_no"))
            .name(rs.getString("name"))
            .relation(rs.getString("relation"))
            .houseNo(rs.getString("house_no"))
            .age(rs.getInt("age"))
            .gender(enumValue(rs, "gender", Gender.class))
            .booth(UnitRepository.map(rs, "booth_"))
            .wardNo(integer(rs, "ward_no"))
            .createdAt(instant(rs, "created_at"))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<Voter> findById(UUID id) {
        return first(SELECT + " WHERE v.id = :id", new MapSqlParameterSource("id", id));
    }

    public Optional<Voter> findByEpicNoIgnoreCase(String epicNo) {
        return first(SELECT + " WHERE LOWER(v.epic_no) = LOWER(:epicNo)", new MapSqlParameterSource("epicNo", epicNo));
    }

    public boolean existsByEpicNoIgnoreCase(String epicNo) {
        return count(
                        "SELECT COUNT(*) FROM voter WHERE LOWER(epic_no) = LOWER(:epicNo)",
                        new MapSqlParameterSource("epicNo", epicNo))
                > 0;
    }

    public long count() {
        return count("SELECT COUNT(*) FROM voter", new MapSqlParameterSource());
    }

    /**
     * Roll order: by house number, numerically, so "4/A" comes before "13" (a
     * text sort would put "13" first). The numeric part is the leading digits;
     * voters without one (blank or lettered house numbers) go last. Within a
     * house, by name; v.id breaks the remaining ties so pages never overlap or
     * skip. The regex form is plain enough for both PostgreSQL and H2.
     */
    private static final String ORDER_BY =
            " ORDER BY CAST(NULLIF(REGEXP_REPLACE(COALESCE(v.house_no, ''), '[^0-9].*$', ''), '') AS NUMERIC) NULLS LAST,"
                    + " v.house_no, v.name, v.id";

    /**
     * The voter roll, filtered and paged — shared by the admin and agent lists.
     * Every filter is optional. Sentiment lives in its own table, so filtering by
     * it is an EXISTS subquery rather than a join, which keeps the count honest.
     *
     * @param boothIds restrict to these booths; null means unrestricted, empty means nothing matches
     * @param notRecorded voters with no entry (for {@code candidateId}, if given) instead of those with one
     */
    public PageResult<Voter> search(
            Collection<UUID> boothIds,
            UUID boothId,
            String search,
            VoterSearchBy searchBy,
            SentimentValue sentiment,
            boolean notRecorded,
            UUID candidateId,
            int page,
            int size) {

        if (boothIds != null && boothIds.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0);
        }

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (boothIds != null) {
            where.append(" AND v.booth_id IN (:boothIds)");
            params.addValue("boothIds", boothIds);
        }
        if (boothId != null) {
            where.append(" AND v.booth_id = :boothId");
            params.addValue("boothId", boothId);
        }
        if (search != null && !search.isBlank()) {
            String column = searchBy == VoterSearchBy.HOUSE_NO ? "v.house_no" : "v.epic_no";
            where.append(" AND (LOWER(v.name) LIKE :search OR LOWER(").append(column).append(") LIKE :search)");
            params.addValue("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (notRecorded || sentiment != null) {
            StringBuilder entry = new StringBuilder("SELECT 1 FROM sentiment_entry e WHERE e.voter_id = v.id");
            if (candidateId != null) {
                entry.append(" AND e.candidate_id = :candidateId");
                params.addValue("candidateId", candidateId);
            }
            if (sentiment != null) {
                entry.append(" AND e.sentiment = :sentiment");
                params.addValue("sentiment", sentiment.name());
            }
            where.append(notRecorded ? " AND NOT EXISTS (" : " AND EXISTS (").append(entry).append(')');
        }

        long total = count("SELECT COUNT(*) FROM voter v" + where, params);
        if (total == 0) {
            return new PageResult<>(List.of(), page, size, 0);
        }

        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<Voter> content =
                jdbc.query(SELECT + where + ORDER_BY + " LIMIT :limit OFFSET :offset", params, MAPPER);
        return new PageResult<>(content, page, size, total);
    }

    public Voter save(Voter voter) {
        MapSqlParameterSource params = params(voter);
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
        return voter;
    }

    /** One batched statement for a roll of voters that do not exist yet — Excel import and seed data. */
    public void insertAll(List<Voter> voters) {
        if (voters.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT, voters.stream().map(VoterRepository::params).toArray(SqlParameterSource[]::new));
    }

    /**
     * The schema cascades the voter's sentiment entries and nulls the link on any
     * change request that pointed at it.
     */
    public void delete(Voter voter) {
        jdbc.update("DELETE FROM voter WHERE id = :id", new MapSqlParameterSource("id", voter.getId()));
    }

    private Optional<Voter> first(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, MAPPER).stream().findFirst();
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private static MapSqlParameterSource params(Voter voter) {
        return new MapSqlParameterSource()
                .addValue("id", voter.getId())
                .addValue("epicNo", voter.getEpicNo())
                .addValue("name", voter.getName())
                .addValue("relation", voter.getRelation())
                .addValue("houseNo", voter.getHouseNo())
                .addValue("age", voter.getAge())
                .addValue("gender", name(voter.getGender()))
                .addValue("boothId", voter.getBooth().getId())
                .addValue("wardNo", voter.getWardNo())
                .addValue("createdAt", timestamp(voter.getCreatedAt()));
    }

    public long countByBoothId(UUID boothId) {
        return count("SELECT COUNT(*) FROM voter WHERE booth_id = :boothId",
                new MapSqlParameterSource("boothId", boothId));
    }

    /** Bulk clear for one booth. The schema cascades each voter's sentiment entries. */
    public int deleteByBoothId(UUID boothId) {
        return jdbc.update(
                "DELETE FROM voter WHERE booth_id = :boothId", new MapSqlParameterSource("boothId", boothId));
    }
}
