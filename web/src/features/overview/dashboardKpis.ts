import type { OverviewState } from './useDashboardOverview';

export type DashboardKpi = {
  label: string;
  value: number | null;
  state: 'loading' | 'ready' | 'error';
};

type DashboardKpiLabels = {
  recipients: string;
  recorded: string;
  notRecorded: string;
};

export function getDashboardKpis(state: OverviewState, labels: DashboardKpiLabels): DashboardKpi[] {
  if (state.status === 'loading' || state.status === 'error') {
    const status = state.status === 'loading' ? 'loading' : 'error';
    return Object.values(labels).map((label) => ({ label, value: null, state: status }));
  }

  const statusUnavailable = state.status === 'partial' || state.data.some((row) => row.status === 'error');
  return [
    { label: labels.recipients, value: state.data.length, state: 'ready' },
    // A partial history result cannot provide a complete recorded-note count.
    { label: labels.recorded, value: statusUnavailable ? null : state.data.filter((row) => row.status === 'recorded').length, state: statusUnavailable ? 'error' : 'ready' },
    { label: labels.notRecorded, value: statusUnavailable ? null : state.data.filter((row) => row.status === 'missing').length, state: statusUnavailable ? 'error' : 'ready' },
  ];
}
