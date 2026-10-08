import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AdminLayout } from '@/shared/layouts/AdminLayout';
import { AgentLayout } from '@/shared/layouts/AgentLayout';
import { RequireRole } from '@/shared/hooks/useAuth';
import { PRE_ELECTION_ONLY } from '@/lib/features';
import { LandingPage } from '@/features/landing/pages/LandingPage';
import { DeleteAccountPage } from '@/features/account-deletion/pages/DeleteAccountPage';
import { AccountDeletionsPage } from '@/features/account-deletion/pages/AccountDeletionsPage';
import { HouseDashboardPage } from '@/features/house-sentiment/pages/HouseDashboardPage';
import { CandidateSentimentPage } from '@/features/candidate-sentiment/pages/CandidateSentimentPage';
import { HouseUnitDetailPage } from '@/features/house-sentiment/pages/HouseUnitDetailPage';
import { AdminLoginPage } from '@/features/auth/pages/AdminLoginPage';
import { AgentLoginPage } from '@/features/auth/pages/AgentLoginPage';
import { AgentSignupPage } from '@/features/auth/pages/AgentSignupPage';
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage';
import { AccessRequestsPage } from '@/features/access-requests/pages/AccessRequestsPage';
import { VoterListsPage } from '@/features/voter-lists/pages/VoterListsPage';
import { VoterChangeRequestsPage } from '@/features/voter-changes/pages/VoterChangeRequestsPage';
import { UsersPage } from '@/features/users/pages/UsersPage';
import { AdminsPage } from '@/features/users/pages/AdminsPage';
import { LocationsPage } from '@/features/locations/pages/LocationsPage';
import { ReportsPage } from '@/features/reports/pages/ReportsPage';
import { StatusPage } from '@/features/agent-status/pages/StatusPage';
import { RequestAccessPage } from '@/features/agent-access-request/pages/RequestAccessPage';
import { UnitSelectPage } from '@/features/agent-voter-list/pages/UnitSelectPage';
import { VoterListPage } from '@/features/agent-voter-list/pages/VoterListPage';
import { VoterChangePage } from '@/features/agent-voter-list/pages/VoterChangePage';
import { SentimentEntryPage } from '@/features/sentiment-entry/pages/SentimentEntryPage';
import { HouseSentimentPage } from '@/features/house-sentiment/pages/HouseSentimentPage';
import { HouseFormPage } from '@/features/house-sentiment/pages/HouseFormPage';
import { HouseInsightsPage } from '@/features/house-sentiment/pages/HouseInsightsPage';
import { InsightsPage } from '@/features/agent-insights/pages/InsightsPage';

/** Must match Vite's `base` and the nginx `location /kpulse/` mount. */
const BASENAME = '/kpulse';

/**
 * One app, three route trees: "/" splash, "/admin/*", "/agent/*".
 * The mobile shell points its WebView straight at /agent and skips the splash.
 */
export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  // Public, no sign-in: the account-deletion URL registered with Google Play.
  { path: '/delete-account', element: <DeleteAccountPage /> },

  { path: '/admin/login', element: <AdminLoginPage /> },
  {
    path: '/admin',
    element: (
      <RequireRole role="ADMIN">
        <AdminLayout />
      </RequireRole>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'requests', element: <AccessRequestsPage /> },
      { path: 'voter-lists', element: <VoterListsPage /> },
      { path: 'voter-changes', element: <VoterChangeRequestsPage /> },
      { path: 'users', element: <UsersPage /> },
      // FR-A13: admin accounts, super admin only (the page says so, the API enforces it).
      { path: 'admins', element: <AdminsPage /> },
      { path: 'account-deletions', element: <AccountDeletionsPage /> },
      { path: 'locations', element: <LocationsPage /> },
      // FR-U12 from the campaign side: house tallies rolled up.
      // FR-A15: one candidate, their verdict, and the units driving it.
      { path: 'candidate-sentiment', element: <CandidateSentimentPage /> },
      { path: 'pre-election', element: <HouseDashboardPage /> },
      // The houses behind one row of the pre-election report.
      { path: 'pre-election/unit/:unitId', element: <HouseUnitDetailPage /> },
      { path: 'reports', element: <ReportsPage /> },
    ],
  },

  { path: '/agent/login', element: <AgentLoginPage /> },
  { path: '/agent/signup', element: <AgentSignupPage /> },
  {
    path: '/agent',
    element: (
      <RequireRole role="FIELD_AGENT">
        <AgentLayout />
      </RequireRole>
    ),
    children: [
      // Agents land on their voter list; /agent (login, the mobile shell) redirects there.
      { index: true, element: <Navigate to="voters" replace /> },
      { path: 'status', element: <StatusPage /> },
      { path: 'request', element: <RequestAccessPage /> },
      { path: 'voters', element: <UnitSelectPage /> },
      // With VITE_PRE_ELECTION_ONLY the named-voter screens are closed, so an
      // old link or bookmark lands back on the unit picker rather than a screen
      // this build no longer offers.
      { path: 'voters/list', element: PRE_ELECTION_ONLY ? <Navigate to="/agent/voters" replace /> : <VoterListPage /> },
      { path: 'voters/change', element: PRE_ELECTION_ONLY ? <Navigate to="/agent/voters" replace /> : <VoterChangePage /> },
      {
        path: 'voters/:voterId/sentiment',
        element: PRE_ELECTION_ONLY ? <Navigate to="/agent/voters" replace /> : <SentimentEntryPage />,
      },
      // FR-U12: house-level tallies, reached from the same unit picker as the voter list.
      { path: 'houses', element: <HouseSentimentPage /> },
      { path: 'houses/record', element: <HouseFormPage /> },
      // A pre-election-only build has no named-voter entries to chart, so the
      // Insights tab reads the house tallies instead.
      { path: 'insights', element: PRE_ELECTION_ONLY ? <HouseInsightsPage /> : <InsightsPage /> },
    ],
  },

  { path: '*', element: <Navigate to="/" replace /> },
], { basename: BASENAME });
