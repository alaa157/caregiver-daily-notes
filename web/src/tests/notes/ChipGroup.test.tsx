import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { ChipGroup, type ChipGroupOption } from '../../features/notes/ChipGroup';

const options: ChipGroupOption[] = [
  { key: 'calm', label: 'Calm' },
  { key: 'happy', label: 'Happy' },
  { key: 'sad', label: 'Sad' },
];

function renderGroup(props?: Partial<Parameters<typeof ChipGroup>[0]>) {
  const onSelect = vi.fn();
  render(
    <ChipGroup
      id="mood"
      label="Mood"
      options={options}
      selectedKey={null}
      onSelect={onSelect}
      {...props}
    />,
  );
  return { onSelect };
}

function StatefulGroup(props?: Partial<Parameters<typeof ChipGroup>[0]>) {
  const [selected, setSelected] = useState<string | null>(null);
  return (
    <ChipGroup
      id="mood"
      label="Mood"
      options={options}
      selectedKey={selected}
      onSelect={setSelected}
      {...props}
    />
  );
}

const radios = () => screen.getAllByRole('radio');

describe('ChipGroup', () => {
  it('renders one radio per option with accessible names', () => {
    renderGroup();
    const chips = radios();
    expect(chips).toHaveLength(3);
    expect(chips.map((c) => c.textContent)).toEqual(['Calm', 'Happy', 'Sad']);
  });

  it('wraps options in a labelled radiogroup', () => {
    renderGroup();
    const group = screen.getByRole('radiogroup', { name: 'Mood' });
    expect(group).toHaveAttribute('aria-labelledby', 'mood-label');
    expect(document.getElementById('mood-label')).toHaveTextContent('Mood');
  });

  it('calls onSelect with the key when an unselected chip is clicked', async () => {
    const user = userEvent.setup();
    const { onSelect } = renderGroup({ selectedKey: 'calm' });
    await user.click(screen.getByRole('radio', { name: 'Happy' }));
    expect(onSelect).toHaveBeenCalledWith('happy');
  });

  it('calls onSelect(null) when the selected chip is clicked', async () => {
    const user = userEvent.setup();
    const { onSelect } = renderGroup({ selectedKey: 'calm' });
    await user.click(screen.getByRole('radio', { name: 'Calm' }));
    expect(onSelect).toHaveBeenCalledWith(null);
  });

  it('sets aria-checked only on the selected chip', () => {
    renderGroup({ selectedKey: 'happy' });
    expect(screen.getByRole('radio', { name: 'Calm' })).toHaveAttribute('aria-checked', 'false');
    expect(screen.getByRole('radio', { name: 'Happy' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: 'Sad' })).toHaveAttribute('aria-checked', 'false');
  });

  it('gives the first chip tabIndex 0 when nothing is selected', () => {
    renderGroup();
    expect(radios()[0]).toHaveAttribute('tabindex', '0');
    expect(radios()[1]).toHaveAttribute('tabindex', '-1');
    expect(radios()[2]).toHaveAttribute('tabindex', '-1');
  });

  it('gives only the selected chip tabIndex 0', () => {
    renderGroup({ selectedKey: 'sad' });
    expect(radios()[0]).toHaveAttribute('tabindex', '-1');
    expect(radios()[1]).toHaveAttribute('tabindex', '-1');
    expect(radios()[2]).toHaveAttribute('tabindex', '0');
  });

  it('ArrowRight on the last chip wraps to the first and selects it', () => {
    const { onSelect } = renderGroup();
    const last = radios()[2];
    last.focus();
    fireEvent.keyDown(last, { key: 'ArrowRight' });
    expect(onSelect).toHaveBeenCalledWith('calm');
    expect(document.activeElement).toBe(radios()[0]);
  });

  it('ArrowLeft on the first chip wraps to the last and selects it', () => {
    const { onSelect } = renderGroup();
    const first = radios()[0];
    first.focus();
    fireEvent.keyDown(first, { key: 'ArrowLeft' });
    expect(onSelect).toHaveBeenCalledWith('sad');
    expect(document.activeElement).toBe(radios()[2]);
  });

  it('Space selects the focused chip', () => {
    const { onSelect } = renderGroup();
    const chip = radios()[1];
    chip.focus();
    fireEvent.keyDown(chip, { key: ' ' });
    expect(onSelect).toHaveBeenCalledWith('happy');
  });

  it('Enter selects the focused chip', () => {
    const { onSelect } = renderGroup();
    const chip = radios()[1];
    chip.focus();
    fireEvent.keyDown(chip, { key: 'Enter' });
    expect(onSelect).toHaveBeenCalledWith('happy');
  });

  it('Space on the already-selected chip calls onSelect(null)', () => {
    const { onSelect } = renderGroup({ selectedKey: 'happy' });
    const chip = radios()[1];
    chip.focus();
    fireEvent.keyDown(chip, { key: ' ' });
    expect(onSelect).toHaveBeenCalledWith(null);
  });

  it('does not call onSelect and disables every chip when disabled', async () => {
    const user = userEvent.setup();
    const { onSelect } = renderGroup({ disabled: true });
    for (const chip of radios()) {
      expect(chip).toBeDisabled();
      await user.click(chip);
    }
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('sets aria-invalid on the group when invalid', () => {
    renderGroup({ invalid: true });
    expect(screen.getByRole('radiogroup')).toHaveAttribute('aria-invalid', 'true');
  });

  it('sets aria-describedby on the group when describedBy is passed', () => {
    renderGroup({ describedBy: 'mood-hint' });
    expect(screen.getByRole('radiogroup')).toHaveAttribute('aria-describedby', 'mood-hint');
  });

  it('renders an aria-hidden checkmark only on the selected chip', async () => {
    const user = userEvent.setup();
    render(<StatefulGroup />);
    expect(document.querySelector('.chip-check')).toBeNull();
    await user.click(screen.getByRole('radio', { name: 'Happy' }));
    const selected = screen.getByRole('radio', { name: 'Happy' });
    const check = selected.querySelector('.chip-check');
    expect(check).not.toBeNull();
    expect(check).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('radio', { name: 'Calm' }).querySelector('.chip-check')).toBeNull();
  });
});
