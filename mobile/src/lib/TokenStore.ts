import AsyncStorage from '@react-native-async-storage/async-storage';

const TOKEN_KEY = 'auth.token';

/** JWT storage. Values are never logged anywhere in the app. */
export const TokenStore = {
  async get(): Promise<string | null> {
    return AsyncStorage.getItem(TOKEN_KEY);
  },

  async set(token: string): Promise<void> {
    await AsyncStorage.setItem(TOKEN_KEY, token);
  },

  async clear(): Promise<void> {
    await AsyncStorage.removeItem(TOKEN_KEY);
  },
};
