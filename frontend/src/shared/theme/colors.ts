/** Design tokens lifted verbatim from the K-Pulse UX mockup. */
export const colors = {
  ink: '#152A38',
  inkSoft: '#4A5A66',
  paper: '#F3EEE3',
  card: '#FFFFFF',
  line: '#DAD2BE',
  marigold: '#C98A2E',
  marigoldDeep: '#8F5F17',
  positive: '#2F6B4F',
  negative: '#B5482F',
  neutral: '#A9925E',
  muted: '#8A8271',
  positiveWash: '#E7F0EA',
  neutralWash: '#F1EBDC',
  negativeWash: '#F6E7E2',
  marigoldWash: '#F5E9D5',
} as const;

export type ColorToken = keyof typeof colors;
