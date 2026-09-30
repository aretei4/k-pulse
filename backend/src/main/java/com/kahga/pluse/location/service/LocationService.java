package com.kahga.pluse.location.service;

import com.kahga.pluse.accessrequest.repository.AccessRequestRepository;
import com.kahga.pluse.common.exception.BusinessException;
import com.kahga.pluse.common.exception.NotFoundException;
import com.kahga.pluse.housesentiment.repository.HouseSentimentEntryRepository;
import com.kahga.pluse.location.dto.UnitRequest;
import com.kahga.pluse.location.entity.Unit;
import com.kahga.pluse.location.entity.UnitLevel;
import com.kahga.pluse.location.repository.UnitRepository;
import com.kahga.pluse.voter.repository.VoterRepository;
import com.kahga.pluse.voter.repository.VoterUploadRepository;
import com.kahga.pluse.voterchangerequest.repository.VoterChangeRequestRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    /** Coarse to fine: a unit's parent is always the level directly before it here. */
    private static final List<UnitLevel> ORDER =
            List.of(UnitLevel.DISTRICT, UnitLevel.BLOCK, UnitLevel.PANCHAYAT, UnitLevel.BOOTH);

    private final UnitRepository unitRepository;
    private final VoterRepository voterRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final VoterUploadRepository voterUploadRepository;
    private final VoterChangeRequestRepository voterChangeRequestRepository;
    private final HouseSentimentEntryRepository houseSentimentEntryRepository;

    public List<Unit> findAll() {
        return unitRepository.findAllByOrderByPathAsc();
    }

    public List<Unit> find(UnitLevel level, UUID parentId) {
        if (level != null && parentId != null) {
            return unitRepository.findByParentIdOrderByNameAsc(parentId).stream()
                    .filter(u -> u.getLevel() == level)
                    .toList();
        }
        if (level != null) {
            return unitRepository.findByLevelOrderByNameAsc(level);
        }
        if (parentId != null) {
            return unitRepository.findByParentIdOrderByNameAsc(parentId);
        }
        return findAll();
    }

    public Unit require(UUID unitId) {
        return unitRepository.findById(unitId).orElseThrow(() -> new NotFoundException("Unknown unit"));
    }

    @Transactional
    public Unit create(UnitRequest request) {
        String name = cleanName(request.name());
        Unit parent = resolveParent(request.level(), request.parentId());
        requireNameFree(name, parent, null);

        Unit unit = Unit.builder()
                .id(UUID.randomUUID())
                .level(request.level())
                .name(name)
                .path(pathFor(name, parent))
                .parent(parent)
                .build();
        return unitRepository.save(unit);
    }

    /**
     * Renaming or moving a unit rewrites the readable trail of everything beneath
     * it, so the paths shown across the app stay true.
     */
    @Transactional
    public Unit update(UUID id, UnitRequest request) {
        Unit unit = require(id);
        if (unit.getLevel() != request.level()) {
            throw new BusinessException(
                    "A location's level cannot be changed. Delete it and add it again at the level you want.");
        }
        Unit parent = resolveParent(request.level(), request.parentId());
        if (parent != null && idsAtOrUnder(id).contains(parent.getId())) {
            throw new BusinessException("A location cannot sit under itself or one of its own locations");
        }
        String name = cleanName(request.name());
        requireNameFree(name, parent, id);

        unit.setName(name);
        unit.setParent(parent);
        unit.setPath(pathFor(name, parent));
        unitRepository.save(unit);
        repath(unit);
        return unit;
    }

    @Transactional
    public void delete(UUID id) {
        Unit unit = require(id);
        refuseIfInUse(unit);
        unitRepository.delete(id);
    }

    /**
     * Every booth at or below a unit. This is what makes FR-U3 work: approving a
     * panchayat hands the agent all of its booths without extra bookkeeping.
     */
    public Set<UUID> boothIdsUnder(UUID unitId) {
        Unit root = require(unitId);
        if (root.getLevel() == UnitLevel.BOOTH) {
            return Set.of(root.getId());
        }
        Set<UUID> booths = new LinkedHashSet<>();
        List<UUID> frontier = List.of(root.getId());
        while (!frontier.isEmpty()) {
            List<Unit> children = unitRepository.findByParentIds(frontier);
            List<UUID> next = new ArrayList<>();
            for (Unit child : children) {
                if (child.getLevel() == UnitLevel.BOOTH) {
                    booths.add(child.getId());
                } else {
                    next.add(child.getId());
                }
            }
            frontier = next;
        }
        return booths;
    }

    public Set<UUID> boothIdsUnder(Iterable<UUID> unitIds) {
        Set<UUID> booths = new LinkedHashSet<>();
        unitIds.forEach(id -> booths.addAll(boothIdsUnder(id)));
        return booths;
    }

    private String cleanName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new BusinessException("Enter a name");
        }
        return clean;
    }

    private Unit resolveParent(UnitLevel level, UUID parentId) {
        if (level == UnitLevel.DISTRICT) {
            if (parentId != null) {
                throw new BusinessException("A district sits at the top, so it has no parent");
            }
            return null;
        }
        UnitLevel expected = ORDER.get(ORDER.indexOf(level) - 1);
        if (parentId == null) {
            throw new BusinessException("Pick the " + label(expected) + " this " + label(level) + " belongs to");
        }
        Unit parent = require(parentId);
        if (parent.getLevel() != expected) {
            throw new BusinessException(
                    "A " + label(level) + " belongs to a " + label(expected) + ", not a " + label(parent.getLevel()));
        }
        return parent;
    }

    private void requireNameFree(String name, Unit parent, UUID ignoreId) {
        List<Unit> siblings = parent == null
                ? unitRepository.findByLevelOrderByNameAsc(UnitLevel.DISTRICT)
                : unitRepository.findByParentIdOrderByNameAsc(parent.getId());
        boolean taken = siblings.stream()
                .filter(u -> ignoreId == null || !u.getId().equals(ignoreId))
                .anyMatch(u -> u.getName().equalsIgnoreCase(name));
        if (taken) {
            String where = parent == null ? "at district level" : "under " + parent.getName();
            throw new BusinessException("There is already a " + name + " " + where, HttpStatus.CONFLICT);
        }
    }

    private String pathFor(String name, Unit parent) {
        return parent == null ? name : parent.getPath() + " > " + name;
    }

    private String label(UnitLevel level) {
        return level.name().toLowerCase(Locale.ROOT);
    }

    /** The unit itself plus everything under it — used to refuse a move into its own subtree. */
    /** A unit and everything beneath it — the shape an admin's scope resolves to. */
    public Set<UUID> idsAtOrUnder(UUID unitId) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(unitId);
        List<UUID> frontier = List.of(unitId);
        while (!frontier.isEmpty()) {
            List<Unit> children = unitRepository.findByParentIds(frontier);
            List<UUID> next = new ArrayList<>();
            for (Unit child : children) {
                if (ids.add(child.getId())) {
                    next.add(child.getId());
                }
            }
            frontier = next;
        }
        return ids;
    }

    private void repath(Unit parent) {
        for (Unit child : unitRepository.findByParentIdOrderByNameAsc(parent.getId())) {
            child.setParent(parent);
            child.setPath(pathFor(child.getName(), parent));
            unitRepository.save(child);
            repath(child);
        }
    }

    /**
     * Deleting is only allowed once nothing points at the unit. The foreign keys
     * would stop it anyway, but as a 500 with no explanation.
     */
    private void refuseIfInUse(Unit unit) {
        String name = unit.getName();
        long children = unitRepository.countByParentId(unit.getId());
        if (children > 0) {
            throw new BusinessException(
                    name + " still has " + children + " location(s) under it. Delete those first.", HttpStatus.CONFLICT);
        }
        long voters = voterRepository.countByBoothId(unit.getId());
        if (voters > 0) {
            throw new BusinessException(
                    name + " holds " + voters + " voter(s), so it cannot be deleted.", HttpStatus.CONFLICT);
        }
        long grants = accessRequestRepository.countByUnitId(unit.getId());
        if (grants > 0) {
            throw new BusinessException(
                    grants + " access request(s) point at " + name + ", so it cannot be deleted.", HttpStatus.CONFLICT);
        }
        long uploads = voterUploadRepository.countByUnitId(unit.getId());
        if (uploads > 0) {
            throw new BusinessException(
                    uploads + " roll upload(s) point at " + name + ", so it cannot be deleted.", HttpStatus.CONFLICT);
        }
        long changes = voterChangeRequestRepository.countByBoothId(unit.getId());
        if (changes > 0) {
            throw new BusinessException(
                    changes + " voter change request(s) point at " + name + ", so it cannot be deleted.",
                    HttpStatus.CONFLICT);
        }
        long houses = houseSentimentEntryRepository.countByBoothId(unit.getId());
        if (houses > 0) {
            throw new BusinessException(
                    houses + " pre-election house entr(ies) were recorded in " + name + ", so it cannot be deleted.",
                    HttpStatus.CONFLICT);
        }
    }
}
