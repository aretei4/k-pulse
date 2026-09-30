import { useMemo, useState } from 'react';
import { ShieldCheck, ShieldOff, UserPlus } from 'lucide-react';
import {
  Button,
  EmptyState,
  ErrorNote,
  Field,
  InfoNote,
  Modal,
  Row,
  RowList,
  Select,
  Spinner,
  Tag,
  useToast,
} from '@/shared/components';
import { PageHeader } from '@/shared/layouts/PageHeader';
import { useAsync } from '@/shared/hooks/useAsync';
import { useAuth } from '@/shared/hooks/useAuth';
import { useUnitTree } from '@/shared/hooks/useUnitTree';
import { colors, fonts } from '@/shared/theme';
import { isAdminRole } from '@/shared/types';
import type { AccountUser, UnitLevel } from '@/shared/types';
import { usersApi } from '../services/usersApi';

/** FR-A9: an admin is scoped to one of these — never a booth. */
const scopeLevels = [
  { value: 'DISTRICT', label: 'District' },
  { value: 'BLOCK', label: 'Block' },
  { value: 'PANCHAYAT', label: 'Panchayat' },
];

/**
 * FR-A13: administrator accounts, which only a super admin can create, re-scope
 * or switch off. The server enforces all of that; this screen just refuses to
 * show a door that would not open.
 */
export function AdminsPage() {
  const { notify } = useToast();
  const { user } = useAuth();
  const tree = useUnitTree();

  const accounts = useAsync(() => usersApi.admins(), []);
  const [adding, setAdding] = useState(false);
  const [rescoping, setRescoping] = useState<AccountUser | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [superAdmin, setSuperAdmin] = useState(false);
  const [scopeLevel, setScopeLevel] = useState<UnitLevel>('PANCHAYAT');
  const [scopeUnitId, setScopeUnitId] = useState('');

  const isSuperAdmin = user?.role === 'SUPER_ADMIN';
  const admins = useMemo(
    () => (accounts.data ?? []).filter((a) => isAdminRole(a.role)),
    [accounts.data],
  );
  const unitChoices = tree.byLevel(scopeLevel);

  function resetForm() {
    setName('');
    setEmail('');
    setPhone('');
    setPassword('');
    setSuperAdmin(false);
    setScopeLevel('PANCHAYAT');
    setScopeUnitId('');
    setError(null);
  }

  async function create() {
    if (!name.trim()) return setError('Enter the name');
    if (!email.trim()) return setError('Enter the work email');
    if (password.length < 8) return setError('The password must be at least 8 characters');
    if (!superAdmin && !scopeUnitId) return setError('Pick the unit this admin looks after');

    setBusy(true);
    setError(null);
    try {
      await usersApi.createAdmin({
        name: name.trim(),
        email: email.trim(),
        phone: phone.trim() || undefined,
        password,
        superAdmin,
        scopeLevel: superAdmin ? undefined : scopeLevel,
        scopeUnitId: superAdmin ? undefined : scopeUnitId,
      });
      notify(superAdmin ? 'Super admin created' : 'Admin created');
      setAdding(false);
      resetForm();
      accounts.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the account');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  async function saveScope() {
    if (!rescoping) return;
    if (!scopeUnitId) return setError('Pick the unit this admin looks after');
    setBusy(true);
    setError(null);
    try {
      await usersApi.updateScope(rescoping.id, scopeLevel, scopeUnitId);
      notify(`${rescoping.name} now looks after that unit`);
      setRescoping(null);
      accounts.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not change the area');
    } finally {
      setBusy(false);
    }
    return undefined;
  }

  async function setActive(account: AccountUser, active: boolean) {
    try {
      await usersApi.setActive(account.id, active);
      notify(active ? `${account.name} reactivated` : `${account.name} deactivated`);
      accounts.reload();
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Could not change that account', 'error');
    }
  }

  if (!isSuperAdmin) {
    return (
      <div>
        <PageHeader title="Administrators" />
        <EmptyState
          title="Only a super admin can manage admin accounts"
          hint="Ask a super admin to add, re-scope or deactivate an administrator."
        />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Administrators"
        subtitle="Super admins see everything. An ordinary admin is scoped to one district, block or panchayat and sees only that area."
        action={
          <Button
            tone="accent"
            small
            icon={UserPlus}
            onClick={() => {
              resetForm();
              setAdding(true);
            }}
          >
            Add admin
          </Button>
        }
      />

      {accounts.error && <ErrorNote message={accounts.error} />}

      {accounts.loading && admins.length === 0 ? (
        <Spinner />
      ) : admins.length === 0 ? (
        <EmptyState title="No admin accounts yet" />
      ) : (
        <RowList>
          {admins.map((account, i) => (
            <Row key={account.id} last={i === admins.length - 1}>
              <div style={{ minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <p style={{ fontFamily: fonts.sans, fontSize: 13, fontWeight: 500, color: colors.ink, margin: 0 }}>
                    {account.name}
                  </p>
                  <Tag color={account.role === 'SUPER_ADMIN' ? colors.marigoldDeep : colors.inkSoft}>
                    {account.role === 'SUPER_ADMIN' ? 'Super admin' : 'Admin'}
                  </Tag>
                  {!account.active && <Tag color={colors.negative}>Inactive</Tag>}
                </div>
                <p style={{ fontFamily: fonts.sans, fontSize: 12, color: colors.inkSoft, margin: '3px 0 0' }}>
                  {account.email}
                  {account.phone ? ` · ${account.phone}` : ''}
                </p>
                <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '2px 0 0' }}>
                  {account.role === 'SUPER_ADMIN'
                    ? 'Every unit'
                    : account.scopeUnitName
                      ? `${account.scopeLevel?.toLowerCase()} · ${account.scopeUnitName}`
                      : 'No area assigned'}
                </p>
              </div>

              <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                {account.role === 'ADMIN' && (
                  <Button
                    tone="ghost"
                    small
                    onClick={() => {
                      setScopeLevel(account.scopeLevel ?? 'PANCHAYAT');
                      setScopeUnitId(account.scopeUnitId ?? '');
                      setError(null);
                      setRescoping(account);
                    }}
                  >
                    Change area
                  </Button>
                )}
                {account.active ? (
                  <Button tone="ghost" small icon={ShieldOff} onClick={() => void setActive(account, false)}>
                    Deactivate
                  </Button>
                ) : (
                  <Button tone="ghost" small icon={ShieldCheck} onClick={() => void setActive(account, true)}>
                    Reactivate
                  </Button>
                )}
              </div>
            </Row>
          ))}
        </RowList>
      )}

      <div style={{ marginTop: 12 }}>
        <InfoNote>
          K-Pulse always keeps at least one active super admin — the last one cannot be deactivated, so the system can
          never end up with nobody able to administer it.
        </InfoNote>
      </div>

      <Modal open={adding} title="Add an administrator" onClose={() => setAdding(false)} width={460}>
        {error && <ErrorNote message={error} />}
        <Field label="Full name" placeholder="e.g. Debasish Patra" value={name} onChange={setName} />
        <Field label="Work email" placeholder="admin@k-pulse.in" value={email} onChange={setEmail} />
        <Field
          label="Phone (optional)"
          placeholder="98XXXXXX21"
          value={phone}
          onChange={(v) => setPhone(v.replace(/[^0-9]/g, '').slice(0, 10))}
        />
        <Field label="Temporary password" type="password" value={password} onChange={setPassword} />

        <Select
          label="Account type"
          options={[
            { value: 'ADMIN', label: 'Admin — one district, block or panchayat' },
            { value: 'SUPER_ADMIN', label: 'Super admin — every unit' },
          ]}
          value={superAdmin ? 'SUPER_ADMIN' : 'ADMIN'}
          onChange={(v) => setSuperAdmin(v === 'SUPER_ADMIN')}
        />

        {!superAdmin && (
          <>
            <Select
              label="Access level"
              options={scopeLevels}
              value={scopeLevel}
              onChange={(v) => {
                setScopeLevel(v as UnitLevel);
                setScopeUnitId('');
              }}
            />
            <Select
              label="Unit"
              options={unitChoices.map((u) => ({ value: u.id, label: u.path }))}
              value={scopeUnitId}
              onChange={setScopeUnitId}
              placeholder="Select a unit"
            />
            <p style={{ fontFamily: fonts.sans, fontSize: 11, color: colors.muted, margin: '0 0 12px' }}>
              They will see access requests, voter lists, change requests and reports for that unit and everything under
              it — nothing else.
            </p>
          </>
        )}

        <div style={{ display: 'flex', gap: 8 }}>
          <Button tone="accent" loading={busy} onClick={() => void create()}>
            Create account
          </Button>
          <Button tone="ghost" onClick={() => setAdding(false)}>
            Cancel
          </Button>
        </div>
      </Modal>

      <Modal
        open={rescoping !== null}
        title="Change the area"
        subtitle={rescoping?.name}
        onClose={() => setRescoping(null)}
        width={420}
      >
        {error && <ErrorNote message={error} />}
        <Select
          label="Access level"
          options={scopeLevels}
          value={scopeLevel}
          onChange={(v) => {
            setScopeLevel(v as UnitLevel);
            setScopeUnitId('');
          }}
        />
        <Select
          label="Unit"
          options={unitChoices.map((u) => ({ value: u.id, label: u.path }))}
          value={scopeUnitId}
          onChange={setScopeUnitId}
          placeholder="Select a unit"
        />
        <div style={{ display: 'flex', gap: 8 }}>
          <Button tone="accent" loading={busy} onClick={() => void saveScope()}>
            Save area
          </Button>
          <Button tone="ghost" onClick={() => setRescoping(null)}>
            Cancel
          </Button>
        </div>
      </Modal>
    </div>
  );
}
