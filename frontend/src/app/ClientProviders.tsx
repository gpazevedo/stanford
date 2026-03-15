// src/app/ClientProviders.tsx
'use client';
import '@/lib/amplifyConfig';   // side-effect: configures Amplify once (browser only)
import { Authenticator } from '@aws-amplify/ui-react';
import '@aws-amplify/ui-react/styles.css';
import Link from 'next/link';

export function ClientProviders({ children }: { children: React.ReactNode }) {
  return (
    <Authenticator>
      {({ signOut, user }) => (
        <>
          <nav className="bg-white border-b border-gray-200 px-6 py-3 flex items-center justify-between">
            <div className="flex items-center gap-6">
              <span className="font-semibold text-gray-900">Stanford CS-PMN</span>
              <Link href="/"             className="text-sm text-gray-600 hover:text-gray-900">Search</Link>
              <Link href="/applications" className="text-sm text-gray-600 hover:text-gray-900">My Applications</Link>
              <Link href="/profile"      className="text-sm text-gray-600 hover:text-gray-900">Profile</Link>
            </div>
            <div className="flex items-center gap-3">
              <span className="text-sm text-gray-500">{user?.signInDetails?.loginId}</span>
              <button
                onClick={signOut}
                className="text-sm text-gray-600 hover:text-gray-900 border border-gray-300 rounded px-2 py-1"
              >
                Sign out
              </button>
            </div>
          </nav>
          <main className="mx-auto max-w-3xl px-4 py-8">
            {children}
          </main>
        </>
      )}
    </Authenticator>
  );
}
