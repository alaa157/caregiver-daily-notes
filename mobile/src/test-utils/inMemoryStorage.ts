// In-memory AsyncStorage double for tests. Register with:
// jest.mock('@react-native-async-storage/async-storage', () => require('./inMemoryStorage').mock);
const store = new Map<string, string>();

export const mock = {
  __esModule: true,
  default: {
    setItem: jest.fn(async (k: string, v: string) => {
      store.set(k, v);
    }),
    getItem: jest.fn(async (k: string) => store.get(k) ?? null),
    removeItem: jest.fn(async (k: string) => {
      store.delete(k);
    }),
    clear: jest.fn(async () => {
      store.clear();
    }),
  },
};
