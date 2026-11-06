import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

const dialogShowModal = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');
const dialogClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');

beforeEach(() => {
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value(this: HTMLDialogElement) { this.setAttribute('open', ''); },
  });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value(this: HTMLDialogElement) { this.removeAttribute('open'); },
  });
});

afterEach(() => {
  cleanup();
  if (dialogShowModal) Object.defineProperty(HTMLDialogElement.prototype, 'showModal', dialogShowModal);
  else Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
  if (dialogClose) Object.defineProperty(HTMLDialogElement.prototype, 'close', dialogClose);
  else Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
  localStorage.clear();
  sessionStorage.clear();
  vi.unstubAllGlobals();
  vi.useRealTimers();
  document.documentElement.lang = 'en';
  document.documentElement.dir = 'ltr';
});
