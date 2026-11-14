import type { Observation } from '../../api/observation';
import type { ChipOption } from './chipOptions';

export function chipKeyFor<T>(
  observation: Observation<T>,
  options: readonly ChipOption<any>[],
): string | null {
  if (observation.state === 'NOT_RECORDED') {
    return null;
  }

  if (observation.state === 'UNKNOWN') {
    const unknown = options.find((option) => option.state === 'UNKNOWN');
    return unknown ? unknown.key : null;
  }

  if (observation.state === 'RECORDED') {
    const match = options.find(
      (option) => option.state === 'RECORDED' && Object.is(option.value, observation.value),
    );
    return match ? match.key : null;
  }

  return null;
}

export function observationForChip<T>(
  key: string | null,
  options: readonly ChipOption<any>[],
): Observation<T> {
  if (key === null) {
    return { state: 'NOT_RECORDED', value: null };
  }

  const option = options.find((entry) => entry.key === key);
  if (!option) {
    return { state: 'NOT_RECORDED', value: null };
  }

  if (option.state === 'UNKNOWN') {
    return { state: 'UNKNOWN', value: null };
  }

  return { state: 'RECORDED', value: option.value as T };
}
