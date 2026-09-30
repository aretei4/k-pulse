import { PageHeader } from '@/shared/layouts/PageHeader';
import { HouseReportPanel } from '../components/HouseReportPanel';

/**
 * FR-U12 seen from the campaign side: houses tallied, rolled up booth to
 * district. The same panel appears as the Pre-election tab on Reports.
 */
export function HouseDashboardPage() {
  return (
    <div>
      <PageHeader
        title="Pre-election sentiment"
        subtitle="House-level tallies from the field, rolled up from booths. Households, not named voters — these figures are separate from the voter roll reports."
      />
      <HouseReportPanel />
    </div>
  );
}
