import { useRef, type KeyboardEvent } from 'react';
import './chips.css';

export type ChipGroupOption = { key: string; label: string };

export type ChipGroupProps = {
  id: string;
  label: string;
  options: ChipGroupOption[];
  selectedKey: string | null;
  onSelect: (key: string | null) => void;
  disabled?: boolean;
  invalid?: boolean;
  describedBy?: string;
};

export function ChipGroup({
  id,
  label,
  options,
  selectedKey,
  onSelect,
  disabled = false,
  invalid = false,
  describedBy,
}: ChipGroupProps) {
  const groupRef = useRef<HTMLDivElement>(null);

  const chips = () =>
    Array.from(
      groupRef.current?.querySelectorAll<HTMLElement>('[role="radio"]') ?? [],
    );

  const focusChip = (index: number) => {
    const all = chips();
    const target = all[((index % all.length) + all.length) % all.length];
    target?.focus();
    return target;
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (disabled) return;
    const all = chips();
    if (all.length === 0) return;
    const current = all.indexOf(document.activeElement as HTMLElement);
    const from = current === -1 ? 0 : current;

    const move = (index: number) => {
      event.preventDefault();
      const target = focusChip(index);
      const key = target?.dataset.chipKey;
      if (key !== undefined) onSelect(key);
    };

    switch (event.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        move(from + 1);
        break;
      case 'ArrowLeft':
      case 'ArrowUp':
        move(from - 1);
        break;
      case 'Home':
        move(0);
        break;
      case 'End':
        move(all.length - 1);
        break;
      case ' ':
      case 'Enter': {
        event.preventDefault();
        const focused = document.activeElement as HTMLElement | null;
        const key = focused?.dataset.chipKey;
        if (key !== undefined) onSelect(key === selectedKey ? null : key);
        break;
      }
      default:
        break;
    }
  };

  const tabStopKey =
    selectedKey !== null && options.some((o) => o.key === selectedKey)
      ? selectedKey
      : options[0]?.key;

  return (
    <div
      ref={groupRef}
      className="chip-group"
      role="radiogroup"
      aria-labelledby={`${id}-label`}
      aria-describedby={describedBy}
      aria-invalid={invalid ? true : undefined}
      onKeyDown={onKeyDown}
    >
      <span className="chip-group-label" id={`${id}-label`}>
        {label}
      </span>
      {options.map((option) => {
        const selected = option.key === selectedKey;
        return (
          <button
            key={option.key}
            type="button"
            role="radio"
            className="chip"
            data-chip-key={option.key}
            aria-checked={selected}
            disabled={disabled}
            tabIndex={option.key === tabStopKey ? 0 : -1}
            onClick={() => onSelect(selected ? null : option.key)}
          >
            {selected && (
              <span className="chip-check" aria-hidden="true">
                ✓
              </span>
            )}
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
