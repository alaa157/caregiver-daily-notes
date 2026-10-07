import {TokenStore} from './TokenStore';
import {Transport} from './Transport';

/** Typed API entry point. Feature modules add thin wrappers; none touch fetch. */
export class ApiClient {
  constructor(readonly transport: Transport) {}

  async authHeader(): Promise<string | undefined> {
    const token = await TokenStore.get();
    return token ? `Bearer ${token}` : undefined;
  }

  get<T>(path: string): Promise<T> {
    return this.transport.request<T>('GET', path);
  }

  post<T>(path: string, body: unknown): Promise<T> {
    return this.transport.request<T>('POST', path, {body});
  }

  put<T>(path: string, body: unknown): Promise<T> {
    return this.transport.request<T>('PUT', path, {body});
  }

  del<T>(path: string): Promise<T> {
    return this.transport.request<T>('DELETE', path);
  }
}
