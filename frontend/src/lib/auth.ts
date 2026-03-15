// src/lib/auth.ts
import { fetchAuthSession } from 'aws-amplify/auth';

/** Returns the Cognito ID token string for use as a Bearer token. */
export async function getCurrentJwt(): Promise<string> {
  const session = await fetchAuthSession();
  const token = session.tokens?.idToken?.toString();
  if (!token) throw new Error('Not authenticated');
  return token;
}
