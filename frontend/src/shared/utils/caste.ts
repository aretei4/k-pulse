import type { Caste } from '@/shared/types';

/**
 * The reservation categories, in the order the dropdown offers them, matching
 * the backend's Caste enum. Individual caste names differ by district and would
 * need a master of their own; these are what a break-up of a booth groups by.
 */
export const casteOptions: Array<{ value: Caste; label: string }> = [
  { value: 'GENERAL', label: 'General' },
  { value: 'SEBC', label: 'SEBC / OBC' },
  { value: 'SC', label: 'SC' },
  { value: 'ST', label: 'ST' },
  { value: 'MINORITY', label: 'Minority' },
  { value: 'OTHER', label: 'Other' },
];

/** What to show where a voter has none: the roll never carried one. */
export function casteLabel(caste: Caste | null | undefined) {
  return casteOptions.find((option) => option.value === caste)?.label ?? 'Caste not recorded';
}
