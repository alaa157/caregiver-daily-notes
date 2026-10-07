import AsyncStorage from '@react-native-async-storage/async-storage';
import appJson from '../../app.json';

const URL_KEY = 'app.serverUrl';

export class InvalidServerUrlError extends Error {
  constructor(value: string) {
    super(`Invalid server URL: ${value}`);
    this.name = 'InvalidServerUrlError';
  }
}

function defaultUrl(): string {
  const fallback = 'https://CHANGE-ME';
  const configured: unknown = (appJson as {serverUrlDefault?: unknown}).serverUrlDefault;
  return typeof configured === 'string' && configured.length > 0 ? configured : fallback;
}

function normalize(raw: string): string {
  const trimmed = raw.trim().replace(/\/+$/, '');
  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    throw new InvalidServerUrlError(raw);
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new InvalidServerUrlError(raw);
  }
  return parsed.toString().replace(/\/+$/, '');
}

/** Backend base URL. Read per call by transports — never cached at import time. */
export const ServerUrlStore = {
  async get(): Promise<string> {
    const stored = await AsyncStorage.getItem(URL_KEY);
    return stored && stored.length > 0 ? stored : defaultUrl();
  },

  async set(raw: string): Promise<void> {
    await AsyncStorage.setItem(URL_KEY, normalize(raw));
  },

  async reset(): Promise<void> {
    await AsyncStorage.removeItem(URL_KEY);
  },
};
