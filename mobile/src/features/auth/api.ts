import {ApiClient} from '../../lib/ApiClient';

export interface AuthTokenResponse {
  token: string;
}

export interface RegisterBody {
  email: string;
  password: string;
}

// Provisional paths — reconciled against backend OpenAPI in Part B (Task B9).
export async function login(
  client: ApiClient,
  email: string,
  password: string,
): Promise<AuthTokenResponse> {
  return client.post<AuthTokenResponse>('/api/auth/login', {email, password});
}

export async function register(
  client: ApiClient,
  body: RegisterBody,
): Promise<AuthTokenResponse> {
  return client.post<AuthTokenResponse>('/api/auth/register', body);
}
