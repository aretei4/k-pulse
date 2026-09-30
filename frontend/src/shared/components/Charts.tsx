import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { colors, fonts } from '@/shared/theme';
import type { ConfidenceSummary, SentimentSummary } from '@/shared/types';
import { Legend } from './Card';

const tooltipStyle = { fontFamily: fonts.sans, fontSize: 12, border: `1px solid ${colors.line}`, borderRadius: 6 };
const axisTick = { fontFamily: fonts.sans, fontSize: 11, fill: colors.inkSoft };

export const sentimentLegend = [
  { name: 'Positive', color: colors.positive },
  { name: 'Neutral', color: colors.neutral },
  { name: 'Negative', color: colors.negative },
];

export function SentimentBarChart({ data, height = 200 }: { data: SentimentSummary[]; height?: number }) {
  const rows = data.map((d) => ({
    name: d.unitName,
    Positive: d.positive,
    Neutral: d.neutral,
    Negative: d.negative,
  }));

  return (
    <>
      <div style={{ height }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 4, right: 4, left: -18, bottom: 0 }}>
            <CartesianGrid stroke={colors.line} vertical={false} />
            <XAxis dataKey="name" tick={axisTick} axisLine={{ stroke: colors.line }} tickLine={false} interval={0} />
            <YAxis tick={axisTick} axisLine={false} tickLine={false} allowDecimals={false} />
            <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'rgba(21,42,56,0.05)' }} />
            <Bar dataKey="Positive" fill={colors.positive} radius={[3, 3, 0, 0]} />
            <Bar dataKey="Neutral" fill={colors.neutral} radius={[3, 3, 0, 0]} />
            <Bar dataKey="Negative" fill={colors.negative} radius={[3, 3, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <Legend items={sentimentLegend} />
    </>
  );
}

export function SentimentDonut({ data, height = 180 }: { data: SentimentSummary[]; height?: number }) {
  const totals = data.reduce(
    (acc, d) => ({
      positive: acc.positive + d.positive,
      neutral: acc.neutral + d.neutral,
      negative: acc.negative + d.negative,
    }),
    { positive: 0, neutral: 0, negative: 0 },
  );
  const slices = [
    { name: 'Positive', value: totals.positive, color: colors.positive },
    { name: 'Neutral', value: totals.neutral, color: colors.neutral },
    { name: 'Negative', value: totals.negative, color: colors.negative },
  ].filter((s) => s.value > 0);

  return (
    <>
      <div style={{ height }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={slices} dataKey="value" innerRadius="52%" outerRadius="82%" paddingAngle={2}>
              {slices.map((s) => (
                <Cell key={s.name} fill={s.color} />
              ))}
            </Pie>
            <Tooltip contentStyle={tooltipStyle} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <Legend items={slices.map((s) => ({ name: s.name, color: s.color }))} />
    </>
  );
}

export function ConfidenceDonut({ confidence, height = 180 }: { confidence: ConfidenceSummary; height?: number }) {
  const slices = [
    { name: 'High', value: confidence.high, color: colors.marigoldDeep },
    { name: 'Medium', value: confidence.medium, color: colors.marigold },
    { name: 'Low', value: confidence.low, color: colors.line },
  ].filter((s) => s.value > 0);

  return (
    <>
      <div style={{ height }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={slices} dataKey="value" innerRadius="52%" outerRadius="82%" paddingAngle={2}>
              {slices.map((s) => (
                <Cell key={s.name} fill={s.color} />
              ))}
            </Pie>
            <Tooltip contentStyle={tooltipStyle} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <Legend items={slices.map((s) => ({ name: s.name, color: s.color }))} />
    </>
  );
}

/** Single-booth positive-vs-negative bar, the compact form used on mobile. */
export function BoothSplitBar({ summary }: { summary: SentimentSummary }) {
  const rows = [{ name: summary.unitName, Positive: summary.positive, Negative: summary.negative }];
  return (
    <div style={{ height: 100 }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 8, left: 8, bottom: 4 }}>
          <XAxis type="number" hide allowDecimals={false} />
          <YAxis type="category" dataKey="name" hide />
          <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'rgba(21,42,56,0.05)' }} />
          <Bar dataKey="Positive" fill={colors.positive} radius={3} barSize={16} />
          <Bar dataKey="Negative" fill={colors.negative} radius={3} barSize={16} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
