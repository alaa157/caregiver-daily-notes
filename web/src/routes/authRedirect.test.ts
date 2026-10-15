import { describe, expect, it } from 'vitest';
import { safeReturnPath } from './authRedirect';

describe('post-login destination', () => {
  it('preserves a local workspace path, query and fragment', () => {
    expect(safeReturnPath('/app?recipient=sample#notes')).toBe('/app?recipient=sample#notes');
    expect(safeReturnPath('/app/recipients')).toBe('/app/recipients');
  });
  it.each([undefined, {}, 'https://external.example', '//external.example', '/\\external.example', '/login', '/app/../../login', '/application', '/app\n'])('rejects unsafe or non-workspace destinations', (path) => {
    expect(safeReturnPath(path)).toBe('/app');
  });
});
