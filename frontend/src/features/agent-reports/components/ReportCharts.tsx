import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { colors, fonts } from '@/shared/theme';
import type { AgentReport, AgentReportRow } from '@/shared/types';

/** The four things an agent can tick, in the order the page shows them. */
export type Slice = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | 'NOT_RECORDED';

export const sliceLabel: Record<Slice, string> = {
  POSITIVE: 'Positive',
  NEUTRAL: 'Neutral',
  NEGATIVE: 'Negative',
  NOT_RECORDED: 'Not recorded',
};

export const sliceColor: Record<Slice, string> = {
  POSITIVE: colors.positive,
  NEUTRAL: colors.neutral,
  NEGATIVE: colors.negative,
  NOT_RECORDED: colors.muted,
};

export function sliceValue(row: AgentReportRow, slice: Slice): number {
  if (slice === 'POSITIVE') return row.positive;
  if (slice === 'NEUTRAL') return row.neutral;
  if (slice === 'NEGATIVE') return row.negative;
  return row.notRecorded;
}

const tooltipStyle = {
  fontFamily: fonts.sans,
  fontSize: 12,
  border: `1px solid ${colors.line}`,
  borderRadius: 6,
  background: colors.card,
};

const axisTick = { fontSize: 10, fill: colors.inkSoft, fontFamily: fonts.sans };

/**
 * AG-5: one stacked bar per approved booth. Stacked rather than grouped so the
 * bar's height is the booth's roll under the current choices — an agent reads
 * "how much of this booth is done" off it at a glance.
 */
export function BoothStack({ report, slices }: { report: AgentReport; slices: Slice[] }) {
  const rows = report.booths.map((booth) => {
    const row: Record<string, string | number> = { name: booth.boothName };
    slices.forEach((slice) => {
      row[sliceLabel[slice]] = sliceValue(booth, slice);
    });
    return row;
  });

  return (
    <div style={{ height: Math.max(180, rows.length * 38) }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 8, left: 4, bottom: 4 }}>
          <CartesianGrid stroke={colors.line} horizontal={false} />
          <XAxis type="number" tick={axisTick} axisLine={{ stroke: colors.line }} tickLine={false} allowDecimals={false} />
          <YAxis type="category" dataKey="name" tick={axisTick} width={86} axisLine={false} tickLine={false} />
          <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'rgba(21,42,56,0.05)' }} />
          {slices.map((slice) => (
            <Bar
              key={slice}
              dataKey={sliceLabel[slice]}
              stackId="booth"
              fill={sliceColor[slice]}
              barSize={18}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** AG-5: the agent's booths together — "your booths", not the whole panchayat. */
export function TotalsPie({ report, slices }: { report: AgentReport; slices: Slice[] }) {
  const data = slices
    .map((slice) => ({ name: sliceLabel[slice], value: sliceValue(report.totals, slice), color: sliceColor[slice] }))
    .filter((slice) => slice.value > 0);

  if (data.length === 0) {
    return (
      <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.muted, margin: 0 }}>
        Nothing to show for these choices.
      </p>
    );
  }

  return (
    <div style={{ height: 190 }}>
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="name" innerRadius={34} outerRadius={70} paddingAngle={2}>
            {data.map((slice) => (
              <Cell key={slice.name} fill={slice.color} />
            ))}
          </Pie>
          <Tooltip contentStyle={tooltipStyle} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

export function SliceLegend({ slices }: { slices: Slice[] }) {
  return (
    <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 6 }}>
      {slices.map((slice) => (
        <span
          key={slice}
          style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontFamily: fonts.sans, fontSize: 11, color: colors.inkSoft }}
        >
          <span style={{ width: 9, height: 9, borderRadius: 2, background: sliceColor[slice] }} />
          {sliceLabel[slice]}
        </span>
      ))}
    </div>
  );
}
