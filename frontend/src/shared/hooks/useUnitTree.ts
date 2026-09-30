import { useMemo } from 'react';
import { useAsync } from './useAsync';
import { referenceApi } from '@/shared/services/referenceApi';
import type { Unit, UnitLevel } from '@/shared/types';

export interface UnitTree {
  units: Unit[];
  loading: boolean;
  error: string | null;
  byLevel: (level: UnitLevel) => Unit[];
  childrenOf: (parentId: string | null) => Unit[];
  boothsUnder: (unitId: string | null) => Unit[];
  find: (unitId: string | null | undefined) => Unit | undefined;
}

/** Loads the whole District > Block > Panchayat > Booth tree once and slices it locally. */
export function useUnitTree(): UnitTree {
  const { data, loading, error } = useAsync(() => referenceApi.units(), []);
  const units = useMemo(() => data ?? [], [data]);

  return useMemo(() => {
    const childrenOf = (parentId: string | null) => units.filter((u) => u.parentId === parentId);

    const boothsUnder = (unitId: string | null): Unit[] => {
      if (!unitId) return units.filter((u) => u.level === 'BOOTH');
      const start = units.find((u) => u.id === unitId);
      if (!start) return [];
      if (start.level === 'BOOTH') return [start];
      const out: Unit[] = [];
      const stack = childrenOf(start.id);
      while (stack.length) {
        const unit = stack.pop()!;
        if (unit.level === 'BOOTH') out.push(unit);
        else stack.push(...childrenOf(unit.id));
      }
      return out.sort((a, b) => a.name.localeCompare(b.name));
    };

    return {
      units,
      loading,
      error,
      byLevel: (level: UnitLevel) => units.filter((u) => u.level === level),
      childrenOf,
      boothsUnder,
      find: (unitId: string | null | undefined) => units.find((u) => u.id === unitId),
    };
  }, [units, loading, error]);
}
